package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.application.vmware.ProvisioningErrorCode
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sealed interface for provisioning failures with type-safe error discrimination.
 *
 * Provides compile-time exhaustiveness checking for error handling in callers.
 * Replaces `Result<..., Any>` to ensure all error cases are explicitly handled.
 */
public sealed interface ProvisioningFailure {
    /** All retry attempts exhausted - contains retry metadata */
    public data class Exhausted(val error: ResilientProvisioningService.RetryExhaustedError) : ProvisioningFailure

    /** Hypervisor-level error (VMware, Proxmox, etc.) */
    public data class HypervisorError(val error: VsphereError) : ProvisioningFailure
}

/**
 * Configuration for retry behavior in [ResilientProvisioningService].
 *
 * Can be injected from Spring properties:
 * ```yaml
 * dvmm:
 *   provisioning:
 *     retry:
 *       max-attempts: 5
 *       initial-backoff-seconds: 10
 *       backoff-multiplier: 2.0
 *       max-backoff-ms: 120000
 * ```
 *
 * @property maxAttempts Maximum total attempts (1 initial + retries)
 * @property initialBackoffSeconds Initial backoff duration in seconds
 * @property backoffMultiplier Multiplier for exponential backoff (typically 2.0)
 * @property maxBackoffMs Maximum backoff duration in milliseconds
 */
public data class RetryConfiguration(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialBackoffSeconds: Long = DEFAULT_INITIAL_BACKOFF_SECONDS,
    val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
    val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS
) {
    public companion object {
        public const val DEFAULT_MAX_ATTEMPTS: Int = 5
        public const val DEFAULT_INITIAL_BACKOFF_SECONDS: Long = 10L
        public const val DEFAULT_BACKOFF_MULTIPLIER: Double = 2.0
        public const val DEFAULT_MAX_BACKOFF_MS: Long = 120_000L
    }
}

/**
 * Resilient provisioning service that wraps HypervisorPort with retry logic.
 *
 * ## Retry Policy (AC-3.6.1)
 *
 * - **Total attempts:** 5 (1 initial + 4 retries)
 * - **Backoff:** Exponential starting at 10s, multiplier 2.0, max 120s
 *   - Attempt 1: immediate
 *   - Attempt 2: after 10s
 *   - Attempt 3: after 20s (10s * 2)
 *   - Attempt 4: after 40s (20s * 2)
 *   - Attempt 5: after 80s (40s * 2, under 120s cap)
 *
 * ## Error Classification (AC-3.6.3)
 *
 * - **Retriable errors:** Connection errors, timeouts, temporary API failures
 * - **Permanent errors:** Invalid configuration, missing resources, auth failures
 *
 * Permanent errors skip retry entirely - they require human intervention.
 *
 * ## Usage
 *
 * ```kotlin
 * val result = service.createVmWithRetry(spec, correlationId) { stage ->
 *     // Progress callback for UI updates
 * }
 * when (result) {
 *     is Result.Success -> handleSuccess(result.value)
 *     is Result.Failure -> when (val failure = result.error) {
 *         is ProvisioningFailure.Exhausted -> handleExhausted(failure.error)
 *         is ProvisioningFailure.HypervisorError -> handleHypervisorError(failure.error)
 *     }
 * }
 * ```
 *
 * @see VsphereError.retriable for error classification
 * @see ProvisioningFailure for type-safe error discrimination
 */
public class ResilientProvisioningService(
    private val hypervisorPort: HypervisorPort,
    private val retryConfiguration: RetryConfiguration = RetryConfiguration()
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Error returned when all retry attempts are exhausted.
     *
     * Contains retry metadata for logging and user notification (AC-3.6.2).
     */
    public data class RetryExhaustedError(
        /** Total number of attempts made */
        val attemptCount: Int,
        /** Error code from the last attempt */
        val lastErrorCode: ProvisioningErrorCode,
        /** User-friendly error message */
        val userMessage: String,
        /** The underlying vSphere error from the last attempt */
        val lastError: VsphereError
    )

    private val retryConfig: RetryConfig = RetryConfig.custom<Result<VmProvisioningResult, VsphereError>>()
        .maxAttempts(retryConfiguration.maxAttempts)
        // Exponential backoff with configurable parameters
        // Default sequence: 10s -> 20s -> 40s -> 80s (max 120s)
        .intervalFunction(
            IntervalFunction.ofExponentialBackoff(
                Duration.ofSeconds(retryConfiguration.initialBackoffSeconds),
                retryConfiguration.backoffMultiplier,
                Duration.ofMillis(retryConfiguration.maxBackoffMs)
            )
        )
        .retryOnResult { result ->
            // Only retry if the error is retriable (transient)
            result is Result.Failure && result.error.retriable
        }
        // Don't retry these exceptions - they should propagate immediately:
        // - PermanentErrorException: Our escape hatch for non-retriable errors
        // - CancellationException: Coroutine cancellation must propagate for structured concurrency
        .ignoreExceptions(PermanentErrorException::class.java, CancellationException::class.java)
        .failAfterMaxAttempts(true)
        .build()

    /**
     * Create a VM with automatic retry for transient errors.
     *
     * @param spec VM specification
     * @param correlationId Correlation ID for logging (typically from EventMetadata)
     * @param onProgress Optional callback for progress updates
     * @return Success with VmProvisioningResult, or Failure with [ProvisioningFailure]
     */
    public suspend fun createVmWithRetry(
        spec: VmSpec,
        correlationId: String,
        onProgress: suspend (VmProvisioningStage) -> Unit = {}
    ): Result<VmProvisioningResult, ProvisioningFailure> {
        val retry = Retry.of("provisioning-$correlationId", retryConfig)

        // Track retry attempts for logging - use atomic types for thread-safety
        // (Resilience4j's onRetry callback may execute on different thread than retry block)
        val attemptCount = AtomicInteger(0)
        val lastError = AtomicReference<VsphereError?>(null)

        retry.eventPublisher.onRetry { event ->
            logger.warn {
                "Retry attempt ${event.numberOfRetryAttempts}/${retryConfiguration.maxAttempts} for provisioning. " +
                    "CorrelationId: $correlationId, " +
                    "LastError: ${event.lastThrowable?.message ?: "result-based retry"}, " +
                    "WaitDuration: ${event.waitInterval.toSeconds()}s"
            }
        }

        return try {
            val innerResult = retry.executeSuspendFunction {
                val currentAttempt = attemptCount.incrementAndGet()
                if (currentAttempt > 1) {
                    logger.info {
                        "Provisioning attempt $currentAttempt/${retryConfiguration.maxAttempts}. CorrelationId: $correlationId"
                    }
                }

                val result = hypervisorPort.createVm(spec, onProgress)

                // Track last error for RetryExhaustedError
                if (result is Result.Failure) {
                    lastError.set(result.error)
                    // If permanent error, don't retry - return immediately
                    if (!result.error.retriable) {
                        logger.info {
                            "Permanent error detected, skipping retry. " +
                                "CorrelationId: $correlationId, " +
                                "Error: ${result.error.message}"
                        }
                        // Throw to break out of retry loop for permanent errors
                        throw PermanentErrorException(result.error)
                    }
                }

                result
            }

            // Map the inner result to ProvisioningFailure type
            when (innerResult) {
                is Result.Success -> innerResult.value.success()
                is Result.Failure -> ProvisioningFailure.HypervisorError(innerResult.error).failure()
            }
        } catch (e: PermanentErrorException) {
            // Permanent error - return without retry, wrapped in ProvisioningFailure
            ProvisioningFailure.HypervisorError(e.error).failure()
        } catch (e: io.github.resilience4j.retry.MaxRetriesExceededException) {
            // All retries exhausted - wrap in ProvisioningFailure.Exhausted
            // If lastError is null, it indicates a bug in retry/error tracking - fail fast
            val error = lastError.get() ?: run {
                logger.error {
                    "INTERNAL LOGIC ERROR: lastError is null after retry exhaustion. " +
                        "This indicates a bug in retry/error tracking. CorrelationId: $correlationId"
                }
                throw IllegalStateException(
                    "lastError is null after retry exhaustion. " +
                        "This indicates a bug in retry/error tracking."
                )
            }
            logger.error(e) {
                "Max retries (${retryConfiguration.maxAttempts}) exhausted for provisioning. " +
                    "CorrelationId: $correlationId, " +
                    "LastError: ${error.message}, " +
                    "ErrorCode: ${error.errorCode}"
            }
            val exhaustedError = RetryExhaustedError(
                attemptCount = attemptCount.get(),
                lastErrorCode = error.errorCode,
                userMessage = error.userMessage,
                lastError = error
            )
            ProvisioningFailure.Exhausted(exhaustedError).failure()
        } catch (e: CancellationException) {
            throw e // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.error(e) {
                "Unexpected error during provisioning. CorrelationId: $correlationId"
            }
            val apiError = VsphereError.ApiError("Unexpected error: ${e.message}", e)
            ProvisioningFailure.HypervisorError(apiError).failure()
        }
    }

    /**
     * Internal exception to break out of retry loop for permanent errors.
     * Not exposed externally - converted to Result.Failure.
     */
    private class PermanentErrorException(val error: VsphereError) : Exception(error.message)
}

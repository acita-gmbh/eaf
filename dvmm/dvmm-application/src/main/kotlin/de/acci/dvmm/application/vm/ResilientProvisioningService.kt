package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.application.vmware.ProvisioningErrorCode
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

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
 *   - Attempt 5: after 80s (max 120s cap applies here)
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
 * ```
 *
 * @see VsphereError.retriable for error classification
 */
public class ResilientProvisioningService(
    private val hypervisorPort: HypervisorPort
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
        .maxAttempts(MAX_ATTEMPTS)
        // Exponential backoff: 10s initial, 2x multiplier, capped at 120s
        // Sequence: 10s -> 20s -> 40s -> 80s (max 120s)
        .intervalFunction(
            IntervalFunction.ofExponentialBackoff(
                Duration.ofSeconds(INITIAL_BACKOFF_SECONDS),
                BACKOFF_MULTIPLIER,
                Duration.ofMillis(MAX_BACKOFF_MS)
            )
        )
        .retryOnResult { result ->
            // Only retry if the error is retriable (transient)
            result is Result.Failure && result.error.retriable
        }
        // Don't retry PermanentErrorException - it's our escape hatch for non-retriable errors
        .ignoreExceptions(PermanentErrorException::class.java)
        .failAfterMaxAttempts(true)
        .build()

    /**
     * Create a VM with automatic retry for transient errors.
     *
     * @param spec VM specification
     * @param correlationId Correlation ID for logging (typically from EventMetadata)
     * @param onProgress Optional callback for progress updates
     * @return Success with VmProvisioningResult, or Failure with RetryExhaustedError or VsphereError
     */
    public suspend fun createVmWithRetry(
        spec: VmSpec,
        correlationId: String,
        onProgress: suspend (VmProvisioningStage) -> Unit = {}
    ): Result<VmProvisioningResult, Any> {
        val retry = Retry.of("provisioning-$correlationId", retryConfig)

        // Track retry attempts for logging
        var attemptCount = 0
        var lastError: VsphereError? = null

        retry.eventPublisher.onRetry { event ->
            logger.warn {
                "Retry attempt ${event.numberOfRetryAttempts}/$MAX_ATTEMPTS for provisioning. " +
                    "CorrelationId: $correlationId, " +
                    "LastError: ${event.lastThrowable?.message ?: "result-based retry"}, " +
                    "WaitDuration: ${event.waitInterval.toSeconds()}s"
            }
        }

        return try {
            retry.executeSuspendFunction {
                attemptCount++
                if (attemptCount > 1) {
                    logger.info {
                        "Provisioning attempt $attemptCount/$MAX_ATTEMPTS. CorrelationId: $correlationId"
                    }
                }

                val result = hypervisorPort.createVm(spec, onProgress)

                // Track last error for RetryExhaustedError
                if (result is Result.Failure) {
                    lastError = result.error
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
        } catch (e: PermanentErrorException) {
            // Permanent error - return without retry
            e.error.failure()
        } catch (e: io.github.resilience4j.retry.MaxRetriesExceededException) {
            // All retries exhausted
            val error = lastError ?: VsphereError.ApiError("Unknown error after retries")
            logger.error(e) {
                "Max retries ($MAX_ATTEMPTS) exhausted for provisioning. " +
                    "CorrelationId: $correlationId, " +
                    "LastError: ${error.message}, " +
                    "ErrorCode: ${error.errorCode}"
            }
            RetryExhaustedError(
                attemptCount = attemptCount,
                lastErrorCode = error.errorCode,
                userMessage = error.userMessage,
                lastError = error
            ).failure()
        } catch (e: CancellationException) {
            throw e // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.error(e) {
                "Unexpected error during provisioning. CorrelationId: $correlationId"
            }
            VsphereError.ApiError("Unexpected error: ${e.message}", e).failure()
        }
    }

    /**
     * Internal exception to break out of retry loop for permanent errors.
     * Not exposed externally - converted to Result.Failure.
     */
    private class PermanentErrorException(val error: VsphereError) : Exception(error.message)

    private companion object {
        /** Maximum total attempts (1 initial + 4 retries) */
        const val MAX_ATTEMPTS = 5

        /** Initial backoff in seconds */
        const val INITIAL_BACKOFF_SECONDS = 10L

        /** Exponential backoff multiplier (doubles each retry) */
        const val BACKOFF_MULTIPLIER = 2.0

        /** Maximum backoff in milliseconds (120 seconds) */
        const val MAX_BACKOFF_MS = 120_000L
    }
}

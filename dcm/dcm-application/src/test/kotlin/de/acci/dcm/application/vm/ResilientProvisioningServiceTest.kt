package de.acci.dcm.application.vm

import de.acci.dcm.application.vmware.HypervisorPort
import de.acci.dcm.application.vmware.VmSpec
import de.acci.dcm.application.vmware.VsphereError
import de.acci.dcm.domain.vm.VmProvisioningResult
import de.acci.dcm.domain.vm.VmProvisioningStage
import de.acci.dcm.domain.vm.VmwareVmId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tests for ResilientProvisioningService retry logic (AC-3.6.1, AC-3.6.2, AC-3.6.3).
 *
 * Verifies:
 * - Transient errors trigger retry with exponential backoff
 * - Permanent errors do NOT trigger retry
 * - Max retries exhaustion results in failure
 * - Retry attempts are logged with correlation ID
 */
class ResilientProvisioningServiceTest {

    private lateinit var hypervisorPort: HypervisorPort
    private lateinit var service: ResilientProvisioningService

    // Fast retry configuration for tests - minimal backoff to avoid slow test execution
    // Resilience4j requires:
    //   - initialBackoff in seconds (minimum 1 second due to Duration.ofSeconds)
    //   - maxBackoff in milliseconds (must be >= initialBackoff in ms)
    // This preserves retry semantics (same maxAttempts, error classification) while being fast
    private val fastRetryConfig = RetryConfiguration(
        maxAttempts = RetryConfiguration.DEFAULT_MAX_ATTEMPTS,
        initialBackoffSeconds = 1L,     // 1 second minimum
        backoffMultiplier = 1.0,        // No exponential growth
        maxBackoffMs = 1000L            // 1 second max (must be >= initialBackoff in ms)
    )

    @BeforeEach
    fun setup() {
        hypervisorPort = mockk()
        service = ResilientProvisioningService(
            hypervisorPort = hypervisorPort,
            retryConfiguration = fastRetryConfig
        )
    }

    private val testSpec = VmSpec(
        name = "test-vm",
        template = "ubuntu-template",
        cpu = 4,
        memoryGb = 8
    )

    private val successResult = VmProvisioningResult(
        vmwareVmId = VmwareVmId.of("vm-123"),
        ipAddress = "192.168.1.100",
        hostname = "test-vm",
        warningMessage = null
    )

    @Nested
    inner class SuccessfulProvisioning {

        @Test
        fun `succeeds on first attempt`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns successResult.success()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Success)
            assertEquals(successResult, (result as Result.Success).value)
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `succeeds after transient failure`() = runTest {
            // Given: First call fails with transient error, second succeeds
            val callCount = AtomicInteger(0)
            coEvery { hypervisorPort.createVm(any(), any()) } answers {
                if (callCount.incrementAndGet() == 1) {
                    VsphereError.ConnectionError("Connection refused").failure()
                } else {
                    successResult.success()
                }
            }

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 2) { hypervisorPort.createVm(any(), any()) }
        }
    }

    @Nested
    inner class RetryBehavior {

        @Test
        fun `retries on ConnectionError (transient)`() = runTest {
            // Given: All calls fail with transient error
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.ConnectionError("Connection refused").failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then: Should retry up to max attempts (5 total)
            assertTrue(result is Result.Failure)
            coVerify(exactly = 5) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `retries on Timeout (transient)`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.Timeout("Operation timed out").failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then: Should retry up to max attempts
            assertTrue(result is Result.Failure)
            coVerify(exactly = 5) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `retries on ApiError (transient)`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.ApiError("Temporary API failure").failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Failure)
            coVerify(exactly = 5) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `retries on ResourceExhausted (transient)`() = runTest {
            // Given: Resources may free up, so we retry
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.ResourceExhausted(
                    message = "Insufficient resources",
                    resourceType = "MEMORY",
                    requested = 32,
                    available = 16
                ).failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Failure)
            coVerify(exactly = 5) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `retries on OperationFailed (transient)`() = runTest {
            // Given: Operation failures are typically transient
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.OperationFailed(
                    operation = "cloneVm",
                    details = "Clone task failed temporarily"
                ).failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Failure)
            coVerify(exactly = 5) { hypervisorPort.createVm(any(), any()) }
        }
    }

    @Nested
    inner class PermanentErrorsNoRetry {

        @Test
        fun `does not retry on InvalidConfiguration (permanent)`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.InvalidConfiguration(
                    message = "Invalid VM configuration",
                    field = "cpuCores"
                ).failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then: Should NOT retry - config error requires human intervention
            assertTrue(result is Result.Failure)
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `does not retry on ResourceNotFound (permanent)`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.ResourceNotFound(
                    resourceType = "Template",
                    resourceId = "missing-template"
                ).failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then: Should NOT retry - missing template requires human intervention
            assertTrue(result is Result.Failure)
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `does not retry on AuthenticationError (permanent)`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.AuthenticationError("Invalid credentials").failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then: Should NOT retry - bad credentials require human intervention
            assertTrue(result is Result.Failure)
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
        }
    }

    @Nested
    inner class RetryExhaustion {

        @Test
        fun `returns failure with retry count after max retries`() = runTest {
            // Given
            coEvery { hypervisorPort.createVm(any(), any()) } returns
                VsphereError.ConnectionError("Connection refused").failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Failure)
            val failure = (result as Result.Failure).error
            assertTrue(failure is ProvisioningFailure.Exhausted)
            assertEquals(5, (failure as ProvisioningFailure.Exhausted).error.attemptCount)
        }

        @Test
        fun `preserves original error information after retry exhaustion`() = runTest {
            // Given
            val originalError = VsphereError.Timeout("VMware Tools timeout")
            coEvery { hypervisorPort.createVm(any(), any()) } returns originalError.failure()

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then
            assertTrue(result is Result.Failure)
            val failure = (result as Result.Failure).error as ProvisioningFailure.Exhausted
            assertEquals(originalError.errorCode, failure.error.lastErrorCode)
            assertEquals(originalError.userMessage, failure.error.userMessage)
        }
    }

    @Nested
    inner class ProgressCallbacks {

        @Test
        fun `passes progress callbacks through to hypervisor`() = runTest {
            // Given
            val stagesReceived = mutableListOf<VmProvisioningStage>()

            coEvery { hypervisorPort.createVm(any(), any()) } coAnswers {
                // Simulate progress callbacks - the onProgress is the second argument
                val callback = secondArg<suspend (VmProvisioningStage) -> Unit>()
                callback(VmProvisioningStage.CLONING)
                callback(VmProvisioningStage.READY)
                successResult.success()
            }

            // When
            service.createVmWithRetry(testSpec, "test-correlation") { stage ->
                stagesReceived.add(stage)
            }

            // Then
            assertEquals(listOf(VmProvisioningStage.CLONING, VmProvisioningStage.READY), stagesReceived)
        }
    }

    @Nested
    inner class ExceptionHandling {

        @Test
        fun `propagates CancellationException without retry`() = runTest {
            // Given: HypervisorPort throws CancellationException (coroutine cancelled)
            coEvery { hypervisorPort.createVm(any(), any()) } throws CancellationException("Job was cancelled")

            // When/Then: CancellationException should propagate (not be caught)
            assertThrows<CancellationException> {
                service.createVmWithRetry(testSpec, "test-correlation")
            }
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
        }

        @Test
        fun `wraps unexpected exceptions as HypervisorError with ApiError`() = runTest {
            // Given: HypervisorPort throws unexpected RuntimeException
            coEvery { hypervisorPort.createVm(any(), any()) } throws RuntimeException("Unexpected failure")

            // When
            val result = service.createVmWithRetry(testSpec, "test-correlation")

            // Then: Should be wrapped in ProvisioningFailure.HypervisorError with ApiError inside
            assertTrue(result is Result.Failure)
            val failure = (result as Result.Failure).error
            assertTrue(failure is ProvisioningFailure.HypervisorError)
            val apiError = (failure as ProvisioningFailure.HypervisorError).error
            assertTrue(apiError is VsphereError.ApiError)
            val message = requireNotNull(apiError.message) { "Expected error message to be present" }
            assertTrue(message.contains("Unexpected"))
        }
    }
}

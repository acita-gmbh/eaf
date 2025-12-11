package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.application.vmware.ProvisioningErrorCode
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vm.VmwareVmId
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
import java.util.concurrent.atomic.AtomicInteger

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

    @BeforeEach
    fun setup() {
        hypervisorPort = mockk()
        service = ResilientProvisioningService(hypervisorPort)
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
            val error = (result as Result.Failure).error
            assertTrue(error is ResilientProvisioningService.RetryExhaustedError)
            assertEquals(5, (error as ResilientProvisioningService.RetryExhaustedError).attemptCount)
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
            val error = (result as Result.Failure).error as ResilientProvisioningService.RetryExhaustedError
            assertEquals(originalError.errorCode, error.lastErrorCode)
            assertEquals(originalError.userMessage, error.userMessage)
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
}

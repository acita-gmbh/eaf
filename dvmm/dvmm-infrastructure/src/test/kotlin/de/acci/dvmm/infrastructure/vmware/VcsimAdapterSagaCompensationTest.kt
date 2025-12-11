package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.eaf.core.result.Result
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for VcsimAdapter error simulation and saga compensation support (AC-3.6.4).
 *
 * These tests verify:
 * - Immediate error simulation (no partial VM created)
 * - Staged error simulation (partial VM created, then error)
 * - VM deletion tracking for saga compensation verification
 */
@DisplayName("VcsimAdapter Saga Compensation")
class VcsimAdapterSagaCompensationTest {

    private lateinit var adapter: VcsimAdapter

    private val testSpec = VmSpec(
        name = "test-vm",
        template = "ubuntu-template",
        cpu = 4,
        memoryGb = 8
    )

    @BeforeEach
    fun setup() {
        adapter = VcsimAdapter()
    }

    @AfterEach
    fun cleanup() {
        adapter.clearSimulatedError()
    }

    @Nested
    @DisplayName("Immediate Error Simulation")
    inner class ImmediateError {

        @Test
        fun `returns error immediately when no afterStage configured`() = runTest {
            // Given: Immediate error configured
            val error = VsphereError.ConnectionError("Simulated connection failure")
            adapter.simulateError(error)
            val stagesReceived = mutableListOf<VmProvisioningStage>()

            // When: Create VM
            val result = adapter.createVm(testSpec) { stage ->
                stagesReceived.add(stage)
            }

            // Then: Error returned immediately, no stages progressed
            assertTrue(result is Result.Failure)
            assertEquals(error.message, (result as Result.Failure).error.message)
            assertTrue(stagesReceived.isEmpty(), "No stages should be emitted on immediate error")
        }

        @Test
        fun `connection error is retriable`() = runTest {
            // Given
            val error = VsphereError.ConnectionError("Network timeout")
            adapter.simulateError(error)

            // When
            val result = adapter.createVm(testSpec)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error.retriable, "ConnectionError should be retriable")
        }

        @Test
        fun `invalid configuration error is not retriable`() = runTest {
            // Given
            val error = VsphereError.InvalidConfiguration("Invalid CPU count", "cpu")
            adapter.simulateError(error)

            // When
            val result = adapter.createVm(testSpec)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(!failure.error.retriable, "InvalidConfiguration should NOT be retriable")
        }
    }

    @Nested
    @DisplayName("Staged Error Simulation (Partial VM Creation)")
    inner class StagedError {

        @Test
        fun `error after CLONING stage simulates partial VM creation`() = runTest {
            // Given: Error configured after CLONING (VM created but subsequent steps fail)
            val error = VsphereError.Timeout("VMware Tools timeout")
            adapter.simulateError(error, afterStage = VmProvisioningStage.CLONING)
            val stagesReceived = mutableListOf<VmProvisioningStage>()

            // When: Wrapped in try-catch since staged errors throw SimulatedVsphereException
            val result = try {
                adapter.createVm(testSpec) { stage -> stagesReceived.add(stage) }
            } catch (e: VcsimAdapter.SimulatedVsphereException) {
                Result.Failure(e.error)
            }

            // Then: Error thrown after CLONING stage completes
            assertTrue(result is Result.Failure, "Should fail after CLONING")
            assertTrue(stagesReceived.contains(VmProvisioningStage.CLONING), "CLONING stage should complete before error")
            assertTrue(!stagesReceived.contains(VmProvisioningStage.CONFIGURING), "CONFIGURING should NOT start after error")
        }

        @Test
        fun `error after WAITING_FOR_NETWORK stage - IP detection failure`() = runTest {
            // Given: Error after network wait (common failure scenario)
            val error = VsphereError.Timeout("VMware Tools did not respond")
            adapter.simulateError(error, afterStage = VmProvisioningStage.WAITING_FOR_NETWORK)
            val stagesReceived = mutableListOf<VmProvisioningStage>()

            // When: Wrapped in try-catch since staged errors throw
            val result = try {
                adapter.createVm(testSpec) { stage -> stagesReceived.add(stage) }
            } catch (e: VcsimAdapter.SimulatedVsphereException) {
                Result.Failure(e.error)
            }

            // Then: Progressed through all stages up to WAITING_FOR_NETWORK
            assertTrue(stagesReceived.contains(VmProvisioningStage.CLONING))
            assertTrue(stagesReceived.contains(VmProvisioningStage.CONFIGURING))
            assertTrue(stagesReceived.contains(VmProvisioningStage.POWERING_ON))
            assertTrue(stagesReceived.contains(VmProvisioningStage.WAITING_FOR_NETWORK))
            assertTrue(result is Result.Failure)
        }
    }

    @Nested
    @DisplayName("VM Deletion Tracking")
    inner class DeletionTracking {

        @Test
        fun `tracks deleted VM IDs for saga compensation verification`() = runTest {
            // Given: No errors, successful creation
            val result = adapter.createVm(testSpec)
            assertTrue(result is Result.Success)
            val vmId = (result as Result.Success).value.vmwareVmId

            // When: Delete the VM
            adapter.deleteVm(de.acci.dvmm.application.vmware.VmId(vmId.value))

            // Then: VM ID tracked in deleted list
            assertTrue(adapter.getDeletedVmIds().contains(vmId.value))
        }

        @Test
        fun `deleted VMs list is cleared on clearSimulatedError`() = runTest {
            // Given: Delete some VMs
            adapter.deleteVm(de.acci.dvmm.application.vmware.VmId("vm-001"))
            adapter.deleteVm(de.acci.dvmm.application.vmware.VmId("vm-002"))
            assertEquals(2, adapter.getDeletedVmIds().size)

            // When: Clear simulation state
            adapter.clearSimulatedError()

            // Then: Deleted VMs list cleared
            assertTrue(adapter.getDeletedVmIds().isEmpty())
        }

        @Test
        fun `multiple deletions are tracked separately`() = runTest {
            // When: Delete multiple VMs
            adapter.deleteVm(de.acci.dvmm.application.vmware.VmId("vm-001"))
            adapter.deleteVm(de.acci.dvmm.application.vmware.VmId("vm-002"))
            adapter.deleteVm(de.acci.dvmm.application.vmware.VmId("vm-003"))

            // Then: All tracked
            val deletedIds = adapter.getDeletedVmIds()
            assertEquals(3, deletedIds.size)
            assertTrue(deletedIds.contains("vm-001"))
            assertTrue(deletedIds.contains("vm-002"))
            assertTrue(deletedIds.contains("vm-003"))
        }
    }

    @Nested
    @DisplayName("Clear Error State")
    inner class ClearState {

        @Test
        fun `clearSimulatedError resets to success behavior`() = runTest {
            // Given: Error configured
            adapter.simulateError(VsphereError.Timeout("Should not see this"))

            // When: Clear error
            adapter.clearSimulatedError()
            val result = adapter.createVm(testSpec)

            // Then: Success (default behavior)
            assertTrue(result is Result.Success)
        }
    }

    @Nested
    @DisplayName("Success Path")
    inner class SuccessPath {

        @Test
        fun `completes all stages on success`() = runTest {
            // Given: No error simulation
            val stagesReceived = mutableListOf<VmProvisioningStage>()

            // When
            val result = adapter.createVm(testSpec) { stage ->
                stagesReceived.add(stage)
            }

            // Then: All stages completed in order
            assertTrue(result is Result.Success)
            assertEquals(
                listOf(
                    VmProvisioningStage.CLONING,
                    VmProvisioningStage.CONFIGURING,
                    VmProvisioningStage.POWERING_ON,
                    VmProvisioningStage.WAITING_FOR_NETWORK,
                    VmProvisioningStage.READY
                ),
                stagesReceived
            )
        }

        @Test
        fun `returns simulated VM ID and IP on success`() = runTest {
            // When
            val result = adapter.createVm(testSpec)

            // Then
            assertTrue(result is Result.Success)
            val vmResult = (result as Result.Success).value
            assertEquals("vm-100", vmResult.vmwareVmId.value)
            assertEquals("192.168.1.100", vmResult.ipAddress)
            assertEquals(testSpec.name, vmResult.hostname)
        }
    }
}

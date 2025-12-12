package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.Cluster
import de.acci.dvmm.application.vmware.Datacenter
import de.acci.dvmm.application.vmware.Datastore
import de.acci.dvmm.application.vmware.Network
import de.acci.dvmm.application.vmware.ResourcePool
import de.acci.dvmm.application.vmware.VmId
import de.acci.dvmm.application.vmware.VmInfo
import de.acci.dvmm.application.vmware.VmPowerState
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.dvmm.domain.vmware.VcenterConnectionParams
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * VCSIM adapter for vSphere operations in test environments.
 *
 * VCSIM (vCenter Simulator) is VMware's official testing tool that simulates
 * a vCenter Server. This adapter connects to a VCSIM instance for integration tests.
 *
 * ## Profile Selection
 *
 * Activated when the "vcsim" profile is active:
 * ```yaml
 * spring:
 *   profiles:
 *     active: vcsim
 * ```
 *
 * ## VCSIM Setup
 *
 * Run VCSIM via Docker:
 * ```bash
 * docker run -d --name vcsim -p 443:443 vmware/vcsim
 * ```
 *
 * Default credentials: user/pass
 * Default datacenter: DC0
 * Default cluster: /DC0/host/DC0_C0
 *
 * ## Test Mode Behavior
 *
 * This adapter validates the basic connection but uses simplified validation
 * logic suitable for the VCSIM environment.
 *
 * @see VcenterAdapter for production implementation
 */
@Component
@Profile("vcsim")
public class VcsimAdapter : HypervisorPort {

    private val logger = KotlinLogging.logger {}

    // Error simulation support for testing (thread-safe for parallel test execution)
    private val simulatedError = AtomicReference<VsphereError?>(null)
    private val errorAfterStage = AtomicReference<VmProvisioningStage?>(null)
    private val deletedVmIds: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /**
     * Configure error simulation for testing saga compensation.
     *
     * @param error The error to return
     * @param afterStage If set, error occurs after this stage (simulating partial creation)
     */
    public fun simulateError(error: VsphereError?, afterStage: VmProvisioningStage? = null) {
        this.simulatedError.set(error)
        this.errorAfterStage.set(afterStage)
        logger.info { "VCSIM error simulation configured: error=$error, afterStage=$afterStage" }
    }

    /**
     * Clear any configured error simulation.
     */
    public fun clearSimulatedError() {
        this.simulatedError.set(null)
        this.errorAfterStage.set(null)
        synchronized(this.deletedVmIds) {
            this.deletedVmIds.clear()
        }
        logger.info { "VCSIM error simulation cleared" }
    }

    /**
     * Get list of VM IDs that were deleted (for verifying saga compensation).
     */
    public fun getDeletedVmIds(): List<String> = synchronized(deletedVmIds) { deletedVmIds.toList() }

    /**
     * Test connection to VCSIM.
     *
     * In VCSIM mode, this performs a simplified connection test that validates
     * basic connectivity and authentication.
     */
    override suspend fun testConnection(
        params: VcenterConnectionParams,
        password: String
    ): Result<de.acci.dvmm.application.vmware.ConnectionInfo, de.acci.dvmm.application.vmware.ConnectionError> = withContext(Dispatchers.IO) {
        logger.info {
            "Testing VCSIM connection: " +
                "url=${params.vcenterUrl}, " +
                "user=${params.username}, " +
                "datacenter=${params.datacenterName}"
        }

        try {
            // Validate URL format (already validated by VcenterConnectionParams init,
            // but checking again for defense in depth)
            if (!params.vcenterUrl.startsWith("https://")) {
                return@withContext de.acci.dvmm.application.vmware.ConnectionError.NetworkError(
                    message = "vCenter URL must start with https://"
                ).failure()
            }

            // Validate credentials are not empty
            if (params.username.isBlank() || password.isBlank()) {
                return@withContext de.acci.dvmm.application.vmware.ConnectionError.AuthenticationFailed(
                    message = "Username and password cannot be empty"
                ).failure()
            }

            // This is an intentional stub for unit/integration tests without real vCenter.
            // For tests against actual VCSIM containers, use VcenterAdapter with vcsim URL.
            logger.info { "VCSIM connection test successful (simulated)" }

            de.acci.dvmm.application.vmware.ConnectionInfo(
                vcenterVersion = "8.0.2 (VCSIM)",
                clusterName = params.clusterName,
                clusterHosts = 3, // VCSIM default
                datastoreFreeGb = 500L // Simulated value
            ).success()
        } catch (e: CancellationException) {
            throw e // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.error(e) { "VCSIM connection test failed" }
            de.acci.dvmm.application.vmware.ConnectionError.ApiError(
                message = "VCSIM connection failed: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun listDatacenters(): Result<List<Datacenter>, VsphereError> {
        return listOf(Datacenter("datacenter-1", "DC0")).success()
    }

    override suspend fun listClusters(datacenter: Datacenter): Result<List<Cluster>, VsphereError> {
        return listOf(Cluster("cluster-1", "DC0_C0")).success()
    }

    override suspend fun listDatastores(cluster: Cluster): Result<List<Datastore>, VsphereError> {
        return listOf(Datastore("datastore-1", "LocalDS_0")).success()
    }

    override suspend fun listNetworks(datacenter: Datacenter): Result<List<Network>, VsphereError> {
        return listOf(Network("network-1", "VM Network")).success()
    }

    override suspend fun listResourcePools(cluster: Cluster): Result<List<ResourcePool>, VsphereError> {
        return listOf(ResourcePool("rp-1", "Resources")).success()
    }

    override suspend fun createVm(
        spec: VmSpec,
        onProgress: suspend (VmProvisioningStage) -> Unit
    ): Result<VmProvisioningResult, VsphereError> {
        logger.info { "VCSIM simulating VM creation: ${spec.name}" }

        // Capture current error state atomically for this operation
        val currentError = simulatedError.get()
        val currentErrorAfterStage = errorAfterStage.get()

        // Check for immediate error (no partial creation)
        if (currentError != null && currentErrorAfterStage == null) {
            logger.info { "VCSIM returning simulated error (immediate): $currentError" }
            return currentError.failure()
        }

        // Helper to check if error should occur after this stage
        suspend fun checkError(stage: VmProvisioningStage) {
            if (currentErrorAfterStage == stage && currentError != null) {
                logger.info { "VCSIM simulating error after stage $stage: $currentError" }
                throw SimulatedVsphereException(currentError)
            }
        }

        onProgress(VmProvisioningStage.CLONING)
        delay(500)
        checkError(VmProvisioningStage.CLONING)

        onProgress(VmProvisioningStage.CONFIGURING)
        delay(200)
        checkError(VmProvisioningStage.CONFIGURING)

        onProgress(VmProvisioningStage.POWERING_ON)
        delay(200)
        checkError(VmProvisioningStage.POWERING_ON)

        onProgress(VmProvisioningStage.WAITING_FOR_NETWORK)
        delay(500)
        checkError(VmProvisioningStage.WAITING_FOR_NETWORK)

        onProgress(VmProvisioningStage.READY)

        return VmProvisioningResult(
            vmwareVmId = VmwareVmId.of("vm-100"),
            ipAddress = "192.168.1.100", // Simulated VCSIM IP
            hostname = spec.name,
            warningMessage = null
        ).success()
    }

    /**
     * Exception wrapper for simulated vSphere errors during saga compensation testing.
     *
     * This exception is thrown during staged error simulation (when [simulateError] is called
     * with a non-null `afterStage` parameter). It allows testing saga compensation patterns
     * where a VM is partially created before an error occurs.
     *
     * ## Usage in Tests
     *
     * ```kotlin
     * // Simulate error after CLONING stage (VM partially created)
     * vcsimAdapter.simulateError(
     *     error = VsphereError.Timeout("VMware Tools timeout"),
     *     afterStage = VmProvisioningStage.CLONING
     * )
     *
     * // createVm will throw SimulatedVsphereException after CLONING completes
     * // saga compensation should then delete the partially created VM
     * ```
     *
     * @property error The underlying [VsphereError] that caused the simulated failure
     * @see simulateError for configuring error simulation
     * @see clearSimulatedError for resetting simulation state
     */
    public class SimulatedVsphereException(public val error: VsphereError) : RuntimeException(error.message)

    override suspend fun getVm(vmId: VmId): Result<VmInfo, VsphereError> {
        logger.info { "VCSIM simulating getVm: ${vmId.value}" }
        return VmInfo(
            id = vmId.value,
            name = "simulated-vm",
            powerState = VmPowerState.POWERED_ON,
            ipAddress = "192.168.1.100",
            hostname = "simulated-vm.local",
            guestOs = "Ubuntu 22.04.3 LTS (64-bit)",
            bootTime = java.time.Instant.now().minusSeconds(86400 * 2 + 3600 * 4) // 2 days, 4 hours ago
        ).success()
    }

    override suspend fun deleteVm(vmId: VmId): Result<Unit, VsphereError> {
        logger.info { "VCSIM simulating VM deletion: ${vmId.value}" }
        deletedVmIds.add(vmId.value)
        return Unit.success()
    }
}
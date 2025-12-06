package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.Cluster
import de.acci.dvmm.application.vmware.Datacenter
import de.acci.dvmm.application.vmware.Datastore
import de.acci.dvmm.application.vmware.Network
import de.acci.dvmm.application.vmware.ResourcePool
import de.acci.dvmm.application.vmware.VmId
import de.acci.dvmm.application.vmware.VmInfo
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.domain.vmware.VcenterConnectionParams
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
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
public class VcsimAdapter : VspherePort {

    private val logger = KotlinLogging.logger {}

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

    override suspend fun createVm(spec: VmSpec): Result<VmId, VsphereError> {
        return VmId("vm-100").success()
    }

    override suspend fun getVm(vmId: VmId): Result<VmInfo, VsphereError> {
        return VmInfo(vmId.value, "simulated-vm").success()
    }

    override suspend fun deleteVm(vmId: VmId): Result<Unit, VsphereError> {
        return Unit.success()
    }
}

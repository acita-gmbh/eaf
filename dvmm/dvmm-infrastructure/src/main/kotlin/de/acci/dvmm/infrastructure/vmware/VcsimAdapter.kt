package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.ConnectionError
import de.acci.dvmm.application.vmware.ConnectionInfo
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
    ): Result<ConnectionInfo, ConnectionError> = withContext(Dispatchers.IO) {
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
                return@withContext ConnectionError.NetworkError(
                    message = "vCenter URL must start with https://"
                ).failure()
            }

            // Validate credentials are not empty
            if (params.username.isBlank() || password.isBlank()) {
                return@withContext ConnectionError.AuthenticationFailed(
                    message = "Username and password cannot be empty"
                ).failure()
            }

            // This is an intentional stub for unit/integration tests without real vCenter.
            // For tests against actual VCSIM containers, use VcenterAdapter with vcsim URL.
            logger.info { "VCSIM connection test successful (simulated)" }

            ConnectionInfo(
                vcenterVersion = "8.0.2 (VCSIM)",
                clusterName = params.clusterName,
                clusterHosts = 3, // VCSIM default
                datastoreFreeGb = 500L // Simulated value
            ).success()
        } catch (e: Exception) {
            logger.error(e) { "VCSIM connection test failed" }
            ConnectionError.ApiError(
                message = "VCSIM connection failed: ${e.message}",
                cause = e
            ).failure()
        }
    }
}

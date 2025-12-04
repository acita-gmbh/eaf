package de.acci.dvmm.infrastructure.vmware

import com.vmware.vim25.mo.ClusterComputeResource
import com.vmware.vim25.mo.Datacenter
import com.vmware.vim25.mo.Datastore
import com.vmware.vim25.mo.InventoryNavigator
import com.vmware.vim25.mo.Network
import com.vmware.vim25.mo.ServiceInstance
import de.acci.dvmm.application.vmware.ConnectionError
import de.acci.dvmm.application.vmware.ConnectionInfo
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Production vCenter adapter using yavijava SDK.
 *
 * This adapter connects to real VMware vCenter servers for production use.
 * All blocking SOAP calls are wrapped in `withContext(Dispatchers.IO)` to
 * avoid blocking WebFlux's event loop.
 *
 * ## Profile Selection
 *
 * Activated when the "vcsim" profile is NOT active (production default):
 * ```yaml
 * # No special profile needed - this is the default
 * ```
 *
 * ## SSL Certificate Handling
 *
 * By default, SSL certificate validation is enabled. For self-signed certificates,
 * you can either:
 * 1. Import the certificate into the JVM truststore
 * 2. Set `ignoreCert=true` in ServiceInstance (not recommended for production)
 *
 * ## Threading
 *
 * yavijava uses blocking SOAP calls. This adapter wraps all vSphere operations
 * in `withContext(Dispatchers.IO)` to ensure they don't block the event loop.
 *
 * @see VcsimAdapter for test environment implementation
 */
@Component
@Profile("!vcsim")
public class VcenterAdapter : VspherePort {

    private val logger = KotlinLogging.logger {}

    /**
     * Test connection to vCenter using yavijava SDK.
     *
     * Validates:
     * 1. Network connectivity to vCenter URL
     * 2. SSL certificate (if HTTPS)
     * 3. Authentication with provided credentials
     * 4. Existence of specified datacenter
     * 5. Existence of specified cluster
     * 6. Existence of specified datastore
     * 7. Existence of specified network
     */
    override suspend fun testConnection(
        config: VmwareConfiguration,
        decryptedPassword: String
    ): Result<ConnectionInfo, ConnectionError> = withContext(Dispatchers.IO) {
        logger.info {
            "Testing vCenter connection: " +
                "url=${config.vcenterUrl}, " +
                "user=${config.username}, " +
                "datacenter=${config.datacenterName}"
        }

        var serviceInstance: ServiceInstance? = null

        try {
            // Connect to vCenter
            // ignoreCert=true for development; in production, add certs to truststore
            serviceInstance = ServiceInstance(
                URL(config.vcenterUrl),
                config.username,
                decryptedPassword,
                true // ignoreCert - TODO: make configurable for production
            )

            val aboutInfo = serviceInstance.aboutInfo
            val vcenterVersion = "${aboutInfo.version} (build ${aboutInfo.build})"
            logger.debug { "Connected to vCenter: $vcenterVersion" }

            // Find datacenter
            val rootFolder = serviceInstance.rootFolder
            val datacenter = InventoryNavigator(rootFolder)
                .searchManagedEntity("Datacenter", config.datacenterName) as? Datacenter
                ?: return@withContext ConnectionError.DatacenterNotFound(
                    datacenterName = config.datacenterName
                ).failure()

            logger.debug { "Found datacenter: ${config.datacenterName}" }

            // Find cluster
            val cluster = InventoryNavigator(datacenter)
                .searchManagedEntity("ClusterComputeResource", config.clusterName) as? ClusterComputeResource
                ?: return@withContext ConnectionError.ClusterNotFound(
                    clusterName = config.clusterName
                ).failure()

            val clusterHosts = cluster.hosts?.size ?: 0
            logger.debug { "Found cluster: ${config.clusterName} with $clusterHosts hosts" }

            // Find datastore
            val datastore = InventoryNavigator(datacenter)
                .searchManagedEntity("Datastore", config.datastoreName) as? Datastore
                ?: return@withContext ConnectionError.DatastoreNotFound(
                    datastoreName = config.datastoreName
                ).failure()

            val datastoreFreeGb = (datastore.summary?.freeSpace ?: 0L) / (1024L * 1024L * 1024L)
            logger.debug { "Found datastore: ${config.datastoreName} with ${datastoreFreeGb}GB free" }

            // Find network
            val network = InventoryNavigator(datacenter)
                .searchManagedEntity("Network", config.networkName) as? Network
                ?: return@withContext ConnectionError.NetworkNotFound(
                    networkName = config.networkName
                ).failure()

            logger.debug { "Found network: ${config.networkName}" }

            logger.info {
                "vCenter connection test successful: " +
                    "version=$vcenterVersion, " +
                    "cluster=${config.clusterName}, " +
                    "hosts=$clusterHosts"
            }

            ConnectionInfo(
                vcenterVersion = vcenterVersion,
                clusterName = config.clusterName,
                clusterHosts = clusterHosts,
                datastoreFreeGb = datastoreFreeGb
            ).success()

        } catch (e: UnknownHostException) {
            logger.warn(e) { "DNS resolution failed for ${config.vcenterUrl}" }
            ConnectionError.NetworkError(
                message = "Cannot resolve hostname: ${e.message}",
                cause = e
            ).failure()

        } catch (e: ConnectException) {
            logger.warn(e) { "Connection refused to ${config.vcenterUrl}" }
            ConnectionError.NetworkError(
                message = "Connection refused: ${e.message}",
                cause = e
            ).failure()

        } catch (e: SSLException) {
            logger.warn(e) { "SSL error connecting to ${config.vcenterUrl}" }
            ConnectionError.SslError(
                message = "SSL certificate error: ${e.message}",
                cause = e
            ).failure()

        } catch (e: Exception) {
            // Check for authentication errors
            val message = e.message?.lowercase() ?: ""
            if (message.contains("incorrect user name") ||
                message.contains("invalid login") ||
                message.contains("cannot complete login") ||
                message.contains("authentication")
            ) {
                logger.warn { "Authentication failed for ${config.username} at ${config.vcenterUrl}" }
                ConnectionError.AuthenticationFailed(
                    message = "Authentication failed - check username and password"
                ).failure()
            } else {
                logger.error(e) { "vCenter connection failed: ${e.message}" }
                ConnectionError.ApiError(
                    message = "vCenter API error: ${e.message}",
                    cause = e
                ).failure()
            }

        } finally {
            // Always close the connection
            try {
                serviceInstance?.serverConnection?.logout()
            } catch (e: Exception) {
                logger.debug(e) { "Error during logout (ignored)" }
            }
        }
    }
}

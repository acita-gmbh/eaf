package de.acci.dvmm.infrastructure.vmware

import com.vmware.vim25.mo.ClusterComputeResource
import com.vmware.vim25.mo.Datacenter
import com.vmware.vim25.mo.Datastore
import com.vmware.vim25.mo.InventoryNavigator
import com.vmware.vim25.mo.ServiceInstance
import com.vmware.vim25.mo.VirtualMachine
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
import org.springframework.beans.factory.annotation.Value
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
 * ## Connection Timeouts
 *
 * yavijava uses Java's URLConnection internally, which respects JVM socket settings.
 * To configure connection timeouts, set JVM system properties:
 * ```
 * -Dsun.net.client.defaultConnectTimeout=30000  # 30 seconds connect timeout
 * -Dsun.net.client.defaultReadTimeout=60000     # 60 seconds read timeout
 * ```
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
public class VcenterAdapter(
    @Value("\${dvmm.vcenter.ignore-cert:false}")
    private val ignoreCert: Boolean = false
) : VspherePort {

    private val logger = KotlinLogging.logger {}

    init {
        if (ignoreCert) {
            logger.warn {
                "SSL certificate validation is DISABLED for vCenter connections. " +
                    "This is a security risk - use only for development/testing with self-signed certs. " +
                    "For production, import vCenter certificate into JVM truststore."
            }
        }
    }

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
     * 8. Existence of specified VM template
     */
    override suspend fun testConnection(
        params: VcenterConnectionParams,
        password: String
    ): Result<ConnectionInfo, ConnectionError> = withContext(Dispatchers.IO) {
        logger.info {
            "Testing vCenter connection: " +
                "url=${params.vcenterUrl}, " +
                "user=${params.username}, " +
                "datacenter=${params.datacenterName}"
        }

        var serviceInstance: ServiceInstance? = null

        try {
            // Connect to vCenter
            // Note: Connection timeouts are controlled by JVM socket settings.
            // For custom timeouts, configure: -Dsun.net.client.defaultConnectTimeout=30000
            // ignoreCert=false by default (secure); set dvmm.vcenter.ignore-cert=true for self-signed certs
            serviceInstance = ServiceInstance(
                URL(params.vcenterUrl),
                params.username,
                password,
                ignoreCert
            )

            val aboutInfo = serviceInstance.aboutInfo
            val vcenterVersion = "${aboutInfo.version} (build ${aboutInfo.build})"
            logger.debug { "Connected to vCenter: $vcenterVersion" }

            // Find datacenter
            val rootFolder = serviceInstance.rootFolder
            val datacenter = InventoryNavigator(rootFolder)
                .searchManagedEntity("Datacenter", params.datacenterName) as? Datacenter
                ?: return@withContext ConnectionError.DatacenterNotFound(
                    datacenterName = params.datacenterName
                ).failure()

            logger.debug { "Found datacenter: ${params.datacenterName}" }

            // Find cluster
            val cluster = InventoryNavigator(datacenter)
                .searchManagedEntity("ClusterComputeResource", params.clusterName) as? ClusterComputeResource
                ?: return@withContext ConnectionError.ClusterNotFound(
                    clusterName = params.clusterName
                ).failure()

            val clusterHosts = cluster.hosts?.size ?: 0
            logger.debug { "Found cluster: ${params.clusterName} with $clusterHosts hosts" }

            // Find datastore
            val datastore = InventoryNavigator(datacenter)
                .searchManagedEntity("Datastore", params.datastoreName) as? Datastore
                ?: return@withContext ConnectionError.DatastoreNotFound(
                    datastoreName = params.datastoreName
                ).failure()

            val datastoreFreeGb = (datastore.summary?.freeSpace ?: 0L) / (1024L * 1024L * 1024L)
            logger.debug { "Found datastore: ${params.datastoreName} with ${datastoreFreeGb}GB free" }

            // Verify network exists (result not stored - only existence check needed)
            if (InventoryNavigator(datacenter)
                    .searchManagedEntity("Network", params.networkName) == null
            ) {
                return@withContext ConnectionError.NetworkNotFound(
                    networkName = params.networkName
                ).failure()
            }

            logger.debug { "Found network: ${params.networkName}" }

            // Verify template exists (VirtualMachine with template flag)
            val template = InventoryNavigator(datacenter)
                .searchManagedEntity("VirtualMachine", params.templateName) as? VirtualMachine
            if (template == null || template.config?.template != true) {
                logger.warn {
                    "Template not found or not marked as template: ${params.templateName} " +
                        "(found=${template != null}, isTemplate=${template?.config?.template})"
                }
                return@withContext ConnectionError.TemplateNotFound(
                    templateName = params.templateName
                ).failure()
            }

            logger.debug { "Found template: ${params.templateName}" }

            logger.info {
                "vCenter connection test successful: " +
                    "version=$vcenterVersion, " +
                    "cluster=${params.clusterName}, " +
                    "hosts=$clusterHosts"
            }

            ConnectionInfo(
                vcenterVersion = vcenterVersion,
                clusterName = params.clusterName,
                clusterHosts = clusterHosts,
                datastoreFreeGb = datastoreFreeGb
            ).success()

        } catch (e: UnknownHostException) {
            logger.warn(e) { "DNS resolution failed for ${params.vcenterUrl}" }
            ConnectionError.NetworkError(
                message = "Cannot resolve hostname: ${e.message}",
                cause = e
            ).failure()

        } catch (e: ConnectException) {
            logger.warn(e) { "Connection refused to ${params.vcenterUrl}" }
            ConnectionError.NetworkError(
                message = "Connection refused: ${e.message}",
                cause = e
            ).failure()

        } catch (e: SSLException) {
            logger.warn(e) { "SSL error connecting to ${params.vcenterUrl}" }
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
                logger.warn { "Authentication failed for ${params.username} at ${params.vcenterUrl}" }
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

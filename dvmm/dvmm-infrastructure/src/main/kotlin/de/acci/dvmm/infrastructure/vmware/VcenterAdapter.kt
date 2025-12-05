package de.acci.dvmm.infrastructure.vmware

import com.vmware.sdk.vsphere.utils.VcenterClient
import com.vmware.sdk.vsphere.utils.VcenterClientFactory
import com.vmware.sdk.vsphere.utils.VimClient.getVimServiceInstanceRef
import com.vmware.vim25.InvalidLoginFaultMsg
import com.vmware.vim25.ManagedObjectReference
import com.vmware.vim25.ObjectSpec
import com.vmware.vim25.PropertyFilterSpec
import com.vmware.vim25.PropertySpec
import com.vmware.vim25.RetrieveOptions
import com.vmware.vim25.VimPortType
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
import java.net.URI
import java.net.UnknownHostException
import java.security.KeyStore
import javax.net.ssl.SSLException

/**
 * Production vCenter adapter using VCF SDK 9.0 (Official VMware SDK).
 *
 * This adapter connects to real VMware vCenter servers for production use.
 * All blocking SOAP calls are wrapped in `withContext(Dispatchers.IO)` to
 * avoid blocking WebFlux's event loop.
 *
 * ## SDK Migration (Story 3.1.1)
 *
 * Migrated from yavijava (deprecated, last release 2017) to VCF SDK 9.0:
 * - `com.vmware.sdk:vsphere-utils:9.0.0.0` from Maven Central
 * - Apache 2.0 license
 * - Official VMware SDK with vSphere 7.x/8.x SOAP API compatibility
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
 * set `dvmm.vcenter.ignore-cert=true` (not recommended for production).
 *
 * ## Threading
 *
 * VCF SDK uses blocking SOAP calls. This adapter wraps all vSphere operations
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
     * Test connection to vCenter using VCF SDK 9.0.
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

        var client: VcenterClient? = null

        try {
            // Extract hostname from URL for VcenterClientFactory
            val vcenterHost = URI(params.vcenterUrl).host
                ?: return@withContext ConnectionError.NetworkError(
                    message = "Invalid vCenter URL: '${params.vcenterUrl}' - failed to extract hostname. " +
                        "Expected format: 'https://<hostname>/sdk'"
                ).failure()

            // Create client factory with optional SSL bypass
            // An empty trust store is passed to VcenterClientFactory to disable SSL verification (for self-signed certs)
            val trustStore: KeyStore? = if (ignoreCert) KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
            } else null

            val factory = VcenterClientFactory(vcenterHost, trustStore)
            client = factory.createClient(params.username, password, null)

            val vimPort = client.vimPort
            val serviceContent = vimPort.retrieveServiceContent(getVimServiceInstanceRef())

            // Get vCenter version info
            val aboutInfo = serviceContent.about
            val vcenterVersion = "${aboutInfo.version} (build ${aboutInfo.build})"
            logger.debug { "Connected to vCenter: $vcenterVersion" }

            // Find datacenter using SearchIndex
            val searchIndex = serviceContent.searchIndex
            val datacenterPath = params.datacenterName
            val datacenterRef = vimPort.findByInventoryPath(searchIndex, datacenterPath)
                ?: return@withContext ConnectionError.DatacenterNotFound(
                    datacenterName = params.datacenterName
                ).failure()

            logger.debug { "Found datacenter: ${params.datacenterName}" }

            // Find cluster using SearchIndex (path: datacenter/host/clusterName)
            val clusterPath = "${params.datacenterName}/host/${params.clusterName}"
            val clusterRef = vimPort.findByInventoryPath(searchIndex, clusterPath)
                ?: return@withContext ConnectionError.ClusterNotFound(
                    clusterName = params.clusterName
                ).failure()

            // Get cluster host count using PropertyCollector
            val clusterHosts = getClusterHostCount(vimPort, serviceContent.propertyCollector, clusterRef)
            logger.debug { "Found cluster: ${params.clusterName} with $clusterHosts hosts" }

            // Find datastore using SearchIndex (path: datacenter/datastore/datastoreName)
            val datastorePath = "${params.datacenterName}/datastore/${params.datastoreName}"
            val datastoreRef = vimPort.findByInventoryPath(searchIndex, datastorePath)
                ?: return@withContext ConnectionError.DatastoreNotFound(
                    datastoreName = params.datastoreName
                ).failure()

            // Get datastore free space using PropertyCollector
            val datastoreFreeGb = getDatastoreFreeSpaceGb(vimPort, serviceContent.propertyCollector, datastoreRef)
            logger.debug { "Found datastore: ${params.datastoreName} with ${datastoreFreeGb}GB free" }

            // Find network using SearchIndex (path: datacenter/network/networkName)
            val networkPath = "${params.datacenterName}/network/${params.networkName}"
            if (vimPort.findByInventoryPath(searchIndex, networkPath) == null) {
                return@withContext ConnectionError.NetworkNotFound(
                    networkName = params.networkName
                ).failure()
            }

            logger.debug { "Found network: ${params.networkName}" }

            // Find template using SearchIndex (path: datacenter/vm/templateName)
            val templatePath = "${params.datacenterName}/vm/${params.templateName}"
            val templateRef = vimPort.findByInventoryPath(searchIndex, templatePath)
            if (templateRef == null) {
                return@withContext ConnectionError.TemplateNotFound(
                    templateName = params.templateName
                ).failure()
            }

            // Verify it's actually a template (config.template == true)
            val isTemplate = isVmTemplate(vimPort, serviceContent.propertyCollector, templateRef)
            if (!isTemplate) {
                logger.warn {
                    "VM found but not marked as template: ${params.templateName}"
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

        } catch (e: InvalidLoginFaultMsg) {
            logger.warn(e) { "Authentication failed for ${params.username} at ${params.vcenterUrl}" }
            ConnectionError.AuthenticationFailed(
                message = "Authentication failed - check username and password"
            ).failure()

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
            // Check for authentication errors in exception message
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
                client?.close()
            } catch (e: Exception) {
                logger.debug(e) { "Error during logout (ignored)" }
            }
        }
    }

    /**
     * Get the number of hosts in a cluster using PropertyCollector.
     */
    private fun getClusterHostCount(
        vimPort: VimPortType,
        propertyCollector: ManagedObjectReference,
        clusterRef: ManagedObjectReference
    ): Int {
        val propSpec = PropertySpec().apply {
            type = "ClusterComputeResource"
            pathSet.add("host")
        }

        val objSpec = ObjectSpec().apply {
            obj = clusterRef
            isSkip = false
        }

        val filterSpec = PropertyFilterSpec().apply {
            propSet.add(propSpec)
            objectSet.add(objSpec)
        }

        val result = vimPort.retrievePropertiesEx(
            propertyCollector,
            listOf(filterSpec),
            RetrieveOptions()
        )

        return result?.objects?.firstOrNull()
            ?.propSet
            ?.firstOrNull { it.name == "host" }
            ?.let { prop ->
                @Suppress("UNCHECKED_CAST")
                (prop.`val` as? List<ManagedObjectReference>)?.size ?: 0
            } ?: 0
    }

    /**
     * Get datastore free space in GB using PropertyCollector.
     */
    private fun getDatastoreFreeSpaceGb(
        vimPort: VimPortType,
        propertyCollector: ManagedObjectReference,
        datastoreRef: ManagedObjectReference
    ): Long {
        val propSpec = PropertySpec().apply {
            type = "Datastore"
            pathSet.add("summary.freeSpace")
        }

        val objSpec = ObjectSpec().apply {
            obj = datastoreRef
            isSkip = false
        }

        val filterSpec = PropertyFilterSpec().apply {
            propSet.add(propSpec)
            objectSet.add(objSpec)
        }

        val result = vimPort.retrievePropertiesEx(
            propertyCollector,
            listOf(filterSpec),
            RetrieveOptions()
        )

        val freeSpaceBytes = result?.objects?.firstOrNull()
            ?.propSet
            ?.firstOrNull { it.name == "summary.freeSpace" }
            ?.`val` as? Long ?: 0L

        return freeSpaceBytes / (1024L * 1024L * 1024L)
    }

    /**
     * Check if a VirtualMachine is marked as a template using PropertyCollector.
     */
    private fun isVmTemplate(
        vimPort: VimPortType,
        propertyCollector: ManagedObjectReference,
        vmRef: ManagedObjectReference
    ): Boolean {
        val propSpec = PropertySpec().apply {
            type = "VirtualMachine"
            pathSet.add("config.template")
        }

        val objSpec = ObjectSpec().apply {
            obj = vmRef
            isSkip = false
        }

        val filterSpec = PropertyFilterSpec().apply {
            propSet.add(propSpec)
            objectSet.add(objSpec)
        }

        val result = vimPort.retrievePropertiesEx(
            propertyCollector,
            listOf(filterSpec),
            RetrieveOptions()
        )

        return result?.objects?.firstOrNull()
            ?.propSet
            ?.firstOrNull { it.name == "config.template" }
            ?.`val` as? Boolean ?: false
    }
}

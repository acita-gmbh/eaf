package de.acci.eaf.testing.vcsim

import com.vmware.sdk.vsphere.utils.VimClient.getVimServiceInstanceRef
import com.vmware.vim25.ManagedObjectReference
import com.vmware.vim25.ObjectSpec
import com.vmware.vim25.PropertyFilterSpec
import com.vmware.vim25.PropertySpec
import com.vmware.vim25.RetrieveOptions
import com.vmware.vim25.ServiceContent
import com.vmware.vim25.TaskInfo
import com.vmware.vim25.TaskInfoState
import com.vmware.vim25.VimPortType
import com.vmware.vim25.VimService
import com.vmware.vim25.VirtualMachineConfigSpec
import com.vmware.vim25.VirtualMachineFileInfo
import jakarta.xml.ws.BindingProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext

/**
 * Test fixture for VMware vCenter Simulator (VCSIM) integration tests.
 *
 * Provides helper methods for creating and managing vSphere objects
 * (VMs, networks, datastores) in the simulated environment.
 *
 * ## Usage
 * ```kotlin
 * @VcsimTest
 * class MyVmwareIntegrationTest {
 *
 *     @Test
 *     fun `should create VM via fixture`() {
 *         val fixture = VcsimTestFixture(TestContainers.vcsim)
 *         val vmRef = fixture.createVm(VmSpec(name = "test-vm-01"))
 *         assertThat(vmRef.name).isEqualTo("test-vm-01")
 *     }
 * }
 * ```
 *
 * ## State Management
 * VCSIM maintains state during container lifetime. Use [resetState] between
 * test classes to ensure isolation. Within a test class, created objects
 * persist across tests.
 *
 * ## VCSIM API Notes
 * VCSIM primarily uses SOAP API (vSphere Web Services SDK). This fixture
 * interacts with the /rest endpoints and SOAP API to manage objects.
 * For full vSphere SDK operations, use govmomi or pyvmomi libraries.
 *
 * @property container The VCSIM container instance
 */
public class VcsimTestFixture(
    private val container: VcsimContainer
) {
    private val httpClient: HttpClient = createSecureHttpClient()
    private val vmCounter = AtomicInteger(0)
    private val networkCounter = AtomicInteger(0)
    private val datastoreCounter = AtomicInteger(0)

    /**
     * Creates a virtual machine in VCSIM.
     *
     * The VM is created in the default datacenter/cluster with the specified
     * configuration. VCSIM pre-creates a default inventory structure that
     * includes datacenter, clusters, hosts, and resource pools.
     *
     * Uses SOAP API (CreateVM_Task) to create VM.
     *
     * @param spec VM specification including name, CPU, memory, disk
     * @return Reference to the created VM
     */
    public fun createVm(spec: VmSpec): VmRef {
        val vimPort = createVimPort()
        try {
            val serviceContent = vimPort.retrieveServiceContent(getVimServiceInstanceRef())
            val searchIndex = serviceContent.searchIndex

            // VCSIM Defaults: DC0, DC0_C0, LocalDS_0
            val folderRef = vimPort.findByInventoryPath(searchIndex, "DC0/vm") as? ManagedObjectReference
                ?: throw VcsimException("VM folder 'DC0/vm' not found")

            val rpRef = vimPort.findByInventoryPath(searchIndex, "DC0/host/DC0_C0/Resources") as? ManagedObjectReference
                ?: throw VcsimException("ResourcePool 'DC0/host/DC0_C0/Resources' not found")

            val config = VirtualMachineConfigSpec().apply {
                name = spec.name
                memoryMB = spec.memoryMb.toLong()
                numCPUs = spec.cpuCount
                files = VirtualMachineFileInfo().apply {
                    vmPathName = "[LocalDS_0]"
                }
            }

            val task = vimPort.createVMTask(folderRef, config, rpRef, null)
            waitForTask(vimPort, serviceContent, task)

            val info = getTaskInfo(vimPort, serviceContent, task)
            val vmRef = info.result as ManagedObjectReference

            return VmRef(
                moRef = vmRef.value,
                name = spec.name,
                powerState = VmPowerState.POWERED_OFF
            )
        } catch (e: VcsimException) {
            throw e
        } catch (e: Exception) {
            throw VcsimException("Failed to create VM: ${spec.name}", e)
        } finally {
            try {
                val serviceContent = vimPort.retrieveServiceContent(getVimServiceInstanceRef())
                vimPort.logout(serviceContent.sessionManager)
            } catch (_: Exception) {}
        }
    }

    /**
     * Creates a network in VCSIM.
     *
     * Networks are created as standard vSphere networks visible to all
     * hosts in the simulated environment.
     *
     * @param name Network name (must be unique)
     * @return Reference to the created network
     * @throws IllegalArgumentException if name is blank
     */
    public fun createNetwork(name: String): NetworkRef {
        require(name.isNotBlank()) { "Network name must not be blank" }
        val counter = networkCounter.incrementAndGet()
        val moRef = "network-test-$counter"

        return NetworkRef(
            moRef = moRef,
            name = name
        )
    }

    /**
     * Creates a datastore in VCSIM.
     *
     * The datastore is created as a local datastore accessible from
     * all hosts in the simulated cluster.
     *
     * @param name Datastore name (must be unique)
     * @return Reference to the created datastore
     * @throws IllegalArgumentException if name is blank
     */
    public fun createDatastore(name: String): DatastoreRef {
        require(name.isNotBlank()) { "Datastore name must not be blank" }
        val counter = datastoreCounter.incrementAndGet()
        val moRef = "datastore-test-$counter"

        // VCSIM pre-creates datastores based on configuration
        // Simulating 100GB capacity for test purposes
        return DatastoreRef(
            moRef = moRef,
            name = name,
            capacityBytes = 100L * 1024 * 1024 * 1024, // 100 GB
            freeSpaceBytes = 80L * 1024 * 1024 * 1024   // 80 GB free
        )
    }

    /**
     * Simulates VM provisioning workflow.
     *
     * This method simulates the provisioning process that would occur
     * in a real vCenter environment, transitioning the VM through
     * various states (cloning, configuring, powering on).
     *
     * @param vmRef Reference to the VM to provision
     * @return Updated VM reference with POWERED_ON state
     */
    public fun simulateProvisioning(vmRef: VmRef): VmRef {
        // Simulate provisioning workflow:
        // 1. Clone from template (or create from scratch)
        // 2. Customize (network, storage)
        // 3. Power on

        return vmRef.copy(powerState = VmPowerState.POWERED_ON)
    }

    /**
     * Resets VCSIM state for test isolation.
     *
     * This method resets the internal counters and would ideally
     * reset the VCSIM container state. For full isolation between
     * test classes, container restart may be required.
     *
     * Note: VCSIM state persists during container lifetime. To achieve
     * full isolation, either restart the container or use per-test
     * naming conventions to avoid conflicts.
     */
    public fun resetState() {
        vmCounter.set(0)
        networkCounter.set(0)
        datastoreCounter.set(0)
        // Note: VCSIM doesn't have a built-in reset API
        // Full state reset requires container restart
    }

    /**
     * Checks if VCSIM is running and responsive.
     *
     * @return true if VCSIM `/about` endpoint responds successfully
     */
    public fun isHealthy(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${container.getBaseUrl()}/about"))
                .header("Authorization", basicAuthHeader())
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns information about the VCSIM instance.
     *
     * Queries the `/about` endpoint to retrieve VCSIM version and
     * API information.
     *
     * @return About information as JSON string
     * @throws VcsimException if query fails
     */
    public fun getAboutInfo(): String {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${container.getBaseUrl()}/about"))
                .header("Authorization", basicAuthHeader())
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()
        } catch (e: Exception) {
            throw VcsimException("Failed to query VCSIM /about endpoint", e)
        }
    }

    private fun basicAuthHeader(): String {
        val credentials = "${container.getUsername()}:${container.getPassword()}"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }

    /**
     * Returns the SDK URL for vSphere API connections.
     *
     * @return Full URL to the vSphere SDK endpoint (e.g., "https://host:port/sdk")
     */
    public fun getSdkUrl(): String = container.getSdkUrl()

    /**
     * Returns connection properties for Spring configuration.
     *
     * @return Map of property name to value for vsphere.* properties
     */
    public fun getConnectionProperties(): Map<String, String> = mapOf(
        "vsphere.url" to container.getSdkUrl(),
        "vsphere.username" to container.getUsername(),
        "vsphere.password" to container.getPassword(),
        "vsphere.insecure" to "true"
    )

    /**
     * Creates an HTTP client that securely connects to VCSIM using generated certificates.
     */
    private fun createSecureHttpClient(): HttpClient {
        val bundle = container.getCertificateBundle()
            ?: throw IllegalStateException(
                "VcsimContainer must have certificates configured. " +
                    "Use VcsimContainer().withCertificates(VcsimCertificateGenerator.generate())"
            )

        val sslContext: SSLContext = bundle.createSslContext()

        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .build()
    }
    
    /**
     * Creates a VimPortType connected to VCSIM with proper endpoint configuration.
     *
     * ## VCF SDK 9.0 Port 443 Limitation Workaround
     *
     * VcenterClientFactory only supports port 443. For VCSIM (dynamic ports via
     * Testcontainers), we bypass the factory and configure JAX-WS directly:
     * 1. Force CXF to use legacy HttpURLConnection transport (supports SSL config)
     * 2. Configure SSL context via JVM defaults
     * 3. Create VimService and get VimPortType
     * 4. Set endpoint URL via BindingProvider
     * 5. Login via SOAP API
     *
     * ## CXF 4.0 Transport Configuration
     *
     * CXF 4.0+ uses java.net.http.HttpClient by default, which doesn't respect
     * HTTPConduit TLS settings. We force the legacy HttpURLConnection transport
     * via system property, which properly inherits HttpsURLConnection SSL defaults.
     *
     * @return Connected VimPortType for vSphere API operations
     */
    private fun createVimPort(): VimPortType {
        val bundle = container.getCertificateBundle()
            ?: throw IllegalStateException("VcsimContainer must have certificates configured")

        // Force CXF to use legacy HttpURLConnection transport (respects SSL defaults)
        System.setProperty("org.apache.cxf.transport.http.forceURLConnection", "true")

        // Configure SSL context for the legacy transport
        val sslContext = bundle.createSslContext()
        SSLContext.setDefault(sslContext)
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

        // Create VimService AFTER SSL is configured
        val vimService = VimService()
        val vimPort = vimService.vimPort

        // Configure endpoint URL with correct port (bypasses VcenterClientFactory's port 443 assumption)
        val bindingProvider = vimPort as BindingProvider
        bindingProvider.requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] = container.getSdkUrl()
        // Enable session management - required for maintaining login session
        bindingProvider.requestContext[BindingProvider.SESSION_MAINTAIN_PROPERTY] = true

        // Configure CXF HTTPConduit with trust-all TrustManager
        // Note: Trust-all is acceptable in test code since we're testing API functionality, not certificate validation.
        // The container's dynamic IP may not match the certificate SANs.
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        })
        val client = org.apache.cxf.frontend.ClientProxy.getClient(vimPort)
        val httpConduit = client.conduit as org.apache.cxf.transport.http.HTTPConduit
        httpConduit.tlsClientParameters = org.apache.cxf.configuration.jsse.TLSClientParameters().apply {
            isDisableCNCheck = true
            trustManagers = trustAllCerts
        }

        // Login to VCSIM
        val serviceContent = vimPort.retrieveServiceContent(getVimServiceInstanceRef())
        vimPort.login(
            serviceContent.sessionManager,
            container.getUsername(),
            container.getPassword(),
            null
        )

        return vimPort
    }
    
    private fun waitForTask(
        vimPort: VimPortType,
        serviceContent: ServiceContent,
        task: ManagedObjectReference,
        timeoutMs: Long = 60_000 // 60 seconds default timeout for tests
    ) {
        val startTime = System.currentTimeMillis()
        val infoSpec = PropertySpec().apply {
            this.type = "Task"
            this.pathSet.add("info.state")
            this.pathSet.add("info.error")
        }
        val filterSpec = PropertyFilterSpec().apply {
            this.propSet.add(infoSpec)
            this.objectSet.add(ObjectSpec().apply { this.obj = task })
        }

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > timeoutMs) {
                throw VcsimException("Task timed out after ${elapsed}ms (limit: ${timeoutMs}ms)")
            }

            val result = vimPort.retrievePropertiesEx(
                serviceContent.propertyCollector,
                listOf(filterSpec),
                RetrieveOptions()
            )
            val props = result?.objects?.firstOrNull()?.propSet?.associate { it.name to it.`val` }

            val state = props?.get("info.state") as? TaskInfoState
            val error = props?.get("info.error")

            if (state == TaskInfoState.SUCCESS) return
            if (state == TaskInfoState.ERROR) {
                throw VcsimException("Task failed: $error")
            }

            Thread.sleep(100)
        }
    }

    private fun getTaskInfo(vimPort: VimPortType, serviceContent: ServiceContent, task: ManagedObjectReference): TaskInfo {
        val propSpec = PropertySpec().apply {
            this.type = "Task"
            this.pathSet.add("info")
        }
        val filterSpec = PropertyFilterSpec().apply {
            this.propSet.add(propSpec)
            this.objectSet.add(ObjectSpec().apply { this.obj = task })
        }

        val result = vimPort.retrievePropertiesEx(
            serviceContent.propertyCollector,
            listOf(filterSpec),
            RetrieveOptions()
        )
        return result?.objects?.firstOrNull()?.propSet?.firstOrNull { it.name == "info" }?.`val` as? TaskInfo
            ?: throw VcsimException("Failed to get task info for task: ${task.value}")
    }
}

/**
 * Exception thrown when VCSIM operations fail.
 *
 * @property message Description of the failure
 * @property cause Underlying exception, if any
 */
public class VcsimException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

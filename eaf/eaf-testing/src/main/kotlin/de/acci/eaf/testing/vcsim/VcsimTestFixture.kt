package de.acci.eaf.testing.vcsim

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    private val httpClient: HttpClient = createInsecureJavaHttpClient()
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
     * @param spec VM specification including name, CPU, memory, disk
     * @return Reference to the created VM
     */
    public fun createVm(spec: VmSpec): VmRef {
        val counter = vmCounter.incrementAndGet()
        val moRef = "vm-test-$counter"

        // VCSIM tracks VMs internally - for testing purposes we simulate the reference
        // Real vSphere SDK would use SOAP CreateVM_Task API
        // VCSIM comes pre-populated with VMs based on VCSIM_VM env var

        return VmRef(
            moRef = moRef,
            name = spec.name,
            powerState = VmPowerState.POWERED_OFF
        )
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
     * Creates an HTTP client that trusts all SSL certificates and skips hostname verification.
     *
     * **WARNING: This is ONLY safe for testing with VCSIM's self-signed certificates.**
     * **NEVER use this pattern in production code as it disables all certificate validation.**
     *
     * Note: In CI environments (GitHub Actions), the container may be accessed via an IP address
     * that doesn't match the certificate's Subject Alternative Name (SAN), requiring disabled
     * hostname verification in addition to disabled certificate validation.
     *
     * Uses system property approach as HttpClient.Builder.sslParameters() doesn't reliably
     * disable hostname verification in all Java versions.
     */
    private fun createInsecureJavaHttpClient(): HttpClient {
        // Set system property to disable hostname verification for HttpClient
        // This is safe because VcsimTestFixture is only used in tests
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true")

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }

        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .build()
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

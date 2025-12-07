package integration.vmware

import de.acci.dvmm.application.vmware.CredentialEncryptor
import de.acci.dvmm.application.vmware.NoOpCredentialEncryptor
import de.acci.dvmm.application.vmware.VmId
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.dvmm.infrastructure.vmware.VsphereClient
import de.acci.dvmm.infrastructure.vmware.VsphereSessionManager
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.tenant.TenantContextElement
import de.acci.eaf.testing.vcsim.VcsimContainer
import de.acci.eaf.testing.vcsim.VcsimTestFixture
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import javax.net.ssl.SSLContext

/**
 * Integration tests for VsphereClient using VCSIM (vCenter Simulator).
 *
 * These tests run on both AMD64 and ARM64 architectures:
 * - **AMD64**: Uses official `vmware/vcsim` image
 * - **ARM64 (Apple Silicon)**: Builds native VCSIM image from govmomi source
 *
 * First run on ARM64 takes ~60s to build the image, subsequent runs use cached image.
 *
 * @see VcsimContainer.create for architecture detection logic
 */
@Testcontainers
class VsphereClientIntegrationTest {

    companion object {
        // Set CXF property at class load time, before any CXF classes are initialized
        init {
            System.setProperty("org.apache.cxf.transport.http.forceURLConnection", "true")
        }

        @Container
        val vcsim = VcsimContainer.create()
            .withCertificates(de.acci.eaf.testing.vcsim.VcsimCertificateGenerator.generate())

        /**
         * Configure SSL context for VCSIM tests using generated certificates.
         *
         * Uses the certificate bundle's trust managers for proper certificate validation.
         * The CXF property is set in init{} to ensure it's applied before CXF classes load.
         */
        @JvmStatic
        @BeforeAll
        fun setupSsl() {
            val bundle = vcsim.getCertificateBundle()
                ?: throw IllegalStateException("VcsimContainer must have certificates configured")

            val sslContext = bundle.createSslContext()
            SSLContext.setDefault(sslContext)
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            // Disable hostname verification - container IP may not match certificate SAN
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        }
    }

    private val fixture = VcsimTestFixture(vcsim)
    private val sessionManager = VsphereSessionManager()
    private val configPort = mockk<VmwareConfigurationPort>()
    private val encryptor = NoOpCredentialEncryptor
    // VCSIM tests use ignoreCert=true since we're testing API functionality, not certificate validation
    private val client = VsphereClient(sessionManager, configPort, encryptor, ignoreCert = true)
    private val tenantId = TenantId(UUID.randomUUID())

    @Test
    @Disabled("VCSIM doesn't support VMware Tools IP detection - test times out waiting for IP")
    fun `should create and list VM`() = runBlocking(TenantContextElement(tenantId)) {
        // 1. Setup Config
        val config = VmwareConfiguration.create(
            tenantId = tenantId,
            vcenterUrl = vcsim.getSdkUrl(),
            username = vcsim.getUsername(),
            passwordEncrypted = encryptor.encrypt(vcsim.getPassword()),
            datacenterName = "DC0",
            clusterName = "DC0_C0",
            datastoreName = "LocalDS_0",
            networkName = "VM Network",
            templateName = "ubuntu-template",
            folderPath = null,
            userId = UserId(UUID.randomUUID()),
            timestamp = Instant.now()
        )
        coEvery { configPort.findByTenantId(tenantId) } returns config

        // 2. Prepare VCSIM
        // Create template VM
        fixture.createVm(de.acci.eaf.testing.vcsim.VmSpec("ubuntu-template"))
        
        // 3. Execute VsphereClient.createVm
        val spec = VmSpec(
            name = "test-client-vm",
            template = "ubuntu-template",
            cpu = 2,
            memoryGb = 4
        )
        
        val result = client.createVm(spec)

        assertTrue(result is Result.Success, "Create VM failed: ${if(result is Result.Failure) result.error else ""}")
        val provisioningResult = (result as Result.Success).value

        // 4. Verify
        val vmId = VmId(provisioningResult.vmwareVmId.value)
        val getResult = client.getVm(vmId)
        assertTrue(getResult is Result.Success)
        assertEquals("test-client-vm", (getResult as Result.Success).value.name)
    }
    
    @Test
    fun `should list datacenters`() = runBlocking(TenantContextElement(tenantId)) {
        // Setup Config (reuse)
        val config = VmwareConfiguration.create(
            tenantId = tenantId,
            vcenterUrl = vcsim.getSdkUrl(),
            username = vcsim.getUsername(),
            passwordEncrypted = encryptor.encrypt(vcsim.getPassword()),
            datacenterName = "DC0",
            clusterName = "DC0_C0",
            datastoreName = "LocalDS_0",
            networkName = "VM Network",
            userId = UserId(UUID.randomUUID()),
            timestamp = Instant.now()
        )
        coEvery { configPort.findByTenantId(tenantId) } returns config

        val listResult = client.listDatacenters()
        assertTrue(listResult is Result.Success, "Expected Success but got: $listResult")
        val dcs = (listResult as Result.Success).value
        assertTrue(dcs.any { it.name == "DC0" })
    }

    @Test
    fun `should list clusters in datacenter`() = runBlocking(TenantContextElement(tenantId)) {
        val config = createTestConfig()
        coEvery { configPort.findByTenantId(tenantId) } returns config

        // First get datacenter
        val dcResult = client.listDatacenters()
        assertTrue(dcResult is Result.Success, "Failed to list datacenters")
        val dc0 = (dcResult as Result.Success).value.first { it.name == "DC0" }

        // Then list clusters
        val clusterResult = client.listClusters(dc0)
        assertTrue(clusterResult is Result.Success, "Failed to list clusters: ${if (clusterResult is Result.Failure) clusterResult.error else ""}")
        val clusters = (clusterResult as Result.Success).value
        assertTrue(clusters.isNotEmpty(), "Expected at least one cluster")
        assertTrue(clusters.any { it.name.contains("DC0") }, "Expected cluster with DC0 in name")
    }

    @Test
    fun `should list datastores in cluster`() = runBlocking(TenantContextElement(tenantId)) {
        val config = createTestConfig()
        coEvery { configPort.findByTenantId(tenantId) } returns config

        // Get datacenter and cluster
        val dcResult = client.listDatacenters()
        assertTrue(dcResult is Result.Success)
        val dc0 = (dcResult as Result.Success).value.first { it.name == "DC0" }

        val clusterResult = client.listClusters(dc0)
        assertTrue(clusterResult is Result.Success)
        val cluster = (clusterResult as Result.Success).value.first()

        // List datastores via cluster
        // Note: VCSIM may not populate cluster.datastore property - success with empty list is acceptable
        val dsResult = client.listDatastores(cluster)
        assertTrue(dsResult is Result.Success, "Failed to list datastores: ${if (dsResult is Result.Failure) dsResult.error else ""}")
        // In real vSphere, clusters aggregate host datastores; VCSIM may return empty at cluster level
        // The important test is that the API call succeeds without error
    }

    @Test
    fun `should list networks in datacenter`() = runBlocking(TenantContextElement(tenantId)) {
        val config = createTestConfig()
        coEvery { configPort.findByTenantId(tenantId) } returns config

        // Get datacenter
        val dcResult = client.listDatacenters()
        assertTrue(dcResult is Result.Success)
        val dc0 = (dcResult as Result.Success).value.first { it.name == "DC0" }

        // List networks
        val networkResult = client.listNetworks(dc0)
        assertTrue(networkResult is Result.Success, "Failed to list networks: ${if (networkResult is Result.Failure) networkResult.error else ""}")
        val networks = (networkResult as Result.Success).value
        assertTrue(networks.isNotEmpty(), "Expected at least one network")
    }

    @Test
    fun `should list resource pools in cluster`() = runBlocking(TenantContextElement(tenantId)) {
        val config = createTestConfig()
        coEvery { configPort.findByTenantId(tenantId) } returns config

        // Get datacenter and cluster
        val dcResult = client.listDatacenters()
        assertTrue(dcResult is Result.Success)
        val dc0 = (dcResult as Result.Success).value.first { it.name == "DC0" }

        val clusterResult = client.listClusters(dc0)
        assertTrue(clusterResult is Result.Success)
        val cluster = (clusterResult as Result.Success).value.first()

        // List resource pools
        val rpResult = client.listResourcePools(cluster)
        assertTrue(rpResult is Result.Success, "Failed to list resource pools: ${if (rpResult is Result.Failure) rpResult.error else ""}")
        val pools = (rpResult as Result.Success).value
        assertTrue(pools.isNotEmpty(), "Expected at least one resource pool")
    }

    @Test
    @Disabled("VCSIM doesn't support VMware Tools IP detection - test times out waiting for IP")
    fun `should delete VM`() = runBlocking(TenantContextElement(tenantId)) {
        val config = createTestConfig()
        coEvery { configPort.findByTenantId(tenantId) } returns config

        // Create a template for cloning
        fixture.createVm(de.acci.eaf.testing.vcsim.VmSpec("delete-test-template"))

        // Create VM via client
        val spec = VmSpec(
            name = "vm-to-delete",
            template = "delete-test-template",
            cpu = 1,
            memoryGb = 1
        )
        val createResult = client.createVm(spec)
        assertTrue(createResult is Result.Success, "Failed to create VM for deletion test")
        val provisioningResult = (createResult as Result.Success).value
        val vmId = VmId(provisioningResult.vmwareVmId.value)

        // Verify VM exists
        val getBeforeResult = client.getVm(vmId)
        assertTrue(getBeforeResult is Result.Success, "VM should exist before deletion")

        // Delete VM
        val deleteResult = client.deleteVm(vmId)
        assertTrue(deleteResult is Result.Success, "Failed to delete VM: ${if (deleteResult is Result.Failure) deleteResult.error else ""}")

        // Verify VM no longer exists (getVm should fail with NotFound)
        val getAfterResult = client.getVm(vmId)
        assertTrue(getAfterResult is Result.Failure, "VM should not exist after deletion")
    }

    private fun createTestConfig(): VmwareConfiguration {
        return VmwareConfiguration.create(
            tenantId = tenantId,
            vcenterUrl = vcsim.getSdkUrl(),
            username = vcsim.getUsername(),
            passwordEncrypted = encryptor.encrypt(vcsim.getPassword()),
            datacenterName = "DC0",
            clusterName = "DC0_C0",
            datastoreName = "LocalDS_0",
            networkName = "VM Network",
            templateName = "ubuntu-template",
            folderPath = null,
            userId = UserId(UUID.randomUUID()),
            timestamp = Instant.now()
        )
    }
}

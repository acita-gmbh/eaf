package de.acci.dcm.infrastructure.vmware

import de.acci.dcm.application.vmware.Cluster
import de.acci.dcm.application.vmware.Datacenter
import de.acci.dcm.application.vmware.Datastore
import de.acci.dcm.application.vmware.Network
import de.acci.dcm.application.vmware.ResourcePool
import de.acci.dcm.application.vmware.VmId
import de.acci.dcm.application.vmware.VmInfo
import de.acci.dcm.application.vmware.VmSpec
import de.acci.dcm.application.vmware.VsphereError
import de.acci.dcm.application.vmware.HypervisorPort
import de.acci.dcm.domain.vm.VmProvisioningResult
import de.acci.dcm.domain.vm.VmwareVmId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [VcenterAdapter] delegation to [VsphereClient].
 *
 * These tests verify that the adapter correctly delegates all HypervisorPort
 * methods to the underlying VsphereClient implementation.
 */
class VcenterAdapterTest {

    private val vsphereClient = mockk<VsphereClient>()
    private val adapter: HypervisorPort = VcenterAdapter(vsphereClient = vsphereClient)

    // ==========================================
    // Interface Implementation
    // ==========================================

    @Test
    fun `adapter implements HypervisorPort`() {
        assertTrue(adapter is HypervisorPort)
    }

    // ==========================================
    // Delegation Tests
    // ==========================================

    @Test
    fun `listDatacenters delegates to vsphereClient`() = runTest {
        val expected = listOf(
            Datacenter(id = "dc-1", name = "DC0"),
            Datacenter(id = "dc-2", name = "DC1")
        )
        coEvery { vsphereClient.listDatacenters() } returns expected.success()

        val result = adapter.listDatacenters()

        assertTrue(result is Result.Success)
        assertEquals(expected, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.listDatacenters() }
    }

    @Test
    fun `listClusters delegates to vsphereClient with datacenter parameter`() = runTest {
        val datacenter = Datacenter(id = "dc-1", name = "DC0")
        val expected = listOf(
            Cluster(id = "cluster-1", name = "Cluster0"),
            Cluster(id = "cluster-2", name = "Cluster1")
        )
        coEvery { vsphereClient.listClusters(datacenter) } returns expected.success()

        val result = adapter.listClusters(datacenter)

        assertTrue(result is Result.Success)
        assertEquals(expected, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.listClusters(datacenter) }
    }

    @Test
    fun `listDatastores delegates to vsphereClient with cluster parameter`() = runTest {
        val cluster = Cluster(id = "cluster-1", name = "Cluster0")
        val expected = listOf(
            Datastore(id = "ds-1", name = "LocalDS_0"),
            Datastore(id = "ds-2", name = "LocalDS_1")
        )
        coEvery { vsphereClient.listDatastores(cluster) } returns expected.success()

        val result = adapter.listDatastores(cluster)

        assertTrue(result is Result.Success)
        assertEquals(expected, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.listDatastores(cluster) }
    }

    @Test
    fun `listNetworks delegates to vsphereClient with datacenter parameter`() = runTest {
        val datacenter = Datacenter(id = "dc-1", name = "DC0")
        val expected = listOf(
            Network(id = "net-1", name = "VM Network"),
            Network(id = "net-2", name = "Management")
        )
        coEvery { vsphereClient.listNetworks(datacenter) } returns expected.success()

        val result = adapter.listNetworks(datacenter)

        assertTrue(result is Result.Success)
        assertEquals(expected, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.listNetworks(datacenter) }
    }

    @Test
    fun `listResourcePools delegates to vsphereClient with cluster parameter`() = runTest {
        val cluster = Cluster(id = "cluster-1", name = "Cluster0")
        val expected = listOf(
            ResourcePool(id = "rp-1", name = "Resources"),
            ResourcePool(id = "rp-2", name = "Production")
        )
        coEvery { vsphereClient.listResourcePools(cluster) } returns expected.success()

        val result = adapter.listResourcePools(cluster)

        assertTrue(result is Result.Success)
        assertEquals(expected, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.listResourcePools(cluster) }
    }

    @Test
    fun `createVm delegates to vsphereClient with spec parameter`() = runTest {
        val spec = VmSpec(
            name = "test-vm",
            template = "ubuntu-template",
            cpu = 4,
            memoryGb = 16
        )
        val expectedResult = VmProvisioningResult(
            vmwareVmId = VmwareVmId.of("vm-123"),
            ipAddress = "192.168.1.100",
            hostname = "test-vm",
            warningMessage = null
        )
        coEvery { vsphereClient.createVm(spec, any()) } returns expectedResult.success()

        val result = adapter.createVm(spec) {}

        assertTrue(result is Result.Success)
        assertEquals(expectedResult, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.createVm(spec, any()) }
    }

    @Test
    fun `getVm delegates to vsphereClient with vmId parameter`() = runTest {
        val vmId = VmId("vm-123")
        val expected = VmInfo(id = "vm-123", name = "test-vm")
        coEvery { vsphereClient.getVm(vmId) } returns expected.success()

        val result = adapter.getVm(vmId)

        assertTrue(result is Result.Success)
        assertEquals(expected, (result as Result.Success).value)
        coVerify(exactly = 1) { vsphereClient.getVm(vmId) }
    }

    @Test
    fun `deleteVm delegates to vsphereClient with vmId parameter`() = runTest {
        val vmId = VmId("vm-123")
        coEvery { vsphereClient.deleteVm(vmId) } returns Unit.success()

        val result = adapter.deleteVm(vmId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { vsphereClient.deleteVm(vmId) }
    }

    // ==========================================
    // Error Propagation Tests
    // ==========================================

    @Test
    fun `listDatacenters propagates errors from vsphereClient`() = runTest {
        val expectedError = VsphereError.ConnectionError("Connection failed")
        coEvery { vsphereClient.listDatacenters() } returns Result.Failure(expectedError)

        val result = adapter.listDatacenters()

        assertTrue(result is Result.Failure)
        assertEquals(expectedError, (result as Result.Failure).error)
    }

    @Test
    fun `createVm propagates errors from vsphereClient`() = runTest {
        val spec = VmSpec(
            name = "test-vm",
            template = "ubuntu-template",
            cpu = 4,
            memoryGb = 16
        )
        val expectedError = VsphereError.ProvisioningError("VM creation failed")
        coEvery { vsphereClient.createVm(spec, any()) } returns Result.Failure(expectedError)

        val result = adapter.createVm(spec) {}

        assertTrue(result is Result.Failure)
        assertEquals(expectedError, (result as Result.Failure).error)
    }

    @Test
    fun `deleteVm propagates errors from vsphereClient`() = runTest {
        val vmId = VmId("vm-123")
        val expectedError = VsphereError.DeletionError("VM not found")
        coEvery { vsphereClient.deleteVm(vmId) } returns Result.Failure(expectedError)

        val result = adapter.deleteVm(vmId)

        assertTrue(result is Result.Failure)
        assertEquals(expectedError, (result as Result.Failure).error)
    }
}

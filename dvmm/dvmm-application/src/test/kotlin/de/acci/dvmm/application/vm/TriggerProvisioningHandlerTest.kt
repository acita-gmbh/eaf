package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmId as VsphereVmId
import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.dvmm.domain.vmware.VmwareConfigurationId
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TriggerProvisioningHandlerTest {

    private val vspherePort = mockk<VspherePort>()
    private val configPort = mockk<VmwareConfigurationPort>()

    @Test
    fun `onVmProvisioningStarted should call VspherePort createVm`() = runTest {
        // Given
        val aggregateId = VmId.generate()
        val requestId = VmRequestId.generate()
        val tenantId = TenantId.generate()
        val userId = UserId.generate()
        val projectId = ProjectId.generate()
        val vmName = VmName.of("web-server")
        val size = VmSize.M
        val correlationId = CorrelationId.generate()

        val event = VmProvisioningStarted(
            aggregateId = aggregateId,
            requestId = requestId,
            projectId = projectId,
            vmName = vmName,
            size = size,
            requesterId = userId,
            metadata = EventMetadata.create(tenantId, userId, correlationId)
        )

        val config = VmwareConfiguration.create(
            tenantId = tenantId,
            vcenterUrl = "https://vcenter.example.com",
            username = "user",
            passwordEncrypted = ByteArray(0),
            datacenterName = "DC1",
            clusterName = "Cluster1",
            datastoreName = "DS1",
            networkName = "VM Network",
            templateName = "Ubuntu-Template",
            folderPath = null,
            userId = userId,
            timestamp = Instant.now()
        )

        coEvery { configPort.findByTenantId(tenantId) } returns config

        val specSlot = slot<VmSpec>()
        coEvery { vspherePort.createVm(capture(specSlot)) } returns VsphereVmId("vm-123").success()

        val handler = TriggerProvisioningHandler(vspherePort, configPort)

        // When
        handler.onVmProvisioningStarted(event)

        // Then
        coVerify(exactly = 1) { vspherePort.createVm(any()) }
        val spec = specSlot.captured
        assertEquals(vmName.value, spec.name)
        assertEquals(config.templateName, spec.template)
        assertEquals(size.cpuCores, spec.cpu)
        assertEquals(size.memoryGb, spec.memoryGb)
    }
}

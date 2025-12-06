package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.application.vmware.VmId as VsphereVmId
import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("TriggerProvisioningHandler")
class TriggerProvisioningHandlerTest {

    private val vspherePort = mockk<VspherePort>()
    private val configPort = mockk<VmwareConfigurationPort>()
    private val eventStore = mockk<EventStore>()

    private fun createHandler() = TriggerProvisioningHandler(
        vspherePort = vspherePort,
        configPort = configPort,
        eventStore = eventStore
    )

    private fun createEvent(
        tenantId: TenantId = TenantId.generate(),
        userId: UserId = UserId.generate()
    ): VmProvisioningStarted = VmProvisioningStarted(
        aggregateId = VmId.generate(),
        requestId = VmRequestId.generate(),
        projectId = ProjectId.generate(),
        vmName = VmName.of("web-server"),
        size = VmSize.M,
        requesterId = userId,
        metadata = EventMetadata.create(tenantId, userId, CorrelationId.generate())
    )

    private fun createConfig(tenantId: TenantId, userId: UserId): VmwareConfiguration =
        VmwareConfiguration.create(
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

    @Nested
    @DisplayName("successful provisioning")
    inner class SuccessfulProvisioning {

        @Test
        fun `should call VspherePort createVm with correct spec`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)

            coEvery { configPort.findByTenantId(tenantId) } returns config

            val specSlot = slot<VmSpec>()
            coEvery { vspherePort.createVm(capture(specSlot)) } returns VsphereVmId("vm-123").success()

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 1) { vspherePort.createVm(any()) }
            val spec = specSlot.captured
            assertEquals(event.vmName.value, spec.name)
            assertEquals(config.templateName, spec.template)
            assertEquals(event.size.cpuCores, spec.cpu)
            assertEquals(event.size.memoryGb, spec.memoryGb)
        }

        @Test
        fun `should not emit failure event on success`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vspherePort.createVm(any()) } returns VsphereVmId("vm-123").success()

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("missing configuration")
    inner class MissingConfiguration {

        @Test
        fun `should emit VmProvisioningFailed when config is missing`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)

            coEvery { configPort.findByTenantId(tenantId) } returns null

            // Mock event store load to return 1 event (VmProvisioningStarted)
            coEvery { eventStore.load(event.aggregateId.value) } returns listOf(
                de.acci.eaf.eventsourcing.StoredEvent(
                    id = java.util.UUID.randomUUID(),
                    aggregateId = event.aggregateId.value,
                    aggregateType = "Vm",
                    eventType = "VmProvisioningStarted",
                    payload = "{}",
                    metadata = event.metadata,
                    version = 1,
                    createdAt = java.time.Instant.now()
                )
            )

            val eventsSlot = slot<List<DomainEvent>>()
            coEvery {
                eventStore.append(
                    aggregateId = event.aggregateId.value,
                    events = capture(eventsSlot),
                    expectedVersion = 1L
                )
            } returns 2L.success()

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 0) { vspherePort.createVm(any()) }
            coVerify(exactly = 1) { eventStore.append(any(), any(), any()) }

            val failedEvent = eventsSlot.captured.single() as VmProvisioningFailed
            assertEquals(event.aggregateId, failedEvent.aggregateId)
            assertEquals(event.requestId, failedEvent.requestId)
            assertTrue(failedEvent.reason.contains("VMware configuration missing"))
        }
    }

    @Nested
    @DisplayName("vSphere failure")
    inner class VsphereFailure {

        @Test
        fun `should emit VmProvisioningFailed when vSphere call fails`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vspherePort.createVm(any()) } returns de.acci.eaf.core.result.Result.Failure(
                VsphereError.Timeout("Connection timeout")
            )

            // Mock event store load to return 1 event (VmProvisioningStarted)
            coEvery { eventStore.load(event.aggregateId.value) } returns listOf(
                de.acci.eaf.eventsourcing.StoredEvent(
                    id = java.util.UUID.randomUUID(),
                    aggregateId = event.aggregateId.value,
                    aggregateType = "Vm",
                    eventType = "VmProvisioningStarted",
                    payload = "{}",
                    metadata = event.metadata,
                    version = 1,
                    createdAt = java.time.Instant.now()
                )
            )

            val eventsSlot = slot<List<DomainEvent>>()
            coEvery {
                eventStore.append(
                    aggregateId = event.aggregateId.value,
                    events = capture(eventsSlot),
                    expectedVersion = 1L
                )
            } returns 2L.success()

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 1) { vspherePort.createVm(any()) }
            coVerify(exactly = 1) { eventStore.append(any(), any(), any()) }

            val failedEvent = eventsSlot.captured.single() as VmProvisioningFailed
            assertEquals(event.aggregateId, failedEvent.aggregateId)
            assertEquals(event.requestId, failedEvent.requestId)
            assertTrue(failedEvent.reason.contains("vSphere provisioning failed"))
        }
    }
}

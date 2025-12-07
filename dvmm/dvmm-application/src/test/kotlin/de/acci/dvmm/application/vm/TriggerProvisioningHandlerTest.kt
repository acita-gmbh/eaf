package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmrequest.NewTimelineEvent
import de.acci.dvmm.application.vmrequest.TimelineEventProjectionUpdater
import de.acci.dvmm.application.vmrequest.TimelineEventType
import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.application.vmrequest.VmRequestSummary
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vm.events.VmProvisioned
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmrequest.events.VmRequestReady
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.StoredEvent
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
import java.util.UUID

@DisplayName("TriggerProvisioningHandler")
class TriggerProvisioningHandlerTest {

    private val vspherePort = mockk<VspherePort>()
    private val configPort = mockk<VmwareConfigurationPort>()
    private val eventStore = mockk<EventStore>()
    private val vmEventDeserializer = mockk<VmEventDeserializer>()
    private val vmRequestEventDeserializer = mockk<VmRequestEventDeserializer>()
    private val timelineUpdater = mockk<TimelineEventProjectionUpdater>()
    private val vmRequestReadRepository = mockk<VmRequestReadRepository>()

    private fun createHandler() = TriggerProvisioningHandler(
        vspherePort = vspherePort,
        configPort = configPort,
        eventStore = eventStore,
        vmEventDeserializer = vmEventDeserializer,
        vmRequestEventDeserializer = vmRequestEventDeserializer,
        timelineUpdater = timelineUpdater,
        vmRequestReadRepository = vmRequestReadRepository
    )

    private fun createProvisioningResult(
        vmwareVmId: VmwareVmId = VmwareVmId.of("vm-123"),
        ipAddress: String? = "192.168.1.100",
        hostname: String = "web-server",
        warningMessage: String? = null
    ): VmProvisioningResult = VmProvisioningResult(
        vmwareVmId = vmwareVmId,
        ipAddress = ipAddress,
        hostname = hostname,
        warningMessage = warningMessage
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

    private fun createVmRequestSummary(
        id: VmRequestId,
        projectName: String = "My Project"
    ): VmRequestSummary = VmRequestSummary(
        id = id,
        tenantId = TenantId.generate(),
        requesterId = UserId.generate(),
        requesterName = "User",
        projectId = ProjectId.generate(),
        projectName = projectName,
        vmName = "web-server",
        size = VmSize.M,
        justification = "Test",
        status = VmRequestStatus.APPROVED,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Nested
    @DisplayName("successful provisioning")
    inner class SuccessfulProvisioning {

        @Test
        fun `should call VspherePort createVm with correct spec including project prefix`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)
            val provisioningResult = createProvisioningResult()
            val projectInfo = createVmRequestSummary(event.requestId, "My Project")

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns projectInfo

            val specSlot = slot<VmSpec>()
            coEvery { vspherePort.createVm(capture(specSlot)) } returns provisioningResult.success()

            // Mock event store loads for success path
            setupSuccessPathMocks(event)

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 1) { vspherePort.createVm(any()) }
            val spec = specSlot.captured
            // "My Project" -> "MYPR" -> "MYPR-web-server"
            assertEquals("MYPR-${event.vmName.value}", spec.name)
            assertEquals(config.templateName, spec.template)
            assertEquals(event.size.cpuCores, spec.cpu)
            assertEquals(event.size.memoryGb, spec.memoryGb)
        }

        @Test
        fun `should emit VmProvisioned and VmRequestReady events on success`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)
            val provisioningResult = createProvisioningResult()
            val projectInfo = createVmRequestSummary(event.requestId, "My Project")

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns projectInfo
            coEvery { vspherePort.createVm(any()) } returns provisioningResult.success()

            val vmEventsSlot = slot<List<DomainEvent>>()
            val requestEventsSlot = slot<List<DomainEvent>>()

            // Mock VM aggregate load and append
            val vmStoredEvent = createStoredEvent(event.aggregateId.value, "VmProvisioningStarted")
            coEvery { eventStore.load(event.aggregateId.value) } returns listOf(vmStoredEvent)
            coEvery { vmEventDeserializer.deserialize(vmStoredEvent) } returns event
            coEvery {
                eventStore.append(
                    aggregateId = event.aggregateId.value,
                    events = capture(vmEventsSlot),
                    expectedVersion = 1L
                )
            } returns 2L.success()

            // Mock VmRequest aggregate load and append
            val requestStoredEvent = createStoredEvent(event.requestId.value, "VmRequestProvisioningStarted")
            val provisioningStartedEvent = createVmRequestProvisioningStartedEvent(event.requestId, tenantId, userId)
            coEvery { eventStore.load(event.requestId.value) } returns listOf(requestStoredEvent)
            coEvery { vmRequestEventDeserializer.deserialize(requestStoredEvent) } returns provisioningStartedEvent
            coEvery {
                eventStore.append(
                    aggregateId = event.requestId.value,
                    events = capture(requestEventsSlot),
                    expectedVersion = 1L
                )
            } returns 2L.success()

            // Mock timeline updater
            coEvery { timelineUpdater.addTimelineEvent(any()) } returns Unit.success()

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            // Verify VM aggregate was updated
            coVerify(exactly = 1) { eventStore.append(event.aggregateId.value, any(), 1L) }
            val vmProvisionedEvent = vmEventsSlot.captured.single() as VmProvisioned
            assertEquals(event.aggregateId, vmProvisionedEvent.aggregateId)
            assertEquals(provisioningResult.vmwareVmId, vmProvisionedEvent.vmwareVmId)
            assertEquals(provisioningResult.ipAddress, vmProvisionedEvent.ipAddress)
            // Expect prefixed hostname
            assertEquals("MYPR-web-server", vmProvisionedEvent.hostname)

            // Verify VmRequest aggregate was updated
            coVerify(exactly = 1) { eventStore.append(event.requestId.value, any(), 1L) }
            val vmReadyEvent = requestEventsSlot.captured.single() as VmRequestReady
            assertEquals(event.requestId, vmReadyEvent.aggregateId)
            assertEquals(provisioningResult.vmwareVmId, vmReadyEvent.vmwareVmId)
            assertEquals("MYPR-web-server", vmReadyEvent.hostname)
        }

        @Test
        fun `should add VM_READY timeline event on success`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)
            val provisioningResult = createProvisioningResult()
            val projectInfo = createVmRequestSummary(event.requestId, "My Project")

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns projectInfo
            coEvery { vspherePort.createVm(any()) } returns provisioningResult.success()
            setupSuccessPathMocks(event)

            val timelineSlot = slot<NewTimelineEvent>()
            coEvery { timelineUpdater.addTimelineEvent(capture(timelineSlot)) } returns Unit.success()

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 1) { timelineUpdater.addTimelineEvent(any()) }
            val timelineEvent = timelineSlot.captured
            assertEquals(TimelineEventType.VM_READY, timelineEvent.eventType)
            assertEquals(event.requestId, timelineEvent.requestId)
            assertEquals(tenantId, timelineEvent.tenantId)
            assertTrue(timelineEvent.details!!.contains(provisioningResult.vmwareVmId.value))
            assertTrue(timelineEvent.details!!.contains("MYPR-web-server"))
        }

        private fun setupSuccessPathMocks(event: VmProvisioningStarted) {
            // Mock VM aggregate load and append
            val vmStoredEvent = createStoredEvent(event.aggregateId.value, "VmProvisioningStarted")
            coEvery { eventStore.load(event.aggregateId.value) } returns listOf(vmStoredEvent)
            coEvery { vmEventDeserializer.deserialize(vmStoredEvent) } returns event
            coEvery {
                eventStore.append(event.aggregateId.value, any(), any())
            } returns 2L.success()

            // Mock VmRequest aggregate load and append
            val requestStoredEvent = createStoredEvent(event.requestId.value, "VmRequestProvisioningStarted")
            val provisioningStartedEvent = createVmRequestProvisioningStartedEvent(
                event.requestId,
                event.metadata.tenantId,
                event.requesterId
            )
            coEvery { eventStore.load(event.requestId.value) } returns listOf(requestStoredEvent)
            coEvery { vmRequestEventDeserializer.deserialize(requestStoredEvent) } returns provisioningStartedEvent
            coEvery {
                eventStore.append(event.requestId.value, any(), any())
            } returns 2L.success()

            // Mock timeline updater
            coEvery { timelineUpdater.addTimelineEvent(any()) } returns Unit.success()
        }
    }

    private fun createStoredEvent(aggregateId: UUID, eventType: String): StoredEvent = StoredEvent(
        id = UUID.randomUUID(),
        aggregateId = aggregateId,
        aggregateType = if (eventType.startsWith("Vm") && !eventType.startsWith("VmRequest")) "Vm" else "VmRequest",
        eventType = eventType,
        payload = "{}",
        metadata = EventMetadata.create(TenantId.generate(), UserId.generate(), CorrelationId.generate()),
        version = 1,
        createdAt = Instant.now()
    )

    private fun createVmRequestProvisioningStartedEvent(
        requestId: VmRequestId,
        tenantId: TenantId,
        userId: UserId
    ): de.acci.dvmm.domain.vmrequest.events.VmRequestProvisioningStarted =
        de.acci.dvmm.domain.vmrequest.events.VmRequestProvisioningStarted(
            aggregateId = requestId,
            metadata = EventMetadata.create(tenantId, userId, CorrelationId.generate())
        )

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
    @DisplayName("missing project info")
    inner class MissingProjectInfo {

        @Test
        fun `should emit VmProvisioningFailed when project info is missing`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns null

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
            assertTrue(failedEvent.reason.contains("Could not find project info"))
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
            val projectInfo = createVmRequestSummary(event.requestId, "My Project")

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns projectInfo
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

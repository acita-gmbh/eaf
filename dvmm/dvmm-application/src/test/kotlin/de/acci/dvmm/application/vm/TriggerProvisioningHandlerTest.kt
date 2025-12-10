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
import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningProgressUpdated
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("TriggerProvisioningHandler")
class TriggerProvisioningHandlerTest {

    private val hypervisorPort = mockk<HypervisorPort>()
    private val configPort = mockk<VmwareConfigurationPort>()
    private val eventStore = mockk<EventStore>()
    private val vmEventDeserializer = mockk<VmEventDeserializer>()
    private val vmRequestEventDeserializer = mockk<VmRequestEventDeserializer>()
    private val timelineUpdater = mockk<TimelineEventProjectionUpdater>()
    private val vmRequestReadRepository = mockk<VmRequestReadRepository>()
    private val progressRepository = mockk<VmProvisioningProgressProjectionRepository>(relaxed = true)

    private fun createHandler() = TriggerProvisioningHandler(
        hypervisorPort = hypervisorPort,
        configPort = configPort,
        eventStore = eventStore,
        vmEventDeserializer = vmEventDeserializer,
        vmRequestEventDeserializer = vmRequestEventDeserializer,
        timelineUpdater = timelineUpdater,
        vmRequestReadRepository = vmRequestReadRepository,
        progressRepository = progressRepository
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
        fun `should call HypervisorPort createVm with correct spec including project prefix`() = runTest {
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
            coEvery { hypervisorPort.createVm(capture(specSlot), any()) } returns provisioningResult.success()

            // Mock event store loads for success path
            setupSuccessPathMocks(event)

            val handler = createHandler()

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
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
            coEvery { hypervisorPort.createVm(any(), any()) } returns provisioningResult.success()

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
            coEvery { hypervisorPort.createVm(any(), any()) } returns provisioningResult.success()
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
            coVerify(exactly = 0) { hypervisorPort.createVm(any(), any()) }
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
            coVerify(exactly = 0) { hypervisorPort.createVm(any(), any()) }
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
            coEvery { hypervisorPort.createVm(any(), any()) } returns de.acci.eaf.core.result.Result.Failure(
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
            coVerify(exactly = 1) { hypervisorPort.createVm(any(), any()) }
            coVerify(exactly = 1) { eventStore.append(any(), any(), any()) }

            val failedEvent = eventsSlot.captured.single() as VmProvisioningFailed
            assertEquals(event.aggregateId, failedEvent.aggregateId)
            assertEquals(event.requestId, failedEvent.requestId)
            assertTrue(failedEvent.reason.contains("vSphere provisioning failed"))
        }
    }

    @Nested
    @DisplayName("progress tracking")
    inner class ProgressTracking {

        /**
         * Verifies that the handler correctly accumulates stage timestamps when
         * updating progress. Each call to emitProgress should:
         * 1. Load existing projection to get previously accumulated timestamps
         * 2. Add the current stage timestamp to the accumulated map
         * 3. Save with the merged timestamps
         */
        @Test
        fun `should accumulate stage timestamps when updating progress`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)
            val projectInfo = createVmRequestSummary(event.requestId, "My Project")

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns projectInfo

            coEvery { hypervisorPort.createVm(any(), any()) } coAnswers {
                val progressCallback = secondArg<suspend (VmProvisioningStage) -> Unit>()
                // Simulate vSphere reporting progress through stages
                progressCallback(VmProvisioningStage.CLONING)
                progressCallback(VmProvisioningStage.CONFIGURING)
                createProvisioningResult().success()
            }

            // Set up non-relaxed progress repository to track calls
            val nonRelaxedProgressRepository = mockk<VmProvisioningProgressProjectionRepository>()

            // Capture saved projections to verify accumulation
            val savedProjections = mutableListOf<VmProvisioningProgressProjection>()

            // First call (CLONING) - no existing projection
            coEvery {
                nonRelaxedProgressRepository.findByVmRequestId(event.requestId, tenantId)
            } returnsMany listOf(
                null, // First call: no existing projection
                VmProvisioningProgressProjection( // Second call: return what was saved in first call
                    vmRequestId = event.requestId,
                    stage = VmProvisioningStage.CLONING,
                    details = "Provisioning stage: cloning",
                    startedAt = Instant.now(),
                    updatedAt = Instant.now(),
                    stageTimestamps = mapOf(VmProvisioningStage.CLONING to Instant.now()),
                    estimatedRemainingSeconds = 80L
                )
            )

            coEvery { nonRelaxedProgressRepository.save(capture(savedProjections), tenantId) } returns Unit
            coEvery { nonRelaxedProgressRepository.delete(event.requestId, tenantId) } returns Unit

            // Mock event store for VM aggregate
            val vmStoredEvent = createStoredEvent(event.aggregateId.value, "VmProvisioningStarted")
            coEvery { eventStore.load(event.aggregateId.value) } returns listOf(vmStoredEvent)
            coEvery { vmEventDeserializer.deserialize(vmStoredEvent) } returns event
            coEvery { eventStore.append(event.aggregateId.value, any(), any()) } returns 2L.success()

            // Mock VmRequest aggregate
            val requestStoredEvent = createStoredEvent(event.requestId.value, "VmRequestProvisioningStarted")
            val provisioningStartedEvent = createVmRequestProvisioningStartedEvent(event.requestId, tenantId, userId)
            coEvery { eventStore.load(event.requestId.value) } returns listOf(requestStoredEvent)
            coEvery { vmRequestEventDeserializer.deserialize(requestStoredEvent) } returns provisioningStartedEvent
            coEvery { eventStore.append(event.requestId.value, any(), any()) } returns 2L.success()

            // Mock timeline updater
            coEvery { timelineUpdater.addTimelineEvent(any()) } returns Unit.success()

            // Create handler with non-relaxed progress repository
            val handler = TriggerProvisioningHandler(
                hypervisorPort = hypervisorPort,
                configPort = configPort,
                eventStore = eventStore,
                vmEventDeserializer = vmEventDeserializer,
                vmRequestEventDeserializer = vmRequestEventDeserializer,
                timelineUpdater = timelineUpdater,
                vmRequestReadRepository = vmRequestReadRepository,
                progressRepository = nonRelaxedProgressRepository
            )

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            // Verify findByVmRequestId was called to load existing projection
            coVerify(atLeast = 2) { nonRelaxedProgressRepository.findByVmRequestId(event.requestId, tenantId) }

            // Verify save was called for each progress update
            coVerify(atLeast = 2) { nonRelaxedProgressRepository.save(any(), tenantId) }

            // Verify first save has CLONING stage
            assertTrue(savedProjections.isNotEmpty(), "Should have saved at least one projection")
            assertEquals(VmProvisioningStage.CLONING, savedProjections[0].stage)
            assertTrue(savedProjections[0].stageTimestamps.containsKey(VmProvisioningStage.CLONING))

            // Verify second save has CONFIGURING stage AND accumulated CLONING timestamp
            if (savedProjections.size >= 2) {
                assertEquals(VmProvisioningStage.CONFIGURING, savedProjections[1].stage)
                assertTrue(
                    savedProjections[1].stageTimestamps.containsKey(VmProvisioningStage.CLONING),
                    "Second save should preserve CLONING timestamp from previous save"
                )
                assertTrue(
                    savedProjections[1].stageTimestamps.containsKey(VmProvisioningStage.CONFIGURING),
                    "Second save should add CONFIGURING timestamp"
                )
            }

            // Verify cleanup was called after successful provisioning
            coVerify { nonRelaxedProgressRepository.delete(event.requestId, tenantId) }
        }

        @Test
        fun `should calculate estimated remaining time based on current stage`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val event = createEvent(tenantId, userId)
            val config = createConfig(tenantId, userId)
            val projectInfo = createVmRequestSummary(event.requestId, "My Project")

            coEvery { configPort.findByTenantId(tenantId) } returns config
            coEvery { vmRequestReadRepository.findById(event.requestId) } returns projectInfo

            // Track the projection saved for CLONING stage
            val savedProjection = slot<VmProvisioningProgressProjection>()
            val nonRelaxedProgressRepository = mockk<VmProvisioningProgressProjectionRepository>()
            coEvery { nonRelaxedProgressRepository.findByVmRequestId(any(), any()) } returns null
            coEvery { nonRelaxedProgressRepository.save(capture(savedProjection), any()) } returns Unit
            coEvery { nonRelaxedProgressRepository.delete(any(), any()) } returns Unit

            // Only trigger one progress callback to capture
            coEvery { hypervisorPort.createVm(any(), any()) } coAnswers {
                val progressCallback = secondArg<suspend (VmProvisioningStage) -> Unit>()
                progressCallback(VmProvisioningStage.CLONING)
                createProvisioningResult().success()
            }

            // Mock event store
            val vmStoredEvent = createStoredEvent(event.aggregateId.value, "VmProvisioningStarted")
            coEvery { eventStore.load(event.aggregateId.value) } returns listOf(vmStoredEvent)
            coEvery { vmEventDeserializer.deserialize(vmStoredEvent) } returns event
            coEvery { eventStore.append(event.aggregateId.value, any(), any()) } returns 2L.success()

            val requestStoredEvent = createStoredEvent(event.requestId.value, "VmRequestProvisioningStarted")
            val provisioningStartedEvent = createVmRequestProvisioningStartedEvent(event.requestId, tenantId, userId)
            coEvery { eventStore.load(event.requestId.value) } returns listOf(requestStoredEvent)
            coEvery { vmRequestEventDeserializer.deserialize(requestStoredEvent) } returns provisioningStartedEvent
            coEvery { eventStore.append(event.requestId.value, any(), any()) } returns 2L.success()
            coEvery { timelineUpdater.addTimelineEvent(any()) } returns Unit.success()

            val handler = TriggerProvisioningHandler(
                hypervisorPort = hypervisorPort,
                configPort = configPort,
                eventStore = eventStore,
                vmEventDeserializer = vmEventDeserializer,
                vmRequestEventDeserializer = vmRequestEventDeserializer,
                timelineUpdater = timelineUpdater,
                vmRequestReadRepository = vmRequestReadRepository,
                progressRepository = nonRelaxedProgressRepository
            )

            // When
            handler.onVmProvisioningStarted(event)

            // Then
            assertTrue(savedProjection.isCaptured, "Should have saved a projection")
            assertNotNull(savedProjection.captured.estimatedRemainingSeconds)

            // CLONING stage: remaining = CONFIGURING(15) + POWERING_ON(20) + WAITING_FOR_NETWORK(45) + READY(0) = 80
            val expectedRemaining = VmProvisioningProgressProjection.calculateEstimatedRemaining(VmProvisioningStage.CLONING)
            assertEquals(expectedRemaining, savedProjection.captured.estimatedRemainingSeconds)
            assertEquals(80L, expectedRemaining, "ETA after CLONING should be ~80 seconds")
        }
    }
}

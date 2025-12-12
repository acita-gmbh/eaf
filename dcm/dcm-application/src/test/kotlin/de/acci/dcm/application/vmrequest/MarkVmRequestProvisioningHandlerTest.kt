package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestAggregate
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.dcm.domain.vmrequest.events.VmRequestApproved
import de.acci.dcm.domain.vmrequest.events.VmRequestCreated
import de.acci.dcm.domain.vmrequest.events.VmRequestProvisioningStarted
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Instant
import java.util.UUID

class MarkVmRequestProvisioningHandlerTest {

    private val eventStore = mockk<EventStore>()
    private val deserializer = mockk<VmRequestEventDeserializer>()
    private val timelineUpdater = mockk<TimelineEventProjectionUpdater>(relaxed = true)

    private fun createStoredEvent(payload: DomainEvent): StoredEvent {
        return StoredEvent(
            id = UUID.randomUUID(),
            aggregateId = UUID.randomUUID(),
            aggregateType = "VmRequest",
            eventType = "EventType",
            payload = "{}",
            metadata = payload.metadata,
            version = 1,
            createdAt = Instant.now()
        )
    }

    @Test
    fun `should mark request as provisioning`() = runTest {
        // Given
        val requestId = VmRequestId.generate()
        val tenantId = TenantId.generate()
        val userId = UserId.generate()
        val correlationId = CorrelationId.generate()

        val createdEvent = VmRequestCreated(
            aggregateId = requestId,
            projectId = ProjectId.generate(),
            vmName = VmName.of("test-vm"),
            size = VmSize.S,
            justification = "Justification",
            requesterEmail = "user@example.com",
            metadata = EventMetadata.create(tenantId, userId)
        )
        val approvedEvent = VmRequestApproved(
            aggregateId = requestId,
            vmName = VmName.of("test-vm"),
            projectId = ProjectId.generate(),
            requesterId = userId,
            requesterEmail = "user@example.com",
            metadata = EventMetadata.create(tenantId, UserId.generate())
        )

        val storedEvent1 = createStoredEvent(createdEvent)
        val storedEvent2 = createStoredEvent(approvedEvent)

        coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent1, storedEvent2)
        every { deserializer.deserialize(storedEvent1) } returns createdEvent
        every { deserializer.deserialize(storedEvent2) } returns approvedEvent

        val eventsSlot = slot<List<DomainEvent>>()
        coEvery {
            eventStore.append(any(), capture(eventsSlot), any())
        } returns 3L.success()

        val handler = MarkVmRequestProvisioningHandler(eventStore, deserializer, timelineUpdater)
        val command = MarkVmRequestProvisioningCommand(requestId, tenantId, userId)

        // When
        val result = handler.handle(command, correlationId)

        // Then
        if (result is Result.Failure) {
            fail("Handler failed: ${result.error}")
        }
        
        val events = eventsSlot.captured
        assertEquals(1, events.size)
        val event = events[0] as VmRequestProvisioningStarted
        assertEquals(requestId, event.aggregateId)
        assertEquals(correlationId, event.metadata.correlationId)
        
        coVerify(exactly = 1) {
            // expectedVersion should be 2 (created + approved)
            eventStore.append(requestId.value, any(), 2L)
        }
    }
}
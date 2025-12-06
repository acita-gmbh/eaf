package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
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
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import de.acci.dvmm.domain.vm.VmId

class VmProvisioningListenerTest {

    private val eventStore = mockk<EventStore>()
    private val deserializer = mockk<VmRequestEventDeserializer>()
    private val provisionVmHandler = mockk<ProvisionVmHandler>()

    private fun createStoredEvent(id: UUID = UUID.randomUUID()): StoredEvent {
        return StoredEvent(
            id = id,
            aggregateId = UUID.randomUUID(),
            aggregateType = "VmRequest",
            eventType = "VmRequestCreated",
            payload = "{}",
            metadata = EventMetadata.create(TenantId.generate(), UserId.generate()),
            version = 1,
            createdAt = Instant.now()
        )
    }

    @Test
    fun `onVmRequestApproved should load aggregate and dispatch ProvisionVmCommand`() = runTest {
        // Given
        val requestId = VmRequestId.generate()
        val tenantId = TenantId.generate()
        val userId = UserId.generate()
        val projectId = ProjectId.generate()
        val vmName = VmName.of("web-server")
        val size = VmSize.M
        val correlationId = CorrelationId.generate()

        val event = VmRequestApproved(
            aggregateId = requestId,
            vmName = vmName,
            projectId = projectId,
            requesterId = userId,
            requesterEmail = "test@example.com",
            metadata = EventMetadata.create(tenantId, UserId.generate(), correlationId)
        )

        val createdEvent = VmRequestCreated(
            aggregateId = requestId,
            projectId = projectId,
            vmName = vmName,
            size = size,
            justification = "Testing",
            requesterEmail = "test@example.com",
            metadata = EventMetadata.create(tenantId, userId, correlationId)
        )
        
        val storedEvent1 = createStoredEvent()
        val storedEvent2 = createStoredEvent()

        coEvery {
            eventStore.load(requestId.value)
        } returns listOf(storedEvent1, storedEvent2)

        every { deserializer.deserialize(storedEvent1) } returns createdEvent
        every { deserializer.deserialize(storedEvent2) } returns event

        val commandSlot = slot<ProvisionVmCommand>()
        val correlationIdSlot = slot<CorrelationId>()

        coEvery {
            provisionVmHandler.handle(capture(commandSlot), capture(correlationIdSlot))
        } returns ProvisionVmResult(VmId.generate()).success()

        val listener = VmProvisioningListener(eventStore, deserializer, provisionVmHandler)

        // When
        listener.onVmRequestApproved(event)

        // Then
        coVerify(exactly = 1) {
            eventStore.load(requestId.value)
        }
        coVerify(exactly = 1) {
            provisionVmHandler.handle(any(), any())
        }

        val command = commandSlot.captured
        assertEquals(requestId, command.requestId)
        assertEquals(tenantId, command.tenantId)
        assertEquals(projectId, command.projectId)
        assertEquals(vmName, command.vmName)
        assertEquals(size, command.size)
        assertEquals(userId, command.requesterId)
        assertEquals(correlationId, correlationIdSlot.captured)
    }
}
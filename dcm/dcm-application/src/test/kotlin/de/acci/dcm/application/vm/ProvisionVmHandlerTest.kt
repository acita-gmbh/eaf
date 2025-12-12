package de.acci.dcm.application.vm

import de.acci.dcm.domain.vm.VmStatus
import de.acci.dcm.domain.vm.events.VmProvisioningStarted
import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProvisionVmHandlerTest {

    private val eventStore = mockk<EventStore>()

    @Test
    fun `should create VmAggregate and persist VmProvisioningStarted event`() = runTest {
        // Given
        val command = ProvisionVmCommand(
            requestId = VmRequestId.generate(),
            tenantId = TenantId.generate(),
            projectId = ProjectId.generate(),
            vmName = VmName.of("web-vm"),
            size = VmSize.S,
            requesterId = UserId.generate()
        )
        val correlationId = CorrelationId.generate()
        val eventsSlot = slot<List<DomainEvent>>()

        coEvery {
            eventStore.append(any(), capture(eventsSlot), any())
        } returns 1L.success()

        val handler = ProvisionVmHandler(eventStore)

        // When
        val result = handler.handle(command, correlationId)

        // Then
        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            eventStore.append(any(), any(), eq(0L))
        }

        val events = eventsSlot.captured
        assertEquals(1, events.size)
        val event = events[0] as VmProvisioningStarted

        assertEquals(command.requestId, event.requestId)
        assertEquals(command.vmName, event.vmName)
        assertEquals(correlationId, event.metadata.correlationId)
    }
}

package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmrequest.MarkVmRequestProvisioningCommand
import de.acci.dvmm.application.vmrequest.MarkVmRequestProvisioningHandler
import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
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
import java.util.UUID

class VmRequestStatusUpdaterTest {

    private val handler = mockk<MarkVmRequestProvisioningHandler>()

    @Test
    fun `onVmProvisioningStarted should dispatch MarkVmRequestProvisioningCommand`() = runTest {
        // Given
        val aggregateId = VmId.generate()
        val requestId = VmRequestId.generate()
        val tenantId = TenantId.generate()
        val userId = UserId.generate()
        val correlationId = CorrelationId.generate()

        val event = VmProvisioningStarted(
            aggregateId = aggregateId,
            requestId = requestId,
            projectId = ProjectId.generate(),
            vmName = VmName.of("web-server"),
            size = VmSize.M,
            requesterId = userId, // Requester
            metadata = EventMetadata.create(tenantId, UserId.generate(), correlationId) // Metadata has admin or system?
        )

        val commandSlot = slot<MarkVmRequestProvisioningCommand>()
        val correlationIdSlot = slot<CorrelationId>()

        coEvery {
            handler.handle(capture(commandSlot), capture(correlationIdSlot))
        } returns Unit.success()

        val updater = VmRequestStatusUpdater(handler)

        // When
        updater.onVmProvisioningStarted(event)

        // Then
        coVerify(exactly = 1) {
            handler.handle(any(), any())
        }

        val command = commandSlot.captured
        assertEquals(requestId, command.requestId)
        assertEquals(tenantId, command.tenantId)
        assertEquals(event.metadata.userId, command.userId) // Metadata user propagated
        assertEquals(correlationId, correlationIdSlot.captured)
    }
}

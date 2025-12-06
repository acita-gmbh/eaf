package de.acci.dvmm.domain.vm

import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class VmAggregateTest {

    @Test
    fun `should start provisioning when requested`() {
        // Given
        val tenantId = TenantId.fromString("11111111-1111-1111-1111-111111111111")
        val userId = UserId.fromString("22222222-2222-2222-2222-222222222222")
        val metadata = EventMetadata.create(
            tenantId = tenantId,
            userId = userId
        )
        val requestId = VmRequestId.fromString("33333333-3333-3333-3333-333333333333")
        val projectId = ProjectId.fromString("44444444-4444-4444-4444-444444444444")
        val vmName = VmName.of("test-vm-01")
        val size = VmSize.L

        // When
        val aggregate = VmAggregate.startProvisioning(
            requestId = requestId,
            projectId = projectId,
            vmName = vmName,
            size = size,
            requesterId = userId,
            metadata = metadata
        )

        // Then
        assertEquals(1, aggregate.uncommittedEvents.size)
        val event = aggregate.uncommittedEvents.first() as VmProvisioningStarted

        assertEquals(aggregate.id, event.aggregateId)
        assertEquals(requestId, event.requestId)
        assertEquals(projectId, event.projectId)
        assertEquals(vmName, event.vmName)
        assertEquals(size, event.size)
        assertEquals(userId, event.requesterId)
        assertEquals(metadata.tenantId, event.metadata.tenantId)

        assertEquals(VmStatus.PROVISIONING, aggregate.status)
    }
}

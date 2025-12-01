package de.acci.dvmm.domain.vmrequest.events

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VmRequestCreatedTest {

    private fun createMetadata() = EventMetadata.create(
        tenantId = TenantId.generate(),
        userId = UserId.generate(),
        correlationId = CorrelationId.generate()
    )

    @Test
    fun `should implement DomainEvent interface`() {
        val event = createTestEvent()
        assertTrue(event is DomainEvent)
    }

    @Test
    fun `should have correct aggregate type`() {
        val event = createTestEvent()
        assertEquals("VmRequest", event.aggregateType)
        assertEquals(VmRequestCreated.AGGREGATE_TYPE, event.aggregateType)
    }

    @Test
    fun `should contain all required fields`() {
        val requestId = VmRequestId.generate()
        val projectId = ProjectId.generate()
        val vmName = VmName.of("test-vm-01")
        val size = VmSize.M
        val justification = "Testing the VM request system"
        val metadata = createMetadata()

        val event = VmRequestCreated(
            aggregateId = requestId,
            projectId = projectId,
            vmName = vmName,
            size = size,
            justification = justification,
            metadata = metadata
        )

        assertEquals(requestId, event.aggregateId)
        assertEquals(projectId, event.projectId)
        assertEquals(vmName, event.vmName)
        assertEquals(size, event.size)
        assertEquals(justification, event.justification)
        assertEquals(metadata, event.metadata)
    }

    @Test
    fun `should have non-null metadata`() {
        val event = createTestEvent()
        assertNotNull(event.metadata)
        assertNotNull(event.metadata.tenantId)
        assertNotNull(event.metadata.userId)
        assertNotNull(event.metadata.correlationId)
        assertNotNull(event.metadata.timestamp)
    }

    @Test
    fun `should be a data class with copy support`() {
        val event = createTestEvent()
        val newProjectId = ProjectId.generate()

        val copied = event.copy(projectId = newProjectId)

        assertEquals(newProjectId, copied.projectId)
        assertEquals(event.aggregateId, copied.aggregateId)
        assertEquals(event.vmName, copied.vmName)
    }

    private fun createTestEvent(): VmRequestCreated {
        return VmRequestCreated(
            aggregateId = VmRequestId.generate(),
            projectId = ProjectId.generate(),
            vmName = VmName.of("test-vm-01"),
            size = VmSize.M,
            justification = "Test justification for VM request",
            metadata = createMetadata()
        )
    }
}

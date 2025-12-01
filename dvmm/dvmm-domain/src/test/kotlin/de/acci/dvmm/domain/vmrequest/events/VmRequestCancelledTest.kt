package de.acci.dvmm.domain.vmrequest.events

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.testing.fixtures.TestMetadataFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VmRequestCancelledTest {

    @Test
    fun `should implement DomainEvent interface`() {
        val event = createTestEvent()
        assertTrue(event is DomainEvent)
    }

    @Test
    fun `should have correct aggregate type`() {
        val event = createTestEvent()
        assertEquals("VmRequest", event.aggregateType)
        assertEquals(VmRequestCancelled.AGGREGATE_TYPE, event.aggregateType)
    }

    @Test
    fun `should contain aggregateId and metadata`() {
        val requestId = VmRequestId.generate()
        val metadata = TestMetadataFactory.create()

        val event = VmRequestCancelled(
            aggregateId = requestId,
            reason = "No longer needed",
            metadata = metadata
        )

        assertEquals(requestId, event.aggregateId)
        assertEquals("No longer needed", event.reason)
        assertEquals(metadata, event.metadata)
    }

    @Test
    fun `should allow null cancellation reason`() {
        val event = VmRequestCancelled(
            aggregateId = VmRequestId.generate(),
            reason = null,
            metadata = TestMetadataFactory.create()
        )

        assertEquals(null, event.reason)
    }

    @Test
    fun `should have non-null metadata with tenant and user context`() {
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
        val newReason = "Changed my mind"

        val copied = event.copy(reason = newReason)

        assertEquals(newReason, copied.reason)
        assertEquals(event.aggregateId, copied.aggregateId)
        assertEquals(event.metadata, copied.metadata)
    }

    private fun createTestEvent(): VmRequestCancelled {
        return VmRequestCancelled(
            aggregateId = VmRequestId.generate(),
            reason = "User cancelled request",
            metadata = TestMetadataFactory.create()
        )
    }
}

package de.acci.dvmm.infrastructure.eventsourcing

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStoreObjectMapper
import de.acci.eaf.eventsourcing.StoredEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@DisplayName("JacksonVmRequestEventDeserializer")
class JacksonVmRequestEventDeserializerTest {

    private val objectMapper = EventStoreObjectMapper.create()
    private val deserializer = JacksonVmRequestEventDeserializer(objectMapper)

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testCorrelationId = CorrelationId(UUID.randomUUID())
    private val testVmRequestId = VmRequestId.generate()
    private val testProjectId = ProjectId.generate()

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = testCorrelationId,
        timestamp = Instant.now()
    )

    @Nested
    @DisplayName("VmRequestCreated Deserialization")
    inner class VmRequestCreatedDeserialization {

        @Test
        fun `deserializes VmRequestCreated event correctly`() {
            // Given: A stored VmRequestCreated event
            val vmName = VmName.create("test-vm").getOrThrow()
            val event = VmRequestCreated(
                aggregateId = testVmRequestId,
                projectId = testProjectId,
                vmName = vmName,
                size = VmSize.M,
                justification = "Test justification",
                requesterEmail = "test@example.com",
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmRequestId.value,
                aggregateType = "VmRequest",
                eventType = "VmRequestCreated",
                payload = payload,
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is VmRequestCreated)
            val created = result as VmRequestCreated
            assertEquals(event.aggregateId.value, created.aggregateId.value)
            assertEquals(event.projectId.value, created.projectId.value)
            assertEquals(event.vmName.value, created.vmName.value)
            assertEquals(event.size, created.size)
            assertEquals(event.justification, created.justification)
            assertEquals(event.requesterEmail, created.requesterEmail)
        }
    }

    @Nested
    @DisplayName("VmRequestCancelled Deserialization")
    inner class VmRequestCancelledDeserialization {

        @Test
        fun `deserializes VmRequestCancelled event correctly`() {
            // Given: A stored VmRequestCancelled event
            val event = VmRequestCancelled(
                aggregateId = testVmRequestId,
                reason = "No longer needed",
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmRequestId.value,
                aggregateType = "VmRequest",
                eventType = "VmRequestCancelled",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is VmRequestCancelled)
            val cancelled = result as VmRequestCancelled
            assertEquals(event.aggregateId.value, cancelled.aggregateId.value)
            assertEquals(event.reason, cancelled.reason)
        }

        @Test
        fun `deserializes VmRequestCancelled with null reason`() {
            // Given: A cancelled event without reason
            val event = VmRequestCancelled(
                aggregateId = testVmRequestId,
                reason = null,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmRequestId.value,
                aggregateType = "VmRequest",
                eventType = "VmRequestCancelled",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Reason is null
            assertTrue(result is VmRequestCancelled)
            val cancelled = result as VmRequestCancelled
            assertEquals(null, cancelled.reason)
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        fun `throws exception for unknown event type`() {
            // Given: A stored event with unknown type
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                aggregateType = "VmRequest",
                eventType = "UnknownEventType",
                payload = "{}",
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When/Then: Exception thrown with helpful message
            val exception = assertThrows<IllegalArgumentException> {
                deserializer.deserialize(storedEvent)
            }
            assertTrue(exception.message!!.contains("UnknownEventType"))
            assertTrue(exception.message!!.contains("JacksonVmRequestEventDeserializer"))
        }

        @Test
        fun `throws exception for malformed JSON payload`() {
            // Given: A stored event with invalid JSON
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                aggregateType = "VmRequest",
                eventType = "VmRequestCreated",
                payload = "not valid json",
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When/Then: Jackson throws parsing exception
            assertThrows<Exception> {
                deserializer.deserialize(storedEvent)
            }
        }
    }
}

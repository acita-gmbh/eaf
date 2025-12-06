package de.acci.dvmm.infrastructure.eventsourcing

import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
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

@DisplayName("JacksonVmEventDeserializer")
class JacksonVmEventDeserializerTest {

    private val objectMapper = EventStoreObjectMapper.create()
    private val deserializer = JacksonVmEventDeserializer(objectMapper)

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testCorrelationId = CorrelationId(UUID.randomUUID())
    private val testVmId = VmId.generate()
    private val testRequestId = VmRequestId.generate()
    private val testProjectId = ProjectId.generate()

    private fun createTestMetadata(): EventMetadata = EventMetadata(
        tenantId = testTenantId,
        userId = testUserId,
        correlationId = testCorrelationId,
        timestamp = Instant.now()
    )

    @Nested
    @DisplayName("VmProvisioningStarted Deserialization")
    inner class VmProvisioningStartedDeserialization {

        @Test
        fun `deserializes VmProvisioningStarted event correctly`() {
            // Given: A stored VmProvisioningStarted event
            val vmName = VmName.create("test-vm").getOrThrow()
            val event = VmProvisioningStarted(
                aggregateId = testVmId,
                requestId = testRequestId,
                projectId = testProjectId,
                vmName = vmName,
                size = VmSize.M,
                requesterId = testUserId,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmId.value,
                aggregateType = "Vm",
                eventType = "VmProvisioningStarted",
                payload = payload,
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is VmProvisioningStarted)
            val provisioningStarted = result as VmProvisioningStarted
            assertEquals(event.aggregateId.value, provisioningStarted.aggregateId.value)
            assertEquals(event.requestId.value, provisioningStarted.requestId.value)
            assertEquals(event.projectId.value, provisioningStarted.projectId.value)
            assertEquals(event.vmName.value, provisioningStarted.vmName.value)
            assertEquals(event.size, provisioningStarted.size)
            assertEquals(event.requesterId.value, provisioningStarted.requesterId.value)
        }
    }

    @Nested
    @DisplayName("VmProvisioningFailed Deserialization")
    inner class VmProvisioningFailedDeserialization {

        @Test
        fun `deserializes VmProvisioningFailed event correctly`() {
            // Given: A stored VmProvisioningFailed event
            val event = VmProvisioningFailed(
                aggregateId = testVmId,
                requestId = testRequestId,
                reason = "VMware configuration missing for tenant",
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmId.value,
                aggregateType = "Vm",
                eventType = "VmProvisioningFailed",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type returned
            assertTrue(result is VmProvisioningFailed)
            val provisioningFailed = result as VmProvisioningFailed
            assertEquals(event.aggregateId.value, provisioningFailed.aggregateId.value)
            assertEquals(event.requestId.value, provisioningFailed.requestId.value)
            assertEquals(event.reason, provisioningFailed.reason)
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
                aggregateType = "Vm",
                eventType = "UnknownVmEventType",
                payload = "{}",
                metadata = createTestMetadata(),
                version = 1,
                createdAt = Instant.now()
            )

            // When/Then: Exception thrown with helpful message
            val exception = assertThrows<IllegalArgumentException> {
                deserializer.deserialize(storedEvent)
            }
            assertTrue(exception.message!!.contains("UnknownVmEventType"))
            assertTrue(exception.message!!.contains("JacksonVmEventDeserializer"))
        }

        @Test
        fun `throws exception for malformed JSON payload`() {
            // Given: A stored event with invalid JSON
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                aggregateType = "Vm",
                eventType = "VmProvisioningStarted",
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

package de.acci.dcm.infrastructure.eventsourcing

import de.acci.dcm.domain.vm.VmId
import de.acci.dcm.domain.vm.VmwareVmId
import de.acci.dcm.domain.vm.events.VmProvisioned
import de.acci.dcm.domain.vm.events.VmProvisioningFailed
import de.acci.dcm.domain.vm.events.VmProvisioningStarted
import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmSize
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
        fun `deserializes VmProvisioningFailed event with all AC-3-6-2 fields`() {
            // Given: A stored VmProvisioningFailed event with all new fields (AC-3.6.2)
            val lastAttemptAt = Instant.parse("2025-01-15T10:30:00Z")
            val event = VmProvisioningFailed(
                aggregateId = testVmId,
                requestId = testRequestId,
                reason = "Temporary connection issue. We will retry automatically.",
                errorCode = "CONNECTION_TIMEOUT",
                errorMessage = "Temporary connection issue. We will retry automatically.",
                retryCount = 5,
                lastAttemptAt = lastAttemptAt,
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

            // Then: All AC-3.6.2 fields correctly deserialized
            assertTrue(result is VmProvisioningFailed)
            val provisioningFailed = result as VmProvisioningFailed
            assertEquals(event.aggregateId.value, provisioningFailed.aggregateId.value)
            assertEquals(event.requestId.value, provisioningFailed.requestId.value)
            assertEquals("CONNECTION_TIMEOUT", provisioningFailed.errorCode)
            assertEquals("Temporary connection issue. We will retry automatically.", provisioningFailed.errorMessage)
            assertEquals(5, provisioningFailed.retryCount)
            assertEquals(lastAttemptAt, provisioningFailed.lastAttemptAt)
            // Legacy field also preserved
            assertEquals(event.reason, provisioningFailed.reason)
        }

        @Test
        fun `deserializes legacy VmProvisioningFailed event without new fields`() {
            // Given: A legacy event JSON that only has the original fields
            // (simulating data stored before AC-3.6.2 enhancement)
            val legacyPayload = """
                {
                    "aggregateId": "${testVmId.value}",
                    "requestId": "${testRequestId.value}",
                    "reason": "VMware configuration missing for tenant",
                    "metadata": {
                        "tenantId": "${testTenantId.value}",
                        "userId": "${testUserId.value}",
                        "correlationId": "${testCorrelationId.value}",
                        "timestamp": "2025-01-15T10:00:00Z"
                    }
                }
            """.trimIndent()
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmId.value,
                aggregateType = "Vm",
                eventType = "VmProvisioningFailed",
                payload = legacyPayload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize legacy event
            val result = deserializer.deserialize(storedEvent)

            // Then: Legacy field preserved, new fields get defaults
            assertTrue(result is VmProvisioningFailed)
            val provisioningFailed = result as VmProvisioningFailed
            assertEquals("VMware configuration missing for tenant", provisioningFailed.reason)
            // New fields should have sensible defaults
            assertEquals("UNKNOWN", provisioningFailed.errorCode)
            assertEquals("VMware configuration missing for tenant", provisioningFailed.errorMessage) // defaults to reason
            assertEquals(1, provisioningFailed.retryCount)
        }
    }

    @Nested
    @DisplayName("VmProvisioned Deserialization")
    inner class VmProvisionedDeserialization {

        @Test
        fun `deserializes VmProvisioned event correctly`() {
            // Given: A stored VmProvisioned event with IP address
            val vmwareVmId = VmwareVmId.of("vm-12345")
            val event = VmProvisioned(
                aggregateId = testVmId,
                requestId = testRequestId,
                vmwareVmId = vmwareVmId,
                ipAddress = "10.0.0.100",
                hostname = "test-vm-01",
                warningMessage = null,
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmId.value,
                aggregateType = "Vm",
                eventType = "VmProvisioned",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Correct event type with all fields
            assertTrue(result is VmProvisioned)
            val provisioned = result as VmProvisioned
            assertEquals(event.aggregateId.value, provisioned.aggregateId.value)
            assertEquals(event.requestId.value, provisioned.requestId.value)
            assertEquals(event.vmwareVmId.value, provisioned.vmwareVmId.value)
            assertEquals(event.ipAddress, provisioned.ipAddress)
            assertEquals(event.hostname, provisioned.hostname)
            assertEquals(event.warningMessage, provisioned.warningMessage)
        }

        @Test
        fun `deserializes VmProvisioned with null IP address and warning message`() {
            // Given: A VmProvisioned event when VMware Tools timed out
            val vmwareVmId = VmwareVmId.of("vm-67890")
            val event = VmProvisioned(
                aggregateId = testVmId,
                requestId = testRequestId,
                vmwareVmId = vmwareVmId,
                ipAddress = null,
                hostname = "test-vm-02",
                warningMessage = "VMware Tools timeout - IP detection pending",
                metadata = createTestMetadata()
            )
            val payload = objectMapper.writeValueAsString(event)
            val storedEvent = StoredEvent(
                id = UUID.randomUUID(),
                aggregateId = testVmId.value,
                aggregateType = "Vm",
                eventType = "VmProvisioned",
                payload = payload,
                metadata = createTestMetadata(),
                version = 2,
                createdAt = Instant.now()
            )

            // When: Deserialize
            val result = deserializer.deserialize(storedEvent)

            // Then: Null IP and warning preserved
            assertTrue(result is VmProvisioned)
            val provisioned = result as VmProvisioned
            assertEquals(null, provisioned.ipAddress)
            assertEquals("VMware Tools timeout - IP detection pending", provisioned.warningMessage)
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

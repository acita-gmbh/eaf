package de.acci.dvmm.domain.vmrequest

import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.fixtures.TestMetadataFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("VmRequestAggregate")
class VmRequestAggregateTest {

    @Nested
    @DisplayName("create()")
    inner class CreateTests {

        @Test
        @DisplayName("should create aggregate with VmRequestCreated event")
        fun `should create aggregate with VmRequestCreated event`() {
            // Given
            val projectId = ProjectId.generate()
            val vmName = VmName.of("web-server-01")
            val size = VmSize.M
            val justification = "Test VM for development"
            val metadata = TestMetadataFactory.create()

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = metadata.userId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification,
                metadata = metadata
            )

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertTrue(aggregate.uncommittedEvents[0] is VmRequestCreated)
        }

        @Test
        @DisplayName("should set aggregate ID to new UUID")
        fun `should set aggregate ID to new UUID`() {
            // When
            val aggregate = createValidAggregate()

            // Then
            assertTrue(aggregate.id.value.toString().isNotBlank())
        }

        @Test
        @DisplayName("should set version to 1 after creation")
        fun `should set version to 1 after creation`() {
            // When
            val aggregate = createValidAggregate()

            // Then
            assertEquals(1, aggregate.version)
        }

        @Test
        @DisplayName("should set status to PENDING")
        fun `should set status to PENDING`() {
            // When
            val aggregate = createValidAggregate()

            // Then
            assertEquals(VmRequestStatus.PENDING, aggregate.status)
        }

        @Test
        @DisplayName("should capture tenant from metadata")
        fun `should capture tenant from metadata`() {
            // Given
            val tenantId = TenantId.generate()
            val metadata = TestMetadataFactory.create(tenantId = tenantId)

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = metadata.userId,
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = "Capturing tenant test",
                metadata = metadata
            )

            // Then
            assertEquals(tenantId, aggregate.tenantId)
        }

        @Test
        @DisplayName("should capture requester from metadata")
        fun `should capture requester from metadata`() {
            // Given
            val userId = UserId.generate()
            val metadata = TestMetadataFactory.create(userId = userId)

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = userId,
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = "Capturing requester test",
                metadata = metadata
            )

            // Then
            assertEquals(userId, aggregate.requesterId)
        }

        @Test
        @DisplayName("should reject justification shorter than 10 characters")
        fun `should reject justification shorter than 10 characters`() {
            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                VmRequestAggregate.create(
                    requesterId = UserId.generate(),
                    projectId = ProjectId.generate(),
                    vmName = VmName.of("test-vm-01"),
                    size = VmSize.S,
                    justification = "Short",
                    metadata = TestMetadataFactory.create()
                )
            }

            assertTrue(exception.message?.contains("10 characters") == true)
        }

        @Test
        @DisplayName("should accept justification with exactly 10 characters")
        fun `should accept justification with exactly 10 characters`() {
            // Given
            val justification = "0123456789" // exactly 10 chars

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = UserId.generate(),
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = justification,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(justification, aggregate.justification)
        }

        @Test
        @DisplayName("should capture all field values correctly")
        fun `should capture all field values correctly`() {
            // Given
            val projectId = ProjectId.generate()
            val vmName = VmName.of("database-prod")
            val size = VmSize.XL
            val justification = "Production database server"
            val metadata = TestMetadataFactory.create()

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = metadata.userId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification,
                metadata = metadata
            )

            // Then
            assertEquals(projectId, aggregate.projectId)
            assertEquals(vmName, aggregate.vmName)
            assertEquals(size, aggregate.size)
            assertEquals(justification, aggregate.justification)
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    inner class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute state from events")
        fun `should reconstitute state from events`() {
            // Given
            val requestId = VmRequestId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("reconstituted-vm")
            val size = VmSize.L
            val justification = "Reconstitution test justification"
            val metadata = TestMetadataFactory.create()

            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = size,
                    justification = justification,
                    metadata = metadata
                )
            )

            // When
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then
            assertEquals(requestId, aggregate.id)
            assertEquals(projectId, aggregate.projectId)
            assertEquals(vmName, aggregate.vmName)
            assertEquals(size, aggregate.size)
            assertEquals(justification, aggregate.justification)
            assertEquals(VmRequestStatus.PENDING, aggregate.status)
        }

        @Test
        @DisplayName("should have empty uncommittedEvents after reconstitution")
        fun `should have empty uncommittedEvents after reconstitution`() {
            // Given
            val aggregate = createValidAggregate()
            val events = aggregate.uncommittedEvents.toList()
            aggregate.clearUncommittedEvents()

            // When
            val reconstituted = VmRequestAggregate.reconstitute(aggregate.id, events)

            // Then
            assertTrue(reconstituted.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("should set version to event count after reconstitution")
        fun `should set version to event count after reconstitution`() {
            // Given
            val aggregate = createValidAggregate()
            val events = aggregate.uncommittedEvents.toList()

            // When
            val reconstituted = VmRequestAggregate.reconstitute(aggregate.id, events)

            // Then
            assertEquals(1, reconstituted.version)
        }

        @Test
        @DisplayName("should handle empty events list")
        fun `should handle empty events list`() {
            // Given
            val id = VmRequestId.generate()

            // When
            val aggregate = VmRequestAggregate.reconstitute(id, emptyList())

            // Then
            assertEquals(id, aggregate.id)
            assertEquals(0, aggregate.version)
        }
    }

    @Nested
    @DisplayName("VmRequestCreated event data")
    inner class EventDataTests {

        @Test
        @DisplayName("event should contain correct aggregate type")
        fun `event should contain correct aggregate type`() {
            // Given
            val aggregate = createValidAggregate()
            val event = aggregate.uncommittedEvents[0] as VmRequestCreated

            // Then
            assertEquals("VmRequest", event.aggregateType)
        }

        @Test
        @DisplayName("event should contain aggregate ID")
        fun `event should contain aggregate ID`() {
            // Given
            val aggregate = createValidAggregate()
            val event = aggregate.uncommittedEvents[0] as VmRequestCreated

            // Then
            assertEquals(aggregate.id, event.aggregateId)
        }

        @Test
        @DisplayName("event should contain metadata with tenant context")
        fun `event should contain metadata with tenant context`() {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val metadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)

            val aggregate = VmRequestAggregate.create(
                requesterId = userId,
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = "Event metadata test",
                metadata = metadata
            )

            val event = aggregate.uncommittedEvents[0] as VmRequestCreated

            // Then
            assertEquals(tenantId, event.metadata.tenantId)
            assertEquals(userId, event.metadata.userId)
        }
    }

    @Nested
    @DisplayName("cancel()")
    inner class CancelTests {

        @Test
        @DisplayName("should reject reason exceeding max length")
        fun `should reject reason exceeding max length`() {
            // Given
            val aggregate = createValidAggregate()
            aggregate.clearUncommittedEvents()
            val tooLongReason = "a".repeat(VmRequestCancelled.MAX_REASON_LENGTH + 1)

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                aggregate.cancel(
                    reason = tooLongReason,
                    metadata = TestMetadataFactory.create()
                )
            }

            assertTrue(exception.message!!.contains("${VmRequestCancelled.MAX_REASON_LENGTH}"))
        }

        @Test
        @DisplayName("should accept reason at max length")
        fun `should accept reason at max length`() {
            // Given
            val aggregate = createValidAggregate()
            aggregate.clearUncommittedEvents()
            val maxLengthReason = "a".repeat(VmRequestCancelled.MAX_REASON_LENGTH)

            // When
            aggregate.cancel(
                reason = maxLengthReason,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(maxLengthReason, event.reason)
        }

        @Test
        @DisplayName("should accept null reason")
        fun `should accept null reason`() {
            // Given
            val aggregate = createValidAggregate()
            aggregate.clearUncommittedEvents()

            // When
            aggregate.cancel(
                reason = null,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(null, event.reason)
        }
    }

    private fun createValidAggregate(): VmRequestAggregate {
        return VmRequestAggregate.create(
            requesterId = UserId.generate(),
            projectId = ProjectId.generate(),
            vmName = VmName.of("test-vm-01"),
            size = VmSize.M,
            justification = "Valid justification for testing",
            metadata = TestMetadataFactory.create()
        )
    }
}

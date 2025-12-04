package de.acci.dvmm.domain.vmrequest

import de.acci.dvmm.domain.exceptions.InvalidStateException
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

@DisplayName("VmRequestAggregate cancel()")
class VmRequestAggregateCancelTest {

    private fun createPendingAggregate(
        tenantId: TenantId = TenantId.generate(),
        userId: UserId = UserId.generate()
    ): VmRequestAggregate {
        val metadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
        return VmRequestAggregate.create(
            requesterId = userId,
            projectId = ProjectId.generate(),
            vmName = VmName.of("test-vm-01"),
            size = VmSize.M,
            justification = "Valid justification for testing",
            requesterEmail = "test@example.com",
            metadata = metadata
        )
    }

    @Nested
    @DisplayName("when status is PENDING")
    inner class PendingStateTests {

        @Test
        @DisplayName("should emit VmRequestCancelled event")
        fun `should emit VmRequestCancelled event`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When
            aggregate.cancel(reason = "No longer needed", metadata = cancelMetadata)

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertTrue(aggregate.uncommittedEvents[0] is VmRequestCancelled)
        }

        @Test
        @DisplayName("should change status to CANCELLED")
        fun `should change status to CANCELLED`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When
            aggregate.cancel(reason = "No longer needed", metadata = cancelMetadata)

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
        }

        @Test
        @DisplayName("should include reason in event")
        fun `should include reason in event`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()
            val reason = "Project cancelled"

            // When
            aggregate.cancel(reason = reason, metadata = cancelMetadata)

            // Then
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(reason, event.reason)
        }

        @Test
        @DisplayName("should support null reason")
        fun `should support null reason`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When
            aggregate.cancel(reason = null, metadata = cancelMetadata)

            // Then
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(null, event.reason)
        }

        @Test
        @DisplayName("event should contain correct aggregateId")
        fun `event should contain correct aggregateId`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When
            aggregate.cancel(reason = "No longer needed", metadata = cancelMetadata)

            // Then
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(aggregate.id, event.aggregateId)
        }

        @Test
        @DisplayName("event should contain metadata with tenant and user context")
        fun `event should contain metadata with tenant and user context`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val cancelMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)

            // When
            aggregate.cancel(reason = "No longer needed", metadata = cancelMetadata)

            // Then
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(tenantId, event.metadata.tenantId)
            assertEquals(userId, event.metadata.userId)
        }

        @Test
        @DisplayName("should increment version")
        fun `should increment version`() {
            // Given
            val aggregate = createPendingAggregate()
            val initialVersion = aggregate.version
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When
            aggregate.cancel(reason = "No longer needed", metadata = cancelMetadata)

            // Then
            assertEquals(initialVersion + 1, aggregate.version)
        }
    }

    @Nested
    @DisplayName("when status is CANCELLED (idempotent)")
    inner class IdempotentCancelTests {

        @Test
        @DisplayName("should not emit new event when already CANCELLED")
        fun `should not emit new event when already CANCELLED`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()
            aggregate.cancel(reason = "First cancel", metadata = cancelMetadata)
            aggregate.clearUncommittedEvents()

            // When
            aggregate.cancel(reason = "Second cancel attempt", metadata = cancelMetadata)

            // Then
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("should remain in CANCELLED status")
        fun `should remain in CANCELLED status`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()
            aggregate.cancel(reason = "First cancel", metadata = cancelMetadata)

            // When
            aggregate.cancel(reason = "Second cancel attempt", metadata = cancelMetadata)

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
        }

        @Test
        @DisplayName("should not increment version when idempotent")
        fun `should not increment version when idempotent`() {
            // Given
            val aggregate = createPendingAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()
            aggregate.cancel(reason = "First cancel", metadata = cancelMetadata)
            val versionAfterFirstCancel = aggregate.version

            // When
            aggregate.cancel(reason = "Second cancel attempt", metadata = cancelMetadata)

            // Then
            assertEquals(versionAfterFirstCancel, aggregate.version)
        }
    }

    @Nested
    @DisplayName("when status is not cancellable")
    inner class InvalidStateTests {

        @Test
        @DisplayName("should throw InvalidStateException for APPROVED status")
        fun `should throw InvalidStateException for APPROVED status`() {
            // Given
            val aggregate = createApprovedAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.cancel(reason = "Too late", metadata = cancelMetadata)
            }
            assertEquals(VmRequestStatus.APPROVED, exception.currentState)
            assertEquals("cancel", exception.operation)
        }

        @Test
        @DisplayName("should throw InvalidStateException for REJECTED status")
        fun `should throw InvalidStateException for REJECTED status`() {
            // Given
            val aggregate = createRejectedAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.cancel(reason = "Too late", metadata = cancelMetadata)
            }
            assertEquals(VmRequestStatus.REJECTED, exception.currentState)
            assertEquals("cancel", exception.operation)
        }

        @Test
        @DisplayName("should throw InvalidStateException for PROVISIONING status")
        fun `should throw InvalidStateException for PROVISIONING status`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.cancel(reason = "Too late", metadata = cancelMetadata)
            }
            assertEquals(VmRequestStatus.PROVISIONING, exception.currentState)
            assertEquals("cancel", exception.operation)
        }

        @Test
        @DisplayName("should throw InvalidStateException for READY status")
        fun `should throw InvalidStateException for READY status`() {
            // Given
            val aggregate = createReadyAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.cancel(reason = "Too late", metadata = cancelMetadata)
            }
            assertEquals(VmRequestStatus.READY, exception.currentState)
            assertEquals("cancel", exception.operation)
        }

        @Test
        @DisplayName("should throw InvalidStateException for FAILED status")
        fun `should throw InvalidStateException for FAILED status`() {
            // Given
            val aggregate = createFailedAggregate()
            aggregate.clearUncommittedEvents()
            val cancelMetadata = TestMetadataFactory.create()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.cancel(reason = "Too late", metadata = cancelMetadata)
            }
            assertEquals(VmRequestStatus.FAILED, exception.currentState)
            assertEquals("cancel", exception.operation)
        }
    }

    @Nested
    @DisplayName("reconstitute with cancel event")
    inner class ReconstituteWithCancelTests {

        @Test
        @DisplayName("should reconstitute CANCELLED status from events")
        fun `should reconstitute CANCELLED status from events`() {
            // Given
            val requestId = VmRequestId.generate()
            val metadata = TestMetadataFactory.create()
            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = ProjectId.generate(),
                    vmName = VmName.of("test-vm-01"),
                    size = VmSize.M,
                    justification = "Test justification",
                    requesterEmail = "test@example.com",
                    metadata = metadata
                ),
                VmRequestCancelled(
                    aggregateId = requestId,
                    reason = "Cancelled by user",
                    metadata = metadata
                )
            )

            // When
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
            assertEquals(2, aggregate.version)
        }
    }

    // Helper methods to create aggregates in different states for testing
    // These simulate event replay to reach specific states

    private fun createApprovedAggregate(): VmRequestAggregate {
        val aggregate = createPendingAggregate()
        // Simulate approval by directly setting status (for testing only)
        // In production, this would happen through an approve() method
        setAggregateStatus(aggregate, VmRequestStatus.APPROVED)
        return aggregate
    }

    private fun createRejectedAggregate(): VmRequestAggregate {
        val aggregate = createPendingAggregate()
        setAggregateStatus(aggregate, VmRequestStatus.REJECTED)
        return aggregate
    }

    private fun createProvisioningAggregate(): VmRequestAggregate {
        val aggregate = createPendingAggregate()
        setAggregateStatus(aggregate, VmRequestStatus.PROVISIONING)
        return aggregate
    }

    private fun createReadyAggregate(): VmRequestAggregate {
        val aggregate = createPendingAggregate()
        setAggregateStatus(aggregate, VmRequestStatus.READY)
        return aggregate
    }

    private fun createFailedAggregate(): VmRequestAggregate {
        val aggregate = createPendingAggregate()
        setAggregateStatus(aggregate, VmRequestStatus.FAILED)
        return aggregate
    }

    private fun setAggregateStatus(aggregate: VmRequestAggregate, status: VmRequestStatus) {
        // Use reflection to set status for testing different states
        // This is acceptable in tests to avoid needing to implement all state transitions
        val statusField = VmRequestAggregate::class.java.getDeclaredField("status")
        statusField.isAccessible = true
        statusField.set(aggregate, status)
    }
}

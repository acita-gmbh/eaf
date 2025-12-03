package de.acci.dvmm.domain.vmrequest

import de.acci.dvmm.domain.exceptions.InvalidStateException
import de.acci.dvmm.domain.exceptions.SelfApprovalException
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.dvmm.domain.vmrequest.events.VmRequestRejected
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.fixtures.TestMetadataFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for VmRequestAggregate approve/reject functionality.
 *
 * Tests cover:
 * - Approve from PENDING → APPROVED
 * - Reject from PENDING → REJECTED with reason
 * - Reject reason validation (10-500 chars)
 * - Approve/Reject from non-PENDING state throws InvalidStateException
 * - Self-approval/rejection throws SelfApprovalException
 */
@DisplayName("VmRequestAggregate Approve/Reject")
class VmRequestAggregateApproveRejectTest {

    @Nested
    @DisplayName("approve()")
    inner class ApproveTests {

        @Test
        @DisplayName("should transition from PENDING to APPROVED")
        fun `should transition from PENDING to APPROVED`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate() // Different from requester
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val adminMetadata = TestMetadataFactory.create(userId = adminId)

            // When
            aggregate.approve(adminId = adminId, metadata = adminMetadata)

            // Then
            assertEquals(VmRequestStatus.APPROVED, aggregate.status)
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertTrue(aggregate.uncommittedEvents[0] is VmRequestApproved)
        }

        @Test
        @DisplayName("should emit VmRequestApproved event with denormalized fields")
        fun `should emit VmRequestApproved event with denormalized fields`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("test-vm-01")
            val aggregate = createPendingAggregate(
                requesterId = requesterId,
                projectId = projectId,
                vmName = vmName
            )
            aggregate.clearUncommittedEvents()
            val adminMetadata = TestMetadataFactory.create(userId = adminId)

            // When
            aggregate.approve(adminId = adminId, metadata = adminMetadata)

            // Then
            val event = aggregate.uncommittedEvents[0] as VmRequestApproved
            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(vmName, event.vmName)
            assertEquals(projectId, event.projectId)
            assertEquals(requesterId, event.requesterId)
            assertEquals(adminId, event.metadata.userId)
        }

        @Test
        @DisplayName("should throw SelfApprovalException when admin approves own request")
        fun `should throw SelfApprovalException when admin approves own request`() {
            // Given
            val requesterId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val adminMetadata = TestMetadataFactory.create(userId = requesterId) // Same as requester

            // When/Then
            val exception = assertThrows<SelfApprovalException> {
                aggregate.approve(adminId = requesterId, metadata = adminMetadata)
            }

            assertEquals("approve", exception.operation)
            assertEquals(requesterId, exception.adminId)
        }

        @Test
        @DisplayName("should throw InvalidStateException when approving from APPROVED state")
        fun `should throw InvalidStateException when approving from APPROVED state`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createApprovedAggregate(requesterId, adminId)
            aggregate.clearUncommittedEvents()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.approve(
                    adminId = adminId,
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertEquals(VmRequestStatus.APPROVED, exception.currentState)
            assertEquals("approve", exception.operation)
        }

        @Test
        @DisplayName("should throw InvalidStateException when approving from REJECTED state")
        fun `should throw InvalidStateException when approving from REJECTED state`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createRejectedAggregate(requesterId, adminId)
            aggregate.clearUncommittedEvents()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.approve(
                    adminId = adminId,
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertEquals(VmRequestStatus.REJECTED, exception.currentState)
        }

        @Test
        @DisplayName("should throw InvalidStateException when approving from CANCELLED state")
        fun `should throw InvalidStateException when approving from CANCELLED state`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createCancelledAggregate(requesterId)
            aggregate.clearUncommittedEvents()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.approve(
                    adminId = adminId,
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertEquals(VmRequestStatus.CANCELLED, exception.currentState)
        }
    }

    @Nested
    @DisplayName("reject()")
    inner class RejectTests {

        @Test
        @DisplayName("should transition from PENDING to REJECTED")
        fun `should transition from PENDING to REJECTED`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val reason = "Insufficient resources available in the datacenter"
            val adminMetadata = TestMetadataFactory.create(userId = adminId)

            // When
            aggregate.reject(adminId = adminId, reason = reason, metadata = adminMetadata)

            // Then
            assertEquals(VmRequestStatus.REJECTED, aggregate.status)
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertTrue(aggregate.uncommittedEvents[0] is VmRequestRejected)
        }

        @Test
        @DisplayName("should emit VmRequestRejected event with denormalized fields and reason")
        fun `should emit VmRequestRejected event with denormalized fields and reason`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("test-vm-02")
            val aggregate = createPendingAggregate(
                requesterId = requesterId,
                projectId = projectId,
                vmName = vmName
            )
            aggregate.clearUncommittedEvents()
            val reason = "Security review required before provisioning"
            val adminMetadata = TestMetadataFactory.create(userId = adminId)

            // When
            aggregate.reject(adminId = adminId, reason = reason, metadata = adminMetadata)

            // Then
            val event = aggregate.uncommittedEvents[0] as VmRequestRejected
            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(reason, event.reason)
            assertEquals(vmName, event.vmName)
            assertEquals(projectId, event.projectId)
            assertEquals(requesterId, event.requesterId)
            assertEquals(adminId, event.metadata.userId)
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when reason is less than 10 chars")
        fun `should throw IllegalArgumentException when reason is less than 10 chars`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val shortReason = "Too short" // 9 chars

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                aggregate.reject(
                    adminId = adminId,
                    reason = shortReason,
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertTrue(exception.message!!.contains("${VmRequestRejected.MIN_REASON_LENGTH}"))
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when reason exceeds 500 chars")
        fun `should throw IllegalArgumentException when reason exceeds 500 chars`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val tooLongReason = "a".repeat(VmRequestRejected.MAX_REASON_LENGTH + 1)

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                aggregate.reject(
                    adminId = adminId,
                    reason = tooLongReason,
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertTrue(exception.message!!.contains("${VmRequestRejected.MAX_REASON_LENGTH}"))
        }

        @Test
        @DisplayName("should accept reason with exactly 10 chars")
        fun `should accept reason with exactly 10 chars`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val minLengthReason = "0123456789" // exactly 10 chars

            // When
            aggregate.reject(
                adminId = adminId,
                reason = minLengthReason,
                metadata = TestMetadataFactory.create(userId = adminId)
            )

            // Then
            assertEquals(VmRequestStatus.REJECTED, aggregate.status)
            val event = aggregate.uncommittedEvents[0] as VmRequestRejected
            assertEquals(minLengthReason, event.reason)
        }

        @Test
        @DisplayName("should accept reason with exactly 500 chars")
        fun `should accept reason with exactly 500 chars`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val maxLengthReason = "a".repeat(VmRequestRejected.MAX_REASON_LENGTH)

            // When
            aggregate.reject(
                adminId = adminId,
                reason = maxLengthReason,
                metadata = TestMetadataFactory.create(userId = adminId)
            )

            // Then
            assertEquals(VmRequestStatus.REJECTED, aggregate.status)
        }

        @Test
        @DisplayName("should throw SelfApprovalException when admin rejects own request")
        fun `should throw SelfApprovalException when admin rejects own request`() {
            // Given
            val requesterId = UserId.generate()
            val aggregate = createPendingAggregate(requesterId)
            aggregate.clearUncommittedEvents()
            val adminMetadata = TestMetadataFactory.create(userId = requesterId)

            // When/Then
            val exception = assertThrows<SelfApprovalException> {
                aggregate.reject(
                    adminId = requesterId,
                    reason = "Some valid rejection reason",
                    metadata = adminMetadata
                )
            }

            assertEquals("reject", exception.operation)
            assertEquals(requesterId, exception.adminId)
        }

        @Test
        @DisplayName("should throw InvalidStateException when rejecting from APPROVED state")
        fun `should throw InvalidStateException when rejecting from APPROVED state`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createApprovedAggregate(requesterId, adminId)
            aggregate.clearUncommittedEvents()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.reject(
                    adminId = adminId,
                    reason = "Trying to reject approved request",
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertEquals(VmRequestStatus.APPROVED, exception.currentState)
        }

        @Test
        @DisplayName("should throw InvalidStateException when rejecting from REJECTED state")
        fun `should throw InvalidStateException when rejecting from REJECTED state`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createRejectedAggregate(requesterId, adminId)
            aggregate.clearUncommittedEvents()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.reject(
                    adminId = adminId,
                    reason = "Trying to reject again",
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertEquals(VmRequestStatus.REJECTED, exception.currentState)
        }

        @Test
        @DisplayName("should throw InvalidStateException when rejecting from CANCELLED state")
        fun `should throw InvalidStateException when rejecting from CANCELLED state`() {
            // Given
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val aggregate = createCancelledAggregate(requesterId)
            aggregate.clearUncommittedEvents()

            // When/Then
            val exception = assertThrows<InvalidStateException> {
                aggregate.reject(
                    adminId = adminId,
                    reason = "Trying to reject cancelled request",
                    metadata = TestMetadataFactory.create(userId = adminId)
                )
            }

            assertEquals(VmRequestStatus.CANCELLED, exception.currentState)
        }
    }

    @Nested
    @DisplayName("reconstitute() with approve/reject events")
    inner class ReconstituteApproveRejectTests {

        @Test
        @DisplayName("should reconstitute APPROVED state from events")
        fun `should reconstitute APPROVED state from events`() {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("reconstituted-vm")
            val requesterMetadata = TestMetadataFactory.create(userId = requesterId)
            val adminMetadata = TestMetadataFactory.create(userId = adminId)

            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = VmSize.M,
                    justification = "Test justification",
                    metadata = requesterMetadata
                ),
                VmRequestApproved(
                    aggregateId = requestId,
                    vmName = vmName,
                    projectId = projectId,
                    requesterId = requesterId,
                    metadata = adminMetadata
                )
            )

            // When
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then
            assertEquals(VmRequestStatus.APPROVED, aggregate.status)
            assertEquals(2, aggregate.version)
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("should reconstitute REJECTED state from events")
        fun `should reconstitute REJECTED state from events`() {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("rejected-vm")
            val requesterMetadata = TestMetadataFactory.create(userId = requesterId)
            val adminMetadata = TestMetadataFactory.create(userId = adminId)

            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = VmSize.M,
                    justification = "Test justification",
                    metadata = requesterMetadata
                ),
                VmRequestRejected(
                    aggregateId = requestId,
                    reason = "Insufficient resources",
                    vmName = vmName,
                    projectId = projectId,
                    requesterId = requesterId,
                    metadata = adminMetadata
                )
            )

            // When
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then
            assertEquals(VmRequestStatus.REJECTED, aggregate.status)
            assertEquals(2, aggregate.version)
        }
    }

    // Helper methods to create aggregates in various states

    private fun createPendingAggregate(
        requesterId: UserId = UserId.generate(),
        projectId: ProjectId = ProjectId.generate(),
        vmName: VmName = VmName.of("test-vm-01")
    ): VmRequestAggregate {
        val metadata = TestMetadataFactory.create(userId = requesterId)
        return VmRequestAggregate.create(
            requesterId = requesterId,
            projectId = projectId,
            vmName = vmName,
            size = VmSize.M,
            justification = "Valid justification for testing",
            metadata = metadata
        )
    }

    private fun createApprovedAggregate(requesterId: UserId, adminId: UserId): VmRequestAggregate {
        val requestId = VmRequestId.generate()
        val projectId = ProjectId.generate()
        val vmName = VmName.of("approved-vm")
        val requesterMetadata = TestMetadataFactory.create(userId = requesterId)
        val adminMetadata = TestMetadataFactory.create(userId = adminId)

        return VmRequestAggregate.reconstitute(
            requestId,
            listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = VmSize.M,
                    justification = "Test justification",
                    metadata = requesterMetadata
                ),
                VmRequestApproved(
                    aggregateId = requestId,
                    vmName = vmName,
                    projectId = projectId,
                    requesterId = requesterId,
                    metadata = adminMetadata
                )
            )
        )
    }

    private fun createRejectedAggregate(requesterId: UserId, adminId: UserId): VmRequestAggregate {
        val requestId = VmRequestId.generate()
        val projectId = ProjectId.generate()
        val vmName = VmName.of("rejected-vm")
        val requesterMetadata = TestMetadataFactory.create(userId = requesterId)
        val adminMetadata = TestMetadataFactory.create(userId = adminId)

        return VmRequestAggregate.reconstitute(
            requestId,
            listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = VmSize.M,
                    justification = "Test justification",
                    metadata = requesterMetadata
                ),
                VmRequestRejected(
                    aggregateId = requestId,
                    reason = "Test rejection reason",
                    vmName = vmName,
                    projectId = projectId,
                    requesterId = requesterId,
                    metadata = adminMetadata
                )
            )
        )
    }

    private fun createCancelledAggregate(requesterId: UserId): VmRequestAggregate {
        val aggregate = createPendingAggregate(requesterId)
        aggregate.clearUncommittedEvents()
        aggregate.cancel(
            reason = "Test cancellation",
            metadata = TestMetadataFactory.create(userId = requesterId)
        )
        return aggregate
    }
}

package de.acci.dcm.domain.vmrequest.events

import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a VM request has been rejected by an admin.
 *
 * Emitted when an admin rejects a PENDING request with a mandatory reason.
 * The request transitions to REJECTED status (terminal state).
 *
 * Fields are denormalized for notification purposes in Story 2.12:
 * - [vmName] and [projectId] for email content
 * - [requesterId] to identify who should receive the rejection notification
 * - [requesterEmail] for sending the rejection notification
 * - [reason] explains why the request was rejected
 *
 * [requesterEmail] is denormalized for notifications (Story 2.12).
 * Note: [GDPR-DEBT] PII in events requires crypto-shredding in Epic 5.
 *
 * ## State Transition
 * PENDING → REJECTED
 *
 * ## Authorization
 * - Admin must have ADMIN role
 * - Admin cannot reject their own request (separation of duties)
 *
 * Use the [create] factory method to construct instances with validation.
 */
public data class VmRequestRejected(
    /** Unique identifier for this VM request */
    val aggregateId: VmRequestId,
    /** Mandatory reason for rejection ([MIN_REASON_LENGTH] to [MAX_REASON_LENGTH] characters) */
    val reason: String,
    /** VM name (denormalized for notifications) */
    val vmName: VmName,
    /** Project this VM belongs to (denormalized for notifications) */
    val projectId: ProjectId,
    /** Original requester who will receive the rejection notification */
    val requesterId: UserId,
    /** Requester's email for notifications (denormalized from aggregate state) */
    val requesterEmail: String,
    /** Event metadata (tenant, userId=admin who rejected, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "VmRequest"

        /** Minimum length for rejection reason */
        public const val MIN_REASON_LENGTH: Int = 10

        /** Maximum length for rejection reason (same as VmRequestCancelled) */
        public const val MAX_REASON_LENGTH: Int = 500

        /**
         * Creates a VmRequestRejected event with validation.
         *
         * @param aggregateId The VM request ID
         * @param reason Mandatory rejection reason
         * @param vmName VM name for notifications
         * @param projectId Project for notifications
         * @param requesterId Original requester for notifications
         * @param requesterEmail Email for sending rejection notification
         * @param metadata Event metadata
         * @throws IllegalArgumentException if reason is shorter than [MIN_REASON_LENGTH] or exceeds [MAX_REASON_LENGTH]
         */
        public fun create(
            aggregateId: VmRequestId,
            reason: String,
            vmName: VmName,
            projectId: ProjectId,
            requesterId: UserId,
            requesterEmail: String,
            metadata: EventMetadata
        ): VmRequestRejected {
            require(reason.length >= MIN_REASON_LENGTH) {
                "Rejection reason must be at least $MIN_REASON_LENGTH characters"
            }
            require(reason.length <= MAX_REASON_LENGTH) {
                "Rejection reason must not exceed $MAX_REASON_LENGTH characters"
            }
            return VmRequestRejected(
                aggregateId = aggregateId,
                reason = reason,
                vmName = vmName,
                projectId = projectId,
                requesterId = requesterId,
                requesterEmail = requesterEmail,
                metadata = metadata
            )
        }
    }
}

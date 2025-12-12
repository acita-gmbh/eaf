package de.acci.dcm.domain.vmrequest.events

import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a VM request has been approved by an admin.
 *
 * Emitted when an admin approves a PENDING request. The request transitions
 * to APPROVED status, ready for provisioning in Epic 3.
 *
 * Fields are denormalized for notification purposes in Story 2.12:
 * - [vmName] and [projectId] for email content
 * - [requesterId] to identify who should receive the notification
 * - [requesterEmail] for sending the approval notification
 *
 * [requesterEmail] is denormalized for notifications (Story 2.12).
 * Note: [GDPR-DEBT] PII in events requires crypto-shredding in Epic 5.
 *
 * ## State Transition
 * PENDING → APPROVED
 *
 * ## Authorization
 * - Admin must have ADMIN role
 * - Admin cannot approve their own request (separation of duties)
 */
public data class VmRequestApproved(
    /** Unique identifier for this VM request */
    val aggregateId: VmRequestId,
    /** VM name (denormalized for notifications) */
    val vmName: VmName,
    /** Project this VM belongs to (denormalized for notifications) */
    val projectId: ProjectId,
    /** Original requester who will receive the approval notification */
    val requesterId: UserId,
    /** Requester's email for notifications (denormalized from aggregate state) */
    val requesterEmail: String,
    /** Event metadata (tenant, userId=admin who approved, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "VmRequest"
    }
}

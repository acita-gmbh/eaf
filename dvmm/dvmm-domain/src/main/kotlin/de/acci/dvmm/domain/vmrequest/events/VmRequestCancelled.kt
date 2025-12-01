package de.acci.dvmm.domain.vmrequest.events

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a VM request has been cancelled by the requester.
 *
 * Emitted when a user cancels their own PENDING request before admin approval.
 * The request transitions to CANCELLED status (terminal state).
 */
public data class VmRequestCancelled(
    /** Unique identifier for this VM request */
    val aggregateId: VmRequestId,
    /** Optional reason for cancellation */
    val reason: String?,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "VmRequest"
    }
}

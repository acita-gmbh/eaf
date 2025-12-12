package de.acci.dcm.domain.vmrequest.events

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a VM request has been cancelled by the requester.
 *
 * Emitted when a user cancels their own PENDING request before admin approval.
 * The request transitions to CANCELLED status (terminal state).
 *
 * Use the [create] factory method to construct instances with validation.
 */
public data class VmRequestCancelled(
    /** Unique identifier for this VM request */
    val aggregateId: VmRequestId,
    /** Optional reason for cancellation (max [MAX_REASON_LENGTH] characters) */
    val reason: String?,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "VmRequest"

        /** Maximum length for cancellation reason */
        public const val MAX_REASON_LENGTH: Int = 500

        /**
         * Creates a VmRequestCancelled event with validation.
         *
         * @param aggregateId The VM request ID
         * @param reason Optional cancellation reason
         * @param metadata Event metadata
         * @throws IllegalArgumentException if reason exceeds [MAX_REASON_LENGTH] characters
         */
        public fun create(
            aggregateId: VmRequestId,
            reason: String?,
            metadata: EventMetadata
        ): VmRequestCancelled {
            require(reason == null || reason.length <= MAX_REASON_LENGTH) {
                "Cancellation reason must not exceed $MAX_REASON_LENGTH characters"
            }
            return VmRequestCancelled(
                aggregateId = aggregateId,
                reason = reason,
                metadata = metadata
            )
        }
    }
}

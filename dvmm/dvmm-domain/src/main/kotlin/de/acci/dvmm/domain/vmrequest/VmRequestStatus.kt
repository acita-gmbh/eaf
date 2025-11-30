package de.acci.dvmm.domain.vmrequest

/**
 * Lifecycle status of a VM request.
 *
 * State transitions:
 * - PENDING -> APPROVED, REJECTED, CANCELLED
 * - APPROVED -> PROVISIONING
 * - PROVISIONING -> READY, FAILED
 * - REJECTED, CANCELLED, READY, FAILED -> (terminal states)
 */
public enum class VmRequestStatus {
    /** Request submitted, awaiting admin approval */
    PENDING,

    /** Request approved by admin, queued for provisioning */
    APPROVED,

    /** Request rejected by admin with reason */
    REJECTED,

    /** Request cancelled by requester before approval */
    CANCELLED,

    /** VM is being provisioned in vCenter */
    PROVISIONING,

    /** VM successfully provisioned and ready for use */
    READY,

    /** VM provisioning failed due to technical error */
    FAILED;

    /** Returns true if this is a terminal state that cannot transition. */
    public fun isTerminal(): Boolean = this in setOf(REJECTED, CANCELLED, READY, FAILED)

    /** Returns true if admin action (approve/reject) is allowed. */
    public fun canBeActedOnByAdmin(): Boolean = this == PENDING
}

package de.acci.dcm.domain.vm

/**
 * Lifecycle status of a Virtual Machine.
 *
 * State transitions:
 * - PROVISIONING -> READY, FAILED
 * - READY -> RUNNING, STOPPED, DELETED
 * - RUNNING -> STOPPED, DELETED
 * - STOPPED -> RUNNING, DELETED
 * - FAILED, DELETED -> (terminal states)
 */
public enum class VmStatus {
    /** Initial state when provisioning starts */
    PROVISIONING,

    /** VM successfully provisioned and ready for use (initial state after provisioning) */
    READY,

    /** VM is running and active (confirmed via live vCenter query) */
    RUNNING,

    /** VM is powered off */
    STOPPED,

    /** VM has been deleted */
    DELETED,

    /** Provisioning failed */
    FAILED
}

package de.acci.dvmm.domain.vm

/**
 * Lifecycle status of a Virtual Machine.
 */
public enum class VmStatus {
    /** Initial state when provisioning starts */
    PROVISIONING,

    /** VM is running and active */
    RUNNING,

    /** VM is powered off */
    STOPPED,

    /** VM has been deleted */
    DELETED,

    /** Provisioning failed */
    FAILED
}

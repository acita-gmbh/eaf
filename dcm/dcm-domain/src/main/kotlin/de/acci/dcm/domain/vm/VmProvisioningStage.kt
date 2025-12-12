package de.acci.dcm.domain.vm

/**
 * Represents the stages of VM provisioning as reported by the vSphere adapter.
 *
 * Note: The frontend UI may show additional stages (like "Request Created") that
 * are not part of this enum - those are UI-only concepts not signaled by vSphere.
 */
public enum class VmProvisioningStage {
    CLONING,
    CONFIGURING,
    POWERING_ON,
    WAITING_FOR_NETWORK,
    READY
}
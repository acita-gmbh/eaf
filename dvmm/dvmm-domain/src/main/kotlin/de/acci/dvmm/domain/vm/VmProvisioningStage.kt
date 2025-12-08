package de.acci.dvmm.domain.vm

public enum class VmProvisioningStage {
    CREATED,
    CLONING,
    CONFIGURING,
    POWERING_ON,
    WAITING_FOR_NETWORK,
    READY
}
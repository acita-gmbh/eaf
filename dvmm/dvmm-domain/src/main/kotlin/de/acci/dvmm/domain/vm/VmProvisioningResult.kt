package de.acci.dvmm.domain.vm

/**
 * Value object representing the result of a successful VM provisioning operation.
 *
 * Contains all information gathered during the provisioning process that needs
 * to be stored in domain events.
 *
 * @property vmwareVmId VMware MoRef for the created VM (e.g., "vm-12345")
 * @property ipAddress Detected IP address via VMware Tools (null if timeout)
 * @property hostname The configured hostname for the VM
 * @property warningMessage Optional warning message (e.g., "IP detection timed out")
 */
public data class VmProvisioningResult(
    val vmwareVmId: VmwareVmId,
    val ipAddress: String?,
    val hostname: String,
    val warningMessage: String?
) {
    init {
        require(hostname.isNotBlank()) { "Hostname must not be blank" }
    }

    /**
     * Whether IP detection was successful.
     */
    public val hasIpAddress: Boolean
        get() = ipAddress != null

    /**
     * Whether there are any warnings to report.
     */
    public val hasWarning: Boolean
        get() = warningMessage != null
}

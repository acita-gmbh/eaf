package de.acci.dcm.domain.vm.events

import de.acci.dcm.domain.vm.VmId
import de.acci.dcm.domain.vm.VmwareVmId
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Event emitted when VM provisioning completes successfully.
 *
 * Marks the transition from PROVISIONING to READY status. Contains
 * the VMware-assigned identifiers and network information.
 *
 * @property aggregateId The VM aggregate ID
 * @property requestId The originating request ID for traceability
 * @property vmwareVmId VMware MoRef for the created VM
 * @property ipAddress Detected IP address (null if VMware Tools timed out)
 * @property hostname The configured hostname
 * @property warningMessage Optional warning (e.g., "IP detection timed out")
 */
public data class VmProvisioned(
    public val aggregateId: VmId,
    public val requestId: VmRequestId,
    public val vmwareVmId: VmwareVmId,
    public val ipAddress: String?,
    public val hostname: String,
    public val warningMessage: String?,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "Vm"
    }
}

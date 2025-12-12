package de.acci.dcm.domain.vmrequest.events

import de.acci.dcm.domain.vm.VmwareVmId
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import java.time.Instant

/**
 * Event indicating that VM provisioning has completed and the VM is ready for use.
 *
 * Marks the transition from PROVISIONING to READY status on the request.
 *
 * @property aggregateId The VM request aggregate ID
 * @property vmwareVmId VMware MoRef for the created VM
 * @property ipAddress Detected IP address (null if VMware Tools timed out)
 * @property hostname The configured hostname
 * @property provisionedAt Timestamp when provisioning completed
 * @property warningMessage Optional warning (e.g., "IP detection timed out")
 */
public data class VmRequestReady(
    val aggregateId: VmRequestId,
    val vmwareVmId: VmwareVmId,
    val ipAddress: String?,
    val hostname: String,
    val provisionedAt: Instant,
    val warningMessage: String?,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = "VmRequest"
}

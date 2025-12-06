package de.acci.dvmm.domain.vm.events

import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Event emitted when VM provisioning fails.
 *
 * This event signals that the provisioning process could not complete,
 * allowing downstream systems to react (e.g., update request status, notify user).
 */
public data class VmProvisioningFailed(
    public val aggregateId: VmId,
    public val requestId: VmRequestId,
    public val reason: String,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = "vm"
}

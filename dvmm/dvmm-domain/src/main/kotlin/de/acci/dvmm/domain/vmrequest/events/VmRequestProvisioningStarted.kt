package de.acci.dvmm.domain.vmrequest.events

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Event indicating that the VM request has moved to PROVISIONING state.
 * Triggered when the provisioning process (VmAggregate) has started.
 */
public data class VmRequestProvisioningStarted(
    val aggregateId: VmRequestId,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = "VmRequest"
}

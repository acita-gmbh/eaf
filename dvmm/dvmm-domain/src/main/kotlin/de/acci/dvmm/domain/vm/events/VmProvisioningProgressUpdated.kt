package de.acci.dvmm.domain.vm.events

import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

public data class VmProvisioningProgressUpdated(
    public val aggregateId: VmId,
    public val requestId: VmRequestId,
    public val currentStage: VmProvisioningStage,
    public val details: String,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "Vm"
    }
}
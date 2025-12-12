package de.acci.dvmm.domain.vm.events

import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Event emitted when VM provisioning makes progress (e.g. step completed).
 */
public data class VmProvisioningProgressUpdated(
    /** The VM aggregate ID */
    public val aggregateId: VmId,
    /** The related Request ID */
    public val requestId: VmRequestId,
    /** The current stage reached */
    public val currentStage: VmProvisioningStage,
    /** Human-readable details */
    public val details: String,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "Vm"
    }
}
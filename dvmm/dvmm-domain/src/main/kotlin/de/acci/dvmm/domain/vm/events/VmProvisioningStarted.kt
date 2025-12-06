package de.acci.dvmm.domain.vm.events

import de.acci.dvmm.domain.vm.VmId
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Event emitted when VM provisioning process starts.
 */
public data class VmProvisioningStarted(
    public val aggregateId: VmId,
    public val requestId: VmRequestId,
    public val projectId: ProjectId,
    public val vmName: VmName,
    public val size: VmSize,
    public val requesterId: UserId,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "Vm"
    }
}

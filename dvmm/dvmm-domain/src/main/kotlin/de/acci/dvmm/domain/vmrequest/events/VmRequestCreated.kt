package de.acci.dvmm.domain.vmrequest.events

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Domain event indicating a new VM request has been created.
 *
 * Emitted when a user submits a VM request form.
 * The request enters PENDING status awaiting admin approval.
 */
public data class VmRequestCreated(
    /** Unique identifier for this VM request */
    val aggregateId: VmRequestId,
    /** Project this VM belongs to */
    val projectId: ProjectId,
    /** Validated VM name */
    val vmName: VmName,
    /** Selected VM size */
    val size: VmSize,
    /** Business justification for the request */
    val justification: String,
    /** Event metadata (tenant, user, correlation, timestamp) */
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "VmRequest"
    }
}

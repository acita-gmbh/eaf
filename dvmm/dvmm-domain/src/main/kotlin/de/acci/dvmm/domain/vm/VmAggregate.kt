package de.acci.dvmm.domain.vm

import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.eventsourcing.aggregate.AggregateRoot
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata

/**
 * Aggregate Root for a Virtual Machine.
 * Handles the lifecycle of the VM resource.
 */
public class VmAggregate private constructor(
    override val id: VmId
) : AggregateRoot<VmId>() {

    public var tenantId: TenantId = TenantId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    public var requestId: VmRequestId = VmRequestId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    public var projectId: ProjectId = ProjectId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    public var vmName: VmName = VmName.of("placeholder")
        private set

    public var size: VmSize = VmSize.M
        private set

    public var requesterId: UserId = UserId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    public var status: VmStatus = VmStatus.PROVISIONING
        private set

    public var failureReason: String? = null
        private set

    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is VmProvisioningStarted -> apply(event)
            is VmProvisioningFailed -> apply(event)
        }
    }

    private fun apply(event: VmProvisioningStarted) {
        tenantId = event.metadata.tenantId
        requestId = event.requestId
        projectId = event.projectId
        vmName = event.vmName
        size = event.size
        requesterId = event.requesterId
        status = VmStatus.PROVISIONING
    }

    private fun apply(event: VmProvisioningFailed) {
        status = VmStatus.FAILED
        failureReason = event.reason
    }

    /**
     * Marks the VM provisioning as failed.
     *
     * @param reason Description of why provisioning failed
     * @param metadata Event metadata including tenant and correlation info
     */
    public fun markFailed(reason: String, metadata: EventMetadata) {
        check(status == VmStatus.PROVISIONING) {
            "Cannot mark VM ${id.value} as failed: expected status PROVISIONING, but was $status"
        }

        val event = VmProvisioningFailed(
            aggregateId = id,
            requestId = requestId,
            reason = reason,
            metadata = metadata
        )
        applyEvent(event)
    }

    public companion object {
        public fun startProvisioning(
            requestId: VmRequestId,
            projectId: ProjectId,
            vmName: VmName,
            size: VmSize,
            requesterId: UserId,
            metadata: EventMetadata
        ): VmAggregate {
            val id = VmId.generate()
            val aggregate = VmAggregate(id)

            val event = VmProvisioningStarted(
                aggregateId = id,
                requestId = requestId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                requesterId = requesterId,
                metadata = metadata
            )

            aggregate.applyEvent(event)
            return aggregate
        }

        public fun reconstitute(id: VmId, events: List<DomainEvent>): VmAggregate {
            val aggregate = VmAggregate(id)
            events.forEach { event ->
                aggregate.applyEvent(event, isReplay = true)
            }
            return aggregate
        }
    }
}

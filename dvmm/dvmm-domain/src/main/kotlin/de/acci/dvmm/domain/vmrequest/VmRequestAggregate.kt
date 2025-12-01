package de.acci.dvmm.domain.vmrequest

import de.acci.dvmm.domain.exceptions.InvalidStateException
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.aggregate.AggregateRoot

/**
 * Aggregate root for VM requests.
 *
 * Represents a request for a virtual machine that goes through the
 * approval workflow: PENDING -> APPROVED/REJECTED -> PROVISIONING -> READY/FAILED.
 *
 * ## Usage
 *
 * Create a new VM request:
 * ```kotlin
 * val aggregate = VmRequestAggregate.create(
 *     requesterId = userId,
 *     projectId = projectId,
 *     vmName = VmName.of("web-server-01"),
 *     size = VmSize.M,
 *     justification = "New web application server",
 *     metadata = metadata
 * )
 * eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, aggregate.version)
 * ```
 *
 * Reconstitute from events:
 * ```kotlin
 * val events = eventStore.loadEvents(aggregateId)
 * val aggregate = VmRequestAggregate.reconstitute(VmRequestId(aggregateId), events)
 * ```
 */
public class VmRequestAggregate private constructor(
    override val id: VmRequestId
) : AggregateRoot<VmRequestId>() {

    /** Tenant this request belongs to */
    public var tenantId: TenantId = TenantId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    /** User who submitted the request */
    public var requesterId: UserId = UserId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    /** Project this VM belongs to */
    public var projectId: ProjectId = ProjectId.fromString("00000000-0000-0000-0000-000000000000")
        private set

    /** Validated VM name */
    public var vmName: VmName = VmName.of("placeholder")
        private set

    /** Selected VM size */
    public var size: VmSize = VmSize.M
        private set

    /** Business justification */
    public var justification: String = ""
        private set

    /** Current request status */
    public var status: VmRequestStatus = VmRequestStatus.PENDING
        private set

    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is VmRequestCreated -> apply(event)
            is VmRequestCancelled -> apply(event)
            // Future: VmRequestApproved, VmRequestRejected, etc.
        }
    }

    private fun apply(event: VmRequestCreated) {
        tenantId = event.metadata.tenantId
        requesterId = event.metadata.userId
        projectId = event.projectId
        vmName = event.vmName
        size = event.size
        justification = event.justification
        status = VmRequestStatus.PENDING
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: VmRequestCancelled) {
        status = VmRequestStatus.CANCELLED
    }

    /**
     * Cancels this VM request.
     *
     * Only PENDING requests can be cancelled. Attempting to cancel an already
     * cancelled request is idempotent (no-op). Cancelling requests in other
     * states throws [InvalidStateException].
     *
     * @param reason Optional reason for cancellation (max [VmRequestCancelled.MAX_REASON_LENGTH] chars)
     * @param metadata Event metadata with tenant context
     * @throws InvalidStateException if request is not in PENDING or CANCELLED state
     * @throws IllegalArgumentException if reason exceeds max length
     */
    public fun cancel(reason: String?, metadata: EventMetadata) {
        // Idempotent: already cancelled, no-op
        if (status == VmRequestStatus.CANCELLED) {
            return
        }

        // Only PENDING requests can be cancelled
        if (status != VmRequestStatus.PENDING) {
            throw InvalidStateException(
                currentState = status,
                expectedState = VmRequestStatus.PENDING,
                operation = "cancel"
            )
        }

        val event = VmRequestCancelled.create(
            aggregateId = id,
            reason = reason,
            metadata = metadata
        )

        applyEvent(event)
    }

    public companion object {
        /**
         * Creates a new VM request.
         *
         * @param requesterId User submitting the request
         * @param projectId Project the VM belongs to
         * @param vmName Validated VM name
         * @param size Selected VM size
         * @param justification Business justification (minimum 10 characters)
         * @param metadata Event metadata with tenant context
         * @return New VmRequestAggregate with VmRequestCreated event
         * @throws IllegalArgumentException if justification is too short
         */
        public fun create(
            requesterId: UserId,
            projectId: ProjectId,
            vmName: VmName,
            size: VmSize,
            justification: String,
            metadata: EventMetadata
        ): VmRequestAggregate {
            require(justification.length >= 10) {
                "Justification must be at least 10 characters"
            }

            val id = VmRequestId.generate()
            val aggregate = VmRequestAggregate(id)

            val event = VmRequestCreated(
                aggregateId = id,
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification,
                metadata = metadata
            )

            aggregate.applyEvent(event)
            return aggregate
        }

        /**
         * Reconstitutes aggregate state from a sequence of events.
         *
         * @param id The aggregate identifier
         * @param events Historical events to replay
         * @return Reconstituted aggregate
         */
        public fun reconstitute(
            id: VmRequestId,
            events: List<DomainEvent>
        ): VmRequestAggregate {
            val aggregate = VmRequestAggregate(id)
            events.forEach { event ->
                aggregate.applyEvent(event, isReplay = true)
            }
            return aggregate
        }
    }
}

package de.acci.dvmm.domain.vmrequest

import de.acci.dvmm.domain.exceptions.InvalidStateException
import de.acci.dvmm.domain.exceptions.SelfApprovalException
import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.dvmm.domain.vmrequest.events.VmRequestProvisioningStarted
import de.acci.dvmm.domain.vmrequest.events.VmRequestReady
import de.acci.dvmm.domain.vmrequest.events.VmRequestRejected
import de.acci.eaf.core.types.TenantId
import java.time.Instant
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

    /** Requester's email for notifications (denormalized from JWT at creation) */
    public var requesterEmail: String = ""
        private set

    /** Current request status */
    public var status: VmRequestStatus = VmRequestStatus.PENDING
        private set

    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is VmRequestCreated -> apply(event)
            is VmRequestCancelled -> apply(event)
            is VmRequestApproved -> apply(event)
            is VmRequestRejected -> apply(event)
            is VmRequestProvisioningStarted -> apply(event)
            is VmRequestReady -> apply(event)
        }
    }

    private fun apply(event: VmRequestCreated) {
        tenantId = event.metadata.tenantId
        requesterId = event.metadata.userId
        projectId = event.projectId
        vmName = event.vmName
        size = event.size
        justification = event.justification
        requesterEmail = event.requesterEmail
        status = VmRequestStatus.PENDING
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: VmRequestCancelled) {
        status = VmRequestStatus.CANCELLED
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: VmRequestApproved) {
        status = VmRequestStatus.APPROVED
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: VmRequestRejected) {
        status = VmRequestStatus.REJECTED
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: VmRequestProvisioningStarted) {
        status = VmRequestStatus.PROVISIONING
    }

    private fun apply(@Suppress("UNUSED_PARAMETER") event: VmRequestReady) {
        status = VmRequestStatus.READY
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

    /**
     * Approves this VM request (admin action).
     *
     * Only PENDING requests can be approved. Admins cannot approve their own requests
     * (separation of duties).
     *
     * @param adminId The admin performing the approval
     * @param metadata Event metadata with tenant context
     * @throws InvalidStateException if request is not in PENDING state
     * @throws SelfApprovalException if admin is trying to approve their own request
     */
    public fun approve(adminId: UserId, metadata: EventMetadata) {
        // Separation of duties: cannot approve own request
        if (requesterId == adminId) {
            throw SelfApprovalException(adminId = adminId, operation = "approve")
        }

        // Only PENDING requests can be approved
        if (!status.canBeActedOnByAdmin()) {
            throw InvalidStateException(
                currentState = status,
                expectedState = VmRequestStatus.PENDING,
                operation = "approve"
            )
        }

        val event = VmRequestApproved(
            aggregateId = id,
            vmName = vmName,
            projectId = projectId,
            requesterId = requesterId,
            requesterEmail = requesterEmail,
            metadata = metadata
        )

        applyEvent(event)
    }

    /**
     * Rejects this VM request with a mandatory reason (admin action).
     *
     * Only PENDING requests can be rejected. Admins cannot reject their own requests
     * (separation of duties).
     *
     * @param adminId The admin performing the rejection
     * @param reason Mandatory rejection reason ([VmRequestRejected.MIN_REASON_LENGTH] to [VmRequestRejected.MAX_REASON_LENGTH] chars)
     * @param metadata Event metadata with tenant context
     * @throws InvalidStateException if request is not in PENDING state
     * @throws SelfApprovalException if admin is trying to reject their own request
     * @throws IllegalArgumentException if reason is too short or too long
     */
    public fun reject(adminId: UserId, reason: String, metadata: EventMetadata) {
        // Separation of duties: cannot reject own request
        if (requesterId == adminId) {
            throw SelfApprovalException(adminId = adminId, operation = "reject")
        }

        // Only PENDING requests can be rejected
        if (!status.canBeActedOnByAdmin()) {
            throw InvalidStateException(
                currentState = status,
                expectedState = VmRequestStatus.PENDING,
                operation = "reject"
            )
        }

        // Validation happens in VmRequestRejected.create()
        val event = VmRequestRejected.create(
            aggregateId = id,
            reason = reason,
            vmName = vmName,
            projectId = projectId,
            requesterId = requesterId,
            requesterEmail = requesterEmail,
            metadata = metadata
        )

        applyEvent(event)
    }

    /**
     * Marks the request as PROVISIONING.
     *
     * Triggered when the provisioning process starts.
     *
     * @param metadata Event metadata
     * @throws InvalidStateException if request is not in APPROVED state
     */
    public fun markProvisioning(metadata: EventMetadata) {
        if (status != VmRequestStatus.APPROVED) {
            throw InvalidStateException(
                currentState = status,
                expectedState = VmRequestStatus.APPROVED,
                operation = "markProvisioning"
            )
        }

        applyEvent(VmRequestProvisioningStarted(id, metadata))
    }

    /**
     * Marks the request as READY with VM details.
     *
     * Triggered when VM provisioning completes successfully.
     *
     * @param vmwareVmId VMware MoRef for the created VM
     * @param ipAddress Detected IP address (null if VMware Tools timed out)
     * @param hostname The configured hostname
     * @param provisionedAt Timestamp when VM became ready (injectable for testability)
     * @param warningMessage Optional warning (e.g., "IP detection timed out")
     * @param metadata Event metadata
     * @throws InvalidStateException if request is not in PROVISIONING state
     */
    public fun markReady(
        vmwareVmId: VmwareVmId,
        ipAddress: String?,
        hostname: String,
        provisionedAt: Instant,
        warningMessage: String?,
        metadata: EventMetadata
    ) {
        if (status != VmRequestStatus.PROVISIONING) {
            throw InvalidStateException(
                currentState = status,
                expectedState = VmRequestStatus.PROVISIONING,
                operation = "markReady"
            )
        }

        applyEvent(
            VmRequestReady(
                aggregateId = id,
                vmwareVmId = vmwareVmId,
                ipAddress = ipAddress,
                hostname = hostname,
                provisionedAt = provisionedAt,
                warningMessage = warningMessage,
                metadata = metadata
            )
        )
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
         * @param requesterEmail Email address for notifications (extracted from JWT)
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
            requesterEmail: String,
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
                requesterEmail = requesterEmail,
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

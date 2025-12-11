package de.acci.dvmm.domain.vm

import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vm.events.VmProvisioned
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.eventsourcing.aggregate.AggregateRoot
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import java.time.Instant

import de.acci.dvmm.domain.vm.events.VmProvisioningProgressUpdated

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

    /** Machine-readable error code for the failure (e.g., "CONNECTION_TIMEOUT") */
    public var failureErrorCode: String? = null
        private set

    /** Number of retry attempts made before final failure */
    public var failureRetryCount: Int? = null
        private set

    /** Timestamp of the final failed attempt */
    public var failureLastAttemptAt: Instant? = null
        private set

    /** VMware MoRef assigned to this VM after provisioning */
    public var vmwareVmId: VmwareVmId? = null
        private set

    /** IP address detected via VMware Tools (null if detection timed out) */
    public var ipAddress: String? = null
        private set

    /** Hostname configured during provisioning */
    public var hostname: String? = null
        private set

    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is VmProvisioningStarted -> apply(event)
            is VmProvisioningFailed -> apply(event)
            is VmProvisioned -> apply(event)
            is VmProvisioningProgressUpdated -> apply(event)
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
        failureReason = event.errorMessage
        failureErrorCode = event.errorCode
        failureRetryCount = event.retryCount
        failureLastAttemptAt = event.lastAttemptAt
    }

    private fun apply(event: VmProvisioned) {
        status = VmStatus.READY
        vmwareVmId = event.vmwareVmId
        ipAddress = event.ipAddress
        hostname = event.hostname
    }

    @Suppress("UNUSED_PARAMETER")
    private fun apply(event: VmProvisioningProgressUpdated) {
        // No state change needed in the aggregate for progress updates.
        // Status remains PROVISIONING until completion.
    }

    /**
     * Updates the provisioning progress stage.
     *
     * @param stage The new provisioning stage
     * @param metadata Event metadata including tenant and correlation info
     */
    public fun updateProgress(stage: VmProvisioningStage, metadata: EventMetadata) {
        check(status == VmStatus.PROVISIONING) {
            "Cannot update progress for VM ${id.value}: expected status PROVISIONING, but was $status"
        }

        val event = VmProvisioningProgressUpdated(
            aggregateId = id,
            requestId = requestId,
            currentStage = stage,
            details = "Provisioning stage updated to $stage",
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Marks the VM provisioning as failed.
     *
     * @param errorCode Machine-readable error code (e.g., "CONNECTION_TIMEOUT", "INSUFFICIENT_RESOURCES")
     * @param errorMessage User-friendly error message suitable for display
     * @param retryCount Number of retry attempts made (including the initial attempt)
     * @param lastAttemptAt Timestamp of the final failed attempt
     * @param metadata Event metadata including tenant and correlation info
     */
    public fun markFailed(
        errorCode: String,
        errorMessage: String,
        retryCount: Int,
        lastAttemptAt: Instant,
        metadata: EventMetadata
    ) {
        check(status == VmStatus.PROVISIONING) {
            "Cannot mark VM ${id.value} as failed: expected status PROVISIONING, but was $status"
        }

        val event = VmProvisioningFailed(
            aggregateId = id,
            requestId = requestId,
            reason = errorMessage, // Legacy field for backward compatibility
            errorCode = errorCode,
            errorMessage = errorMessage,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
            metadata = metadata
        )
        applyEvent(event)
    }

    /**
     * Marks the VM provisioning as failed (simplified version for permanent errors).
     *
     * @param reason Description of why provisioning failed (used as errorMessage)
     * @param metadata Event metadata including tenant and correlation info
     */
    @Deprecated(
        "Use markFailed(errorCode, errorMessage, retryCount, lastAttemptAt, metadata) instead",
        ReplaceWith("markFailed(\"UNKNOWN\", reason, 1, Instant.now(), metadata)")
    )
    public fun markFailed(reason: String, metadata: EventMetadata) {
        markFailed(
            errorCode = "UNKNOWN",
            errorMessage = reason,
            retryCount = 1,
            lastAttemptAt = Instant.now(),
            metadata = metadata
        )
    }

    /**
     * Marks the VM provisioning as successfully completed.
     *
     * @param vmwareVmId VMware MoRef for the created VM
     * @param ipAddress Detected IP address (null if VMware Tools timed out)
     * @param hostname The configured hostname
     * @param warningMessage Optional warning (e.g., "IP detection timed out")
     * @param metadata Event metadata including tenant and correlation info
     */
    public fun markProvisioned(
        vmwareVmId: VmwareVmId,
        ipAddress: String?,
        hostname: String,
        warningMessage: String?,
        metadata: EventMetadata
    ) {
        check(status == VmStatus.PROVISIONING) {
            "Cannot mark VM ${id.value} as provisioned: expected status PROVISIONING, but was $status"
        }

        val event = VmProvisioned(
            aggregateId = id,
            requestId = requestId,
            vmwareVmId = vmwareVmId,
            ipAddress = ipAddress,
            hostname = hostname,
            warningMessage = warningMessage,
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

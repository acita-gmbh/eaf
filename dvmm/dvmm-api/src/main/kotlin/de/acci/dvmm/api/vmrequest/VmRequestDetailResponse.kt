package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.TimelineEventItem
import de.acci.dvmm.application.vmrequest.VmRequestDetail
import java.time.Instant

/**
 * Response DTO for detailed VM request with timeline and VM runtime details.
 *
 * Story 3-7: Includes VM runtime information for provisioned VMs.
 *
 * @property id Unique identifier for this request
 * @property vmName Requested VM name
 * @property size VM size with resource specifications
 * @property justification Business justification for the request
 * @property status Current request status
 * @property projectName Display name of the project
 * @property requesterName Display name of the requester
 * @property createdAt When the request was created
 * @property timeline List of timeline events in chronological order
 * @property vmDetails VM runtime details (null if not yet provisioned)
 */
public data class VmRequestDetailResponse(
    val id: String,
    val vmName: String,
    val size: VmSizeResponse,
    val justification: String,
    val status: String,
    val projectName: String,
    val requesterName: String,
    val createdAt: Instant,
    val timeline: List<TimelineEventResponse>,
    val vmDetails: VmRuntimeDetailsResponse? = null
) {
    public companion object {
        public fun fromDomain(detail: VmRequestDetail): VmRequestDetailResponse =
            VmRequestDetailResponse(
                id = detail.id.value.toString(),
                vmName = detail.vmName,
                size = VmSizeResponse(
                    code = detail.size,
                    cpuCores = detail.cpuCores,
                    memoryGb = detail.memoryGb,
                    diskGb = detail.diskGb
                ),
                justification = detail.justification,
                status = detail.status,
                projectName = detail.projectName,
                requesterName = detail.requesterName,
                createdAt = detail.createdAt,
                timeline = detail.timeline.map { TimelineEventResponse.fromDomain(it) },
                vmDetails = VmRuntimeDetailsResponse.fromDomain(detail)
            )
    }
}

/**
 * VM runtime details for provisioned VMs.
 *
 * Story 3-7: Shows IP address, hostname, power state, and other runtime info.
 *
 * @property vmwareVmId VMware MoRef ID (e.g., vm-123)
 * @property ipAddress Primary IP address from VMware Tools
 * @property hostname Guest hostname from VMware Tools
 * @property powerState VM power state: POWERED_ON, POWERED_OFF, SUSPENDED
 * @property guestOs Detected guest OS from VMware Tools
 * @property lastSyncedAt Timestamp of last status sync from vSphere
 */
public data class VmRuntimeDetailsResponse(
    val vmwareVmId: String?,
    val ipAddress: String?,
    val hostname: String?,
    val powerState: String?,
    val guestOs: String?,
    val lastSyncedAt: Instant?
) {
    public companion object {
        /**
         * Creates VmRuntimeDetailsResponse from VmRequestDetail.
         * Returns null if no VM details are available (not yet provisioned).
         */
        public fun fromDomain(detail: VmRequestDetail): VmRuntimeDetailsResponse? {
            // Return null if VM hasn't been provisioned yet (no vmwareVmId)
            if (detail.vmwareVmId == null) {
                return null
            }
            return VmRuntimeDetailsResponse(
                vmwareVmId = detail.vmwareVmId,
                ipAddress = detail.ipAddress,
                hostname = detail.hostname,
                powerState = detail.powerState,
                guestOs = detail.guestOs,
                lastSyncedAt = detail.lastSyncedAt
            )
        }
    }
}

/**
 * Response DTO for a single timeline event.
 *
 * @property eventType Type of event (CREATED, APPROVED, REJECTED, CANCELLED, etc.)
 * @property actorName Display name of the user who performed the action (null for system events)
 * @property details Additional event details (e.g., rejection reason as JSON)
 * @property occurredAt When the event occurred
 */
public data class TimelineEventResponse(
    val eventType: String,
    val actorName: String?,
    val details: String?,
    val occurredAt: Instant
) {
    public companion object {
        public fun fromDomain(event: TimelineEventItem): TimelineEventResponse =
            TimelineEventResponse(
                eventType = event.eventType.name,
                actorName = event.actorName,
                details = event.details,
                occurredAt = event.occurredAt
            )
    }
}

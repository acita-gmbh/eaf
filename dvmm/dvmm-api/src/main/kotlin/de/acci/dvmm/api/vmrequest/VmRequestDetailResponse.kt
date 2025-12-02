package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.TimelineEventItem
import de.acci.dvmm.application.vmrequest.VmRequestDetail
import java.time.Instant

/**
 * Response DTO for detailed VM request with timeline.
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
    val timeline: List<TimelineEventResponse>
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
                timeline = detail.timeline.map { TimelineEventResponse.fromDomain(it) }
            )
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

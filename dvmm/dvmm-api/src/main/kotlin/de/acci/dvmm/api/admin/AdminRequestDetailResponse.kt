package de.acci.dvmm.api.admin

import de.acci.dvmm.api.vmrequest.TimelineEventResponse
import de.acci.dvmm.api.vmrequest.VmSizeResponse
import de.acci.dvmm.application.vmrequest.AdminRequestDetail
import de.acci.dvmm.application.vmrequest.VmRequestHistorySummary
import java.time.Instant

/**
 * Requester information for admin view.
 *
 * Story 2.10: Request Detail View (Admin)
 * AC 2: Requester Information displayed
 *
 * @property id Unique identifier for the requester
 * @property name Display name of the requester
 * @property email Email address of the requester
 * @property role Role/title of the requester
 */
public data class RequesterInfoResponse(
    val id: String,
    val name: String,
    val email: String,
    val role: String
)

/**
 * Summary of a requester's previous request for history display.
 *
 * Story 2.10: Request Detail View (Admin)
 * AC 6: Requester History shown
 *
 * @property id Unique identifier for the request
 * @property vmName Name of the VM requested
 * @property status Current status of the request
 * @property createdAt When the request was created
 */
public data class RequestHistorySummaryResponse(
    val id: String,
    val vmName: String,
    val status: String,
    val createdAt: Instant
) {
    public companion object {
        /**
         * Maps from application layer summary to API response DTO.
         */
        public fun fromSummary(summary: VmRequestHistorySummary): RequestHistorySummaryResponse =
            RequestHistorySummaryResponse(
                id = summary.id.value.toString(),
                vmName = summary.vmName,
                status = summary.status,
                createdAt = summary.createdAt
            )
    }
}

/**
 * Response DTO for admin request detail view.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Contains all information needed for admin decision-making:
 * - Request details (AC 3)
 * - Requester info (AC 2)
 * - Timeline events (AC 5)
 * - Requester history (AC 6)
 *
 * @property id Unique identifier for this request
 * @property vmName Requested VM name
 * @property size VM size with resource specifications
 * @property justification Business justification provided
 * @property status Current request status
 * @property projectName Display name of the project
 * @property requester Information about who made the request
 * @property timeline Status change history
 * @property requesterHistory Recent requests from same requester
 * @property createdAt When the request was created
 */
public data class AdminRequestDetailResponse(
    val id: String,
    val vmName: String,
    val size: VmSizeResponse,
    val justification: String,
    val status: String,
    val projectName: String,
    val requester: RequesterInfoResponse,
    val timeline: List<TimelineEventResponse>,
    val requesterHistory: List<RequestHistorySummaryResponse>,
    val createdAt: Instant
) {
    public companion object {
        /**
         * Maps from application layer AdminRequestDetail to API response DTO.
         */
        public fun fromDomain(detail: AdminRequestDetail): AdminRequestDetailResponse =
            AdminRequestDetailResponse(
                id = detail.id.value.toString(),
                vmName = detail.vmName,
                size = VmSizeResponse(
                    code = detail.size.code,
                    cpuCores = detail.size.cpuCores,
                    memoryGb = detail.size.memoryGb,
                    diskGb = detail.size.diskGb
                ),
                justification = detail.justification,
                status = detail.status,
                projectName = detail.projectName,
                requester = RequesterInfoResponse(
                    id = detail.requester.id.value.toString(),
                    name = detail.requester.name,
                    email = detail.requester.email,
                    role = detail.requester.role
                ),
                timeline = detail.timeline.map { TimelineEventResponse.fromDomain(it) },
                requesterHistory = detail.requesterHistory.map {
                    RequestHistorySummaryResponse.fromSummary(it)
                },
                createdAt = detail.createdAt
            )
    }
}

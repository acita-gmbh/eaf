package de.acci.dcm.api.admin

import de.acci.dcm.api.vmrequest.VmSizeResponse
import de.acci.dcm.application.vmrequest.VmRequestSummary
import java.time.Instant

/**
 * Response DTO for a pending VM request in the admin approval queue.
 *
 * Story 2.9: Admin Approval Queue (AC 2)
 *
 * @property id Unique identifier for this request
 * @property requesterName Name of the user who submitted the request
 * @property vmName Requested VM name
 * @property projectName Display name of the project
 * @property size VM size with resource specifications
 * @property createdAt When the request was created (used for age calculation)
 */
public data class PendingRequestResponse(
    val id: String,
    val requesterName: String,
    val vmName: String,
    val projectName: String,
    val size: VmSizeResponse,
    val createdAt: Instant
) {
    public companion object {
        /**
         * Maps from application layer VmRequestSummary to API response DTO.
         */
        public fun fromSummary(summary: VmRequestSummary): PendingRequestResponse =
            PendingRequestResponse(
                id = summary.id.value.toString(),
                requesterName = summary.requesterName,
                vmName = summary.vmName,
                projectName = summary.projectName,
                size = VmSizeResponse.fromDomain(summary.size),
                createdAt = summary.createdAt
            )
    }
}

/**
 * Paginated response for pending requests.
 *
 * Story 2.9: Admin Approval Queue (AC 1)
 *
 * @property items List of pending requests for current page
 * @property page Current page number (0-indexed)
 * @property size Number of items per page
 * @property totalElements Total number of pending requests
 * @property totalPages Total number of pages
 */
public data class PendingRequestsPageResponse(
    val items: List<PendingRequestResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
) {
    public companion object {
        /**
         * Maps from application layer PagedResponse to API response DTO.
         */
        public fun fromPagedResponse(
            response: de.acci.eaf.eventsourcing.projection.PagedResponse<VmRequestSummary>
        ): PendingRequestsPageResponse =
            PendingRequestsPageResponse(
                items = response.items.map { PendingRequestResponse.fromSummary(it) },
                page = response.page,
                size = response.size,
                totalElements = response.totalElements,
                totalPages = response.totalPages
            )
    }
}

/**
 * Response DTO for a project in the filter dropdown.
 *
 * Story 2.9: Admin Approval Queue (AC 5)
 *
 * @property id Project unique identifier
 * @property name Project display name
 */
public data class ProjectResponse(
    val id: String,
    val name: String
) {
    public companion object {
        /**
         * Maps from application layer ProjectSummary to API response DTO.
         */
        public fun fromSummary(summary: de.acci.dcm.application.vmrequest.ProjectSummary): ProjectResponse =
            ProjectResponse(
                id = summary.id.value.toString(),
                name = summary.name
            )
    }
}

package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.VmRequestSummary
import de.acci.eaf.eventsourcing.projection.PagedResponse
import java.time.Instant

/**
 * Response DTO for paginated VM requests list.
 *
 * @property items List of VM request summaries in the current page
 * @property page Current page number (zero-based)
 * @property size Number of items per page
 * @property totalElements Total number of elements across all pages
 * @property totalPages Total number of pages
 * @property hasNext Whether there is a next page
 * @property hasPrevious Whether there is a previous page
 */
public data class PagedVmRequestsResponse(
    val items: List<VmRequestSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    public companion object {
        public fun fromDomain(pagedResponse: PagedResponse<VmRequestSummary>): PagedVmRequestsResponse =
            PagedVmRequestsResponse(
                items = pagedResponse.items.map { VmRequestSummaryResponse.fromDomain(it) },
                page = pagedResponse.page,
                size = pagedResponse.size,
                totalElements = pagedResponse.totalElements,
                totalPages = pagedResponse.totalPages,
                hasNext = pagedResponse.hasNext,
                hasPrevious = pagedResponse.hasPrevious
            )
    }
}

/**
 * Response DTO for a single VM request summary.
 *
 * ## Field Design Notes
 *
 * - **tenantId/requesterId omitted**: These are internal identifiers used for
 *   authorization and tenant isolation. They are intentionally not exposed in
 *   the API response. Row-Level Security (RLS) at the database layer ensures
 *   users only see their own tenant's data.
 *
 * - **size as object**: The size is returned as a [VmSizeResponse] object
 *   containing cpuCores, memoryGb, and diskGb fields for display purposes.
 *   Frontend may flatten these for convenience.
 *
 * - **version omitted in list view**: Version is used for optimistic locking
 *   in write operations but is not needed for read-only list display.
 *   Individual request detail endpoints include version when needed.
 */
public data class VmRequestSummaryResponse(
    val id: String,
    val requesterName: String,
    val projectId: String,
    val projectName: String,
    val vmName: String,
    val size: VmSizeResponse,
    val justification: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    public companion object {
        public fun fromDomain(summary: VmRequestSummary): VmRequestSummaryResponse =
            VmRequestSummaryResponse(
                id = summary.id.value.toString(),
                requesterName = summary.requesterName,
                projectId = summary.projectId.value.toString(),
                projectName = summary.projectName,
                vmName = summary.vmName,
                size = VmSizeResponse(
                    code = summary.size.name,
                    cpuCores = summary.size.cpuCores,
                    memoryGb = summary.size.memoryGb,
                    diskGb = summary.size.diskGb
                ),
                justification = summary.justification,
                status = summary.status.name,
                createdAt = summary.createdAt,
                updatedAt = summary.updatedAt
            )
    }
}

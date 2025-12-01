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
                    cpuCores = summary.cpuCores,
                    memoryGb = summary.memoryGb,
                    diskGb = summary.diskGb
                ),
                justification = summary.justification,
                status = summary.status.name,
                createdAt = summary.createdAt,
                updatedAt = summary.updatedAt
            )
    }
}

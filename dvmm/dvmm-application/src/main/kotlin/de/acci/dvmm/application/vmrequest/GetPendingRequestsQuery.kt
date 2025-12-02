package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.eventsourcing.projection.PageRequest

/**
 * Query to retrieve pending VM requests for admin review.
 *
 * Used by admins to see all pending requests in their tenant.
 * Supports optional project filtering and pagination.
 *
 * Story 2.9: Admin Approval Queue (AC 1, 2, 3, 5, 6)
 *
 * @property tenantId Tenant context for multi-tenancy isolation (AC 6)
 * @property projectId Optional project filter (AC 5)
 * @property pageRequest Pagination parameters (default 25, max 100)
 */
public data class GetPendingRequestsQuery(
    val tenantId: TenantId,
    val projectId: ProjectId? = null,
    val pageRequest: PageRequest = PageRequest(size = DEFAULT_PAGE_SIZE)
) {
    init {
        // Cap page size at 100 to prevent abuse
        require(pageRequest.size <= MAX_PAGE_SIZE) {
            "Page size must not exceed $MAX_PAGE_SIZE"
        }
    }

    public companion object {
        public const val MAX_PAGE_SIZE: Int = 100
        public const val DEFAULT_PAGE_SIZE: Int = 25
    }
}

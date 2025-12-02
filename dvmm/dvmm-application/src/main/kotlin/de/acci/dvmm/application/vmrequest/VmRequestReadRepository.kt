package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse

/**
 * Simple data class representing a project for filter dropdowns.
 *
 * Story 2.9: Admin Approval Queue (AC 5)
 */
public data class ProjectSummary(
    val id: ProjectId,
    val name: String
)

/**
 * Read-only repository interface for VM request queries.
 *
 * This is a port (in hexagonal architecture terms) that allows the
 * application layer to query VM requests without depending on specific
 * infrastructure implementations.
 *
 * RLS NOTE: Implementations should ensure tenant isolation is applied.
 * With PostgreSQL RLS, this happens automatically via session variables.
 */
public interface VmRequestReadRepository {
    /**
     * Finds all VM requests submitted by a specific user.
     *
     * @param requesterId The ID of the user who submitted the requests
     * @param pageRequest Pagination parameters
     * @return A paginated response of VM request summaries
     */
    public suspend fun findByRequesterId(
        requesterId: UserId,
        pageRequest: PageRequest = PageRequest()
    ): PagedResponse<VmRequestSummary>

    /**
     * Finds all pending VM requests for a tenant (admin view).
     *
     * Story 2.9: Admin Approval Queue (AC 1, 2, 3, 5, 6)
     *
     * Returns PENDING requests only, sorted by creation date ascending
     * (oldest first) per AC 3.
     *
     * @param tenantId The tenant context for RLS filtering
     * @param projectId Optional project filter (AC 5)
     * @param pageRequest Pagination parameters (max 100)
     * @return A paginated response of pending VM request summaries
     */
    public suspend fun findPendingByTenantId(
        tenantId: TenantId,
        projectId: ProjectId? = null,
        pageRequest: PageRequest = PageRequest(size = 25)
    ): PagedResponse<VmRequestSummary>

    /**
     * Finds all distinct projects that have VM requests.
     *
     * Story 2.9: Admin Approval Queue (AC 5)
     *
     * Used to populate the project filter dropdown.
     *
     * @param tenantId The tenant context for RLS filtering
     * @return List of projects sorted alphabetically by name
     */
    public suspend fun findDistinctProjects(
        tenantId: TenantId
    ): List<ProjectSummary>
}

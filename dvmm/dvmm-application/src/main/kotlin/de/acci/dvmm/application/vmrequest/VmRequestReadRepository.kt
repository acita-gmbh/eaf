package de.acci.dvmm.application.vmrequest

import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse

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
}

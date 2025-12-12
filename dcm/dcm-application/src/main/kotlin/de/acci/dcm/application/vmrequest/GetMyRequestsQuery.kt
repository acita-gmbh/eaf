package de.acci.dcm.application.vmrequest

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest

/**
 * Query to retrieve VM requests submitted by the current user.
 *
 * Returns a paginated list of the user's own requests, ordered by
 * creation date (newest first).
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property userId The current user's ID (whose requests to retrieve)
 * @property pageRequest Pagination parameters (page number and size)
 */
public data class GetMyRequestsQuery(
    val tenantId: TenantId,
    val userId: UserId,
    val pageRequest: PageRequest = PageRequest()
)

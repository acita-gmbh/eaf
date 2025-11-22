package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.domain.WidgetId
import java.time.Instant

/**
 * Widget read model projection.
 *
 * Denormalized view of Widget state optimized for query performance.
 * Populated by WidgetProjectionEventHandler from domain events.
 *
 * **Performance Target:** Single widget query <50ms (FR011)
 *
 * **Multi-Tenancy (Story 4.6 AC5):**
 * tenantId enables Layer 3 RLS enforcement at database level.
 * Column protected by PostgreSQL RLS policy (V101 migration).
 *
 * @property id Unique identifier of the widget
 * @property name Display name of the widget
 * @property published Whether the widget is publicly available
 * @property tenantId Tenant identifier for multi-tenant isolation (Layer 3)
 * @property createdAt UTC timestamp when the widget was created
 * @property updatedAt UTC timestamp when the widget was last modified
 */
data class WidgetProjection(
    val id: WidgetId,
    val name: String,
    val published: Boolean,
    val tenantId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Paginated response for widget list queries.
 *
 * Uses cursor-based pagination to provide stable page boundaries
 * even when data changes between requests.
 *
 * @property widgets List of widget projections for the current page
 * @property nextCursor Opaque cursor for fetching the next page (null if no more results)
 * @property hasMore Whether additional results exist beyond this page
 */
data class PaginatedWidgetResponse(
    val widgets: List<WidgetProjection>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

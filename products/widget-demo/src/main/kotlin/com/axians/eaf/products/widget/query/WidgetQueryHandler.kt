package com.axians.eaf.products.widget.query

import com.axians.eaf.framework.web.pagination.CursorPaginationSupport
import com.axians.eaf.products.widget.domain.WidgetId
import org.axonframework.queryhandling.QueryHandler
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Query handler for retrieving Widget projections.
 *
 * Provides read-only access to the widget_projection table using jOOQ.
 * Implements cursor-based pagination for stable, consistent results.
 *
 * **Performance Targets (FR011):**
 * - Single widget query: <50ms
 * - Paginated list query: <200ms
 *
 * **Query Optimization:**
 * - Uses widget_projection denormalized table (no joins)
 * - Cursor pagination avoids expensive offset calculations
 * - Indexed on created_at for efficient sorting
 */
@Component
class WidgetQueryHandler(
    private val dsl: DSLContext,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val WIDGET_PROJECTION_TABLE = DSL.table("widget_projection")
        private val ID_FIELD = DSL.field("id", UUID::class.java)
        private val NAME_FIELD = DSL.field("name", String::class.java)
        private val PUBLISHED_FIELD = DSL.field("published", Boolean::class.java)
        private val TENANT_ID_FIELD = DSL.field("tenant_id", String::class.java) // Story 4.6 AC5
        private val CREATED_AT_FIELD = DSL.field("created_at", OffsetDateTime::class.java)
        private val UPDATED_AT_FIELD = DSL.field("updated_at", OffsetDateTime::class.java)
    }

    /**
     * Handles FindWidgetQuery to retrieve a single widget by ID.
     *
     * Returns null if widget does not exist in projection.
     *
     * @param query Query containing the widget ID to find
     * @return Widget projection if found, null otherwise
     */
    @QueryHandler
    fun handle(query: FindWidgetQuery): WidgetProjection? {
        logger.debug("Handling FindWidgetQuery for widgetId={}", query.widgetId.value)

        return dsl
            .select(
                ID_FIELD,
                NAME_FIELD,
                PUBLISHED_FIELD,
                TENANT_ID_FIELD,
                CREATED_AT_FIELD,
                UPDATED_AT_FIELD,
            ).from(WIDGET_PROJECTION_TABLE)
            .where(ID_FIELD.eq(UUID.fromString(query.widgetId.value)))
            .fetchOne { record ->
                WidgetProjection(
                    id = WidgetId(record[ID_FIELD].toString()),
                    name = record[NAME_FIELD],
                    published = record[PUBLISHED_FIELD],
                    tenantId = record[TENANT_ID_FIELD],
                    createdAt = record[CREATED_AT_FIELD].toInstant(),
                    updatedAt = record[UPDATED_AT_FIELD].toInstant(),
                )
            }
    }

    /**
     * Handles ListWidgetsQuery with cursor-based pagination.
     *
     * Fetches limit+1 rows to determine hasMore flag efficiently.
     * Results ordered by created_at DESC (newest first).
     *
     * @param query Query containing limit and optional cursor
     * @return Paginated response with widgets, nextCursor, and hasMore flag
     */
    @QueryHandler
    fun handle(query: ListWidgetsQuery): PaginatedWidgetResponse {
        logger.debug("Handling ListWidgetsQuery with limit={}, cursor={}", query.limit, query.cursor)

        // Validate limit bounds (max 100)
        val safeLimit = query.limit.coerceIn(1, 100)

        // Decode cursor to get timestamp filter
        val cursorTimestamp = query.cursor?.let { CursorPaginationSupport.decodeCursor(it) }

        // Build WHERE condition for cursor-based pagination
        val whereCondition: Condition =
            cursorTimestamp?.let { CREATED_AT_FIELD.lt(it.atOffset(ZoneOffset.UTC)) }
                ?: DSL.noCondition()

        // Fetch limit+1 to detect hasMore
        val records =
            dsl
                .select(
                    ID_FIELD,
                    NAME_FIELD,
                    PUBLISHED_FIELD,
                    TENANT_ID_FIELD,
                    CREATED_AT_FIELD,
                    UPDATED_AT_FIELD,
                ).from(WIDGET_PROJECTION_TABLE)
                .where(whereCondition)
                .orderBy(CREATED_AT_FIELD.desc())
                .limit(safeLimit + 1)
                .fetch { record ->
                    WidgetProjection(
                        id = WidgetId(record[ID_FIELD].toString()),
                        name = record[NAME_FIELD],
                        published = record[PUBLISHED_FIELD],
                        tenantId = record[TENANT_ID_FIELD],
                        createdAt = record[CREATED_AT_FIELD].toInstant(),
                        updatedAt = record[UPDATED_AT_FIELD].toInstant(),
                    )
                }

        // Detect if more results exist
        val hasMore = records.size > safeLimit
        val widgets = if (hasMore) records.take(safeLimit) else records

        // Generate nextCursor from last item's created_at
        // Safe: safeLimit >= 1 (via coerceIn), so widgets.size >= 1 when hasMore=true
        // When hasMore=false, widgets.last() is never called (nextCursor = null)
        val nextCursor =
            if (hasMore) {
                CursorPaginationSupport.encodeCursor(widgets.last().createdAt)
            } else {
                null
            }

        logger.debug("Returning {} widgets, hasMore={}", widgets.size, hasMore)

        return PaginatedWidgetResponse(
            widgets = widgets,
            nextCursor = nextCursor,
            hasMore = hasMore,
        )
    }
}

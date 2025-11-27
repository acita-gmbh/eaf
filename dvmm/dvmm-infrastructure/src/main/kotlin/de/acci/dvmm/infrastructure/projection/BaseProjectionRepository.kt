package de.acci.dvmm.infrastructure.projection

import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table

/**
 * Base class for projection repositories providing common query patterns.
 *
 * RLS NOTE: Tenant filtering is handled automatically by PostgreSQL Row-Level Security.
 * DO NOT add explicit WHERE tenant_id = ? clauses - RLS policies enforce this at the database level.
 *
 * @param T The domain entity type returned by this repository
 */
public abstract class BaseProjectionRepository<T : Any>(
    protected val dsl: DSLContext
) {

    /**
     * Maps a jOOQ record to the domain entity type.
     */
    protected abstract fun mapRecord(record: Record): T

    /**
     * Returns the primary table for this projection.
     */
    protected abstract fun table(): Table<*>

    /**
     * Returns the default sort fields for deterministic pagination.
     * Override this method to customize the default ordering.
     *
     * @return List of sort fields for ORDER BY clause
     */
    protected open fun defaultOrderBy(): List<SortField<*>> = emptyList()

    /**
     * Finds all entities with pagination.
     *
     * Note: For deterministic pagination results, subclasses should override [defaultOrderBy]
     * to provide a consistent sort order.
     *
     * @param pageRequest The pagination parameters
     * @return PagedResponse containing the entities
     */
    public open suspend fun findAll(pageRequest: PageRequest = PageRequest()): PagedResponse<T> =
        withContext(Dispatchers.IO) {
            val totalElements = dsl.fetchCount(table()).toLong()

            val query = dsl.selectFrom(table())
            val orderBy = defaultOrderBy()
            val orderedQuery = if (orderBy.isNotEmpty()) {
                query.orderBy(orderBy)
            } else {
                query
            }

            val items = orderedQuery
                .limit(pageRequest.size)
                .offset(pageRequest.offset)
                .fetch()
                .map { mapRecord(it) }

            PagedResponse(
                items = items,
                page = pageRequest.page,
                size = pageRequest.size,
                totalElements = totalElements
            )
        }

    /**
     * Counts total entities in the projection.
     *
     * @return The total count
     */
    public open suspend fun count(): Long = withContext(Dispatchers.IO) {
        dsl.fetchCount(table()).toLong()
    }

    /**
     * Checks if any entities exist.
     *
     * @return true if at least one entity exists
     */
    public open suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        dsl.fetchExists(table())
    }
}

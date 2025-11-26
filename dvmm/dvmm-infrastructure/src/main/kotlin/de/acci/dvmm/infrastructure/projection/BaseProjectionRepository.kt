package de.acci.dvmm.infrastructure.projection

import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Select
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
     * Executes a paginated query and returns a PagedResponse.
     *
     * @param pageRequest The pagination parameters
     * @param query The base query to paginate (without LIMIT/OFFSET)
     * @return PagedResponse containing the items and pagination metadata
     */
    protected suspend fun <R : Record> paginate(
        pageRequest: PageRequest,
        query: Select<R>
    ): PagedResponse<T> = withContext(Dispatchers.IO) {
        // Count total elements (executed as a separate query)
        val totalElements = dsl.fetchCount(query).toLong()

        // Fetch the page using a wrapper query with LIMIT/OFFSET
        val items = dsl.selectFrom(query.asTable("subquery"))
            .limit(pageRequest.size)
            .offset(pageRequest.offset.toInt())
            .fetch()
            .map { record -> mapRecord(record) }

        PagedResponse(
            items = items,
            page = pageRequest.page,
            size = pageRequest.size,
            totalElements = totalElements
        )
    }

    /**
     * Finds all entities with pagination.
     *
     * @param pageRequest The pagination parameters
     * @return PagedResponse containing the entities
     */
    public open suspend fun findAll(pageRequest: PageRequest = PageRequest()): PagedResponse<T> =
        withContext(Dispatchers.IO) {
            val totalElements = dsl.fetchCount(table()).toLong()

            val items = dsl.selectFrom(table())
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

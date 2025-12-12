package de.acci.dcm.infrastructure.projection

import de.acci.dcm.infrastructure.jooq.`public`.tables.RequestTimelineEvents.Companion.REQUEST_TIMELINE_EVENTS
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.RequestTimelineEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.InsertSetMoreStep
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Repository for querying VM request timeline event projections.
 *
 * RLS NOTE: Tenant filtering is handled automatically by PostgreSQL Row-Level Security.
 * All queries through this repository are automatically filtered to the current tenant
 * based on the `app.tenant_id` session variable set by the connection customizer.
 *
 * ## Column Symmetry Pattern
 *
 * This repository uses a sealed column mapping pattern to ensure read/write symmetry.
 * Both [mapRecord] (read) and [insert] (write) use the same [ProjectionColumns] sealed
 * interface to guarantee that all columns are handled consistently.
 *
 * **Adding new columns:**
 * 1. Add new sealed class to [ProjectionColumns]
 * 2. Add case to [mapColumn] in [mapRecord]
 * 3. Add case to [setColumn] in insert
 * 4. Compile will fail if any step is missed
 *
 * @see ProjectionColumns
 */
public class TimelineEventRepository(
    dsl: DSLContext
) : BaseProjectionRepository<RequestTimelineEvents>(dsl) {

    /**
     * Sealed interface defining all columns in REQUEST_TIMELINE_EVENTS.
     *
     * This pattern ensures compile-time safety: if a new column is added to this
     * sealed hierarchy, both [mapRecord] and [insert] must handle it or the
     * exhaustive `when` expressions will fail to compile.
     */
    public sealed interface ProjectionColumns {
        public data object Id : ProjectionColumns
        public data object RequestId : ProjectionColumns
        public data object TenantId : ProjectionColumns
        public data object EventType : ProjectionColumns
        public data object ActorId : ProjectionColumns
        public data object ActorName : ProjectionColumns
        public data object Details : ProjectionColumns
        public data object OccurredAt : ProjectionColumns

        public companion object {
            /**
             * All columns that must be handled by read and write operations.
             */
            public val all: List<ProjectionColumns> = listOf(
                Id, RequestId, TenantId, EventType, ActorId, ActorName, Details, OccurredAt
            )
        }
    }

    /**
     * Maps a column to its value from a jOOQ Record.
     * Exhaustive when expression ensures all columns are handled.
     */
    private fun mapColumn(record: Record, column: ProjectionColumns): Any? = when (column) {
        ProjectionColumns.Id -> record.get(REQUEST_TIMELINE_EVENTS.ID)!!
        ProjectionColumns.RequestId -> record.get(REQUEST_TIMELINE_EVENTS.REQUEST_ID)!!
        ProjectionColumns.TenantId -> record.get(REQUEST_TIMELINE_EVENTS.TENANT_ID)!!
        ProjectionColumns.EventType -> record.get(REQUEST_TIMELINE_EVENTS.EVENT_TYPE)!!
        ProjectionColumns.ActorId -> record.get(REQUEST_TIMELINE_EVENTS.ACTOR_ID)
        ProjectionColumns.ActorName -> record.get(REQUEST_TIMELINE_EVENTS.ACTOR_NAME)
        ProjectionColumns.Details -> record.get(REQUEST_TIMELINE_EVENTS.DETAILS)
        ProjectionColumns.OccurredAt -> record.get(REQUEST_TIMELINE_EVENTS.OCCURRED_AT)!!
    }

    /**
     * Sets a column value in an INSERT statement.
     * Exhaustive when expression ensures all columns are handled symmetrically with [mapColumn].
     */
    private fun setColumn(
        step: InsertSetMoreStep<*>,
        column: ProjectionColumns,
        event: RequestTimelineEvents
    ): InsertSetMoreStep<*> = when (column) {
        ProjectionColumns.Id -> step.set(REQUEST_TIMELINE_EVENTS.ID, event.id)
        ProjectionColumns.RequestId -> step.set(REQUEST_TIMELINE_EVENTS.REQUEST_ID, event.requestId)
        ProjectionColumns.TenantId -> step.set(REQUEST_TIMELINE_EVENTS.TENANT_ID, event.tenantId)
        ProjectionColumns.EventType -> step.set(REQUEST_TIMELINE_EVENTS.EVENT_TYPE, event.eventType)
        ProjectionColumns.ActorId -> step.set(REQUEST_TIMELINE_EVENTS.ACTOR_ID, event.actorId)
        ProjectionColumns.ActorName -> step.set(REQUEST_TIMELINE_EVENTS.ACTOR_NAME, event.actorName)
        ProjectionColumns.Details -> step.set(REQUEST_TIMELINE_EVENTS.DETAILS, event.details)
        ProjectionColumns.OccurredAt -> step.set(REQUEST_TIMELINE_EVENTS.OCCURRED_AT, event.occurredAt)
    }

    override fun mapRecord(record: Record): RequestTimelineEvents {
        return RequestTimelineEvents(
            id = mapColumn(record, ProjectionColumns.Id) as UUID,
            requestId = mapColumn(record, ProjectionColumns.RequestId) as UUID,
            tenantId = mapColumn(record, ProjectionColumns.TenantId) as UUID,
            eventType = mapColumn(record, ProjectionColumns.EventType) as String,
            actorId = mapColumn(record, ProjectionColumns.ActorId) as UUID?,
            actorName = mapColumn(record, ProjectionColumns.ActorName) as String?,
            details = mapColumn(record, ProjectionColumns.Details) as String?,
            occurredAt = mapColumn(record, ProjectionColumns.OccurredAt) as OffsetDateTime
        )
    }

    override fun table(): Table<*> = REQUEST_TIMELINE_EVENTS

    /**
     * Returns the default ordering for timeline queries.
     * Orders by occurrence time ascending (oldest first for chronological display).
     */
    override fun defaultOrderBy(): List<SortField<*>> = listOf(
        REQUEST_TIMELINE_EVENTS.OCCURRED_AT.asc()
    )

    /**
     * Finds all timeline events for a specific VM request.
     *
     * @param requestId The ID of the VM request
     * @return List of timeline events sorted chronologically (oldest first)
     */
    public suspend fun findByRequestId(requestId: UUID): List<RequestTimelineEvents> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(REQUEST_TIMELINE_EVENTS)
                .where(REQUEST_TIMELINE_EVENTS.REQUEST_ID.eq(requestId))
                .orderBy(defaultOrderBy())
                .fetch()
                .map { mapRecord(it) }
        }

    /**
     * Inserts a new timeline event.
     *
     * Uses INSERT ... ON CONFLICT DO NOTHING for idempotency during event replay.
     * Uses the sealed [ProjectionColumns] pattern to ensure all columns are set symmetrically
     * with [mapRecord].
     *
     * @param event The timeline event to insert
     */
    public suspend fun insert(event: RequestTimelineEvents): Unit = withContext(Dispatchers.IO) {
        // Start with ID column to get InsertSetMoreStep type
        val initialStep: InsertSetMoreStep<*> = dsl.insertInto(REQUEST_TIMELINE_EVENTS)
            .set(REQUEST_TIMELINE_EVENTS.ID, event.id)

        // Set remaining columns, explicitly filtering out Id to avoid order dependency on all list
        var step = initialStep
        ProjectionColumns.all
            .filterNot { it is ProjectionColumns.Id }
            .forEach { column -> step = setColumn(step, column, event) }

        step.onConflictDoNothing()
            .execute()
    }

    /**
     * Inserts a new timeline event with all fields specified explicitly.
     *
     * Delegates to [insert] with a [RequestTimelineEvents] object to reuse the sealed
     * column pattern.
     *
     * @param id Unique identifier for the timeline event
     * @param requestId The VM request this event belongs to
     * @param tenantId The tenant ID
     * @param eventType Type of event (CREATED, APPROVED, REJECTED, CANCELLED, etc.)
     * @param actorId ID of the user who performed the action (nullable)
     * @param actorName Display name of the actor (nullable)
     * @param details Additional event details as JSON string (nullable)
     * @param occurredAt When the event occurred
     */
    public suspend fun insert(
        id: UUID,
        requestId: UUID,
        tenantId: UUID,
        eventType: String,
        actorId: UUID?,
        actorName: String?,
        details: String?,
        occurredAt: OffsetDateTime
    ): Unit = insert(
        RequestTimelineEvents(
            id = id,
            requestId = requestId,
            tenantId = tenantId,
            eventType = eventType,
            actorId = actorId,
            actorName = actorName,
            details = details,
            occurredAt = occurredAt
        )
    )

    /**
     * Checks if a timeline event already exists.
     *
     * @param eventId The event ID to check
     * @return true if the event exists
     */
    public suspend fun exists(eventId: UUID): Boolean = withContext(Dispatchers.IO) {
        dsl.fetchExists(
            dsl.selectFrom(REQUEST_TIMELINE_EVENTS)
                .where(REQUEST_TIMELINE_EVENTS.ID.eq(eventId))
        )
    }
}

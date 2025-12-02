package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.infrastructure.jooq.`public`.tables.RequestTimelineEvents.Companion.REQUEST_TIMELINE_EVENTS
import de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.RequestTimelineEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
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
 */
public class TimelineEventRepository(
    dsl: DSLContext
) : BaseProjectionRepository<RequestTimelineEvents>(dsl) {

    override fun mapRecord(record: Record): RequestTimelineEvents {
        return RequestTimelineEvents(
            id = record.get(REQUEST_TIMELINE_EVENTS.ID)!!,
            requestId = record.get(REQUEST_TIMELINE_EVENTS.REQUEST_ID)!!,
            tenantId = record.get(REQUEST_TIMELINE_EVENTS.TENANT_ID)!!,
            eventType = record.get(REQUEST_TIMELINE_EVENTS.EVENT_TYPE)!!,
            actorId = record.get(REQUEST_TIMELINE_EVENTS.ACTOR_ID),
            actorName = record.get(REQUEST_TIMELINE_EVENTS.ACTOR_NAME),
            details = record.get(REQUEST_TIMELINE_EVENTS.DETAILS),
            occurredAt = record.get(REQUEST_TIMELINE_EVENTS.OCCURRED_AT)!!
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
     *
     * @param event The timeline event to insert
     */
    public suspend fun insert(event: RequestTimelineEvents): Unit = withContext(Dispatchers.IO) {
        dsl.insertInto(REQUEST_TIMELINE_EVENTS)
            .set(REQUEST_TIMELINE_EVENTS.ID, event.id)
            .set(REQUEST_TIMELINE_EVENTS.REQUEST_ID, event.requestId)
            .set(REQUEST_TIMELINE_EVENTS.TENANT_ID, event.tenantId)
            .set(REQUEST_TIMELINE_EVENTS.EVENT_TYPE, event.eventType)
            .set(REQUEST_TIMELINE_EVENTS.ACTOR_ID, event.actorId)
            .set(REQUEST_TIMELINE_EVENTS.ACTOR_NAME, event.actorName)
            .set(REQUEST_TIMELINE_EVENTS.DETAILS, event.details)
            .set(REQUEST_TIMELINE_EVENTS.OCCURRED_AT, event.occurredAt)
            .onConflictDoNothing()
            .execute()
    }

    /**
     * Inserts a new timeline event with all fields specified explicitly.
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
    ): Unit = withContext(Dispatchers.IO) {
        dsl.insertInto(REQUEST_TIMELINE_EVENTS)
            .set(REQUEST_TIMELINE_EVENTS.ID, id)
            .set(REQUEST_TIMELINE_EVENTS.REQUEST_ID, requestId)
            .set(REQUEST_TIMELINE_EVENTS.TENANT_ID, tenantId)
            .set(REQUEST_TIMELINE_EVENTS.EVENT_TYPE, eventType)
            .set(REQUEST_TIMELINE_EVENTS.ACTOR_ID, actorId)
            .set(REQUEST_TIMELINE_EVENTS.ACTOR_NAME, actorName)
            .set(REQUEST_TIMELINE_EVENTS.DETAILS, details)
            .set(REQUEST_TIMELINE_EVENTS.OCCURRED_AT, occurredAt)
            .onConflictDoNothing()
            .execute()
    }

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

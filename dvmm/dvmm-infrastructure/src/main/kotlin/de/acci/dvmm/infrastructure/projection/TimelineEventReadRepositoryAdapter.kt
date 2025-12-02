package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.application.vmrequest.TimelineEventItem
import de.acci.dvmm.application.vmrequest.TimelineEventReadRepository
import de.acci.dvmm.application.vmrequest.TimelineEventType
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.infrastructure.jooq.`public`.tables.RequestTimelineEvents.Companion.REQUEST_TIMELINE_EVENTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

/**
 * Infrastructure adapter implementing TimelineEventReadRepository.
 *
 * Provides read access to timeline event projections.
 * RLS ensures tenant isolation automatically.
 */
public class TimelineEventReadRepositoryAdapter(
    private val dsl: DSLContext
) : TimelineEventReadRepository {

    override suspend fun findByRequestId(requestId: VmRequestId): List<TimelineEventItem> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(REQUEST_TIMELINE_EVENTS)
                .where(REQUEST_TIMELINE_EVENTS.REQUEST_ID.eq(requestId.value))
                .orderBy(REQUEST_TIMELINE_EVENTS.OCCURRED_AT.asc())
                .fetch()
                .map { record ->
                    TimelineEventItem(
                        eventType = TimelineEventType.valueOf(
                            record.get(REQUEST_TIMELINE_EVENTS.EVENT_TYPE)!!
                        ),
                        actorName = record.get(REQUEST_TIMELINE_EVENTS.ACTOR_NAME),
                        details = record.get(REQUEST_TIMELINE_EVENTS.DETAILS),
                        occurredAt = record.get(REQUEST_TIMELINE_EVENTS.OCCURRED_AT)!!.toInstant()
                    )
                }
        }
}

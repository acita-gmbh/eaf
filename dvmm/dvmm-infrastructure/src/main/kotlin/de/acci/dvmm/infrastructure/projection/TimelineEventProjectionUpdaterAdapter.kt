package de.acci.dvmm.infrastructure.projection

import de.acci.dvmm.application.vmrequest.NewTimelineEvent
import de.acci.dvmm.application.vmrequest.TimelineEventProjectionUpdater
import de.acci.dvmm.infrastructure.jooq.`public`.tables.pojos.RequestTimelineEvents
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.eventsourcing.projection.ProjectionError
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Infrastructure adapter that implements TimelineEventProjectionUpdater
 * using the jOOQ-based TimelineEventRepository.
 *
 * This adapter translates application-layer timeline event projection operations
 * into infrastructure-layer database operations.
 *
 * ## Error Handling
 *
 * Projection updates return [Result] types to make errors explicit.
 * Errors are logged at the infrastructure layer, but callers decide
 * whether to propagate failures or allow the command to succeed.
 * Failed projections can be reconstructed from the event store.
 *
 * ## Idempotency
 *
 * The underlying repository uses INSERT ON CONFLICT DO NOTHING,
 * making timeline event insertion idempotent during event replay.
 */
public class TimelineEventProjectionUpdaterAdapter(
    private val repository: TimelineEventRepository
) : TimelineEventProjectionUpdater {

    private val logger = KotlinLogging.logger {}

    override suspend fun addTimelineEvent(data: NewTimelineEvent): Result<Unit, ProjectionError> {
        return try {
            val event = RequestTimelineEvents(
                id = data.id,
                requestId = data.requestId.value,
                tenantId = data.tenantId.value,
                eventType = data.eventType.name,
                actorId = data.actorId?.value,
                actorName = data.actorName,
                details = data.details,
                occurredAt = OffsetDateTime.ofInstant(data.occurredAt, ZoneOffset.UTC)
            )
            repository.insert(event)
            logger.debug {
                "Added timeline event for request ${data.requestId.value}: ${data.eventType}"
            }
            Unit.success()
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to add timeline event for request ${data.requestId.value}: ${data.eventType}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = data.requestId.value.toString(),
                message = "Failed to add timeline event: ${e.message}",
                cause = e
            ).failure()
        }
    }
}

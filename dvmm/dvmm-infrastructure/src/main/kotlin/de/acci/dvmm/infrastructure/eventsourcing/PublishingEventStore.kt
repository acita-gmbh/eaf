package de.acci.dvmm.infrastructure.eventsourcing

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.onSuccess
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import de.acci.eaf.eventsourcing.StoredEvent
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * EventStore decorator that publishes events to Spring ApplicationEventPublisher after persistence.
 */
public class PublishingEventStore(
    private val delegate: EventStore,
    private val publisher: ApplicationEventPublisher
) : EventStore {
    private val logger = KotlinLogging.logger {}

    override suspend fun append(
        aggregateId: UUID,
        events: List<DomainEvent>,
        expectedVersion: Long
    ): Result<Long, EventStoreError> {
        val result = delegate.append(aggregateId, events, expectedVersion)
        
        result.onSuccess {
            // Publish events to Spring context
            events.forEach { event ->
                try {
                    logger.debug { "Publishing domain event: ${event::class.simpleName} (Agg: $aggregateId)" }
                    publisher.publishEvent(event)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to publish event ${event::class.simpleName} for aggregate $aggregateId" }
                }
            }
        }
        
        return result
    }

    override suspend fun load(aggregateId: UUID): List<StoredEvent> = delegate.load(aggregateId)

    override suspend fun loadFrom(aggregateId: UUID, fromVersion: Long): List<StoredEvent> = 
        delegate.loadFrom(aggregateId, fromVersion)
}

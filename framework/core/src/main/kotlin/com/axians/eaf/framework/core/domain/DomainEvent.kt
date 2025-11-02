package com.axians.eaf.framework.core.domain

import java.time.Instant
import java.util.UUID

/**
 * Marker interface for domain events.
 *
 * All domain events must provide temporal metadata (occurredAt) and
 * a unique identifier (eventId) for correlation and deduplication.
 *
 * Domain events represent facts about state changes in the domain model
 * and should be named in past tense (e.g., WidgetCreated, OrderPlaced).
 *
 * Example:
 * ```kotlin
 * data class WidgetCreatedEvent(
 *     override val occurredAt: Instant = Instant.now(),
 *     override val eventId: UUID = UUID.randomUUID(),
 *     val widgetId: String,
 *     val name: String
 * ) : DomainEvent
 * ```
 *
 * @property occurredAt UTC timestamp when the event occurred
 * @property eventId Unique identifier for correlation and deduplication
 */
interface DomainEvent {
    val occurredAt: Instant
    val eventId: UUID
}

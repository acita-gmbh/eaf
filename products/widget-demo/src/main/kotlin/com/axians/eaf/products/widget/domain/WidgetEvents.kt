package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.core.domain.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * Event indicating a new Widget was created.
 *
 * @property widgetId Unique identifier of the created widget
 * @property name Display name assigned to the widget
 * @property occurredAt UTC timestamp when the widget was created
 * @property eventId Unique identifier for this event instance
 */
data class WidgetCreatedEvent(
    val widgetId: WidgetId,
    val name: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID(),
) : DomainEvent

/**
 * Event indicating a Widget's name was updated.
 *
 * @property widgetId Identifier of the updated widget
 * @property name New display name for the widget
 * @property occurredAt UTC timestamp when the update occurred
 * @property eventId Unique identifier for this event instance
 */
data class WidgetUpdatedEvent(
    val widgetId: WidgetId,
    val name: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID(),
) : DomainEvent

/**
 * Event indicating a Widget was published.
 *
 * @property widgetId Identifier of the published widget
 * @property occurredAt UTC timestamp when the widget was published
 * @property eventId Unique identifier for this event instance
 */
data class WidgetPublishedEvent(
    val widgetId: WidgetId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID(),
) : DomainEvent

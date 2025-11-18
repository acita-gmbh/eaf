package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.core.domain.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * Event indicating a new Widget was created.
 *
 * **Multi-Tenancy (Story 4.6):**
 * - tenantId included in event payload for Layer 3 (PostgreSQL RLS)
 * - tenantId also automatically added to event metadata by CorrelationDataProvider (Story 4.5)
 * - Async event handlers restore tenant context from metadata
 *
 * @property widgetId Unique identifier of the created widget
 * @property name Display name assigned to the widget
 * @property tenantId Tenant identifier for multi-tenant isolation
 * @property occurredAt UTC timestamp when the widget was created
 * @property eventId Unique identifier for this event instance
 */
data class WidgetCreatedEvent(
    val widgetId: WidgetId,
    val name: String,
    val tenantId: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID(),
) : DomainEvent

/**
 * Event indicating a Widget's name was updated.
 *
 * **Multi-Tenancy (Story 4.6):**
 * - tenantId included for Layer 3 isolation and event handler context
 *
 * @property widgetId Identifier of the updated widget
 * @property name New display name for the widget
 * @property tenantId Tenant identifier
 * @property occurredAt UTC timestamp when the update occurred
 * @property eventId Unique identifier for this event instance
 */
data class WidgetUpdatedEvent(
    val widgetId: WidgetId,
    val name: String,
    val tenantId: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID(),
) : DomainEvent

/**
 * Event indicating a Widget was published.
 *
 * **Multi-Tenancy (Story 4.6):**
 * - tenantId included for Layer 3 isolation
 *
 * @property widgetId Identifier of the published widget
 * @property tenantId Tenant identifier
 * @property occurredAt UTC timestamp when the widget was published
 * @property eventId Unique identifier for this event instance
 */
data class WidgetPublishedEvent(
    val widgetId: WidgetId,
    val tenantId: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID(),
) : DomainEvent

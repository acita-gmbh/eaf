package com.axians.eaf.products.widget.domain

import org.axonframework.modelling.command.TargetAggregateIdentifier

/**
 * Command to create a new Widget aggregate.
 *
 * @property widgetId Unique identifier for the widget to create
 * @property name Display name for the widget (must not be blank)
 * @property tenantId Tenant identifier for multi-tenant isolation (Story 4.6)
 */
data class CreateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String,
    val tenantId: String,
)

/**
 * Command to update an existing Widget's name.
 *
 * @property widgetId Identifier of the widget to update
 * @property name New display name for the widget
 * @property tenantId Tenant identifier for multi-tenant isolation (Story 4.6)
 */
data class UpdateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String,
    val tenantId: String,
)

/**
 * Command to publish a Widget, making it publicly available.
 *
 * @property widgetId Identifier of the widget to publish
 * @property tenantId Tenant identifier for multi-tenant isolation (Story 4.6)
 */
data class PublishWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val tenantId: String,
)

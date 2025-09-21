package com.axians.eaf.api.widget.queries

import org.axonframework.modelling.command.TargetAggregateIdentifier

/**
 * Query to find a specific widget by its ID.
 * Follows CQRS query patterns with tenant context propagation.
 */
data class FindWidgetByIdQuery(
    @TargetAggregateIdentifier
    val widgetId: String,
    val tenantId: String,
)

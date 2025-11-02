package com.axians.eaf.framework.core.exceptions

/**
 * Exception thrown when an aggregate cannot be found by its ID.
 *
 * This exception indicates that a requested aggregate does not exist
 * in the event store or has been deleted.
 *
 * Example:
 * ```kotlin
 * throw AggregateNotFoundException(
 *     aggregateId = "widget-123",
 *     aggregateType = "Widget"
 * )
 * ```
 *
 * @param aggregateId The ID of the aggregate that was not found
 * @param aggregateType The type/name of the aggregate (e.g., "Widget", "Order")
 */
class AggregateNotFoundException(
    aggregateId: String,
    aggregateType: String,
) : EafException("Aggregate $aggregateType with ID $aggregateId not found")

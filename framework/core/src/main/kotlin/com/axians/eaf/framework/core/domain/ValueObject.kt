package com.axians.eaf.framework.core.domain

import java.io.Serializable

/**
 * Base class for DDD value objects.
 *
 * Value objects are immutable objects that represent concepts in the domain
 * purely by their attributes (e.g., Money, Address, Quantity).
 *
 * Key characteristics:
 * - **Immutability**: All properties should be `val` (read-only)
 * - **Structural Equality**: Two value objects are equal if all their attributes are equal
 * - **No Identity**: Unlike entities, value objects have no identifier
 * - **Serializable**: Supports Axon snapshot serialization for event-sourced aggregates
 *
 * Implementation guideline:
 * Use Kotlin `data class` for automatic equals/hashCode based on all properties.
 *
 * Example:
 * ```kotlin
 * data class Money(
 *     val amount: BigDecimal,
 *     val currency: Currency
 * ) : ValueObject()
 * ```
 *
 * @see Entity
 * @see AggregateRoot
 */
abstract class ValueObject : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

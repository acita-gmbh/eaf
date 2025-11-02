package com.axians.eaf.framework.core.domain

import com.axians.eaf.framework.core.common.types.Identifier

/**
 * Base class for DDD entities.
 *
 * Entities are objects that have a unique identity that persists over time.
 * Unlike value objects, two entities are considered equal if they have the same ID,
 * regardless of their other attributes.
 *
 * Key characteristics:
 * - **Identity-Based Equality**: Two entities are equal if they have the same ID
 * - **Mutable State**: Entities can change their attributes over time (unlike value objects)
 * - **Unique Identity**: Each entity has a unique identifier
 *
 * This base class implements equals/hashCode based solely on the entity's ID,
 * ensuring correct behavior in collections (Set, Map) and for domain comparisons.
 *
 * Example:
 * ```kotlin
 * data class WidgetId(override val value: String) : Identifier
 *
 * data class Widget(
 *     override val id: WidgetId,
 *     val name: String,
 *     val status: WidgetStatus
 * ) : Entity<WidgetId>(id)
 * ```
 *
 * @param ID The type of identifier (must implement Identifier interface)
 * @property id The unique identifier for this entity
 *
 * @see AggregateRoot
 * @see ValueObject
 */
abstract class Entity<ID : Identifier>(
    open val id: ID,
) {
    /**
     * Identity-based equality.
     *
     * Two entities are equal if:
     * 1. They are the same instance (reference equality), OR
     * 2. They are the same class type AND have the same ID
     *
     * Note: Entity type is checked to prevent false positives
     * between different entity types with overlapping ID types.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
        other as Entity<*>
        return id == other.id
    }

    /**
     * Hash code based on entity ID.
     *
     * Ensures consistent behavior in hash-based collections (HashSet, HashMap).
     * Two entities with the same ID will have the same hash code.
     */
    override fun hashCode(): Int = id.hashCode()
}

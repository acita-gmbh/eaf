package com.axians.eaf.framework.core.domain

import com.axians.eaf.framework.core.common.types.Identifier

/**
 * Base class for DDD aggregate roots.
 *
 * An aggregate is a cluster of domain objects that can be treated as a single unit.
 * The aggregate root is the entry point for all operations on the aggregate and
 * maintains consistency boundaries.
 *
 * This base class extends Entity and adds domain event management capabilities:
 * - Events can be registered during command handling
 * - Events are retrieved for publishing after successful transaction
 * - Events are cleared after publishing
 *
 * Key characteristics:
 * - **Event Sourcing**: Aggregates track state changes as domain events
 * - **Transactional Boundary**: All changes to aggregate members go through the root
 * - **Consistency Boundary**: Invariants are enforced within aggregate boundaries
 *
 * Example:
 * ```kotlin
 * class WidgetAggregate(
 *     override val id: WidgetId,
 *     var name: String,
 *     var status: WidgetStatus
 * ) : AggregateRoot<WidgetId>(id) {
 *
 *     fun create(name: String) {
 *         this.name = name
 *         this.status = WidgetStatus.DRAFT
 *         registerEvent(WidgetCreatedEvent(widgetId = id, name = name))
 *     }
 *
 *     fun activate() {
 *         require(status == WidgetStatus.DRAFT) { "Only draft widgets can be activated" }
 *         status = WidgetStatus.ACTIVE
 *         registerEvent(WidgetActivatedEvent(widgetId = id))
 *     }
 * }
 * ```
 *
 * @param ID The type of identifier (must implement Identifier interface)
 * @property id The unique identifier for this aggregate
 *
 * @see Entity
 * @see DomainEvent
 */
abstract class AggregateRoot<ID : Identifier>(
    override val id: ID,
) : Entity<ID>(id) {
    /**
     * Internal list of domain events registered during command handling.
     * Events are accumulated and published after successful transaction commit.
     */
    private val domainEvents = mutableListOf<DomainEvent>()

    /**
     * Register a domain event to be published after transaction commit.
     *
     * Events should be registered when state changes occur that other parts
     * of the system need to be notified about.
     *
     * @param event The domain event to register
     */
    protected fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    /**
     * Clear all registered events.
     *
     * Typically called after events have been successfully published to
     * prevent duplicate event processing.
     */
    fun clearEvents() {
        domainEvents.clear()
    }

    /**
     * Retrieve all registered events.
     *
     * Returns an immutable copy to prevent external modification of the
     * internal event list.
     *
     * @return Immutable list of domain events
     */
    fun getEvents(): List<DomainEvent> = domainEvents.toList()
}

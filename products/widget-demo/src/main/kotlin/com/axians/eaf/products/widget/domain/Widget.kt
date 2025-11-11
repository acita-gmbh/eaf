package com.axians.eaf.products.widget.domain

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import java.io.Serializable

/**
 * Widget aggregate demonstrating CQRS/Event Sourcing pattern.
 *
 * Implements command handling with business logic validation and
 * event sourcing handlers for state reconstruction from events.
 *
 * **Business Rules:**
 * - Widget name must not be blank
 * - Published widgets cannot be updated
 * - Widgets can only be published once
 *
 * **Event Sourcing:**
 * State is reconstructed by replaying all events in chronological order.
 * No business logic in event sourcing handlers - only state updates.
 *
 * **Snapshots:**
 * Serializable for Axon snapshot support (every 100 events per configuration).
 *
 * **Aggregate Caching:**
 * Uses WeakReferenceCache to avoid repeated event loading from the event store.
 * After first load, aggregate stays in memory for subsequent commands (until GC'd).
 * This provides ~100-150ms performance improvement per command for hot aggregates.
 */
@Aggregate(cache = "aggregateCache")
open class Widget : Serializable {
    @AggregateIdentifier
    private lateinit var widgetId: WidgetId

    private lateinit var name: String
    private var published: Boolean = false

    /**
     * Required no-arg constructor for Axon Framework.
     */
    constructor()

    companion object {
        private const val serialVersionUID = 1L
    }

    /**
     * Command handler for widget creation.
     *
     * **Validation:**
     * - Name must not be blank
     *
     * @param command CreateWidgetCommand with widget ID and name
     * @throws IllegalArgumentException if name is blank
     */
    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        AggregateLifecycle.apply(
            WidgetCreatedEvent(command.widgetId, command.name),
        )
    }

    /**
     * Command handler for widget updates.
     *
     * **Validation:**
     * - Widget must not be published
     * - Name must not be blank
     *
     * @param command UpdateWidgetCommand with new name
     * @throws IllegalArgumentException if widget is already published or name is blank
     */
    @CommandHandler
    open fun handle(command: UpdateWidgetCommand) {
        require(!published) { "Cannot update published widget" }
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        AggregateLifecycle.apply(
            WidgetUpdatedEvent(widgetId, command.name),
        )
    }

    /**
     * Command handler for widget publishing.
     *
     * **Validation:**
     * - Widget must not already be published
     *
     * @param command PublishWidgetCommand
     * @throws IllegalArgumentException if widget is already published
     */
    @CommandHandler
    open fun handle(command: PublishWidgetCommand) {
        require(!published) { "Widget already published" }

        AggregateLifecycle.apply(
            WidgetPublishedEvent(widgetId),
        )
    }

    /**
     * Event sourcing handler for widget creation.
     *
     * Initializes aggregate state from WidgetCreatedEvent.
     * No business logic - pure state reconstruction.
     *
     * @param event WidgetCreatedEvent with initial widget data
     */
    @EventSourcingHandler
    open fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.name = event.name
        this.published = false
    }

    /**
     * Event sourcing handler for widget updates.
     *
     * Updates widget name from WidgetUpdatedEvent.
     * No business logic - pure state reconstruction.
     *
     * @param event WidgetUpdatedEvent with new name
     */
    @EventSourcingHandler
    open fun on(event: WidgetUpdatedEvent) {
        this.name = event.name
    }

    /**
     * Event sourcing handler for widget publishing.
     *
     * Marks widget as published from WidgetPublishedEvent.
     * No business logic - pure state reconstruction.
     *
     * @param event WidgetPublishedEvent
     */
    @EventSourcingHandler
    open fun on(event: WidgetPublishedEvent) {
        this.published = true
    }
}

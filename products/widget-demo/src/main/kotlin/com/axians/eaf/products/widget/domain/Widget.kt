package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.multitenancy.TenantContext
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
class Widget : Serializable {
    @AggregateIdentifier
    private lateinit var widgetId: WidgetId

    private lateinit var name: String
    private var published: Boolean = false
    private lateinit var tenantId: String

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
     * - Tenant context must match command tenant ID (Layer 2 validation, Story 4.6)
     *
     * @param command CreateWidgetCommand with widget ID, name, and tenant ID
     * @throws IllegalArgumentException if name is blank or tenant context mismatch
     */
    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // Layer 2: Tenant context validation (Story 4.6, AC3)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // Generic message (CWE-209 protection)
        }

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
     * - Tenant context must match command tenant ID (Layer 2 validation, Story 4.6)
     *
     * @param command UpdateWidgetCommand with new name and tenant ID
     * @throws IllegalArgumentException if widget is already published, name is blank, or tenant context mismatch
     */
    @CommandHandler
    fun handle(command: UpdateWidgetCommand) {
        require(!published) { "Cannot update published widget" }
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // Layer 2: Tenant context validation (Story 4.6, AC3)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // Generic message (CWE-209 protection)
        }
        require(this.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // Generic message (CWE-209 protection)
        }

        AggregateLifecycle.apply(
            WidgetUpdatedEvent(widgetId, command.name),
        )
    }

    /**
     * Command handler for widget publishing.
     *
     * **Validation:**
     * - Widget must not already be published
     * - Tenant context must match command tenant ID (Layer 2 validation, Story 4.6)
     *
     * @param command PublishWidgetCommand with tenant ID
     * @throws IllegalArgumentException if widget is already published or tenant context mismatch
     */
    @CommandHandler
    fun handle(command: PublishWidgetCommand) {
        require(!published) { "Widget already published" }

        // Layer 2: Tenant context validation (Story 4.6, AC3)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // Generic message (CWE-209 protection)
        }
        require(this.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // Generic message (CWE-209 protection)
        }

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
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.name = event.name
        this.published = false
        // Extract tenant_id from event metadata (Story 4.6, AC4)
        // TenantCorrelationDataProvider automatically enriches event metadata
        this.tenantId = TenantContext.getCurrentTenantId()
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
    fun on(event: WidgetUpdatedEvent) {
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
    fun on(event: WidgetPublishedEvent) {
        this.published = true
    }
}

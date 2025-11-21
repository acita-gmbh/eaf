package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.multitenancy.TenantContext
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.messaging.MetaData
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
     * - Tenant context must match command tenantId (Layer 2 - Story 4.6)
     *
     * @param command CreateWidgetCommand with widget ID, name, and tenantId
     * @throws IllegalArgumentException if name is blank
     * @throws IllegalStateException if tenant context is missing or mismatched
     */
    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        // Layer 2: Tenant Context Validation (Story 4.6 AC3)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 compliant generic message
        }

        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // AC4: Enrich event with tenant_id metadata for async processors (Story 4.5)
        AggregateLifecycle.apply(
            WidgetCreatedEvent(command.widgetId, command.name),
            MetaData.with("tenant_id", command.tenantId),
        )
    }

    /**
     * Command handler for widget updates.
     *
     * **Validation:**
     * - Tenant context must match command tenantId (Layer 2 - Story 4.6)
     * - Widget must not be published
     * - Name must not be blank
     *
     * @param command UpdateWidgetCommand with new name and tenantId
     * @throws IllegalStateException if tenant context is missing or mismatched
     * @throws IllegalArgumentException if widget is already published or name is blank
     */
    @CommandHandler
    fun handle(command: UpdateWidgetCommand) {
        // Layer 2: Tenant Context Validation (Story 4.6 AC3)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 compliant generic message
        }

        require(!published) { "Cannot update published widget" }
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // AC4: Enrich event with tenant_id metadata for async processors (Story 4.5)
        AggregateLifecycle.apply(
            WidgetUpdatedEvent(widgetId, command.name),
            MetaData.with("tenant_id", command.tenantId),
        )
    }

    /**
     * Command handler for widget publishing.
     *
     * **Validation:**
     * - Tenant context must match command tenantId (Layer 2 - Story 4.6)
     * - Widget must not already be published
     *
     * @param command PublishWidgetCommand with tenantId
     * @throws IllegalStateException if tenant context is missing or mismatched
     * @throws IllegalArgumentException if widget is already published
     */
    @CommandHandler
    fun handle(command: PublishWidgetCommand) {
        // Layer 2: Tenant Context Validation (Story 4.6 AC3)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 compliant generic message
        }

        require(!published) { "Widget already published" }

        // AC4: Enrich event with tenant_id metadata for async processors (Story 4.5)
        AggregateLifecycle.apply(
            WidgetPublishedEvent(widgetId),
            MetaData.with("tenant_id", command.tenantId),
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

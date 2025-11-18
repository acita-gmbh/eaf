package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.multitenancy.TenantContext
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import java.io.Serializable

/**
 * Widget aggregate demonstrating CQRS/Event Sourcing pattern with multi-tenancy.
 *
 * Implements command handling with business logic validation and
 * event sourcing handlers for state reconstruction from events.
 *
 * **Business Rules:**
 * - Widget name must not be blank
 * - Published widgets cannot be updated
 * - Widgets can only be published once
 *
 * **Multi-Tenancy (Story 4.6):**
 * - All commands implement TenantAwareCommand (Layer 2 validation)
 * - Command handler validates tenantId matches TenantContext (defensive check)
 * - Events include tenantId in payload for Layer 3 (PostgreSQL RLS)
 * - Event metadata automatically enriched with tenantId (Story 4.5)
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
    private lateinit var tenantId: String
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
     * **Multi-Tenancy (Story 4.6 - AC2, AC3):**
     * - AC2: Extract tenant_id from TenantContext (already validated by Layer 2 interceptor)
     * - AC3: Defensive validation - verify command.tenantId matches TenantContext
     * - Layer 2 (TenantValidationInterceptor) already validated this, but we add defensive check
     *
     * @param command CreateWidgetCommand with widget ID, name, and tenantId
     * @throws IllegalArgumentException if name is blank or tenant context mismatch
     */
    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // AC3: Defensive tenant context validation (Layer 2 already checked, but verify)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch"
        }

        // AC2: Include tenant_id in event (from command, already validated)
        AggregateLifecycle.apply(
            WidgetCreatedEvent(
                widgetId = command.widgetId,
                name = command.name,
                tenantId = command.tenantId,
            ),
        )
    }

    /**
     * Command handler for widget updates.
     *
     * **Validation:**
     * - Widget must not be published
     * - Name must not be blank
     *
     * **Multi-Tenancy (Story 4.6 - AC3):**
     * - Defensive tenant validation
     *
     * @param command UpdateWidgetCommand with new name and tenantId
     * @throws IllegalArgumentException if widget is already published, name is blank, or tenant mismatch
     */
    @CommandHandler
    fun handle(command: UpdateWidgetCommand) {
        require(!published) { "Cannot update published widget" }
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // AC3: Defensive tenant context validation
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch"
        }

        AggregateLifecycle.apply(
            WidgetUpdatedEvent(
                widgetId = widgetId,
                name = command.name,
                tenantId = command.tenantId,
            ),
        )
    }

    /**
     * Command handler for widget publishing.
     *
     * **Validation:**
     * - Widget must not already be published
     *
     * **Multi-Tenancy (Story 4.6 - AC3):**
     * - Defensive tenant validation
     *
     * @param command PublishWidgetCommand with tenantId
     * @throws IllegalArgumentException if widget is already published or tenant mismatch
     */
    @CommandHandler
    fun handle(command: PublishWidgetCommand) {
        require(!published) { "Widget already published" }

        // AC3: Defensive tenant context validation
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch"
        }

        AggregateLifecycle.apply(
            WidgetPublishedEvent(
                widgetId = widgetId,
                tenantId = command.tenantId,
            ),
        )
    }

    /**
     * Event sourcing handler for widget creation.
     *
     * Initializes aggregate state from WidgetCreatedEvent.
     * No business logic - pure state reconstruction.
     *
     * @param event WidgetCreatedEvent with initial widget data and tenantId
     */
    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.name = event.name
        this.tenantId = event.tenantId
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

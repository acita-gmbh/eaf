package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.domain.WidgetCreatedEvent
import com.axians.eaf.products.widget.domain.WidgetPublishedEvent
import com.axians.eaf.products.widget.domain.WidgetUpdatedEvent
import io.micrometer.core.instrument.MeterRegistry
import org.axonframework.eventhandling.EventHandler
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.util.UUID

/**
 * Event handler that projects Widget domain events into the widget_projection read model.
 *
 * This handler listens to Widget events and maintains the denormalized projection table
 * for efficient querying. Uses jOOQ for type-safe SQL operations.
 *
 * **Performance Target:** Event lag <10 seconds (FR011)
 *
 * **Error Handling:** Logs exceptions, emits metrics, and re-throws for Axon retry mechanism.
 */
@Component
class WidgetProjectionEventHandler(
    private val dsl: DSLContext,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val WIDGET_PROJECTION_TABLE = DSL.table("widget_projection")
    }

    /**
     * Projects WidgetCreatedEvent into widget_projection table.
     *
     * **Multi-Tenancy (Story 4.6):**
     * - Inserts tenant_id from event payload for Layer 3 isolation
     * - TenantContext available from Story 4.5 context propagation
     *
     * Inserts a new row with initial state: published=false.
     */
    @EventHandler
    fun on(event: WidgetCreatedEvent) {
        try {
            val insertedRows =
                dsl
                    .insertInto(WIDGET_PROJECTION_TABLE)
                    .columns(
                        DSL.field("id"),
                        DSL.field("tenant_id"),
                        DSL.field("name"),
                        DSL.field("published"),
                        DSL.field("created_at"),
                        DSL.field("updated_at"),
                    ).values(
                        UUID.fromString(event.widgetId.value),
                        event.tenantId,
                        event.name,
                        false,
                        event.occurredAt.atOffset(ZoneOffset.UTC),
                        event.occurredAt.atOffset(ZoneOffset.UTC),
                    ).onConflictDoNothing()
                    .execute()

            // Only increment metric if row was actually inserted (idempotency support)
            if (insertedRows > 0) {
                meterRegistry.counter("projection.widget.created").increment()
            }
        } catch (e: Exception) {
            logger.error("Failed to project WidgetCreatedEvent for widgetId=${event.widgetId.value}", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e // Re-throw for Axon retry mechanism
        }
    }

    /**
     * Projects WidgetUpdatedEvent into widget_projection table.
     *
     * Updates the name and updated_at timestamp.
     */
    @EventHandler
    fun on(event: WidgetUpdatedEvent) {
        try {
            val updatedRows =
                dsl
                    .update(WIDGET_PROJECTION_TABLE)
                    .set(DSL.field("name", String::class.java), event.name)
                    .set(DSL.field("updated_at"), event.occurredAt.atOffset(ZoneOffset.UTC))
                    .where(DSL.field("id").eq(UUID.fromString(event.widgetId.value)))
                    .execute()

            // Fail fast if projection row is missing (event ordering issue or data loss)
            require(updatedRows == 1) {
                "Projection row missing for widgetId=${event.widgetId.value}. " +
                    "Expected 1 row updated, got $updatedRows. Possible event ordering issue or missing WidgetCreatedEvent."
            }

            meterRegistry.counter("projection.widget.updated").increment()
        } catch (e: Exception) {
            logger.error("Failed to project WidgetUpdatedEvent for widgetId=${event.widgetId.value}", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e // Re-throw for Axon retry mechanism
        }
    }

    /**
     * Projects WidgetPublishedEvent into widget_projection table.
     *
     * Sets published flag to true and updates timestamp.
     */
    @EventHandler
    fun on(event: WidgetPublishedEvent) {
        try {
            val publishedRows =
                dsl
                    .update(WIDGET_PROJECTION_TABLE)
                    .set(DSL.field("published", Boolean::class.java), true)
                    .set(DSL.field("updated_at"), event.occurredAt.atOffset(ZoneOffset.UTC))
                    .where(DSL.field("id").eq(UUID.fromString(event.widgetId.value)))
                    .execute()

            // Fail fast if projection row is missing (event ordering issue or data loss)
            require(publishedRows == 1) {
                "Projection row missing for widgetId=${event.widgetId.value}. " +
                    "Expected 1 row updated, got $publishedRows. Possible event ordering issue or missing WidgetCreatedEvent."
            }

            meterRegistry.counter("projection.widget.published").increment()
        } catch (e: Exception) {
            logger.error("Failed to project WidgetPublishedEvent for widgetId=${event.widgetId.value}", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e // Re-throw for Axon retry mechanism
        }
    }
}

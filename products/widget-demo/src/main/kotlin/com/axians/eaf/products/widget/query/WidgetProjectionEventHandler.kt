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

    /**
     * Projects WidgetCreatedEvent into widget_projection table.
     *
     * Inserts a new row with initial state: published=false.
     */
    @EventHandler
    fun on(event: WidgetCreatedEvent) {
        try {
            val table = DSL.table("widget_projection")
            dsl
                .insertInto(table)
                .columns(
                    DSL.field("id"),
                    DSL.field("name"),
                    DSL.field("published"),
                    DSL.field("created_at"),
                    DSL.field("updated_at"),
                ).values(
                    UUID.fromString(event.widgetId.value),
                    event.name,
                    false,
                    event.occurredAt.atOffset(ZoneOffset.UTC),
                    event.occurredAt.atOffset(ZoneOffset.UTC),
                ).execute()

            meterRegistry.counter("projection.widget.created").increment()
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
            val table = DSL.table("widget_projection")
            dsl
                .update(table)
                .set(DSL.field("name"), event.name)
                .set(DSL.field("updated_at"), event.occurredAt.atOffset(ZoneOffset.UTC))
                .where(DSL.field("id").eq(UUID.fromString(event.widgetId.value)))
                .execute()

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
            val table = DSL.table("widget_projection")
            dsl
                .update(table)
                .set(DSL.field("published"), true)
                .set(DSL.field("updated_at"), event.occurredAt.atOffset(ZoneOffset.UTC))
                .where(DSL.field("id").eq(UUID.fromString(event.widgetId.value)))
                .execute()

            meterRegistry.counter("projection.widget.published").increment()
        } catch (e: Exception) {
            logger.error("Failed to project WidgetPublishedEvent for widgetId=${event.widgetId.value}", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e // Re-throw for Axon retry mechanism
        }
    }
}

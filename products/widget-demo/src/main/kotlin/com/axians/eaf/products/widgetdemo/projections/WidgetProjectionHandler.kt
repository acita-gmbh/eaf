package com.axians.eaf.products.widgetdemo.projections

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.api.widget.events.WidgetUpdatedEvent
import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.eventhandling.EventHandler
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Event handler for Widget projection updates.
 *
 * This component processes Widget events and maintains the read-side projections
 * for CQRS queries. It uses the Tracking Event Processor to consume events
 * from the event store and update the widget_projection table.
 *
 * The handler is configured with @ProcessingGroup annotation to use the
 * "widget-projection" processor defined in application.yml.
 */
@Component
@org.axonframework.config.ProcessingGroup("widget-projection")
class WidgetProjectionHandler(
    private val repository: WidgetProjectionRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(WidgetProjectionHandler::class.java)

    /**
     * Handles WidgetCreatedEvent by creating a new projection record.
     *
     * This method processes the event and creates a corresponding entry in the
     * widget_projection table for read operations.
     *
     * @param event the WidgetCreatedEvent containing widget data
     */
    @EventHandler
    @Transactional
    fun on(event: WidgetCreatedEvent) {
        logger.debug(
            "Processing WidgetCreatedEvent for widgetId: {} in tenant: {}",
            event.widgetId,
            event.tenantId,
        )

        try {
            // Convert metadata map to JSON string for storage
            // Empty map serializes to "{}" (explicit clear semantics)
            val metadataJson =
                if (event.metadata.isEmpty()) {
                    "{}" // Explicit empty JSON object
                } else {
                    objectMapper.writeValueAsString(event.metadata)
                }

            val projection =
                WidgetProjection(
                    widgetId = event.widgetId,
                    tenantId = event.tenantId,
                    name = event.name,
                    description = event.description,
                    value = event.value,
                    category = event.category,
                    metadata = metadataJson,
                    createdAt = event.createdAt,
                    updatedAt = event.createdAt,
                )

            repository.save(projection)

            logger.debug(
                "Successfully created widget projection for widgetId: {} in tenant: {}",
                event.widgetId,
                event.tenantId,
            )
        } catch (exception: DataAccessException) {
            logger.error(
                "Failed to process WidgetCreatedEvent for widgetId: {} in tenant: {}. Database error: {}",
                event.widgetId,
                event.tenantId,
                exception.message,
                exception,
            )
            throw exception
        }
    }

    /**
     * Handles WidgetUpdatedEvent by updating the existing projection record.
     *
     * This method processes the event and updates the corresponding entry in the
     * widget_projection table for read operations.
     *
     * @param event the WidgetUpdatedEvent containing updated widget data
     */
    @EventHandler
    @Transactional
    fun on(event: WidgetUpdatedEvent) {
        logger.debug(
            "Processing WidgetUpdatedEvent for widgetId: {} in tenant: {}",
            event.widgetId,
            event.tenantId,
        )

        try {
            val existingProjection = repository.findByWidgetIdAndTenantId(event.widgetId, event.tenantId)

            if (existingProjection == null) {
                logger.warn(
                    "No existing projection found for widgetId: {} in tenant: {} during update. " +
                        "This may indicate events processed out of order.",
                    event.widgetId,
                    event.tenantId,
                )
                return
            }

            // Convert metadata map to JSON string for storage
            // Empty map serializes to "{}" (explicit clear), null means field omitted (keep existing)
            val metadata = event.metadata
            val metadataJson =
                when {
                    metadata == null -> existingProjection.metadata // Field omitted - keep existing
                    metadata.isEmpty() -> "{}" // Explicit clear to empty JSON object
                    else -> objectMapper.writeValueAsString(metadata)
                }

            val updatedProjection =
                existingProjection.update(
                    name = event.name ?: existingProjection.name,
                    description = event.description ?: existingProjection.description,
                    value = event.value ?: existingProjection.value,
                    category = event.category ?: existingProjection.category,
                    metadata = metadataJson,
                )

            repository.save(updatedProjection)

            logger.debug(
                "Successfully updated widget projection for widgetId: {} in tenant: {}",
                event.widgetId,
                event.tenantId,
            )
        } catch (exception: DataAccessException) {
            logger.error(
                "Failed to process WidgetUpdatedEvent for widgetId: {} in tenant: {}. Database error: {}",
                event.widgetId,
                event.tenantId,
                exception.message,
                exception,
            )
            throw exception
        }
    }

    /**
     * Handles ResetProjectionEvent to clear projections during replay scenarios.
     *
     * This method can be used to clean up projections before replaying events
     * from the event store to rebuild the read model.
     *
     * Note: This is called during projection rebuild operations and should
     * handle cleanup gracefully.
     *
     * PERFORMANCE NOTE: Uses batch deletion to avoid locking large tables.
     * Future enhancement: Add repository.deleteAllByTenantId() for tenant-scoped resets.
     */
    @org.axonframework.eventhandling.ResetHandler
    fun resetProjections() {
        logger.info("Resetting widget projections for replay...")

        try {
            val batchSize = 1000
            var totalDeleted = 0L

            while (true) {
                val deleted = repository.deleteBatch(batchSize)
                if (deleted == 0L) break

                totalDeleted += deleted
                logger.debug("Deleted batch of {} projections", deleted)
            }

            logger.info("Successfully cleared {} widget projections for replay", totalDeleted)
        } catch (exception: DataAccessException) {
            logger.error("Failed to reset widget projections. Database error: {}", exception.message, exception)
            throw exception
        }
    }
}

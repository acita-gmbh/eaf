package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.projection.ProjectionError
import io.github.oshai.kotlinlogging.KLogger

/**
 * Extension function for logging projection errors consistently across handlers.
 *
 * This provides a shared implementation for logging projection update failures,
 * ensuring consistent error messages and log levels across all project handlers.
 *
 * Projection errors are logged as warnings because:
 * - The command has already succeeded (events are persisted)
 * - Projections can be rebuilt from the event store
 * - These are not critical failures, but should be monitored
 */
internal fun KLogger.logProjectionError(
    error: ProjectionError,
    projectId: ProjectId,
    correlationId: CorrelationId
) {
    when (error) {
        is ProjectionError.DatabaseError -> warn {
            "Projection update failed for project ${projectId.value}: ${error.message}. " +
                "correlationId=${correlationId.value}. " +
                "Projection can be rebuilt from event store."
        }
        is ProjectionError.NotFound -> warn {
            "Projection not found for project ${projectId.value}. " +
                "correlationId=${correlationId.value}. " +
                "Projection may need to be rebuilt from event store."
        }
    }
}

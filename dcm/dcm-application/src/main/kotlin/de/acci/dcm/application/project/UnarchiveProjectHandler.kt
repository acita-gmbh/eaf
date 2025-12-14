package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectAggregate
import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectStatus
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import de.acci.eaf.eventsourcing.belongsToTenant
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when unarchiving a project.
 */
public sealed class UnarchiveProjectError {
    /**
     * Project not found in the event store.
     */
    public data class NotFound(
        val projectId: ProjectId,
        val message: String = "Project not found: ${projectId.value}"
    ) : UnarchiveProjectError()

    /**
     * Project is not archived.
     */
    public data class NotArchived(
        val projectId: ProjectId,
        val message: String = "Project is not archived: ${projectId.value}"
    ) : UnarchiveProjectError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : UnarchiveProjectError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : UnarchiveProjectError()
}

/**
 * Result of successfully unarchiving a project.
 */
public data class UnarchiveProjectResult(
    val projectId: ProjectId
)

/**
 * Handler for UnarchiveProjectCommand.
 *
 * Loads the ProjectAggregate from the event store, performs the unarchive
 * operation, and persists the resulting events.
 *
 * ## State Requirement
 *
 * Only archived projects can be unarchived. Attempting to unarchive
 * an active project returns NotArchived error.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = UnarchiveProjectHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * ```
 */
public class UnarchiveProjectHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: ProjectEventDeserializer,
    private val projectionUpdater: ProjectProjectionUpdater = NoOpProjectProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the unarchive project command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the unarchived project ID or an error
     */
    public suspend fun handle(
        command: UnarchiveProjectCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<UnarchiveProjectResult, UnarchiveProjectError> {
        // 1. Load events from event store
        val storedEvents = try {
            eventStore.load(command.projectId.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to load events for project: " +
                    "projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UnarchiveProjectError.PersistenceFailure(
                message = "Failed to load project: ${e.message}"
            ).failure()
        }

        // 2. Validate tenant ownership (defense-in-depth security)
        if (!storedEvents.belongsToTenant(command.tenantId)) {
            logger.warn {
                "Tenant mismatch for project: projectId=${command.projectId.value}, " +
                    "requestedTenant=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UnarchiveProjectError.NotFound(
                projectId = command.projectId
            ).failure()
        }

        // 3. Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return UnarchiveProjectError.NotFound(
                projectId = command.projectId
            ).failure()
        }

        // 4. Deserialize events and reconstitute aggregate
        val aggregate = try {
            val domainEvents = storedEvents.map { eventDeserializer.deserialize(it) }
            ProjectAggregate.reconstitute(command.projectId, domainEvents)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to deserialize/reconstitute events for project: " +
                    "projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UnarchiveProjectError.PersistenceFailure(
                message = "Failed to reconstitute project: ${e.message}"
            ).failure()
        }

        // 4. Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return UnarchiveProjectError.ConcurrencyConflict(
                message = "Version mismatch: expected ${command.version}, actual ${aggregate.version}"
            ).failure()
        }

        val expectedVersion = aggregate.version

        // 5. Check if actually archived
        if (aggregate.status == ProjectStatus.ACTIVE) {
            return UnarchiveProjectError.NotArchived(
                projectId = command.projectId
            ).failure()
        }

        // 6. Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.unarchivedBy,
            correlationId = correlationId
        )

        // 7. Perform unarchive
        try {
            aggregate.unarchive(metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logger.debug(e) {
                "Unarchive failed: projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UnarchiveProjectError.NotArchived(
                projectId = command.projectId
            ).failure()
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to unarchive project: projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UnarchiveProjectError.PersistenceFailure(
                message = "Failed to unarchive project: ${e.message}"
            ).failure()
        }

        // 8. Persist new events
        val appendResult = try {
            eventStore.append(
                aggregateId = command.projectId.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = expectedVersion
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to persist project unarchive: " +
                    "projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UnarchiveProjectError.PersistenceFailure(
                message = "Failed to persist unarchive: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "Project unarchived: projectId=${command.projectId.value}, " +
                        "unarchivedBy=${command.unarchivedBy.value}"
                }

                // Update projection (non-fatal)
                try {
                    projectionUpdater.updateStatus(
                        ProjectStatusUpdate(
                            id = command.projectId,
                            status = ProjectStatus.ACTIVE,
                            version = aggregate.version.toInt()
                        )
                    ).onFailure { error ->
                        logger.logProjectionError(
                            error = error,
                            projectId = command.projectId,
                            correlationId = correlationId
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) {
                        "Projection update failed (updateStatus): " +
                            "projectId=${command.projectId.value}, " +
                            "correlationId=${correlationId.value}"
                    }
                }

                UnarchiveProjectResult(projectId = command.projectId).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    UnarchiveProjectError.ConcurrencyConflict(
                        message = "Concurrent modification detected for project ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }
}

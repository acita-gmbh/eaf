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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when archiving a project.
 */
public sealed class ArchiveProjectError {
    /**
     * Project not found in the event store.
     */
    public data class NotFound(
        val projectId: ProjectId,
        val message: String = "Project not found: ${projectId.value}"
    ) : ArchiveProjectError()

    /**
     * Project is already archived.
     */
    public data class AlreadyArchived(
        val projectId: ProjectId,
        val message: String = "Project is already archived: ${projectId.value}"
    ) : ArchiveProjectError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : ArchiveProjectError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : ArchiveProjectError()
}

/**
 * Result of successfully archiving a project.
 */
public data class ArchiveProjectResult(
    val projectId: ProjectId
)

/**
 * Handler for ArchiveProjectCommand.
 *
 * Loads the ProjectAggregate from the event store, performs the archive
 * operation, and persists the resulting events.
 *
 * ## Idempotency
 *
 * Archiving an already archived project is treated as a no-op error
 * (AlreadyArchived) rather than silently succeeding. This allows the
 * caller to detect if another admin already archived the project.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = ArchiveProjectHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * ```
 */
public class ArchiveProjectHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: ProjectEventDeserializer,
    private val projectionUpdater: ProjectProjectionUpdater = NoOpProjectProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the archive project command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the archived project ID or an error
     */
    public suspend fun handle(
        command: ArchiveProjectCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<ArchiveProjectResult, ArchiveProjectError> {
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
            return ArchiveProjectError.PersistenceFailure(
                message = "Failed to load project: ${e.message}"
            ).failure()
        }

        // 2. Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return ArchiveProjectError.NotFound(
                projectId = command.projectId
            ).failure()
        }

        // 3. Deserialize events and reconstitute aggregate
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
            return ArchiveProjectError.PersistenceFailure(
                message = "Failed to reconstitute project: $e"
            ).failure()
        }

        // 4. Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return ArchiveProjectError.ConcurrencyConflict(
                message = "Version mismatch: expected ${command.version}, actual ${aggregate.version}"
            ).failure()
        }

        val expectedVersion = aggregate.version

        // 5. Check if already archived (aggregate.archive() is idempotent, but we want to inform caller)
        if (aggregate.status == ProjectStatus.ARCHIVED) {
            return ArchiveProjectError.AlreadyArchived(
                projectId = command.projectId
            ).failure()
        }

        // 6. Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.archivedBy,
            correlationId = correlationId
        )

        // 7. Perform archive
        aggregate.archive(metadata)

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
                "Failed to persist project archive: " +
                    "projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return ArchiveProjectError.PersistenceFailure(
                message = "Failed to persist archive: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "Project archived: projectId=${command.projectId.value}, " +
                        "archivedBy=${command.archivedBy.value}"
                }

                // Update projection
                projectionUpdater.updateStatus(
                    ProjectStatusUpdate(
                        id = command.projectId,
                        status = ProjectStatus.ARCHIVED,
                        version = aggregate.version.toInt()
                    )
                ).onFailure { error ->
                    logger.logProjectionError(
                        error = error,
                        projectId = command.projectId,
                        correlationId = correlationId
                    )
                }

                ArchiveProjectResult(projectId = command.projectId).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    ArchiveProjectError.ConcurrencyConflict(
                        message = "Concurrent modification detected for project ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }
}

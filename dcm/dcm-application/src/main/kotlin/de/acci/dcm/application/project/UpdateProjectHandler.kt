package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectAggregate
import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
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
 * Errors that can occur when updating a project.
 */
public sealed class UpdateProjectError {
    /**
     * Project not found in the event store.
     */
    public data class NotFound(
        val projectId: ProjectId,
        val message: String = "Project not found: ${projectId.value}"
    ) : UpdateProjectError()

    /**
     * Project is archived and cannot be updated.
     */
    public data class ProjectArchived(
        val projectId: ProjectId,
        val message: String = "Cannot update archived project: ${projectId.value}"
    ) : UpdateProjectError()

    /**
     * New name conflicts with an existing project.
     */
    public data class NameAlreadyExists(
        val name: ProjectName,
        val message: String = "Project name already exists: ${name.value}"
    ) : UpdateProjectError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : UpdateProjectError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : UpdateProjectError()
}

/**
 * Result of successfully updating a project.
 */
public data class UpdateProjectResult(
    val projectId: ProjectId
)

/**
 * Handler for UpdateProjectCommand.
 *
 * Loads the ProjectAggregate from the event store, validates the update
 * is allowed (not archived), and persists the resulting events.
 *
 * ## Archived Projects
 *
 * Archived projects reject all mutations except unarchive.
 * Attempting to update an archived project returns ProjectArchived error.
 *
 * ## Name Uniqueness
 *
 * If the name is changing, validates the new name doesn't conflict
 * with another project in the same tenant.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = UpdateProjectHandler(eventStore, eventDeserializer, projectQueryService)
 * val result = handler.handle(command)
 * ```
 */
public class UpdateProjectHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: ProjectEventDeserializer,
    private val projectQueryService: ProjectQueryService = NoOpProjectQueryService,
    private val projectionUpdater: ProjectProjectionUpdater = NoOpProjectProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the update project command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the updated project ID or an error
     */
    public suspend fun handle(
        command: UpdateProjectCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<UpdateProjectResult, UpdateProjectError> {
        // 1. Load events from event store
        val storedEvents = try {
            eventStore.load(command.projectId.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to load events for project: " +
                    "projectId=${command.projectId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UpdateProjectError.PersistenceFailure(
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
            return UpdateProjectError.NotFound(
                projectId = command.projectId
            ).failure()
        }

        // 3. Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return UpdateProjectError.NotFound(
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
            return UpdateProjectError.PersistenceFailure(
                message = "Failed to reconstitute project: ${e.message}"
            ).failure()
        }

        // 4. Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return UpdateProjectError.ConcurrencyConflict(
                message = "Version mismatch: expected ${command.version}, actual ${aggregate.version}"
            ).failure()
        }

        val expectedVersion = aggregate.version

        // 5. Check if name is changing and if new name is unique
        if (aggregate.name.normalized != command.name.normalized) {
            val existingProject = try {
                projectQueryService.findByName(command.tenantId, command.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) {
                    "Failed to check project name uniqueness: " +
                        "name=${command.name.value}, " +
                        "correlationId=${correlationId.value}"
                }
                return UpdateProjectError.PersistenceFailure(
                    message = "Failed to check uniqueness: ${e.message}"
                ).failure()
            }

            if (existingProject != null && existingProject != command.projectId) {
                return UpdateProjectError.NameAlreadyExists(
                    name = command.name
                ).failure()
            }
        }

        // 6. Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.updatedBy,
            correlationId = correlationId
        )

        // 7. Perform update (may throw IllegalStateException if archived)
        try {
            aggregate.update(
                name = command.name,
                description = command.description,
                metadata = metadata
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logger.debug(e) {
                "Update failed: projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UpdateProjectError.ProjectArchived(
                projectId = command.projectId
            ).failure()
        } catch (e: Exception) {
            logger.error(e) {
                "Unexpected error during project update: projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UpdateProjectError.PersistenceFailure(
                message = "Failed to update project: ${e.message}"
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
                "Failed to persist project update: " +
                    "projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return UpdateProjectError.PersistenceFailure(
                message = "Failed to persist update: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "Project updated: projectId=${command.projectId.value}, " +
                        "name=${command.name.value}, " +
                        "updatedBy=${command.updatedBy.value}"
                }

                // Update projection
                projectionUpdater.updateProject(
                    UpdateProjectProjection(
                        id = command.projectId,
                        name = command.name,
                        description = command.description,
                        version = aggregate.version.toInt()
                    )
                ).onFailure { error ->
                    logger.logProjectionError(
                        error = error,
                        projectId = command.projectId,
                        correlationId = correlationId
                    )
                }

                UpdateProjectResult(projectId = command.projectId).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    UpdateProjectError.ConcurrencyConflict(
                        message = "Concurrent modification detected for project ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }
}

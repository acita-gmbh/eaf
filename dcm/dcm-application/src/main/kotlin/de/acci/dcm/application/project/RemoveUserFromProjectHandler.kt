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
import de.acci.eaf.eventsourcing.projection.ProjectionError
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when removing a user from a project.
 */
public sealed class RemoveUserFromProjectError {
    /**
     * Project not found in the event store.
     */
    public data class NotFound(
        val projectId: ProjectId,
        val message: String = "Project not found: ${projectId.value}"
    ) : RemoveUserFromProjectError()

    /**
     * Project is archived and cannot have members removed.
     */
    public data class ProjectArchived(
        val projectId: ProjectId,
        val message: String = "Cannot remove users from archived project: ${projectId.value}"
    ) : RemoveUserFromProjectError()

    /**
     * Cannot remove the project creator.
     */
    public data class CannotRemoveCreator(
        val projectId: ProjectId,
        val message: String = "Cannot remove the project creator"
    ) : RemoveUserFromProjectError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : RemoveUserFromProjectError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : RemoveUserFromProjectError()
}

/**
 * Result of successfully removing a user from a project.
 */
public data class RemoveUserFromProjectResult(
    val projectId: ProjectId,
    val wasNotMember: Boolean
)

/**
 * Handler for RemoveUserFromProjectCommand.
 *
 * Loads the ProjectAggregate from the event store, removes the user,
 * and persists the resulting events.
 *
 * ## Creator Protection
 *
 * The project creator cannot be removed. Attempting to remove the
 * creator returns a CannotRemoveCreator error.
 *
 * ## Idempotency
 *
 * Removing a user who is not a member is idempotent (no new event emitted).
 *
 * ## Archived Projects
 *
 * Users cannot be removed from archived projects. This returns a
 * ProjectArchived error.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = RemoveUserFromProjectHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * ```
 */
public class RemoveUserFromProjectHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: ProjectEventDeserializer,
    private val projectionUpdater: ProjectProjectionUpdater = NoOpProjectProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the remove user from project command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing success status or an error
     */
    public suspend fun handle(
        command: RemoveUserFromProjectCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<RemoveUserFromProjectResult, RemoveUserFromProjectError> {
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
            return RemoveUserFromProjectError.PersistenceFailure(
                message = "Failed to load project: ${e.message}"
            ).failure()
        }

        // 2. Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return RemoveUserFromProjectError.NotFound(
                projectId = command.projectId
            ).failure()
        }

        // 3. Deserialize events and reconstitute aggregate
        val domainEvents = try {
            storedEvents.map { eventDeserializer.deserialize(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to deserialize events for project: " +
                    "projectId=${command.projectId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return RemoveUserFromProjectError.PersistenceFailure(
                message = "Failed to deserialize project events: ${e.message}"
            ).failure()
        }
        val aggregate = ProjectAggregate.reconstitute(command.projectId, domainEvents)

        // 4. Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return RemoveUserFromProjectError.ConcurrencyConflict(
                message = "Version mismatch: expected ${command.version}, actual ${aggregate.version}"
            ).failure()
        }

        val expectedVersion = aggregate.version

        // 5. Check if project is archived
        if (aggregate.status == ProjectStatus.ARCHIVED) {
            return RemoveUserFromProjectError.ProjectArchived(
                projectId = command.projectId
            ).failure()
        }

        // 6. Check if user was a member (for result feedback)
        val wasMember = aggregate.hasMember(command.userId)

        // 7. Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.removedBy,
            correlationId = correlationId
        )

        // 8. Perform removal (may throw IllegalArgumentException if creator)
        try {
            aggregate.removeUser(
                userId = command.userId,
                metadata = metadata
            )
        } catch (e: IllegalArgumentException) {
            logger.warn {
                "Attempt to remove project creator: projectId=${command.projectId.value}, " +
                    "userId=${command.userId.value}"
            }
            return RemoveUserFromProjectError.CannotRemoveCreator(
                projectId = command.projectId
            ).failure()
        }

        // 9. If no events were emitted (idempotent case - not a member), return early
        if (aggregate.uncommittedEvents.isEmpty()) {
            return RemoveUserFromProjectResult(
                projectId = command.projectId,
                wasNotMember = !wasMember
            ).success()
        }

        // 10. Persist new events
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
                "Failed to persist user removal: " +
                    "projectId=${command.projectId.value}, " +
                    "userId=${command.userId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return RemoveUserFromProjectError.PersistenceFailure(
                message = "Failed to persist removal: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "User removed from project: projectId=${command.projectId.value}, " +
                        "userId=${command.userId.value}, " +
                        "removedBy=${command.removedBy.value}"
                }

                // Update projection
                projectionUpdater.removeMember(
                    projectId = command.projectId,
                    userId = command.userId
                ).onFailure { error ->
                    logProjectionError(error, command.projectId, correlationId)
                }

                RemoveUserFromProjectResult(
                    projectId = command.projectId,
                    wasNotMember = false
                ).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    RemoveUserFromProjectError.ConcurrencyConflict(
                        message = "Concurrent modification detected for project ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }

    private fun logProjectionError(
        error: ProjectionError,
        projectId: ProjectId,
        correlationId: CorrelationId
    ) {
        when (error) {
            is ProjectionError.DatabaseError -> {
                logger.warn {
                    "Projection update failed for project ${projectId.value}: ${error.message}. " +
                        "correlationId=${correlationId.value}."
                }
            }
            is ProjectionError.NotFound -> {
                logger.warn {
                    "Projection not found for project ${projectId.value}. " +
                        "correlationId=${correlationId.value}."
                }
            }
        }
    }
}

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
 * Errors that can occur when assigning a user to a project.
 */
public sealed class AssignUserToProjectError {
    /**
     * Project not found in the event store.
     */
    public data class NotFound(
        val projectId: ProjectId,
        val message: String = "Project not found: ${projectId.value}"
    ) : AssignUserToProjectError()

    /**
     * Project is archived and cannot have members assigned.
     */
    public data class ProjectArchived(
        val projectId: ProjectId,
        val message: String = "Cannot assign users to archived project: ${projectId.value}"
    ) : AssignUserToProjectError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : AssignUserToProjectError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : AssignUserToProjectError()
}

/**
 * Result of successfully assigning a user to a project.
 */
public data class AssignUserToProjectResult(
    val projectId: ProjectId,
    val wasAlreadyMember: Boolean
)

/**
 * Handler for AssignUserToProjectCommand.
 *
 * Loads the ProjectAggregate from the event store, assigns the user,
 * and persists the resulting events.
 *
 * ## Idempotency
 *
 * Assigning a user who is already a member with the same role is idempotent
 * (no new event emitted). If the user exists with a different role, an
 * upgrade event is emitted.
 *
 * ## Archived Projects
 *
 * Users cannot be assigned to archived projects. This returns a
 * ProjectArchived error.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = AssignUserToProjectHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * ```
 */
public class AssignUserToProjectHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: ProjectEventDeserializer,
    private val projectionUpdater: ProjectProjectionUpdater = NoOpProjectProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the assign user to project command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing success status or an error
     */
    public suspend fun handle(
        command: AssignUserToProjectCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<AssignUserToProjectResult, AssignUserToProjectError> {
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
            return AssignUserToProjectError.PersistenceFailure(
                message = "Failed to load project: ${e.message}"
            ).failure()
        }

        // 2. Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return AssignUserToProjectError.NotFound(
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
            return AssignUserToProjectError.PersistenceFailure(
                message = "Failed to deserialize project events: ${e.message}"
            ).failure()
        }
        val aggregate = ProjectAggregate.reconstitute(command.projectId, domainEvents)

        // 4. Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return AssignUserToProjectError.ConcurrencyConflict(
                message = "Version mismatch: expected ${command.version}, actual ${aggregate.version}"
            ).failure()
        }

        val expectedVersion = aggregate.version

        // 5. Check if project is archived
        if (aggregate.status == ProjectStatus.ARCHIVED) {
            return AssignUserToProjectError.ProjectArchived(
                projectId = command.projectId
            ).failure()
        }

        // 6. Check if user is already a member with same role (for result feedback)
        val existingMember = aggregate.getMember(command.userId)
        val wasAlreadyMemberWithSameRole = existingMember?.role == command.role

        // 7. Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.assignedBy,
            correlationId = correlationId
        )

        // 8. Perform assignment (idempotent if same role)
        aggregate.assignUser(
            userId = command.userId,
            role = command.role,
            metadata = metadata
        )

        // 9. If no events were emitted (idempotent case), return early
        if (aggregate.uncommittedEvents.isEmpty()) {
            return AssignUserToProjectResult(
                projectId = command.projectId,
                wasAlreadyMember = true
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
                "Failed to persist user assignment: " +
                    "projectId=${command.projectId.value}, " +
                    "userId=${command.userId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return AssignUserToProjectError.PersistenceFailure(
                message = "Failed to persist assignment: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "User assigned to project: projectId=${command.projectId.value}, " +
                        "userId=${command.userId.value}, " +
                        "role=${command.role}, " +
                        "assignedBy=${command.assignedBy.value}"
                }

                // Update projection
                val member = aggregate.getMember(command.userId)
                if (member != null) {
                    if (existingMember != null) {
                        // Role upgrade
                        projectionUpdater.updateMemberRole(
                            UpdateProjectMemberRole(
                                projectId = command.projectId,
                                userId = command.userId,
                                role = command.role
                            )
                        ).onFailure { error ->
                            logProjectionError(error, command.projectId, correlationId)
                        }
                    } else {
                        // New member
                        projectionUpdater.insertMember(
                            NewProjectMemberProjection(
                                projectId = command.projectId,
                                tenantId = command.tenantId,
                                userId = command.userId,
                                role = command.role,
                                assignedBy = command.assignedBy,
                                assignedAt = member.assignedAt
                            )
                        ).onFailure { error ->
                            logProjectionError(error, command.projectId, correlationId)
                        }
                    }
                }

                AssignUserToProjectResult(
                    projectId = command.projectId,
                    wasAlreadyMember = wasAlreadyMemberWithSameRole
                ).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    AssignUserToProjectError.ConcurrencyConflict(
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

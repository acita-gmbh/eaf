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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when creating a project.
 */
public sealed class CreateProjectError {
    /**
     * A project with this name already exists in the tenant.
     * Names are case-insensitive unique.
     */
    public data class NameAlreadyExists(
        val name: ProjectName,
        val message: String = "Project name already exists: ${name.value}"
    ) : CreateProjectError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : CreateProjectError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : CreateProjectError()
}

/**
 * Result of successfully creating a project.
 */
public data class CreateProjectResult(
    val projectId: ProjectId
)

/**
 * Handler for CreateProjectCommand.
 *
 * Creates a new ProjectAggregate and persists the resulting events
 * to the event store. Enforces project name uniqueness within a tenant.
 *
 * ## Uniqueness Constraint
 *
 * Project names must be unique within a tenant (case-insensitive).
 * This is enforced at the handler level by querying the read model
 * before creating the aggregate, as cross-aggregate constraints
 * cannot be enforced within a single aggregate.
 *
 * ## Creator Auto-Assignment
 *
 * The creator is automatically assigned as PROJECT_ADMIN.
 * This produces two events: ProjectCreated + UserAssignedToProject.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = CreateProjectHandler(eventStore, projectQueryService)
 * val result = handler.handle(command)
 * result.fold(
 *     onSuccess = { println("Created project: ${it.projectId}") },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class CreateProjectHandler(
    private val eventStore: EventStore,
    private val projectQueryService: ProjectQueryService = NoOpProjectQueryService,
    private val projectionUpdater: ProjectProjectionUpdater = NoOpProjectProjectionUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the create project command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the created project ID or an error
     */
    public suspend fun handle(
        command: CreateProjectCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<CreateProjectResult, CreateProjectError> {
        // 1. Check name uniqueness via projection (cross-aggregate constraint)
        val existingProject = try {
            projectQueryService.findByName(command.tenantId, command.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to check project name uniqueness: " +
                    "name=${command.name.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return CreateProjectError.PersistenceFailure(
                message = "Failed to check uniqueness: ${e.message}"
            ).failure()
        }

        if (existingProject != null) {
            logger.debug {
                "Project name already exists: name=${command.name.value}, " +
                    "existingId=${existingProject.value}, " +
                    "tenantId=${command.tenantId.value}"
            }
            return CreateProjectError.NameAlreadyExists(
                name = command.name
            ).failure()
        }

        // 2. Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.createdBy,
            correlationId = correlationId
        )

        // 3. Create the aggregate (emits ProjectCreated + UserAssignedToProject)
        val aggregate = ProjectAggregate.create(
            name = command.name,
            description = command.description,
            metadata = metadata
        )

        // 4. Persist events to the event store
        val appendResult = try {
            eventStore.append(
                aggregateId = aggregate.id.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = 0 // New aggregate starts at version 0
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to persist project: " +
                    "projectId=${aggregate.id.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "userId=${command.createdBy.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return CreateProjectError.PersistenceFailure(
                message = "Failed to persist project: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "Project created: projectId=${aggregate.id.value}, " +
                        "name=${command.name.value}, " +
                        "tenantId=${command.tenantId.value}, " +
                        "createdBy=${command.createdBy.value}"
                }

                // Update projection (fire-and-forget, log errors but don't fail)
                projectionUpdater.insertProject(
                    NewProjectProjection(
                        id = aggregate.id,
                        tenantId = command.tenantId,
                        name = command.name,
                        description = command.description,
                        status = aggregate.status,
                        createdBy = command.createdBy,
                        createdAt = metadata.timestamp,
                        version = aggregate.version.toInt()
                    )
                ).onFailure { error ->
                    logger.warn {
                        "Failed to insert project projection: projectId=${aggregate.id.value}, " +
                            "error=$error. Projection can be reconstructed from event store."
                    }
                }

                // Insert creator as member
                val creatorMember = aggregate.getMember(command.createdBy)
                if (creatorMember != null) {
                    projectionUpdater.insertMember(
                        NewProjectMemberProjection(
                            projectId = aggregate.id,
                            tenantId = command.tenantId,
                            userId = command.createdBy,
                            role = creatorMember.role,
                            assignedBy = creatorMember.assignedBy,
                            assignedAt = creatorMember.assignedAt
                        )
                    ).onFailure { error ->
                        logger.warn {
                            "Failed to insert member projection: projectId=${aggregate.id.value}, " +
                                "userId=${command.createdBy.value}, error=$error. " +
                                "Projection can be reconstructed from event store."
                        }
                    }
                }

                CreateProjectResult(projectId = aggregate.id).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    CreateProjectError.ConcurrencyConflict(
                        message = "Concurrent modification detected for aggregate ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }
}

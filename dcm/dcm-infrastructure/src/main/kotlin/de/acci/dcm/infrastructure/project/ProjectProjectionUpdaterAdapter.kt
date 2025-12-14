package de.acci.dcm.infrastructure.project

import de.acci.dcm.application.project.NewProjectMemberProjection
import de.acci.dcm.application.project.NewProjectProjection
import de.acci.dcm.application.project.ProjectProjectionUpdater
import de.acci.dcm.application.project.ProjectStatusUpdate
import de.acci.dcm.application.project.UpdateProjectMemberRole
import de.acci.dcm.application.project.UpdateProjectProjection
import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.ProjectMembers
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.Projects
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.ProjectionError
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

/**
 * Infrastructure adapter that implements ProjectProjectionUpdater
 * using the jOOQ-based projection repositories.
 *
 * This adapter translates application-layer projection operations
 * into infrastructure-layer database operations.
 *
 * ## Error Handling
 *
 * Projection updates return [Result] types to make errors explicit.
 * Errors are logged at the infrastructure layer, but callers decide
 * whether to propagate failures or allow the command to succeed.
 * Failed projections can be reconstructed from the event store.
 */
public class ProjectProjectionUpdaterAdapter(
    private val projectRepository: ProjectProjectionRepository,
    private val memberRepository: ProjectMemberProjectionRepository
) : ProjectProjectionUpdater {

    private val logger = KotlinLogging.logger {}

    override suspend fun insertProject(
        projection: NewProjectProjection
    ): Result<Unit, ProjectionError> {
        return try {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val pojo = Projects(
                id = projection.id.value,
                tenantId = projection.tenantId.value,
                name = projection.name.value,
                description = projection.description,
                status = projection.status.name,
                createdBy = projection.createdBy.value,
                createdAt = projection.createdAt.atOffset(ZoneOffset.UTC),
                updatedAt = now,
                version = projection.version
            )
            projectRepository.insert(pojo)
            logger.debug { "Inserted project projection: ${projection.id.value}" }
            Unit.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to insert project projection: ${projection.id.value}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = projection.id.value.toString(),
                message = "Failed to insert projection: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun updateProject(
        update: UpdateProjectProjection
    ): Result<Unit, ProjectionError> {
        return try {
            val rowsUpdated = projectRepository.update(
                id = update.id.value,
                name = update.name.value,
                description = update.description,
                version = update.version
            )
            if (rowsUpdated > 0) {
                logger.debug { "Updated project projection: ${update.id.value}" }
                Unit.success()
            } else {
                logger.warn {
                    "No projection found to update for project: ${update.id.value}. " +
                        "Projection may need to be reconstructed from event store."
                }
                ProjectionError.NotFound(
                    aggregateId = update.id.value.toString()
                ).failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to update project projection: ${update.id.value}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = update.id.value.toString(),
                message = "Failed to update projection: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun updateStatus(
        update: ProjectStatusUpdate
    ): Result<Unit, ProjectionError> {
        return try {
            val rowsUpdated = projectRepository.updateStatus(
                id = update.id.value,
                status = update.status.name,
                version = update.version
            )
            if (rowsUpdated > 0) {
                logger.debug {
                    "Updated project status: ${update.id.value} -> ${update.status}"
                }
                Unit.success()
            } else {
                logger.warn {
                    "No projection found to update status for project: ${update.id.value}."
                }
                ProjectionError.NotFound(
                    aggregateId = update.id.value.toString()
                ).failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to update project status: ${update.id.value}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = update.id.value.toString(),
                message = "Failed to update status: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun insertMember(
        projection: NewProjectMemberProjection
    ): Result<Unit, ProjectionError> {
        return try {
            val pojo = ProjectMembers(
                id = java.util.UUID.randomUUID(),
                projectId = projection.projectId.value,
                tenantId = projection.tenantId.value,
                userId = projection.userId.value,
                role = projection.role.name,
                assignedBy = projection.assignedBy.value,
                assignedAt = projection.assignedAt.atOffset(ZoneOffset.UTC),
                version = 1
            )
            memberRepository.insert(pojo)
            logger.debug {
                "Inserted project member: project=${projection.projectId.value}, " +
                    "user=${projection.userId.value}"
            }
            Unit.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to insert project member: project=${projection.projectId.value}, " +
                    "user=${projection.userId.value}. " +
                    "Projection can be reconstructed from event store."
            }
            ProjectionError.DatabaseError(
                aggregateId = projection.projectId.value.toString(),
                message = "Failed to insert member: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun updateMemberRole(
        update: UpdateProjectMemberRole
    ): Result<Unit, ProjectionError> {
        return try {
            val rowsUpdated = memberRepository.updateRole(
                projectId = update.projectId.value,
                userId = update.userId.value,
                role = update.role.name
            )
            if (rowsUpdated > 0) {
                logger.debug {
                    "Updated member role: project=${update.projectId.value}, " +
                        "user=${update.userId.value}, role=${update.role}"
                }
                Unit.success()
            } else {
                logger.warn {
                    "No member found to update role: project=${update.projectId.value}, " +
                        "user=${update.userId.value}."
                }
                ProjectionError.NotFound(
                    aggregateId = update.projectId.value.toString()
                ).failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to update member role: project=${update.projectId.value}, " +
                    "user=${update.userId.value}."
            }
            ProjectionError.DatabaseError(
                aggregateId = update.projectId.value.toString(),
                message = "Failed to update member role: ${e.message}",
                cause = e
            ).failure()
        }
    }

    override suspend fun removeMember(
        projectId: ProjectId,
        userId: UserId
    ): Result<Unit, ProjectionError> {
        return try {
            val rowsDeleted = memberRepository.remove(
                projectId = projectId.value,
                userId = userId.value
            )
            if (rowsDeleted > 0) {
                logger.debug {
                    "Removed project member: project=${projectId.value}, user=${userId.value}"
                }
                Unit.success()
            } else {
                logger.warn {
                    "No member found to remove: project=${projectId.value}, user=${userId.value}. " +
                        "Projection may be out of sync with event store."
                }
                // Return success anyway - the member doesn't exist, which is the desired end state
                // This handles idempotent removal scenarios
                Unit.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to remove project member: project=${projectId.value}, " +
                    "user=${userId.value}."
            }
            ProjectionError.DatabaseError(
                aggregateId = projectId.value.toString(),
                message = "Failed to remove member: ${e.message}",
                cause = e
            ).failure()
        }
    }
}

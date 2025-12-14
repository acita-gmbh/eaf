package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.dcm.domain.project.ProjectRole
import de.acci.dcm.domain.project.ProjectStatus
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.ProjectionError
import java.time.Instant

/**
 * Data for inserting a new project into the projection.
 */
public data class NewProjectProjection(
    val id: ProjectId,
    val tenantId: TenantId,
    val name: ProjectName,
    val description: String?,
    val status: ProjectStatus,
    val createdBy: UserId,
    val createdAt: Instant,
    val version: Int
)

/**
 * Data for updating a project in the projection.
 */
public data class UpdateProjectProjection(
    val id: ProjectId,
    val name: ProjectName,
    val description: String?,
    val version: Int
)

/**
 * Data for updating project status in the projection.
 */
public data class ProjectStatusUpdate(
    val id: ProjectId,
    val status: ProjectStatus,
    val version: Int
)

/**
 * Data for inserting a new project member into the projection.
 */
public data class NewProjectMemberProjection(
    val projectId: ProjectId,
    val tenantId: TenantId,
    val userId: UserId,
    val role: ProjectRole,
    val assignedBy: UserId,
    val assignedAt: Instant
)

/**
 * Data for updating a project member's role.
 */
public data class UpdateProjectMemberRole(
    val projectId: ProjectId,
    val userId: UserId,
    val role: ProjectRole
)

/**
 * Interface for updating Project projections (read models).
 *
 * Implemented in the infrastructure layer using jOOQ to update
 * the PROJECTS and PROJECT_MEMBERS tables.
 *
 * Updates are fire-and-forget - errors are logged but don't
 * fail the command. The projection can be rebuilt from events.
 */
public interface ProjectProjectionUpdater {

    /**
     * Insert a new project into the projection.
     */
    public suspend fun insertProject(
        projection: NewProjectProjection
    ): Result<Unit, ProjectionError>

    /**
     * Update project name and description.
     */
    public suspend fun updateProject(
        update: UpdateProjectProjection
    ): Result<Unit, ProjectionError>

    /**
     * Update project status (archive/unarchive).
     */
    public suspend fun updateStatus(
        update: ProjectStatusUpdate
    ): Result<Unit, ProjectionError>

    /**
     * Insert a new member into the project.
     */
    public suspend fun insertMember(
        projection: NewProjectMemberProjection
    ): Result<Unit, ProjectionError>

    /**
     * Update an existing member's role.
     */
    public suspend fun updateMemberRole(
        update: UpdateProjectMemberRole
    ): Result<Unit, ProjectionError>

    /**
     * Remove a member from the project.
     */
    public suspend fun removeMember(
        projectId: ProjectId,
        userId: UserId
    ): Result<Unit, ProjectionError>
}

/**
 * No-op implementation for testing or when projection is not needed.
 */
public object NoOpProjectProjectionUpdater : ProjectProjectionUpdater {
    override suspend fun insertProject(projection: NewProjectProjection): Result<Unit, ProjectionError> =
        Unit.success()

    override suspend fun updateProject(update: UpdateProjectProjection): Result<Unit, ProjectionError> =
        Unit.success()

    override suspend fun updateStatus(update: ProjectStatusUpdate): Result<Unit, ProjectionError> =
        Unit.success()

    override suspend fun insertMember(projection: NewProjectMemberProjection): Result<Unit, ProjectionError> =
        Unit.success()

    override suspend fun updateMemberRole(update: UpdateProjectMemberRole): Result<Unit, ProjectionError> =
        Unit.success()

    override suspend fun removeMember(projectId: ProjectId, userId: UserId): Result<Unit, ProjectionError> =
        Unit.success()
}

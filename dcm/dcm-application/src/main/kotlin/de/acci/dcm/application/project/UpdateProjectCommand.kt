package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectName
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to update an existing project's name and/or description.
 *
 * Only active (non-archived) projects can be updated.
 * Optimistic locking via version prevents concurrent modification conflicts.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = UpdateProjectCommand(
 *     tenantId = TenantId.fromString("..."),
 *     projectId = ProjectId.fromString("..."),
 *     updatedBy = UserId.fromString("..."),
 *     name = ProjectName.of("New Name"),
 *     description = "Updated description",
 *     version = 2L
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property projectId ID of the project to update
 * @property updatedBy User performing the update
 * @property name New project name
 * @property description New project description (null to remove)
 * @property version Expected aggregate version for optimistic locking
 */
public data class UpdateProjectCommand(
    val tenantId: TenantId,
    val projectId: ProjectId,
    val updatedBy: UserId,
    val name: ProjectName,
    val description: String?,
    val version: Long
)

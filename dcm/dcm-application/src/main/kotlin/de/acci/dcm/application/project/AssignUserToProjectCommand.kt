package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.dcm.domain.project.ProjectRole
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to assign a user to a project with a specific role.
 *
 * Users can be assigned as MEMBER or PROJECT_ADMIN.
 * If the user is already a member with a different role, the role is upgraded.
 * Assigning with the same role is idempotent (no new event).
 *
 * ## Usage
 *
 * ```kotlin
 * val command = AssignUserToProjectCommand(
 *     tenantId = TenantId.fromString("..."),
 *     projectId = ProjectId.fromString("..."),
 *     assignedBy = UserId.fromString("..."),
 *     userId = UserId.fromString("..."),
 *     role = ProjectRole.MEMBER,
 *     version = 2L
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property projectId ID of the project to assign user to
 * @property assignedBy Admin performing the assignment
 * @property userId User to be assigned
 * @property role Role to assign (MEMBER or PROJECT_ADMIN)
 * @property version Expected aggregate version for optimistic locking
 */
public data class AssignUserToProjectCommand(
    val tenantId: TenantId,
    val projectId: ProjectId,
    val assignedBy: UserId,
    val userId: UserId,
    val role: ProjectRole,
    val version: Long
)

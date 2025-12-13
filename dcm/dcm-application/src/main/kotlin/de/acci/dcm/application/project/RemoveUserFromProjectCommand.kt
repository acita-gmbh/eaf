package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to remove a user from a project.
 *
 * The project creator cannot be removed (creator protection invariant).
 * Removing a non-member is idempotent (no new event).
 *
 * ## Usage
 *
 * ```kotlin
 * val command = RemoveUserFromProjectCommand(
 *     tenantId = TenantId.fromString("..."),
 *     projectId = ProjectId.fromString("..."),
 *     removedBy = UserId.fromString("..."),
 *     userId = UserId.fromString("..."),
 *     version = 3L
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property projectId ID of the project
 * @property removedBy Admin performing the removal
 * @property userId User to be removed
 * @property version Expected aggregate version for optimistic locking
 */
public data class RemoveUserFromProjectCommand(
    val tenantId: TenantId,
    val projectId: ProjectId,
    val removedBy: UserId,
    val userId: UserId,
    val version: Long
)

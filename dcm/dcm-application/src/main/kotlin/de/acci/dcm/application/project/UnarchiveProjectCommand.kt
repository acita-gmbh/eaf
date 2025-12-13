package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to unarchive a previously archived project.
 *
 * Restores the project to ACTIVE status, allowing updates
 * and member management.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = UnarchiveProjectCommand(
 *     tenantId = TenantId.fromString("..."),
 *     projectId = ProjectId.fromString("..."),
 *     unarchivedBy = UserId.fromString("..."),
 *     version = 4L
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property projectId ID of the project to unarchive
 * @property unarchivedBy User performing the unarchive
 * @property version Expected aggregate version for optimistic locking
 */
public data class UnarchiveProjectCommand(
    val tenantId: TenantId,
    val projectId: ProjectId,
    val unarchivedBy: UserId,
    val version: Long
)

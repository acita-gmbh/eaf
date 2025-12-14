package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to archive a project (soft delete).
 *
 * Archived projects cannot be updated or have members assigned/removed.
 * Use [UnarchiveProjectCommand] to restore an archived project.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = ArchiveProjectCommand(
 *     tenantId = TenantId.fromString("..."),
 *     projectId = ProjectId.fromString("..."),
 *     archivedBy = UserId.fromString("..."),
 *     version = 3L
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property projectId ID of the project to archive
 * @property archivedBy User performing the archive
 * @property version Expected aggregate version for optimistic locking
 */
public data class ArchiveProjectCommand(
    val tenantId: TenantId,
    val projectId: ProjectId,
    val archivedBy: UserId,
    val version: Long
)

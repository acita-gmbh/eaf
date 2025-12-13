package de.acci.dcm.application.project

import de.acci.dcm.domain.project.ProjectName
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to create a new project.
 *
 * Projects organize VMs and control access via membership.
 * The creator is automatically assigned as PROJECT_ADMIN.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = CreateProjectCommand(
 *     tenantId = TenantId.fromString("..."),
 *     createdBy = UserId.fromString("..."),
 *     name = ProjectName.of("My Project"),
 *     description = "Optional project description"
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property createdBy User creating the project (becomes PROJECT_ADMIN)
 * @property name Validated project name (3-100 chars, alphanumeric start)
 * @property description Optional project description
 */
public data class CreateProjectCommand(
    val tenantId: TenantId,
    val createdBy: UserId,
    val name: ProjectName,
    val description: String?
)

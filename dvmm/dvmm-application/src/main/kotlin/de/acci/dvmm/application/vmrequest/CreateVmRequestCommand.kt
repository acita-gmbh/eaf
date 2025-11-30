package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to create a new VM request.
 *
 * This command initiates the VM request workflow by creating
 * a new VmRequestAggregate with PENDING status.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = CreateVmRequestCommand(
 *     tenantId = TenantId.fromString("..."),
 *     requesterId = UserId.fromString("..."),
 *     projectId = ProjectId.fromString("..."),
 *     vmName = VmName.of("web-server-01"),
 *     size = VmSize.M,
 *     justification = "New web application server"
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property requesterId User submitting the request
 * @property projectId Project this VM belongs to
 * @property vmName Validated VM name (lowercase, alphanumeric + hyphens)
 * @property size Selected VM size (S, M, L, XL)
 * @property justification Business justification (minimum 10 characters)
 */
public data class CreateVmRequestCommand(
    val tenantId: TenantId,
    val requesterId: UserId,
    val projectId: ProjectId,
    val vmName: VmName,
    val size: VmSize,
    val justification: String
)

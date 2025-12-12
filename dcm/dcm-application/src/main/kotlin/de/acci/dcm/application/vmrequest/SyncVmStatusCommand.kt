package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to synchronize VM status from the hypervisor.
 *
 * Story 3-7: Allows users to refresh the current VM status (power state, IP, etc.)
 * by querying the vSphere API and updating the projection.
 *
 * ## Usage
 *
 * ```kotlin
 * val command = SyncVmStatusCommand(
 *     tenantId = TenantId.fromString("..."),
 *     requestId = VmRequestId.fromString("..."),
 *     userId = UserId.fromString("...")
 * )
 * val result = handler.handle(command)
 * ```
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property requestId The VM request to sync status for
 * @property userId User requesting the sync (for authorization)
 */
public data class SyncVmStatusCommand(
    val tenantId: TenantId,
    val requestId: VmRequestId,
    val userId: UserId
)

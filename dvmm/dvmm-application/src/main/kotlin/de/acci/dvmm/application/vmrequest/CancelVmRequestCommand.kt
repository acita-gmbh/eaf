package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to cancel a pending VM request.
 *
 * Only the original requester can cancel their own request,
 * and only while it is still in PENDING status.
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property requestId ID of the VM request to cancel
 * @property userId User attempting the cancellation (must be original requester)
 * @property reason Optional reason for cancellation
 */
public data class CancelVmRequestCommand(
    val tenantId: TenantId,
    val requestId: VmRequestId,
    val userId: UserId,
    val reason: String? = null
)

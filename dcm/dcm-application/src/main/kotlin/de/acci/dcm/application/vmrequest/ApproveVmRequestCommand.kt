package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to approve a pending VM request (admin action).
 *
 * Admins can approve requests that are in PENDING status.
 * Separation of duties: admin cannot approve their own request.
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property requestId ID of the VM request to approve
 * @property adminId Admin performing the approval
 * @property version Expected aggregate version for optimistic locking
 */
public data class ApproveVmRequestCommand(
    val tenantId: TenantId,
    val requestId: VmRequestId,
    val adminId: UserId,
    val version: Long
)

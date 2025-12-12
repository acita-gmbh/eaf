package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to reject a pending VM request with a mandatory reason (admin action).
 *
 * Admins can reject requests that are in PENDING status.
 * Separation of duties: admin cannot reject their own request.
 *
 * @property tenantId Tenant context for multi-tenancy isolation
 * @property requestId ID of the VM request to reject
 * @property adminId Admin performing the rejection
 * @property reason Mandatory rejection reason (10-500 characters)
 * @property version Expected aggregate version for optimistic locking
 */
public data class RejectVmRequestCommand(
    val tenantId: TenantId,
    val requestId: VmRequestId,
    val adminId: UserId,
    val reason: String,
    val version: Long
)

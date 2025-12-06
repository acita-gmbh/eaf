package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to mark a VM request as provisioning.
 */
public data class MarkVmRequestProvisioningCommand(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val userId: UserId
)

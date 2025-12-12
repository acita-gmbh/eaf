package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
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

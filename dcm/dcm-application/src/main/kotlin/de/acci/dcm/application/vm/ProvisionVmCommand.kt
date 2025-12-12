package de.acci.dcm.application.vm

import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Command to start provisioning a VM.
 */
public data class ProvisionVmCommand(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val projectId: ProjectId,
    val vmName: VmName,
    val size: VmSize,
    val requesterId: UserId
)

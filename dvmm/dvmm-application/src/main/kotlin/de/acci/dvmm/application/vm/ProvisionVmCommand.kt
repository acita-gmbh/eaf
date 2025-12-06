package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
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

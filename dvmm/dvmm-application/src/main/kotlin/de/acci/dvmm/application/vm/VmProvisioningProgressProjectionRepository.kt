package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId

public interface VmProvisioningProgressProjectionRepository {
    public suspend fun save(projection: VmProvisioningProgressProjection, tenantId: TenantId)
    public suspend fun delete(vmRequestId: VmRequestId, tenantId: TenantId)
    public suspend fun findByVmRequestId(vmRequestId: VmRequestId, tenantId: TenantId): VmProvisioningProgressProjection?
}

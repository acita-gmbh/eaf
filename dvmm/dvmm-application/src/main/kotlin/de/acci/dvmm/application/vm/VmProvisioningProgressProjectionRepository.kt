package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.TenantId

/**
 * Repository for managing [VmProvisioningProgressProjection]s.
 *
 * Stores transient progress data for running provisioning workflows.
 */
public interface VmProvisioningProgressProjectionRepository {
    /** Saves or updates the projection. */
    public suspend fun save(projection: VmProvisioningProgressProjection, tenantId: TenantId)
    /** Deletes the projection (when provisioning completes or fails). */
    public suspend fun delete(vmRequestId: VmRequestId, tenantId: TenantId)
    /** Finds projection by request ID. */
    public suspend fun findByVmRequestId(vmRequestId: VmRequestId, tenantId: TenantId): VmProvisioningProgressProjection?
}

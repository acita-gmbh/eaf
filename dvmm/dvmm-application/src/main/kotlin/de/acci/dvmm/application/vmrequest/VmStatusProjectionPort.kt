package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Port for updating VM status in the read-side projection.
 *
 * Story 3-7: Provides an abstraction for updating VM runtime details
 * in the projection without coupling to infrastructure implementation.
 *
 * ## Hexagonal Architecture Note
 *
 * This is an outbound port (driven port) that defines how the application
 * layer interacts with the projection storage. The adapter implementation
 * is in the infrastructure layer.
 */
public interface VmStatusProjectionPort {

    /**
     * Update VM runtime details for a request.
     *
     * @param requestId The VM request to update
     * @param vmwareVmId VMware MoRef ID (e.g., "vm-123")
     * @param ipAddress Primary IP address from VMware Tools
     * @param hostname Guest hostname from VMware Tools
     * @param powerState Power state: POWERED_ON, POWERED_OFF, SUSPENDED
     * @param guestOs Detected guest OS type
     * @param lastSyncedAt Timestamp of this sync
     * @return Number of rows updated (0 if request not found)
     */
    public suspend fun updateVmDetails(
        requestId: VmRequestId,
        vmwareVmId: String?,
        ipAddress: String?,
        hostname: String?,
        powerState: String?,
        guestOs: String?,
        lastSyncedAt: Instant
    ): Int

    /**
     * Get the VMware VM ID for a request (needed to query vSphere).
     *
     * @param requestId The VM request to look up
     * @return The VMware VM ID if the VM has been provisioned, null otherwise
     */
    public suspend fun getVmwareVmId(requestId: VmRequestId): String?

    /**
     * Get the requester ID for a request (for ownership verification).
     *
     * @param requestId The VM request to look up
     * @return The requester's user ID if the request exists, null otherwise
     */
    public suspend fun getRequesterId(requestId: VmRequestId): UserId?
}

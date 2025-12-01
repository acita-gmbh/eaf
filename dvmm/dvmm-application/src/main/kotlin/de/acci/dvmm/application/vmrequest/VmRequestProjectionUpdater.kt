package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Port interface for updating VM request projections.
 *
 * This is an application-layer port that abstracts away the infrastructure
 * details of how projections are stored. Implementations in the infrastructure
 * layer handle the actual database operations.
 *
 * ## Usage
 *
 * Command handlers inject this interface and call appropriate methods
 * after successfully persisting domain events to the event store.
 *
 * ## Error Handling
 *
 * Projection updates should be resilient to failures - if an update fails,
 * it can be retried or reconstructed from the event store. Implementations
 * should log errors but may not need to propagate them to callers.
 */
public interface VmRequestProjectionUpdater {

    /**
     * Insert a new VM request projection.
     *
     * Called after a VmRequestCreated event is persisted.
     *
     * @param data The projection data to insert
     */
    public suspend fun insert(data: NewVmRequestProjection)

    /**
     * Update status of an existing VM request projection.
     *
     * Called after status-changing events (Cancelled, Approved, Rejected, etc.)
     * are persisted.
     *
     * @param data The projection update data
     */
    public suspend fun updateStatus(data: VmRequestStatusUpdate)
}

/**
 * Data for inserting a new VM request projection.
 */
public data class NewVmRequestProjection(
    val id: VmRequestId,
    val tenantId: TenantId,
    val requesterId: UserId,
    val requesterName: String,
    val projectId: ProjectId,
    val projectName: String,
    val vmName: VmName,
    val size: VmSize,
    val justification: String,
    val status: VmRequestStatus,
    val createdAt: Instant,
    val version: Int
)

/**
 * Data for updating VM request status.
 */
public data class VmRequestStatusUpdate(
    val id: VmRequestId,
    val status: VmRequestStatus,
    val version: Int,
    val approvedBy: UserId? = null,
    val approvedByName: String? = null,
    val rejectedBy: UserId? = null,
    val rejectedByName: String? = null,
    val rejectionReason: String? = null
)

/**
 * No-op implementation that does nothing.
 *
 * Used for testing handlers in isolation without projection side effects,
 * or as a placeholder during development.
 */
public object NoOpVmRequestProjectionUpdater : VmRequestProjectionUpdater {
    override suspend fun insert(data: NewVmRequestProjection) {
        // No-op
    }

    override suspend fun updateStatus(data: VmRequestStatusUpdate) {
        // No-op
    }
}

package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.ProjectionError
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
 * Projection updates return [Result] types to make errors explicit.
 * Callers should log errors for monitoring but typically allow the
 * command to succeed - failed projections can be reconstructed from
 * the event store.
 */
public interface VmRequestProjectionUpdater {

    /**
     * Insert a new VM request projection.
     *
     * Called after a VmRequestCreated event is persisted.
     *
     * @param data The projection data to insert
     * @return Success with Unit, or Failure with [ProjectionError]
     */
    public suspend fun insert(data: NewVmRequestProjection): Result<Unit, ProjectionError>

    /**
     * Update status of an existing VM request projection.
     *
     * Called after status-changing events (Cancelled, Approved, Rejected, etc.)
     * are persisted.
     *
     * @param data The projection update data
     * @return Success with Unit, or Failure with [ProjectionError]
     */
    public suspend fun updateStatus(data: VmRequestStatusUpdate): Result<Unit, ProjectionError>
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
    override suspend fun insert(data: NewVmRequestProjection): Result<Unit, ProjectionError> {
        return Result.Success(Unit)
    }

    override suspend fun updateStatus(data: VmRequestStatusUpdate): Result<Unit, ProjectionError> {
        return Result.Success(Unit)
    }
}

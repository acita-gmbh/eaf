package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.ProjectionError
import java.time.Instant
import java.util.UUID

/**
 * Port interface for updating timeline event projections.
 *
 * This is an application-layer port that abstracts away the infrastructure
 * details of how timeline events are stored. Implementations in the infrastructure
 * layer handle the actual database operations.
 *
 * ## Event Types
 *
 * - CREATED: VM request was created
 * - APPROVED: VM request was approved by admin
 * - REJECTED: VM request was rejected by admin
 * - CANCELLED: VM request was cancelled by requester
 * - PROVISIONING_STARTED: VM provisioning has begun (Epic 3)
 * - VM_READY: VM is ready for use (Epic 3)
 *
 * ## Usage
 *
 * Command handlers inject this interface and call [addTimelineEvent] after
 * successfully persisting domain events to the event store.
 */
public interface TimelineEventProjectionUpdater {

    /**
     * Add a new timeline event for a VM request.
     *
     * Uses INSERT ON CONFLICT DO NOTHING for idempotency during event replay.
     *
     * @param data The timeline event data to insert
     * @return Success with Unit, or Failure with [ProjectionError]
     */
    public suspend fun addTimelineEvent(data: NewTimelineEvent): Result<Unit, ProjectionError>
}

/**
 * Data for inserting a new timeline event.
 */
public data class NewTimelineEvent(
    /** Unique identifier for this timeline event (typically derived from domain event ID) */
    val id: UUID,
    /** The VM request this event belongs to */
    val requestId: VmRequestId,
    /** Tenant ID for multi-tenancy */
    val tenantId: TenantId,
    /** Event type (CREATED, APPROVED, REJECTED, CANCELLED, etc.) */
    val eventType: TimelineEventType,
    /** User who performed the action (nullable for system events) */
    val actorId: UserId?,
    /** Display name of the actor (resolved at projection time) */
    val actorName: String?,
    /** Additional event details as JSON string (e.g., rejection reason) */
    val details: String?,
    /** When the event occurred */
    val occurredAt: Instant
)

/**
 * Types of timeline events.
 */
public enum class TimelineEventType {
    CREATED,
    APPROVED,
    REJECTED,
    CANCELLED,
    PROVISIONING_STARTED,
    PROVISIONING_QUEUED,
    PROVISIONING_FAILED,
    VM_READY
}

/**
 * No-op implementation that does nothing.
 *
 * Used for testing handlers in isolation without projection side effects,
 * or as a placeholder during development.
 */
public object NoOpTimelineEventProjectionUpdater : TimelineEventProjectionUpdater {
    override suspend fun addTimelineEvent(data: NewTimelineEvent): Result<Unit, ProjectionError> {
        return Result.Success(Unit)
    }
}

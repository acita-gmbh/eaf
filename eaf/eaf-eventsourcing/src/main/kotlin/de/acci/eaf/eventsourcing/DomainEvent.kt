package de.acci.eaf.eventsourcing

import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Marker interface for domain events.
 *
 * Domain events represent something that happened in the domain
 * and are the source of truth in an event-sourced system.
 */
public interface DomainEvent {
    /**
     * The type of aggregate this event belongs to.
     * Used for event store partitioning and replay filtering.
     */
    public val aggregateType: String

    /**
     * Metadata associated with this event.
     * Contains context information like tenant, user, and correlation IDs.
     */
    public val metadata: EventMetadata
}

/**
 * Metadata attached to every domain event.
 *
 * Provides audit trail and multi-tenancy support for event sourcing.
 *
 * @property tenantId The tenant this event belongs to (for RLS enforcement)
 * @property userId The user who triggered the action that produced this event
 * @property correlationId Unique ID for tracing related operations across services
 * @property timestamp When the event occurred (should be set at creation time)
 */
public data class EventMetadata(
    public val tenantId: TenantId,
    public val userId: UserId,
    public val correlationId: CorrelationId,
    public val timestamp: Instant
) {
    public companion object {
        /**
         * Create metadata with current timestamp.
         */
        public fun create(
            tenantId: TenantId,
            userId: UserId,
            correlationId: CorrelationId = CorrelationId.generate()
        ): EventMetadata = EventMetadata(
            tenantId = tenantId,
            userId = userId,
            correlationId = correlationId,
            timestamp = Instant.now()
        )
    }
}

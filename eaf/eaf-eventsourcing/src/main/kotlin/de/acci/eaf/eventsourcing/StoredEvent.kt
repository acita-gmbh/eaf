package de.acci.eaf.eventsourcing

import java.time.Instant
import java.util.UUID

/**
 * A domain event as persisted in the event store.
 *
 * Contains both the serialized event payload and all metadata
 * required for event replay and audit.
 *
 * @property id Unique identifier of this stored event record
 * @property aggregateId The aggregate instance this event belongs to
 * @property aggregateType Type name of the aggregate (e.g., "VmRequest")
 * @property eventType Type name of the event (e.g., "VmRequestCreated")
 * @property payload JSON-serialized event data
 * @property metadata Event metadata (tenant, user, correlation, timestamp)
 * @property version Aggregate version after this event (starts at 1)
 * @property createdAt When this event was persisted to the store
 */
public data class StoredEvent(
    public val id: UUID,
    public val aggregateId: UUID,
    public val aggregateType: String,
    public val eventType: String,
    public val payload: String,
    public val metadata: EventMetadata,
    public val version: Long,
    public val createdAt: Instant
)

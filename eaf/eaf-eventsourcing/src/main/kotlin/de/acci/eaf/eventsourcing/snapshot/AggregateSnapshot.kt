package de.acci.eaf.eventsourcing.snapshot

import java.time.Instant
import java.util.UUID

/**
 * A snapshot of an aggregate's state at a specific version.
 *
 * Snapshots are used to optimize aggregate reconstitution by avoiding
 * the need to replay all events from the beginning. Instead, the aggregate
 * can be restored from the snapshot and only events after the snapshot
 * version need to be replayed.
 *
 * @property aggregateId The unique identifier of the aggregate
 * @property aggregateType The type name of the aggregate (e.g., "VmRequest")
 * @property version The aggregate version when this snapshot was taken
 * @property state The serialized aggregate state (JSON format)
 * @property tenantId The tenant this aggregate belongs to (for RLS enforcement)
 * @property createdAt When this snapshot was created
 */
public data class AggregateSnapshot(
    public val aggregateId: UUID,
    public val aggregateType: String,
    public val version: Long,
    public val state: String,
    public val tenantId: UUID,
    public val createdAt: Instant
)

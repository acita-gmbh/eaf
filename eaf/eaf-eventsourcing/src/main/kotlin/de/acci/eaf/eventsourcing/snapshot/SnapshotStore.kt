package de.acci.eaf.eventsourcing.snapshot

import java.util.UUID

/**
 * Interface for storing and retrieving aggregate snapshots.
 *
 * Snapshots provide a performance optimization for aggregates with many events.
 * Instead of replaying all events, an aggregate can be restored from a snapshot
 * and only events after the snapshot version need to be replayed.
 *
 * ## Usage
 *
 * ```kotlin
 * // Save a snapshot after many events
 * if (aggregate.version >= AggregateRoot.DEFAULT_SNAPSHOT_THRESHOLD) {
 *     val snapshot = AggregateSnapshot(
 *         aggregateId = aggregate.id,
 *         aggregateType = "VmRequest",
 *         version = aggregate.version,
 *         state = objectMapper.writeValueAsString(aggregate.toSnapshot()),
 *         tenantId = tenantId,
 *         createdAt = Instant.now()
 *     )
 *     snapshotStore.save(snapshot)
 * }
 *
 * // Load aggregate with snapshot optimization
 * val snapshot = snapshotStore.load(aggregateId)
 * val aggregate = if (snapshot != null) {
 *     val restored = VmRequest.fromSnapshot(snapshot)
 *     val remainingEvents = eventStore.loadFrom(aggregateId, snapshot.version + 1)
 *     remainingEvents.forEach { restored.replay(it) }
 *     restored
 * } else {
 *     val events = eventStore.load(aggregateId)
 *     VmRequest.reconstitute(aggregateId, events)
 * }
 * ```
 *
 * Implementations must ensure tenant isolation via RLS or explicit filtering.
 */
public interface SnapshotStore {

    /**
     * Save or update a snapshot for an aggregate.
     *
     * If a snapshot already exists for the same aggregate (same tenant_id and aggregate_id),
     * it will be replaced (upsert behavior).
     *
     * @param snapshot The snapshot to save
     */
    public suspend fun save(snapshot: AggregateSnapshot)

    /**
     * Load the latest snapshot for an aggregate.
     *
     * @param aggregateId The aggregate instance ID
     * @return The snapshot if one exists, null otherwise
     */
    public suspend fun load(aggregateId: UUID): AggregateSnapshot?
}

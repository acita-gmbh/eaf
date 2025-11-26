package de.acci.eaf.eventsourcing

import de.acci.eaf.core.result.Result
import java.util.UUID

/**
 * Event store interface for persisting and loading domain events.
 *
 * This interface is framework-agnostic (no Spring annotations) and
 * uses suspending functions for non-blocking I/O.
 *
 * Implementations must ensure:
 * - Events are persisted atomically
 * - Optimistic locking via version constraint
 * - Tenant isolation via RLS or explicit filtering
 */
public interface EventStore {
    /**
     * Append events to the event store for a specific aggregate.
     *
     * Uses optimistic locking: the operation fails if expectedVersion
     * does not match the current aggregate version.
     *
     * @param aggregateId The aggregate instance ID
     * @param events List of domain events to append (ordered)
     * @param expectedVersion The expected current version (0 for new aggregates)
     * @return Result containing the new version on success, or ConcurrencyConflict on failure
     */
    public suspend fun append(
        aggregateId: UUID,
        events: List<DomainEvent>,
        expectedVersion: Long
    ): Result<Long, EventStoreError>

    /**
     * Load all events for an aggregate in version order.
     *
     * @param aggregateId The aggregate instance ID
     * @return List of stored events ordered by version ASC, empty if aggregate not found
     */
    public suspend fun load(aggregateId: UUID): List<StoredEvent>

    /**
     * Load events for an aggregate starting from a specific version.
     *
     * Useful for replaying events after loading from a snapshot.
     *
     * @param aggregateId The aggregate instance ID
     * @param fromVersion The version to start loading from (inclusive)
     * @return List of stored events with version >= fromVersion, ordered by version ASC
     */
    public suspend fun loadFrom(aggregateId: UUID, fromVersion: Long): List<StoredEvent>
}

/**
 * Errors that can occur during event store operations.
 */
public sealed class EventStoreError {
    /**
     * Optimistic locking conflict: another process modified the aggregate.
     *
     * @property aggregateId The aggregate that had the conflict
     * @property expectedVersion The version the caller expected
     * @property actualVersion The actual current version in the store
     */
    public data class ConcurrencyConflict(
        public val aggregateId: UUID,
        public val expectedVersion: Long,
        public val actualVersion: Long
    ) : EventStoreError()
}

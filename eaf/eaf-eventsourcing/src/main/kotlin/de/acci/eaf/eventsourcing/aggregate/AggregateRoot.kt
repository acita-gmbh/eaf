package de.acci.eaf.eventsourcing.aggregate

import de.acci.eaf.eventsourcing.DomainEvent

/**
 * Base class for event-sourced aggregate roots.
 *
 * An aggregate root is the entry point to an aggregate - a cluster of domain objects
 * that are treated as a single unit for data changes. All events affecting the aggregate
 * are emitted through the root.
 *
 * ## Usage
 *
 * Extend this class and implement the [handleEvent] method to mutate state based on events:
 *
 * ```kotlin
 * class VmRequest private constructor(override val id: UUID) : AggregateRoot<UUID>() {
 *     var status: Status = Status.PENDING
 *         private set
 *
 *     fun approve(metadata: EventMetadata) {
 *         applyEvent(VmRequestApproved(metadata))
 *     }
 *
 *     override fun handleEvent(event: DomainEvent) {
 *         when (event) {
 *             is VmRequestApproved -> status = Status.APPROVED
 *         }
 *     }
 * }
 * ```
 *
 * @param TId The type of the aggregate identifier (typically UUID)
 */
public abstract class AggregateRoot<TId : Any> {

    /**
     * The unique identifier for this aggregate instance.
     */
    public abstract val id: TId

    /**
     * The current version of this aggregate.
     *
     * - New aggregates start at version 0 (no events persisted yet)
     * - Each applied event increments the version by 1
     * - After reconstitution, version equals the number of events replayed
     *
     * Used for optimistic concurrency control in [de.acci.eaf.eventsourcing.EventStore.append].
     */
    public var version: Long = 0
        protected set

    /**
     * Internal mutable list of events applied since last commit.
     */
    private val _uncommittedEvents: MutableList<DomainEvent> = mutableListOf()

    /**
     * Events that have been applied but not yet persisted to the event store.
     *
     * Returns an immutable copy of the uncommitted events list.
     * Call [clearUncommittedEvents] after successful persistence.
     */
    public val uncommittedEvents: List<DomainEvent>
        get() = _uncommittedEvents.toList()

    /**
     * Apply an event to this aggregate, updating its state.
     *
     * @param event The domain event to apply
     * @param isReplay If true, the event is being replayed during reconstitution
     *                 and should not be added to uncommittedEvents
     */
    protected fun applyEvent(event: DomainEvent, isReplay: Boolean = false) {
        handleEvent(event)
        version++
        if (!isReplay) {
            _uncommittedEvents.add(event)
        }
    }

    /**
     * Handle a domain event by mutating the aggregate's internal state.
     *
     * This method is called for both new events and replayed events during reconstitution.
     * Implementations should update internal state based on the event type.
     *
     * **Important:** This method must be deterministic and side-effect free.
     * It should only modify the aggregate's internal state, never perform I/O.
     *
     * @param event The domain event to handle
     */
    protected abstract fun handleEvent(event: DomainEvent)

    /**
     * Clear the list of uncommitted events after successful persistence.
     *
     * Call this method after the events have been successfully appended to the event store.
     */
    public fun clearUncommittedEvents() {
        _uncommittedEvents.clear()
    }

    public companion object {
        /**
         * Default threshold for creating snapshots.
         *
         * When an aggregate has more than this many events, consider creating
         * a snapshot to improve reconstitution performance.
         */
        public const val DEFAULT_SNAPSHOT_THRESHOLD: Int = 100
    }
}

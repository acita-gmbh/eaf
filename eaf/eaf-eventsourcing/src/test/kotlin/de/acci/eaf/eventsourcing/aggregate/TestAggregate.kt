package de.acci.eaf.eventsourcing.aggregate

import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import java.util.UUID

/**
 * Test aggregate for demonstrating and testing the AggregateRoot pattern.
 *
 * This aggregate manages a simple entity with a name and value.
 */
internal class TestAggregate private constructor(
    override val id: UUID
) : AggregateRoot<UUID>() {

    var name: String = ""
        private set

    var value: Int = 0
        private set

    /**
     * Create a new TestAggregate with the given name and initial value.
     */
    fun create(name: String, value: Int, metadata: EventMetadata) {
        applyEvent(TestAggregateEvent.Created(name, value, metadata))
    }

    /**
     * Update the value of this aggregate.
     */
    fun updateValue(newValue: Int, metadata: EventMetadata) {
        applyEvent(TestAggregateEvent.Updated(newValue, metadata))
    }

    override fun handleEvent(event: DomainEvent) {
        when (event) {
            is TestAggregateEvent.Created -> {
                name = event.name
                value = event.value
            }
            is TestAggregateEvent.Updated -> {
                value = event.newValue
            }
            else -> error("Unknown event type: ${event::class.simpleName}")
        }
    }

    companion object {
        /**
         * Factory method to create a new aggregate.
         */
        fun new(id: UUID): TestAggregate = TestAggregate(id)

        /**
         * Reconstitute an aggregate from its event history.
         */
        fun reconstitute(id: UUID, events: List<DomainEvent>): TestAggregate {
            val aggregate = TestAggregate(id)
            events.forEach { event ->
                aggregate.applyEvent(event, isReplay = true)
            }
            return aggregate
        }
    }
}

/**
 * Sealed class hierarchy for TestAggregate events.
 */
internal sealed class TestAggregateEvent : DomainEvent {
    override val aggregateType: String = "TestAggregate"

    /**
     * Event emitted when a TestAggregate is created.
     */
    data class Created(
        val name: String,
        val value: Int,
        override val metadata: EventMetadata
    ) : TestAggregateEvent()

    /**
     * Event emitted when a TestAggregate's value is updated.
     */
    data class Updated(
        val newValue: Int,
        override val metadata: EventMetadata
    ) : TestAggregateEvent()
}

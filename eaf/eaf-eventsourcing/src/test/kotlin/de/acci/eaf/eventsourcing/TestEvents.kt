package de.acci.eaf.eventsourcing

/**
 * Test domain events for use in event store tests.
 */
internal data class TestEventCreated(
    override val aggregateType: String = "TestAggregate",
    override val metadata: EventMetadata,
    val name: String,
    val value: Int
) : DomainEvent

internal data class TestEventUpdated(
    override val aggregateType: String = "TestAggregate",
    override val metadata: EventMetadata,
    val newValue: Int
) : DomainEvent

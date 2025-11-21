package com.axians.eaf.framework.core.domain

import com.axians.eaf.framework.core.common.types.Identifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for AggregateRoot base class - the foundation of Domain-Driven Design in EAF.
 *
 * Validates core DDD aggregate functionality including event sourcing patterns, immutability
 * guarantees, and proper event lifecycle management.
 *
 * **Test Coverage:**
 * - Event registration via protected methods
 * - Event retrieval (immutable copy to prevent external modification)
 * - Event clearing after persistence
 * - Multiple event handling and ordering
 * - Event sequence preservation (FIFO order)
 *
 * **DDD Patterns Validated:**
 * - Aggregate Root as consistency boundary
 * - Domain events as first-class citizens
 * - Event sourcing readiness (event collection)
 * - Immutability of event history
 *
 * @see AggregateRoot Primary class under test
 * @see DomainEvent Event base class
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class AggregateRootTest {
    // Test aggregate, identifier, and event implementations
    data class TestId(
        override val value: String,
    ) : Identifier

    data class TestEvent(
        override val occurredAt: Instant = Instant.now(),
        override val eventId: UUID = UUID.randomUUID(),
        val message: String,
    ) : DomainEvent

    data class AnotherEvent(
        override val occurredAt: Instant = Instant.now(),
        override val eventId: UUID = UUID.randomUUID(),
        val value: Int,
    ) : DomainEvent

    class TestAggregate(
        override val id: TestId,
        var name: String,
    ) : AggregateRoot<TestId>(id) {
        fun performAction(message: String) {
            registerEvent(TestEvent(message = message))
        }

        fun performMultipleActions(messages: List<String>) {
            messages.forEach { message ->
                registerEvent(TestEvent(message = message))
            }
        }

        fun performMixedActions() {
            registerEvent(TestEvent(message = "first"))
            registerEvent(AnotherEvent(value = 42))
            registerEvent(TestEvent(message = "second"))
        }
    }

    @Test
    fun `should register single domain event`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performAction("Event 1")

        val events = aggregate.getEvents()
        assertThat(events).hasSize(1)
        assertThat((events[0] as TestEvent).message).isEqualTo("Event 1")
    }

    @Test
    fun `should register multiple domain events in order`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performMultipleActions(listOf("First", "Second", "Third"))

        val events = aggregate.getEvents()
        assertThat(events).hasSize(3)
        assertThat((events[0] as TestEvent).message).isEqualTo("First")
        assertThat((events[1] as TestEvent).message).isEqualTo("Second")
        assertThat((events[2] as TestEvent).message).isEqualTo("Third")
    }

    @Test
    fun `should clear all registered events`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performMultipleActions(listOf("Event 1", "Event 2"))

        assertThat(aggregate.getEvents()).hasSize(2)

        aggregate.clearEvents()
        assertThat(aggregate.getEvents()).isEmpty()
    }

    @Test
    fun `should return immutable copy of events (modifications don't affect original)`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performAction("Event 1")

        val events1 = aggregate.getEvents()
        val events2 = aggregate.getEvents()

        // Both should contain the same event
        assertThat(events1).hasSize(1)
        assertThat(events2).hasSize(1)

        // Add another event
        aggregate.performAction("Event 2")

        // Original retrieved lists unchanged (immutable copy)
        assertThat(events1).hasSize(1)
        assertThat(events2).hasSize(1)

        // New retrieval reflects updated state
        assertThat(aggregate.getEvents()).hasSize(2)
    }

    @Test
    fun `should handle empty event list`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")

        assertThat(aggregate.getEvents()).isEmpty()
    }

    @Test
    fun `should support different event types`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performMixedActions()

        val events = aggregate.getEvents()
        assertThat(events).hasSize(3)
        assertThat(events.filterIsInstance<TestEvent>()).hasSize(2)
        assertThat(events.filterIsInstance<AnotherEvent>()).hasSize(1)
    }

    @Test
    fun `should preserve event order across mixed types`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performMixedActions()

        val events = aggregate.getEvents()
        assertThat((events[0] as TestEvent).message).isEqualTo("first")
        assertThat((events[1] as AnotherEvent).value).isEqualTo(42)
        assertThat((events[2] as TestEvent).message).isEqualTo("second")
    }

    @Test
    fun `should allow event registration after clearing`() {
        val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
        aggregate.performAction("Event 1")
        aggregate.clearEvents()
        aggregate.performAction("Event 2")

        val events = aggregate.getEvents()
        assertThat(events).hasSize(1)
        assertThat((events[0] as TestEvent).message).isEqualTo("Event 2")
    }

    @Test
    fun `should inherit entity identity-based equality`() {
        val aggregate1 = TestAggregate(id = TestId("agg-123"), name = "First")
        val aggregate2 = TestAggregate(id = TestId("agg-123"), name = "Second")

        // Same ID = equal (inherits Entity behavior)
        assertThat(aggregate1).isEqualTo(aggregate2)
    }
}

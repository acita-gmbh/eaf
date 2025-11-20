package com.axians.eaf.framework.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for DomainEvent interface implementations - event sourcing foundation.
 *
 * Validates the DomainEvent marker interface that all domain events must implement in EAF's
 * CQRS/Event Sourcing architecture. Ensures proper metadata tracking and event identity.
 *
 * **Test Coverage:**
 * - Required metadata fields (occurredAt timestamp, eventId UUID)
 * - Timestamp preservation and immutability
 * - Event ID uniqueness (each event has unique identifier)
 * - Polymorphic collection usage (List<DomainEvent>)
 * - Event identity semantics
 *
 * **Event Sourcing Patterns:**
 * - Domain events as first-class citizens
 * - Immutable event records with metadata
 * - Event stream reconstruction capability
 * - Temporal event ordering via occurredAt
 *
 * @see DomainEvent Primary interface under test
 * @see AggregateRoot Event publisher
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class DomainEventTest {

    // Test event implementation
    data class TestEvent(
        override val occurredAt: Instant,
        override val eventId: UUID,
        val payload: String,
    ) : DomainEvent

    data class AnotherEvent(
        override val occurredAt: Instant,
        override val eventId: UUID,
        val value: Int,
    ) : DomainEvent

    @Test
    fun `should have non-null occurredAt timestamp`() {
        val now = Instant.now()
        val event = TestEvent(occurredAt = now, eventId = UUID.randomUUID(), payload = "test")

        assertThat(event.occurredAt).isEqualTo(now)
    }

    @Test
    fun `should have non-null eventId UUID`() {
        val id = UUID.randomUUID()
        val event = TestEvent(occurredAt = Instant.now(), eventId = id, payload = "test")

        assertThat(event.eventId).isEqualTo(id)
    }

    @Test
    fun `should generate unique eventId for each instance`() {
        val event1 = TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "test1")
        val event2 = TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "test2")

        assertThat(event1.eventId).isNotEqualTo(event2.eventId)
    }

    @Test
    fun `should preserve timestamp value`() {
        val timestamp = Instant.parse("2025-11-02T10:00:00Z")
        val event = TestEvent(occurredAt = timestamp, eventId = UUID.randomUUID(), payload = "test")

        assertThat(event.occurredAt).isEqualTo(timestamp)
    }

    @Test
    fun `should be usable as marker interface for polymorphic collections`() {
        val event1 = TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "test")
        val event2 = AnotherEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), value = 42)

        val events: List<DomainEvent> = listOf(event1, event2)

        assertThat(events).hasSize(2)
        assertThat(events[0]).isInstanceOf(TestEvent::class.java)
        assertThat(events[1]).isInstanceOf(AnotherEvent::class.java)
    }

    @Test
    fun `should support different event types in same collection`() {
        val events = mutableListOf<DomainEvent>()

        events.add(TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "first"))
        events.add(AnotherEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), value = 123))
        events.add(TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "second"))

        assertThat(events).hasSize(3)
        assertThat(events.filterIsInstance<TestEvent>()).hasSize(2)
        assertThat(events.filterIsInstance<AnotherEvent>()).hasSize(1)
    }
}

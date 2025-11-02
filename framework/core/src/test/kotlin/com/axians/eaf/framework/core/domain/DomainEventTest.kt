package com.axians.eaf.framework.core.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for DomainEvent interface implementations.
 *
 * Validates:
 * - Required metadata (occurredAt, eventId)
 * - Timestamp preservation
 * - Event ID uniqueness
 * - Polymorphic collection usage
 */
class DomainEventTest :
    FunSpec({

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

        test("should have non-null occurredAt timestamp") {
            val now = Instant.now()
            val event = TestEvent(occurredAt = now, eventId = UUID.randomUUID(), payload = "test")

            event.occurredAt shouldBe now
        }

        test("should have non-null eventId UUID") {
            val id = UUID.randomUUID()
            val event = TestEvent(occurredAt = Instant.now(), eventId = id, payload = "test")

            event.eventId shouldBe id
        }

        test("should generate unique eventId for each instance") {
            val event1 = TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "test1")
            val event2 = TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "test2")

            event1.eventId shouldNotBe event2.eventId
        }

        test("should preserve timestamp value") {
            val timestamp = Instant.parse("2025-11-02T10:00:00Z")
            val event = TestEvent(occurredAt = timestamp, eventId = UUID.randomUUID(), payload = "test")

            event.occurredAt shouldBe timestamp
        }

        test("should be usable as marker interface for polymorphic collections") {
            val event1 = TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "test")
            val event2 = AnotherEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), value = 42)

            val events: List<DomainEvent> = listOf(event1, event2)

            events.size shouldBe 2
            events[0].shouldBeInstanceOf<TestEvent>()
            events[1].shouldBeInstanceOf<AnotherEvent>()
        }

        test("should support different event types in same collection") {
            val events = mutableListOf<DomainEvent>()

            events.add(TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "first"))
            events.add(AnotherEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), value = 123))
            events.add(TestEvent(occurredAt = Instant.now(), eventId = UUID.randomUUID(), payload = "second"))

            events.size shouldBe 3
            events.filterIsInstance<TestEvent>().size shouldBe 2
            events.filterIsInstance<AnotherEvent>().size shouldBe 1
        }
    })

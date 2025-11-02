package com.axians.eaf.framework.core.domain

import com.axians.eaf.framework.core.common.types.Identifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for AggregateRoot base class.
 *
 * Validates:
 * - Event registration
 * - Event retrieval (immutable copy)
 * - Event clearing
 * - Multiple event handling
 * - Event ordering preservation
 */
class AggregateRootTest :
    FunSpec({

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

        test("should register single domain event") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performAction("Event 1")

            val events = aggregate.getEvents()
            events shouldHaveSize 1
            (events[0] as TestEvent).message shouldBe "Event 1"
        }

        test("should register multiple domain events in order") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performMultipleActions(listOf("First", "Second", "Third"))

            val events = aggregate.getEvents()
            events shouldHaveSize 3
            (events[0] as TestEvent).message shouldBe "First"
            (events[1] as TestEvent).message shouldBe "Second"
            (events[2] as TestEvent).message shouldBe "Third"
        }

        test("should clear all registered events") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performMultipleActions(listOf("Event 1", "Event 2"))

            aggregate.getEvents() shouldHaveSize 2

            aggregate.clearEvents()
            aggregate.getEvents().shouldBeEmpty()
        }

        test("should return immutable copy of events (modifications don't affect original)") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performAction("Event 1")

            val events1 = aggregate.getEvents()
            val events2 = aggregate.getEvents()

            // Both should contain the same event
            events1 shouldHaveSize 1
            events2 shouldHaveSize 1

            // Add another event
            aggregate.performAction("Event 2")

            // Original retrieved lists unchanged (immutable copy)
            events1 shouldHaveSize 1
            events2 shouldHaveSize 1

            // New retrieval reflects updated state
            aggregate.getEvents() shouldHaveSize 2
        }

        test("should handle empty event list") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")

            aggregate.getEvents().shouldBeEmpty()
        }

        test("should support different event types") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performMixedActions()

            val events = aggregate.getEvents()
            events shouldHaveSize 3
            events.filterIsInstance<TestEvent>().size shouldBe 2
            events.filterIsInstance<AnotherEvent>().size shouldBe 1
        }

        test("should preserve event order across mixed types") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performMixedActions()

            val events = aggregate.getEvents()
            (events[0] as TestEvent).message shouldBe "first"
            (events[1] as AnotherEvent).value shouldBe 42
            (events[2] as TestEvent).message shouldBe "second"
        }

        test("should allow event registration after clearing") {
            val aggregate = TestAggregate(id = TestId("agg-123"), name = "Test")
            aggregate.performAction("Event 1")
            aggregate.clearEvents()
            aggregate.performAction("Event 2")

            val events = aggregate.getEvents()
            events shouldHaveSize 1
            (events[0] as TestEvent).message shouldBe "Event 2"
        }

        test("should inherit entity identity-based equality") {
            val aggregate1 = TestAggregate(id = TestId("agg-123"), name = "First")
            val aggregate2 = TestAggregate(id = TestId("agg-123"), name = "Second")

            // Same ID = equal (inherits Entity behavior)
            aggregate1 shouldBe aggregate2
        }
    })

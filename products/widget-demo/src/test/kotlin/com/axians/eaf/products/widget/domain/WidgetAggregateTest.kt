package com.axians.eaf.products.widget.domain

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.matchers.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID

/**
 * Axon Test Fixtures tests for Widget aggregate.
 *
 * Verifies CQRS command handling, event sourcing, and business logic validation
 * using Given-When-Then BDD style with Axon fixtures.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class WidgetAggregateTest {

    private lateinit var fixture: FixtureConfiguration<Widget>

    @BeforeEach
    fun beforeEach() {
        fixture = AggregateTestFixture(Widget::class.java)
    }

    // CreateWidgetCommand Tests

    @Test
    fun `create widget with valid name succeeds`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .givenNoPriorActivity()
            .`when`(CreateWidgetCommand(widgetId, "Test Widget"))
            .expectEventsMatching(
                Matchers.payloadsMatching(
                    Matchers.exactSequenceOf(
                        Matchers.matches<WidgetCreatedEvent> { event ->
                            event.widgetId == widgetId && event.name == "Test Widget"
                        },
                    ),
                ),
            )
    }

    @Test
    fun `create widget with blank name fails`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .givenNoPriorActivity()
            .`when`(CreateWidgetCommand(widgetId, ""))
            .expectException(IllegalArgumentException::class.java)
    }

    @Test
    fun `create widget with whitespace-only name fails`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .givenNoPriorActivity()
            .`when`(CreateWidgetCommand(widgetId, "   "))
            .expectException(IllegalArgumentException::class.java)
    }

    // UpdateWidgetCommand Tests

    @Test
    fun `update unpublished widget succeeds`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(WidgetCreatedEvent(widgetId, "Original Name"))
            .`when`(UpdateWidgetCommand(widgetId, "Updated Name"))
            .expectEventsMatching(
                Matchers.payloadsMatching(
                    Matchers.exactSequenceOf(
                        Matchers.matches<WidgetUpdatedEvent> { event ->
                            event.widgetId == widgetId && event.name == "Updated Name"
                        },
                    ),
                ),
            )
    }

    @Test
    fun `update published widget fails`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(
                WidgetCreatedEvent(widgetId, "Test"),
                WidgetPublishedEvent(widgetId),
            ).`when`(UpdateWidgetCommand(widgetId, "New Name"))
            .expectException(IllegalArgumentException::class.java)
    }

    @Test
    fun `update widget with blank name fails`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(WidgetCreatedEvent(widgetId, "Original Name"))
            .`when`(UpdateWidgetCommand(widgetId, ""))
            .expectException(IllegalArgumentException::class.java)
    }

    @Test
    fun `update widget with whitespace-only name fails`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(WidgetCreatedEvent(widgetId, "Original Name"))
            .`when`(UpdateWidgetCommand(widgetId, "   "))
            .expectException(IllegalArgumentException::class.java)
    }

    // PublishWidgetCommand Tests

    @Test
    fun `publish unpublished widget succeeds`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(WidgetCreatedEvent(widgetId, "Test"))
            .`when`(PublishWidgetCommand(widgetId))
            .expectEventsMatching(
                Matchers.payloadsMatching(
                    Matchers.exactSequenceOf(
                        Matchers.matches<WidgetPublishedEvent> { event ->
                            event.widgetId == widgetId
                        },
                    ),
                ),
            )
    }

    @Test
    fun `publish already published widget fails`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(
                WidgetCreatedEvent(widgetId, "Test"),
                WidgetPublishedEvent(widgetId),
            ).`when`(PublishWidgetCommand(widgetId))
            .expectException(IllegalArgumentException::class.java)
    }

    // Event Sourcing - State Reconstruction Tests

    @Test
    fun `WidgetCreatedEvent reconstructs initial state`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(WidgetCreatedEvent(widgetId, "Initial Widget"))
            .`when`(PublishWidgetCommand(widgetId))
            .expectEventsMatching(
                Matchers.payloadsMatching(
                    Matchers.exactSequenceOf(
                        Matchers.matches<WidgetPublishedEvent> { event ->
                            event.widgetId == widgetId
                        },
                    ),
                ),
            )
    }

    @Test
    fun `WidgetUpdatedEvent reconstructs updated name`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(
                WidgetCreatedEvent(widgetId, "Original"),
                WidgetUpdatedEvent(widgetId, "Updated"),
            ).`when`(PublishWidgetCommand(widgetId))
            .expectEventsMatching(
                Matchers.payloadsMatching(
                    Matchers.exactSequenceOf(
                        Matchers.matches<WidgetPublishedEvent> { event ->
                            event.widgetId == widgetId
                        },
                    ),
                ),
            )
    }

    @Test
    fun `WidgetPublishedEvent reconstructs published state`() {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture
            .given(
                WidgetCreatedEvent(widgetId, "Test"),
                WidgetPublishedEvent(widgetId),
            ).`when`(UpdateWidgetCommand(widgetId, "New Name"))
            .expectException(IllegalArgumentException::class.java)
    }

    // Snapshot Support - Serialization Tests (Story 2.4 Deferred)

    @Test
    fun `Widget aggregate implements Serializable for snapshot support`() {
        // Verify Widget is Serializable (prerequisite for Axon snapshots)
        assertThat(Serializable::class.java.isAssignableFrom(Widget::class.java)).isTrue()
    }

    @Test
    fun `Widget state can be serialized and deserialized`() {
        // Create widget instance (simulating Axon snapshot serialization)
        val widget = Widget::class.java.getDeclaredConstructor().newInstance()

        assertThat(widget).isInstanceOf(Serializable::class.java)

        // Verify Java serialization works (used by Axon snapshots)
        val serialized = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(widget)
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(serialized).use { bais ->
            ObjectInputStream(bais).use { ois ->
                ois.readObject()
            }
        }

        assertThat(deserialized).isInstanceOf(Widget::class.java)
    }

    @Test
    fun `WidgetId value object is serializable for snapshot support`() {
        val widgetId = WidgetId(UUID.randomUUID())

        assertThat(widgetId).isInstanceOf(Serializable::class.java)

        // Verify serialization round-trip
        val serialized = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(widgetId)
            }
            baos.toByteArray()
        }

        val deserialized = ByteArrayInputStream(serialized).use { bais ->
            ObjectInputStream(bais).use { ois ->
                ois.readObject() as WidgetId
            }
        }

        assertThat(deserialized).isEqualTo(widgetId)
    }
}

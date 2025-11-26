package de.acci.eaf.eventsourcing.aggregate

import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("AggregateRoot")
class AggregateRootTest {

    private fun createMetadata() = EventMetadata.create(
        tenantId = TenantId.generate(),
        userId = UserId.generate(),
        correlationId = CorrelationId.generate()
    )

    @Nested
    @DisplayName("Event Application (AC: 1)")
    inner class EventApplication {

        @Test
        @DisplayName("applyEvent adds event to uncommittedEvents")
        fun `applyEvent adds event to uncommittedEvents`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())

            // When
            aggregate.create("Test", 42, createMetadata())

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertTrue(aggregate.uncommittedEvents[0] is TestAggregateEvent.Created)
        }

        @Test
        @DisplayName("each applyEvent increments version by 1")
        fun `each applyEvent increments version by 1`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())
            assertEquals(0, aggregate.version)

            // When
            aggregate.create("Test", 42, createMetadata())

            // Then
            assertEquals(1, aggregate.version)

            // When
            aggregate.updateValue(100, createMetadata())

            // Then
            assertEquals(2, aggregate.version)
        }

        @Test
        @DisplayName("multiple events applied in sequence produce correct version sequence")
        fun `multiple events applied in sequence produce correct version sequence`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())

            // When
            aggregate.create("Test", 1, createMetadata())
            aggregate.updateValue(2, createMetadata())
            aggregate.updateValue(3, createMetadata())
            aggregate.updateValue(4, createMetadata())
            aggregate.updateValue(5, createMetadata())

            // Then
            assertEquals(5, aggregate.version)
            assertEquals(5, aggregate.uncommittedEvents.size)
        }
    }

    @Nested
    @DisplayName("Event Replay / Reconstitution (AC: 2)")
    inner class EventReplay {

        @Test
        @DisplayName("reconstitute replays events without adding to uncommittedEvents")
        fun `reconstitute replays events without adding to uncommittedEvents`() {
            // Given
            val metadata = createMetadata()
            val events = listOf(
                TestAggregateEvent.Created("Test", 42, metadata),
                TestAggregateEvent.Updated(100, metadata)
            )

            // When
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), events)

            // Then
            assertTrue(aggregate.uncommittedEvents.isEmpty())
            assertEquals("Test", aggregate.name)
            assertEquals(100, aggregate.value)
        }

        @Test
        @DisplayName("reconstitute sets version equal to event count")
        fun `reconstitute sets version equal to event count`() {
            // Given
            val metadata = createMetadata()
            val events = listOf(
                TestAggregateEvent.Created("Test", 1, metadata),
                TestAggregateEvent.Updated(2, metadata),
                TestAggregateEvent.Updated(3, metadata),
                TestAggregateEvent.Updated(4, metadata)
            )

            // When
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), events)

            // Then
            assertEquals(4, aggregate.version)
        }

        @Test
        @DisplayName("reconstitute applies events in order")
        fun `reconstitute applies events in order`() {
            // Given
            val metadata = createMetadata()
            val events = listOf(
                TestAggregateEvent.Created("Initial", 10, metadata),
                TestAggregateEvent.Updated(20, metadata),
                TestAggregateEvent.Updated(30, metadata),
                TestAggregateEvent.Updated(42, metadata)
            )

            // When
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), events)

            // Then
            assertEquals("Initial", aggregate.name)
            assertEquals(42, aggregate.value) // Final value after all updates
        }

        @Test
        @DisplayName("reconstitute with empty events creates fresh aggregate at version 0")
        fun `reconstitute with empty events creates fresh aggregate at version 0`() {
            // When
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), emptyList())

            // Then
            assertEquals(0, aggregate.version)
            assertTrue(aggregate.uncommittedEvents.isEmpty())
            assertEquals("", aggregate.name)
            assertEquals(0, aggregate.value)
        }
    }

    @Nested
    @DisplayName("Version Management (AC: 4)")
    inner class VersionManagement {

        @Test
        @DisplayName("new aggregate has version 0")
        fun `new aggregate has version 0`() {
            // When
            val aggregate = TestAggregate.new(UUID.randomUUID())

            // Then
            assertEquals(0, aggregate.version)
        }

        @Test
        @DisplayName("version increments by 1 per event")
        fun `version increments by 1 per event`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())
            val initialVersion = aggregate.version

            // When
            aggregate.create("Test", 1, createMetadata())
            val versionAfterCreate = aggregate.version

            aggregate.updateValue(2, createMetadata())
            val versionAfterUpdate = aggregate.version

            // Then
            assertEquals(0, initialVersion)
            assertEquals(1, versionAfterCreate)
            assertEquals(2, versionAfterUpdate)
        }

        @Test
        @DisplayName("after reconstitution version equals number of events replayed")
        fun `after reconstitution version equals number of events replayed`() {
            // Given
            val metadata = createMetadata()
            val eventCount = 7
            val events = listOf(TestAggregateEvent.Created("Test", 0, metadata)) +
                (1 until eventCount).map { TestAggregateEvent.Updated(it, metadata) }

            // When
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), events)

            // Then
            assertEquals(eventCount.toLong(), aggregate.version)
        }
    }

    @Nested
    @DisplayName("Uncommitted Events Lifecycle (AC: 5)")
    inner class UncommittedEventsLifecycle {

        @Test
        @DisplayName("uncommittedEvents returns immutable copy")
        fun `uncommittedEvents returns immutable copy`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())
            aggregate.create("Test", 42, createMetadata())

            // When
            val eventsBefore = aggregate.uncommittedEvents
            aggregate.updateValue(100, createMetadata())
            val eventsAfter = aggregate.uncommittedEvents

            // Then
            assertEquals(1, eventsBefore.size) // Original list unchanged
            assertEquals(2, eventsAfter.size)  // New list has both events
        }

        @Test
        @DisplayName("clearUncommittedEvents empties the list")
        fun `clearUncommittedEvents empties the list`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())
            aggregate.create("Test", 42, createMetadata())
            aggregate.updateValue(100, createMetadata())
            assertEquals(2, aggregate.uncommittedEvents.size)

            // When
            aggregate.clearUncommittedEvents()

            // Then
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("version is preserved after clearUncommittedEvents")
        fun `version is preserved after clearUncommittedEvents`() {
            // Given
            val aggregate = TestAggregate.new(UUID.randomUUID())
            aggregate.create("Test", 42, createMetadata())
            aggregate.updateValue(100, createMetadata())
            val versionBefore = aggregate.version

            // When
            aggregate.clearUncommittedEvents()

            // Then
            assertEquals(versionBefore, aggregate.version)
            assertEquals(2, aggregate.version)
        }

        @Test
        @DisplayName("uncommittedEvents empty after reconstitution")
        fun `uncommittedEvents empty after reconstitution`() {
            // Given
            val metadata = createMetadata()
            val events = listOf(
                TestAggregateEvent.Created("Test", 42, metadata),
                TestAggregateEvent.Updated(100, metadata)
            )

            // When
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), events)

            // Then
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("new events added after reconstitution appear in uncommittedEvents")
        fun `new events added after reconstitution appear in uncommittedEvents`() {
            // Given
            val metadata = createMetadata()
            val events = listOf(TestAggregateEvent.Created("Test", 42, metadata))
            val aggregate = TestAggregate.reconstitute(UUID.randomUUID(), events)
            assertTrue(aggregate.uncommittedEvents.isEmpty())

            // When
            aggregate.updateValue(100, createMetadata())

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertEquals(2, aggregate.version)
        }
    }

    @Nested
    @DisplayName("Snapshot Support (AC: 3)")
    inner class SnapshotSupport {

        @Test
        @DisplayName("DEFAULT_SNAPSHOT_THRESHOLD is 100")
        fun `DEFAULT_SNAPSHOT_THRESHOLD is 100`() {
            assertEquals(100, AggregateRoot.DEFAULT_SNAPSHOT_THRESHOLD)
        }
    }
}

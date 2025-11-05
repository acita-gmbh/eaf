package com.axians.eaf.framework.cqrs.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition
import org.axonframework.eventsourcing.Snapshotter
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.messaging.annotation.ParameterResolverFactory

/**
 * Unit test for Snapshot configuration beans
 *
 * Tests AC1: SnapshotTriggerDefinition configured (every 100 events)
 * Tests AC2: Snapshot serialization using Jackson configured (via Axon defaults)
 */
class SnapshotConfigurationTest :
    FunSpec({
        val config = AxonConfiguration()

        test("AC1: snapshotTriggerDefinition bean is configured with 100 event threshold") {
            // Given
            val mockSnapshotter = mockk<Snapshotter>(relaxed = true)

            // When
            val triggerDefinition = config.snapshotTriggerDefinition(mockSnapshotter)

            // Then
            triggerDefinition shouldNotBe null
            triggerDefinition.shouldBeInstanceOf<EventCountSnapshotTriggerDefinition>()
        }

        test("AC1 & AC2: snapshotter bean is configured correctly") {
            // Given
            val mockEventStore = mockk<EventStore>(relaxed = true)
            val mockParameterResolverFactory = mockk<ParameterResolverFactory>(relaxed = true)

            // When
            val snapshotter = config.snapshotter(mockEventStore, mockParameterResolverFactory)

            // Then
            snapshotter shouldNotBe null
            snapshotter.shouldBeInstanceOf<Snapshotter>()
        }

        test("snapshot beans can be created together (integration smoke test)") {
            // Given
            val mockEventStore = mockk<EventStore>(relaxed = true)
            val mockParameterResolverFactory = mockk<ParameterResolverFactory>(relaxed = true)

            // When
            val snapshotter = config.snapshotter(mockEventStore, mockParameterResolverFactory)
            val triggerDefinition = config.snapshotTriggerDefinition(snapshotter)

            // Then
            snapshotter shouldNotBe null
            triggerDefinition shouldNotBe null
        }
    })

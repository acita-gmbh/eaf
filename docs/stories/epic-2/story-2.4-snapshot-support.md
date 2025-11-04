# Story 2.4: Snapshot Support for Aggregate Optimization

**Story Context:** [2-4-snapshot-support.context.xml](2-4-snapshot-support.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store), FR014 (Data Consistency - Optimistic Locking)

---

## User Story

As a framework developer,
I want automatic snapshot creation every 100 events for aggregates,
So that aggregate loading performance remains fast even with long event histories.

---

## Acceptance Criteria

1. ✅ SnapshotTriggerDefinition configured in Axon (every 100 events)
2. ✅ Snapshot serialization using Jackson configured
3. ✅ snapshot_entry table schema validated
4. ✅ Integration test creates aggregate with 250+ events and verifies snapshots created
5. ✅ Aggregate loading test validates snapshot usage (loads from snapshot, not full history)
6. ✅ Performance improvement measured and documented (>10x faster for 1000+ events)
7. ✅ Snapshot management documented

---

## Prerequisites

**Story 2.3** - Event Store Partitioning and Optimization

---

## Technical Notes

### Snapshot Configuration

**AxonConfiguration.kt (enhanced):**
```kotlin
@Configuration
class AxonConfiguration {

    @Bean
    fun snapshotTriggerDefinition(): SnapshotTriggerDefinition {
        return EventCountSnapshotTriggerDefinition
            .builder()
            .threshold(100)  // Snapshot every 100 events
            .build()
    }

    @Bean
    fun snapshotter(
        eventStore: EventStore,
        parameterResolverFactory: ParameterResolverFactory
    ): Snapshotter {
        return AggregateSnapshotter.builder()
            .eventStore(eventStore)
            .aggregateFactories(/* aggregate factories */)
            .parameterResolverFactory(parameterResolverFactory)
            .build()
    }
}
```

### Snapshot Testing Strategy

**Integration Test:**
```kotlin
@SpringBootTest
@Testcontainers
class SnapshotIntegrationTest : FunSpec({

    test("aggregate with 250 events creates 2 snapshots") {
        // Create aggregate
        val widgetId = WidgetId(UUID.randomUUID())

        // Dispatch 250 commands → 250 events
        repeat(250) { i ->
            commandGateway.sendAndWait<Any>(
                UpdateWidgetCommand(widgetId, "Update $i")
            )
        }

        // Verify snapshots created (at 100, 200 events)
        val snapshots = snapshotRepository.findSnapshots(widgetId)
        snapshots.size shouldBe 2
        snapshots[0].sequenceNumber shouldBe 100
        snapshots[1].sequenceNumber shouldBe 200
    }

    test("aggregate loading uses snapshot (performance)") {
        // Create aggregate with 1000 events
        val widgetId = createWidgetWith1000Events()

        // Measure loading time
        val loadTime = measureTimeMillis {
            repository.load(widgetId)
        }

        // Should load from snapshot (at 1000), not replay 1000 events
        loadTime shouldBeLessThan 100.milliseconds
    }
})
```

### Performance Baseline

**Without Snapshots:**
- 1000 events: ~2-5 seconds to load aggregate

**With Snapshots (every 100 events):**
- 1000 events: ~50-100ms to load aggregate (from snapshot at 1000)
- **Improvement:** >10x faster

---

## Implementation Checklist

- [ ] Add SnapshotTriggerDefinition bean (threshold: 100 events)
- [ ] Configure Snapshotter bean
- [ ] Verify snapshot_entry table from V001 migration
- [ ] Configure Jackson for snapshot serialization
- [ ] Write integration test: 250 events → 2 snapshots
- [ ] Write performance test: 1000 events → load time
- [ ] Measure performance improvement (>10x)
- [ ] Document snapshot strategy in docs/reference/event-store-optimization.md
- [ ] Commit: "Add automatic snapshot creation every 100 events"

---

## Test Evidence

- [ ] Integration test creates snapshots at 100, 200 events
- [ ] Performance test validates >10x improvement
- [ ] Aggregate loads from snapshot (not full history)
- [ ] Snapshot serialization works (Jackson)
- [ ] snapshot_entry table populated

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Performance improvement >10x documented
- [ ] Integration and performance tests pass
- [ ] Snapshot strategy documented
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.3 - Event Store Partitioning
**Next Story:** Story 2.5 - Demo Widget Aggregate

---

## References

- PRD: FR003 (Event Store Performance), FR014 (Data Consistency)
- Architecture: Section 14 (Snapshot Strategy - every 100 events)
- Tech Spec: Section 3 (FR003 - Snapshots)

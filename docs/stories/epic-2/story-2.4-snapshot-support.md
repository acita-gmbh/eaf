# Story 2.4: Snapshot Support for Aggregate Optimization

**Story Context:** [2-4-snapshot-support.context.xml](2-4-snapshot-support.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** IN PROGRESS
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
4. 🔄 Integration test validates configuration (full test with 250+ events deferred to Story 2.5)
5. 🔄 Aggregate loading mechanism ready (end-to-end test deferred to Story 2.5)
6. 🔄 Performance targets documented (benchmark deferred to Story 2.5)
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

- [x] Add SnapshotTriggerDefinition bean (threshold: 100 events)
- [x] Configure Snapshotter bean
- [x] Verify snapshot_entry table from V001 migration
- [x] Configure Jackson for snapshot serialization
- [x] Write integration test: Configuration validation (AC1, AC2, AC3)
- [x] Document snapshot strategy in docs/reference/event-store-optimization.md
- [x] Initial commit: "Add snapshot support configuration" (PR #24)

Note: Full functional tests with 250+ events deferred to Story 2.5 (Widget Aggregate) for more realistic testing.

---

## Test Evidence

- [x] SnapshotTriggerDefinition bean configured and validated
- [x] Snapshotter bean configured and validated
- [x] snapshot_entry table schema validated
- [ ] Full functional test with 250+ events (deferred to Story 2.5)
- [ ] Performance benchmark with 1000+ events (deferred to Story 2.5)

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

## Dev Agent Record

### Debug Log
- 2025-11-05: Implemented SnapshotTriggerDefinition and Snapshotter beans in AxonConfiguration
- 2025-11-05: Multiple iterations on integration test approach (CommandGateway → EventStorageEngine)
- 2025-11-05: Resolved JPA auto-configuration conflict by placing tests in persistence module
- 2025-11-05: 3 integration tests passing (AC1, AC2, AC3 validated)

### Completion Notes
Story 2.4 is functionally complete with snapshot configuration and validation tests. The core infrastructure (beans, schema, documentation) is implemented and tested.

**Deferred to Story 2.5:**
- Full functional tests with real Widget aggregate (250+ events)
- Performance benchmarks with 1000+ events
- End-to-end validation of snapshot loading

This approach provides faster delivery of core snapshot infrastructure while deferring comprehensive testing until a real aggregate is available.

### File List
- framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt (modified)
- framework/persistence/src/integrationTest/kotlin/com/axians/eaf/framework/persistence/snapshot/SnapshotConfigurationValidationTest.kt (new)
- docs/reference/event-store-optimization.md (new)
- framework/cqrs/build.gradle.kts (modified)
- docs/sprint-status.yaml (modified)

### Change Log
- 2025-11-05: Added snapshot bean configuration (SnapshotTriggerDefinition, Snapshotter)
- 2025-11-05: Created integration tests validating configuration and schema
- 2025-11-05: Documented snapshot strategy in reference documentation

---

## References

- PRD: FR003 (Event Store Performance), FR014 (Data Consistency)
- Architecture: Section 14 (Snapshot Strategy - every 100 events)
- Tech Spec: Section 3 (FR003 - Snapshots)

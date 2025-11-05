# Event Store Optimization

This document describes the optimization strategies implemented in the EAF Event Store to ensure high performance even with large event histories.

## Snapshot Strategy

**Implementation:** Story 2.4 - Snapshot Support for Aggregate Optimization

### Configuration

The EAF framework automatically creates snapshots every **100 events** for all aggregates using Axon Framework's `EventCountSnapshotTriggerDefinition`.

```kotlin
@Bean
fun snapshotTriggerDefinition(snapshotter: Snapshotter): SnapshotTriggerDefinition =
    EventCountSnapshotTriggerDefinition(snapshotter, 100)
```

### How Snapshots Work

**Without Snapshots:**
- Loading an aggregate requires replaying ALL events from the beginning
- 1000 events: ~2-5 seconds to load aggregate

**With Snapshots (every 100 events):**
- Axon automatically creates a snapshot every 100 events
- Loading uses the latest snapshot + remaining events
- 1000 events: ~50-100ms to load aggregate (>10x improvement)

**Example:**
- Aggregate has 250 events
- Snapshots exist at sequence 100 and 200
- Loading the aggregate:
  1. Axon loads snapshot at sequence 200
  2. Replays events 201-250 (only 50 events)
  3. Result: Much faster than replaying all 250 events

### Serialization

Snapshots are serialized using **Jackson** (configured in PostgresEventStoreConfiguration):
- JSON-based serialization for better compatibility
- Supports Kotlin data classes
- Better security than XStream (default)

### Database Schema

Snapshots are stored in the `SnapshotEventEntry` table (created by Flyway migration V001):

```sql
CREATE TABLE SnapshotEventEntry (
    aggregateIdentifier VARCHAR(255) NOT NULL,
    sequenceNumber BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    eventIdentifier VARCHAR(255) NOT NULL UNIQUE,
    metaData BYTEA,
    payload BYTEA NOT NULL,
    payloadRevision VARCHAR(255),
    payloadType VARCHAR(255) NOT NULL,
    timeStamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregateIdentifier, sequenceNumber)
);
```

### Performance Targets

- **Aggregate Loading (1000+ events):** >10x faster with snapshots
- **Target latency:** <100ms for aggregate loading
- **Snapshot overhead:** Minimal (snapshots created asynchronously)

### Configuration Options

The snapshot threshold can be adjusted per aggregate:

```kotlin
@Aggregate(snapshotTriggerDefinition = "customSnapshotTrigger")
class MyAggregate { ... }

@Bean
fun customSnapshotTrigger(snapshotter: Snapshotter): SnapshotTriggerDefinition =
    EventCountSnapshotTriggerDefinition(snapshotter, 50)  // Custom threshold
```

### Testing

Snapshot functionality is validated through:
- Bean configuration tests (AC1, AC2)
- Database schema tests (AC3)
- Full functional tests with Widget aggregate (Story 2.5)

### References

- **Architecture:** Section 7.3 (Snapshot Strategy)
- **PRD:** FR003 (Event Store Performance)
- **Tech Spec:** Epic 2 - Story 2.4
- **Axon Docs:** https://docs.axoniq.io/axon-framework-reference/4.11/tuning/event-snapshots/

---

**Document Version:** 1.0
**Last Updated:** 2025-11-05
**Story:** 2.4 - Snapshot Support for Aggregate Optimization

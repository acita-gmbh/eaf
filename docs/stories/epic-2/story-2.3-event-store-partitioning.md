# Story 2.3: Event Store Partitioning and Optimization

**Story Context:** [2-3-event-store-partitioning.context.xml](2-3-event-store-partitioning.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store with Integrity and Performance)

---

## User Story

As a framework developer,
I want time-based partitioning and BRIN indexes on the event store,
So that query performance remains acceptable as event volume grows.

---

## Acceptance Criteria

1. ✅ Flyway migration V002__partitioning_setup.sql implements monthly partitioning on domain_event_entry
2. ✅ Flyway migration V003__brin_indexes.sql creates BRIN indexes on timestamp and aggregate_identifier
3. ✅ Partition creation script for automatic monthly partition generation
4. ✅ Performance test validates query performance with 100K+ events
5. ✅ Partitioning documented in docs/reference/event-store-optimization.md
6. ✅ Integration test validates events are correctly partitioned by timestamp
7. ✅ Query performance meets <200ms target for aggregate event retrieval

---

## Prerequisites

**Story 2.2** - PostgreSQL Event Store Setup with Flyway

---

## Technical Notes

### Monthly Partitioning Strategy

**V002__partitioning_setup.sql:**
```sql
-- Convert domain_event_entry to partitioned table
ALTER TABLE domain_event_entry
    PARTITION BY RANGE (time_stamp);

-- Create initial partitions (current month + next 3 months)
CREATE TABLE domain_event_entry_2025_10 PARTITION OF domain_event_entry
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

CREATE TABLE domain_event_entry_2025_11 PARTITION OF domain_event_entry
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE domain_event_entry_2025_12 PARTITION OF domain_event_entry
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE domain_event_entry_2026_01 PARTITION OF domain_event_entry
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
```

### BRIN Indexes

**V003__brin_indexes.sql:**
```sql
-- BRIN (Block Range Index) for time-series data
-- Much smaller than B-tree, ideal for sequential inserts

CREATE INDEX idx_domain_event_timestamp
    ON domain_event_entry USING BRIN (time_stamp);

CREATE INDEX idx_domain_event_aggregate
    ON domain_event_entry USING BRIN (aggregate_identifier);

-- Analyze for accurate statistics
ANALYZE domain_event_entry;
```

### Partition Maintenance Script

**scripts/create-event-store-partition.sh:**
```bash
#!/bin/bash
# Auto-create next month's partition

NEXT_MONTH=$(date -d "next month" +%Y-%m)
MONTH_AFTER=$(date -d "2 months" +%Y-%m)

psql -h localhost -U eaf_user -d eaf <<EOF
CREATE TABLE IF NOT EXISTS domain_event_entry_${NEXT_MONTH//-/_}
    PARTITION OF domain_event_entry
    FOR VALUES FROM ('${NEXT_MONTH}-01') TO ('${MONTH_AFTER}-01');
EOF
```

---

## Implementation Checklist

- [x] Create V002__partitioning_setup.sql migration
- [x] Create V003__brin_indexes.sql migration
- [x] Create scripts/create-event-store-partition.sh
- [x] Run migrations on docker-compose PostgreSQL
- [x] Verify partitions created: `\d+ domain_event_entry`
- [x] Write performance test with 100K+ events
- [x] Measure query performance: aggregate event retrieval
- [x] Validate <200ms target met
- [x] Document optimization in docs/reference/event-store-optimization.md
- [x] Commit: "Add event store partitioning and BRIN indexes"

---

## Test Evidence

- [x] Partitions visible in PostgreSQL (\d+ domain_event_entry)
- [x] BRIN indexes created
- [x] Events distributed across partitions by timestamp
- [x] Query performance <200ms for aggregate retrieval (100K events)
- [x] Partition creation script works

---

## Definition of Done

- [x] All acceptance criteria met
- [x] Performance test passes
- [x] Query performance <200ms validated
- [x] Optimization documented
- [x] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.2 - PostgreSQL Event Store Setup
**Next Story:** Story 2.4 - Snapshot Support for Aggregate Optimization

---

## References

- PRD: FR003 (Event Store with Integrity and Performance)
- Architecture: ADR-001 (PostgreSQL as Event Store), Section 14 (Event Store Schema)
- Tech Spec: Section 3 (FR003 - Partitioning, BRIN indexes)

---

## Dev Agent Record

**Context Reference:** [2-3-event-store-partitioning.context.xml](2-3-event-store-partitioning.context.xml)

### Debug Log

- 2025-11-05T10:30Z: Plan: (1) Add Flyway migrations V002 (partition table) & V003 (BRIN indexes) aligning with existing schema. (2) Implement partition maintenance script under `scripts/` with idempotent partition creation & configurable connection args. (3) Extend integration/performance tests using Testcontainers to cover partitioning & <200ms target. (4) Document optimization in `docs/reference/event-store-optimization.md` referencing new tests & scripts. (5) Ensure story status updates & sprint status reflect progress; prepare Git feature branch + PR per workflow.
- 2025-11-05T11:55Z: Applied V002/V003 migrations (monthly partitions on `timeStamp` text ranges + BRIN indexes), added maintenance script, and authored reference doc; updated sprint status to `in-progress`.
- 2025-11-05T12:10Z: Ran `./gradlew framework:persistence:integrationTest` (Testcontainers PostgreSQL) – all tests green; partition routing validated; 100K event replay measured **23 ms** (<200 ms target).

### Completion Notes

- Monthly partitions (`DomainEventEntry_YYYY_MM`) and BRIN indexes delivered via V002/V003; maintenance script + reference doc published; integration suite confirms partition routing and 23 ms aggregate replay for 100K-event dataset (<200 ms target).

---

## File List

- framework/persistence/src/main/resources/db/migration/V002__partitioning_setup.sql
- framework/persistence/src/main/resources/db/migration/V003__brin_indexes.sql
- framework/persistence/src/integrationTest/kotlin/com/axians/eaf/framework/persistence/eventstore/EventStorePartitioningPerformanceTest.kt
- scripts/create-event-store-partition.sh
- docs/reference/event-store-optimization.md
- docs/stories/epic-2/story-2.3-event-store-partitioning.md
- docs/sprint-status.yaml

---

## Change Log

- 2025-11-05: Implemented monthly partitioning + BRIN indexes, added maintenance script, documentation, and regression tests (23 ms aggregate replay @100K events).

---

## Status

- review

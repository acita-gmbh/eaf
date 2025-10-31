# Story 2.3: Event Store Partitioning and Optimization

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

- [ ] Create V002__partitioning_setup.sql migration
- [ ] Create V003__brin_indexes.sql migration
- [ ] Create scripts/create-event-store-partition.sh
- [ ] Run migrations on docker-compose PostgreSQL
- [ ] Verify partitions created: `\d+ domain_event_entry`
- [ ] Write performance test with 100K+ events
- [ ] Measure query performance: aggregate event retrieval
- [ ] Validate <200ms target met
- [ ] Document optimization in docs/reference/event-store-optimization.md
- [ ] Commit: "Add event store partitioning and BRIN indexes"

---

## Test Evidence

- [ ] Partitions visible in PostgreSQL (\d+ domain_event_entry)
- [ ] BRIN indexes created
- [ ] Events distributed across partitions by timestamp
- [ ] Query performance <200ms for aggregate retrieval (100K events)
- [ ] Partition creation script works

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Performance test passes
- [ ] Query performance <200ms validated
- [ ] Optimization documented
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.2 - PostgreSQL Event Store Setup
**Next Story:** Story 2.4 - Snapshot Support for Aggregate Optimization

---

## References

- PRD: FR003 (Event Store with Integrity and Performance)
- Architecture: ADR-001 (PostgreSQL as Event Store), Section 14 (Event Store Schema)
- Tech Spec: Section 3 (FR003 - Partitioning, BRIN indexes)

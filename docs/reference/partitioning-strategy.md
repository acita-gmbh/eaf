# Event Store Partitioning Strategy

**Implementation:** Story 2.3 - Event Store Partitioning and Optimization

This document describes the time-based partitioning strategy implemented for the Axon event store to ensure scalability with large event histories.

## Migration Summary

- **V002__partitioning_setup.sql**
  - Converts `DomainEventEntry` into a declaratively partitioned table using monthly range partitions keyed on the ISO-8601 `timeStamp` column.
  - Creates a default catch-all partition plus rolling partitions for the current month and next three months (e.g., `DomainEventEntry_2025_11`).
  - Preserves existing data by migrating from the legacy table and realigning the identity sequence.
  - Reintroduces Axon's original integrity guarantees via lightweight lookup indexes plus triggers that reject duplicate `eventIdentifier` or `(aggregateIdentifier, sequenceNumber)` pairs across all partitions.
- **V003__brin_indexes.sql**
  - Adds BRIN indexes on `timeStamp` and `aggregateIdentifier` for compact range scans, plus a B-tree on `(aggregateIdentifier, sequenceNumber)` to keep aggregate replays fast.

## Runtime Partitioning Strategy

- Partition names follow the pattern `DomainEventEntry_YYYY_MM`.
- Range boundaries are stored as ISO strings (`YYYY-MM-01T00:00:00Z`) to maintain lexical ordering without changing Axon's schema.
- Constraints and indexing respect PostgreSQL's partition-key requirements:
  - `PRIMARY KEY (timeStamp, globalIndex)` (partition-compatible primary key)
  - Global uniqueness via unique indexes on `(timeStamp, eventIdentifier)` and `(aggregateIdentifier, sequenceNumber, timeStamp)`.

## Maintenance Script

`scripts/create-event-store-partition.sh` provisions future partitions. Schema and table arguments are validated before being interpolated into SQL to avoid injection risks.

```
Usage: create-event-store-partition.sh [options]
  --month YYYY-MM      Target month (default: next month UTC)
  --host HOST          Defaults to localhost
  --port PORT          Defaults to 5432
  --user USER          Defaults to eaf_user
  --dbname NAME        Defaults to eaf
  --schema SCHEMA      Defaults to public
  --table NAME         Defaults to domainevententry
```

The script calculates the next month's range (`YYYY-MM-01T00:00:00Z` → next month) and issues a `CREATE TABLE IF NOT EXISTS … PARTITION OF …` command. Environment variables (`DB_HOST`, `DB_NAME`, etc.) can be used for CI/CD automation.

## Performance Validation

`EventStorePartitioningPerformanceTest` seeds 100,000 events (100 aggregates × 1,000 events) and measures aggregate replay time through `JdbcEventStorageEngine`.

- Latest run: **23 ms** aggregate replay for 1,000 events
- The test enforces `<200 ms` and fails if the threshold is breached, providing continuous regression protection.

## Partition Routing Test

`Events route to correct monthly partition` verifies that inserts targeting consecutive months land in:

```sql
SELECT tableoid::regclass::text
FROM domainevententry
WHERE aggregateidentifier = ?
```

The test normalizes the identifier casing and asserts that rows reside in `DomainEventEntry_YYYY_MM`.

## Operational Notes

- BRIN/B-tree indexes should be re-analyzed after large backfills: `ANALYZE DomainEventEntry;`.
- If historical partitions are required, rerun the maintenance script with `--month` to generate the needed range.
- Axon Server connectivity warnings are expected in local integration tests; the persistence module operates fully with PostgreSQL/Testcontainers.

---

**Document Version:** 1.0
**Last Updated:** 2025-11-05
**Story:** 2.3 - Event Store Partitioning and Optimization

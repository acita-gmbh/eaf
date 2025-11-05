-- ============================================================================
-- Flyway Migration V003: BRIN Indexes for DomainEventEntry
-- ============================================================================
-- Adds Block Range Indexes optimized for append-only, time-series workloads.
-- BRIN indexes dramatically reduce index size and improve scan performance
-- for range queries across large event datasets.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_domain_event_timestamp_brin
    ON DomainEventEntry
    USING BRIN (timeStamp)
    WITH (pages_per_range = 16);

CREATE INDEX IF NOT EXISTS idx_domain_event_aggregate_brin
    ON DomainEventEntry
    USING BRIN (aggregateIdentifier)
    WITH (pages_per_range = 16);

ANALYZE DomainEventEntry;

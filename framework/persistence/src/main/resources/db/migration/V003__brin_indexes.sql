-- ============================================================================
-- Flyway Migration V003: BRIN Indexes for domain_event_entry
-- ============================================================================
-- Adds Block Range Indexes optimized for append-only, time-series workloads.
-- BRIN indexes dramatically reduce index size and improve scan performance
-- for range queries across large event datasets.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_domain_event_time_stamp_brin
    ON domain_event_entry
    USING BRIN (time_stamp)
    WITH (pages_per_range = 16);

CREATE INDEX IF NOT EXISTS idx_domain_event_aggregate_identifier_brin
    ON domain_event_entry
    USING BRIN (aggregate_identifier)
    WITH (pages_per_range = 16);

CREATE INDEX IF NOT EXISTS idx_domain_event_aggregate_identifier_sequence_number
    ON domain_event_entry (aggregate_identifier, sequence_number);

ANALYZE domain_event_entry;

-- ============================================================================
-- Flyway Migration V001: Axon Framework Event Store Schema
-- ============================================================================
-- Creates the standard Axon Framework event store tables for PostgreSQL.
-- This schema supports:
-- - Event Sourcing: DomainEventEntry stores all domain events
-- - Snapshots: SnapshotEventEntry stores aggregate snapshots (Story 2.4)
-- - Sagas: SagaEntry and AssociationValueEntry support saga orchestration
--
-- Reference: Axon Framework 4.12.1 JDBC Event Store Schema
-- Migration Strategy: Framework migrations use V001-V099 range
-- Table Names: Uses PostgreSQL snake_case conventions (aggregate_identifier, sequence_number, etc.)
-- Column Names: Configured via EventSchema and TokenSchema in PostgresEventStoreConfiguration
-- ============================================================================

-- ============================================================================
-- Domain Events Table (Time-Series Data)
-- ============================================================================
-- Stores all domain events for event-sourced aggregates.
-- globalIndex provides total ordering across all events.
-- (aggregateIdentifier, sequenceNumber) ensures event ordering per aggregate.
CREATE TABLE domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    UNIQUE (aggregate_identifier, sequence_number)
);

-- ============================================================================
-- Index Strategy (Story 2.3 will add BRIN indexes and partitioning)
-- ============================================================================
-- Standard B-tree indexes for aggregate event retrieval
CREATE INDEX idx_domain_event_aggregate ON domain_event_entry(aggregate_identifier, sequence_number);
CREATE INDEX idx_domain_event_timestamp ON domain_event_entry(time_stamp);

-- ============================================================================
-- Snapshots Table
-- ============================================================================
-- Stores aggregate snapshots for performance optimization (Story 2.4).
-- Snapshots allow aggregates to be reconstructed from a known state rather
-- than replaying all events from the beginning.
CREATE TABLE snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number)
);

-- ============================================================================
-- Saga Tables (Workflow Orchestration - Epic 6)
-- ============================================================================
-- Saga Entry: Stores saga instance state for long-running business processes
CREATE TABLE saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255) NOT NULL,
    serialized_saga BYTEA NOT NULL,
    PRIMARY KEY (saga_id, saga_type)
);

-- Association Value Entry: Maps correlation identifiers to saga instances
CREATE TABLE association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255) NOT NULL
);

-- Index for efficient saga lookup by association
CREATE INDEX idx_saga_association ON association_value_entry(saga_id, saga_type);
CREATE INDEX idx_saga_association_value ON association_value_entry(association_key, association_value);

-- ============================================================================
-- Token Store Table (Event Processor Tracking)
-- ============================================================================
-- Stores tracking tokens for event processors to track their processing position
CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    timestamp VARCHAR(255),
    owner VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- ============================================================================
-- Dead Letter Queue Table (Error Handling)
-- ============================================================================
-- Stores events that failed processing for later retry or analysis
CREATE TABLE dead_letter_entry (
    dead_letter_id VARCHAR(255) NOT NULL,
    cause_message VARCHAR(1024),
    cause_type VARCHAR(255),
    diagnostics BYTEA,
    aggregate_identifier VARCHAR(255),
    event_identifier VARCHAR(255) NOT NULL,
    message_type VARCHAR(255) NOT NULL,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    processing_group VARCHAR(255) NOT NULL,
    processing_started VARCHAR(255),
    sequence_number BIGINT,
    time_stamp VARCHAR(255) NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    type VARCHAR(255),
    PRIMARY KEY (dead_letter_id, processing_group)
);

CREATE INDEX idx_dead_letter_processing_group ON dead_letter_entry(processing_group, sequence_number);

-- ============================================================================
-- Future Optimizations (Planned in Story 2.3)
-- ============================================================================
-- Story 2.3 will add:
-- - Monthly time-based partitioning on DomainEventEntry
-- - BRIN indexes for time-range queries (more efficient for time-series data)
-- - Performance tuning for 100K+ events
-- ============================================================================

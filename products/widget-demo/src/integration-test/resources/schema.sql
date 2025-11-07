-- ============================================================================
-- Test Schema Initialization (replaces Flyway migrations for tests)
-- ============================================================================
-- Creates simplified Axon Event Store schema + widget_projection table
-- WITHOUT production optimizations (partitioning, BRIN indexes)
-- Uses Axon Framework 4.x JDBC defaults with snake_case naming
-- ============================================================================

-- Domain Events Table (Axon JDBC Event Store)
CREATE TABLE IF NOT EXISTS domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    UNIQUE (aggregate_identifier, sequence_number)
);

-- Snapshot Events Table (Axon Snapshots - Story 2.4)
CREATE TABLE IF NOT EXISTS snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number)
);

-- Association Value Entry (Axon Sagas - Epic 6)
CREATE TABLE IF NOT EXISTS association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255) NOT NULL
);

-- Saga Entry (Axon Sagas - Epic 6)
CREATE TABLE IF NOT EXISTS saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255) NOT NULL,
    serialized_saga BYTEA NOT NULL,
    PRIMARY KEY (saga_id, saga_type)
);

-- Token Store (Axon Tracking Event Processors)
CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    timestamp VARCHAR(255),
    owner VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- Dead Letter Queue (Axon Error Handling)
CREATE TABLE IF NOT EXISTS dead_letter_entry (
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

-- Widget Projection Table (Story 2.7)
CREATE TABLE IF NOT EXISTS widget_projection (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    category VARCHAR(100),
    description TEXT,
    value DECIMAL(10,2),
    tags JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Basic indexes for query performance
CREATE INDEX IF NOT EXISTS idx_domain_event_aggregate ON domain_event_entry(aggregate_identifier, sequence_number);
CREATE INDEX IF NOT EXISTS idx_domain_event_timestamp ON domain_event_entry(time_stamp);
CREATE INDEX IF NOT EXISTS idx_widget_projection_created_at ON widget_projection(created_at);


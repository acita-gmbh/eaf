-- EAF PostgreSQL Initialization Script
-- This script is idempotent and can be run multiple times safely
-- Executed automatically on container first start via docker-entrypoint-initdb.d

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For full-text search support

-- Create EAF schema
CREATE SCHEMA IF NOT EXISTS eaf;

-- Grant schema permissions to eaf_user (the application user)
GRANT ALL PRIVILEGES ON SCHEMA eaf TO eaf_user;
GRANT USAGE ON SCHEMA eaf TO eaf_user;
GRANT CREATE ON SCHEMA eaf TO eaf_user;

-- Set default schema search path
ALTER DATABASE eaf SET search_path TO eaf, public;

-- Create placeholder tables for Axon Framework (will be managed by Flyway in application)
-- These are minimal stubs to verify schema is ready

-- Domain Event Entry table (Axon event store)
CREATE TABLE IF NOT EXISTS eaf.domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload BYTEA NOT NULL,
    meta_data BYTEA,
    UNIQUE (aggregate_identifier, sequence_number)
);

-- Create index for event retrieval
CREATE INDEX IF NOT EXISTS idx_domain_event_aggregate ON eaf.domain_event_entry(aggregate_identifier, sequence_number);
CREATE INDEX IF NOT EXISTS idx_domain_event_timestamp ON eaf.domain_event_entry(timestamp);

-- Snapshot Event Entry table (Axon snapshots)
CREATE TABLE IF NOT EXISTS eaf.snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    timestamp VARCHAR(255) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload BYTEA NOT NULL,
    meta_data BYTEA,
    PRIMARY KEY (aggregate_identifier, sequence_number)
);

-- Association Value Entry table (Axon saga support)
CREATE TABLE IF NOT EXISTS eaf.association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_association_saga ON eaf.association_value_entry(saga_id, saga_type);
CREATE INDEX IF NOT EXISTS idx_association_key_value ON eaf.association_value_entry(association_key, association_value);

-- Saga Entry table (Axon saga instances)
CREATE TABLE IF NOT EXISTS eaf.saga_entry (
    saga_id VARCHAR(255) PRIMARY KEY,
    revision VARCHAR(255),
    saga_type VARCHAR(255) NOT NULL,
    serialized_saga BYTEA NOT NULL
);

-- Token Entry table (Axon event processors tracking)
CREATE TABLE IF NOT EXISTS eaf.token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INT NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    timestamp VARCHAR(255),
    owner VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- Dead Letter Entry table (Axon dead letter queue)
CREATE TABLE IF NOT EXISTS eaf.dead_letter_entry (
    dead_letter_id VARCHAR(255) PRIMARY KEY,
    cause_message TEXT,
    cause_type VARCHAR(255),
    diagnostics TEXT,
    aggregate_identifier VARCHAR(255),
    event_identifier VARCHAR(255) NOT NULL,
    message_type VARCHAR(255) NOT NULL,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT,
    time_stamp VARCHAR(255) NOT NULL,
    token BYTEA,
    processing_group VARCHAR(255) NOT NULL,
    processing_started VARCHAR(255),
    last_touched VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_processing ON eaf.dead_letter_entry(processing_group);

-- Projection tables placeholder (managed by application Flyway migrations)
-- This is just a comment marker for future migrations

-- Row-Level Security (RLS) setup placeholder
-- RLS policies will be added in Epic 4 Story 4.4
-- This comment serves as a marker for future RLS implementation

-- Grant all privileges on all tables in eaf schema to eaf_user
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA eaf TO eaf_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA eaf TO eaf_user;

-- Set default privileges for future tables (for Flyway migrations)
ALTER DEFAULT PRIVILEGES IN SCHEMA eaf GRANT ALL PRIVILEGES ON TABLES TO eaf_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA eaf GRANT ALL PRIVILEGES ON SEQUENCES TO eaf_user;

-- Verify setup
DO $$
BEGIN
    RAISE NOTICE 'EAF PostgreSQL initialization completed successfully';
    RAISE NOTICE 'Schema: eaf';
    RAISE NOTICE 'Extensions: uuid-ossp, pg_trgm';
    RAISE NOTICE 'Axon Framework tables created';
    RAISE NOTICE 'Permissions granted to eaf_user';
END $$;

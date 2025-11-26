-- V001__create_event_store.sql
-- Event Store schema for EAF Event Sourcing
-- AC: 1 (Event Persistence), 3 (Event Immutability), 4 (Flyway Migration)

-- Create dedicated schema for event sourcing
CREATE SCHEMA IF NOT EXISTS eaf_events;

-- Events table: immutable append-only log
CREATE TABLE eaf_events.events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    tenant_id       UUID NOT NULL,
    version         INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Optimistic locking: unique constraint prevents concurrent writes (AC: 2)
    -- Includes tenant_id to allow same aggregate_id across different tenants
    CONSTRAINT uq_aggregate_version UNIQUE (tenant_id, aggregate_id, version),
    -- Version must be positive (starts at 1)
    CONSTRAINT chk_version_positive CHECK (version > 0)
);

-- Indexes for efficient queries (AC: 1, 5)
-- Note: idx_events_aggregate is not needed as uq_aggregate_version unique constraint creates an index
CREATE INDEX idx_events_tenant ON eaf_events.events (tenant_id);
CREATE INDEX idx_events_aggregate_type ON eaf_events.events (aggregate_type);
CREATE INDEX idx_events_created_at ON eaf_events.events (created_at);

-- Snapshots table for aggregate state caching (future use)
CREATE TABLE eaf_events.snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(255) NOT NULL,
    version         INT NOT NULL,
    state           JSONB NOT NULL,
    tenant_id       UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Includes tenant_id to allow same aggregate_id across different tenants
    CONSTRAINT uq_snapshot_aggregate UNIQUE (tenant_id, aggregate_id)
);

CREATE INDEX idx_snapshots_tenant ON eaf_events.snapshots (tenant_id);

-- Create application role for RLS enforcement (non-superuser)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eaf_app') THEN
        CREATE ROLE eaf_app NOLOGIN;
    END IF;
END $$;

-- Grant schema usage to application role
GRANT USAGE ON SCHEMA eaf_events TO eaf_app;

-- Grant only SELECT and INSERT on events table (AC: 3 - no UPDATE/DELETE)
GRANT SELECT, INSERT ON eaf_events.events TO eaf_app;

-- Grant full CRUD on snapshots (snapshots can be updated and replaced)
GRANT SELECT, INSERT, UPDATE, DELETE ON eaf_events.snapshots TO eaf_app;

-- Revoke UPDATE and DELETE from PUBLIC on events table (AC: 3 - Event Immutability)
REVOKE UPDATE, DELETE ON eaf_events.events FROM PUBLIC;

-- Create trigger to prevent UPDATE/DELETE even by superusers (AC: 3)
CREATE OR REPLACE FUNCTION eaf_events.prevent_event_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Events are immutable. UPDATE and DELETE operations are not allowed on eaf_events.events table.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_event_update
    BEFORE UPDATE ON eaf_events.events
    FOR EACH ROW
    EXECUTE FUNCTION eaf_events.prevent_event_modification();

CREATE TRIGGER trg_prevent_event_delete
    BEFORE DELETE ON eaf_events.events
    FOR EACH ROW
    EXECUTE FUNCTION eaf_events.prevent_event_modification();

-- Add comments for documentation
COMMENT ON TABLE eaf_events.events IS 'Immutable event log for event sourcing. No UPDATE or DELETE allowed.';
COMMENT ON TABLE eaf_events.snapshots IS 'Aggregate state snapshots for performance optimization.';
COMMENT ON COLUMN eaf_events.events.version IS 'Aggregate version. Starts at 1, increments with each event.';
COMMENT ON COLUMN eaf_events.events.metadata IS 'Event metadata: tenant_id, user_id, correlation_id, timestamp';

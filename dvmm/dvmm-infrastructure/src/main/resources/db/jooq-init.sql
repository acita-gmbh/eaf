-- jooq-init.sql
-- Combined initialization script for jOOQ code generation
-- This script combines all Flyway migrations needed for jOOQ to generate type-safe code
-- NOTE: Keep in sync with eaf-eventsourcing migrations

-- ============================================================================
-- V001__create_event_store.sql content
-- ============================================================================

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

    CONSTRAINT uq_aggregate_version UNIQUE (tenant_id, aggregate_id, version),
    CONSTRAINT chk_version_positive CHECK (version > 0)
);

CREATE INDEX idx_events_tenant ON eaf_events.events (tenant_id);
CREATE INDEX idx_events_aggregate_type ON eaf_events.events (aggregate_type);
CREATE INDEX idx_events_created_at ON eaf_events.events (created_at);

-- Snapshots table for aggregate state caching
CREATE TABLE eaf_events.snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(255) NOT NULL,
    version         INT NOT NULL,
    state           JSONB NOT NULL,
    tenant_id       UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_snapshot_aggregate UNIQUE (tenant_id, aggregate_id)
);

CREATE INDEX idx_snapshots_tenant ON eaf_events.snapshots (tenant_id);

-- Create application role for RLS enforcement
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eaf_app') THEN
        CREATE ROLE eaf_app NOLOGIN;
    END IF;
END $$;

-- Grant schema usage to application role
GRANT USAGE ON SCHEMA eaf_events TO eaf_app;
GRANT SELECT, INSERT ON eaf_events.events TO eaf_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON eaf_events.snapshots TO eaf_app;

-- ============================================================================
-- V002__enable_rls.sql content
-- ============================================================================

-- Enable RLS on events table
ALTER TABLE eaf_events.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE eaf_events.snapshots ENABLE ROW LEVEL SECURITY;

-- Create RLS policies
CREATE POLICY tenant_isolation_events ON eaf_events.events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_snapshots ON eaf_events.snapshots
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users
ALTER TABLE eaf_events.events FORCE ROW LEVEL SECURITY;
ALTER TABLE eaf_events.snapshots FORCE ROW LEVEL SECURITY;

-- ============================================================================
-- V003__create_vm_requests_projection.sql content
-- ============================================================================

-- Projection table for VM requests read model
CREATE TABLE IF NOT EXISTS vm_requests_projection (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    requester_id    UUID NOT NULL,
    vm_name         VARCHAR(255) NOT NULL,
    cpu_cores       INT NOT NULL,
    memory_gb       INT NOT NULL,
    status          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_vm_requests_projection_tenant ON vm_requests_projection (tenant_id);
CREATE INDEX idx_vm_requests_projection_status ON vm_requests_projection (status);
CREATE INDEX idx_vm_requests_projection_requester ON vm_requests_projection (requester_id);
CREATE INDEX idx_vm_requests_projection_created ON vm_requests_projection (created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON vm_requests_projection TO eaf_app;

ALTER TABLE vm_requests_projection ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vm_requests_projection ON vm_requests_projection
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE vm_requests_projection FORCE ROW LEVEL SECURITY;

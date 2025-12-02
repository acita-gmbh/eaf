-- jooq-init.sql
-- Combined initialization script for jOOQ code generation
-- This script combines all Flyway migrations needed for jOOQ to generate type-safe code
-- NOTE: Keep in sync with eaf-eventsourcing migrations
--
-- IMPORTANT: This file uses jOOQ ignore tokens to skip PostgreSQL-specific statements
-- that are not supported by DDLDatabase (which uses H2 internally).
-- See: https://www.jooq.org/doc/latest/manual/code-generation/codegen-ddl/
--
-- Statements wrapped in [jooq ignore start/stop] are:
-- - RLS policies (ENABLE/FORCE ROW LEVEL SECURITY, CREATE POLICY)
-- - Permission grants (GRANT, REVOKE)
-- - Roles (CREATE ROLE)
-- - Triggers and functions (CREATE TRIGGER, CREATE FUNCTION)
-- - PL/pgSQL blocks (DO $$ ... $$)
-- - Comments (COMMENT ON)
--
-- These are runtime concerns that don't affect generated jOOQ code.

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

-- [jooq ignore start]
-- PostgreSQL-specific: Roles, Grants, Triggers, Functions (not needed for jOOQ code generation)

-- Create application role for RLS enforcement
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eaf_app') THEN
        CREATE ROLE eaf_app NOLOGIN;
    END IF;
END $$;

-- Grant schema usage to application role
GRANT USAGE ON SCHEMA eaf_events TO eaf_app;

-- Grant only SELECT and INSERT on events table (no UPDATE/DELETE for immutability)
GRANT SELECT, INSERT ON eaf_events.events TO eaf_app;

-- Grant full CRUD on snapshots (snapshots can be updated and replaced)
GRANT SELECT, INSERT, UPDATE, DELETE ON eaf_events.snapshots TO eaf_app;

-- Revoke UPDATE and DELETE from PUBLIC on events table (Event Immutability)
REVOKE UPDATE, DELETE ON eaf_events.events FROM PUBLIC;

-- Create trigger to prevent UPDATE/DELETE even by superusers
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

-- [jooq ignore stop]

-- ============================================================================
-- V003__create_vm_requests_projection.sql content
-- ============================================================================

-- Projection table for VM requests read model
-- Note: Explicit PUBLIC schema prefix ensures jOOQ generates code in the 'public' package
CREATE TABLE IF NOT EXISTS PUBLIC.vm_requests_projection (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    requester_id        UUID NOT NULL,
    requester_name      VARCHAR(255) NOT NULL,
    project_id          UUID NOT NULL,
    project_name        VARCHAR(255) NOT NULL,
    vm_name             VARCHAR(255) NOT NULL,
    size                VARCHAR(10) NOT NULL,
    cpu_cores           INT NOT NULL,
    memory_gb           INT NOT NULL,
    disk_gb             INT NOT NULL,
    justification       TEXT NOT NULL,
    status              VARCHAR(50) NOT NULL,
    approved_by         UUID,
    approved_by_name    VARCHAR(255),
    rejected_by         UUID,
    rejected_by_name    VARCHAR(255),
    rejection_reason    TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_vm_requests_projection_tenant ON PUBLIC.vm_requests_projection (tenant_id);
CREATE INDEX idx_vm_requests_projection_status ON PUBLIC.vm_requests_projection (status);
CREATE INDEX idx_vm_requests_projection_requester ON PUBLIC.vm_requests_projection (requester_id);
CREATE INDEX idx_vm_requests_projection_created ON PUBLIC.vm_requests_projection (created_at DESC);
CREATE INDEX idx_vm_requests_projection_project ON PUBLIC.vm_requests_projection (project_id);

-- [jooq ignore start]
-- PostgreSQL-specific: Grants, RLS, Comments (not needed for jOOQ code generation)

GRANT SELECT, INSERT, UPDATE, DELETE ON vm_requests_projection TO eaf_app;

ALTER TABLE vm_requests_projection ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vm_requests_projection ON vm_requests_projection
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE vm_requests_projection FORCE ROW LEVEL SECURITY;

-- Column comments for documentation
COMMENT ON COLUMN vm_requests_projection.id IS 'Unique identifier for the VM request';
COMMENT ON COLUMN vm_requests_projection.tenant_id IS 'Tenant identifier for multi-tenancy isolation';
COMMENT ON COLUMN vm_requests_projection.requester_id IS 'User ID who requested the VM';
COMMENT ON COLUMN vm_requests_projection.vm_name IS 'Requested name for the virtual machine';
COMMENT ON COLUMN vm_requests_projection.cpu_cores IS 'Number of CPU cores requested';
COMMENT ON COLUMN vm_requests_projection.memory_gb IS 'Amount of memory in GB requested';
COMMENT ON COLUMN vm_requests_projection.status IS 'Current status: PENDING, APPROVED, REJECTED, PROVISIONING, COMPLETED, FAILED';
COMMENT ON COLUMN vm_requests_projection.created_at IS 'Timestamp when the request was created';
COMMENT ON COLUMN vm_requests_projection.updated_at IS 'Timestamp when the request was last updated';
COMMENT ON COLUMN vm_requests_projection.version IS 'Optimistic locking version for concurrent updates';

-- [jooq ignore stop]

-- ============================================================================
-- V005__create_request_timeline_events.sql content
-- ============================================================================

-- Projection table for VM request timeline events
-- Story 2.8: Request Status Timeline
-- Note: Quoted uppercase identifiers required for H2 DDL compatibility (jOOQ DDLDatabase)
CREATE TABLE IF NOT EXISTS PUBLIC."REQUEST_TIMELINE_EVENTS" (
    "ID"              UUID PRIMARY KEY,
    "REQUEST_ID"      UUID NOT NULL,
    "TENANT_ID"       UUID NOT NULL,
    "EVENT_TYPE"      VARCHAR(50) NOT NULL,
    "ACTOR_ID"        UUID,
    "ACTOR_NAME"      VARCHAR(255),
    "DETAILS"         VARCHAR(4000),
    "OCCURRED_AT"     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS "IDX_TIMELINE_EVENTS_REQUEST" ON PUBLIC."REQUEST_TIMELINE_EVENTS" ("REQUEST_ID", "OCCURRED_AT");
CREATE INDEX IF NOT EXISTS "IDX_TIMELINE_EVENTS_TENANT" ON PUBLIC."REQUEST_TIMELINE_EVENTS" ("TENANT_ID");

-- [jooq ignore start]
-- PostgreSQL-specific: Grants, RLS, Comments (not needed for jOOQ code generation)

ALTER TABLE PUBLIC."REQUEST_TIMELINE_EVENTS" ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_timeline_events ON PUBLIC."REQUEST_TIMELINE_EVENTS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE PUBLIC."REQUEST_TIMELINE_EVENTS" FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON PUBLIC."REQUEST_TIMELINE_EVENTS" TO eaf_app;

COMMENT ON TABLE PUBLIC."REQUEST_TIMELINE_EVENTS" IS 'Projection table for VM request timeline events';
COMMENT ON COLUMN PUBLIC."REQUEST_TIMELINE_EVENTS"."EVENT_TYPE" IS 'Type: CREATED, APPROVED, REJECTED, CANCELLED, PROVISIONING_STARTED, VM_READY';
-- [jooq ignore stop]

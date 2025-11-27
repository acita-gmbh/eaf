-- jooq-init.sql (Test version)
-- Combined initialization script for integration tests
--
-- IMPORTANT: This file uses quoted uppercase table names to match jOOQ-generated code.
-- jOOQ uses H2's DDLDatabase which stores table names as UPPERCASE.
-- PostgreSQL requires exact case match with quoted identifiers.

-- ============================================================================
-- V001__create_event_store.sql content
-- ============================================================================

-- Create dedicated schema for event sourcing
CREATE SCHEMA IF NOT EXISTS eaf_events;

-- Events table: immutable append-only log
CREATE TABLE eaf_events."EVENTS" (
    "ID"              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "AGGREGATE_ID"    UUID NOT NULL,
    "AGGREGATE_TYPE"  VARCHAR(255) NOT NULL,
    "EVENT_TYPE"      VARCHAR(255) NOT NULL,
    "PAYLOAD"         JSONB NOT NULL,
    "METADATA"        JSONB NOT NULL DEFAULT '{}',
    "TENANT_ID"       UUID NOT NULL,
    "VERSION"         INT NOT NULL,
    "CREATED_AT"      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT "UQ_AGGREGATE_VERSION" UNIQUE ("TENANT_ID", "AGGREGATE_ID", "VERSION"),
    CONSTRAINT "CHK_VERSION_POSITIVE" CHECK ("VERSION" > 0)
);

CREATE INDEX "IDX_EVENTS_TENANT" ON eaf_events."EVENTS" ("TENANT_ID");
CREATE INDEX "IDX_EVENTS_AGGREGATE_TYPE" ON eaf_events."EVENTS" ("AGGREGATE_TYPE");
CREATE INDEX "IDX_EVENTS_CREATED_AT" ON eaf_events."EVENTS" ("CREATED_AT");

-- Snapshots table for aggregate state caching
CREATE TABLE eaf_events."SNAPSHOTS" (
    "ID"              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "AGGREGATE_ID"    UUID NOT NULL,
    "AGGREGATE_TYPE"  VARCHAR(255) NOT NULL,
    "VERSION"         INT NOT NULL,
    "STATE"           JSONB NOT NULL,
    "TENANT_ID"       UUID NOT NULL,
    "CREATED_AT"      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT "UQ_SNAPSHOT_AGGREGATE" UNIQUE ("TENANT_ID", "AGGREGATE_ID")
);

CREATE INDEX "IDX_SNAPSHOTS_TENANT" ON eaf_events."SNAPSHOTS" ("TENANT_ID");

-- Create application role for RLS enforcement
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eaf_app') THEN
        CREATE ROLE eaf_app NOLOGIN;
    END IF;
END $$;

-- Grant schema usage to application role
GRANT USAGE ON SCHEMA eaf_events TO eaf_app;
GRANT USAGE ON SCHEMA public TO eaf_app;

-- Grant only SELECT and INSERT on events table (no UPDATE/DELETE for immutability)
GRANT SELECT, INSERT ON eaf_events."EVENTS" TO eaf_app;

-- Grant full CRUD on snapshots (snapshots can be updated and replaced)
GRANT SELECT, INSERT, UPDATE, DELETE ON eaf_events."SNAPSHOTS" TO eaf_app;

-- ============================================================================
-- V003__create_vm_requests_projection.sql content
-- ============================================================================

-- Projection table for VM requests read model
-- Note: Quoted uppercase names to match jOOQ-generated code
CREATE TABLE IF NOT EXISTS public."VM_REQUESTS_PROJECTION" (
    "ID"              UUID PRIMARY KEY,
    "TENANT_ID"       UUID NOT NULL,
    "REQUESTER_ID"    UUID NOT NULL,
    "VM_NAME"         VARCHAR(255) NOT NULL,
    "CPU_CORES"       INT NOT NULL,
    "MEMORY_GB"       INT NOT NULL,
    "STATUS"          VARCHAR(50) NOT NULL,
    "CREATED_AT"      TIMESTAMPTZ NOT NULL,
    "UPDATED_AT"      TIMESTAMPTZ NOT NULL,
    "VERSION"         INT NOT NULL DEFAULT 1
);

CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_TENANT" ON public."VM_REQUESTS_PROJECTION" ("TENANT_ID");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_STATUS" ON public."VM_REQUESTS_PROJECTION" ("STATUS");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_REQUESTER" ON public."VM_REQUESTS_PROJECTION" ("REQUESTER_ID");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_CREATED" ON public."VM_REQUESTS_PROJECTION" ("CREATED_AT" DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON public."VM_REQUESTS_PROJECTION" TO eaf_app;

-- Enable RLS on all tables
ALTER TABLE eaf_events."EVENTS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE eaf_events."SNAPSHOTS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE public."VM_REQUESTS_PROJECTION" ENABLE ROW LEVEL SECURITY;

-- Create RLS policies
CREATE POLICY tenant_isolation_events ON eaf_events."EVENTS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_snapshots ON eaf_events."SNAPSHOTS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_vm_requests_projection ON public."VM_REQUESTS_PROJECTION"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users
ALTER TABLE eaf_events."EVENTS" FORCE ROW LEVEL SECURITY;
ALTER TABLE eaf_events."SNAPSHOTS" FORCE ROW LEVEL SECURITY;
ALTER TABLE public."VM_REQUESTS_PROJECTION" FORCE ROW LEVEL SECURITY;

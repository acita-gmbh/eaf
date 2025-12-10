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
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_snapshots ON eaf_events.snapshots
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users
ALTER TABLE eaf_events.events FORCE ROW LEVEL SECURITY;
ALTER TABLE eaf_events.snapshots FORCE ROW LEVEL SECURITY;

-- [jooq ignore stop]

-- ============================================================================
-- V003__create_vm_requests_projection.sql content
-- ============================================================================

-- Projection table for VM requests read model
-- Note: Quoted uppercase identifiers required for H2 DDL compatibility (jOOQ DDLDatabase)
CREATE TABLE IF NOT EXISTS PUBLIC."VM_REQUESTS_PROJECTION" (
    "ID"                  UUID PRIMARY KEY,
    "TENANT_ID"           UUID NOT NULL,
    "REQUESTER_ID"        UUID NOT NULL,
    "REQUESTER_NAME"      VARCHAR(255) NOT NULL,
    "REQUESTER_EMAIL"     VARCHAR(255),
    "REQUESTER_ROLE"      VARCHAR(100),
    "PROJECT_ID"          UUID NOT NULL,
    "PROJECT_NAME"        VARCHAR(255) NOT NULL,
    "VM_NAME"             VARCHAR(255) NOT NULL,
    "SIZE"                VARCHAR(10) NOT NULL,
    "CPU_CORES"           INT NOT NULL,
    "MEMORY_GB"           INT NOT NULL,
    "DISK_GB"             INT NOT NULL,
    "JUSTIFICATION"       TEXT NOT NULL,
    "STATUS"              VARCHAR(50) NOT NULL,
    "APPROVED_BY"         UUID,
    "APPROVED_BY_NAME"    VARCHAR(255),
    "REJECTED_BY"         UUID,
    "REJECTED_BY_NAME"    VARCHAR(255),
    "REJECTION_REASON"    TEXT,
    "CREATED_AT"          TIMESTAMPTZ NOT NULL,
    "UPDATED_AT"          TIMESTAMPTZ NOT NULL,
    "VERSION"             INT NOT NULL DEFAULT 1
);

CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_TENANT" ON PUBLIC."VM_REQUESTS_PROJECTION" ("TENANT_ID");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_STATUS" ON PUBLIC."VM_REQUESTS_PROJECTION" ("STATUS");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_REQUESTER" ON PUBLIC."VM_REQUESTS_PROJECTION" ("REQUESTER_ID");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_CREATED" ON PUBLIC."VM_REQUESTS_PROJECTION" ("CREATED_AT" DESC);
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_PROJECT" ON PUBLIC."VM_REQUESTS_PROJECTION" ("PROJECT_ID");

-- [jooq ignore start]
-- PostgreSQL-specific: Grants, RLS, Comments (not needed for jOOQ code generation)

GRANT SELECT, INSERT, UPDATE, DELETE ON "VM_REQUESTS_PROJECTION" TO eaf_app;

ALTER TABLE "VM_REQUESTS_PROJECTION" ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vm_requests_projection ON "VM_REQUESTS_PROJECTION"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "VM_REQUESTS_PROJECTION" FORCE ROW LEVEL SECURITY;

-- Column comments for documentation
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."ID" IS 'Unique identifier for the VM request';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."TENANT_ID" IS 'Tenant identifier for multi-tenancy isolation';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REQUESTER_ID" IS 'User ID who requested the VM';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."VM_NAME" IS 'Requested name for the virtual machine';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."CPU_CORES" IS 'Number of CPU cores requested';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."MEMORY_GB" IS 'Amount of memory in GB requested';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."STATUS" IS 'Current status: PENDING, APPROVED, REJECTED, PROVISIONING, COMPLETED, FAILED';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."CREATED_AT" IS 'Timestamp when the request was created';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."UPDATED_AT" IS 'Timestamp when the request was last updated';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."VERSION" IS 'Optimistic locking version for concurrent updates';

-- [jooq ignore stop]

-- ============================================================================
-- V005__create_request_timeline_events.sql content
-- ============================================================================

-- Projection table for VM request timeline events
-- Story 2.8: Request Status Timeline
-- Note: Quoted uppercase identifiers required for H2 DDL compatibility (jOOQ DDLDatabase)
CREATE TABLE IF NOT EXISTS PUBLIC."REQUEST_TIMELINE_EVENTS" (
    "ID"              UUID PRIMARY KEY,
    "REQUEST_ID"      UUID NOT NULL REFERENCES "VM_REQUESTS_PROJECTION"("ID") ON DELETE CASCADE,
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
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE PUBLIC."REQUEST_TIMELINE_EVENTS" FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON PUBLIC."REQUEST_TIMELINE_EVENTS" TO eaf_app;

COMMENT ON TABLE PUBLIC."REQUEST_TIMELINE_EVENTS" IS 'Projection table for VM request timeline events';
COMMENT ON COLUMN PUBLIC."REQUEST_TIMELINE_EVENTS"."EVENT_TYPE" IS 'Type: CREATED, APPROVED, REJECTED, CANCELLED, PROVISIONING_STARTED, VM_READY';
-- [jooq ignore stop]

-- ============================================================================
-- V008__vmware_configurations.sql content
-- Story 3.1: VMware Connection Configuration
-- ============================================================================

-- VMware vCenter configuration per tenant (one config per tenant)
CREATE TABLE IF NOT EXISTS PUBLIC."VMWARE_CONFIGURATIONS" (
    "ID"                 UUID PRIMARY KEY,
    "TENANT_ID"          UUID NOT NULL UNIQUE,
    "VCENTER_URL"        VARCHAR(500) NOT NULL,
    "USERNAME"           VARCHAR(255) NOT NULL,
    "PASSWORD_ENCRYPTED" VARBINARY(4096) NOT NULL,  -- H2 compatible (PostgreSQL uses BYTEA)
    "DATACENTER_NAME"    VARCHAR(255) NOT NULL,
    "CLUSTER_NAME"       VARCHAR(255) NOT NULL,
    "DATASTORE_NAME"     VARCHAR(255) NOT NULL,
    "NETWORK_NAME"       VARCHAR(255) NOT NULL,
    "TEMPLATE_NAME"      VARCHAR(255) NOT NULL DEFAULT 'ubuntu-22.04-template',
    "FOLDER_PATH"        VARCHAR(500),
    "VERIFIED_AT"        TIMESTAMP WITH TIME ZONE,
    "CREATED_AT"         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "UPDATED_AT"         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    "CREATED_BY"         UUID NOT NULL,
    "UPDATED_BY"         UUID NOT NULL,
    "VERSION"            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS "IDX_VMWARE_CONFIGS_TENANT" ON PUBLIC."VMWARE_CONFIGURATIONS"("TENANT_ID");

-- [jooq ignore start]
-- PostgreSQL-specific: RLS, Grants, Comments (not needed for jOOQ code generation)

ALTER TABLE "VMWARE_CONFIGURATIONS" ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vmware_configs ON "VMWARE_CONFIGURATIONS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "VMWARE_CONFIGURATIONS" FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON "VMWARE_CONFIGURATIONS" TO eaf_app;

COMMENT ON TABLE "VMWARE_CONFIGURATIONS" IS 'VMware vCenter connection configuration per tenant';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."PASSWORD_ENCRYPTED" IS 'AES-256 encrypted password';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."VERSION" IS 'Optimistic locking version';
-- [jooq ignore stop]

-- ============================================================================
-- V009__create_provisioning_progress_table.sql content + V010 (stage_timestamps)
-- Story 3.5: Provisioning Progress Tracking
-- ============================================================================

CREATE TABLE IF NOT EXISTS PUBLIC."PROVISIONING_PROGRESS" (
    "VM_REQUEST_ID"   UUID PRIMARY KEY,
    "STAGE"           VARCHAR(255) NOT NULL,
    "DETAILS"         VARCHAR(4000) NOT NULL,
    "STARTED_AT"      TIMESTAMP WITH TIME ZONE NOT NULL,
    "UPDATED_AT"      TIMESTAMP WITH TIME ZONE NOT NULL,
    "TENANT_ID"       UUID NOT NULL,
    -- Note: JSONB in PostgreSQL (V010 migration), VARCHAR here for H2 DDLDatabase compatibility.
    -- H2 doesn't support JSONB natively; jOOQ generates String type for both, so functionally equivalent.
    -- The adapter serializes/deserializes JSON via Jackson ObjectMapper.
    "STAGE_TIMESTAMPS" VARCHAR(4000) NOT NULL DEFAULT '{}',
    CONSTRAINT "FK_PROVISIONING_PROGRESS_REQUEST"
        FOREIGN KEY ("VM_REQUEST_ID") REFERENCES PUBLIC."VM_REQUESTS_PROJECTION"("ID") ON DELETE CASCADE
);

-- Index for tenant queries (RLS performance)
CREATE INDEX IF NOT EXISTS "IDX_PROVISIONING_PROGRESS_TENANT" ON PUBLIC."PROVISIONING_PROGRESS"("TENANT_ID");

-- [jooq ignore start]
ALTER TABLE "PROVISIONING_PROGRESS" ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_progress ON "PROVISIONING_PROGRESS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "PROVISIONING_PROGRESS" FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON "PROVISIONING_PROGRESS" TO eaf_app;
-- [jooq ignore stop]
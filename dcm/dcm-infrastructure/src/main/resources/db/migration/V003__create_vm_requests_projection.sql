-- V003__create_vm_requests_projection.sql
-- Projection table for VM requests read model
-- Part of Story 1.8: jOOQ Projection Base

-- Create projection table in public schema (not eaf_events schema)
-- Projections are denormalized read models optimized for queries
CREATE TABLE IF NOT EXISTS "VM_REQUESTS_PROJECTION" (
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

-- Index for tenant-based queries (RLS will filter, but index helps performance)
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_TENANT" ON "VM_REQUESTS_PROJECTION" ("TENANT_ID");

-- Index for common query patterns
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_STATUS" ON "VM_REQUESTS_PROJECTION" ("STATUS");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_REQUESTER" ON "VM_REQUESTS_PROJECTION" ("REQUESTER_ID");
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_CREATED" ON "VM_REQUESTS_PROJECTION" ("CREATED_AT" DESC);

-- [jooq ignore start]
-- PostgreSQL-specific: Grants, RLS, Comments (not needed for jOOQ code generation)

-- Grant permissions to application role
GRANT SELECT, INSERT, UPDATE, DELETE ON "VM_REQUESTS_PROJECTION" TO eaf_app;

-- Enable RLS for tenant isolation
ALTER TABLE "VM_REQUESTS_PROJECTION" ENABLE ROW LEVEL SECURITY;

-- Create RLS policy using same pattern as event store (fail-closed)
-- WITH CHECK ensures writes are also tenant-isolated (prevents cross-tenant data injection)
CREATE POLICY tenant_isolation_vm_requests_projection ON "VM_REQUESTS_PROJECTION"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users including table owner
ALTER TABLE "VM_REQUESTS_PROJECTION" FORCE ROW LEVEL SECURITY;

-- Add comments
COMMENT ON TABLE "VM_REQUESTS_PROJECTION" IS 'Read model for VM requests. Updated asynchronously from events.';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."VERSION" IS 'Optimistic locking version for projection updates.';
-- [jooq ignore stop]

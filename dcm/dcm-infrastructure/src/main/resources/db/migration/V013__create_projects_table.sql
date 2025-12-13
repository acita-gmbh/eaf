-- V013__create_projects_table.sql
-- Projection table for Projects read model
-- Part of Story 4.1: Project Aggregate

-- Create projection table for project list/details queries
CREATE TABLE IF NOT EXISTS "PROJECTS" (
    "ID"              UUID PRIMARY KEY,
    "TENANT_ID"       UUID NOT NULL,
    "NAME"            VARCHAR(100) NOT NULL,
    "DESCRIPTION"     TEXT,
    "STATUS"          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "CREATED_BY"      UUID NOT NULL,
    "CREATED_AT"      TIMESTAMPTZ NOT NULL,
    "UPDATED_AT"      TIMESTAMPTZ NOT NULL,
    "VERSION"         INT NOT NULL DEFAULT 1
);

-- Index for tenant-based queries (RLS will filter, but index helps performance)
CREATE INDEX "IDX_PROJECTS_TENANT" ON "PROJECTS" ("TENANT_ID");

-- Index for status filtering
CREATE INDEX "IDX_PROJECTS_STATUS" ON "PROJECTS" ("STATUS");

-- Index for sorting by creation date
CREATE INDEX "IDX_PROJECTS_CREATED" ON "PROJECTS" ("CREATED_AT" DESC);

-- Case-insensitive unique constraint on name within tenant
-- Uses LOWER() to enforce: "Alpha" = "ALPHA" = "alpha" (AC-4.1.3)
CREATE UNIQUE INDEX "IDX_PROJECTS_NAME_UNIQUE_LOWER"
    ON "PROJECTS" ("TENANT_ID", LOWER("NAME"));

-- [jooq ignore start]
-- PostgreSQL-specific: Grants, RLS, Comments (not needed for jOOQ code generation)

-- Grant permissions to application role
GRANT SELECT, INSERT, UPDATE, DELETE ON "PROJECTS" TO eaf_app;

-- Enable RLS for tenant isolation
ALTER TABLE "PROJECTS" ENABLE ROW LEVEL SECURITY;

-- Create RLS policy (fail-closed)
-- USING for reads, WITH CHECK for writes
CREATE POLICY tenant_isolation_projects ON "PROJECTS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users including table owner
ALTER TABLE "PROJECTS" FORCE ROW LEVEL SECURITY;

-- Add comments
COMMENT ON TABLE "PROJECTS" IS 'Read model for projects. Projects organize VMs and control access via membership.';
COMMENT ON COLUMN "PROJECTS"."STATUS" IS 'ACTIVE or ARCHIVED';
COMMENT ON COLUMN "PROJECTS"."VERSION" IS 'Optimistic locking version for projection updates.';
-- [jooq ignore stop]

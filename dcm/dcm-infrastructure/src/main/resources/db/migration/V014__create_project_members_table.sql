-- V014__create_project_members_table.sql
-- Projection table for Project Members read model
-- Part of Story 4.1: Project Aggregate

-- Create projection table for project membership queries
CREATE TABLE IF NOT EXISTS "PROJECT_MEMBERS" (
    "ID"              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "PROJECT_ID"      UUID NOT NULL REFERENCES "PROJECTS"("ID") ON DELETE CASCADE,
    "TENANT_ID"       UUID NOT NULL,
    "USER_ID"         UUID NOT NULL,
    "ROLE"            VARCHAR(20) NOT NULL,
    "ASSIGNED_BY"     UUID NOT NULL,
    "ASSIGNED_AT"     TIMESTAMPTZ NOT NULL,
    "VERSION"         INT NOT NULL DEFAULT 1
);

-- Index for project-based queries (get all members of a project)
CREATE INDEX "IDX_PROJECT_MEMBERS_PROJECT" ON "PROJECT_MEMBERS" ("PROJECT_ID");

-- Index for user-based queries (get all projects for a user)
CREATE INDEX "IDX_PROJECT_MEMBERS_USER" ON "PROJECT_MEMBERS" ("USER_ID");

-- Index for tenant isolation
CREATE INDEX "IDX_PROJECT_MEMBERS_TENANT" ON "PROJECT_MEMBERS" ("TENANT_ID");

-- Unique constraint: user can only have one membership per project
CREATE UNIQUE INDEX "IDX_PROJECT_MEMBERS_UNIQUE"
    ON "PROJECT_MEMBERS" ("PROJECT_ID", "USER_ID");

-- [jooq ignore start]
-- PostgreSQL-specific: Grants, RLS, Comments (not needed for jOOQ code generation)

-- Grant permissions to application role
GRANT SELECT, INSERT, UPDATE, DELETE ON "PROJECT_MEMBERS" TO eaf_app;

-- Enable RLS for tenant isolation
ALTER TABLE "PROJECT_MEMBERS" ENABLE ROW LEVEL SECURITY;

-- Create RLS policy (fail-closed)
-- USING for reads, WITH CHECK for writes
CREATE POLICY tenant_isolation_project_members ON "PROJECT_MEMBERS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users including table owner
ALTER TABLE "PROJECT_MEMBERS" FORCE ROW LEVEL SECURITY;

-- Add comments
COMMENT ON TABLE "PROJECT_MEMBERS" IS 'Read model for project membership. Links users to projects with roles.';
COMMENT ON COLUMN "PROJECT_MEMBERS"."ROLE" IS 'MEMBER or PROJECT_ADMIN';
COMMENT ON COLUMN "PROJECT_MEMBERS"."VERSION" IS 'Optimistic locking version for projection updates.';
-- [jooq ignore stop]

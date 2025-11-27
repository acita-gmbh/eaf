-- V003__create_vm_requests_projection.sql
-- Projection table for VM requests read model
-- Part of Story 1.8: jOOQ Projection Base

-- Create projection table in public schema (not eaf_events schema)
-- Projections are denormalized read models optimized for queries
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

-- Index for tenant-based queries (RLS will filter, but index helps performance)
CREATE INDEX idx_vm_requests_projection_tenant ON vm_requests_projection (tenant_id);

-- Index for common query patterns
CREATE INDEX idx_vm_requests_projection_status ON vm_requests_projection (status);
CREATE INDEX idx_vm_requests_projection_requester ON vm_requests_projection (requester_id);
CREATE INDEX idx_vm_requests_projection_created ON vm_requests_projection (created_at DESC);

-- Grant permissions to application role
GRANT SELECT, INSERT, UPDATE, DELETE ON vm_requests_projection TO eaf_app;

-- Enable RLS for tenant isolation
ALTER TABLE vm_requests_projection ENABLE ROW LEVEL SECURITY;

-- Create RLS policy using same pattern as event store (fail-closed)
CREATE POLICY tenant_isolation_vm_requests_projection ON vm_requests_projection
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users including table owner
ALTER TABLE vm_requests_projection FORCE ROW LEVEL SECURITY;

-- Add comments
COMMENT ON TABLE vm_requests_projection IS 'Read model for VM requests. Updated asynchronously from events.';
COMMENT ON COLUMN vm_requests_projection.version IS 'Optimistic locking version for projection updates.';

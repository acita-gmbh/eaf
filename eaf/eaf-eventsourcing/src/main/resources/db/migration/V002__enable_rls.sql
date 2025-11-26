-- V002__enable_rls.sql
-- Row-Level Security (RLS) for multi-tenant isolation
-- AC: 1 (RLS enabled on tenant-scoped tables), 3 (RLS policy definition),
--     5 (Flyway migration), 6 (Superuser bypass disabled)

-- Enable RLS on events table
ALTER TABLE eaf_events.events ENABLE ROW LEVEL SECURITY;

-- Enable RLS on snapshots table
ALTER TABLE eaf_events.snapshots ENABLE ROW LEVEL SECURITY;

-- Create RLS policy for events table
-- Uses current_setting with 'true' parameter for missing_ok (returns empty string instead of error)
-- Uses NULLIF to convert empty string to NULL before casting to uuid
-- This implements fail-closed semantics: tenant_id = NULL::uuid matches no rows (SQL NULL semantics)
CREATE POLICY tenant_isolation_events ON eaf_events.events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Create RLS policy for snapshots table
CREATE POLICY tenant_isolation_snapshots ON eaf_events.snapshots
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Force RLS for ALL users including table owner (AC: 6)
-- This ensures the application role cannot bypass RLS
ALTER TABLE eaf_events.events FORCE ROW LEVEL SECURITY;
ALTER TABLE eaf_events.snapshots FORCE ROW LEVEL SECURITY;

-- Add comments for documentation
COMMENT ON POLICY tenant_isolation_events ON eaf_events.events IS
    'Enforces tenant isolation. Returns zero rows when app.tenant_id session variable is not set (fail-closed).';
COMMENT ON POLICY tenant_isolation_snapshots ON eaf_events.snapshots IS
    'Enforces tenant isolation. Returns zero rows when app.tenant_id session variable is not set (fail-closed).';

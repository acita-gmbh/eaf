-- V003__fix_rls_empty_string_handling.sql
-- Fix RLS policy to handle empty string from set_config(..., NULL, ...)
--
-- Issue: PostgreSQL set_config(name, NULL, false) sets the value to empty string ''
-- rather than true NULL. The previous RLS policy used:
--   tenant_id = current_setting('app.tenant_id', true)::uuid
-- which fails with "invalid input syntax for type uuid" when value is ''.
--
-- Fix: Use NULLIF to convert empty string to NULL before casting:
--   tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
-- This ensures both unset (NULL) and cleared ('') states result in NULL::uuid,
-- which matches no rows (SQL NULL semantics = fail-closed).

-- Drop and recreate events policy with NULLIF fix
DROP POLICY IF EXISTS tenant_isolation_events ON eaf_events.events;
CREATE POLICY tenant_isolation_events ON eaf_events.events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Drop and recreate snapshots policy with NULLIF fix
DROP POLICY IF EXISTS tenant_isolation_snapshots ON eaf_events.snapshots;
CREATE POLICY tenant_isolation_snapshots ON eaf_events.snapshots
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Update comments
COMMENT ON POLICY tenant_isolation_events ON eaf_events.events IS
    'Enforces tenant isolation. Uses NULLIF to handle empty string from cleared context. Returns zero rows when app.tenant_id is not set or empty (fail-closed).';
COMMENT ON POLICY tenant_isolation_snapshots ON eaf_events.snapshots IS
    'Enforces tenant isolation. Uses NULLIF to handle empty string from cleared context. Returns zero rows when app.tenant_id is not set or empty (fail-closed).';

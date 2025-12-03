-- V007__fix_rls_with_check.sql
-- SECURITY FIX: Add WITH CHECK clause to all RLS policies
--
-- Issue: RLS policies using only USING clause filter reads but allow writes
-- to any tenant_id value, enabling cross-tenant data injection.
--
-- Per CLAUDE.md: "CRITICAL: Always include WITH CHECK in RLS policies.
-- Without it, RLS only filters reads but allows writes to any tenant,
-- enabling cross-tenant data injection."
--
-- Affected tables:
-- 1. vm_requests_projection (Story 1.8)
-- 2. eaf_events.events (Story 1.2) - less critical due to immutability trigger
-- 3. eaf_events.snapshots (Story 1.2)

-- ============================================================================
-- Fix vm_requests_projection RLS policy
-- ============================================================================

-- Drop existing policy (missing WITH CHECK)
DROP POLICY IF EXISTS tenant_isolation_vm_requests_projection ON vm_requests_projection;

-- Recreate with both USING (reads) and WITH CHECK (writes)
CREATE POLICY tenant_isolation_vm_requests_projection ON vm_requests_projection
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON POLICY tenant_isolation_vm_requests_projection ON vm_requests_projection IS
    'Enforces tenant isolation for reads (USING) and writes (WITH CHECK). Returns zero rows when app.tenant_id is not set (fail-closed).';

-- ============================================================================
-- Fix eaf_events.events RLS policy
-- ============================================================================

-- Drop existing policy (missing WITH CHECK)
DROP POLICY IF EXISTS tenant_isolation_events ON eaf_events.events;

-- Recreate with both USING (reads) and WITH CHECK (writes)
-- Note: The events table also has an immutability trigger preventing UPDATE/DELETE,
-- but WITH CHECK provides defense-in-depth for INSERT operations.
CREATE POLICY tenant_isolation_events ON eaf_events.events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON POLICY tenant_isolation_events ON eaf_events.events IS
    'Enforces tenant isolation for reads (USING) and writes (WITH CHECK). Returns zero rows when app.tenant_id is not set (fail-closed).';

-- ============================================================================
-- Fix eaf_events.snapshots RLS policy
-- ============================================================================

-- Drop existing policy (missing WITH CHECK)
DROP POLICY IF EXISTS tenant_isolation_snapshots ON eaf_events.snapshots;

-- Recreate with both USING (reads) and WITH CHECK (writes)
CREATE POLICY tenant_isolation_snapshots ON eaf_events.snapshots
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON POLICY tenant_isolation_snapshots ON eaf_events.snapshots IS
    'Enforces tenant isolation for reads (USING) and writes (WITH CHECK). Returns zero rows when app.tenant_id is not set (fail-closed).';

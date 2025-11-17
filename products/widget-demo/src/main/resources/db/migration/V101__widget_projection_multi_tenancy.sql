-- ============================================================================
-- Widget Projection Multi-Tenancy Enhancement (Story 4.4)
-- ============================================================================
-- Adds tenant_id column to widget_projection table for Row-Level Security.
-- This column enables Layer 3 tenant isolation at the database level.
--
-- Prerequisites:
-- - V100__widget_projection.sql (creates widget_projection table)
-- - Story 4.1: TenantContext (ThreadLocal tenant management)
-- - Story 4.2: TenantContextFilter (Layer 1 - JWT extraction)
-- - Story 4.3: TenantValidationInterceptor (Layer 2 - Service validation)
--
-- Layer 3 Defense:
-- - RLS policies will enforce tenant_id = current_setting('app.tenant_id')
-- - Even SQL injection cannot bypass database-level isolation
-- - Provides final defense if application layers fail
-- ============================================================================

-- Add tenant_id column to widget_projection
-- NOT NULL enforces that all widgets MUST belong to a tenant
ALTER TABLE widget_projection
    ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'default-tenant';

-- Remove default after column exists (future inserts MUST provide tenant_id)
ALTER TABLE widget_projection
    ALTER COLUMN tenant_id DROP DEFAULT;

-- Create index for tenant_id queries
-- BTREE index (not BRIN) for efficient tenant filtering in small-medium datasets
-- Story 4.4 will add BRIN if performance testing shows benefit for large datasets
CREATE INDEX idx_widget_projection_tenant_id ON widget_projection (tenant_id);

-- Composite index for common query pattern: tenant + time-based queries
CREATE INDEX idx_widget_projection_tenant_created ON widget_projection (tenant_id, created_at DESC);

-- ============================================================================
-- Enable Row-Level Security (AC1)
-- ============================================================================

-- Enable RLS on widget_projection table
-- Once enabled, all queries must satisfy RLS policies
ALTER TABLE widget_projection ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- Create RLS Policy for Tenant Isolation (AC2)
-- ============================================================================

-- Create tenant isolation policy using helper function from V004
-- Policy name: tenant_isolation (standard across all tables)
-- Applies to: ALL operations (SELECT, INSERT, UPDATE, DELETE)
-- Enforcement: tenant_id must match session variable app.tenant_id
CREATE POLICY tenant_isolation ON widget_projection
    FOR ALL
    USING (tenant_id = get_current_tenant_id());

-- ============================================================================
-- Verify RLS Configuration
-- ============================================================================

-- Verify RLS is enabled (should return 'f' for relforcerowsecurity = false initially)
-- After ALTER TABLE ENABLE, relrowsecurity should be 't'
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_tables
        WHERE tablename = 'widget_projection'
        AND rowsecurity = true
    ) THEN
        RAISE EXCEPTION 'RLS not enabled on widget_projection';
    END IF;
END $$;

-- Verify policy exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'widget_projection'
        AND policyname = 'tenant_isolation'
    ) THEN
        RAISE EXCEPTION 'RLS policy tenant_isolation not found on widget_projection';
    END IF;
END $$;

-- ============================================================================
-- Comments for Documentation
-- ============================================================================

COMMENT ON COLUMN widget_projection.tenant_id IS 'Tenant identifier for multi-tenant isolation (Story 4.4). Enforced by RLS policies.';

-- ============================================================================
-- Post-Migration Validation
-- ============================================================================
-- Run: SELECT * FROM pg_policies WHERE tablename = 'widget_projection';
-- Expected: 1 row with policyname='tenant_isolation'
--
-- Run: SELECT relrowsecurity FROM pg_class WHERE relname = 'widget_projection';
-- Expected: true
-- ============================================================================

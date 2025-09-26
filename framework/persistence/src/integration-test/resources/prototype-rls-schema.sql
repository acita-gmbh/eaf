-- =====================================================
-- PROTOTYPE: Row-Level Security (RLS) Schema
-- =====================================================
-- Purpose: Validate RLS + Axon + Connection Pooling integration
-- Story: 4.3 - Implement Layer 3 (Database Layer)
-- Risk Mitigation: SEC-001, SEC-002, TECH-001
-- =====================================================

-- Drop existing test tables if they exist (idempotent)
DROP TABLE IF EXISTS prototype_widget_projection CASCADE;
DROP TABLE IF EXISTS prototype_domain_event_entry CASCADE;

-- =====================================================
-- Test Event Store Table (Minimal Axon Event Store)
-- =====================================================
CREATE TABLE prototype_domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    payload BYTEA NOT NULL,
    meta_data BYTEA,
    tenant_id UUID NOT NULL,  -- Tenant isolation column
    UNIQUE (aggregate_identifier, sequence_number)
);

-- Create indexes including tenant_id (required for RLS performance)
CREATE INDEX idx_prototype_event_tenant_agg ON prototype_domain_event_entry(tenant_id, aggregate_identifier);
CREATE INDEX idx_prototype_event_timestamp ON prototype_domain_event_entry(timestamp);

-- =====================================================
-- Test Projection Table (Minimal Read Model)
-- =====================================================
CREATE TABLE prototype_widget_projection (
    widget_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create index including tenant_id (required for RLS performance)
CREATE INDEX idx_prototype_projection_tenant ON prototype_widget_projection(tenant_id, status);

-- =====================================================
-- Enable Row-Level Security (RLS)
-- =====================================================
ALTER TABLE prototype_domain_event_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE prototype_widget_projection ENABLE ROW LEVEL SECURITY;

-- =====================================================
-- RLS Policies for Event Store
-- =====================================================

-- Policy: SELECT - Only return rows for current tenant
CREATE POLICY prototype_event_select_policy ON prototype_domain_event_entry
    FOR SELECT
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- Policy: INSERT - Only allow inserts for current tenant
CREATE POLICY prototype_event_insert_policy ON prototype_domain_event_entry
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant')::UUID);

-- Policy: UPDATE - Only allow updates for current tenant
CREATE POLICY prototype_event_update_policy ON prototype_domain_event_entry
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant')::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant')::UUID);

-- Policy: DELETE - Only allow deletes for current tenant
CREATE POLICY prototype_event_delete_policy ON prototype_domain_event_entry
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- =====================================================
-- RLS Policies for Projection Table
-- =====================================================

-- Policy: SELECT - Only return rows for current tenant
CREATE POLICY prototype_projection_select_policy ON prototype_widget_projection
    FOR SELECT
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- Policy: INSERT - Only allow inserts for current tenant
CREATE POLICY prototype_projection_insert_policy ON prototype_widget_projection
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant')::UUID);

-- Policy: UPDATE - Only allow updates for current tenant
CREATE POLICY prototype_projection_update_policy ON prototype_widget_projection
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant')::UUID)
    WITH CHECK (tenant_id = current_setting('app.current_tenant')::UUID);

-- Policy: DELETE - Only allow deletes for current tenant
CREATE POLICY prototype_projection_delete_policy ON prototype_widget_projection
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- =====================================================
-- Validation: Verify RLS is Enabled
-- =====================================================

-- Query to verify RLS status (for test assertions)
-- SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 'prototype_%';

-- Query to verify policies exist (for test assertions)
-- SELECT schemaname, tablename, policyname, cmd, qual FROM pg_policies WHERE tablename LIKE 'prototype_%';

-- =====================================================
-- Test Data Setup
-- =====================================================

-- Note: Test data should be inserted WITH tenant session variable set
-- This validates that RLS policies work as expected

-- Example test data for Tenant A (UUID: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa)
-- SET LOCAL app.current_tenant = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
-- INSERT INTO prototype_widget_projection VALUES (
--     'widget-a1'::UUID,
--     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::UUID,
--     'Widget A1',
--     'ACTIVE',
--     NOW(),
--     NOW()
-- );

-- Example test data for Tenant B (UUID: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb)
-- SET LOCAL app.current_tenant = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
-- INSERT INTO prototype_widget_projection VALUES (
--     'widget-b1'::UUID,
--     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::UUID,
--     'Widget B1',
--     'ACTIVE',
--     NOW(),
--     NOW()
-- );

-- =====================================================
-- Security Validation Queries
-- =====================================================

-- Query 1: Verify tenant isolation (should return 0 rows without session variable)
-- SELECT * FROM prototype_widget_projection; -- Should return 0 rows

-- Query 2: Verify tenant isolation (should return only current tenant's rows)
-- SET LOCAL app.current_tenant = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
-- SELECT * FROM prototype_widget_projection; -- Should return only Tenant A rows

-- Query 3: Verify RLS blocks cross-tenant access
-- SET LOCAL app.current_tenant = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
-- SELECT * FROM prototype_widget_projection WHERE tenant_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::UUID;
-- -- Should return 0 rows (RLS blocks access to Tenant B data)

-- =====================================================
-- Performance Analysis Queries
-- =====================================================

-- EXPLAIN ANALYZE to verify RLS doesn't degrade performance significantly
-- SET LOCAL app.current_tenant = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
-- EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM prototype_widget_projection WHERE status = 'ACTIVE';

-- =====================================================
-- Cleanup (for test teardown)
-- =====================================================
-- DROP TABLE IF EXISTS prototype_widget_projection CASCADE;
-- DROP TABLE IF EXISTS prototype_domain_event_entry CASCADE;
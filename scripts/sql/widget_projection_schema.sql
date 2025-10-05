CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS widget_projection (
    widget_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    value NUMERIC(19, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_widget_projection_tenant_id
    ON widget_projection (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_widget_projection_category
    ON widget_projection (tenant_id, category, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_widget_projection_value_desc
    ON widget_projection (tenant_id, value DESC);

CREATE INDEX IF NOT EXISTS idx_widget_projection_created_at
    ON widget_projection (tenant_id, created_at DESC);

ALTER TABLE widget_projection
    ALTER COLUMN metadata SET DEFAULT '{}'::jsonb;

ALTER TABLE widget_projection ENABLE ROW LEVEL SECURITY;

-- Tenant isolation policies rely on CURRENT_SETTING('app.current_tenant').
-- Application code is responsible for setting the tenant context before queries.
CREATE POLICY widget_projection_tenant_isolation_select
    ON widget_projection FOR SELECT
    USING (tenant_id::text = current_setting('app.current_tenant', true));

CREATE POLICY widget_projection_tenant_isolation_modify
    ON widget_projection FOR ALL
    USING (tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true));

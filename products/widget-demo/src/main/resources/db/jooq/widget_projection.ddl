CREATE TABLE widget_projection (
    widget_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    "value" NUMERIC(19, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    metadata CLOB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_widget_projection_tenant_id
    ON widget_projection (tenant_id, created_at DESC);

CREATE INDEX idx_widget_projection_category
    ON widget_projection (tenant_id, category, created_at DESC);

CREATE INDEX idx_widget_projection_value_desc
    ON widget_projection (tenant_id, "value" DESC);

CREATE INDEX idx_widget_projection_created_at
    ON widget_projection (tenant_id, created_at DESC);

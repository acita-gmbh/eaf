-- ============================================================================
-- Widget Projection Table (Story 2.7)
-- ============================================================================
-- Read model projection for Widget aggregates.
-- Populated by WidgetProjectionEventHandler from domain events.
--
-- Table Design:
-- - Denormalized for efficient queries
-- - No foreign keys (projection is eventually consistent)
-- - BRIN indexes for time-based queries
-- - Supports multi-tenancy (tenant_id added in Story 4.4)
-- ============================================================================

CREATE TABLE widget_projection (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(100),
    description TEXT,
    value DECIMAL(10,2),
    tags JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- Indexes for Efficient Querying
-- ============================================================================

-- Time-based queries (BRIN for range scans)
CREATE INDEX idx_widget_projection_created_at ON widget_projection USING BRIN (created_at);

-- Category filtering
CREATE INDEX idx_widget_projection_category ON widget_projection (category);

-- Value-based sorting
CREATE INDEX idx_widget_projection_value_desc ON widget_projection (value DESC NULLS LAST);

-- Tenant isolation index (placeholder for Story 4.4)
-- Multi-tenancy will add: tenant_id VARCHAR(255) NOT NULL
-- And index: CREATE INDEX idx_widget_projection_tenant_id ON widget_projection (tenant_id);

-- ============================================================================
-- Comments for Documentation
-- ============================================================================

COMMENT ON TABLE widget_projection IS 'Read model projection for Widget aggregates (Story 2.7)';
COMMENT ON COLUMN widget_projection.id IS 'Widget aggregate ID (matches WidgetId value object)';
COMMENT ON COLUMN widget_projection.name IS 'Widget display name';
COMMENT ON COLUMN widget_projection.published IS 'Publication status (false=draft, true=published)';
COMMENT ON COLUMN widget_projection.created_at IS 'Widget creation timestamp (from WidgetCreatedEvent)';
COMMENT ON COLUMN widget_projection.updated_at IS 'Last update timestamp (from events)';

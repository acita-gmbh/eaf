-- Widget Read Model (Projection)
-- Product-specific migration V100+ (Framework uses V001-V099)
--
-- Purpose: Create widget_view projection table for jOOQ type-safe queries
-- Architecture: CQRS read model materialized from WidgetCreatedEvent, WidgetPublishedEvent

CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Performance indexes for common query patterns
CREATE INDEX idx_widget_created ON widget_view (created_at DESC);
CREATE INDEX idx_widget_published ON widget_view (published);

COMMENT ON TABLE widget_view IS 'Widget projection table for read queries (CQRS read model)';
COMMENT ON COLUMN widget_view.id IS 'Widget aggregate identifier (UUID)';
COMMENT ON COLUMN widget_view.name IS 'Widget display name';
COMMENT ON COLUMN widget_view.published IS 'Publication status (true = publicly visible)';
COMMENT ON COLUMN widget_view.created_at IS 'Widget creation timestamp';
COMMENT ON COLUMN widget_view.updated_at IS 'Last update timestamp';

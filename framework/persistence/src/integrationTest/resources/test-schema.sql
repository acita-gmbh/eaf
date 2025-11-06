-- Test schema for jOOQ integration tests
-- Creates widget_view projection table for Testcontainers PostgreSQL

CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_widget_created ON widget_view (created_at DESC);
CREATE INDEX idx_widget_published ON widget_view (published);

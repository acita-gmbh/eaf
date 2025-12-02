-- V005: Create REQUEST_TIMELINE_EVENTS projection table
-- Stores timeline events for VM request status history
-- Story 2.8: Request Status Timeline

CREATE TABLE IF NOT EXISTS PUBLIC.request_timeline_events (
    id              UUID PRIMARY KEY,
    request_id      UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    actor_id        UUID,
    actor_name      VARCHAR(255),
    details         JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL
);

-- Index for efficient timeline queries by request
CREATE INDEX idx_timeline_events_request ON PUBLIC.request_timeline_events (request_id, occurred_at);
-- Index for tenant isolation queries
CREATE INDEX idx_timeline_events_tenant ON PUBLIC.request_timeline_events (tenant_id);

-- Enable Row-Level Security for multi-tenancy
ALTER TABLE request_timeline_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_timeline_events ON request_timeline_events
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE request_timeline_events FORCE ROW LEVEL SECURITY;

-- Grant permissions to application role
GRANT SELECT, INSERT, UPDATE, DELETE ON request_timeline_events TO eaf_app;

-- Documentation
COMMENT ON TABLE request_timeline_events IS 'Projection table for VM request timeline events';
COMMENT ON COLUMN request_timeline_events.id IS 'Unique identifier for the timeline event';
COMMENT ON COLUMN request_timeline_events.request_id IS 'FK to vm_requests_projection.id';
COMMENT ON COLUMN request_timeline_events.event_type IS 'Type: CREATED, APPROVED, REJECTED, CANCELLED, PROVISIONING_STARTED, VM_READY';
COMMENT ON COLUMN request_timeline_events.actor_id IS 'User ID who performed the action';
COMMENT ON COLUMN request_timeline_events.actor_name IS 'Display name of actor (resolved at projection time)';
COMMENT ON COLUMN request_timeline_events.details IS 'Additional event details (e.g., rejection reason)';
COMMENT ON COLUMN request_timeline_events.occurred_at IS 'When the event occurred';

-- V005: Create REQUEST_TIMELINE_EVENTS projection table
-- Stores timeline events for VM request status history
-- Story 2.8: Request Status Timeline

CREATE TABLE IF NOT EXISTS "REQUEST_TIMELINE_EVENTS" (
    "ID"              UUID PRIMARY KEY,
    "REQUEST_ID"      UUID NOT NULL REFERENCES "VM_REQUESTS_PROJECTION"("ID") ON DELETE CASCADE,
    "TENANT_ID"       UUID NOT NULL,
    "EVENT_TYPE"      VARCHAR(50) NOT NULL,
    "ACTOR_ID"        UUID,
    "ACTOR_NAME"      VARCHAR(255),
    "DETAILS"         VARCHAR(4000),
    "OCCURRED_AT"     TIMESTAMPTZ NOT NULL
);

-- Index for efficient timeline queries by request
CREATE INDEX "IDX_TIMELINE_EVENTS_REQUEST" ON "REQUEST_TIMELINE_EVENTS" ("REQUEST_ID", "OCCURRED_AT");
-- Index for tenant isolation queries
CREATE INDEX "IDX_TIMELINE_EVENTS_TENANT" ON "REQUEST_TIMELINE_EVENTS" ("TENANT_ID");

-- Enable Row-Level Security for multi-tenancy
ALTER TABLE "REQUEST_TIMELINE_EVENTS" ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_timeline_events ON "REQUEST_TIMELINE_EVENTS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "REQUEST_TIMELINE_EVENTS" FORCE ROW LEVEL SECURITY;

-- Grant permissions to application role
GRANT SELECT, INSERT, UPDATE, DELETE ON "REQUEST_TIMELINE_EVENTS" TO eaf_app;

-- Documentation
COMMENT ON TABLE "REQUEST_TIMELINE_EVENTS" IS 'Projection table for VM request timeline events';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."ID" IS 'Unique identifier for the timeline event';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."REQUEST_ID" IS 'FK to vm_requests_projection.id';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."EVENT_TYPE" IS 'Type: CREATED, APPROVED, REJECTED, CANCELLED, PROVISIONING_STARTED, VM_READY';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."ACTOR_ID" IS 'User ID who performed the action';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."ACTOR_NAME" IS 'Display name of actor (resolved at projection time)';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."DETAILS" IS 'Additional event details (e.g., rejection reason)';
COMMENT ON COLUMN "REQUEST_TIMELINE_EVENTS"."OCCURRED_AT" IS 'When the event occurred';

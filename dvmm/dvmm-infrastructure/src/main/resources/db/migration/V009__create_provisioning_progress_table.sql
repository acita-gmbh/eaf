CREATE TABLE provisioning_progress (
    vm_request_id UUID PRIMARY KEY,
    stage TEXT NOT NULL,
    details TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL,
    CONSTRAINT fk_provisioning_progress_request
        FOREIGN KEY (vm_request_id) REFERENCES vm_requests_projection(id) ON DELETE CASCADE
);

-- Index for tenant queries (RLS performance)
CREATE INDEX idx_provisioning_progress_tenant ON provisioning_progress(tenant_id);

-- [jooq ignore start]
ALTER TABLE provisioning_progress ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON provisioning_progress
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE provisioning_progress FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON provisioning_progress TO eaf_app;
-- [jooq ignore stop]

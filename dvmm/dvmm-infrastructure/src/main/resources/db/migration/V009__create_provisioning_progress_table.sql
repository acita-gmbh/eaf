CREATE TABLE provisioning_progress (
    vm_request_id UUID PRIMARY KEY,
    stage TEXT NOT NULL,
    details TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID NOT NULL
);

-- [jooq ignore start]
ALTER TABLE provisioning_progress ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON provisioning_progress
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
-- [jooq ignore stop]

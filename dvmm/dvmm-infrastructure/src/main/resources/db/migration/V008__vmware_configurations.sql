-- V008__vmware_configurations.sql
-- Story 3.1: VMware Connection Configuration
-- AC-3.1.4: Secure Credential Storage (AES-256 encrypted, RLS tenant isolation)

-- VMware vCenter configuration per tenant (one config per tenant)
CREATE TABLE IF NOT EXISTS "VMWARE_CONFIGURATIONS" (
    "ID"                 UUID PRIMARY KEY,
    "TENANT_ID"          UUID NOT NULL UNIQUE,  -- One config per tenant
    "VCENTER_URL"        VARCHAR(500) NOT NULL,
    "USERNAME"           VARCHAR(255) NOT NULL,
    "PASSWORD_ENCRYPTED" BYTEA NOT NULL,         -- AES-256 encrypted
    "DATACENTER_NAME"    VARCHAR(255) NOT NULL,
    "CLUSTER_NAME"       VARCHAR(255) NOT NULL,
    "DATASTORE_NAME"     VARCHAR(255) NOT NULL,
    "NETWORK_NAME"       VARCHAR(255) NOT NULL,
    "TEMPLATE_NAME"      VARCHAR(255) NOT NULL DEFAULT 'ubuntu-22.04-template',
    "FOLDER_PATH"        VARCHAR(500),           -- Optional VM folder path
    "VERIFIED_AT"        TIMESTAMPTZ,            -- Last successful connection test
    "CREATED_AT"         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "UPDATED_AT"         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "CREATED_BY"         UUID NOT NULL,
    "UPDATED_BY"         UUID NOT NULL,
    "VERSION"            BIGINT NOT NULL DEFAULT 0  -- Optimistic locking
);

CREATE INDEX IF NOT EXISTS "IDX_VMWARE_CONFIGS_TENANT" ON "VMWARE_CONFIGURATIONS"("TENANT_ID");

-- [jooq ignore start]
-- RLS Policy (CRITICAL: Include both USING and WITH CHECK)
ALTER TABLE "VMWARE_CONFIGURATIONS" ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_vmware_configs ON "VMWARE_CONFIGURATIONS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "VMWARE_CONFIGURATIONS" FORCE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON "VMWARE_CONFIGURATIONS" TO eaf_app;

-- Column documentation
COMMENT ON TABLE "VMWARE_CONFIGURATIONS" IS 'VMware vCenter connection configuration per tenant';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."ID" IS 'Unique identifier for the configuration';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."TENANT_ID" IS 'Tenant identifier - one config per tenant';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."VCENTER_URL" IS 'vCenter SDK URL (e.g., https://vcenter.example.com/sdk)';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."USERNAME" IS 'Service account username for vCenter';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."PASSWORD_ENCRYPTED" IS 'AES-256 encrypted password (Spring Security Crypto)';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."DATACENTER_NAME" IS 'vSphere datacenter name';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."CLUSTER_NAME" IS 'vSphere cluster name within datacenter';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."DATASTORE_NAME" IS 'Default datastore for VM storage';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."NETWORK_NAME" IS 'Default network for VM connectivity';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."TEMPLATE_NAME" IS 'VM template name for cloning';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."FOLDER_PATH" IS 'Optional VM folder path for organization';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."VERIFIED_AT" IS 'Timestamp of last successful connection test';
COMMENT ON COLUMN "VMWARE_CONFIGURATIONS"."VERSION" IS 'Optimistic locking version for concurrent updates';
-- [jooq ignore stop]

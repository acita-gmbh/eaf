-- V011__add_vm_details_to_projection.sql
-- Story 3-7: Add VM details columns to support VM details view for users
--
-- These columns store VM runtime information that is populated:
-- 1. When provisioning completes (from VmProvisioned event)
-- 2. When an authenticated user with VM access triggers "Sync Status" (from live vSphere query)
--
-- The IP_ADDRESS and HOSTNAME come from VMware Tools after provisioning.
-- POWER_STATE and GUEST_OS are fetched from vSphere runtime properties.

-- Add VM details columns to the projection
ALTER TABLE "VM_REQUESTS_PROJECTION"
    ADD COLUMN "VMWARE_VM_ID" VARCHAR(255),
    ADD COLUMN "IP_ADDRESS" VARCHAR(45),
    ADD COLUMN "HOSTNAME" VARCHAR(255),
    ADD COLUMN "POWER_STATE" VARCHAR(50),
    ADD COLUMN "GUEST_OS" VARCHAR(255),
    ADD COLUMN "LAST_SYNCED_AT" TIMESTAMPTZ;

-- Index for VMware VM ID lookups (used during sync operations)
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_VMWARE_VM_ID"
    ON "VM_REQUESTS_PROJECTION" ("VMWARE_VM_ID")
    WHERE "VMWARE_VM_ID" IS NOT NULL;

-- [jooq ignore start]
-- PostgreSQL-specific: Comments (not needed for jOOQ code generation)

COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."VMWARE_VM_ID" IS 'VMware MoRef ID (e.g., vm-123). Set when provisioning completes.';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."IP_ADDRESS" IS 'Primary IP address detected via VMware Tools. IPv4 or IPv6.';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."HOSTNAME" IS 'Guest hostname from VMware Tools guest.hostName property.';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."POWER_STATE" IS 'VM power state: POWERED_ON, POWERED_OFF, SUSPENDED';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."GUEST_OS" IS 'Detected guest OS from VMware Tools (e.g., Ubuntu 22.04 64-bit)';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."LAST_SYNCED_AT" IS 'Timestamp of last successful status sync from vSphere';
-- [jooq ignore stop]

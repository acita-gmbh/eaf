-- V012__add_boot_time_to_projection.sql
-- Story 3-7: Add BOOT_TIME column for VM uptime calculation
--
-- The boot_time is fetched from vSphere runtime.bootTime property.
-- It represents when the VM was last powered on.
-- Combined with current time, this enables uptime display in the UI.

-- Add boot_time column to the projection
ALTER TABLE "VM_REQUESTS_PROJECTION"
    ADD COLUMN "BOOT_TIME" TIMESTAMPTZ;

-- [jooq ignore start]
-- PostgreSQL-specific: Comments (not needed for jOOQ code generation)

COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."BOOT_TIME" IS 'Timestamp when VM was last powered on (from vSphere runtime.bootTime). Used for uptime calculation.';
-- [jooq ignore stop]

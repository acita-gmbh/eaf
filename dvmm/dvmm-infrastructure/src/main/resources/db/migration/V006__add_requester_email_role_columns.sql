-- V006__add_requester_email_role_columns.sql
-- Story 2.10: Request Detail View (Admin)
-- Add requester email and role columns for admin view display

-- Add new columns for requester details (nullable for backward compatibility)
ALTER TABLE "VM_REQUESTS_PROJECTION"
    ADD COLUMN "REQUESTER_EMAIL" VARCHAR(255),
    ADD COLUMN "REQUESTER_ROLE" VARCHAR(100);

-- Add comments for new columns
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REQUESTER_EMAIL" IS 'Denormalized requester email for display in admin view';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REQUESTER_ROLE" IS 'Denormalized requester role for display in admin view';

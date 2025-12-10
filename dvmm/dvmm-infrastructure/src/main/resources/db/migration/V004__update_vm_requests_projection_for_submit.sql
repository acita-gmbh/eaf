-- V004__update_vm_requests_projection_for_submit.sql
-- Story 2.6: VM Request Form - Submit Command
-- Add columns needed for full VM request workflow

-- Add new columns to support complete request submission
ALTER TABLE "VM_REQUESTS_PROJECTION"
    ADD COLUMN "PROJECT_ID" UUID,
    ADD COLUMN "PROJECT_NAME" VARCHAR(255),
    ADD COLUMN "SIZE" VARCHAR(10),
    ADD COLUMN "DISK_GB" INT,
    ADD COLUMN "JUSTIFICATION" TEXT,
    ADD COLUMN "REQUESTER_NAME" VARCHAR(255),
    ADD COLUMN "APPROVED_BY" UUID,
    ADD COLUMN "APPROVED_BY_NAME" VARCHAR(255),
    ADD COLUMN "REJECTED_BY" UUID,
    ADD COLUMN "REJECTED_BY_NAME" VARCHAR(255),
    ADD COLUMN "REJECTION_REASON" TEXT;

-- Update existing rows with default values for NOT NULL columns
-- (In production, this would need data migration strategy)
UPDATE "VM_REQUESTS_PROJECTION"
SET "PROJECT_ID" = '00000000-0000-0000-0000-000000000000',
    "PROJECT_NAME" = 'Unknown Project',
    "SIZE" = 'M',
    "DISK_GB" = 100,
    "JUSTIFICATION" = 'Migrated from previous version',
    "REQUESTER_NAME" = 'Unknown User'
WHERE "PROJECT_ID" IS NULL;

-- Now make required columns NOT NULL
ALTER TABLE "VM_REQUESTS_PROJECTION"
    ALTER COLUMN "PROJECT_ID" SET NOT NULL,
    ALTER COLUMN "PROJECT_NAME" SET NOT NULL,
    ALTER COLUMN "SIZE" SET NOT NULL,
    ALTER COLUMN "DISK_GB" SET NOT NULL,
    ALTER COLUMN "JUSTIFICATION" SET NOT NULL,
    ALTER COLUMN "REQUESTER_NAME" SET NOT NULL;

-- Add index for project filtering
CREATE INDEX "IDX_VM_REQUESTS_PROJECTION_PROJECT" ON "VM_REQUESTS_PROJECTION" ("PROJECT_ID");

-- Add comments for new columns
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."PROJECT_ID" IS 'Project the VM belongs to';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."PROJECT_NAME" IS 'Denormalized project name for display';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."SIZE" IS 'VM size code: S, M, L, XL';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."DISK_GB" IS 'Disk size in GB based on size';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."JUSTIFICATION" IS 'Business justification for the request';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REQUESTER_NAME" IS 'Denormalized requester name for display';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."APPROVED_BY" IS 'Admin who approved the request (nullable)';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."APPROVED_BY_NAME" IS 'Denormalized approver name for display';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REJECTED_BY" IS 'Admin who rejected the request (nullable)';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REJECTED_BY_NAME" IS 'Denormalized rejector name for display';
COMMENT ON COLUMN "VM_REQUESTS_PROJECTION"."REJECTION_REASON" IS 'Reason provided for rejection (nullable)';

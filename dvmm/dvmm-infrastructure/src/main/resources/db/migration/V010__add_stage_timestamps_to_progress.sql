-- Add stagetimestamps column to store completion time for each stage
-- Stored as JSONB: {"CLONING": "2025-12-08T10:00:00Z", "CONFIGURING": "2025-12-08T10:01:00Z", ...}
ALTER TABLE "PROVISIONING_PROGRESS" ADD COLUMN "STAGE_TIMESTAMPS" JSONB NOT NULL DEFAULT '{}';

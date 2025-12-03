-- V006__add_requester_email_role_columns.sql
-- Story 2.10: Request Detail View (Admin)
-- Add requester email and role columns for admin view display

-- Add new columns for requester details (nullable for backward compatibility)
ALTER TABLE vm_requests_projection
    ADD COLUMN requester_email VARCHAR(255),
    ADD COLUMN requester_role VARCHAR(100);

-- Add comments for new columns
COMMENT ON COLUMN vm_requests_projection.requester_email IS 'Denormalized requester email for display in admin view';
COMMENT ON COLUMN vm_requests_projection.requester_role IS 'Denormalized requester role for display in admin view';

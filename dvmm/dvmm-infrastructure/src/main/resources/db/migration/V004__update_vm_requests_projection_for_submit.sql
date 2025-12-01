-- V004__update_vm_requests_projection_for_submit.sql
-- Story 2.6: VM Request Form - Submit Command
-- Add columns needed for full VM request workflow

-- Add new columns to support complete request submission
ALTER TABLE vm_requests_projection
    ADD COLUMN project_id UUID,
    ADD COLUMN project_name VARCHAR(255),
    ADD COLUMN size VARCHAR(10),
    ADD COLUMN disk_gb INT,
    ADD COLUMN justification TEXT,
    ADD COLUMN requester_name VARCHAR(255),
    ADD COLUMN approved_by UUID,
    ADD COLUMN approved_by_name VARCHAR(255),
    ADD COLUMN rejected_by UUID,
    ADD COLUMN rejected_by_name VARCHAR(255),
    ADD COLUMN rejection_reason TEXT;

-- Update existing rows with default values for NOT NULL columns
-- (In production, this would need data migration strategy)
UPDATE vm_requests_projection
SET project_id = '00000000-0000-0000-0000-000000000000',
    project_name = 'Unknown Project',
    size = 'M',
    disk_gb = 100,
    justification = 'Migrated from previous version',
    requester_name = 'Unknown User'
WHERE project_id IS NULL;

-- Now make required columns NOT NULL
ALTER TABLE vm_requests_projection
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN project_name SET NOT NULL,
    ALTER COLUMN size SET NOT NULL,
    ALTER COLUMN disk_gb SET NOT NULL,
    ALTER COLUMN justification SET NOT NULL,
    ALTER COLUMN requester_name SET NOT NULL;

-- Add index for project filtering
CREATE INDEX idx_vm_requests_projection_project ON vm_requests_projection (project_id);

-- Add comments for new columns
COMMENT ON COLUMN vm_requests_projection.project_id IS 'Project the VM belongs to';
COMMENT ON COLUMN vm_requests_projection.project_name IS 'Denormalized project name for display';
COMMENT ON COLUMN vm_requests_projection.size IS 'VM size code: S, M, L, XL';
COMMENT ON COLUMN vm_requests_projection.disk_gb IS 'Disk size in GB based on size';
COMMENT ON COLUMN vm_requests_projection.justification IS 'Business justification for the request';
COMMENT ON COLUMN vm_requests_projection.requester_name IS 'Denormalized requester name for display';
COMMENT ON COLUMN vm_requests_projection.approved_by IS 'Admin who approved the request (nullable)';
COMMENT ON COLUMN vm_requests_projection.approved_by_name IS 'Denormalized approver name for display';
COMMENT ON COLUMN vm_requests_projection.rejected_by IS 'Admin who rejected the request (nullable)';
COMMENT ON COLUMN vm_requests_projection.rejected_by_name IS 'Denormalized rejector name for display';
COMMENT ON COLUMN vm_requests_projection.rejection_reason IS 'Reason provided for rejection (nullable)';

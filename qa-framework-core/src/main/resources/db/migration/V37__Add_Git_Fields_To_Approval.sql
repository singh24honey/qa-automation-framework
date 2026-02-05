-- =====================================================
-- Migration: V44 - Add Git Integration Fields to Approval Requests
-- Purpose: Track Git operations triggered by approval workflow
-- Author: QA Framework
-- Date: 2025-02-02
-- =====================================================

-- Add Git-related columns to approval_requests table
ALTER TABLE approval_requests
ADD COLUMN IF NOT EXISTS auto_commit_on_approval BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS git_operation_triggered BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS git_operation_success BOOLEAN,
ADD COLUMN IF NOT EXISTS git_error_message TEXT,
ADD COLUMN IF NOT EXISTS ai_generated_test_id UUID,
ADD COLUMN IF NOT EXISTS git_branch VARCHAR(255),
ADD COLUMN IF NOT EXISTS git_commit_sha VARCHAR(255),
ADD COLUMN IF NOT EXISTS git_pr_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS git_committed_at TIMESTAMP;

-- Add foreign key to ai_generated_tests if table exists
-- V44 - Safe FK Creation
DO $$
BEGIN
    -- Check if FK doesn't already exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_approval_ai_test'
        AND table_name = 'approval_requests'
    ) THEN
        ALTER TABLE approval_requests
        ADD CONSTRAINT fk_approval_ai_test
            FOREIGN KEY (ai_generated_test_id)
            REFERENCES ai_generated_tests(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_approval_git_triggered ON approval_requests(git_operation_triggered);
CREATE INDEX IF NOT EXISTS idx_approval_git_success ON approval_requests(git_operation_success);
CREATE INDEX IF NOT EXISTS idx_approval_ai_test_id ON approval_requests(ai_generated_test_id);

-- Comments
COMMENT ON COLUMN approval_requests.auto_commit_on_approval IS 'Whether to automatically commit to Git upon approval';
COMMENT ON COLUMN approval_requests.git_operation_triggered IS 'Whether Git commit was triggered';
COMMENT ON COLUMN approval_requests.git_operation_success IS 'Whether Git operation succeeded';
COMMENT ON COLUMN approval_requests.git_error_message IS 'Error message if Git operation failed';
COMMENT ON COLUMN approval_requests.ai_generated_test_id IS 'Reference to AI generated test';
COMMENT ON COLUMN approval_requests.git_branch IS 'Git branch name where test was committed';
COMMENT ON COLUMN approval_requests.git_commit_sha IS 'Git commit SHA';
COMMENT ON COLUMN approval_requests.git_pr_url IS 'Pull request URL';
COMMENT ON COLUMN approval_requests.git_committed_at IS 'Timestamp of Git commit';
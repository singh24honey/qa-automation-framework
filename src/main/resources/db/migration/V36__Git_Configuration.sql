-- =====================================================
-- Migration: V43 - Git Configuration and Commit History
-- Purpose: Store Git repository configurations and track commit history
-- Author: QA Framework
-- Date: 2025-02-01
-- =====================================================

-- Git Configuration Table
-- Stores repository connection details and credentials
CREATE TABLE IF NOT EXISTS git_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    repository_url VARCHAR(1000) NOT NULL,
    repository_type VARCHAR(50) NOT NULL, -- GITHUB, GITLAB, BITBUCKET
    default_target_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    branch_prefix VARCHAR(100) NOT NULL DEFAULT 'AiDraft',

    -- Authentication
    auth_type VARCHAR(50) NOT NULL, -- TOKEN, SSH_KEY
    auth_token_secret_key VARCHAR(500), -- Reference to AWS Secrets Manager
    ssh_key_path VARCHAR(500),

    -- Committer Details
    committer_name VARCHAR(255) NOT NULL,
    committer_email VARCHAR(255) NOT NULL,

    -- PR Configuration
    auto_create_pr BOOLEAN DEFAULT true,
    pr_reviewer_usernames TEXT[], -- Array of GitHub/GitLab usernames
    pr_labels TEXT[], -- Default labels for PRs

    -- Status
    is_active BOOLEAN DEFAULT true,
    is_validated BOOLEAN DEFAULT false,
    last_validation_at TIMESTAMP,
    validation_error TEXT,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    -- Constraints
    CONSTRAINT chk_repo_type CHECK (repository_type IN ('GITHUB', 'GITLAB', 'BITBUCKET')),
    CONSTRAINT chk_auth_type CHECK (auth_type IN ('TOKEN', 'SSH_KEY')),
    CONSTRAINT chk_auth_config CHECK (
        (auth_type = 'TOKEN' AND auth_token_secret_key IS NOT NULL) OR
        (auth_type = 'SSH_KEY' AND ssh_key_path IS NOT NULL)
    )
);

-- Git Commit History Table
CREATE TABLE IF NOT EXISTS git_commit_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Links
    ai_generated_test_id UUID NOT NULL,
    git_configuration_id UUID NOT NULL,
    approval_request_id UUID,

    -- Git Details
    branch_name VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(255),
    commit_message TEXT NOT NULL,
    files_committed TEXT[] NOT NULL,

    -- PR Details
    pr_number INTEGER,
    pr_url VARCHAR(1000),
    pr_status VARCHAR(50),
    pr_created_at TIMESTAMP,
    pr_merged_at TIMESTAMP,

    -- Operation Status
    operation_status VARCHAR(50) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,

    -- Metadata
    total_lines_added INTEGER DEFAULT 0,
    total_lines_deleted INTEGER DEFAULT 0,
    total_files_count INTEGER DEFAULT 0,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),

    -- Foreign Keys
    CONSTRAINT fk_git_commit_ai_test
        FOREIGN KEY (ai_generated_test_id)
        REFERENCES ai_generated_tests(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_git_commit_config
        FOREIGN KEY (git_configuration_id)
        REFERENCES git_configurations(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_git_commit_approval
        FOREIGN KEY (approval_request_id)
        REFERENCES approval_requests(id)
        ON DELETE SET NULL,

    -- Constraints
    CONSTRAINT chk_operation_status CHECK (operation_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_operation_type CHECK (operation_type IN ('BRANCH_CREATE', 'COMMIT', 'PR_CREATE')),
    CONSTRAINT chk_pr_status CHECK (pr_status IS NULL OR pr_status IN ('OPEN', 'MERGED', 'CLOSED', 'DRAFT'))
);

-- Indexes
CREATE INDEX idx_git_config_active ON git_configurations(is_active);
CREATE INDEX idx_git_config_type ON git_configurations(repository_type);

CREATE INDEX idx_git_commit_test_id ON git_commit_history(ai_generated_test_id);
CREATE INDEX idx_git_commit_config_id ON git_commit_history(git_configuration_id);
CREATE INDEX idx_git_commit_approval_id ON git_commit_history(approval_request_id);
CREATE INDEX idx_git_commit_branch ON git_commit_history(branch_name);
CREATE INDEX idx_git_commit_status ON git_commit_history(operation_status);
CREATE INDEX idx_git_commit_created ON git_commit_history(created_at DESC);

-- Update triggers
CREATE OR REPLACE FUNCTION update_git_config_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_git_config_timestamp
BEFORE UPDATE ON git_configurations
FOR EACH ROW
EXECUTE FUNCTION update_git_config_timestamp();

CREATE TRIGGER trigger_update_git_commit_timestamp
BEFORE UPDATE ON git_commit_history
FOR EACH ROW
EXECUTE FUNCTION update_git_config_timestamp();

-- Add Git columns to ai_generated_tests table
ALTER TABLE ai_generated_tests
ADD COLUMN IF NOT EXISTS git_branch VARCHAR(255),
ADD COLUMN IF NOT EXISTS git_commit_sha VARCHAR(255),
ADD COLUMN IF NOT EXISTS git_pr_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS git_committed_at TIMESTAMP;

CREATE INDEX idx_ai_test_git_branch ON ai_generated_tests(git_branch);

-- Comments
COMMENT ON TABLE git_configurations IS 'Stores Git repository configurations for automated commits';
COMMENT ON TABLE git_commit_history IS 'Audit trail of all Git operations performed by the framework';

-- Default configuration
INSERT INTO git_configurations (
    name,
    repository_url,
    repository_type,
    default_target_branch,
    branch_prefix,
    auth_type,
    auth_token_secret_key,
    committer_name,
    committer_email,
    auto_create_pr,
    is_active,
    created_by
) VALUES (
    'qa-framework-default',
    'https://github.com/your-org/qa-framework.git',
    'GITHUB',
    'main',
    'AiDraft',
    'TOKEN',
    'prod/git/qa-framework',
    'QA Framework Bot',
    'qa-bot@your-company.com',
    true,
    false, -- Set to false until configured
    'SYSTEM'
) ON CONFLICT (name) DO NOTHING;
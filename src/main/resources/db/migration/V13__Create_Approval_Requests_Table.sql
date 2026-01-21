-- Approval Requests Table
CREATE TABLE approval_requests (
    id UUID PRIMARY KEY,

    -- Request details
    request_type VARCHAR(50) NOT NULL CHECK (request_type IN ('TEST_GENERATION', 'TEST_MODIFICATION', 'TEST_DELETION')),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),

    -- Generated content
    generated_content TEXT NOT NULL,
    ai_response_metadata TEXT,

    -- Test details
    test_name VARCHAR(255),
    test_framework VARCHAR(50),
    test_language VARCHAR(50),
    target_url VARCHAR(500),

    -- User who requested
    requested_by_id UUID NOT NULL,
    requested_by_name VARCHAR(100),
    requested_by_email VARCHAR(255),

    -- Approver details
    reviewed_by_id UUID,
    reviewed_by_name VARCHAR(100),
    reviewed_by_email VARCHAR(255),

    -- Approval decision
    approval_decision_notes TEXT,
    rejection_reason TEXT,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,

    -- Auto-execution after approval
    auto_execute_on_approval BOOLEAN DEFAULT FALSE,
    executed_test_id UUID,
    execution_id UUID,

    -- Audit
    version BIGINT,

    -- Sanitization info
    sanitization_applied BOOLEAN DEFAULT FALSE,
    redaction_count INTEGER DEFAULT 0,

    CONSTRAINT fk_executed_test FOREIGN KEY (executed_test_id) REFERENCES tests(id) ON DELETE SET NULL,
    CONSTRAINT fk_execution FOREIGN KEY (execution_id) REFERENCES test_executions(id) ON DELETE SET NULL
);

-- Indexes for performance
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_requested_by ON approval_requests(requested_by_id);
CREATE INDEX idx_approval_requests_reviewed_by ON approval_requests(reviewed_by_id);
CREATE INDEX idx_approval_requests_created_at ON approval_requests(created_at);
CREATE INDEX idx_approval_requests_expires_at ON approval_requests(expires_at);

-- Composite index for finding pending approvals
CREATE INDEX idx_approval_requests_status_created ON approval_requests(status, created_at);

COMMENT ON TABLE approval_requests IS 'Human-in-the-loop approval workflow for AI-generated tests';
COMMENT ON COLUMN approval_requests.request_type IS 'Type of approval request';
COMMENT ON COLUMN approval_requests.status IS 'Current status of approval request';
COMMENT ON COLUMN approval_requests.generated_content IS 'AI-generated test code';
COMMENT ON COLUMN approval_requests.ai_response_metadata IS 'Metadata from AI response (tokens, cost, etc.)';
COMMENT ON COLUMN approval_requests.auto_execute_on_approval IS 'Should test be automatically executed after approval?';
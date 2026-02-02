-- ================================================================
-- V42: AI-Generated Tests Storage with Approval Workflow
-- Dependencies: V41 (jira_stories)
--
-- CORRECTED: Uses UUID for all IDs to match framework pattern
-- ================================================================

-- Generated test storage with approval workflow
CREATE TABLE IF NOT EXISTS ai_generated_tests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Source reference (NULLABLE to allow orphaned tests)
    jira_story_id UUID,  -- CORRECTED: UUID not BIGINT
    jira_story_key VARCHAR(50) NOT NULL,

    -- Test metadata
    test_name VARCHAR(255) NOT NULL,
    test_type VARCHAR(50) NOT NULL,
    test_framework VARCHAR(50) NOT NULL,

    -- Generated content (JSONB for structured storage)
    test_code_json JSONB NOT NULL,

    -- AI generation metadata
    ai_provider VARCHAR(50) NOT NULL,
    ai_model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_cost_usd DECIMAL(10, 6),

    -- Quality metrics
    quality_score DECIMAL(5, 2),
    confidence_level VARCHAR(20),
    quality_concerns JSONB,

    -- Approval workflow
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_comments TEXT,

    -- File system tracking
    draft_folder_path VARCHAR(500),
    committed_folder_path VARCHAR(500),

    -- Timestamps (match framework pattern)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    committed_at TIMESTAMP WITH TIME ZONE,

    -- Version for optimistic locking
    version BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT fk_jira_story FOREIGN KEY (jira_story_id)
        REFERENCES jira_stories(id) ON DELETE SET NULL,
    CONSTRAINT valid_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'COMMITTED')),
    CONSTRAINT valid_test_type CHECK (test_type IN ('UI', 'API', 'E2E', 'UNIT')),
    CONSTRAINT valid_test_framework CHECK (test_framework IN ('CUCUMBER', 'TESTNG', 'JUNIT')),
    CONSTRAINT valid_quality_score CHECK (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100))
);

-- Indexes
CREATE INDEX idx_ai_tests_jira_story ON ai_generated_tests(jira_story_id) WHERE jira_story_id IS NOT NULL;
CREATE INDEX idx_ai_tests_story_key ON ai_generated_tests(jira_story_key);
CREATE INDEX idx_ai_tests_status ON ai_generated_tests(status);
CREATE INDEX idx_ai_tests_created_at ON ai_generated_tests(created_at DESC);
CREATE INDEX idx_ai_tests_quality ON ai_generated_tests(quality_score DESC) WHERE quality_score IS NOT NULL;
CREATE INDEX idx_ai_tests_code_gin ON ai_generated_tests USING GIN (test_code_json);
CREATE INDEX idx_ai_tests_concerns_gin ON ai_generated_tests USING GIN (quality_concerns);

COMMENT ON TABLE ai_generated_tests IS 'AI-generated test code with approval workflow';

-- ================================================================
-- Generation Attempt History
-- ================================================================

CREATE TABLE IF NOT EXISTS ai_test_generation_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Source reference (NULLABLE)
    jira_story_id UUID,  -- CORRECTED: UUID not BIGINT
    jira_story_key VARCHAR(50) NOT NULL,

    -- Attempt metadata
    attempt_number INTEGER NOT NULL DEFAULT 1,
    ai_provider VARCHAR(50) NOT NULL,
    ai_model VARCHAR(100) NOT NULL,

    -- Request/response
    prompt_context TEXT NOT NULL,
    raw_response TEXT,

    -- Outcome
    success BOOLEAN NOT NULL DEFAULT false,
    error_message TEXT,
    error_type VARCHAR(100),

    -- Costs
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_cost_usd DECIMAL(10, 6),

    -- Timing
    duration_ms INTEGER,

    -- Timestamps
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Link to successful generation (NO FK - avoids circular dependency)
    generated_test_id UUID,  -- CORRECTED: UUID not BIGINT

    -- Constraints
    CONSTRAINT fk_jira_story_attempt FOREIGN KEY (jira_story_id)
        REFERENCES jira_stories(id) ON DELETE SET NULL
);

-- Indexes
CREATE INDEX idx_attempts_jira_story ON ai_test_generation_attempts(jira_story_id) WHERE jira_story_id IS NOT NULL;
CREATE INDEX idx_attempts_story_key ON ai_test_generation_attempts(jira_story_key);
CREATE INDEX idx_attempts_timestamp ON ai_test_generation_attempts(attempted_at DESC);
CREATE INDEX idx_attempts_success ON ai_test_generation_attempts(success);
CREATE INDEX idx_attempts_error_type ON ai_test_generation_attempts(error_type) WHERE error_type IS NOT NULL;
CREATE INDEX idx_attempts_test_id ON ai_test_generation_attempts(generated_test_id) WHERE generated_test_id IS NOT NULL;

COMMENT ON TABLE ai_test_generation_attempts IS 'Audit trail of AI test generation attempts';

-- ================================================================
-- Update Trigger
-- ================================================================

CREATE OR REPLACE FUNCTION update_ai_test_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_ai_test_timestamp
    BEFORE UPDATE ON ai_generated_tests
    FOR EACH ROW
    EXECUTE FUNCTION update_ai_test_timestamp();

-- ================================================================
-- Views
-- ================================================================

CREATE OR REPLACE VIEW v_pending_test_reviews AS
SELECT
    agt.id,
    agt.jira_story_key,
    agt.test_name,
    agt.test_type,
    agt.quality_score,
    agt.confidence_level,
    agt.created_at,
    js.summary AS story_summary,
    js.story_type,
    js.priority
FROM ai_generated_tests agt
LEFT JOIN jira_stories js ON agt.jira_story_id = js.id
WHERE agt.status IN ('DRAFT', 'PENDING_REVIEW')
ORDER BY agt.quality_score DESC NULLS LAST, agt.created_at DESC;

CREATE OR REPLACE VIEW v_test_generation_metrics AS
SELECT
    DATE_TRUNC('day', agt.created_at) AS generation_date,
    agt.test_type,
    agt.ai_provider,
    agt.ai_model,
    COUNT(*) AS total_generated,
    COUNT(*) FILTER (WHERE status = 'COMMITTED') AS committed_count,
    COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected_count,
    AVG(quality_score) AS avg_quality_score,
    SUM(total_cost_usd) AS total_cost
FROM ai_generated_tests agt
GROUP BY DATE_TRUNC('day', agt.created_at), agt.test_type, agt.ai_provider, agt.ai_model
ORDER BY generation_date DESC;

CREATE OR REPLACE VIEW v_failed_generation_analysis AS
SELECT
    DATE_TRUNC('hour', attempted_at) AS failure_hour,
    ai_provider,
    ai_model,
    error_type,
    COUNT(*) AS failure_count,
    AVG(duration_ms) AS avg_duration_ms
FROM ai_test_generation_attempts
WHERE success = false
GROUP BY DATE_TRUNC('hour', attempted_at), ai_provider, ai_model, error_type
ORDER BY failure_hour DESC;
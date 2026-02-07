-- =====================================================
-- Migration V39: Agent Execution Tracking
-- Purpose: Support autonomous AI agent workflows
-- Week: 14 Day 2
-- Date: 2026-02-07
-- Dependencies: V38 (Playwright support), V13 (approval_requests)
-- =====================================================

-- =====================================================
-- Table: agent_executions
-- Tracks high-level agent execution lifecycle
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Agent identification
    agent_type VARCHAR(50) NOT NULL,

    -- Execution state
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',

    -- Goal being pursued (stored as JSONB)
    goal JSONB NOT NULL,

    -- Iteration tracking
    current_iteration INTEGER DEFAULT 0,
    max_iterations INTEGER DEFAULT 20,

    -- Timing
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,

    -- Triggering user
    triggered_by UUID,
    triggered_by_name VARCHAR(255),

    -- Results (stored as JSONB)
    result JSONB,
    outputs JSONB,
    error_message TEXT,

    -- Cost tracking
    total_ai_cost DECIMAL(10,6) DEFAULT 0,
    total_actions INTEGER DEFAULT 0,

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Optimistic locking
    version BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT valid_agent_type CHECK (agent_type IN (
        'PLAYWRIGHT_TEST_GENERATOR',
        'FLAKY_TEST_FIXER',
        'SELF_HEALING_TEST_FIXER',
        'TEST_FAILURE_ANALYZER',
        'QUALITY_MONITOR'
    )),

    CONSTRAINT valid_agent_status CHECK (status IN (
        'RUNNING',
        'WAITING_FOR_APPROVAL',
        'SUCCEEDED',
        'FAILED',
        'STOPPED',
        'TIMEOUT',
        'BUDGET_EXCEEDED'
    ))
);

-- =====================================================
-- Table: agent_action_history
-- Detailed audit trail of every action taken by agents
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_action_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Link to parent execution
    agent_execution_id UUID NOT NULL REFERENCES agent_executions(id) ON DELETE CASCADE,

    -- Action details
    iteration INTEGER NOT NULL,
    action_type VARCHAR(100) NOT NULL,

    -- Input/output (stored as JSONB)
    action_input JSONB,
    action_output JSONB,

    -- Outcome
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms INTEGER,

    -- Approval workflow integration
    required_approval BOOLEAN DEFAULT FALSE,
    approval_request_id UUID REFERENCES approval_requests(id),

    -- Cost tracking
    ai_cost DECIMAL(10,6),

    -- Timing
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- Indexes for Performance
-- =====================================================

-- Agent executions indexes
CREATE INDEX idx_agent_exec_type ON agent_executions(agent_type);
CREATE INDEX idx_agent_exec_status ON agent_executions(status);
CREATE INDEX idx_agent_exec_started ON agent_executions(started_at DESC);
CREATE INDEX idx_agent_exec_triggered ON agent_executions(triggered_by)
    WHERE triggered_by IS NOT NULL;
CREATE INDEX idx_agent_exec_goal_gin ON agent_executions USING GIN (goal);

-- Agent action history indexes
CREATE INDEX idx_agent_action_exec ON agent_action_history(agent_execution_id);
CREATE INDEX idx_agent_action_type ON agent_action_history(action_type);
CREATE INDEX idx_agent_action_timestamp ON agent_action_history(timestamp DESC);
CREATE INDEX idx_agent_action_success ON agent_action_history(success);
CREATE INDEX idx_agent_action_approval ON agent_action_history(approval_request_id)
    WHERE approval_request_id IS NOT NULL;
CREATE INDEX idx_agent_action_iteration ON agent_action_history(agent_execution_id, iteration);

-- =====================================================
-- Update Trigger
-- =====================================================
CREATE OR REPLACE FUNCTION update_agent_execution_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_agent_execution_timestamp
    BEFORE UPDATE ON agent_executions
    FOR EACH ROW
    EXECUTE FUNCTION update_agent_execution_timestamp();

-- =====================================================
-- Views for Analytics
-- =====================================================

-- Agent execution statistics by type and status
CREATE OR REPLACE VIEW v_agent_execution_stats AS
SELECT
    agent_type,
    status,
    COUNT(*) AS total_executions,
    AVG(current_iteration) AS avg_iterations,
    AVG(total_ai_cost) AS avg_cost,
    AVG(EXTRACT(EPOCH FROM (COALESCE(completed_at, CURRENT_TIMESTAMP) - started_at))) AS avg_duration_seconds,
    COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS success_count,
    COUNT(*) FILTER (WHERE status = 'FAILED') AS failure_count,
    COUNT(*) FILTER (WHERE status = 'TIMEOUT') AS timeout_count,
    MAX(started_at) AS last_execution
FROM agent_executions
GROUP BY agent_type, status
ORDER BY agent_type, status;

-- Action type statistics across all agents
CREATE OR REPLACE VIEW v_agent_action_stats AS
SELECT
    ae.agent_type,
    aah.action_type,
    COUNT(*) AS total_actions,
    COUNT(*) FILTER (WHERE aah.success = true) AS success_count,
    COUNT(*) FILTER (WHERE aah.success = false) AS failure_count,
    ROUND(AVG(aah.duration_ms)::numeric, 2) AS avg_duration_ms,
    SUM(aah.ai_cost) AS total_ai_cost
FROM agent_action_history aah
JOIN agent_executions ae ON aah.agent_execution_id = ae.id
GROUP BY ae.agent_type, aah.action_type
ORDER BY ae.agent_type, total_actions DESC;

-- Failed actions for debugging
CREATE OR REPLACE VIEW v_failed_agent_actions AS
SELECT
    aah.id,
    ae.agent_type,
    ae.id AS execution_id,
    aah.iteration,
    aah.action_type,
    aah.error_message,
    aah.duration_ms,
    aah.timestamp
FROM agent_action_history aah
JOIN agent_executions ae ON aah.agent_execution_id = ae.id
WHERE aah.success = false
ORDER BY aah.timestamp DESC;

-- =====================================================
-- Comments for Documentation
-- =====================================================
COMMENT ON TABLE agent_executions IS 'Tracks autonomous AI agent execution lifecycles';
COMMENT ON TABLE agent_action_history IS 'Detailed audit trail of all actions taken by agents';

COMMENT ON COLUMN agent_executions.goal IS 'JSONB representation of AgentGoal (goalType, parameters, successCriteria)';
COMMENT ON COLUMN agent_executions.result IS 'JSONB representation of AgentResult (final outcome)';
COMMENT ON COLUMN agent_executions.outputs IS 'Work products produced by agent (files, PRs, etc.)';
COMMENT ON COLUMN agent_executions.total_actions IS 'Total number of actions taken during execution';

COMMENT ON COLUMN agent_action_history.action_input IS 'JSONB parameters passed to action';
COMMENT ON COLUMN agent_action_history.action_output IS 'JSONB results returned from action';
COMMENT ON COLUMN agent_action_history.approval_request_id IS 'Link to approval request if action required human approval';

COMMENT ON VIEW v_agent_execution_stats IS 'Statistics grouped by agent type and status';
COMMENT ON VIEW v_agent_action_stats IS 'Action type statistics across all agents';
COMMENT ON VIEW v_failed_agent_actions IS 'Failed actions for debugging and analysis';
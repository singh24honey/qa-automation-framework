-- AI Usage Log Table
CREATE TABLE ai_usage_logs (
    id UUID PRIMARY KEY,

    -- Request Info
    request_id VARCHAR(100) NOT NULL,
    user_id UUID,
    user_name VARCHAR(100),
    user_role VARCHAR(50),

    -- AI Provider (✅ Updated to match your enums)
    provider VARCHAR(50) NOT NULL CHECK (provider IN ('BEDROCK', 'OLLAMA', 'MOCK')),
    model_name VARCHAR(100) NOT NULL,
    task_type VARCHAR(50) NOT NULL CHECK (task_type IN ('TEST_GENERATION', 'FAILURE_ANALYSIS', 'FIX_SUGGESTION', 'CODE_REVIEW', 'TEST_OPTIMIZATION', 'GENERAL', 'DOCUMENTATION')),

    -- Token Usage
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,

    -- Cost Calculation
    prompt_cost_per_token DECIMAL(12, 8),
    completion_cost_per_token DECIMAL(12, 8),
    total_cost DECIMAL(10, 4),
    currency VARCHAR(3) DEFAULT 'USD',

    -- Request Details
    request_content_length INTEGER,
    response_content_length INTEGER,
    processing_time_ms BIGINT,

    -- Success/Failure
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Metadata
    approval_request_id UUID,
    test_id UUID,
    execution_id UUID,

    -- Indexing
    CONSTRAINT fk_approval_request FOREIGN KEY (approval_request_id)
        REFERENCES approval_requests(id) ON DELETE SET NULL,
    CONSTRAINT fk_test FOREIGN KEY (test_id)
        REFERENCES tests(id) ON DELETE SET NULL,
    CONSTRAINT fk_execution FOREIGN KEY (execution_id)
        REFERENCES test_executions(id) ON DELETE SET NULL
);

-- Rest of the migration stays the same...

-- Budget Configuration Table
CREATE TABLE ai_budgets (
    id UUID PRIMARY KEY,

    -- Budget Scope
    scope_type VARCHAR(50) NOT NULL,  -- USER, TEAM, GLOBAL
    scope_id VARCHAR(100),  -- user_id, team_id, or null for global
    scope_name VARCHAR(100),

    -- Budget Limits
    daily_limit DECIMAL(10, 2),
    weekly_limit DECIMAL(10, 2),
    monthly_limit DECIMAL(10, 2),
    currency VARCHAR(3) DEFAULT 'USD',

    -- Alert Thresholds (percentage)
    warning_threshold INTEGER DEFAULT 80,  -- 80%
    critical_threshold INTEGER DEFAULT 95,  -- 95%

    -- Status
    active BOOLEAN DEFAULT TRUE,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,

    -- Audit
    created_by VARCHAR(100),
    version BIGINT
);

-- Budget Alert History
CREATE TABLE ai_budget_alerts (
    id UUID PRIMARY KEY,

    budget_id UUID NOT NULL,

    -- Alert Details
    alert_type VARCHAR(50) NOT NULL,  -- WARNING, CRITICAL, EXCEEDED
    period_type VARCHAR(50) NOT NULL,  -- DAILY, WEEKLY, MONTHLY

    current_spend DECIMAL(10, 2) NOT NULL,
    budget_limit DECIMAL(10, 2) NOT NULL,
    percentage_used DECIMAL(5, 2) NOT NULL,

    -- Notification
    notified BOOLEAN DEFAULT FALSE,
    notification_sent_at TIMESTAMP WITH TIME ZONE,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_budget FOREIGN KEY (budget_id)
        REFERENCES ai_budgets(id) ON DELETE CASCADE
);

-- Indexes for Performance
CREATE INDEX idx_ai_usage_logs_user_id ON ai_usage_logs(user_id);
CREATE INDEX idx_ai_usage_logs_provider ON ai_usage_logs(provider);
CREATE INDEX idx_ai_usage_logs_created_at ON ai_usage_logs(created_at);
CREATE INDEX idx_ai_usage_logs_task_type ON ai_usage_logs(task_type);
CREATE INDEX idx_ai_usage_logs_user_created ON ai_usage_logs(user_id, created_at);

CREATE INDEX idx_ai_budgets_scope ON ai_budgets(scope_type, scope_id);
CREATE INDEX idx_ai_budgets_active ON ai_budgets(active);

CREATE INDEX idx_ai_budget_alerts_budget_id ON ai_budget_alerts(budget_id);
CREATE INDEX idx_ai_budget_alerts_created_at ON ai_budget_alerts(created_at);

-- Comments
COMMENT ON TABLE ai_usage_logs IS 'Tracks all AI API usage including tokens and costs';
COMMENT ON TABLE ai_budgets IS 'AI spending budget configurations';
COMMENT ON TABLE ai_budget_alerts IS 'History of budget alert notifications';

COMMENT ON COLUMN ai_usage_logs.total_cost IS 'Calculated cost in USD based on token usage';
COMMENT ON COLUMN ai_budgets.warning_threshold IS 'Percentage of budget to trigger warning (e.g., 80)';
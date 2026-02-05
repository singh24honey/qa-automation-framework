-- JIRA Integration Schema
-- Week 9 Day 1: Foundation tables for JIRA connectivity

-- JIRA configuration (per project/team)
CREATE TABLE jira_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_name VARCHAR(100) UNIQUE NOT NULL,
    jira_url VARCHAR(500) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    max_requests_per_minute INT DEFAULT 60,

    -- Audit fields
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Secret reference (AWS Secrets Manager ARN)
    secret_arn VARCHAR(500) NOT NULL,

    CONSTRAINT valid_url CHECK (jira_url LIKE 'https://%'),
    CONSTRAINT valid_project_key CHECK (project_key ~ '^[A-Z][A-Z0-9]{1,19}$')
);

-- JIRA API health status (for monitoring)
CREATE TABLE jira_health_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id UUID NOT NULL REFERENCES jira_configurations(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL, -- UP, DOWN, DEGRADED
    response_time_ms INT,
    rate_limit_remaining INT,
    rate_limit_reset_at TIMESTAMP,
    error_message TEXT,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jira_health_config_checked ON jira_health_snapshots(config_id, checked_at DESC);

-- JIRA API call audit log
CREATE TABLE jira_api_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id UUID NOT NULL REFERENCES jira_configurations(id) ON DELETE CASCADE,
    endpoint VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    status_code INT,
    request_id VARCHAR(100),
    duration_ms INT,
    error_message TEXT,
    rate_limited BOOLEAN DEFAULT false,
    retried BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL
);

CREATE INDEX idx_jira_audit_config_created ON jira_api_audit_log(config_id, created_at DESC);
CREATE INDEX idx_jira_audit_request_id ON jira_api_audit_log(request_id);

-- Insert default configuration for development
INSERT INTO jira_configurations (
    id,
    config_name,
    jira_url,
    project_key,
    created_by,
    secret_arn
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'default-dev',
    'https://your-company.atlassian.net',
    'QA',
    'system',
    'arn:aws:secretsmanager:us-east-1:123456789012:secret:dev/jira/api-token'
) ON CONFLICT (config_name) DO NOTHING;
-- Performance indexes for common queries

-- Tests table
CREATE INDEX IF NOT EXISTS idx_tests_framework_active
ON tests(framework, is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_tests_created_at
ON tests(created_at DESC);

-- Executions table
CREATE INDEX IF NOT EXISTS idx_executions_test_status
ON test_executions(test_id, status);

CREATE INDEX IF NOT EXISTS idx_executions_created_at
ON test_executions(created_at DESC);

-- API keys table
CREATE INDEX IF NOT EXISTS idx_api_keys_active
ON api_keys(is_active) WHERE is_active = true;

-- Request logs table
CREATE INDEX IF NOT EXISTS idx_request_logs_api_key_time
ON request_logs(api_key_id, created_at DESC);

-- Analyze tables for better query planning
ANALYZE tests;
ANALYZE test_executions;
ANALYZE api_keys;
ANALYZE request_logs;
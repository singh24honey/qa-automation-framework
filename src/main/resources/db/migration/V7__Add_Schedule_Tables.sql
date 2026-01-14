-- Test Schedules table
CREATE TABLE test_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    test_id UUID NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    cron_expression VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) DEFAULT 'UTC',
    browser VARCHAR(50) DEFAULT 'CHROME',
    environment VARCHAR(100),
    headless BOOLEAN DEFAULT TRUE,
    is_enabled BOOLEAN DEFAULT TRUE,
    is_running BOOLEAN DEFAULT FALSE,
    last_run_time TIMESTAMP WITH TIME ZONE,
    last_run_status VARCHAR(50),
    next_run_time TIMESTAMP WITH TIME ZONE,
    total_runs INTEGER DEFAULT 0,
    successful_runs INTEGER DEFAULT 0,
    failed_runs INTEGER DEFAULT 0,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Indexes for schedules
CREATE INDEX idx_schedules_test_id ON test_schedules(test_id);
CREATE INDEX idx_schedules_enabled ON test_schedules(is_enabled);
CREATE INDEX idx_schedules_next_run ON test_schedules(next_run_time);
CREATE INDEX idx_schedules_is_running ON test_schedules(is_running);

-- Schedule execution history
CREATE TABLE schedule_execution_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id UUID NOT NULL REFERENCES test_schedules(id) ON DELETE CASCADE,
    execution_id UUID REFERENCES test_executions(id) ON DELETE SET NULL,
    scheduled_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    duration_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for history
CREATE INDEX idx_schedule_history_schedule_id ON schedule_execution_history(schedule_id);
CREATE INDEX idx_schedule_history_status ON schedule_execution_history(status);
CREATE INDEX idx_schedule_history_scheduled_time ON schedule_execution_history(scheduled_time DESC);

-- Add comment
COMMENT ON TABLE test_schedules IS 'Cron-based test scheduling configuration';
COMMENT ON TABLE schedule_execution_history IS 'History of scheduled test executions';
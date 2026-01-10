-- Tests table
CREATE TABLE tests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    framework VARCHAR(50) NOT NULL,
    language VARCHAR(50) NOT NULL,
    priority VARCHAR(20),
    estimated_duration INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_tests_framework ON tests(framework);
CREATE INDEX idx_tests_active ON tests(is_active);
CREATE INDEX idx_tests_name ON tests(name);

-- Test executions table
CREATE TABLE test_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id UUID REFERENCES tests(id),
    status VARCHAR(50) NOT NULL,
    environment VARCHAR(100),
    browser VARCHAR(50),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration INTEGER,
    retry_count INTEGER DEFAULT 0,
    triggered_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_executions_test_id ON test_executions(test_id);
CREATE INDEX idx_executions_status ON test_executions(status);
CREATE INDEX idx_executions_start_time ON test_executions(start_time DESC);

-- Test results table
CREATE TABLE test_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID REFERENCES test_executions(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    stack_trace TEXT,
    screenshot_url VARCHAR(500),
    video_url VARCHAR(500),
    logs_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_results_execution_id ON test_results(execution_id);
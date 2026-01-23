-- =====================================================
-- V15: Quality History Tables
-- =====================================================
-- Purpose: Add historical tracking for long-term trends
-- Author: QA Framework Team
-- Date: 2026-01-22
-- =====================================================

-- Table: test_execution_history
CREATE TABLE IF NOT EXISTS test_execution_history (
    id BIGSERIAL PRIMARY KEY,

    -- Optional link to original execution (NO FK)
    execution_id UUID,
    test_name VARCHAR(500) NOT NULL,

    -- Execution data
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL,

    -- Failure info
    failure_reason TEXT,
    failure_type VARCHAR(100),

    -- Context
    browser VARCHAR(50),
    environment VARCHAR(50),
    executed_by VARCHAR(100),

    -- Timestamps
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_execution_history_test_name ON test_execution_history(test_name);
CREATE INDEX idx_test_execution_history_execution_id ON test_execution_history(execution_id) WHERE execution_id IS NOT NULL;
CREATE INDEX idx_test_execution_history_status ON test_execution_history(status);
CREATE INDEX IF NOT EXISTS idx_test_execution_history_executed_at ON test_execution_history(executed_at);
-- =====================================================

-- Table: test_quality_snapshots (MATCHES YOUR ENTITY!)
CREATE TABLE IF NOT EXISTS test_quality_snapshots (
    id BIGSERIAL PRIMARY KEY,

    -- Snapshot date (one per day)
    snapshot_date DATE NOT NULL UNIQUE,

    -- Test metrics (EXACTLY matches TestQualitySnapshot entity)
    total_tests INTEGER NOT NULL,
    active_tests INTEGER NOT NULL,
    stable_tests INTEGER NOT NULL,
    flaky_tests INTEGER NOT NULL,
    failing_tests INTEGER NOT NULL,

    -- Overall scores
    avg_pass_rate DECIMAL(5,2) NOT NULL,
    avg_flakiness_score DECIMAL(5,2) NOT NULL,
    overall_health_score DECIMAL(5,2) NOT NULL,

    -- Execution stats
    total_executions INTEGER NOT NULL,
    avg_execution_time_ms BIGINT NOT NULL,

    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_quality_snapshots_date ON test_quality_snapshots(snapshot_date);

COMMENT ON TABLE test_quality_snapshots IS 'Daily quality snapshots for long-term trending';

-- =====================================================

-- Table: test_failure_patterns
CREATE TABLE IF NOT EXISTS test_failure_patterns (
    id BIGSERIAL PRIMARY KEY,

    -- Pattern identification
    pattern_type VARCHAR(50) NOT NULL,
    test_name VARCHAR(500) NOT NULL,
    error_signature VARCHAR(200) NOT NULL,

    -- Occurrence tracking
    occurrences INTEGER NOT NULL DEFAULT 1,
    first_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Impact assessment (TEXT not TEXT[])
    impact_score DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    affected_browsers TEXT,
    affected_steps TEXT,

    -- Resolution tracking
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT test_failure_patterns_impact_score_range
        CHECK (impact_score >= 0 AND impact_score <= 100),
    UNIQUE(test_name, error_signature)
);

CREATE INDEX idx_failure_pattern_test ON test_failure_patterns(test_name);
CREATE INDEX idx_failure_pattern_type ON test_failure_patterns(pattern_type);
CREATE INDEX idx_failure_pattern_resolved ON test_failure_patterns(is_resolved);
CREATE INDEX idx_failure_pattern_impact ON test_failure_patterns(impact_score DESC);
CREATE INDEX idx_failure_pattern_detected ON test_failure_patterns(last_detected_at DESC);

COMMENT ON COLUMN test_failure_patterns.affected_browsers IS 'Semicolon-separated list of browsers';
COMMENT ON COLUMN test_failure_patterns.affected_steps IS 'Semicolon-separated list of test steps';

-- =====================================================
-- Cleanup Functions
-- =====================================================

CREATE OR REPLACE FUNCTION cleanup_old_execution_history(retention_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE deleted_count INTEGER;
BEGIN
    DELETE FROM test_execution_history WHERE execution_time < NOW() - (retention_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_snapshots(retention_days INTEGER DEFAULT 365)
RETURNS INTEGER AS $$
DECLARE deleted_count INTEGER;
BEGIN
    DELETE FROM test_quality_snapshots WHERE snapshot_date < CURRENT_DATE - retention_days;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_resolved_patterns(retention_days INTEGER DEFAULT 180)
RETURNS INTEGER AS $$
DECLARE deleted_count INTEGER;
BEGIN
    DELETE FROM test_failure_patterns
    WHERE is_resolved = TRUE AND resolved_at < NOW() - (retention_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
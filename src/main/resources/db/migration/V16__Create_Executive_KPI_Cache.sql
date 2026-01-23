-- Executive KPI Cache for fast dashboard loading
CREATE TABLE IF NOT EXISTS executive_kpi_cache (
    id BIGSERIAL PRIMARY KEY,

    -- Time period
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    period_type VARCHAR(20) NOT NULL, -- DAILY, WEEKLY, MONTHLY, QUARTERLY

    -- Quality KPIs
    overall_pass_rate DECIMAL(5,2),
    trend_direction VARCHAR(10), -- IMPROVING, DECLINING, STABLE
    quality_score DECIMAL(5,2),

    -- Execution KPIs
    total_executions BIGINT DEFAULT 0,
    avg_execution_time_ms BIGINT DEFAULT 0,
    flaky_test_count INTEGER DEFAULT 0,

    -- AI KPIs
    ai_accuracy_rate DECIMAL(5,2),
    ai_cost_total DECIMAL(15,2),
    ai_cost_per_fix DECIMAL(10,2),
    ai_suggestions_accepted INTEGER DEFAULT 0,
    ai_suggestions_rejected INTEGER DEFAULT 0,

    -- Resource KPIs
    peak_concurrent_executions INTEGER DEFAULT 0,
    resource_utilization_pct DECIMAL(5,2),

    -- Business Impact
    estimated_time_saved_hours DECIMAL(10,2),
    estimated_cost_saved_usd DECIMAL(15,2),

    -- Metadata
    cache_generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_final BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_exec_kpi_period ON executive_kpi_cache(period_type, period_start, period_end);
CREATE INDEX idx_exec_kpi_generated ON executive_kpi_cache(cache_generated_at);
CREATE UNIQUE INDEX idx_exec_kpi_unique ON executive_kpi_cache(period_type, period_start, period_end);

-- Table comment
COMMENT ON TABLE executive_kpi_cache IS 'Cached KPI metrics for executive dashboard performance';
-- Quality trend analysis with predictions
CREATE TABLE IF NOT EXISTS quality_trend_analysis (
    id BIGSERIAL PRIMARY KEY,

    -- Analysis period
    analysis_date DATE NOT NULL,
    suite_id BIGINT,

    -- Trend metrics
    pass_rate_7d_avg DECIMAL(5,2),
    pass_rate_30d_avg DECIMAL(5,2),
    pass_rate_trend VARCHAR(20),

    -- Volatility metrics
    pass_rate_volatility DECIMAL(5,2),
    flakiness_score DECIMAL(5,2),

    -- Failure analysis
    new_failures_count INTEGER DEFAULT 0,
    recurring_failures_count INTEGER DEFAULT 0,
    resolved_failures_count INTEGER DEFAULT 0,

    -- AI contribution
    ai_fixed_failures INTEGER DEFAULT 0,
    ai_prevented_failures INTEGER DEFAULT 0,

    -- Predictions
    predicted_next_7d_pass_rate DECIMAL(5,2),
    confidence_score DECIMAL(5,2),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(analysis_date, suite_id)
);

-- Indexes
CREATE INDEX idx_trend_date ON quality_trend_analysis(analysis_date);
CREATE INDEX idx_trend_suite ON quality_trend_analysis(suite_id);
CREATE INDEX idx_trend_analysis ON quality_trend_analysis(analysis_date, suite_id);

-- Comment
COMMENT ON TABLE quality_trend_analysis IS 'Daily quality trend analysis with predictions';
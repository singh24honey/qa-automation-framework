-- Executive alerts for proactive notifications
CREATE TABLE IF NOT EXISTS executive_alerts (
    id BIGSERIAL PRIMARY KEY,

    -- Alert classification
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,

    -- Alert details
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    metric_name VARCHAR(100),
    current_value DECIMAL(15,2),
    threshold_value DECIMAL(15,2),

    -- Context
    affected_entity_type VARCHAR(50),
    affected_entity_id BIGINT,

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    acknowledged_by VARCHAR(100),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,

    -- Timestamps
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_alert_status ON executive_alerts(status, severity);
CREATE INDEX idx_alert_type ON executive_alerts(alert_type, detected_at);
CREATE INDEX idx_alert_detected ON executive_alerts(detected_at);

-- Comment
COMMENT ON TABLE executive_alerts IS 'Executive alerts for threshold violations and critical issues';
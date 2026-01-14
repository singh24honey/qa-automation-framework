-- Notification history table
CREATE TABLE notification_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    execution_id UUID,
    test_id UUID,
    recipient VARCHAR(500),
    subject VARCHAR(500),
    content TEXT,
    sent_at TIMESTAMP,
    error_details TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Indexes
CREATE INDEX idx_notification_history_event ON notification_history(event);
CREATE INDEX idx_notification_history_channel ON notification_history(channel);
CREATE INDEX idx_notification_history_status ON notification_history(status);
CREATE INDEX idx_notification_history_execution_id ON notification_history(execution_id);
CREATE INDEX idx_notification_history_test_id ON notification_history(test_id);
CREATE INDEX idx_notification_history_sent_at ON notification_history(sent_at);

-- Add notification preferences to test_executions table
ALTER TABLE test_executions
ADD COLUMN notification_channels VARCHAR(255)[] DEFAULT '{}',
ADD COLUMN notification_recipients VARCHAR(255)[] DEFAULT '{}';

-- Add notification preferences to tests table
ALTER TABLE tests
ADD COLUMN notify_on_failure BOOLEAN DEFAULT true,
ADD COLUMN notify_on_success BOOLEAN DEFAULT false,
ADD COLUMN notification_channels VARCHAR(255)[] DEFAULT '{EMAIL}',
ADD COLUMN notification_recipients VARCHAR(255)[] DEFAULT '{}';
-- Add new columns to test_executions if they don't exist
ALTER TABLE test_executions
ADD COLUMN IF NOT EXISTS error_details TEXT,
ADD COLUMN IF NOT EXISTS screenshot_urls TEXT[],
ADD COLUMN IF NOT EXISTS log_url VARCHAR(500);

-- Add index for faster queries
CREATE INDEX IF NOT EXISTS idx_executions_status_start_time
ON test_executions(status, start_time DESC);
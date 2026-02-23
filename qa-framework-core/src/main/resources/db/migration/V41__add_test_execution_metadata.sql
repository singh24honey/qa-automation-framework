ALTER TABLE tests
    ADD COLUMN IF NOT EXISTS last_execution_status      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_execution_error       TEXT,
    ADD COLUMN IF NOT EXISTS last_executed_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS consecutive_failure_count  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_run_count            INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tests_last_status
    ON tests(last_execution_status)
    WHERE is_active = true;
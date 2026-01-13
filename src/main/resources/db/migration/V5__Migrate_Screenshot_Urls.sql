ALTER TABLE test_executions
    ALTER COLUMN screenshot_urls TYPE TEXT
    USING array_to_string(screenshot_urls, ',');

ALTER TABLE test_executions
    ADD COLUMN IF NOT EXISTS platform VARCHAR(50);
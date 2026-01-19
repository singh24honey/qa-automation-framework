ALTER TABLE test_executions
ALTER COLUMN screenshot_urls
TYPE json
USING screenshot_urls::json;
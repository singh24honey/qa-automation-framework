DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'test_executions'
    ) THEN

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'test_executions'
              AND column_name = 'screenshot_urls'
              AND data_type <> 'json'
        ) THEN
            ALTER TABLE test_executions
            ALTER COLUMN screenshot_urls
            TYPE json
            USING screenshot_urls::json;
        END IF;

    END IF;
END $$;
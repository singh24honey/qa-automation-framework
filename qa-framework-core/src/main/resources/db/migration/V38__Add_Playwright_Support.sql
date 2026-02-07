-- =====================================================
-- Migration V38: Add PLAYWRIGHT Support
-- Purpose: Update test_framework constraint to match Java enum
-- Fixes: DataIntegrityViolationException when saving PLAYWRIGHT tests
-- Dependencies: V32 (ai_generated_tests table)
-- =====================================================

-- Step 1: Drop old constraint
ALTER TABLE ai_generated_tests
DROP CONSTRAINT IF EXISTS valid_test_framework;

-- Step 2: Add new constraint with ALL framework values
-- Must match TestFramework.java enum exactly
ALTER TABLE ai_generated_tests
ADD CONSTRAINT valid_test_framework
CHECK (test_framework IN (
    'SELENIUM',
    'PLAYWRIGHT',       -- NEW: Week 11-13 addition
    'CUCUMBER_TESTNG',  -- Renamed from 'CUCUMBER'
    'TESTNG',          -- Legacy support
    'JUNIT',           -- Legacy support
    'CUCUMBER',        -- Legacy support
    'REST_ASSURED'     -- API testing
));

-- Step 3: Add Playwright-specific columns to test_executions
-- (For storing trace files, videos, browser type)
ALTER TABLE test_executions
ADD COLUMN IF NOT EXISTS trace_path VARCHAR(500),
ADD COLUMN IF NOT EXISTS video_path VARCHAR(500),
ADD COLUMN IF NOT EXISTS browser_type VARCHAR(50);

-- Step 4: Add locator strategy tracking
ALTER TABLE ai_generated_tests
ADD COLUMN IF NOT EXISTS locator_strategy VARCHAR(50);

-- Step 5: Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_ai_tests_framework
ON ai_generated_tests(test_framework);

CREATE INDEX IF NOT EXISTS idx_test_exec_browser
ON test_executions(browser_type)
WHERE browser_type IS NOT NULL;

-- Step 6: Comments for documentation
COMMENT ON COLUMN test_executions.trace_path IS 'Playwright trace file path for debugging';
COMMENT ON COLUMN test_executions.video_path IS 'Playwright video recording path';
COMMENT ON COLUMN test_executions.browser_type IS 'Browser used: chromium, firefox, webkit';
COMMENT ON COLUMN ai_generated_tests.locator_strategy IS 'Primary locator strategy: role, label, testid, css';

-- Step 7: Update view if it references test_framework
-- (The v_test_generation_metrics view should still work fine)
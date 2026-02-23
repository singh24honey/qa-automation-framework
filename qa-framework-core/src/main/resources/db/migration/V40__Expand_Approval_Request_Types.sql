-- Step 1: Drop the old check constraint
ALTER TABLE approval_requests
    DROP CONSTRAINT approval_requests_request_type_check;

-- Step 2: Add new constraint with all 7 values
ALTER TABLE approval_requests
    ADD CONSTRAINT approval_requests_request_type_check
    CHECK (request_type IN (
        'TEST_GENERATION',
        'TEST_MODIFICATION',
        'TEST_DELETION',
        'SELF_HEALING_FIX',
        'SELF_HEALING_MANUAL',
        'FLAKY_FIX',
        'FLAKY_MANUAL'
    ));
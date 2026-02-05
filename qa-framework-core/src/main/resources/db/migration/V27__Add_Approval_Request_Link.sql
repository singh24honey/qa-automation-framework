ALTER TABLE test_generation_context
ADD COLUMN approval_request_id UUID;

-- Add index for better query performance
CREATE INDEX idx_test_gen_context_approval
ON test_generation_context(approval_request_id);
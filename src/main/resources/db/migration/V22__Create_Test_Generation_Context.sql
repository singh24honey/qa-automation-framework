-- Links generated tests to the API context that was used
CREATE TABLE IF NOT EXISTS test_generation_context (
    id BIGSERIAL PRIMARY KEY,

    -- References
    test_id UUID NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    spec_id BIGINT REFERENCES api_specifications(id) ON DELETE SET NULL,
    endpoint_id BIGINT REFERENCES api_endpoints(id) ON DELETE SET NULL,

    -- Context used for generation
    prompt_with_context TEXT,           -- The enhanced prompt sent to AI
    context_type VARCHAR(50),            -- 'ENDPOINT', 'SCHEMA', 'FULL_SPEC'

    -- Schemas referenced
    schemas_used TEXT[],                 -- Array of schema names used

    -- Generation metadata
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ai_model VARCHAR(100),               -- Which AI model was used
    ai_cost DECIMAL(10, 4),              -- Cost of this generation

    -- Quality tracking
    approved BOOLEAN,                    -- Was this test approved?
    approval_notes TEXT,                 -- Why approved/rejected

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_test_gen_context_test_id ON test_generation_context(test_id);
CREATE INDEX idx_test_gen_context_spec_id ON test_generation_context(spec_id);
CREATE INDEX idx_test_gen_context_endpoint_id ON test_generation_context(endpoint_id);
CREATE INDEX idx_test_gen_context_generated_at ON test_generation_context(generated_at DESC);
CREATE INDEX idx_test_gen_context_approved ON test_generation_context(approved);

-- Comments
COMMENT ON TABLE test_generation_context IS 'Tracks which API context was used to generate each test';
COMMENT ON COLUMN test_generation_context.prompt_with_context IS 'Full prompt including API schema details';
COMMENT ON COLUMN test_generation_context.context_type IS 'Type of context: specific endpoint, schema, or full spec';
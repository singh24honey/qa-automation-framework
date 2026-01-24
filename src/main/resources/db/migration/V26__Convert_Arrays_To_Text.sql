-- Step 1: Drop GIN indexes on array columns
-- These indexes were created for array operations and won't work with TEXT

DROP INDEX IF EXISTS idx_api_endpoints_tags;
DROP INDEX IF EXISTS idx_api_schemas_enum_values;
DROP INDEX IF EXISTS idx_test_generation_context_schemas_used;

-- Step 2: Convert PostgreSQL array columns to TEXT columns
-- This allows us to store JSON strings instead of native arrays

-- Convert api_endpoints.tags from text[] to text
ALTER TABLE api_endpoints
    ALTER COLUMN tags TYPE text USING tags::text;

-- Convert api_schemas.enum_values from text[] to text
ALTER TABLE api_schemas
    ALTER COLUMN enum_values TYPE text USING enum_values::text;

-- Convert test_generation_context.schemas_used from text[] to text
ALTER TABLE test_generation_context
    ALTER COLUMN schemas_used TYPE text USING schemas_used::text;

-- Step 3: Add comments for clarity
COMMENT ON COLUMN api_endpoints.tags IS 'JSON array of tags stored as text';
COMMENT ON COLUMN api_schemas.enum_values IS 'JSON array of enum values stored as text';
COMMENT ON COLUMN test_generation_context.schemas_used IS 'JSON array of schema names stored as text';
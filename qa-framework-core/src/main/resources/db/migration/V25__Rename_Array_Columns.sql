-- Rename array columns to indicate they store JSON
-- This is optional - just for clarity

-- api_endpoints: tags is already TEXT, just a comment
COMMENT ON COLUMN api_endpoints.tags IS 'JSON array of tags as string';

-- api_schemas: enum_values is already TEXT, just a comment
COMMENT ON COLUMN api_schemas.enum_values IS 'JSON array of enum values as string';

-- test_generation_context: schemas_used is already TEXT, just a comment
COMMENT ON COLUMN test_generation_context.schemas_used IS 'JSON array of schema names as string';
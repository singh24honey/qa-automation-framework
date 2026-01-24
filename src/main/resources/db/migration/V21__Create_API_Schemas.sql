-- API schemas (reusable components from OpenAPI)
CREATE TABLE IF NOT EXISTS api_schemas (
    id BIGSERIAL PRIMARY KEY,

    -- Reference to parent spec
    spec_id BIGINT NOT NULL REFERENCES api_specifications(id) ON DELETE CASCADE,

    -- Schema identification
    schema_name VARCHAR(255) NOT NULL,  -- e.g., "User", "LoginRequest"
    schema_type VARCHAR(50),            -- object, array, string, etc.

    -- Schema definition
    schema_definition TEXT NOT NULL,    -- Full JSON schema

    -- Metadata
    description TEXT,
    is_enum BOOLEAN DEFAULT false,      -- True if this is an enum
    enum_values TEXT[],                 -- If enum, the possible values

    -- Usage tracking
    used_in_requests INTEGER DEFAULT 0,  -- Count of endpoints using this in requests
    used_in_responses INTEGER DEFAULT 0, -- Count of endpoints using this in responses

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_api_schemas_spec_id ON api_schemas(spec_id);
CREATE INDEX idx_api_schemas_name ON api_schemas(schema_name);
CREATE INDEX idx_api_schemas_type ON api_schemas(schema_type);

-- Unique constraint (one schema name per spec)
CREATE UNIQUE INDEX idx_api_schemas_unique ON api_schemas(spec_id, schema_name);

-- Comments
COMMENT ON TABLE api_schemas IS 'Reusable schema components from OpenAPI specifications';
COMMENT ON COLUMN api_schemas.schema_name IS 'Name from OpenAPI components/schemas section';
COMMENT ON COLUMN api_schemas.schema_definition IS 'Complete JSON schema definition';
COMMENT ON COLUMN api_schemas.used_in_requests IS 'Number of endpoints using this schema in request body';
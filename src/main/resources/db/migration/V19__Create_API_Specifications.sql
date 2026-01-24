-- API specifications storage
-- Stores uploaded OpenAPI/Swagger specifications
CREATE TABLE IF NOT EXISTS api_specifications (
    id BIGSERIAL PRIMARY KEY,

    -- Identification
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50),
    description TEXT,

    -- OpenAPI metadata
    openapi_version VARCHAR(20) NOT NULL, -- e.g., "3.0.0", "3.1.0"
    base_url VARCHAR(500),

    -- Content
    spec_content TEXT NOT NULL,  -- Full OpenAPI spec as JSON
    spec_format VARCHAR(10) NOT NULL, -- 'JSON' or 'YAML'

    -- Metadata
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    uploaded_by VARCHAR(255),
    is_active BOOLEAN DEFAULT true,

    -- Stats (updated when endpoints parsed)
    endpoint_count INTEGER DEFAULT 0,
    schema_count INTEGER DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_api_specs_name ON api_specifications(name);
CREATE INDEX idx_api_specs_active ON api_specifications(is_active);
CREATE INDEX idx_api_specs_uploaded_at ON api_specifications(uploaded_at DESC);

-- Comments
COMMENT ON TABLE api_specifications IS 'Stores OpenAPI/Swagger specifications for context-aware test generation';
COMMENT ON COLUMN api_specifications.spec_content IS 'Full OpenAPI specification as JSON string';
COMMENT ON COLUMN api_specifications.endpoint_count IS 'Number of endpoints extracted from this spec';
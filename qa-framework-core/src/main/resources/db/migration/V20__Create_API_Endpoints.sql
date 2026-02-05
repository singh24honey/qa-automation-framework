-- API endpoints extracted from OpenAPI specifications
CREATE TABLE IF NOT EXISTS api_endpoints (
    id BIGSERIAL PRIMARY KEY,

    -- Reference to parent spec
    spec_id BIGINT NOT NULL REFERENCES api_specifications(id) ON DELETE CASCADE,

    -- Endpoint definition
    path VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL, -- GET, POST, PUT, DELETE, PATCH, etc.
    operation_id VARCHAR(255),   -- OpenAPI operationId

    -- Documentation
    summary TEXT,
    description TEXT,
    tags TEXT[],                 -- Array of tags (e.g., ["auth", "users"])

    -- Request/Response schemas
    request_schema TEXT,         -- JSON schema for request body
    response_schema TEXT,        -- JSON schema for successful response

    -- Parameters
    path_parameters TEXT,        -- JSON array of path params
    query_parameters TEXT,       -- JSON array of query params
    header_parameters TEXT,      -- JSON array of header params

    -- Examples
    request_examples TEXT,       -- JSON with example requests
    response_examples TEXT,      -- JSON with example responses

    -- Security
    security_requirements TEXT,  -- JSON array of security requirements

    -- Metadata
    is_deprecated BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_api_endpoints_spec_id ON api_endpoints(spec_id);
CREATE INDEX idx_api_endpoints_path ON api_endpoints(path);
CREATE INDEX idx_api_endpoints_method ON api_endpoints(method);
CREATE INDEX idx_api_endpoints_tags ON api_endpoints USING GIN(tags);
CREATE INDEX idx_api_endpoints_deprecated ON api_endpoints(is_deprecated);

-- Unique constraint (one method per path per spec)
CREATE UNIQUE INDEX idx_api_endpoints_unique ON api_endpoints(spec_id, path, method);

-- Comments
COMMENT ON TABLE api_endpoints IS 'API endpoints extracted from OpenAPI specifications';
COMMENT ON COLUMN api_endpoints.path IS 'API path (e.g., /api/v1/users/{id})';
COMMENT ON COLUMN api_endpoints.tags IS 'OpenAPI tags for grouping endpoints';
COMMENT ON COLUMN api_endpoints.request_schema IS 'JSON schema for request body validation';
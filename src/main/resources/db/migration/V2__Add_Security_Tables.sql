-- API Keys table
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_value VARCHAR(64) NOT NULL UNIQUE,
    key_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    expires_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_api_keys_value ON api_keys(key_value);
CREATE INDEX idx_api_keys_active ON api_keys(is_active);
CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);

-- Request logs table (for security auditing)
CREATE TABLE request_logs (
    id BIGSERIAL PRIMARY KEY,
    api_key_id UUID REFERENCES api_keys(id),
    method VARCHAR(10),
    endpoint VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    status_code INTEGER,
    response_time_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_request_logs_api_key ON request_logs(api_key_id);
CREATE INDEX idx_request_logs_created_at ON request_logs(created_at DESC);
CREATE INDEX idx_request_logs_endpoint ON request_logs(endpoint);

-- Insert default API key for local development
INSERT INTO api_keys (key_value, key_hash, name, description, is_active, created_by)
VALUES (
    'dev-local-key-12345678901234567890',
    '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', -- SHA-256 hash
    'Development Key',
    'Default API key for local development - DO NOT USE IN PRODUCTION',
    true,
    'system'
);
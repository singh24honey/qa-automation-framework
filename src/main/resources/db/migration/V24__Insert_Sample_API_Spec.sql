-- Insert a sample API specification for development/testing
-- This is OPTIONAL - you can skip this in production

INSERT INTO api_specifications (
    name,
    version,
    description,
    openapi_version,
    base_url,
    spec_content,
    spec_format,
    uploaded_by,
    endpoint_count,
    schema_count
) VALUES (
    'Sample API',
    '1.0.0',
    'Sample OpenAPI specification for testing',
    '3.0.0',
    'https://api.example.com',
    '{
        "openapi": "3.0.0",
        "info": {
            "title": "Sample API",
            "version": "1.0.0"
        },
        "paths": {
            "/api/v1/login": {
                "post": {
                    "summary": "User login",
                    "requestBody": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "properties": {
                                        "email": {"type": "string"},
                                        "password": {"type": "string"}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }',
    'JSON',
    'system',
    0,
    0
);
# Security Guide

## API Key Authentication

This framework uses API key authentication for securing all endpoints.

### How It Works

1. Every API request must include an API key in the header
2. API keys are validated against the database
3. Each request is logged for security auditing
4. Rate limiting prevents abuse (100 requests/minute per key)

### Headers

All API requests must include:

X-API-Key: 518dcbf6-ef74-4821-a856-cb775ed414ee

### Creating API Keys

**Via API:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Application",
    "description": "API key for my app",
    "expiresInDays": 90
  }'
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "keyValue": "ABC123...XYZ789",
    "name": "My Application",
    "description": "API key for my app",
    "isActive": true,
    "createdAt": "2026-01-10T10:00:00Z",
    "expiresAt": "2026-04-10T10:00:00Z"
  },
  "message": "API key created successfully. Save the key value - it won't be shown again!"
}
```

⚠️ **IMPORTANT:** Save the `keyValue` immediately. It will never be shown again!

### Using API Keys

Include in every request:
```bash
# Get all tests
curl http://localhost:8080/api/v1/tests \
  -H "X-API-Key: ABC123...XYZ789"

# Create a test
curl -X POST http://localhost:8080/api/v1/tests \
  -H "X-API-Key: ABC123...XYZ789" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","framework":"SELENIUM","language":"java"}'
```

### Managing API Keys

**List all keys:**
```bash
curl http://localhost:8080/api/v1/auth/api-keys \
  -H "X-API-Key: ABC123...XYZ789"
```

**Revoke a key:**
```bash
curl -X DELETE http://localhost:8080/api/v1/auth/api-keys/{id} \
  -H "X-API-Key: ABC123...XYZ789"
```

### Rate Limiting

- **Limit:** 100 requests per minute per API key
- **Response when exceeded:** 429 Too Many Requests
```json
{
  "error": "Rate limit exceeded"
}
```

### Public Endpoints

These endpoints do NOT require API keys:

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`

### Security Best Practices

1. **Never commit API keys to Git**
    - Add to .gitignore
    - Use environment variables

2. **Rotate keys regularly**
    - Create new key
    - Update applications
    - Revoke old key

3. **Use different keys for different applications**
    - Easier to track usage
    - Easier to revoke if compromised

4. **Monitor usage**
    - Check request logs regularly
    - Look for unusual patterns

5. **Set expiration dates**
    - Force periodic rotation
    - Reduce risk of compromised keys

### Default Development Key

For **local development only**, a default key is pre-configured:

X-API-Key: dev-local-key-12345678901234567890

⚠️ **NEVER use this key in production!**

### Error Responses

**Missing API key:**
```json
{
  "error": "API key required"
}
```

**Invalid API key:**
```json
{
  "error": "Invalid API key"
}
```

**Rate limit exceeded:**
```json
{
  "error": "Rate limit exceeded"
}
```

### Request Logging

All authenticated requests are logged with:

- API key used
- HTTP method and endpoint
- IP address
- User agent
- Response status code
- Response time

Logs are stored in the `request_logs` table for auditing and analysis.
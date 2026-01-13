# Week 2 API Reference

Complete API documentation for all Week 2 endpoints.

---

## Base URL
```
Development: http://localhost:8080
Production: https://api.yourcompany.com
```

## Authentication

All endpoints (except API key creation) require an API key in the header:
```
X-API-Key: qaf_your_api_key_here
```

---

## API Endpoints

### Authentication & API Keys

#### Create API Key
```http
POST /api/v1/auth/api-keys
Content-Type: application/json

{
  "name": "string"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "string",
    "keyValue": "qaf_...",
    "createdAt": "2025-01-12T10:00:00Z",
    "expiresAt": null
  },
  "message": "API key created successfully"
}
```

**Status Codes:**
- `201` - Created
- `400` - Bad Request (validation error)

---

#### List API Keys
```http
GET /api/v1/auth/api-keys
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "string",
      "keyPreview": "qaf_****...****",
      "isActive": true,
      "lastUsedAt": "2025-01-12T09:30:00Z",
      "createdAt": "2025-01-10T10:00:00Z"
    }
  ]
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized

---

#### Revoke API Key
```http
DELETE /api/v1/auth/api-keys/{id}
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "message": "API key revoked successfully"
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized
- `404` - Not Found

---

### Test Management

#### Create Test
```http
POST /api/v1/tests
X-API-Key: qaf_...
Content-Type: application/json

{
  "name": "string",
  "description": "string (optional)",
  "framework": "SELENIUM|PLAYWRIGHT|CYPRESS",
  "language": "string",
  "priority": "LOW|MEDIUM|HIGH|CRITICAL",
  "content": "string"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "string",
    "framework": "SELENIUM",
    "priority": "HIGH",
    "isActive": true,
    "createdAt": "2025-01-12T10:00:00Z"
  },
  "message": "Test created successfully"
}
```

**Status Codes:**
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized

---

#### Get Test
```http
GET /api/v1/tests/{id}
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "string",
    "description": "string",
    "framework": "SELENIUM",
    "language": "json",
    "priority": "HIGH",
    "content": "...",
    "isActive": true,
    "createdAt": "2025-01-12T10:00:00Z",
    "updatedAt": "2025-01-12T10:00:00Z"
  }
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized
- `404` - Not Found

---

#### List Tests
```http
GET /api/v1/tests
X-API-Key: qaf_...

Query Parameters:
- framework (optional): SELENIUM|PLAYWRIGHT|CYPRESS
- priority (optional): LOW|MEDIUM|HIGH|CRITICAL
- isActive (optional): true|false
- page (optional): 0-based page number
- size (optional): items per page (default: 20)
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "string",
      "framework": "SELENIUM",
      "priority": "HIGH",
      "isActive": true,
      "createdAt": "2025-01-12T10:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 50,
    "totalPages": 3
  }
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized

---

#### Update Test
```http
PUT /api/v1/tests/{id}
X-API-Key: qaf_...
Content-Type: application/json

{
  "name": "string (optional)",
  "description": "string (optional)",
  "priority": "LOW|MEDIUM|HIGH|CRITICAL (optional)",
  "content": "string (optional)",
  "isActive": "boolean (optional)"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "string",
    "updatedAt": "2025-01-12T11:00:00Z"
  },
  "message": "Test updated successfully"
}
```

**Status Codes:**
- `200` - OK
- `400` - Bad Request
- `401` - Unauthorized
- `404` - Not Found

---

#### Delete Test
```http
DELETE /api/v1/tests/{id}
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "message": "Test deleted successfully"
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized
- `404` - Not Found

---

### Test Execution

#### Start Execution
```http
POST /api/v1/executions
X-API-Key: qaf_...
Content-Type: application/json

{
  "testId": "uuid",
  "browser": "CHROME|FIREFOX|EDGE",
  "headless": true,
  "configuration": {
    "timeout": 30000,
    "implicitWait": 10,
    "pageLoadTimeout": 30
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "executionId": "uuid",
    "testId": "uuid",
    "status": "PENDING",
    "browser": "CHROME",
    "startedAt": "2025-01-12T10:00:00Z"
  },
  "message": "Execution started"
}
```

**Status Codes:**
- `202` - Accepted
- `400` - Bad Request
- `401` - Unauthorized
- `404` - Test Not Found

---

#### Get Execution Status
```http
GET /api/v1/executions/{id}
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "testId": "uuid",
    "testName": "string",
    "status": "PASSED|FAILED|ERROR|RUNNING|PENDING",
    "browser": "CHROME",
    "startedAt": "2025-01-12T10:00:00Z",
    "completedAt": "2025-01-12T10:05:00Z",
    "duration": 300000,
    "errorMessage": "string (if failed)",
    "failureCategory": "TRANSIENT|ENVIRONMENTAL|TEST_ISSUE",
    "retryCount": 0,
    "artifacts": [
      {
        "filename": "screenshot.png",
        "type": "SCREENSHOT",
        "size": 102400
      }
    ]
  }
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized
- `404` - Not Found

---

#### List Executions
```http
GET /api/v1/executions
X-API-Key: qaf_...

Query Parameters:
- testId (optional): filter by test
- status (optional): PASSED|FAILED|ERROR|RUNNING|PENDING
- from (optional): start date (ISO 8601)
- to (optional): end date (ISO 8601)
- page (optional): 0-based page number
- size (optional): items per page (default: 20)
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "testId": "uuid",
      "testName": "string",
      "status": "PASSED",
      "browser": "CHROME",
      "startedAt": "2025-01-12T10:00:00Z",
      "completedAt": "2025-01-12T10:05:00Z",
      "duration": 300000
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized

---

#### Cancel Execution
```http
DELETE /api/v1/executions/{id}
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "message": "Execution cancelled"
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized
- `404` - Not Found
- `409` - Conflict (already completed)

---

### File Storage

#### Upload File
```http
POST /api/v1/storage/upload/{executionId}
X-API-Key: qaf_...
Content-Type: multipart/form-data

file: (binary)
type: LOG|SCREENSHOT|VIDEO|REPORT
```

**Response:**
```json
{
  "success": true,
  "data": {
    "filename": "string",
    "size": 102400,
    "type": "SCREENSHOT",
    "uploadedAt": "2025-01-12T10:00:00Z"
  },
  "message": "File uploaded successfully"
}
```

**Status Codes:**
- `201` - Created
- `400` - Bad Request (file too large, invalid type)
- `401` - Unauthorized

**Limits:**
- Max file size: 100MB
- Supported types: LOG, SCREENSHOT, VIDEO, REPORT

---

#### List Files
```http
GET /api/v1/storage/files/{executionId}
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "filename": "screenshot-1.png",
      "type": "SCREENSHOT",
      "size": 102400,
      "uploadedAt": "2025-01-12T10:00:00Z"
    }
  ]
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized

---

#### Download File
```http
GET /api/v1/storage/download/{executionId}/{filename}
X-API-Key: qaf_...
```

**Response:**
- Binary file content

**Headers:**
```
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="screenshot.png"
Content-Length: 102400
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized
- `404` - Not Found

---

#### Delete Files
```http
DELETE /api/v1/storage/files/{executionId}
X-API-Key: qaf_...

Query Parameters:
- filename (optional): delete specific file
```

**Response:**
```json
{
  "success": true,
  "message": "Files deleted successfully",
  "data": {
    "deletedCount": 5
  }
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized

---

#### Get Storage Statistics
```http
GET /api/v1/storage/stats
X-API-Key: qaf_...
```

**Response:**
```json
{
  "success": true,
  "data": {
    "totalFiles": 150,
    "totalSize": 524288000,
    "byType": {
      "SCREENSHOT": {
        "count": 100,
        "size": 204800000
      },
      "LOG": {
        "count": 30,
        "size": 10240000
      },
      "VIDEO": {
        "count": 15,
        "size": 307200000
      },
      "REPORT": {
        "count": 5,
        "size": 2048000
      }
    },
    "oldestFile": "2025-01-01T10:00:00Z",
    "newestFile": "2025-01-12T10:00:00Z"
  }
}
```

**Status Codes:**
- `200` - OK
- `401` - Unauthorized

---

## Error Responses

All errors follow this format:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "details": {
      "field": "Additional error details"
    }
  },
  "timestamp": "2025-01-12T10:00:00Z"
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_API_KEY` | 401 | API key is invalid or revoked |
| `MISSING_API_KEY` | 401 | API key not provided |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `RESOURCE_NOT_FOUND` | 404 | Requested resource not found |
| `EXECUTION_FAILED` | 500 | Test execution failed |
| `STORAGE_ERROR` | 500 | File storage operation failed |
| `DATABASE_ERROR` | 500 | Database operation failed |

---

## Rate Limiting

- **Default Limit:** 100 requests/minute per API key
- **Burst Limit:** 20 requests/second

**Rate Limit Headers:**
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 75
X-RateLimit-Reset: 1673524800
```

**Rate Limit Exceeded Response:**
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Try again in 30 seconds."
  },
  "timestamp": "2025-01-12T10:00:00Z"
}
```

**Status Code:** `429 - Too Many Requests`

---

## Pagination

List endpoints support pagination:

**Request:**
```
GET /api/v1/tests?page=0&size=20
```

**Response:**
```json
{
  "success": true,
  "data": [...],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

## Filtering & Sorting

**Filtering:**
```
GET /api/v1/executions?status=FAILED&testId=uuid
```

**Sorting:**
```
GET /api/v1/tests?sort=createdAt,desc
GET /api/v1/tests?sort=name,asc&sort=priority,desc
```

---

## Webhooks (Coming in Week 3)

Future support for webhooks on execution completion:
```json
{
  "event": "execution.completed",
  "executionId": "uuid",
  "status": "PASSED",
  "timestamp": "2025-01-12T10:00:00Z"
}
```

---

## SDK Examples

### cURL
```bash
# Create test
curl -X POST http://localhost:8080/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Test",
    "framework": "SELENIUM",
    "language": "json",
    "priority": "HIGH",
    "content": "{}"
  }'

# Execute test
curl -X POST http://localhost:8080/api/v1/executions \
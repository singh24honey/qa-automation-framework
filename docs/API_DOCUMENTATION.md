# API Documentation

## Base URL

http://localhost:8080/api/v1

## Endpoints

### Get All Tests
```http
GET /tests
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Login Test",
      "description": "Test description",
      "framework": "SELENIUM",
      "language": "java",
      "priority": "HIGH",
      "estimatedDuration": 60,
      "isActive": true
    }
  ],
  "timestamp": "2026-01-10T10:00:00Z"
}
```

### Get Test by ID
```http
GET /tests/{id}
```

### Create Test
```http
POST /tests
Content-Type: application/json

{
  "name": "Test Name",
  "description": "Description",
  "framework": "SELENIUM",
  "language": "java",
  "priority": "HIGH",
  "estimatedDuration": 60
}
```

### Update Test
```http
PUT /tests/{id}
Content-Type: application/json

{
  "name": "Updated Name",
  ...
}
```

### Delete Test
```http
DELETE /tests/{id}
```
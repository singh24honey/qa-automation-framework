# Create API key (if you don't have one)
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name":"Report Test Key"}'

export API_KEY="your-api-key-here"

# Test dashboard
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/reports/dashboard"

# Test with date range
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/reports/dashboard?startDate=2025-01-01&endDate=2025-01-15"

# Test stats
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/reports/stats"

# Test trends
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/reports/trends"

# Test browser stats
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/reports/browser-stats"
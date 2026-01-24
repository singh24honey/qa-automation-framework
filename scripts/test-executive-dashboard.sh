#!/bin/bash

echo "🧪 Testing Executive Dashboard APIs"
echo "===================================="

API_KEY=""
BASE_URL="http://localhost:8080/api/v1"

# Test 1: Get Executive Dashboard
echo ""
echo "Test 1: Get Executive Dashboard"
curl -X GET "$BASE_URL/executive/dashboard" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json"

# Test 2: Get Quality Trends
echo ""
echo ""
echo "Test 2: Get Quality Trends (Last 30 days)"
START_DATE=$(date -d "30 days ago" +%Y-%m-%d)
END_DATE=$(date +%Y-%m-%d)
curl -X GET "$BASE_URL/executive/trends?startDate=$START_DATE&endDate=$END_DATE" \
  -H "X-API-Key: $API_KEY"

# Test 3: Get Active Alerts
echo ""
echo ""
echo "Test 3: Get Active Alerts"
curl -X GET "$BASE_URL/executive/alerts" \
  -H "X-API-Key: $API_KEY"

# Test 4: Refresh KPI Cache
echo ""
echo ""
echo "Test 4: Manual KPI Refresh"
curl -X POST "$BASE_URL/executive/kpi/refresh" \
  -H "X-API-Key: $API_KEY"

echo ""
echo ""
echo "✅ All tests complete!"
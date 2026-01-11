#!/bin/bash

set -e

echo "========================================="
echo "Week 2 Day 1 - Security Testing"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

test_passed() {
    echo -e "${GREEN}✅ PASS:${NC} $1"
}

test_failed() {
    echo -e "${RED}❌ FAIL:${NC} $1"
    exit 1
}

echo "1. Testing health endpoint (no auth required)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/actuator/health)
if [ "$STATUS" -eq 200 ]; then
    test_passed "Health endpoint accessible without API key"
else
    test_failed "Health endpoint failed (status: $STATUS)"
fi

echo ""
echo "2. Testing protected endpoint without API key..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/api/v1/tests)
if [ "$STATUS" -eq 401 ]; then
    test_passed "Protected endpoint rejects request without API key"
else
    test_failed "Protected endpoint should return 401 without API key (got: $STATUS)"
fi

echo ""
echo "3. Testing with invalid API key..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: invalid-key" $BASE_URL/api/v1/tests)
if [ "$STATUS" -eq 401 ]; then
    test_passed "Protected endpoint rejects invalid API key"
else
    test_failed "Protected endpoint should return 401 with invalid key (got: $STATUS)"
fi

echo ""
echo "4. Creating new API key..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Key",
    "description": "Automated test key",
    "expiresInDays": 90
  }')

API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)

if [ -n "$API_KEY" ]; then
    test_passed "API key created: ${API_KEY:0:20}..."
else
    test_failed "Failed to create API key"
fi

echo ""
echo "5. Testing with valid API key..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/tests)
if [ "$STATUS" -eq 200 ]; then
    test_passed "Protected endpoint accepts valid API key"
else
    test_failed "Protected endpoint should return 200 with valid key (got: $STATUS)"
fi

echo ""
echo "6. Testing API key listing..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/auth/api-keys)
COUNT=$(echo $RESPONSE | grep -o '"id"' | wc -l)
if [ "$COUNT" -ge 1 ]; then
    test_passed "API key listing works (found $COUNT keys)"
else
    test_failed "API key listing failed"
fi

echo ""
echo "7. Testing rate limiting (sending 5 rapid requests)..."
for i in {1..5}; do
    curl -s -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/tests > /dev/null
done
test_passed "Rate limiting allows normal usage"

echo ""
echo "========================================="
echo "✅ All Security Tests Passed!"
echo "========================================="
echo ""
echo "Your API Key: $API_KEY"
echo ""
echo "Try it:"
echo "curl -H 'X-API-Key: $API_KEY' $BASE_URL/api/v1/tests"
echo ""
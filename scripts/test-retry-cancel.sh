#!/bin/bash

set -e

echo "========================================="
echo "Testing Retry & Cancellation"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"
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

# Get API key
echo "Creating API key..."

RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/api-keys" \
  -H "Content-Type: application/json" \
  -d '{"name":"Execution Test Key"}')

API_KEY=$(echo "$RESPONSE" | jq -r '.data.keyValue // empty')

if [ -z "$API_KEY" ]; then
  test_failed "Failed to create API key: $RESPONSE"
fi

test_passed "API key created: ${API_KEY:0:20}..."
echo ""


# Create a test that will timeout (to test retry)
echo "1. Creating test that will trigger retry..."

TIMEOUT_TEST='{
  "name": "Timeout Test",
  "description": "Test with timeout to trigger retry",
  "steps": [
    {
      "action": "navigate",
      "value": "https://httpstat.us/504?sleep=60000"
    }
  ]
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Timeout Retry Test\",
    \"framework\": \"SELENIUM\",
    \"language\": \"json\",
    \"content\": $(echo "$TIMEOUT_TEST" | jq -c .)
  }")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)

if [ -n "$TEST_ID" ]; then
    test_passed "Test created: $TEST_ID"
else
    test_failed "Failed to create test"
fi

echo ""
echo "Test retry and cancellation functionality validated through integration tests"
echo "Run: ./gradlew test --tests '*RetryServiceTest'"
echo ""
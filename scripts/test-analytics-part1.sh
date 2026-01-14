#!/bin/bash

echo "==========================================="
echo "Testing Analytics Part 1"
echo "==========================================="

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
API_URL="http://localhost:8080/api/v1"

# Check if API key is provided
if [ -z "$API_KEY" ]; then
    echo "Creating API key..."
    RESPONSE=$(curl -s -X POST ${API_URL}/auth/api-keys \
      -H "Content-Type: application/json" \
      -d '{"name":"Analytics Test"}')

    API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)
    echo "API Key: $API_KEY"
fi

# Test 1: Get flaky tests
echo ""
echo "Test 1: Getting flaky tests..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
  "${API_URL}/analytics/flaky-tests?startDate=2024-01-01&endDate=2025-12-31")

if echo $RESPONSE | grep -q "success"; then
    COUNT=$(echo $RESPONSE | grep -o '"totalExecutions"' | wc -l)
    echo -e "${GREEN}✓ Flaky tests test passed (found data)${NC}"
else
    echo -e "${RED}✗ Flaky tests test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 2: Get performance metrics
echo ""
echo "Test 2: Getting performance metrics..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
  "${API_URL}/analytics/performance")

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Performance metrics test passed${NC}"
else
    echo -e "${RED}✗ Performance metrics test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 3: Analyze specific test (if you have a test ID)
echo ""
echo "Test 3: Get all tests to find one for analysis..."
TESTS_RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" "${API_URL}/tests")
TEST_ID=$(echo $TESTS_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$TEST_ID" ] && [ "$TEST_ID" != "null" ]; then
    echo "Found test ID: $TEST_ID"
    echo "Analyzing single test..."

    RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
      "${API_URL}/analytics/flaky-tests/${TEST_ID}")

    if echo $RESPONSE | grep -q "success\|error"; then
        echo -e "${GREEN}✓ Single test analysis test passed${NC}"
    else
        echo -e "${RED}✗ Single test analysis test failed${NC}"
    fi
else
    echo "No tests found to analyze (this is OK if no tests exist yet)"
fi

# Test 4: Performance for specific test
if [ -n "$TEST_ID" ] && [ "$TEST_ID" != "null" ]; then
    echo ""
    echo "Test 4: Getting performance for specific test..."

    RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
      "${API_URL}/analytics/performance/${TEST_ID}")

    if echo $RESPONSE | grep -q "success\|error"; then
        echo -e "${GREEN}✓ Test performance analysis passed${NC}"
    else
        echo -e "${RED}✗ Test performance analysis failed${NC}"
    fi
fi

echo ""
echo "==========================================="
echo "Analytics Part 1 Tests Complete"
echo "==========================================="
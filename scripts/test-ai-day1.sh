#!/bin/bash

echo "==========================================="
echo "Testing Week 4 Day 1 - AI Abstraction"
echo "==========================================="

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
API_URL="http://localhost:8080/api/v1"

# Get API key
if [ -z "$API_KEY" ]; then
    echo "Creating API key..."
    RESPONSE=$(curl -s -X POST ${API_URL}/auth/api-keys \
      -H "Content-Type: application/json" \
      -d '{"name":"AI Day 1 Test"}')

    API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)
    echo "API Key: $API_KEY"
fi

# Test 1: AI Status
echo ""
echo "Test 1: Checking AI service status..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
  "${API_URL}/ai/status")

if echo $RESPONSE | grep -q "MOCK"; then
    echo -e "${GREEN}✓ AI status test passed (Provider: MOCK)${NC}"
else
    echo -e "${RED}✗ AI status test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 2: Generate Test
echo ""
echo "Test 2: Generating test code..."
RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  "${API_URL}/ai/generate-test" \
  -d '{
    "description": "Test user login functionality",
    "targetUrl": "https://example.com/login",
    "framework": "SELENIUM",
    "language": "java"
  }')

if echo $RESPONSE | grep -q "@Test"; then
    echo -e "${GREEN}✓ Test generation passed${NC}"
else
    echo -e "${RED}✗ Test generation failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 3: Analyze Failure
echo ""
echo "Test 3: Analyzing test failure..."
RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  "${API_URL}/ai/analyze-failure" \
  -d '{
    "testName": "LoginTest",
    "errorMessage": "TimeoutException: Element #loginButton not found",
    "stackTrace": "at LoginTest.testLogin(LoginTest.java:45)"
  }')

if echo $RESPONSE | grep -q "Failure Analysis"; then
    echo -e "${GREEN}✓ Failure analysis passed${NC}"
else
    echo -e "${RED}✗ Failure analysis failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 4: Suggest Fix
echo ""
echo "Test 4: Suggesting fix..."
RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  "${API_URL}/ai/suggest-fix" \
  -d '{
    "testCode": "driver.findElement(By.id(\"button\")).click();",
    "errorMessage": "TimeoutException: Element not found"
  }')

if echo $RESPONSE | grep -q "Fix Suggestions"; then
    echo -e "${GREEN}✓ Fix suggestion passed${NC}"
else
    echo -e "${RED}✗ Fix suggestion failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 5: Custom AI Task
echo ""
echo "Test 5: Executing custom AI task..."
RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  "${API_URL}/ai/execute" \
  -d '{
    "taskType": "CODE_REVIEW",
    "prompt": "Review this test for best practices",
    "maxTokens": 1000,
    "temperature": 0.7
  }')

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Custom AI task passed${NC}"
else
    echo -e "${RED}✗ Custom AI task failed${NC}"
    echo "Response: $RESPONSE"
fi

echo ""
echo "==========================================="
echo "Week 4 Day 1 Tests Complete"
echo "==========================================="
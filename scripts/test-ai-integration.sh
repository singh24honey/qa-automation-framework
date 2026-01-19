#!/bin/bash

# Week 4 Day 5 - AI Integration Test Script
# Tests all AI endpoints with all available providers

set -e

echo "========================================"
echo "AI Integration Tests"
echo "========================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test configuration
BASE_URL="http://localhost:8080"
TESTS_PASSED=0
TESTS_FAILED=0

# Function to test a provider
test_provider() {
    local provider=$1
    echo ""
    echo "========================================"
    echo "Testing Provider: $provider"
    echo "========================================"

    # Set environment
    export AI_PROVIDER=$provider

    # Start application
    echo "Starting application with $provider..."
    ./gradlew bootRun > /tmp/app-$provider.log 2>&1 &
    APP_PID=$!

    # Wait for startup
    echo "Waiting for application to start..."
    sleep 20

    # Check if app is running
    if ! kill -0 $APP_PID 2>/dev/null; then
        echo -e "${RED}✗ Application failed to start${NC}"
        cat /tmp/app-$provider.log
        return 1
    fi

    # Create API key
    echo "Creating API key..."
    API_KEY=$(curl -s -X POST $BASE_URL/api/v1/auth/api-keys \
        -H "Content-Type: application/json" \
        -d '{"name":"Test Key"}' | jq -r '.data.keyValue' 2>/dev/null)

    if [ -z "$API_KEY" ] || [ "$API_KEY" = "null" ]; then
        echo -e "${RED}✗ Failed to create API key${NC}"
        kill $APP_PID 2>/dev/null
        return 1
    fi

    echo "API Key: $API_KEY"

    # Test 1: Status endpoint
    echo ""
    echo "Test 1: GET /api/v1/ai/status"
    STATUS_RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
        $BASE_URL/api/v1/ai/status)

    if echo $STATUS_RESPONSE | jq -e '.success == true' > /dev/null; then
        echo -e "${GREEN}✓ Status endpoint works${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        echo "  Provider: $(echo $STATUS_RESPONSE | jq -r '.data.provider')"
        echo "  Available: $(echo $STATUS_RESPONSE | jq -r '.data.available')"
    else
        echo -e "${RED}✗ Status endpoint failed${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi

    # Test 2: Generate test
    echo ""
    echo "Test 2: POST /api/v1/ai/generate-test"
    GEN_RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
        -H "Content-Type: application/json" \
        $BASE_URL/api/v1/ai/generate-test \
        -d '{
            "description": "Test login functionality",
            "targetUrl": "https://example.com/login",
            "framework": "SELENIUM",
            "language": "java"
        }')

    if echo $GEN_RESPONSE | jq -e '.success == true and .data.success == true' > /dev/null; then
        echo -e "${GREEN}✓ Test generation works${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        TOKENS=$(echo $GEN_RESPONSE | jq -r '.data.tokensUsed')
        DURATION=$(echo $GEN_RESPONSE | jq -r '.data.durationMs')
        echo "  Tokens: $TOKENS, Duration: ${DURATION}ms"
    else
        echo -e "${RED}✗ Test generation failed${NC}"
        echo "  Response: $GEN_RESPONSE"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi

    # Test 3: Analyze failure
    echo ""
    echo "Test 3: POST /api/v1/ai/analyze-failure"
    ANALYZE_RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
        -H "Content-Type: application/json" \
        $BASE_URL/api/v1/ai/analyze-failure \
        -d '{
            "testName": "LoginTest",
            "errorMessage": "NoSuchElementException: #login-btn",
            "stackTrace": "at LoginTest.java:45",
            "testCode": "driver.findElement(By.id(\"login-btn\")).click();"
        }')

    if echo $ANALYZE_RESPONSE | jq -e '.success == true and .data.success == true' > /dev/null; then
        echo -e "${GREEN}✓ Failure analysis works${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ Failure analysis failed${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi

    # Test 4: Suggest fix
    echo ""
    echo "Test 4: POST /api/v1/ai/suggest-fix"
    FIX_RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" \
        -H "Content-Type: application/json" \
        $BASE_URL/api/v1/ai/suggest-fix \
        -d '{
            "testCode": "driver.findElement(By.id(\"btn\")).click();",
            "errorMessage": "ElementClickInterceptedException"
        }')

    if echo $FIX_RESPONSE | jq -e '.success == true and .data.success == true' > /dev/null; then
        echo -e "${GREEN}✓ Fix suggestion works${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ Fix suggestion failed${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi

    # Cleanup
    echo ""
    echo "Stopping application..."
    kill $APP_PID 2>/dev/null
    wait $APP_PID 2>/dev/null

    echo ""
    echo "Provider $provider tests complete"
}

# Main execution
echo "Starting AI Integration Tests"
echo ""

# Test Mock (always available)
test_provider "mock"

# Test Ollama if available
if command -v ollama &> /dev/null; then
    if ollama list | grep -q "codellama"; then
        test_provider "ollama"
    else
        echo -e "${YELLOW}⊘ Ollama model not available, skipping${NC}"
    fi
else
    echo -e "${YELLOW}⊘ Ollama not installed, skipping${NC}"
fi

# Test Bedrock if credentials available
if [ -n "$AWS_ACCESS_KEY_ID" ] && [ -n "$AWS_SECRET_ACCESS_KEY" ]; then
    test_provider "bedrock"
else
    echo -e "${YELLOW}⊘ AWS credentials not set, skipping Bedrock${NC}"
fi

# Summary
echo ""
echo "========================================"
echo "Test Summary"
echo "========================================"
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All AI integration tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
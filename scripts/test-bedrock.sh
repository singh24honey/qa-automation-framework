#!/bin/bash

echo "==========================================="
echo "Testing Bedrock AI Service"
echo "==========================================="

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

API_URL="${API_URL:-http://localhost:8080/api/v1}"

# ============================================
# Pre-flight Checks
# ============================================


# Check provider configuration
echo ""
echo "Checking AI provider configuration..."
if [ "$AI_PROVIDER" = "bedrock" ]; then
    echo -e "${GREEN}Provider: BEDROCK${NC}"

    if [ -z "$AWS_ACCESS_KEY_ID" ]; then
        echo -e "${RED}ERROR: AWS_ACCESS_KEY_ID not set${NC}"
        echo ""
        echo "Set AWS credentials:"
        echo "  export AWS_ACCESS_KEY_ID=your-key"
        echo "  export AWS_SECRET_ACCESS_KEY=your-secret"
        echo "  export AWS_REGION=us-east-1"
        echo ""
        echo "Or use Mock mode:"
        echo "  export AI_PROVIDER=mock"
        exit 1
    fi

    echo "AWS Region: ${AWS_REGION:-us-east-1}"
    echo -e "${GREEN}✓ AWS Credentials configured${NC}"
else
    echo -e "${YELLOW}Provider: ${AI_PROVIDER:-mock}${NC}"
    echo ""
    echo "To test with Bedrock, set:"
    echo "  export AI_PROVIDER=bedrock"
    echo "  export AWS_ACCESS_KEY_ID=your-key"
    echo "  export AWS_SECRET_ACCESS_KEY=your-secret"
fi

# ============================================
# Get or Create API Key
# ============================================

echo ""
echo -e "${BLUE}=== API Key Setup ===${NC}"

if [ -z "$API_KEY" ]; then
    echo "Creating API key..."
    RESPONSE=$(curl -s -X POST "${API_URL}/auth/api-keys" \
      -H "Content-Type: application/json" \
      -d '{"name":"Bedrock Test Key"}')

    API_KEY=$(echo "$RESPONSE" | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)

    if [ -z "$API_KEY" ]; then
        echo -e "${RED}ERROR: Failed to create API key${NC}"
        echo "Response: $RESPONSE"
        exit 1
    fi

    echo -e "${GREEN}✓ API Key created: ${API_KEY:0:20}...${NC}"
else
    echo -e "${GREEN}✓ Using existing API Key: ${API_KEY:0:20}...${NC}"
fi

# ============================================
# Test 1: Generate Test Code
# ============================================

echo ""
echo -e "${BLUE}=== Test 1: Generate Test Code ===${NC}"
echo "Sending request to generate Selenium test..."

RESPONSE=$(curl -s -X POST "${API_URL}/ai/generate-test" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Click the login button and verify user is logged in",
    "targetUrl": "https://example.com/login",
    "framework": "SELENIUM",
    "language": "java"
  }')

# Check for success
if echo "$RESPONSE" | grep -q '"success"[[:space:]]*:[[:space:]]*true'; then
    # Check if content exists in the nested response
    if echo "$RESPONSE" | grep -q '"content"'; then
        echo -e "${GREEN}✓ Test generation successful${NC}"

        # Try to extract and show snippet
        CONTENT=$(echo "$RESPONSE" | sed 's/.*"content":"\([^"]*\)".*/\1/' | head -c 300)
        if [ -n "$CONTENT" ] && [ "$CONTENT" != "$RESPONSE" ]; then
            echo ""
            echo "Generated code snippet:"
            echo "----------------------------------------"
            echo "$CONTENT..."
            echo "----------------------------------------"
        fi

        # Show token usage if available
        TOKENS=$(echo "$RESPONSE" | grep -o '"tokensUsed":[0-9]*' | cut -d':' -f2)
        if [ -n "$TOKENS" ]; then
            echo "Tokens used: $TOKENS"
        fi

        TEST1_PASSED=true
    else
        echo -e "${YELLOW}⚠ Response successful but no content generated${NC}"
        echo "Response: $RESPONSE"
        TEST1_PASSED=false
    fi
else
    echo -e "${RED}✗ Test generation failed${NC}"

    # Check for specific error messages
    if echo "$RESPONSE" | grep -q "INVALID_PAYMENT"; then
        echo -e "${RED}ERROR: AWS Payment method issue${NC}"
        echo "Fix: Add valid payment method in AWS Billing Console"
    elif echo "$RESPONSE" | grep -q "Access Denied\|AccessDenied"; then
        echo -e "${RED}ERROR: AWS Access Denied${NC}"
        echo "Fix: Check IAM permissions and Bedrock model access"
    elif echo "$RESPONSE" | grep -q "not available"; then
        echo -e "${YELLOW}AI Service not available${NC}"
        echo "This may be expected if using mock mode without AI configured"
    fi

    echo ""
    echo "Full response:"
    echo "$RESPONSE"
    TEST1_PASSED=false
fi

# ============================================
# Test 2: Analyze Failure
# ============================================

echo ""
echo -e "${BLUE}=== Test 2: Analyze Failure ===${NC}"
echo "Sending request to analyze test failure..."

RESPONSE=$(curl -s -X POST "${API_URL}/ai/analyze-failure" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "testName": "LoginTest.testSuccessfulLogin",
    "errorMessage": "NoSuchElementException: Unable to locate element: #login-button",
    "stackTrace": "org.openqa.selenium.NoSuchElementException: no such element\n  at LoginTest.java:45",
    "testCode": "@Test public void testLogin() { driver.findElement(By.id(\"login-button\")).click(); }"
  }')

if echo "$RESPONSE" | grep -q '"success"[[:space:]]*:[[:space:]]*true'; then
    if echo "$RESPONSE" | grep -q '"content"'; then
        echo -e "${GREEN}✓ Failure analysis successful${NC}"
        TEST2_PASSED=true
    else
        echo -e "${YELLOW}⚠ Response successful but no analysis generated${NC}"
        TEST2_PASSED=false
    fi
else
    echo -e "${RED}✗ Failure analysis failed${NC}"
    echo "Response: $(echo "$RESPONSE" | head -c 200)"
    TEST2_PASSED=false
fi

# ============================================
# Test 3: Suggest Fix
# ============================================

echo ""
echo -e "${BLUE}=== Test 3: Suggest Fix ===${NC}"
echo "Sending request to suggest fix..."

RESPONSE=$(curl -s -X POST "${API_URL}/ai/suggest-fix" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "testCode": "@Test public void testLogin() { driver.findElement(By.id(\"login-button\")).click(); }",
    "errorMessage": "TimeoutException: Element not clickable after 10 seconds"
  }')

if echo "$RESPONSE" | grep -q '"success"[[:space:]]*:[[:space:]]*true'; then
    if echo "$RESPONSE" | grep -q '"content"'; then
        echo -e "${GREEN}✓ Fix suggestion successful${NC}"
        TEST3_PASSED=true
    else
        echo -e "${YELLOW}⚠ Response successful but no suggestion generated${NC}"
        TEST3_PASSED=false
    fi
else
    echo -e "${RED}✗ Fix suggestion failed${NC}"
    echo "Response: $(echo "$RESPONSE" | head -c 200)"
    TEST3_PASSED=false
fi

# ============================================
# Summary
# ============================================

echo ""
echo "==========================================="
echo -e "${BLUE}Test Summary${NC}"
echo "==========================================="
echo "Provider: ${AI_PROVIDER:-mock}"
echo "Region: ${AWS_REGION:-us-east-1}"
echo ""

PASSED=0
FAILED=0

if [ "$TEST1_PASSED" = true ]; then
    echo -e "Test 1 (Generate Test):    ${GREEN}PASSED${NC}"
    ((PASSED++))
else
    echo -e "Test 1 (Generate Test):    ${RED}FAILED${NC}"
    ((FAILED++))
fi

if [ "$TEST2_PASSED" = true ]; then
    echo -e "Test 2 (Analyze Failure):  ${GREEN}PASSED${NC}"
    ((PASSED++))
else
    echo -e "Test 2 (Analyze Failure):  ${RED}FAILED${NC}"
    ((FAILED++))
fi

if [ "$TEST3_PASSED" = true ]; then
    echo -e "Test 3 (Suggest Fix):      ${GREEN}PASSED${NC}"
    ((PASSED++))
else
    echo -e "Test 3 (Suggest Fix):      ${RED}FAILED${NC}"
    ((FAILED++))
fi

echo ""
echo "==========================================="
echo -e "Results: ${GREEN}$PASSED passed${NC}, ${RED}$FAILED failed${NC}"
echo "==========================================="

# Exit with appropriate code
if [ $FAILED -gt 0 ]; then
    exit 1
else
    exit 0
fi
#!/bin/bash

# ============================================
# QA Dashboard - Sample Data Generator
# ============================================
# This script creates tests and executions to
# populate your dashboard with realistic data
# ============================================

set -e

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  QA Dashboard - Sample Data Generator${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""


# Step 2: Create API Key
echo -e "${YELLOW}Step 2: Creating API Key...${NC}"
API_KEY_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name": "Data Generator Key"}')

API_KEY=$(echo $API_KEY_RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)

if [ -z "$API_KEY" ]; then
    echo -e "${RED}❌ Failed to create API key${NC}"
    echo "Response: $API_KEY_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✅ API Key created: ${API_KEY:0:20}...${NC}"
echo ""

# Step 3: Create Test Cases
echo -e "${YELLOW}Step 3: Creating Test Cases...${NC}"

# Array to store test IDs
declare -a TEST_IDS

# Test 1: Login Test
echo "  Creating: Login Test..."
LOGIN_TEST='{
  "name": "Login Functionality Test",
  "description": "Verifies user can login with valid credentials",
  "framework": "SELENIUM",
  "language": "json",
  "priority": "CRITICAL",
  "content": "{\"name\":\"Login Test\",\"steps\":[{\"action\":\"navigate\",\"value\":\"https://example.com/login\"},{\"action\":\"sendKeys\",\"locator\":\"id=username\",\"value\":\"testuser\"},{\"action\":\"sendKeys\",\"locator\":\"id=password\",\"value\":\"password123\"},{\"action\":\"click\",\"locator\":\"id=login-btn\"},{\"action\":\"assertText\",\"locator\":\"css=.welcome\",\"value\":\"Welcome\"}]}"
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$LOGIN_TEST")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEST_ID" ]; then
    TEST_IDS+=("$TEST_ID")
    echo -e "    ${GREEN}✅ Created: $TEST_ID${NC}"
else
    echo -e "    ${RED}❌ Failed to create Login Test${NC}"
fi

# Test 2: Homepage Test
echo "  Creating: Homepage Test..."
HOMEPAGE_TEST='{
  "name": "Homepage Load Test",
  "description": "Verifies homepage loads correctly with all elements",
  "framework": "SELENIUM",
  "language": "json",
  "priority": "HIGH",
  "content": "{\"name\":\"Homepage Test\",\"steps\":[{\"action\":\"navigate\",\"value\":\"https://example.com\"},{\"action\":\"assertTitle\",\"value\":\"Example Domain\"},{\"action\":\"assertElement\",\"locator\":\"css=h1\"}]}"
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$HOMEPAGE_TEST")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEST_ID" ]; then
    TEST_IDS+=("$TEST_ID")
    echo -e "    ${GREEN}✅ Created: $TEST_ID${NC}"
else
    echo -e "    ${RED}❌ Failed to create Homepage Test${NC}"
fi

# Test 3: Search Functionality Test
echo "  Creating: Search Test..."
SEARCH_TEST='{
  "name": "Search Functionality Test",
  "description": "Verifies search returns relevant results",
  "framework": "SELENIUM",
  "language": "json",
  "priority": "HIGH",
  "content": "{\"name\":\"Search Test\",\"steps\":[{\"action\":\"navigate\",\"value\":\"https://example.com\"},{\"action\":\"sendKeys\",\"locator\":\"id=search\",\"value\":\"test query\"},{\"action\":\"click\",\"locator\":\"id=search-btn\"},{\"action\":\"assertElement\",\"locator\":\"css=.results\"}]}"
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$SEARCH_TEST")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEST_ID" ]; then
    TEST_IDS+=("$TEST_ID")
    echo -e "    ${GREEN}✅ Created: $TEST_ID${NC}"
else
    echo -e "    ${RED}❌ Failed to create Search Test${NC}"
fi

# Test 4: Registration Test
echo "  Creating: Registration Test..."
REG_TEST='{
  "name": "User Registration Test",
  "description": "Verifies new user registration flow",
  "framework": "SELENIUM",
  "language": "json",
  "priority": "CRITICAL",
  "content": "{\"name\":\"Registration Test\",\"steps\":[{\"action\":\"navigate\",\"value\":\"https://example.com/register\"},{\"action\":\"sendKeys\",\"locator\":\"id=email\",\"value\":\"test@example.com\"},{\"action\":\"sendKeys\",\"locator\":\"id=password\",\"value\":\"SecurePass123\"},{\"action\":\"click\",\"locator\":\"id=register-btn\"}]}"
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$REG_TEST")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEST_ID" ]; then
    TEST_IDS+=("$TEST_ID")
    echo -e "    ${GREEN}✅ Created: $TEST_ID${NC}"
else
    echo -e "    ${RED}❌ Failed to create Registration Test${NC}"
fi

# Test 5: Checkout Test
echo "  Creating: Checkout Test..."
CHECKOUT_TEST='{
  "name": "Checkout Process Test",
  "description": "Verifies complete checkout flow",
  "framework": "SELENIUM",
  "language": "json",
  "priority": "CRITICAL",
  "content": "{\"name\":\"Checkout Test\",\"steps\":[{\"action\":\"navigate\",\"value\":\"https://example.com/cart\"},{\"action\":\"click\",\"locator\":\"id=checkout-btn\"},{\"action\":\"sendKeys\",\"locator\":\"id=card-number\",\"value\":\"4111111111111111\"},{\"action\":\"click\",\"locator\":\"id=pay-btn\"}]}"
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$CHECKOUT_TEST")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEST_ID" ]; then
    TEST_IDS+=("$TEST_ID")
    echo -e "    ${GREEN}✅ Created: $TEST_ID${NC}"
else
    echo -e "    ${RED}❌ Failed to create Checkout Test${NC}"
fi

# Test 6: API Integration Test
echo "  Creating: API Test..."
API_TEST='{
  "name": "REST API Integration Test",
  "description": "Verifies API endpoints respond correctly",
  "framework": "REST_ASSURED",
  "language": "json",
  "priority": "HIGH",
  "content": "{\"name\":\"API Test\",\"steps\":[{\"action\":\"apiCall\",\"method\":\"GET\",\"url\":\"/api/users\"},{\"action\":\"assertStatus\",\"value\":\"200\"}]}"
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "$API_TEST")

TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -n "$TEST_ID" ]; then
    TEST_IDS+=("$TEST_ID")
    echo -e "    ${GREEN}✅ Created: $TEST_ID${NC}"
else
    echo -e "    ${RED}❌ Failed to create API Test${NC}"
fi

echo ""
echo -e "${GREEN}✅ Created ${#TEST_IDS[@]} test cases${NC}"
echo ""

# Step 4: Create Test Executions
echo -e "${YELLOW}Step 4: Creating Test Executions...${NC}"
echo "  This will create multiple executions with different statuses and browsers"
echo ""

BROWSERS=("CHROME")
EXECUTION_COUNT=0

for TEST_ID in "${TEST_IDS[@]}"; do
    echo "  Executing test: ${TEST_ID:0:8}..."

    # Create 3-5 executions per test with different browsers
    NUM_EXECUTIONS=$((RANDOM % 3 + 3))

    for ((i=1; i<=NUM_EXECUTIONS; i++)); do
        # Random browser
        BROWSER=${BROWSERS[$((RANDOM % ${#BROWSERS[@]}))]}

        EXEC_REQUEST="{
          \"testId\": \"$TEST_ID\",
          \"browser\": \"$BROWSER\",
          \"headless\": true,
          \"environment\": \"local\"
        }"

        RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/executions \
          -H "X-API-Key: $API_KEY" \
          -H "Content-Type: application/json" \
          -d "$EXEC_REQUEST")

        EXEC_ID=$(echo $RESPONSE | grep -o '"executionId":"[^"]*' | cut -d'"' -f4)

        if [ -n "$EXEC_ID" ] && [ "$EXEC_ID" != "null" ]; then
            ((EXECUTION_COUNT++))
            echo -n "."
        fi

        # Small delay between executions
        sleep 0.5
    done
done

echo ""
echo -e "${GREEN}✅ Created $EXECUTION_COUNT test executions${NC}"
echo ""

# Step 5: Verify Data
echo -e "${YELLOW}Step 5: Verifying Dashboard Data...${NC}"

# Get stats
STATS=$(curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/reports/stats")
echo "Stats Response: $STATS"
echo ""

# Get dashboard
DASHBOARD=$(curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/reports/dashboard")
echo "Dashboard Response (truncated): ${DASHBOARD:0:500}..."
echo ""

# Summary
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  Summary${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "  ${GREEN}✅ API Key: ${API_KEY:0:20}...${NC}"
echo -e "  ${GREEN}✅ Tests Created: ${#TEST_IDS[@]}${NC}"
echo -e "  ${GREEN}✅ Executions Created: $EXECUTION_COUNT${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "  1. Open your dashboard: http://localhost:3000"
echo "  2. Login with your API key"
echo "  3. You should now see data in:"
echo "     - Stats cards (Total, Passed, Failed, Errors)"
echo "     - Trend chart"
echo "     - Browser distribution chart"
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}  Data generation complete! 🎉${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Save API key for reference
echo "API_KEY=$API_KEY" > /tmp/qa-dashboard-api-key.txt
echo "API key saved to: /tmp/qa-dashboard-api-key.txt"
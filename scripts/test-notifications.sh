#!/bin/bash

echo "======================================"
echo "Testing Notification System"
echo "======================================"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
API_URL="http://localhost:8080/api/v1"
TEST_ID="123e4567-e89b-12d3-a456-426614174000"
EXECUTION_ID="123e4567-e89b-12d3-a456-426614174001"

# Check if API key is provided
if [ -z "$API_KEY" ]; then
    echo "Creating API key..."
    RESPONSE=$(curl -s -X POST ${API_URL}/auth/api-keys \
      -H "Content-Type: application/json" \
      -d '{"name":"Notification Test"}')

    API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)
    echo "API Key: $API_KEY"
fi

# Test 1: Send email notification
echo ""
echo "Test 1: Sending email notification..."
RESPONSE=$(curl -s -X POST ${API_URL}/notifications/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"event\": \"TEST_COMPLETED\",
    \"channels\": [\"EMAIL\"],
    \"testId\": \"$TEST_ID\",
    \"executionId\": \"$EXECUTION_ID\",
    \"testName\": \"Login Test\",
    \"recipients\": [\"test@example.com\"],
    \"subject\": \"Test Completed Successfully\",
    \"data\": {
      \"status\": \"PASSED\",
      \"duration\": 120,
      \"browser\": \"CHROME\"
    }
  }")

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Email notification test passed${NC}"
else
    echo -e "${RED}✗ Email notification test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 2: Send Slack notification
echo ""
echo "Test 2: Sending Slack notification..."
RESPONSE=$(curl -s -X POST ${API_URL}/notifications/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"event\": \"TEST_FAILED\",
    \"channels\": [\"SLACK\"],
    \"testId\": \"$TEST_ID\",
    \"executionId\": \"$EXECUTION_ID\",
    \"testName\": \"Login Test\",
    \"data\": {
      \"status\": \"FAILED\",
      \"errorDetails\": \"Element not found: #login-button\",
      \"duration\": 45
    }
  }")

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Slack notification test passed${NC}"
else
    echo -e "${RED}✗ Slack notification test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 3: Send webhook notification
echo ""
echo "Test 3: Sending webhook notification..."
RESPONSE=$(curl -s -X POST ${API_URL}/notifications/send \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"event\": \"TEST_STARTED\",
    \"channels\": [\"WEBHOOK\"],
    \"testId\": \"$TEST_ID\",
    \"executionId\": \"$EXECUTION_ID\",
    \"testName\": \"Login Test\",
    \"webhookUrl\": \"https://webhook.site/your-unique-url\",
    \"data\": {
      \"status\": \"RUNNING\",
      \"environment\": \"staging\",
      \"browser\": \"CHROME\"
    }
  }")

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Webhook notification test passed${NC}"
else
    echo -e "${RED}✗ Webhook notification test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 4: Get notification history
echo ""
echo "Test 4: Getting notification history..."
RESPONSE=$(curl -s -X GET "${API_URL}/notifications/history/$EXECUTION_ID" \
  -H "X-API-Key: $API_KEY")

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Notification history test passed${NC}"
else
    echo -e "${RED}✗ Notification history test failed${NC}"
    echo "Response: $RESPONSE"
fi

# Test 5: Retry failed notifications
echo ""
echo "Test 5: Testing retry mechanism..."
RESPONSE=$(curl -s -X POST ${API_URL}/notifications/retry-failed \
  -H "X-API-Key: $API_KEY")

if echo $RESPONSE | grep -q "success"; then
    echo -e "${GREEN}✓ Retry mechanism test passed${NC}"
else
    echo -e "${RED}✗ Retry mechanism test failed${NC}"
    echo "Response: $RESPONSE"
fi

echo ""
echo "======================================"
echo "Notification Tests Complete"
echo "======================================"
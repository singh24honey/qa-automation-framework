#!/bin/bash
set -e

echo "========================================="
echo "Week 2 Day 3 - Full Execution Test"
echo "========================================="
echo ""

BASE_URL="http://localhost:8080"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

test_passed() {
  echo -e "${GREEN}✅ PASS:${NC} $1"
}

test_failed() {
  echo -e "${RED}❌ FAIL:${NC} $1"
  exit 1
}

test_info() {
  echo -e "${YELLOW}ℹ INFO:${NC} $1"
}

# --------------------------------------------------
# Step 0: Create API Key
# --------------------------------------------------
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

# --------------------------------------------------
# Step 1: Create Test (content MUST be a STRING)
# --------------------------------------------------
echo "1. Creating test with Selenium script..."

TEST_SCRIPT='{
  "name": "Google Search Test",
  "description": "Navigate to Google and verify title",
  "steps": [
    { "action": "navigate", "value": "https://www.google.com" },
    { "action": "assertTitle", "value": "Google" }
  ]
}'

# Convert JSON → escaped JSON STRING
TEST_SCRIPT_STRING=$(echo "$TEST_SCRIPT" | jq -c . | jq -Rs .)

RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/tests" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Google Title Test\",
    \"description\": \"Test Google page title\",
    \"framework\": \"SELENIUM\",
    \"language\": \"json\",
    \"priority\": \"HIGH\",
    \"content\": $TEST_SCRIPT_STRING
  }")

TEST_ID=$(echo "$RESPONSE" | jq -r '.data.id // empty')

if [ -z "$TEST_ID" ]; then
  test_failed "Failed to create test: $RESPONSE"
fi

test_passed "Test created with ID: $TEST_ID"
echo ""

# --------------------------------------------------
# Step 2: Execute Test
# --------------------------------------------------
echo "Fetching executionId..."

MAX_WAIT=30
ELAPSED=0
EXECUTION_ID=""

while [ $ELAPSED -lt $MAX_WAIT ]; do
  EXECUTION_ID=$(curl -s -H "X-API-Key: $API_KEY" \
    "$BASE_URL/api/v1/executions?testId=$TEST_ID&limit=1" \
    | jq -r '.data[0].executionId // empty')

  if [ -n "$EXECUTION_ID" ]; then
    break
  fi

  sleep 2
  ELAPSED=$((ELAPSED + 2))
  echo -n "."
done

echo ""

if [ -z "$EXECUTION_ID" ]; then
  test_failed "Could not fetch executionId after waiting"
fi

test_passed "Execution ID resolved: $EXECUTION_ID"
# --------------------------------------------------
# Step 3: Poll Execution Status (NO trailing slash)
# --------------------------------------------------
echo "3. Waiting for execution to complete..."

MAX_WAIT=120
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT ]; do
  sleep 2
  ELAPSED=$((ELAPSED + 2))

  RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
    "$BASE_URL/api/v1/executions/$EXECUTION_ID")

  STATUS=$(echo "$RESPONSE" | jq -r '.data.status // empty')

  if [ -z "$STATUS" ]; then
    test_failed "Invalid execution response: $RESPONSE"
  fi

  if [[ "$STATUS" == "PASSED" || "$STATUS" == "FAILED" || "$STATUS" == "ERROR" ]]; then
    break
  fi

  echo -n "."
done

echo ""
echo ""

if [ "$STATUS" = "PASSED" ]; then
  test_passed "Execution completed successfully"

elif [ "$STATUS" = "FAILED" ]; then
  ERROR=$(echo "$RESPONSE" | jq -r '.data.errorMessage // "No error message"')
  test_info "Execution failed"
  echo "Error: $ERROR"

elif [ "$STATUS" = "ERROR" ]; then
  test_failed "Execution error occurred"

else
  test_failed "Execution timed out (status=$STATUS)"
fi

echo ""

# --------------------------------------------------
# Step 4: Check Artifacts
# --------------------------------------------------
echo "4. Checking artifacts..."

SCREENSHOT_COUNT=$(echo "$RESPONSE" | jq '.data.screenshotUrls | length // 0')
LOG_URL=$(echo "$RESPONSE" | jq -r '.data.logUrl // empty')

if [ "$SCREENSHOT_COUNT" -gt 0 ] || [ -n "$LOG_URL" ]; then
  test_passed "Artifacts captured"
else
  test_info "No artifacts captured"
fi

echo ""

# --------------------------------------------------
# Step 5: List Stored Files
# --------------------------------------------------
echo "5. Listing stored files..."

RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
  "$BASE_URL/api/v1/storage/files/$EXECUTION_ID")

FILE_COUNT=$(echo "$RESPONSE" | jq '.data | length // 0')

if [ "$FILE_COUNT" -gt 0 ]; then
  test_passed "Found $FILE_COUNT stored files"
else
  test_info "No files stored"
fi

echo ""

# --------------------------------------------------
# Summary
# --------------------------------------------------
echo "========================================="
echo "✅ Execution Test Complete!"
echo "========================================="
echo ""
echo "Summary:"
echo "  Test ID:       $TEST_ID"
echo "  Execution ID:  $EXECUTION_ID"
echo "  Status:        $STATUS"
echo "  Files:         $FILE_COUNT"
echo ""
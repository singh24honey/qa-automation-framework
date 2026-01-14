#!/bin/bash
set -e

echo "========================================="
echo "Week 3 Day 2 - Scheduling System Test"
echo "========================================="

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ PASS:${NC} $1"; }
fail() { echo -e "${RED}❌ FAIL:${NC} $1"; exit 1; }

# Create API key
echo "Creating API key..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name":"Scheduling Test"}')
API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)
[ -n "$API_KEY" ] && pass "API key created" || fail "API key creation"

# Create test
echo "Creating test..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"name":"Scheduled Test","framework":"SELENIUM","language":"json","priority":"HIGH","content":"{\"steps\":[]}"}')
TEST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
[ -n "$TEST_ID" ] && pass "Test created: $TEST_ID" || fail "Test creation"

# Validate cron
echo "Validating cron..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/schedules/validate-cron \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"cronExpression":"0 0 9 * * MON-FRI","timezone":"UTC"}')
echo $RESPONSE | grep -q '"valid":true' && pass "Cron validation" || fail "Cron validation"

# Create schedule
echo "Creating schedule..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/schedules \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Daily Test\",\"testId\":\"$TEST_ID\",\"cronExpression\":\"0 0 9 * * *\"}")
SCHEDULE_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
[ -n "$SCHEDULE_ID" ] && pass "Schedule created: $SCHEDULE_ID" || fail "Schedule creation"

# Get schedule
echo "Getting schedule..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/schedules/$SCHEDULE_ID)
echo $RESPONSE | grep -q '"Daily Test"' && pass "Schedule retrieved" || fail "Schedule retrieval"

# Disable schedule
echo "Disabling schedule..."
RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/schedules/$SCHEDULE_ID/disable)
echo $RESPONSE | grep -q '"success":true' && pass "Schedule disabled" || fail "Schedule disable"

# Enable schedule
echo "Enabling schedule..."
RESPONSE=$(curl -s -X POST -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/schedules/$SCHEDULE_ID/enable)
echo $RESPONSE | grep -q '"success":true' && pass "Schedule enabled" || fail "Schedule enable"

# Delete schedule
echo "Deleting schedule..."
RESPONSE=$(curl -s -X DELETE -H "X-API-Key: $API_KEY" $BASE_URL/api/v1/schedules/$SCHEDULE_ID)
echo $RESPONSE | grep -q '"success":true' && pass "Schedule deleted" || fail "Schedule deletion"

echo ""
echo "========================================="
echo "✅ All Scheduling Tests Passed!"
echo "========================================="
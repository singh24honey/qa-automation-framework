#!/bin/bash

# Week 5 Security Foundation Validation Script
# This script validates that all security components are working correctly

set -e

echo "=================================================="
echo "Week 5 Security Foundation Validation"
echo "=================================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:8080"
USER_ID=$(uuidgen 2>/dev/null || echo "123e4567-e89b-12d3-a456-426614174000")

echo "Using User ID: $USER_ID"
echo ""

# Function to make API calls
call_api() {
    local method=$1
    local endpoint=$2
    local data=$3

    curl -s -X $method \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $USER_ID" \
        -H "X-User-Role: QA_ENGINEER" \
        -d "$data" \
        "$BASE_URL$endpoint"
}

# Test 1: Health Check
echo "Test 1: Health Check"
echo "--------------------"
response=$(curl -s "$BASE_URL/api/v1/ai/health")
if echo "$response" | grep -q "AI Gateway is operational"; then
    echo -e "${GREEN}✓ Health check passed${NC}"
else
    echo -e "${RED}✗ Health check failed${NC}"
    exit 1
fi
echo ""

# Test 2: Rate Limit Status
echo "Test 2: Rate Limit Status"
echo "-------------------------"
response=$(curl -s -H "X-User-Id: $USER_ID" -H "X-User-Role: QA_ENGINEER" \
    "$BASE_URL/api/v1/ai/rate-limit-status")
if echo "$response" | grep -q "allowed"; then
    echo -e "${GREEN}✓ Rate limit status retrieved${NC}"
    echo "Response: $response" | jq '.' 2>/dev/null || echo "$response"
else
    echo -e "${RED}✗ Failed to get rate limit status${NC}"
fi
echo ""

# Test 3: Generate Test (Clean Request)
echo "Test 3: Generate Test - Clean Request"
echo "-------------------------------------"
clean_request='{
  "content": "Generate a test for login page",
  "framework": "JUnit 5",
  "language": "Java",
  "targetUrl": "https://example.com/login",
  "strictMode": true
}'

response=$(call_api POST "/api/v1/ai/generate-test" "$clean_request")
if echo "$response" | grep -q "success"; then
    echo -e "${GREEN}✓ Test generation succeeded${NC}"
    echo "Tokens used:" $(echo "$response" | jq '.data.tokensUsed' 2>/dev/null || echo "N/A")
else
    echo -e "${YELLOW}⚠ Test generation response (may be expected if using Mock AI):${NC}"
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
fi
echo ""

# Test 4: Block Request with AWS Keys
echo "Test 4: Security - Block AWS Keys"
echo "----------------------------------"
malicious_request='{
  "content": "Test with AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE",
  "framework": "JUnit 5",
  "language": "Java",
  "strictMode": true
}'

response=$(call_api POST "/api/v1/ai/generate-test" "$malicious_request")
if echo "$response" | grep -q "blockedBySecurityPolicy"; then
    echo -e "${GREEN}✓ Request correctly blocked for AWS keys${NC}"
else
    echo -e "${RED}✗ Security policy did not block AWS keys${NC}"
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
fi
echo ""

# Test 5: PII Sanitization
echo "Test 5: PII Sanitization"
echo "------------------------"
pii_request='{
  "content": "Test login for john@example.com with phone 555-123-4567",
  "framework": "JUnit 5",
  "language": "Java",
  "strictMode": false
}'

response=$(call_api POST "/api/v1/ai/generate-test" "$pii_request")
if echo "$response" | grep -q "sanitizationApplied"; then
    echo -e "${GREEN}✓ PII sanitization applied${NC}"
    echo "Redactions:" $(echo "$response" | jq '.data.redactionCount' 2>/dev/null || echo "N/A")
else
    echo -e "${YELLOW}⚠ Check PII sanitization manually${NC}"
fi
echo ""

# Test 6: Usage Statistics
echo "Test 6: Usage Statistics"
echo "-----------------------"
response=$(curl -s -H "X-User-Id: $USER_ID" -H "X-User-Role: QA_ENGINEER" \
    "$BASE_URL/api/v1/ai/usage-stats")
if echo "$response" | grep -q "tokensUsed"; then
    echo -e "${GREEN}✓ Usage statistics retrieved${NC}"
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
else
    echo -e "${RED}✗ Failed to get usage statistics${NC}"
fi
echo ""

# Test 7: Rate Limit Enforcement
echo "Test 7: Rate Limit Enforcement"
echo "------------------------------"
echo "Making 5 rapid requests to test rate limiting..."
for i in {1..5}; do
    response=$(call_api POST "/api/v1/ai/generate-test" "$clean_request")
    if echo "$response" | grep -q "rateLimitExceeded"; then
        echo -e "${GREEN}✓ Rate limit enforced after $i requests${NC}"
        break
    fi
    if [ $i -eq 5 ]; then
        echo -e "${YELLOW}⚠ Rate limit not hit in 5 requests (expected for QA_ENGINEER role with 200 limit)${NC}"
    fi
done
echo ""

# Summary
echo "=================================================="
echo "Validation Complete!"
echo "=================================================="
echo ""
echo "Manual Checks:"
echo "1. Check application logs for AUDIT_* entries"
echo "2. Verify Redis has rate limit keys: redis-cli KEYS 'rate_limit:*'"
echo "3. Check PostgreSQL for any audit tables (if implemented)"
echo ""
echo "Expected behavior:"
echo "✓ Clean requests succeed"
echo "✓ Requests with AWS keys are blocked"
echo "✓ PII is sanitized"
echo "✓ Rate limits are enforced"
echo "✓ Usage is tracked"
echo ""
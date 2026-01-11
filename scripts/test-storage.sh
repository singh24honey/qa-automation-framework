#!/bin/bash

set -e

echo "========================================="
echo "Week 2 Day 2 - Storage Testing"
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
echo "Creating API key for testing..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name":"Storage Test Key"}')

API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)

if [ -z "$API_KEY" ]; then
    test_failed "Failed to create API key"
fi

echo "Using API Key: ${API_KEY:0:20}..."
echo ""

# Test 1: Upload a file
echo "1. Testing file upload..."
echo "test image data" > /tmp/test-screenshot.png

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/storage/upload/test-exec-001 \
  -H "X-API-Key: $API_KEY" \
  -F "file=@/tmp/test-screenshot.png" \
  -F "type=SCREENSHOT")

if echo $RESPONSE | grep -q '"success":true'; then
    test_passed "File upload successful"
    FILENAME=$(echo $RESPONSE | grep -o '"filename":"[^"]*' | cut -d'"' -f4)
else
    test_failed "File upload failed"
fi

echo ""

# Test 2: List files
echo "2. Testing file listing..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
  $BASE_URL/api/v1/storage/files/test-exec-001)

if echo $RESPONSE | grep -q '"success":true'; then
    test_passed "File listing successful"
else
    test_failed "File listing failed"
fi

echo ""

# Test 3: Download file
echo "3. Testing file download..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-API-Key: $API_KEY" \
  "$BASE_URL/api/v1/storage/download/test-exec-001/$FILENAME")

if [ "$STATUS" -eq 200 ]; then
    test_passed "File download successful"
else
    test_failed "File download failed (status: $STATUS)"
fi

echo ""

# Test 4: Get storage stats
echo "4. Testing storage statistics..."
RESPONSE=$(curl -s -H "X-API-Key: $API_KEY" \
  $BASE_URL/api/v1/storage/stats)

if echo $RESPONSE | grep -q '"totalFiles"'; then
    test_passed "Storage statistics retrieved"
else
    test_failed "Storage statistics failed"
fi

echo ""

# Test 5: Delete files
echo "5. Testing file deletion..."
RESPONSE=$(curl -s -X DELETE \
  -H "X-API-Key: $API_KEY" \
  $BASE_URL/api/v1/storage/files/test-exec-001)

if echo $RESPONSE | grep -q '"success":true'; then
    test_passed "File deletion successful"
else
    test_failed "File deletion failed"
fi

echo ""

# Test 6: Manual cleanup
echo "6. Testing manual cleanup..."
RESPONSE=$(curl -s -X POST \
  -H "X-API-Key: $API_KEY" \
  $BASE_URL/api/v1/storage/cleanup)

if echo $RESPONSE | grep -q '"success":true'; then
    test_passed "Manual cleanup successful"
else
    test_failed "Manual cleanup failed"
fi

echo ""

# Cleanup
rm -f /tmp/test-screenshot.png

echo "========================================="
echo "✅ All Storage Tests Passed!"
echo "========================================="
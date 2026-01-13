#!/bin/bash

# Helper script to create sample tests for execution

set -e

echo "Creating sample Selenium tests..."

BASE_URL="${1:-http://localhost:8080}"

# Get API key
read -p "Enter API Key (or press Enter to create new): " API_KEY

if [ -z "$API_KEY" ]; then
    echo "Creating new API key..."
    RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/api-keys \
      -H "Content-Type: application/json" \
      -d '{"name":"Sample Test Key"}')

    API_KEY=$(echo $RESPONSE | grep -o '"keyValue":"[^"]*' | cut -d'"' -f4)
    echo "Created API key: $API_KEY"
fi

# Test 1: Google Search
echo ""
echo "Creating Test 1: Google Search..."

GOOGLE_TEST='{
  "name": "Google Search Test",
  "description": "Search on Google",
  "steps": [
    {
      "action": "navigate",
      "value": "https://www.google.com"
    },
    {
      "action": "assertTitle",
      "value": "Google"
    },
    {
      "action": "sendKeys",
      "locator": "name=q",
      "value": "Selenium WebDriver"
    },
    {
      "action": "click",
      "locator": "name=btnK"
    },
    {
      "action": "wait",
      "value": "2"
    }
  ]
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Google Search Test\",
    \"description\": \"Test Google search functionality\",
    \"framework\": \"SELENIUM\",
    \"language\": \"json\",
    \"priority\": \"HIGH\",
    \"estimatedDuration\": 30,
    \"content\": $(echo "$GOOGLE_TEST" | jq -c .)
  }")

TEST1_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "✅ Created test 1: $TEST1_ID"

# Test 2: Example.com
echo ""
echo "Creating Test 2: Example.com..."

EXAMPLE_TEST='{
  "name": "Example.com Test",
  "description": "Simple example.com test",
  "steps": [
    {
      "action": "navigate",
      "value": "https://example.com"
    },
    {
      "action": "assertTitle",
      "value": "Example Domain"
    },
    {
      "action": "assertText",
      "locator": "css=h1",
      "value": "Example Domain"
    }
  ]
}'

RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Example.com Test\",
    \"description\": \"Simple navigation test\",
    \"framework\": \"SELENIUM\",
    \"language\": \"json\",
    \"priority\": \"MEDIUM\",
    \"estimatedDuration\": 15,
    \"content\": $(echo "$EXAMPLE_TEST" | jq -c .)
  }")

TEST2_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "✅ Created test 2: $TEST2_ID"

echo ""
echo "========================================="
echo "Sample tests created successfully!"
echo "========================================="
echo ""
echo "Test 1 ID: $TEST1_ID"
echo "Test 2 ID: $TEST2_ID"
echo "API Key: $API_KEY"
echo ""
echo "To execute a test, run:"
echo ""
echo "curl -X POST $BASE_URL/api/v1/executions \\"
echo "  -H 'X-API-Key: $API_KEY' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"testId\":\"$TEST1_ID\",\"browser\":\"CHROME\",\"headless\":true}'"
echo ""
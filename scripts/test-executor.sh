#!/bin/bash

echo "Testing Selenium Executor..."

# Start application
./gradlew bootRun &
APP_PID=$!
sleep 25

# Create test script file
cat > /tmp/test-script.json << 'EOF'
{
  "name": "Google Search Test",
  "description": "Simple Google search test",
  "steps": [
    {
      "action": "navigate",
      "value": "https://www.google.com"
    },
    {
      "action": "assertTitle",
      "value": "Google"
    }
  ]
}
EOF

echo "Test script created. Now we need the execution service..."
echo "This will be completed in Step 5"

kill $APP_PID
#!/bin/bash

echo "Testing Retry Service..."

# Start application
./gradlew bootRun &
APP_PID=$!
sleep 25

# Create a test that will fail first, then pass (simulating transient failure)
# This is tested via integration tests

# Run integration tests
./gradlew test --tests "*RetryServiceTest"

RESULT=$?

kill $APP_PID

if [ $RESULT -eq 0 ]; then
    echo "✅ Retry service tests passed"
else
    echo "❌ Retry service tests failed"
    exit 1
fi
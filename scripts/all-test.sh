#!/bin/bash

echo "========================================="
echo "Week 2 Day 3 - Final Validation"
echo "========================================="
echo ""

# 1. Start infrastructure
cd docker
docker-compose up -d
sleep 10

# Verify Selenium Grid
curl -s http://localhost:4444/status | jq '.value.ready'
# Should return: true

cd ..

# 2. Start application
./gradlew bootRun &
APP_PID=$!
sleep 25

# 3. Run unit tests
echo "Running unit tests..."
./gradlew test --tests "*SeleniumTestExecutorTest"

# 4. Run integration tests
echo "Running integration tests..."
./gradlew test --tests "*TestExecutionIntegrationTest"

# 5. Create sample tests
echo "Creating sample tests..."
./scripts/create-sample-test.sh

# 6. Run full execution test
echo "Running full execution test..."
./scripts/test-execution-full.sh

# 7. Verify artifacts were created
echo ""
echo "Checking artifacts..."
ls -R ~/qa-framework/artifacts/ | head -20

# 8. Stop application
kill $APP_PID

echo ""
echo "========================================="
echo "🎉 Week 2 Day 3 Complete!"
echo "========================================="
````
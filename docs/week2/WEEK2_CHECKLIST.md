# Week 2 Completion Checklist

Use this checklist to verify Week 2 is complete before moving to Week 3.

---

## Infrastructure ✅

- [ ] Docker is installed and running
- [ ] PostgreSQL container is up (`qa-postgres`)
- [ ] Redis container is up (`qa-redis`)
- [ ] Selenium Hub is running (`selenium-hub`)
- [ ] Chrome node is connected (`selenium-chrome`)
- [ ] Firefox node is connected (optional: `selenium-firefox`)
- [ ] All containers healthy: `docker ps` shows all as "healthy" or "up"

**Verify:**
```bash
docker ps
curl http://localhost:4444/status | jq '.value.ready'
```

---

## Database ✅

- [ ] PostgreSQL is accessible
- [ ] Database `qa_framework` exists
- [ ] All Flyway migrations applied (V1, V2, V3, V4)
- [ ] Tables created: `tests`, `test_executions`, `api_keys`, `request_logs`, `storage_metadata`
- [ ] Indexes created (10+ indexes)
- [ ] Connection pool configured (HikariCP)

**Verify:**
```bash
docker exec qa-postgres psql -U qa_user -d qa_framework -c "\dt"
docker exec qa-postgres psql -U qa_user -d qa_framework -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public';"
```

---

## Application ✅

- [ ] Application builds successfully: `./gradlew build`
- [ ] All unit tests pass: `./gradlew test`
- [ ] Application starts: `./gradlew bootRun`
- [ ] Health endpoint returns UP: `curl http://localhost:8080/actuator/health`
- [ ] Metrics endpoint accessible: `curl http://localhost:8080/actuator/metrics`

**Verify:**
```bash
./gradlew clean build
./gradlew bootRun &
sleep 30
curl http://localhost:8080/actuator/health
```

---

## Day 1: Security & Authentication ✅

- [ ] API key creation endpoint works
- [ ] API keys are SHA-256 hashed in database
- [ ] API key authentication filter active
- [ ] Unauthorized requests return 401
- [ ] Request logging captures all API calls
- [ ] API key listing works
- [ ] API key revocation works

**Verify:**
```bash
# Create key
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Key"}')
API_KEY=$(echo $RESPONSE | jq -r '.data.keyValue')

# Test authentication
curl -H "X-API-Key: $API_KEY" http://localhost:8080/api/v1/tests

# Test without key (should fail)
curl http://localhost:8080/api/v1/tests
```

**Files to check:**
- [ ] `ApiKeyService.java` exists
- [ ] `ApiKeyFilter.java` exists
- [ ] `SecurityConfig.java` exists
- [ ] `V1__Initial_Schema.sql` has `api_keys` table

---

## Day 2: File Storage ✅

- [ ] Storage service configured (local or S3)
- [ ] File upload endpoint works
- [ ] File download endpoint works
- [ ] File listing works
- [ ] File deletion works
- [ ] Storage statistics endpoint works
- [ ] Multiple file types supported (LOG, SCREENSHOT, VIDEO, REPORT)
- [ ] Storage metadata tracked in database

**Verify:**
```bash
# Upload file
echo "test" > /tmp/test.txt
curl -X POST "http://localhost:8080/api/v1/storage/upload/test-exec" \
  -H "X-API-Key: $API_KEY" \
  -F "file=@/tmp/test.txt" \
  -F "type=LOG"

# List files
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/storage/files/test-exec"

# Get stats
curl -H "X-API-Key: $API_KEY" \
  "http://localhost:8080/api/v1/storage/stats"
```

**Files to check:**
- [ ] `StorageService.java` interface exists
- [ ] `LocalStorageService.java` exists
- [ ] `S3StorageService.java` exists
- [ ] `StorageController.java` exists
- [ ] `V2__Storage_Tables.sql` has `storage_metadata` table

---

## Day 3: Test Execution ✅

- [ ] Test CRUD operations work
- [ ] Test execution endpoint works
- [ ] Selenium WebDriver initializes
- [ ] Tests execute on Selenium Grid
- [ ] Screenshots captured on failure
- [ ] Execution status tracked
- [ ] Execution history retrievable
- [ ] Multiple browsers supported

**Verify:**
```bash
# Create test
TEST_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/tests \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sample Test",
    "framework": "SELENIUM",
    "language": "json",
    "priority": "HIGH",
    "content": "{\"steps\": []}"
  }')
TEST_ID=$(echo $TEST_RESPONSE | jq -r '.data.id')

# Execute test
curl -X POST http://localhost:8080/api/v1/executions \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"testId\": \"$TEST_ID\",
    \"browser\": \"CHROME\",
    \"headless\": true
  }"

# Check executions
curl -H "X-API-Key: $API_KEY" \
  http://localhost:8080/api/v1/executions
```

**Files to check:**
- [ ] `TestService.java` exists
- [ ] `ExecutionService.java` exists
- [ ] `SeleniumExecutor.java` exists
- [ ] `TestController.java` exists
- [ ] `ExecutionController.java` exists
- [ ] `V3__Execution_Tables.sql` has `test_executions` table

---

## Day 4: Retry Logic & Error Handling ✅

- [ ] Retry service implemented
- [ ] Failure analyzer categorizes errors
- [ ] Exponential backoff configured
- [ ] Max retry attempts enforced
- [ ] Retry count tracked in database
- [ ] Failure categories stored
- [ ] Error messages captured

**Verify:**
```bash
# Check retry configuration
grep -A 5 "retry:" src/main/resources/application-dev.yml

# Check failure categories in code
grep -r "FailureCategory" src/main/java/
```

**Files to check:**
- [ ] `RetryService.java` exists
- [ ] `FailureAnalyzer.java` exists
- [ ] `FailureCategory.java` enum exists
- [ ] Retry config in `application-dev.yml`

---

## Day 5: Integration & Polish ✅

- [ ] End-to-end integration tests pass
- [ ] Performance tests pass
- [ ] Database indexes created
- [ ] Connection pool monitoring enabled
- [ ] Documentation complete
- [ ] Validation script runs successfully

**Verify:**
```bash
# Run E2E tests
./gradlew test --tests "*Week2EndToEndTest"

# Run performance tests
./gradlew test --tests "*PerformanceTest"

# Run validation script
chmod +x scripts/validate-week2-complete.sh
./scripts/validate-week2-complete.sh
```

**Files to check:**
- [ ] `Week2EndToEndTest.java` exists
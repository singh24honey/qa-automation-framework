# Week 1 Testing Checklist

## Infrastructure Tests

- [ ] Docker services start successfully
```bash
  docker-compose ps
```

- [ ] PostgreSQL is accessible
```bash
  docker exec -it qa-postgres psql -U qa_user -d qa_framework -c "SELECT 1;"
```

- [ ] Redis is accessible
```bash
  docker exec -it qa-redis redis-cli ping
```

- [ ] Selenium Grid is running
    - Open: http://localhost:4444
    - Should show 1 Chrome node

## Application Tests

- [ ] Application starts without errors
```bash
  ./gradlew bootRun
```

- [ ] Health endpoint returns UP
```bash
  curl http://localhost:8080/actuator/health
```

- [ ] Database migrations applied
    - Check logs for "Flyway"
    - Verify tables exist

## API Tests

- [ ] GET /api/v1/tests returns empty array
- [ ] POST /api/v1/tests creates a test
- [ ] GET /api/v1/tests returns created test
- [ ] GET /api/v1/tests/{id} returns specific test
- [ ] PUT /api/v1/tests/{id} updates test
- [ ] DELETE /api/v1/tests/{id} soft deletes test
- [ ] Error handling works (404 for invalid ID)

## Unit Tests

- [ ] All unit tests pass
```bash
  ./gradlew test
```

- [ ] Code coverage > 80%
```bash
  ./gradlew jacocoTestReport
  # Check: build/reports/jacoco/test/html/index.html
```

## Integration Tests

- [ ] Integration tests pass
```bash
  ./gradlew integrationTest
```

## Storage Tests

- [ ] Artifact directories created
```bash
  ls ~/qa-framework/artifacts/
  # Should show: screenshots, videos, logs, reports
```

- [ ] Application logs to file
```bash
  ls ~/qa-framework/artifacts/logs/
  # Should show: application.log
```

## Final Validation

- [ ] Restart application - everything still works
- [ ] Stop and start Docker services - no errors
- [ ] Can create, read, update, delete tests via API
- [ ] All tests pass
- [ ] Documentation is complete
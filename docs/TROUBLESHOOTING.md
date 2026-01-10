# Troubleshooting Guide

## Common Issues

### 1. Application won't start

**Symptom:** Application fails to start with connection errors

**Solution:**
```bash
# Check if Docker services are running
docker ps

# Restart Docker services
cd docker
docker-compose down
docker-compose up -d

# Wait 30 seconds, then restart application
./gradlew bootRun
```

### 2. Database connection failed

**Symptom:** `Connection refused` or `Connection timeout`

**Solution:**
```bash
# Check PostgreSQL status
docker logs qa-postgres

# Test connection manually
docker exec -it qa-postgres psql -U qa_user -d qa_framework -c "SELECT 1;"

# If fails, recreate container
docker-compose down -v
docker-compose up -d postgres
```

### 3. Redis connection failed

**Symptom:** `Cannot get Jedis connection`

**Solution:**
```bash
# Check Redis
docker exec -it qa-redis redis-cli ping
# Should return: PONG

# Restart if needed
docker-compose restart redis
```

### 4. Flyway migration failed

**Symptom:** Migration errors on startup

**Solution:**
```bash
# Check migration files
ls src/main/resources/db/migration/

# Manually repair (if needed)
./gradlew flywayRepair

# Or clean and migrate
./gradlew flywayClean flywayMigrate
```

### 5. Port already in use

**Symptom:** `Address already in use`

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill the process or change port in application.yml
server:
  port: 8081
```

### 6. Out of memory

**Symptom:** `java.lang.OutOfMemoryError`

**Solution:**
```bash
# Increase heap size
export JAVA_OPTS="-Xms512m -Xmx2048m"
./gradlew bootRun

# Or in gradle.properties:
org.gradle.jvmargs=-Xmx2048m
```

## Health Checks
```bash
# Application health
curl http://localhost:8080/actuator/health

# Database
docker exec -it qa-postgres pg_isready

# Redis
docker exec -it qa-redis redis-cli ping

# Selenium Grid
curl http://localhost:4444/status
```
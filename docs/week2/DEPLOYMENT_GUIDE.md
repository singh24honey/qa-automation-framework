# Week 2 Deployment Guide

Complete guide for deploying the QA Automation Framework.

---

## Prerequisites

### Required Software
- **Java:** 17 or higher
- **Docker:** 20.10 or higher
- **Docker Compose:** 1.29 or higher
- **Git:** 2.30 or higher
- **Gradle:** 8.x (or use wrapper)

### System Requirements
- **CPU:** 4+ cores recommended
- **RAM:** 8GB minimum, 16GB recommended
- **Disk:** 20GB minimum free space
- **Network:** Stable internet for Docker image pulls

---

## Local Development Setup

### 1. Clone Repository
```bash
git clone https://github.com/yourcompany/qa-automation-framework.git
cd qa-automation-framework
```

### 2. Start Infrastructure
```bash
cd docker
docker-compose up -d

# Verify all services are running
docker-compose ps

# Should see:
# - qa-postgres (PostgreSQL)
# - qa-redis (Redis)
# - selenium-hub (Selenium Grid Hub)
# - selenium-chrome (Chrome node)
# - selenium-firefox (Firefox node)
```

### 3. Configure Application
```bash
# Copy environment template
cp .env.example .env

# Edit configuration
nano .env
```

**`.env` file:**
```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/qa_framework
DATABASE_USERNAME=qa_user
DATABASE_PASSWORD=qa_password

# Storage
STORAGE_TYPE=local
STORAGE_LOCAL_BASE_PATH=./test-artifacts

# Selenium
SELENIUM_GRID_URL=http://localhost:4444

# Application
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
```

### 4. Build Application
```bash
# Build without tests (faster)
./gradlew build -x test

# Build with tests
./gradlew build
```

### 5. Run Database Migrations
```bash
# Migrations run automatically on startup
# Or run manually:
./gradlew flywayMigrate
```

### 6. Start Application
```bash
# Option 1: Using Gradle
./gradlew bootRun

# Option 2: Using JAR
java -jar build/libs/qa-automation-framework-1.0.0.jar

# Option 3: With custom profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 7. Verify Deployment
```bash
# Health check
curl http://localhost:8080/actuator/health

# Should return:
# {"status":"UP"}

# Create first API key
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name":"Development Key"}'
```

---

## Docker Deployment

### Build Docker Image
```bash
# Create Dockerfile
cat > Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/qa-automation-framework-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Build image
docker build -t qa-automation-framework:1.0.0 .

# Test image
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  qa-automation-framework:1.0.0
```

### Docker Compose (Full Stack)
```yaml
# docker-compose.production.yml
version: '3.8'

services:
  app:
    image: qa-automation-framework:1.0.0
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DATABASE_URL=jdbc:postgresql://postgres:5432/qa_framework
      - DATABASE_USERNAME=qa_user
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - SELENIUM_GRID_URL=http://selenium-hub:4444
      - STORAGE_TYPE=local
      - STORAGE_LOCAL_BASE_PATH=/app/artifacts
    volumes:
      - ./artifacts:/app/artifacts
    depends_on:
      - postgres
      - redis
      - selenium-hub
    restart: unless-stopped

  postgres:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=qa_framework
      - POSTGRES_USER=qa_user
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    restart: unless-stopped

  selenium-hub:
    image: selenium/hub:4.15.0
    ports:
      - "4444:4444"
    restart: unless-stopped

  selenium-chrome:
    image: selenium/node-chrome:4.15.0
    environment:
      - SE_EVENT_BUS_HOST=selenium-hub
      - SE_EVENT_BUS_PUBLISH_PORT=4442
      - SE_EVENT_BUS_SUBSCRIBE_PORT=4443
      - SE_NODE_MAX_SESSIONS=5
    depends_on:
      - selenium-hub
    restart: unless-stopped

volumes:
  postgres-data:
```

**Deploy:**
```bash
# Set environment variables
export DB_PASSWORD=your_secure_password

# Start all services
docker-compose -f docker-compose.production.yml up -d

# Check logs
docker-compose -f docker-compose.production.yml logs -f app

# Stop all services
docker-compose -f docker-compose.production.yml down
```

---

## Cloud Deployment (AWS)

### Architecture
```
┌─────────────────┐
│   CloudFront    │ (CDN for static assets)
└────────┬────────┘
         │
┌────────┴────────┐
│  Load Balancer  │ (Application Load Balancer)
└────────┬────────┘
         │
    ┌────┴────┐
    │   ECS   │ (Fargate containers)
    └────┬────┘
         │
    ┌────┴────────────────┐
    │                     │
┌───┴───┐           ┌─────┴─────┐
│  RDS  │           │    S3     │
│ (DB)  │           │ (Storage) │
└───────┘           └───────────┘
```

### Step 1: Create RDS Database
```bash
aws rds create-db-instance \
  --db-instance-identifier qa-framework-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 15.3 \
  --master-username qa_admin \
  --master-user-password ${DB_PASSWORD} \
  --allocated-storage 20 \
  --backup-retention-period 7 \
  --vpc-security-group-ids sg-xxxxx
```

### Step 2: Create S3 Bucket
```bash
aws s3 mb s3://qa-framework-artifacts-${AWS_ACCOUNT_ID}

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket qa-framework-artifacts-${AWS_ACCOUNT_ID} \
  --versioning-configuration Status=Enabled

# Set lifecycle policy
cat > lifecycle-policy.json << 'EOF'
{
  "Rules": [{
    "Id": "DeleteOldArtifacts",
    "Status": "Enabled",
    "Expiration": {
      "Days": 90
    }
  }]
}
EOF

aws s3api put-bucket-lifecycle-configuration \
  --bucket qa-framework-artifacts-${AWS_ACCOUNT_ID} \
  --lifecycle-configuration file://lifecycle-policy.json
```

### Step 3: Create ECR Repository
```bash
aws ecr create-repository \
  --repository-name qa-automation-framework

# Get login credentials
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS \
  --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com

# Build and push image
docker build -t qa-automation-framework:1.0.0 .
docker tag qa-automation-framework:1.0.0 \
  ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/qa-automation-framework:1.0.0
docker push \
  ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/qa-automation-framework:1.0.0
```

### Step 4: Create ECS Task Definition
```json
{
  "family": "qa-framework",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [{
    "name": "app",
    "image": "${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/qa-automation-framework:1.0.0",
    "portMappings": [{
      "containerPort": 8080,
      "protocol": "tcp"
    }],
    "environment": [
      {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
      {"name": "STORAGE_TYPE", "value": "s3"},
      {"name": "STORAGE_S3_BUCKET", "value": "qa-framework-artifacts-${AWS_ACCOUNT_ID}"},
      {"name": "STORAGE_S3_REGION", "value": "us-east-1"}
    ],
    "secrets": [
      {"name": "DATABASE_URL", "valueFrom": "arn:aws:secretsmanager:..."},
      {"name": "DATABASE_USERNAME", "valueFrom": "arn:aws:secretsmanager:..."},
      {"name": "DATABASE_PASSWORD", "valueFrom": "arn:aws:secretsmanager:..."}
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/qa-framework",
        "awslogs-region": "us-east-1",
        "awslogs-stream-prefix": "app"
      }
    }
  }]
}
```

### Step 5: Create ECS Service
```bash
aws ecs create-service \
  --cluster qa-framework-cluster \
  --service-name qa-framework-service \
  --task-definition qa-framework:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx],securityGroups=[sg-xxxxx],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:...,containerName=app,containerPort=8080"
```

---

## Kubernetes Deployment

### Create Namespace
```yaml
# namespace.yml
apiVersion: v1
kind: Namespace
metadata:
  name: qa-framework
```

### Database Secret
```yaml
# database-secret.yml
apiVersion: v1
kind: Secret
metadata:
  name: database-credentials
  namespace: qa-framework
type: Opaque
stringData:
  url: jdbc:postgresql://postgres-service:5432/qa_framework
  username: qa_user
  password: your_password
```

### Application Deployment
```yaml
# deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: qa-framework
  namespace: qa-framework
spec:
  replicas: 3
  selector:
    matchLabels:
      app: qa-framework
  template:
    metadata:
      labels:
        app: qa-framework
    spec:
      containers:
      - name: app
        image: qa-automation-framework:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: database-credentials
              key: url
        - name: DATABASE_USERNAME
          valueFrom:
            secretKeyRef:
              name: database-credentials
              key: username
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: database-credentials
              key: password
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

### Service
```yaml
# service.yml
apiVersion: v1
kind: Service
metadata:
  name: qa-framework-service
  namespace: qa-framework
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
  selector:
    app: qa-framework
```

### Deploy
```bash
kubectl apply -f namespace.yml
kubectl apply -f database-secret.yml
kubectl apply -f deployment.yml
kubectl apply -f service.yml

# Check status
kubectl get pods -n qa-framework
kubectl get svc -n qa-framework

# View logs
kubectl logs -f deployment/qa-framework -n qa-framework
```

---

## Environment Configuration

### Development (`application-dev.yml`)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/qa_framework
    username: qa_user
    password: qa_password
  jpa:
    show-sql: true
  
storage:
  type: local
  local:
    base-path: ./test-artifacts

selenium:
  grid-url: http://localhost:4444

logging:
  level:
    com.company.qa: DEBUG
```

### Staging (`application-staging.yml`)
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 10
  
storage:
  type: s3
  s3:
    bucket: qa-framework-staging
    region: us-east-1

selenium:
  grid-url: http://selenium-hub:4444

logging:
  level:
    com.company.qa: INFO
```

### Production (`application-prod.yml`)
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
  jpa:
    show-sql: false
  
storage:
  type: s3
  s3:
    bucket: qa-framework-prod
    region: us-east-1

selenium:
  grid-url: ${SELENIUM_GRID_URL}

logging:
  level:
    com.company.qa: WARN
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
```

---

## Monitoring & Logging

### Application Metrics
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info
```

### Log Aggregation (ELK Stack)
```yaml
# filebeat.yml
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /var/log/qa-framework/*.log
  json.keys_under_root: true
  json.add_error_key: true

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "qa-framework-%{+yyyy.MM.dd}"
```

### CloudWatch Integration (AWS)
```java
// Add to application-prod.yml
management:
  metrics:
    export:
      cloudwatch:
        namespace: QAFramework
        enabled: true
```

---

## Security Best Practices

### 1. Environment Variables
```bash
# Never commit secrets to Git
# Use .env files (gitignored)
# Use secret managers in production (AWS Secrets Manager, etc.)

# Example .env
DATABASE_PASSWORD=secure_password_here
API_ENCRYPTION_KEY=32_character_random_key_here
```

### 2. API Key Rotation
```bash
# Rotate keys every 90 days
# Implement automated rotation:
curl -X POST http://localhost:8080/api/v1/auth/api-keys \
  -H "X-API-Key: $ADMIN_KEY" \
  -d '{"name":"New Key","expiresAt":"2025-04-12T00:00:00Z"}'

# Revoke old key
curl -X DELETE http://localhost:8080/api/v1/auth/api-keys/$OLD_KEY_ID \
  -H "X-API-Key: $ADMIN_KEY"
```

### 3. Network Security
- Use HTTPS in production
- Implement firewall rules
- Restrict database access
- Use VPC for AWS deployments

### 4. Database Security
```sql
-- Create read-only user for reporting
CREATE USER qa_readonly WITH PASSWORD 'secure_password';
GRANT CONNECT ON DATABASE qa_framework TO qa_readonly;
GRANT USAGE ON SCHEMA public TO qa_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO qa_readonly;
```

---

## Backup & Recovery

### Database Backup
```bash
# Automated daily backup
0 2 * * * /usr/bin/docker exec qa-postgres pg_dump -U qa_user qa_framework | gzip > /backups/qa_framework_$(date +\%Y\%m\%d).sql.gz

# Restore from backup
gunzip < /backups/qa_framework_20250112.sql.gz | \
  docker exec -i qa-postgres psql -U qa_user qa_framework
```

### S3 Versioning
```bash
# Enable versioning (already done in setup)
aws s3api put-bucket-versioning \
  --bucket qa-framework-artifacts \
  --versioning-configuration Status=Enabled

# Restore specific version
aws s3api get-object \
  --bucket qa-framework-artifacts \
  --key execution-123/screenshot.png \
  --version-id $VERSION_ID \
  screenshot.png
```

---

## Scaling

### Horizontal Scaling
```bash
# Docker Swarm
docker service scale qa-framework=5

# Kubernetes
kubectl scale deployment qa-framework --replicas=5 -n qa-framework

# AWS ECS
aws ecs update-service \
  --cluster qa-framework-cluster \
  --service qa-framework-service \
  --desired-count 5
```

### Database Scaling
- Use read replicas for read-heavy workloads
- Implement connection pooling (HikariCP)
- Add database indexes (already done in Week 2)

### Selenium Grid Scaling
```yaml
# Add more browser nodes
selenium-chrome-2:
  image: selenium/node-chrome:4.15.0
  environment:
    - SE_EVENT_BUS_HOST=selenium-hub
    - SE_EVENT_BUS_PUBLISH_PORT=4442
    - SE_EVENT_BUS_SUBSCRIBE_PORT=4443
    - SE_NODE_MAX_SESSIONS=5
```

---

## Troubleshooting

### Application won't start
```bash
# Check logs
docker logs qa-framework

# Common issues:
# 1. Database not accessible
docker exec qa-postgres pg_isready

# 2. Port already in use
lsof -i :8080
kill -9 $PID

# 3. Missing environment variables
env | grep DATABASE
```

### Database connection issues
```bash
# Test connection
docker exec -it qa-postgres psql -U qa_user -d qa_framework

# Check network
docker network inspect docker_default
```

### Selenium Grid issues
```bash
# Check Grid status
curl http://localhost:4444/status

# Restart Grid
docker-compose restart selenium-hub selenium-chrome
```

---

## CI/CD Pipeline (Preview for Week 3)
```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Setup Java
        uses: actions/setup-java@v2
        with:
          java-version: '17'
          
      - name: Build
        run: ./gradlew build
        
      - name: Run Tests
        run: ./gradlew test
        
      - name: Build Docker Image
        run: docker build -t qa-framework:${{github.sha}} .
        
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login ...
          docker push qa-framework:${{github.sha}}
          
      - name: Deploy to ECS
        run: |
          aws ecs update-service \
            --cluster qa-framework \
            --service qa-framework-service \
            --force-new-deployment
```

---

## Summary

Week 2 deployment is complete when:
- ✅ All Docker containers running
- ✅ Application accessible on port 8080
- ✅ Health check returns "UP"
- ✅ API key creation works
- ✅ Test execution completes successfully
- ✅ File storage functional
- ✅ All tests passing

**Next:** Week 3 - Reporting & Advanced Features
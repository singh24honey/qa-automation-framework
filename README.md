# QA Automation Framework - Local Development

AI-powered production-grade QA automation framework running entirely on your local computer.

## Quick Start
```bash
# 1. Run setup script
./scripts/setup.sh

# 2. Start application
./gradlew bootRun

# 3. Verify
curl http://localhost:8080/actuator/health
```

## Tech Stack (Local)
- Spring Boot 3.2+ (Main application)
- PostgreSQL 15 (Docker container)
- Redis 7 (Docker container)
- Selenium Grid 4 (Docker container)
- Local file system (artifacts storage)

## Services
- Application: http://localhost:8080
- PostgreSQL: localhost:5433
- Redis: localhost:6379
- Selenium Grid: http://localhost:4444
- Chrome VNC: http://localhost:7900 (password: secret)

## Documentation
See `docs/` folder for detailed guides.
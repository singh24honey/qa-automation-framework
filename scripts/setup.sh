#!/bin/bash

set -e

echo "============================================"
echo "QA Framework - Local Setup"
echo "============================================"

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install JDK 17+"
    exit 1
fi
echo "✅ Java found: $(java -version 2>&1 | head -n 1)"

if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker Desktop"
    exit 1
fi
echo "✅ Docker found: $(docker --version)"

# Create local artifact directories
echo ""
echo "Creating local artifact directories..."
mkdir -p ~/qa-framework/artifacts/screenshots
mkdir -p ~/qa-framework/artifacts/videos
mkdir -p ~/qa-framework/artifacts/logs
mkdir -p ~/qa-framework/artifacts/reports
echo "✅ Directories created at: ~/qa-framework/artifacts/"

# Start Docker services
echo ""
echo "Starting Docker services..."
cd docker
docker-compose up -d

echo ""
echo "Waiting for services to be ready..."
sleep 15

# Check services
echo ""
echo "Checking service health..."
docker-compose ps

echo ""
echo "============================================"
echo "✅ Setup Complete!"
echo "============================================"
echo ""
echo "Next steps:"
echo "1. Run: ./gradlew bootRun"
echo "2. Open: http://localhost:8080/actuator/health"
echo "3. Selenium Grid: http://localhost:4444"
echo ""
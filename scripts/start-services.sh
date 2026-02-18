#!/bin/bash

# ============================================
# Football Prediction System Startup Script
# ============================================
# This script builds and starts both modules
# Run from project root: ./scripts/start-services.sh
# ============================================

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║   Football Prediction Multi-Module System - Build & Deploy ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

print_module() {
    echo -e "${BLUE}📦${NC} $1"
}

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    print_error "Please run this script from the project root directory"
    exit 1
fi

# Check if Java is installed
print_info "Checking Java installation..."
if ! command -v java &> /dev/null; then
    print_error "Java is not installed. Please install Java 21 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    print_error "Java 21 or higher is required. Current version: $JAVA_VERSION"
    exit 1
fi
print_success "Java $JAVA_VERSION found"

# Check if Maven is installed
print_info "Checking Maven installation..."
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed. Please install Maven."
    exit 1
fi
print_success "Maven found"

# Verify module structure
print_info "Verifying module structure..."
if [ ! -d "football-prediction-app" ]; then
    print_error "football-prediction-app module not found"
    exit 1
fi

if [ ! -d "model-training-service" ]; then
    print_error "model-training-service module not found"
    exit 1
fi
print_success "Module structure verified"

# Create necessary directories
print_info "Creating directories..."
mkdir -p data
mkdir -p data/model_backups
mkdir -p logs/api
mkdir -p logs/model
mkdir -p logs/ingestion
mkdir -p logs/features
mkdir -p logs/error
mkdir -p logs/model-training

# Create directories in each module
mkdir -p football-prediction-app/data
mkdir -p football-prediction-app/logs
mkdir -p model-training-service/logs
print_success "Directories created"

# Build all modules
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
print_info "Building All Modules (Multi-Module Maven Build)..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

print_module "Building parent project and all modules..."
mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    print_success "All modules built successfully"

    print_module "Module JAR files created:"
    if [ -f "football-prediction-app/target/football-prediction-app-1.0.0.jar" ]; then
        print_success "football-prediction-app-1.0.0.jar"
    fi

    if [ -f "model-training-service/target/model-training-service-1.0.0.jar" ]; then
        print_success "model-training-service-1.0.0.jar"
    fi
else
    print_error "Build failed"
    exit 1
fi

# No need to change directory - everything is built from root
mvn clean package -DskipTests
cd ..

if [ $? -eq 0 ]; then
    print_success "Training service built successfully"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║   Multi-Module Build Complete!                             ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Ask user how to start services
echo "How would you like to start the services?"
echo "1) Docker Compose (recommended)"
echo "2) Separate processes (development)"
echo "3) Build only (no start)"
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        print_info "Starting services with Docker Compose..."
        docker-compose up -d

        echo ""
        print_success "Services started!"
        echo ""
        echo "📊 Service URLs:"
        echo "   Main Application:     http://localhost:8080"
        echo "   Training Service:     http://localhost:8081"
        echo "   H2 Console:           http://localhost:8080/h2-console"
        echo ""
        echo "📝 View logs:"
        echo "   docker-compose logs -f football-predictor"
        echo "   docker-compose logs -f model-training-service"
        echo ""
        echo "🛑 Stop services:"
        echo "   docker-compose down"
        ;;
    2)
        print_info "Starting services in separate terminals..."

        # Start main app in background
        java -jar football-prediction-app/target/football-prediction-app-1.0.0.jar > logs/main-app.log 2>&1 &
        MAIN_PID=$!
        echo $MAIN_PID > .main-app.pid
        print_success "Main application started (PID: $MAIN_PID)"

        # Wait a bit for main app to start
        sleep 5

        # Start training service in background
        java -jar model-training-service/target/model-training-service-1.0.0.jar > logs/training-service.log 2>&1 &
        TRAINING_PID=$!
        echo $TRAINING_PID > .training-service.pid
        print_success "Training service started (PID: $TRAINING_PID)"

        echo ""
        print_success "Services started!"
        echo ""
        echo "📊 Service URLs:"
        echo "   Main Application:     http://localhost:8080"
        echo "   Training Service:     http://localhost:8081"
        echo ""
        echo "📝 View logs:"
        echo "   tail -f logs/main-app.log"
        echo "   tail -f logs/training-service.log"
        echo ""
        echo "🛑 Stop services:"
        echo "   kill $(cat .main-app.pid)"
        echo "   kill $(cat .training-service.pid)"
        ;;
    3)
        print_success "Build complete. Services not started."
        echo ""
        echo "To start services manually:"
        echo "  Main app:        java -jar target/football-prediction-1.0.0.jar"
        echo "  Training service: java -jar model-training-service/target/model-training-service-1.0.0.jar"
        ;;
    *)
        print_error "Invalid choice"
        exit 1
        ;;
esac

echo ""
print_success "Setup complete!"


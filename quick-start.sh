#!/bin/bash

# Football Forecaster - Quick Start Script
# This script helps you quickly build and run the application

set -e  # Exit on error

echo "==================================="
echo "Football Forecaster - Quick Start"
echo "==================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed. Please install Maven first."
    exit 1
fi

# Navigate to project directory
cd "$(dirname "$0")"

# Menu
echo "Select an option:"
echo "1) Build and run application"
echo "2) Run tests only"
echo "3) Build without tests"
echo "4) Clean and rebuild everything"
echo "5) Run application (assuming already built)"
echo ""
read -p "Enter your choice (1-5): " choice

case $choice in
    1)
        print_status "Building application..."
        mvn clean package -DskipTests
        print_status "Build complete!"
        echo ""
        print_status "Starting application..."
        java -jar football-prediction-app/target/football-prediction-app.jar
        ;;
    2)
        print_status "Running tests..."
        mvn test
        print_status "Tests complete!"
        ;;
    3)
        print_status "Building without tests..."
        mvn clean package -DskipTests
        print_status "Build complete!"
        ;;
    4)
        print_status "Cleaning..."
        mvn clean
        print_status "Building..."
        mvn package
        print_status "Complete!"
        ;;
    5)
        print_status "Starting application..."
        if [ ! -f "football-prediction-app/target/football-prediction-app.jar" ]; then
            echo "Error: JAR file not found. Please build the application first (option 1 or 3)."
            exit 1
        fi
        java -jar football-prediction-app/target/football-prediction-app.jar
        ;;
    *)
        echo "Invalid choice. Exiting."
        exit 1
        ;;
esac

echo ""
print_status "Done!"
echo ""
echo "Application URL: http://localhost:8080"
echo "H2 Console: http://localhost:8080/h2-console"
echo "Admin credentials: admin / (set via ADMIN_PASSWORD env variable)"


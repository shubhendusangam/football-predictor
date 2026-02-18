#!/bin/bash

# ============================================
# API Test Script
# Tests both Main App and Training Service
# ============================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MAIN_APP_URL="http://localhost:8080"
TRAINING_URL="http://localhost:8081"

# Function to print colored output
print_header() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

print_test() {
    echo -e "\n${YELLOW}TEST:${NC} $1"
}

# Function to check if service is running
check_service() {
    local url=$1
    local name=$2

    print_test "Checking if $name is running..."

    if curl -s -f "$url" > /dev/null 2>&1; then
        print_success "$name is running"
        return 0
    else
        print_error "$name is NOT running at $url"
        return 1
    fi
}

# Function to make API call and check response
test_api() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4

    print_test "$description"

    if [ -n "$data" ]; then
        response=$(curl -s -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -d "$data" \
            -w "\n%{http_code}")
    else
        response=$(curl -s -X "$method" "$url" \
            -w "\n%{http_code}")
    fi

    # Extract HTTP code (last line)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n -1)

    if [ "$http_code" -eq 200 ] || [ "$http_code" -eq 201 ]; then
        print_success "HTTP $http_code - Success"
        echo "Response: $(echo "$body" | head -c 200)..."
        return 0
    else
        print_error "HTTP $http_code - Failed"
        echo "Response: $body"
        return 1
    fi
}

# Main test execution
echo ""
print_header "FOOTBALL PREDICTION SYSTEM - API TESTS"
echo ""

# Test 1: Check Main Application
check_service "$MAIN_APP_URL/api/model/status" "Main Application (Port 8080)" || {
    print_error "Main application is not running. Start it with: java -jar target/football-prediction-1.0.0.jar"
    exit 1
}

# Test 2: Check Training Service
check_service "$TRAINING_URL/api/training/model-info" "Training Service (Port 8081)" || {
    print_error "Training service is not running. Start it with: java -jar model-training-service/target/model-training-service-1.0.0.jar"
    exit 1
}

echo ""
print_header "TRAINING SERVICE TESTS"
echo ""

# Test 3: Get Model Info
test_api "GET" "$TRAINING_URL/api/training/model-info" "" \
    "Get model information"

# Test 4: Train Model (optional - can take time)
read -p "Do you want to run model training? (takes 5-10 seconds) [y/N]: " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    test_api "POST" "$TRAINING_URL/api/training/train" "" \
        "Train model (this may take a few seconds...)"
fi

# Test 5: Test Model (only if model exists)
read -p "Do you want to test the model? [y/N]: " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    test_api "POST" "$TRAINING_URL/api/training/test" "" \
        "Test model accuracy"
fi

echo ""
print_header "MAIN APPLICATION TESTS"
echo ""

# Test 6: Check Model Status
test_api "GET" "$MAIN_APP_URL/api/model/status" "" \
    "Check if model is loaded in main app"

# Test 7: Make Prediction
test_api "POST" "$MAIN_APP_URL/api/predict" \
    '{"homeTeam":"Arsenal","awayTeam":"Chelsea"}' \
    "Predict Arsenal vs Chelsea"

# Test 8: Make Another Prediction
test_api "POST" "$MAIN_APP_URL/api/predict" \
    '{"homeTeam":"Manchester United","awayTeam":"Liverpool"}' \
    "Predict Manchester United vs Liverpool"

echo ""
print_header "TEST SUMMARY"
echo ""

print_success "All basic tests completed!"
echo ""
echo "📊 Service URLs:"
echo "   Main Application:     $MAIN_APP_URL"
echo "   Training Service:     $TRAINING_URL"
echo "   H2 Console:           $MAIN_APP_URL/h2-console"
echo ""
echo "📖 Documentation:"
echo "   README:               README.md"
echo "   Multi-Module Arch:    MULTI_MODULE_ARCHITECTURE.md"
echo "   Training Service:     model-training-service/README.md"
echo ""
echo "📝 Logs:"
echo "   Main App:             logs/api/api.log"
echo "   Training Service:     logs/model-training/training.log"
echo ""

print_success "System is working correctly! 🎉"


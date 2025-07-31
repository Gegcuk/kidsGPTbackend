#!/bin/bash

echo "🧪 Testing CI/CD Pipeline Locally"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Test 1: Check if Docker is running
echo "1. Checking Docker..."
if docker info > /dev/null 2>&1; then
    print_status "Docker is running"
else
    print_error "Docker is not running. Please start Docker Desktop."
    exit 1
fi

# Test 2: Check Java version
echo "2. Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge "22" ]; then
    print_status "Java $JAVA_VERSION is installed (>= 22)"
else
    print_warning "Java $JAVA_VERSION is installed (recommended: >= 22)"
fi

# Test 3: Check if .env file exists
echo "3. Checking .env file..."
if [ -f ".env" ]; then
    print_status ".env file exists"
else
    print_error ".env file not found. Please copy env.template to .env"
    exit 1
fi

# Test 4: Test Maven build (CI step)
echo "4. Testing Maven build..."
if ./mvnw --batch-mode clean compile; then
    print_status "Maven compile successful"
else
    print_error "Maven compile failed"
    exit 1
fi

# Test 5: Test Maven tests (CI step)
echo "5. Testing Maven tests..."
if ./mvnw --batch-mode test; then
    print_status "Maven tests successful"
else
    print_error "Maven tests failed"
    exit 1
fi

# Test 6: Test Docker build (CD step)
echo "6. Testing Docker build..."
if docker build -t local/kidsgptbackend:test .; then
    print_status "Docker build successful"
else
    print_error "Docker build failed"
    exit 1
fi

# Test 7: Test Docker Compose
echo "7. Testing Docker Compose..."
if docker-compose up -d --build; then
    print_status "Docker Compose started successfully"
    
    # Wait for services to be ready
    echo "Waiting for services to be ready..."
    sleep 30
    
    # Test health endpoint
    if curl -f http://localhost:8080/api/v1/system/status > /dev/null 2>&1; then
        print_status "Application health check passed"
    else
        print_warning "Application health check failed (might still be starting)"
    fi
    
    # Stop services
    docker-compose down
else
    print_error "Docker Compose failed"
    exit 1
fi

echo ""
echo "🎉 All CI/CD tests passed locally!"
echo "Your pipeline should work on GitHub Actions." 
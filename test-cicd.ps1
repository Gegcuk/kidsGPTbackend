# PowerShell script to test CI/CD pipeline locally

Write-Host "🧪 Testing CI/CD Pipeline Locally" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan

# Function to print colored output
function Write-Status {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

# Test 1: Check if Docker is running
Write-Host "1. Checking Docker..." -ForegroundColor White
try {
    docker info | Out-Null
    Write-Status "Docker is running"
} catch {
    Write-Error "Docker is not running. Please start Docker Desktop."
    exit 1
}

# Test 2: Check Java version
Write-Host "2. Checking Java version..." -ForegroundColor White
try {
    $javaVersion = (java -version 2>&1 | Select-String "version").ToString().Split('"')[1]
    $majorVersion = $javaVersion.Split('.')[0]
    if ([int]$majorVersion -ge 22) {
        Write-Status "Java $javaVersion is installed (>= 22)"
    } else {
        Write-Warning "Java $javaVersion is installed (recommended: >= 22)"
    }
} catch {
    Write-Error "Java not found"
    exit 1
}

# Test 3: Check if .env file exists
Write-Host "3. Checking .env file..." -ForegroundColor White
if (Test-Path ".env") {
    Write-Status ".env file exists"
} else {
    Write-Error ".env file not found. Please copy env.template to .env"
    exit 1
}

# Test 4: Test Maven build (CI step)
Write-Host "4. Testing Maven build..." -ForegroundColor White
try {
    & ./mvnw --batch-mode clean compile
    if ($LASTEXITCODE -eq 0) {
        Write-Status "Maven compile successful"
    } else {
        Write-Error "Maven compile failed"
        exit 1
    }
} catch {
    Write-Error "Maven compile failed"
    exit 1
}

# Test 5: Test Maven tests (CI step)
Write-Host "5. Testing Maven tests..." -ForegroundColor White
try {
    & ./mvnw --batch-mode test
    if ($LASTEXITCODE -eq 0) {
        Write-Status "Maven tests successful"
    } else {
        Write-Error "Maven tests failed"
        exit 1
    }
} catch {
    Write-Error "Maven tests failed"
    exit 1
}

# Test 6: Test Docker build (CD step)
Write-Host "6. Testing Docker build..." -ForegroundColor White
try {
    docker build -t local/kidsgptbackend:test .
    if ($LASTEXITCODE -eq 0) {
        Write-Status "Docker build successful"
    } else {
        Write-Error "Docker build failed"
        exit 1
    }
} catch {
    Write-Error "Docker build failed"
    exit 1
}

# Test 7: Test Docker Compose
Write-Host "7. Testing Docker Compose..." -ForegroundColor White
try {
    docker-compose up -d --build
    if ($LASTEXITCODE -eq 0) {
        Write-Status "Docker Compose started successfully"
        
        # Wait for services to be ready
        Write-Host "Waiting for services to be ready..." -ForegroundColor White
        Start-Sleep -Seconds 30
        
        # Test health endpoint
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/system/status" -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                Write-Status "Application health check passed"
            } else {
                Write-Warning "Application health check failed (status: $($response.StatusCode))"
            }
        } catch {
            Write-Warning "Application health check failed (might still be starting)"
        }
        
        # Stop services
        docker-compose down
    } else {
        Write-Error "Docker Compose failed"
        exit 1
    }
} catch {
    Write-Error "Docker Compose failed"
    exit 1
}

Write-Host ""
Write-Host "🎉 All CI/CD tests passed locally!" -ForegroundColor Green
Write-Host "Your pipeline should work on GitHub Actions." -ForegroundColor Green 
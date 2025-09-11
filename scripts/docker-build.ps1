# JAiRouter Docker Build Script (PowerShell)
# This script builds the Docker image using native Docker commands

param(
    [string]$Environment = "prod",
    [string]$Version = ""
)

# Colors for output
$Red = "Red"
$Green = "Green"
$Yellow = "Yellow"
$Blue = "Blue"

# Configuration
$ProjectName = "model-router"
$ImageName = "sodlinken/jairouter"

# Function definitions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor $Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor $Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor $Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor $Red
}

# Validate environment parameter
if ($Environment -notin @("prod", "dev")) {
    Write-Error "Invalid environment parameter: $Environment. Supported environments: prod, dev"
    exit 1
}

# Get version from pom.xml
if ([string]::IsNullOrEmpty($Version)) {
    try {
        $Version = (mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
        if ([string]::IsNullOrEmpty($Version)) {
            $Version = "1.0-SNAPSHOT"
        }
    }
    catch {
        $Version = "1.0-SNAPSHOT"
    }
}

Write-Info "Starting JAiRouter Docker build (Version: $Version)..."
Write-Info "Environment: $Environment"

# Check if Docker is installed
try {
    docker --version | Out-Null
}
catch {
    Write-Error "Docker is not installed or not in PATH"
    exit 1
}

# Check if Maven is installed
try {
    mvn --version | Out-Null
}
catch {
    Write-Error "Maven is not installed or not in PATH"
    exit 1
}

# Step 1: Clean and build the JAR
Write-Info "Step 1: Building JAR file..."
if ($Environment -eq "prod") {
    mvn clean package -DskipTests -Pfast
} else {
    mvn clean package -DskipTests
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed"
    exit 1
}

# Check if JAR was built successfully
$JarPath = "target/${ProjectName}-${Version}.jar"
if (-not (Test-Path $JarPath)) {
    Write-Error "JAR file not found at $JarPath"
    exit 1
}

Write-Success "JAR file built successfully"

# Step 2: Build Docker image
Write-Info "Step 2: Building Docker image..."

if ($Environment -eq "dev") {
    $Dockerfile = "Dockerfile.dev"
    $ImageTag = "${ImageName}:${Version}-dev"
    $AdditionalTags = @()
} else {
    $Dockerfile = "Dockerfile"
    $ImageTag = "${ImageName}:${Version}"
    $AdditionalTags = @("${ImageName}:latest")
}

docker build -f $Dockerfile -t $ImageTag @AdditionalTags .

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker build failed!"
    exit 1
}

Write-Success "Docker image built successfully!"
Write-Info "Image: $ImageTag"
if ($AdditionalTags.Count -gt 0) {
    foreach ($tag in $AdditionalTags) {
        Write-Info "Image: $tag"
    }
}

# Show image info
Write-Info "Image details:"
docker images $ImageName --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"

Write-Success "Build completed successfully!"

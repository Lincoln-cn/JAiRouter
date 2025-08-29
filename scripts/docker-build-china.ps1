#!/usr/bin/env pwsh

# JAiRouter Docker Build Script (China Optimized)
# 使用阿里云Maven镜像加速构建

$ErrorActionPreference = "Stop"

# Colors for output
$RED = "`e[0;31m"
$GREEN = "`e[0;32m"
$YELLOW = "`e[1;33m"
$BLUE = "`e[0;34m"
$NC = "`e[0m" # No Color

# Configuration
$PROJECT_NAME = "jairouter"
$IMAGE_NAME = "sodlinken/${PROJECT_NAME}"
$VERSION = "1.0-SNAPSHOT"

Write-Host "${YELLOW}Starting JAiRouter Docker build (China Optimized)...${NC}"
Write-Host "${BLUE}使用阿里云Maven镜像加速构建${NC}"

# Check if settings-china.xml exists
if (-not (Test-Path "settings-china.xml")) {
    Write-Host "${RED}Error: settings-china.xml not found!${NC}"
    exit 1
}

# Build Docker image using China-optimized Dockerfile
Write-Host "${YELLOW}Building Docker image with China-optimized configuration...${NC}"
docker build -f Dockerfile.china -t "${IMAGE_NAME}:${VERSION}" -t "${IMAGE_NAME}:latest" .

# Check if Docker build was successful
if ($LASTEXITCODE -eq 0) {
    Write-Host "${GREEN}Docker image built successfully!${NC}"
    Write-Host "${GREEN}Image: ${IMAGE_NAME}:${VERSION}${NC}"
    Write-Host "${GREEN}Image: ${IMAGE_NAME}:latest${NC}"

    # Show image info
    Write-Host "${YELLOW}Image details:${NC}"
    docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
} else {
    Write-Host "${RED}Docker build failed!${NC}"
    exit 1
}

Write-Host "${GREEN}Build completed successfully!${NC}"
Write-Host "${BLUE}Maven dependencies downloaded from Alibaba mirrors${NC}"
Write-Host "${BLUE}Configuration files used:${NC}"
Write-Host "${BLUE}  - Dockerfile.china (China-optimized)${NC}"
Write-Host "${BLUE}  - settings-china.xml (Alibaba Maven mirrors)${NC}"
Write-Host "${BLUE}  - pom.xml (china profile)${NC}"

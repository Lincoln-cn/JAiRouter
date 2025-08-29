#!/bin/bash

# JAiRouter Docker Build Script (China Optimized)
# 使用阿里云Maven镜像加速构建

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="jairouter"
IMAGE_NAME="sodlinken/${PROJECT_NAME}"
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

echo -e "${YELLOW}Starting JAiRouter Docker build (China Optimized)...${NC}"
echo -e "${BLUE}使用阿里云Maven镜像加速构建${NC}"

# Check if settings-china.xml exists
if [ ! -f "settings-china.xml" ]; then
    echo -e "${RED}Error: settings-china.xml not found!${NC}"
    exit 1
fi

# Build Docker image using China-optimized Dockerfile
echo -e "${YELLOW}Building Docker image with China-optimized configuration...${NC}"
docker build -f Dockerfile.china -t "${IMAGE_NAME}:${VERSION}" -t "${IMAGE_NAME}:latest" .

# Check if Docker build was successful
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Docker image built successfully!${NC}"
    echo -e "${GREEN}Image: ${IMAGE_NAME}:${VERSION}${NC}"
    echo -e "${GREEN}Image: ${IMAGE_NAME}:latest${NC}"
    
    # Show image info
    echo -e "${YELLOW}Image details:${NC}"
    docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
else
    echo -e "${RED}Docker build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${BLUE}Maven dependencies downloaded from Alibaba mirrors${NC}"
echo -e "${BLUE}Configuration files used:${NC}"
echo -e "${BLUE}  - Dockerfile.china (China-optimized)${NC}"
echo -e "${BLUE}  - settings-china.xml (Alibaba Maven mirrors)${NC}"
echo -e "${BLUE}  - pom.xml (china profile)${NC}"
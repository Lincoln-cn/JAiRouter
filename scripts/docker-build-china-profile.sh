#!/bin/bash

# JAiRouter Docker Build Script (China Profile Version)
# 使用pom.xml中的china profile

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="model-router"
IMAGE_NAME="jairouter/${PROJECT_NAME}"
VERSION="1.0-SNAPSHOT"

echo -e "${YELLOW}Starting JAiRouter Docker build (China Profile)...${NC}"
echo -e "${BLUE}使用pom.xml中的china profile和阿里云Maven仓库${NC}"

# Step 1: Build using china profile
echo -e "${YELLOW}Step 1: Building JAR file with china profile...${NC}"
mvn clean package -Pchina

# Check if JAR was built successfully
if [ ! -f "target/${PROJECT_NAME}-${VERSION}.jar" ]; then
    echo -e "${RED}Error: JAR file not found at target/${PROJECT_NAME}-${VERSION}.jar${NC}"
    exit 1
fi

echo -e "${GREEN}JAR file built successfully using china profile${NC}"

# Step 2: Build Docker image
echo -e "${YELLOW}Step 2: Building Docker image...${NC}"
docker build -t "${IMAGE_NAME}:${VERSION}" -t "${IMAGE_NAME}:latest" .

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

echo -e "${GREEN}Build completed successfully using china profile!${NC}"
echo -e "${BLUE}Dependencies were downloaded from Alibaba Maven repositories${NC}"
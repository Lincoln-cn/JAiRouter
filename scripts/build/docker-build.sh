#!/bin/bash

# JAiRouter Docker Build Script
# Usage: ./scripts/build/docker-build.sh [image-type]
# Image types: standard, optimized, jlink

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="model-router"
IMAGE_NAME="sodlinken/jairouter"

# Parse arguments
IMAGE_TYPE=${1:-optimized}  # default to optimized

# Show usage
show_usage() {
    echo "Usage: $0 [image-type]"
    echo ""
    echo "Image types:"
    echo "  optimized   Optimized image (recommended, 281MB)"
    echo "  standard    Standard image (440MB)"
    echo "  jlink       JLink optimized image (281MB, experimental)"
    echo ""
    echo "Examples:"
    echo "  $0 optimized    # Build optimized image"
    echo "  $0 standard     # Build standard image"
    echo "  $0 jlink        # Build JLink optimized image"
    echo "  $0              # Build optimized image (default)"
    exit 1
}

# Validate image type parameter
if [[ ! "$IMAGE_TYPE" =~ ^(optimized|standard|jlink)$ ]]; then
    echo -e "${RED}Invalid image type: $IMAGE_TYPE${NC}"
    show_usage
fi

# Get version from pom.xml
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

echo -e "${YELLOW}Starting JAiRouter Docker build (Version: ${VERSION})...${NC}"
echo -e "${BLUE}Image type: ${IMAGE_TYPE}${NC}"

# Step 1: Clean and build the JAR
echo -e "${YELLOW}Step 1: Building JAR file...${NC}"
mvn clean package -DskipTests -Pfast

# Check if JAR was built successfully
if [ ! -f "target/${PROJECT_NAME}-${VERSION}.jar" ]; then
    echo -e "${RED}Error: JAR file not found at target/${PROJECT_NAME}-${VERSION}.jar${NC}"
    exit 1
fi

echo -e "${GREEN}JAR file built successfully${NC}"

# Step 2: Build Docker image
echo -e "${YELLOW}Step 2: Building Docker image...${NC}"

case "$IMAGE_TYPE" in
    "optimized")
        echo -e "${BLUE}Building optimized image (281MB, recommended) ⭐${NC}"
        docker build -f Dockerfile.optimized \
            -t "${IMAGE_NAME}:${VERSION}-optimized" \
            -t "${IMAGE_NAME}:latest-optimized" \
            .
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}Optimized Docker image built successfully!${NC}"
            echo -e "${GREEN}Image: ${IMAGE_NAME}:${VERSION}-optimized${NC}"
            echo -e "${GREEN}Image: ${IMAGE_NAME}:latest-optimized${NC}"
        else
            echo -e "${RED}Optimized Docker build failed!${NC}"
            exit 1
        fi
        ;;
    "jlink")
        echo -e "${BLUE}Building JLink optimized image (281MB, experimental) 🔬${NC}"
        docker build -f Dockerfile.jlink \
            -t "${IMAGE_NAME}:${VERSION}-jlink" \
            -t "${IMAGE_NAME}:jlink" \
            .
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}JLink Docker image built successfully!${NC}"
            echo -e "${GREEN}Image: ${IMAGE_NAME}:${VERSION}-jlink${NC}"
            echo -e "${GREEN}Image: ${IMAGE_NAME}:jlink${NC}"
        else
            echo -e "${RED}JLink Docker build failed!${NC}"
            exit 1
        fi
        ;;
    *)
        echo -e "${BLUE}Building standard image (440MB)${NC}"
        docker build \
            -t "${IMAGE_NAME}:${VERSION}" \
            -t "${IMAGE_NAME}:latest" \
            .
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}Standard Docker image built successfully!${NC}"
            echo -e "${GREEN}Image: ${IMAGE_NAME}:${VERSION}${NC}"
            echo -e "${GREEN}Image: ${IMAGE_NAME}:latest${NC}"
        else
            echo -e "${RED}Standard Docker build failed!${NC}"
            exit 1
        fi
        ;;
esac

# Show image info
echo -e "${YELLOW}Image details:${NC}"
docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"

echo -e "${GREEN}Build completed successfully!${NC}"

# Show image comparison
echo ""
echo -e "${YELLOW}=== Image Comparison ===${NC}"
docker images "${IMAGE_NAME}" --format "table {{.Tag}}\t{{.Size}}" | grep -E "optimized|standard|jlink|latest" || true
echo ""
echo -e "${GREEN}Optimized/JLink images are ~36% smaller than standard (281MB vs 440MB) ⭐${NC}"

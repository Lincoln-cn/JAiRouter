#!/bin/bash

# JAiRouter Docker Build Script
# 用法：./scripts/build/docker-build.sh [镜像类型]
# 镜像类型：standard（标准版）, optimized（优化版，推荐）

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

# 参数解析
IMAGE_TYPE=${1:-optimized}  # 默认使用优化版

# 显示使用说明
show_usage() {
    echo "用法：$0 [镜像类型]"
    echo ""
    echo "镜像类型:"
    echo "  optimized   优化版镜像（推荐，281MB）"
    echo "  standard    标准版镜像（440MB）"
    echo ""
    echo "示例:"
    echo "  $0 optimized    # 构建优化版镜像"
    echo "  $0 standard     # 构建标准版镜像"
    echo "  $0              # 默认构建优化版镜像"
    exit 1
}

# 验证镜像类型参数
if [[ ! "$IMAGE_TYPE" =~ ^(optimized|standard)$ ]]; then
    echo -e "${RED}无效的镜像类型：$IMAGE_TYPE${NC}"
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

if [[ "$IMAGE_TYPE" == "optimized" ]]; then
    # 构建优化版镜像（使用 Dockerfile.optimized）
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
else
    # 构建标准版镜像（使用 Dockerfile）
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
fi

# Show image info
echo -e "${YELLOW}Image details:${NC}"
docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"

echo -e "${GREEN}Build completed successfully!${NC}"

# 显示镜像对比
if [[ "$IMAGE_TYPE" == "optimized" ]]; then
    echo ""
    echo -e "${YELLOW}=== 镜像对比 ===${NC}"
    docker images "${IMAGE_NAME}" --format "table {{.Tag}}\t{{.Size}}" | grep -E "optimized|latest"
    echo ""
    echo -e "${GREEN}优化版镜像比标准版小约 36%（159MB）⭐${NC}"
fi

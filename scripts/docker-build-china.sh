#!/bin/bash

# JAiRouter Docker Build Script (China Optimized)
# This script builds the Docker image using Alibaba Maven repository for faster downloads in China

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

echo -e "${YELLOW}Starting JAiRouter Docker build (China Optimized)...${NC}"
echo -e "${BLUE}Using Alibaba Maven repository for faster downloads${NC}"

# Step 1: Create temporary settings.xml for Alibaba Maven repository
echo -e "${YELLOW}Step 1: Configuring Alibaba Maven repository...${NC}"

TEMP_SETTINGS_FILE="target/settings-china.xml"
mkdir -p target

cat > "${TEMP_SETTINGS_FILE}" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
  
  <localRepository>${user.home}/.m2/repository</localRepository>
  
  <mirrors>
    <!-- Alibaba Maven Repository Mirror - 镜像所有仓库 -->
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
  
  <profiles>
    <profile>
      <id>china</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
        <repository>
          <id>aliyun-central</id>
          <name>Alibaba Central Repository</name>
          <url>https://maven.aliyun.com/repository/central</url>
          <layout>default</layout>
          <releases>
            <enabled>true</enabled>
            <updatePolicy>never</updatePolicy>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </repository>
        <repository>
          <id>aliyun-spring</id>
          <name>Alibaba Spring Repository</name>
          <url>https://maven.aliyun.com/repository/spring</url>
          <layout>default</layout>
          <releases>
            <enabled>true</enabled>
            <updatePolicy>never</updatePolicy>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </repository>
        <repository>
          <id>aliyun-spring-plugin</id>
          <name>Alibaba Spring Plugin Repository</name>
          <url>https://maven.aliyun.com/repository/spring-plugin</url>
          <layout>default</layout>
          <releases>
            <enabled>true</enabled>
            <updatePolicy>never</updatePolicy>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>aliyun-plugin</id>
          <name>Alibaba Plugin Repository</name>
          <url>https://maven.aliyun.com/repository/central</url>
          <layout>default</layout>
          <releases>
            <enabled>true</enabled>
            <updatePolicy>never</updatePolicy>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </pluginRepository>
        <pluginRepository>
          <id>aliyun-spring-plugin</id>
          <name>Alibaba Spring Plugin Repository</name>
          <url>https://maven.aliyun.com/repository/spring-plugin</url>
          <layout>default</layout>
          <releases>
            <enabled>true</enabled>
            <updatePolicy>never</updatePolicy>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  
  <activeProfiles>
    <activeProfile>china</activeProfile>
  </activeProfiles>
</settings>
EOF

echo -e "${GREEN}Alibaba Maven configuration created${NC}"

# Step 2: Clean and build the JAR using Alibaba repository
echo -e "${YELLOW}Step 2: Building JAR file with Alibaba Maven repository...${NC}"
# Force use of our settings file and disable default repositories
mvn clean package -DskipTests -Pfast -s "${TEMP_SETTINGS_FILE}" -Dmaven.repo.remote=https://maven.aliyun.com/repository/public

# Check if JAR was built successfully
if [ ! -f "target/${PROJECT_NAME}-${VERSION}.jar" ]; then
    echo -e "${RED}Error: JAR file not found at target/${PROJECT_NAME}-${VERSION}.jar${NC}"
    exit 1
fi

echo -e "${GREEN}JAR file built successfully using Alibaba Maven repository${NC}"

# Step 3: Build Docker image
echo -e "${YELLOW}Step 3: Building Docker image...${NC}"
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

# Step 4: Cleanup temporary files
echo -e "${YELLOW}Step 4: Cleaning up temporary files...${NC}"
rm -f "${TEMP_SETTINGS_FILE}"

echo -e "${GREEN}Build completed successfully using China-optimized configuration!${NC}"
echo -e "${BLUE}Dependencies were downloaded from Alibaba Maven repository for faster speed${NC}"
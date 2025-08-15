# China Network Environment Optimization Deployment

This document provides optimized deployment solutions specifically for the Chinese network environment, including configurations for network acceleration, image optimization, and dependency acceleration, helping Chinese users achieve better deployment and runtime experiences.

## China Optimization Overview

### Optimization Features

- **Maven Mirror Acceleration**: Using Alibaba Cloud Maven mirrors to increase dependency download speed by 5-10 times
- **Docker Image Acceleration**: Configuring domestic Docker image sources for faster image pulling
- **Network Connection Optimization**: Connection timeout and retry configurations tailored for the Chinese network environment
- **CDN Acceleration**: Using domestic CDN services to accelerate static resource access
- **DNS Optimization**: Configuring domestic DNS servers to improve domain name resolution speed

### Network Environment Challenges

| Challenge | Impact | Optimization Solution |
|-----------|--------|------------------------|
| **Slow Maven Dependency Downloads** | Long build times, frequent timeouts | Use Alibaba Cloud Maven mirrors |
| **Slow Docker Image Pulling** | Long deployment times, possible failures | Configure domestic image sources |
| **Unstable Network Connections** | High service call failure rates | Optimize timeout and retry configurations |
| **Slow DNS Resolution** | Service discovery delays | Use domestic DNS services |
| **Cross-border Network Latency** | Slow API call responses | Use domestic AI service providers |

## Maven Build Optimization

### 1. Alibaba Cloud Maven Mirror Configuration

JAiRouter provides specialized build configurations for China optimization:

#### settings-china.xml Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
  
  <mirrors>
    <!-- Alibaba Cloud Maven Central Repository Mirror -->
    <mirror>
      <id>aliyun-central</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Central</name>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
    
    <!-- Alibaba Cloud Maven Public Repository Mirror -->
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
    
    <!-- Alibaba Cloud Spring Repository Mirror -->
    <mirror>
      <id>aliyun-spring</id>
      <mirrorOf>spring-milestones,spring-snapshots</mirrorOf>
      <name>Aliyun Spring</name>
      <url>https://maven.aliyun.com/repository/spring</url>
    </mirror>
  </mirrors>
  
  <profiles>
    <profile>
      <id>china</id>
      <repositories>
        <repository>
          <id>aliyun-central</id>
          <url>https://maven.aliyun.com/repository/central</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </repository>
        
        <repository>
          <id>aliyun-spring</id>
          <url>https://maven.aliyun.com/repository/spring</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
      
      <pluginRepositories>
        <pluginRepository>
          <id>aliyun-plugin</id>
          <url>https://maven.aliyun.com/repository/central</url>
          <releases>
            <enabled>true</enabled>
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
```

### 2. China Optimization Build Scripts

#### Windows PowerShell Script

Create `scripts/docker-build-china.ps1`:

```powershell
#!/usr/bin/env pwsh
# JAiRouter China Optimized Docker Build Script

param(
    [string]$Tag = "latest",
    [string]$Profile = "china"
)

Write-Host "Starting to build JAiRouter (China Optimized Version)..." -ForegroundColor Green
Write-Host "Tag: $Tag" -ForegroundColor Yellow
Write-Host "Profile: $Profile" -ForegroundColor Yellow

# Check if Docker is running
try {
    docker version | Out-Null
} catch {
    Write-Error "Docker is not running or not installed"
    exit 1
}

# Build the application
Write-Host "Step 1: Building application with China mirrors..." -ForegroundColor Cyan
try {
    .\mvnw.cmd clean package -P$Profile -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed"
    }
} catch {
    Write-Error "Maven build failed: $_"
    exit 1
}

# Build Docker image
Write-Host "Step 2: Building Docker image..." -ForegroundColor Cyan
try {
    docker build -f Dockerfile.china -t "jairouter/model-router:$Tag" .
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed"
    }
} catch {
    Write-Error "Docker build failed: $_"
    exit 1
}

# Verify image
Write-Host "Step 3: Verifying image..." -ForegroundColor Cyan
$imageSize = docker images jairouter/model-router:$Tag --format "{{.Size}}"
Write-Host "Image size: $imageSize" -ForegroundColor Green

Write-Host "Build completed!" -ForegroundColor Green
Write-Host "Image: jairouter/model-router:$Tag" -ForegroundColor Yellow
Write-Host "Run command: docker run -d -p 8080:8080 jairouter/model-router:$Tag" -ForegroundColor Yellow
```

#### Linux/macOS Bash Script

Create `scripts/docker-build-china.sh`:

```bash
#!/bin/bash
# JAiRouter China Optimized Docker Build Script

set -e

TAG=${1:-latest}
PROFILE=${2:-china}

echo "Starting to build JAiRouter (China Optimized Version)..."
echo "Tag: $TAG"
echo "Profile: $PROFILE"

# Check if Docker is running
if ! docker version >/dev/null 2>&1; then
    echo "Error: Docker is not running or not installed"
    exit 1
fi

# Build the application
echo "Step 1: Building application with China mirrors..."
./mvnw clean package -P$PROFILE -DskipTests

# Build Docker image
echo "Step 2: Building Docker image..."
docker build -f Dockerfile.china -t "jairouter/model-router:$TAG" .

# Verify image
echo "Step 3: Verifying image..."
IMAGE_SIZE=$(docker images jairouter/model-router:$TAG --format "{{.Size}}")
echo "Image size: $IMAGE_SIZE"

echo "Build completed!"
echo "Image: jairouter/model-router:$TAG"
echo "Run command: docker run -d -p 8080:8080 jairouter/model-router:$TAG"
```

### 3. pom.xml China Optimization Configuration

Add China optimization profile in [pom.xml](file://D:\IdeaProjects\model-router\pom.xml):

```xml
<profiles>
    <profile>
        <id>china</id>
        <properties>
            <maven.compiler.source>17</maven.compiler.source>
            <maven.compiler.target>17</maven.compiler.target>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <!-- Skip some time-consuming checks to speed up build -->
            <checkstyle.skip>true</checkstyle.skip>
            <spotbugs.skip>true</spotbugs.skip>
            <jacoco.skip>true</jacoco.skip>
        </properties>
        
        <repositories>
            <repository>
                <id>aliyun-central</id>
                <url>https://maven.aliyun.com/repository/central</url>
                <releases>
                    <enabled>true</enabled>
                </releases>
                <snapshots>
                    <enabled>false</enabled>
                </snapshots>
            </repository>
        </repositories>
        
        <pluginRepositories>
            <pluginRepository>
                <id>aliyun-plugin</id>
                <url>https://maven.aliyun.com/repository/central</url>
                <releases>
                    <enabled>true</enabled>
                </releases>
                <snapshots>
                    <enabled>false</enabled>
                </snapshots>
            </pluginRepository>
        </pluginRepositories>
    </profile>
</profiles>
```

## Docker Image Optimization

### 1. Docker Image Source Configuration

#### Configure Docker Image Accelerator

Create or edit `/etc/docker/daemon.json`:

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com",
    "https://ccr.ccs.tencentyun.com"
  ],
  "insecure-registries": [],
  "debug": false,
  "experimental": false,
  "features": {
    "buildkit": true
  }
}
```

Restart Docker service:

```bash
# Ubuntu/Debian
sudo systemctl restart docker

# CentOS/RHEL
sudo systemctl restart docker

# Windows
# Restart Docker Desktop
```

### 2. China Optimized Dockerfile

[Dockerfile.china](file://D:\IdeaProjects\model-router\Dockerfile.china) has been optimized for the Chinese network environment:

```dockerfile
# Multi-stage Dockerfile for JAiRouter (China Optimized)
# Using Alibaba Cloud Maven mirror to accelerate build
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy Alibaba Cloud Maven configuration
COPY settings-china.xml /root/.m2/settings.xml

# Copy build files
COPY pom.xml .
COPY src ./src
COPY checkstyle.xml .
COPY spotbugs-security-include.xml .
COPY spotbugs-security-exclude.xml .

# Build application (using china profile)
RUN mvn clean package -Pchina -DskipTests

# Runtime stage - Using Alibaba Cloud image
FROM registry.cn-hangzhou.aliyuncs.com/acs/openjdk:17-jre-alpine

LABEL maintainer="JAiRouter Team"
LABEL description="JAiRouter - AI Model Service Routing and Load Balancing Gateway (China Optimized)"
LABEL version="1.0-SNAPSHOT"

# Create application user
RUN addgroup -g 1001 jairouter && \
    adduser -D -s /bin/sh -u 1001 -G jairouter jairouter

WORKDIR /app

# Create necessary directories
RUN mkdir -p /app/logs /app/config /app/config-store && \
    chown -R jairouter:jairouter /app

# Copy JAR file
COPY --from=builder /app/target/model-router-*.jar app.jar

# Set environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

EXPOSE 8080

USER jairouter

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## Network Connection Optimization

### 1. Application Configuration Optimization

Create `config/application-china.yml`:

```yaml
# China network environment optimization configuration
server:
  port: 8080
  tomcat:
    connection-timeout: 30000  # Increase connection timeout
    max-connections: 8192
    threads:
      max: 200
      min-spare: 10

# WebClient network optimization
webclient:
  connection-timeout: 15s      # Increase connection timeout
  read-timeout: 120s          # Increase read timeout
  write-timeout: 60s          # Increase write timeout
  max-in-memory-size: 50MB
  connection-pool:
    max-connections: 500      # Reduce connection pool size
    max-idle-time: 60s        # Increase idle time
    pending-acquire-timeout: 90s  # Increase connection acquisition timeout

# Retry configuration
retry:
  max-attempts: 5             # Increase retry attempts
  backoff:
    initial-interval: 2s      # Increase initial backoff time
    max-interval: 30s         # Increase maximum backoff time
    multiplier: 2.0

# Circuit breaker configuration (more lenient thresholds)
circuit-breaker:
  failure-threshold: 10       # Increase failure threshold
  recovery-timeout: 120000    # Increase recovery timeout
  success-threshold: 5        # Increase success threshold
  timeout: 60000             # Increase request timeout

# Health check optimization
management:
  health:
    defaults:
      enabled: true
    diskspace:
      enabled: true
      threshold: 10GB
  endpoint:
    health:
      cache:
        time-to-live: 30s     # Increase health check cache time

# Logging configuration
logging:
  level:
    org.springframework.web.reactive.function.client: DEBUG
    org.unreal.modelrouter.adapter: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
```

### 2. DNS Optimization Configuration

#### System DNS Configuration

Edit `/etc/resolv.conf`:

```bash
# Use domestic DNS servers
nameserver 223.5.5.5      # Alibaba Cloud DNS
nameserver 119.29.29.29   # Tencent DNS
nameserver 114.114.114.114 # 114 DNS
nameserver 8.8.8.8        # Google DNS (backup)

# DNS options optimization
options timeout:2 attempts:3 rotate single-request-reopen
```

#### Docker Container DNS Configuration

Configure in `docker-compose.china.yml`:

```yaml
version: '3.8'

services:
  jairouter:
    image: jairouter/model-router:china
    container_name: jairouter-china
    dns:
      - 223.5.5.5      # Alibaba Cloud DNS
      - 119.29.29.29   # Tencent DNS
      - 114.114.114.114 # 114 DNS
    dns_search:
      - localdomain
    dns_opt:
      - timeout:2
      - attempts:3
    environment:
      - SPRING_PROFILES_ACTIVE=china
      - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.net.preferIPv4Stack=true
    ports:
      - "8080:8080"
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    restart: unless-stopped
    networks:
      - china-network

networks:
  china-network:
    driver: bridge
    driver_opts:
      com.docker.network.bridge.name: jairouter-china
```

## AI Service Provider Optimization

### 1. Domestic AI Service Configuration

Configure to use domestic AI service providers to reduce cross-border network latency:

```yaml
# config/services-china.yml
model:
  services:
    chat:
      instances:
        # Alibaba Cloud Qwen
        - name: "qwen-turbo"
          base-url: "https://dashscope.aliyuncs.com"
          path: "/api/v1/services/aigc/text-generation/generation"
          weight: 3
          headers:
            Authorization: "Bearer ${DASHSCOPE_API_KEY}"
          timeout: 60s
          
        # Baidu ERNIE Bot
        - name: "ernie-bot"
          base-url: "https://aip.baidubce.com"
          path: "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
          weight: 2
          headers:
            Content-Type: "application/json"
          timeout: 60s
          
        # Tencent Hunyuan
        - name: "hunyuan"
          base-url: "https://hunyuan.tencentcloudapi.com"
          path: "/v1/chat/completions"
          weight: 2
          headers:
            Authorization: "Bearer ${TENCENT_API_KEY}"
          timeout: 60s
          
        # Zhipu ChatGLM
        - name: "chatglm"
          base-url: "https://open.bigmodel.cn"
          path: "/api/paas/v4/chat/completions"
          weight: 2
          headers:
            Authorization: "Bearer ${ZHIPU_API_KEY}"
          timeout: 60s
    
    embedding:
      instances:
        # Alibaba Cloud Text Embedding
        - name: "text-embedding-v1"
          base-url: "https://dashscope.aliyuncs.com"
          path: "/api/v1/services/embeddings/text-embedding/text-embedding"
          weight: 1
          headers:
            Authorization: "Bearer ${DASHSCOPE_API_KEY}"
```

### 2. Network Proxy Configuration

If you need to access overseas AI services, you can configure a proxy:

```yaml
# Proxy configuration
proxy:
  enabled: true
  http:
    host: proxy.example.com
    port: 8080
    username: ${PROXY_USERNAME}
    password: ${PROXY_PASSWORD}
  https:
    host: proxy.example.com
    port: 8080
    username: ${PROXY_USERNAME}
    password: ${PROXY_PASSWORD}
  no-proxy:
    - localhost
    - 127.0.0.1
    - "*.aliyuncs.com"
    - "*.baidubce.com"
    - "*.tencentcloudapi.com"
```

## Monitoring Optimization

### 1. Domestic Monitoring Service Integration

Configure to use domestic monitoring services:

```yaml
# docker-compose.monitoring-china.yml
version: '3.8'

services:
  jairouter:
    image: jairouter/model-router:china
    # ... other configurations
    
  prometheus:
    image: registry.cn-hangzhou.aliyuncs.com/acs/prometheus:latest
    container_name: prometheus-china
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus-china.yml:/etc/prometheus/prometheus.yml:ro
    networks:
      - monitoring-china
      
  grafana:
    image: registry.cn-hangzhou.aliyuncs.com/acs/grafana:latest
    container_name: grafana-china
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_INSTALL_PLUGINS=grafana-piechart-panel,grafana-worldmap-panel
    networks:
      - monitoring-china

networks:
  monitoring-china:
    driver: bridge
```

### 2. Alert Notification Optimization

Configure domestic notification services:

```yaml
# monitoring/alertmanager-china.yml
global:
  # Use domestic SMTP service
  smtp_smarthost: 'smtp.qq.com:587'
  smtp_from: 'alerts@example.com'
  smtp_auth_username: 'alerts@example.com'
  smtp_auth_password: '${QQ_MAIL_PASSWORD}'

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'china-alerts'

receivers:
  - name: 'china-alerts'
    email_configs:
      - to: 'ops@example.com'
        subject: '[JAiRouter] {{ .GroupLabels.alertname }}'
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          Time: {{ .StartsAt.Format "2006-01-02 15:04:05" }}
          {{ end }}
    
    # WeChat Work notification
    wechat_configs:
      - corp_id: '${WECHAT_CORP_ID}'
        agent_id: '${WECHAT_AGENT_ID}'
        api_secret: '${WECHAT_API_SECRET}'
        to_user: '@all'
        message: |
          JAiRouter Alert Notification
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          {{ end }}
    
    # DingTalk notification
    webhook_configs:
      - url: '${DINGTALK_WEBHOOK_URL}'
        send_resolved: true
        title: 'JAiRouter Alert'
        text: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          {{ end }}
```

## Deployment Script Optimization

### 1. One-click Deployment Script

Create `deploy-china.sh`:

```bash
#!/bin/bash
# JAiRouter China Optimized One-click Deployment Script

set -e

echo "JAiRouter China Optimized Deployment Script"
echo "=========================================="

# Check system environment
check_environment() {
    echo "Checking system environment..."
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo "Error: Docker is not installed"
        echo "Please install Docker first: https://docs.docker.com/engine/install/"
        exit 1
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo "Error: Docker Compose is not installed"
        echo "Please install Docker Compose first: https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    echo "✓ System environment check passed"
}

# Configure Docker image acceleration
configure_docker_mirror() {
    echo "Configuring Docker image acceleration..."
    
    DAEMON_JSON="/etc/docker/daemon.json"
    if [ ! -f "$DAEMON_JSON" ]; then
        sudo mkdir -p /etc/docker
        sudo tee $DAEMON_JSON > /dev/null <<EOF
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
EOF
        sudo systemctl restart docker
        echo "✓ Docker image acceleration configured"
    else
        echo "✓ Docker image acceleration already configured"
    fi
}

# Build image
build_image() {
    echo "Building JAiRouter China optimized image..."
    
    if [ -f "scripts/docker-build-china.sh" ]; then
        chmod +x scripts/docker-build-china.sh
        ./scripts/docker-build-china.sh
    else
        echo "Building with Maven..."
        ./mvnw clean package -Pchina -DskipTests
        docker build -f Dockerfile.china -t jairouter/model-router:china .
    fi
    
    echo "✓ Image build completed"
}

# Deploy application
deploy_application() {
    echo "Deploying JAiRouter application..."
    
    # Create necessary directories
    mkdir -p config logs config-store
    
    # Copy configuration files
    if [ ! -f "config/application-china.yml" ]; then
        cp config/application.yml config/application-china.yml
        echo "✓ Configuration files copied"
    fi
    
    # Start application
    docker-compose -f docker-compose.china.yml up -d
    
    echo "✓ Application deployment completed"
}

# Verify deployment
verify_deployment() {
    echo "Verifying deployment status..."
    
    # Wait for application to start
    echo "Waiting for application to start..."
    sleep 30
    
    # Check health status
    if curl -f -s http://localhost:8080/actuator/health > /dev/null; then
        echo "✓ Application health check passed"
        echo "✓ JAiRouter deployment successful!"
        echo ""
        echo "Access URLs:"
        echo "  Application: http://localhost:8080"
        echo "  Health Check: http://localhost:8080/actuator/health"
        echo "  API Documentation: http://localhost:8080/swagger-ui/index.html"
    else
        echo "✗ Application health check failed"
        echo "Please check logs: docker logs jairouter-china"
        exit 1
    fi
}

# Main process
main() {
    check_environment
    configure_docker_mirror
    build_image
    deploy_application
    verify_deployment
}

# Execute main process
main "$@"
```

### 2. Windows Deployment Script

Create `deploy-china.ps1`:

```powershell
#!/usr/bin/env pwsh
# JAiRouter China Optimized One-click Deployment Script (Windows)

param(
    [switch]$SkipBuild = $false,
    [switch]$Monitoring = $false
)

Write-Host "JAiRouter China Optimized Deployment Script (Windows)" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green

# Check system environment
function Test-Environment {
    Write-Host "Checking system environment..." -ForegroundColor Cyan
    
    # Check Docker
    try {
        docker version | Out-Null
        Write-Host "✓ Docker is installed" -ForegroundColor Green
    } catch {
        Write-Error "Docker is not installed or not running"
        Write-Host "Please install Docker Desktop first: https://www.docker.com/products/docker-desktop"
        exit 1
    }
    
    # Check Docker Compose
    try {
        docker-compose version | Out-Null
        Write-Host "✓ Docker Compose is installed" -ForegroundColor Green
    } catch {
        Write-Error "Docker Compose is not installed"
        exit 1
    }
}

# Build image
function Build-Image {
    if ($SkipBuild) {
        Write-Host "Skipping image build" -ForegroundColor Yellow
        return
    }
    
    Write-Host "Building JAiRouter China optimized image..." -ForegroundColor Cyan
    
    if (Test-Path "scripts\docker-build-china.ps1") {
        & "scripts\docker-build-china.ps1"
    } else {
        Write-Host "Building with Maven..." -ForegroundColor Yellow
        .\mvnw.cmd clean package -Pchina -DskipTests
        docker build -f Dockerfile.china -t jairouter/model-router:china .
    }
    
    Write-Host "✓ Image build completed" -ForegroundColor Green
}

# Deploy application
function Deploy-Application {
    Write-Host "Deploying JAiRouter application..." -ForegroundColor Cyan
    
    # Create necessary directories
    @("config", "logs", "config-store") | ForEach-Object {
        if (!(Test-Path $_)) {
            New-Item -ItemType Directory -Path $_ | Out-Null
        }
    }
    
    # Copy configuration files
    if (!(Test-Path "config\application-china.yml")) {
        Copy-Item "config\application.yml" "config\application-china.yml"
        Write-Host "✓ Configuration files copied" -ForegroundColor Green
    }
    
    # Select Compose file
    $composeFile = if ($Monitoring) { "docker-compose.monitoring-china.yml" } else { "docker-compose.china.yml" }
    
    # Start application
    docker-compose -f $composeFile up -d
    
    Write-Host "✓ Application deployment completed" -ForegroundColor Green
}

# Verify deployment
function Test-Deployment {
    Write-Host "Verifying deployment status..." -ForegroundColor Cyan
    
    # Wait for application to start
    Write-Host "Waiting for application to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
    
    # Check health status
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ Application health check passed" -ForegroundColor Green
            Write-Host "✓ JAiRouter deployment successful!" -ForegroundColor Green
            Write-Host ""
            Write-Host "Access URLs:" -ForegroundColor Yellow
            Write-Host "  Application: http://localhost:8080" -ForegroundColor White
            Write-Host "  Health Check: http://localhost:8080/actuator/health" -ForegroundColor White
            Write-Host "  API Documentation: http://localhost:8080/swagger-ui/index.html" -ForegroundColor White
            
            if ($Monitoring) {
                Write-Host "  Prometheus: http://localhost:9090" -ForegroundColor White
                Write-Host "  Grafana: http://localhost:3000 (admin/admin)" -ForegroundColor White
            }
        }
    } catch {
        Write-Error "Application health check failed"
        Write-Host "Please check logs: docker logs jairouter-china" -ForegroundColor Red
        exit 1
    }
}

# Main process
function Main {
    Test-Environment
    Build-Image
    Deploy-Application
    Test-Deployment
}

# Execute main process
Main
```

## Performance Tuning

### 1. JVM Parameter Optimization

JVM parameter optimization for the Chinese network environment:

```bash
# JVM optimization parameters for Chinese network environment
JAVA_OPTS="
-Xms1g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/

# Network optimization
-Djava.net.preferIPv4Stack=true
-Djava.net.useSystemProxies=true
-Dnetworkaddress.cache.ttl=60
-Dnetworkaddress.cache.negative.ttl=10

# Connection pool optimization
-Dhttp.maxConnections=50
-Dhttp.keepAlive=true
-Dhttp.maxRedirects=3

# Security optimization
-Djava.security.egd=file:/dev/./urandom
-Djava.awt.headless=true
"
```

### 2. System Parameter Optimization

```bash
# Network parameter optimization
cat >> /etc/sysctl.conf << EOF
# TCP optimization
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_intvl = 30
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_tw_reuse = 1

# Connection count optimization
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 65535

# Buffer optimization
net.core.rmem_default = 262144
net.core.rmem_max = 16777216
net.core.wmem_default = 262144
net.core.wmem_max = 16777216
EOF

sysctl -p
```

## Troubleshooting

### 1. Network Connection Issues

```bash
# Check network connectivity
ping -c 4 maven.aliyun.com
ping -c 4 registry.cn-hangzhou.aliyuncs.com

# Check DNS resolution
nslookup maven.aliyun.com
dig maven.aliyun.com

# Check port connectivity
telnet maven.aliyun.com 443
nc -zv maven.aliyun.com 443

# Check proxy settings
echo $http_proxy
echo $https_proxy
```

### 2. Build Issue Troubleshooting

```bash
# Check Maven configuration
./mvnw help:effective-settings

# Check dependency download
./mvnw dependency:resolve -X

# Clean and rebuild
./mvnw clean
rm -rf ~/.m2/repository
./mvnw package -Pchina -DskipTests
```

### 3. Runtime Issue Troubleshooting

```bash
# Check container status
docker ps --filter "name=jairouter"

# Check container logs
docker logs jairouter-china --tail 100

# Check network connections
docker exec jairouter-china netstat -tulpn

# Check DNS resolution
docker exec jairouter-china nslookup baidu.com
```

## Best Practices

### 1. Network Optimization Recommendations

- Use domestic mirror sources and CDN services
- Configure appropriate timeout and retry parameters
- Use connection pools and long connections
- Configure local DNS caching

### 2. Deployment Recommendations

- Choose appropriate server regions (East China, North China, etc.)
- Use SSD storage to improve I/O performance
- Configure monitoring and alerts
- Regularly backup configurations and data

### 3. Operations Recommendations

- Monitor network quality and latency
- Regularly update dependencies and images
- Configure log rotation and cleanup
- Establish fault response procedures

## Next Steps

After completing China optimization deployment, you can:

- **[Docker Deployment](docker.md)** - Learn about complete Docker deployment solutions
- **[Production Environment Deployment](production.md)** - Configure high-availability production environments
- **[Monitoring Guide](../../monitoring/index.md)** - Set up a complete monitoring system
- **[Troubleshooting](../troubleshooting/index.md)** - Learn fault diagnosis and resolution

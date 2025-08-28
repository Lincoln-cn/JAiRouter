﻿# Docker Deployment Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter provides a complete Dockerized deployment solution, supporting multi-environment configuration and container orchestration. This document details how to deploy JAiRouter using Docker, including standalone deployment, cluster deployment, and monitoring integration.

## Docker Deployment Overview

### Core Features

- **Multi-stage Build**: Optimized image size, production image ~200MB
- **Multi-environment Support**: Independent configuration for development, testing, and production environmen
| **China Accelerts
- **China Network Optimization**: Specially optimized Alibaba Cloud Maven image build
- **Security Best Practices**: Non-root user, minimal permission operation
- **Health Check**: Built-in application health monitoring and auto-recovery
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack
- **Log Management**: Structured logs and log rotation
- **Configuration Management**: Support for dynamic configuration and hot updates

### Image Information

| Image Type | Tags | Size | Purpose |
|------------|------|------|---------|
| **Production Image** | `latest`, `v1.0.0` | ~200MB | Production environment |
| **Development Image** | [dev](file://d:\IdeaProjects\model-router\Dockerfile.dev), `v1.0.0-dev` | ~220MB | Development and debugging |
| **China Optimized Image** | [china](file://d:\IdeaProjects\model-router\Dockerfile.china), `v1.0.0-china` | ~200MB | Optimized for Chinese users |

## Quick Start

### 1. Pull Images

```bash
# Pull the latest production image
docker pull sodlinken/jairouter:latest

# Pull a specific version
docker pull sodlinken/jairouter:v1.0.0

# For Chinese users (using Alibaba Cloud mirror)
docker pull registry.cn-hangzhou.aliyuncs.com/sodlinken/jairouter:latest

# Verify the image
docker images | grep sodlinken/jairouter
```

### 2. Basic Run

```bash
# Simplest way to run
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Verify deployment
curl http://localhost:8080/actuator/health
```

### 3. Run with Configuration

```bash
# Run with configuration file mounted
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  sodlinken/jairouter:latest
```

## Image Building

### Build Method Selection

| Build Method | Target Users | Command | Features | Build Time |
|--------------|--------------|---------|----------|------------|

### 1. Using Build Scripts (Recommended)

#### Chinese Users (Recommended)

```
# Using Chinese optimized build script
./scripts/docker-build-china.sh

# Or manual build
mvn clean package -Pchina
docker build -f Dockerfile.china -t sodlinken/jairouter:latest .
```

#### International Users

```
# Using standard build script
./scripts/docker-build.sh

# Or manual build
mvn clean package
docker build -t sodlinken/jairouter:latest .
```

### 2. Using Maven Plugins

```bash
# Using Dockerfile plugin
mvn clean package dockerfile:build -Pdocker

# Using Jib plugin (no Docker required)
mvn clean package jib:dockerBuild -Pjib

# Build and push to registry
mvn clean package jib:build -Pjib \
  -Djib.to.image=your-registry/sodlinken/jairouter:latest
```

### 3. Multi-environment Build

```bash
# Build development environment image
docker build -f Dockerfile.dev -t sodlinken/jairouter:dev .

# Build production environment image
docker build -f Dockerfile -t sodlinken/jairouter:prod .

# Build China optimized image
docker build -f Dockerfile.china -t sodlinken/jairouter:china .
```


## Security Configuration

### 1. Container Security

```bash
# Run container with non-root user
docker run -d \
  --user 1001:1001 \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Set read-only file system (except necessary directories)
docker run -d \
  --read-only \
  --tmpfs /tmp \
  --tmpfs /app/logs \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Limit container capabilities
docker run -d \
  --cap-drop ALL \
  --cap-add NET_BIND_SERVICE \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Set security options
docker run -d \
  --security-opt no-new-privileges:true \
  --security-opt seccomp=profile.json \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest
```

### 2. Network Security

```yaml
# Network security configuration in docker-compose.yml
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    # Limit container network access
    networks:
      - jairouter-network
    # Set internal network, cannot access external network
    networks:
      jairouter-network:
        internal: true

networks:
  jairouter-network:
    driver: bridge
```

### 3. Secret Management

```bash
# Use Docker secrets to manage sensitive information
echo "your-api-key" | docker secret create jairouter-api-key -

# Use secrets in swarm mode
docker service create \
  --name jairouter \
  --secret jairouter-api-key \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Use secrets in docker-compose
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    secrets:
      - jairouter-api-key
    environment:
      - API_KEY_FILE=/run/secrets/jairouter-api-key

secrets:
  jairouter-api-key:
    file: ./secrets/api-key.txt
```

### 4. Application Security Configuration

Create `config/application-security.yml`:

```yaml
# Security configuration
security:
  # API Key configuration
  api-key:
    enabled: true
    header: X-API-Key
    keys:
      - name: default
        value: your-api-key-here
  
  # JWT configuration
  jwt:
    enabled: true
    secret: your-jwt-secret-key
    algorithm: HS256
    expiration-minutes: 60
    issuer: jairouter
    accounts:
      - username: admin
        password: admin-password
        roles: [ADMIN, USER]
        enabled: true
      - username: user
        password: user-password
        roles: [USER]
        enabled: true

  # CORS configuration
  cors:
    allowed-origins: "*"
    allowed-methods: "*"
    allowed-headers: "*"
    allow-credentials: false

# HTTPS configuration
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: password
    key-store-type: PKCS12
    key-alias: jairouter
```

## Log Management

### 1. Log Configuration

Create `config/application-logging.yml`:

```yaml
# Log configuration
logging:
  level:
    # Core component log levels
    org.unreal.modelrouter: INFO
    org.unreal.modelrouter.security: DEBUG
    org.unreal.modelrouter.tracing: DEBUG
    
    # Spring framework log levels
    org.springframework: WARN
    org.springframework.web: INFO
    org.springframework.security: INFO
    
    # Web client log levels
    org.springframework.web.reactive.function.client: DEBUG
  
  # Console log configuration
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  
  # File log configuration
  file:
    name: /app/logs/jairouter.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 10GB
  
  # Logback configuration
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 10GB
```

### 2. Log Viewing

```bash
# View real-time logs
docker logs -f jairouter

# View recent logs
docker logs --tail 100 jairouter

# View logs from a specific time
docker logs --since "2024-01-15T10:00:00" jairouter

# Export logs
docker logs jairouter > jairouter.log 2>&1

# View log files inside container
docker exec jairouter cat /app/logs/jairouter.log
```

### 3. Log Rotation

```bash
# Configure logrotate
cat > /etc/logrotate.d/docker-jairouter << EOF
/var/lib/docker/containers/*/*-json.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0644 root root
}
EOF

# Log configuration in Docker Compose
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "3"
```

### 4. Structured Logging

Create `config/application-structured-logging.yml`:

```yaml
# Structured log configuration
logging:
  level:
    org.unreal.modelrouter: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  
  file:
    name: /app/logs/jairouter.log
  
  # JSON format log configuration
  structured:
    enabled: true
    format: json
    fields:
      timestamp: "@timestamp"
      level: "level"
      logger: "logger"
      message: "message"
      thread: "thread"
      traceId: "traceId"
      spanId: "spanId"

# Structured log output example
# {
#   "@timestamp": "2024-01-15T10:00:00.123Z",
#   "level": "INFO",
#   "logger": "org.unreal.modelrouter.ModelRouterApplication",
#   "message": "Application started successfully",
#   "thread": "main",
#   "traceId": "abc123def456"
# }
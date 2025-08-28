# Docker Deployment Guide

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
docker pull registry.cn-hangzhou.aliyuncs.com/jairouter/model-router:latest

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


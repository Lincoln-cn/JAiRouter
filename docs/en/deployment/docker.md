# Docker Deployment Guide

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
| **Development Image** | [dev](file://D:\IdeaProjects\model-router\Dockerfile.dev), `v1.0.0-dev` | ~220MB | Development and debugging |
| **China Optimized Image** | [china](file://D:\IdeaProjects\model-router\Dockerfile.china), `v1.0.0-china` | ~200MB | Optimized for Chinese users |

## Quick Start

### 1. Pull Images

```bash
# Pull the latest production image
docker pull jairouter/model-router:latest

# Pull a specific version
docker pull jairouter/model-router:v1.0.0

# For Chinese users (using Alibaba Cloud mirror)
docker pull registry.cn-hangzhou.aliyuncs.com/jairouter/model-router:latest

# Verify the image
docker images | grep jairouter
```

### 2. Basic Run

```bash
# Simplest way to run
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  jairouter/model-router:latest

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
  jairouter/model-router:latest
```

## Image Building

### Build Method Selection

| Build Method | Target Users | Command | Features | Build Time |
|--------------|--------------|---------|----------|------------|

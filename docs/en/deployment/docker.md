# Docker Deployment Guide

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->



JAiRouter provides a complete Dockerized deployment solution, supporting multi-environment configuration and container orchestration. This document details how to deploy JAiRouter using Docker, including standalone deployment, cluster deployment, and monitoring integration.

## Docker Deployment Overview

### Core Features

- **Multi-stage Build**: Optimized image size; the production image is approximately **281MB** (optimized version)
- **Multi-environment Support**: Independent configuration for development, testing, and production environments
- **China Network Optimization**: Specially optimized Alibaba Cloud Maven image build
- **Alpine Base**: Uses the Alpine Linux base image for a smaller footprint and higher security
- **Security Best Practices**: Runs as a non-root user with least privilege
- **Health Check**: Built-in application health monitoring and automatic recovery
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack
- **Log Management**: Structured logging and log rotation
- **Configuration Management**: Supports dynamic configuration and hot reload

### Image Information

| Image Type | Tags | Size | Purpose | Dockerfile |
|------------|------|------|---------|------------|
| **Production (Optimized)** | `latest-optimized`, `v1.7.0-optimized` | **~281MB** | Production (Recommended) ⭐ | `Dockerfile.optimized` |
| **Production (JLink)** | `latest-jlink`, `v1.7.0-jlink` | **~281MB** | Production (Experimental) 🔬 | `Dockerfile.jlink` |
| **Production (Standard)** | `latest`, `v1.7.0` | ~440MB | Production | `Dockerfile` |
| **Development** | `dev`, `v1.7.0-dev` | ~220MB | Development and debugging | `Dockerfile.dev` |
| **China Optimized** | `china`, `v1.7.0-china` | ~440MB | Optimized for Chinese users | `Dockerfile.china` |

**Optimized Image Features**:
- ✅ Uses the `eclipse-temurin:17-jre-alpine` base image (~40% smaller than the standard JRE)
- ✅ Multi-stage build + Spring Boot layertools layered extraction
- ✅ Image size reduced from 440MB to **281MB** (a 36% reduction)
- ✅ Maintains full functionality and non-root user security practices

**JLink Image Features**:
- 🔬 Based on Alpine + multi-stage build + JVM parameter optimization
- 🔬 Attempts to use jlink to create a custom JRE module (falls back to the Alpine JRE due to Spring Boot 3.x compatibility issues)
- 🔬 Same image size as the optimized version (281MB), provided as an experimental option
- ✅ Maintains full functionality and non-root user security practices

## Quick Start

### 1. Pull Images

```
# Pull the latest production image
docker pull sodlinken/jairouter:latest

# Pull a specific version
docker pull sodlinken/jairouter:v1.0.0

# For Chinese users (using the Alibaba Cloud mirror)
docker pull registry.cn-hangzhou.aliyuncs.com/sodlinken/jairouter:latest

# Verify the image
docker images | grep sodlinken/jairouter
```

### 2. Basic Run

```
# Simplest way to run (JWT authentication enabled by default)
docker run -d \
  --name jairouter \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -p 8080:8080 \
  sodlinken/jairouter:latest
  
# Simplest way to run (JWT authentication disabled)
docker run -d \
  --name jairouter \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JAIROUTER_SECURITY_JWT_ENABLED=false \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Verify deployment
curl http://localhost:8080/actuator/health
```

### 3. Run with Configuration

```
# Run with the configuration file mounted
docker run -d \
  --name jairouter \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  sodlinken/jairouter:latest
```

## Image Building

### Build Method Selection

| Build Method | Target Users | Command | Features | Build Time |
|--------------|--------------|---------|----------|------------|
| **China Accelerated** | Chinese users | `./scripts/docker-build-china.sh` | Uses the Alibaba Cloud Maven mirror, 5-10x faster | ~3-5 minutes |
| **Standard Build** | International users | `./scripts/docker-build.sh` | Uses Maven Central, stable and reliable | ~8-15 minutes |
| **Maven Build** | Developers | `mvn dockerfile:build -Pdocker` | Integrated build process, supports multiple profiles | ~5-10 minutes |
| **Jib Build** | Advanced users | `mvn jib:dockerBuild -Pjib` | No Docker required, faster build, supports layers | ~2-4 minutes |

### 1. Using Build Scripts (Recommended)

#### Chinese Users (Recommended)

```
# Use the China-optimized build script
./scripts/docker-build-china.sh

# Or build manually
mvn clean package -Pchina
docker build -f Dockerfile.china -t sodlinken/jairouter:latest .
```

#### International Users

```
# Use the standard build script
./scripts/docker-build.sh

# Or build manually
mvn clean package
docker build -t sodlinken/jairouter:latest .
```

### 2. Using Maven Plugins

```
# Use the Dockerfile plugin
mvn clean package dockerfile:build -Pdocker

# Use the Jib plugin (no Docker required)
mvn clean package jib:dockerBuild -Pjib

# Build and push to the registry
mvn clean package jib:build -Pjib \
  -Djib.to.image=your-registry/sodlinken/jairouter:latest
```

### 3. Multi-environment Build

```
# Build the development image
docker build -f Dockerfile.dev -t sodlinken/jairouter:dev .

# Build the production image
docker build -f Dockerfile -t sodlinken/jairouter:prod .

# Build the China-optimized image
docker build -f Dockerfile.china -t sodlinken/jairouter:china .
```

## Container Run

### 1. Run in Production

```
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PROD_ADMIN_API_KEY=adminkey \
  -e PROD_SERVICE_API_KEY=serviceaoi \
  -e PROD_READONLY_API_KEY=readonly \
  -e JAIROUTER_SECURITY_ENABLED=true \
  -e JAIROUTER_SECURITY_API_KEY_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_ENABLED=true \
  -e PROD_JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"\
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/config-store:/app/config-store \
  --restart unless-stopped \
  --health-cmd="curl -f http://localhost:8080/actuator/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-retries=3 \
  --health-start-period=60s \
  sodlinken/jairouter:latest
```

```
docker run  \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PROD_ADMIN_API_KEY=adminkey \
  -e PROD_SERVICE_API_KEY=serviceaoi \
  -e PROD_READONLY_API_KEY=readonly \
  -e JAIROUTER_SECURITY_ENABLED=true \
  -e JAIROUTER_SECURITY_API_KEY_ENABLED=true \
  -e JAIROUTER_SECURITY_JWT_ENABLED=true \
  -e PROD_JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
  --restart unless-stopped \
  --health-cmd="curl -f http://localhost:8080/actuator/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-retries=3 \
  --health-start-period=60s \
  sodlinken/jairouter:latest
```

### 2. Run in Development

```
docker run -d \
  --name jairouter-dev \
  -p 8080:8080 \
  -p 5005:5005 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" \
  -e JAVA_OPTS="-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  -v $(pwd)/config:/app/config \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/src:/app/src:ro \
  sodlinken/jairouter:dev
```

### 3. Using Run Scripts

```
# Windows PowerShell
.\scripts\docker-run.ps1 prod latest

# Linux/macOS Bash
./scripts/docker-run.sh prod latest

# Development environment
./scripts/docker-run.sh dev latest
```

## Docker Compose Deployment

### 1. Basic Compose Configuration

Create `docker-compose.yml`:

```
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - PROD_ADMIN_API_KEY=adminkey 
      - PROD_SERVICE_API_KEY=serviceaoi 
      - PROD_READONLY_API_KEY=readonly 
      - JAIROUTER_SECURITY_ENABLED=true 
      - JAIROUTER_SECURITY_API_KEY_ENABLED=true 
      - JAIROUTER_SECURITY_JWT_ENABLED=true 
      - PROD_JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" 
      - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
      - ./config-store:/app/config-store
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - jairouter-network

networks:
  jairouter-network:
    driver: bridge
```

### 2. Compose Configuration with Monitoring

Create `docker-compose.monitoring.yml`:

```
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - PROD_ADMIN_API_KEY=adminkey 
      - PROD_SERVICE_API_KEY=serviceaoi 
      - PROD_READONLY_API_KEY=readonly 
      - JAIROUTER_SECURITY_ENABLED=true 
      - JAIROUTER_SECURITY_API_KEY_ENABLED=true 
      - JAIROUTER_SECURITY_JWT_ENABLED=true 
      - PROD_JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long" 
      - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    restart: unless-stopped
    networks:
      - monitoring
    depends_on:
      - prometheus

  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
    networks:
      - monitoring

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:

networks:
  monitoring:
    driver: bridge
```

### 3. Development Compose Configuration

Create `docker-compose.dev.yml`:

```
version: '3.8'

services:
  jairouter-dev:
    build:
      context: .
      dockerfile: Dockerfile.dev
    container_name: jairouter-dev
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - JWT_SECRET=your-dev-jwt-secret
      - JAVA_OPTS=-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    volumes:
      - ./config:/app/config
      - ./logs:/app/logs
      - ./src:/app/src:ro  # Source mount (hot reload)
    networks:
      - dev-network

networks:
  dev-network:
    driver: bridge
```

### 4. Running Compose

```
# Start basic services
docker-compose up -d

# Start services with monitoring
docker-compose -f docker-compose.monitoring.yml up -d

# Start the development environment
docker-compose -f docker-compose.dev.yml up -d

# View service status
docker-compose ps

# View logs
docker-compose logs -f jairouter

# Stop services
docker-compose down
```

## Environment Configuration

### 1. Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Active Spring profile |
| `JAVA_OPTS` | See the table below | JVM arguments |
| `SERVER_PORT` | `8080` | Application port |
| `JWT_SECRET` | None | JWT secret for the development environment |
| `PROD_JWT_SECRET` | None | JWT secret for the production environment |
| `MANAGEMENT_PORT` | `8081` | Management port (optional) |
| `PROD_ADMIN_API_KEY` | None | Admin API key for the production environment |
| `PROD_SERVICE_API_KEY` | None | Service API key for the production environment |
| `PROD_READONLY_API_KEY` | None | Read-only API key for the production environment |
| `PROD_JWT_SECRET` | None | JWT secret for the production environment |
| `REDIS_HOST` | `localhost` | Redis host address |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | None | Redis password |
| `SECURITY_ALERT_EMAIL` | None | Security alert email address |
| `SECURITY_ALERT_WEBHOOK` | None | Security alert webhook address |

### 2. JVM Parameter Configuration

| Environment | Memory Configuration | GC Configuration | Other Parameters |
|-------------|----------------------|------------------|------------------|
| **Production** | `-Xms512m -Xmx1024m` | `-XX:+UseG1GC` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |
| **Development** | `-Xms256m -Xmx512m` | `-XX:+UseG1GC` | `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` |
| **Testing** | `-Xms256m -Xmx512m` | `-XX:+UseG1GC` | `-XX:+HeapDumpOnOutOfMemoryError` |

### 3. Directory Mounts

| Container Path | Host Path | Purpose | Permissions |
|----------------|-----------|---------|-------------|
| `/app/config` | `./config` | Production configuration files | Read-only |
| `/app/logs` | `./logs` | Log files | Read/write |
| `/app/config-dev` | `./config-dev` | Development configuration storage | Read/write |

## Network Configuration

### 1. Port Mapping

```
# Basic port mapping
-p 8080:8080    # Application port

# Development environment port mapping
-p 8080:8080    # Application port
-p 5005:5005    # Debug port

# Monitoring port mapping
-p 9090:9090    # Prometheus
-p 3000:3000    # Grafana
```

### 2. Network Modes

```
# Bridge network (default)
networks:
  - jairouter-network

# Host network
network_mode: host

# Custom network
networks:
  custom-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## Health Check

### 1. Container Health Check

```
# Docker runtime health check
docker run -d \
  --health-cmd="curl -f http://localhost:8080/actuator/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-retries=3 \
  --health-start-period=60s \
  sodlinken/jairouter:latest
```

### 2. Compose Health Check

```
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### 3. Health Check Verification

```
# View health status
docker ps --format "table {{.Names}}\t{{.Status}}"

# View health check logs
docker inspect jairouter --format='{{json .State.Health}}'

# Manual health check
curl http://localhost:8080/actuator/health
```

## Monitoring Integration

### 1. Prometheus Configuration

Create `monitoring/prometheus.yml`:

```
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

### 2. Grafana Dashboard

Create `monitoring/grafana/dashboards/jairouter.json`:

```
{
  "dashboard": {
    "title": "JAiRouter Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_total[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      }
    ]
  }
}
```

### 3. Start the Monitoring Stack

```
# Start the complete monitoring stack
docker-compose -f docker-compose.monitoring.yml up -d

# Access the monitoring UI
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

## Log Management

### 1. Log Configuration

Create `config/application-logging.yml`:

```
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

### 2. Viewing Logs

```
# View real-time logs
docker logs -f jairouter

# View the most recent logs
docker logs --tail 100 jairouter

# View logs from a specific time
docker logs --since "2024-01-15T10:00:00" jairouter

# Export logs
docker logs jairouter > jairouter.log 2>&1

# View the log file inside the container
docker exec jairouter cat /app/logs/jairouter.log
```

### 3. Log Rotation

```
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

```
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
```

## Performance Optimization

### 1. Resource Limits

```
services:
  jairouter:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

### 2. Container Optimization

```
# Use multi-stage builds to reduce image size
# Use .dockerignore to exclude unnecessary files
# Run as a non-root user
# Enable health checks
```

### 3. Network Optimization

```
# Use a custom network
networks:
  jairouter-network:
    driver: bridge
    driver_opts:
      com.docker.network.bridge.name: jairouter0
      com.docker.network.driver.mtu: 1500
```

## Troubleshooting

### 1. Common Issues

#### Container Fails to Start

```
# View container status
docker ps -a --filter "name=jairouter"

# View startup logs
docker logs jairouter

# Check port usage
netstat -tulpn | grep 8080

# Check whether the image exists
docker images | grep sodlinken/jairouter
```

#### Health Check Fails

```
# Run a manual health check
curl -v http://localhost:8080/actuator/health

# Inspect inside the container
docker exec -it jairouter sh

# View application logs
docker exec jairouter cat /app/logs/jairouter.log
```

#### Configuration File Issues

```
# Check the configuration file mount
docker exec jairouter ls -la /app/config

# View the configuration file contents
docker exec jairouter cat /app/config/application.yml

# Validate the configuration file format
docker exec jairouter java -jar app.jar --spring.config.location=/app/config/application.yml --dry-run
```

### 2. Debugging Tools

```
# Enter the container for debugging
docker exec -it jairouter sh

# View process status
docker exec jairouter ps aux

# View network connections
docker exec jairouter netstat -tulpn

# View system resources
docker stats jairouter
```

### 3. Performance Analysis

```
# View container resource usage
docker stats --no-stream jairouter

# View detailed container information
docker inspect jairouter

# View image layer information
docker history sodlinken/jairouter:latest
```

## Security Configuration

### 1. Container Security

```
# Run the container with a non-root user
docker run -d \
  --user 1001:1001 \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# Set a read-only file system (except for necessary directories)
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

```
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
    # Set as an internal network with no external access
    networks:
      jairouter-network:
        internal: true

networks:
  jairouter-network:
    driver: bridge
```

### 3. Secret Management

```
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

```
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

## Best Practices

### 1. Image Management

- Use multi-stage builds to reduce image size
- Regularly update base images
- Use image scanning tools to check for vulnerabilities
- Build separate images for different environments

### 2. Container Operations

- Use health checks to ensure service availability
- Configure appropriate resource limits
- Use volume mounts to persist data
- Configure log rotation

### 3. Monitoring and Alerting

- Integrate Prometheus monitoring
- Configure Grafana dashboards
- Set up alerts for key metrics
- Regularly check container health status

### 4. Security Considerations

- Run as a non-root user
- Regularly scan images for vulnerabilities
- Configure network isolation
- Use secret management tools

## Next Steps

After completing Docker deployment, you can:

- **[Kubernetes Deployment](kubernetes.md)** - Scale to a K8s cluster
- **[Production Deployment](production.md)** - Configure a highly available production environment
- **[Monitoring Guide](../monitoring/index.md)** - Set up a complete monitoring system
- **[Troubleshooting](../troubleshooting/index.md)** - Learn troubleshooting and resolution

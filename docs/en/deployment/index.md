# Deployment Guide

This guide covers various deployment options for JAiRouter, from simple standalone deployment to production-ready containerized environments.

## Deployment Options

JAiRouter supports multiple deployment strategies:

1. **[Docker Deployment](docker.md)** - Containerized deployment (recommended)
2. **[Kubernetes Deployment](kubernetes.md)** - Orchestrated container deployment
3. **[Production Environment](production.md)** - Production-ready configurations
4. **[China Optimization](china-optimization.md)** - Optimized deployment for China

## Quick Deployment

### Docker (Recommended)

The fastest way to deploy JAiRouter:

```bash
# Pull the latest image
docker pull jairouter/model-router:latest

# Run with basic configuration
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v ./config:/app/config \
  -v ./logs:/app/logs \
  jairouter/model-router:latest
```

### Standalone JAR

For simple deployments without Docker:

```bash
# Download the latest release
wget https://github.com/Lincoln-cn/JAiRouter/releases/latest/download/jairouter.jar

# Run the application
java -jar jairouter.jar
```

## Configuration for Deployment

### Environment Variables

Configure JAiRouter using environment variables:

```bash
# Server configuration
export SERVER_PORT=8080
export SPRING_PROFILES_ACTIVE=prod

# Service configuration
export MODEL_SERVICES_CHAT_LOAD_BALANCE_TYPE=least-connections
export MODEL_SERVICES_CHAT_RATE_LIMIT_CAPACITY=1000

# Storage configuration
export STORE_TYPE=file
export STORE_PATH=/app/config
```

### Configuration Files

Mount configuration files for persistent settings:

```yaml
# docker-compose.yml
version: '3.8'
services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    restart: unless-stopped
```

## Health Checks

Configure health checks for deployment orchestration:

### Docker Health Check

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

### Kubernetes Health Check

```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jairouter
    image: jairouter/model-router:latest
    livenessProbe:
      httpGet:
        path: /actuator/health
        port: 8080
      initialDelaySeconds: 60
      periodSeconds: 30
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
```

## Monitoring Setup

### Prometheus Integration

JAiRouter exposes metrics for Prometheus:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### Log Management

Configure structured logging for production:

```yaml
# application-prod.yml
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework.web: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /app/logs/jairouter.log
    max-size: 100MB
    max-history: 30
```

## Security Considerations

### Network Security

- Use HTTPS in production
- Implement proper firewall rules
- Consider VPN or private networks for backend services

### Access Control

```yaml
# Example nginx configuration for authentication
server {
    listen 443 ssl;
    server_name jairouter.example.com;
    
    # SSL configuration
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    # Basic authentication
    auth_basic "JAiRouter Access";
    auth_basic_user_file /etc/nginx/.htpasswd;
    
    location / {
        proxy_pass http://jairouter:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Performance Tuning

### JVM Configuration

```bash
# Production JVM settings
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/app/logs/ \
     -jar jairouter.jar
```

### Connection Pooling

```yaml
# application-prod.yml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20000
    max-connections: 8192
```

## Backup and Recovery

### Configuration Backup

```bash
# Backup configuration
tar -czf jairouter-config-$(date +%Y%m%d).tar.gz config/

# Automated backup script
#!/bin/bash
BACKUP_DIR="/backup/jairouter"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR
tar -czf $BACKUP_DIR/config-$DATE.tar.gz config/
find $BACKUP_DIR -name "config-*.tar.gz" -mtime +30 -delete
```

### Database Backup (if using external storage)

```bash
# Example for PostgreSQL
pg_dump -h postgres-host -U username -d jairouter > jairouter-db-$(date +%Y%m%d).sql
```

## Scaling Strategies

### Horizontal Scaling

Deploy multiple JAiRouter instances behind a load balancer:

```yaml
# docker-compose.yml for multiple instances
version: '3.8'
services:
  jairouter-1:
    image: jairouter/model-router:latest
    ports:
      - "8081:8080"
    volumes:
      - ./config:/app/config:ro
  
  jairouter-2:
    image: jairouter/model-router:latest
    ports:
      - "8082:8080"
    volumes:
      - ./config:/app/config:ro
  
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - jairouter-1
      - jairouter-2
```

### Vertical Scaling

Increase resources for single instance:

```yaml
# docker-compose.yml with resource limits
services:
  jairouter:
    image: jairouter/model-router:latest
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '1.0'
          memory: 2G
```

## Troubleshooting Deployment

### Common Issues

**Port Already in Use**:
```bash
# Check what's using the port
netstat -tulpn | grep :8080
# or
lsof -i :8080
```

**Permission Issues**:
```bash
# Fix file permissions
chmod -R 755 config/
chown -R 1000:1000 logs/
```

**Memory Issues**:
```bash
# Monitor memory usage
docker stats jairouter
# Increase memory limits
docker run -m 2g jairouter/model-router:latest
```

### Log Analysis

```bash
# View application logs
docker logs jairouter

# Follow logs in real-time
docker logs -f jairouter

# Search for errors
docker logs jairouter 2>&1 | grep ERROR
```

## Next Steps

Choose your deployment method:

1. **[Docker Deployment](docker.md)** - Start with containerized deployment
2. **[Kubernetes Deployment](kubernetes.md)** - For orchestrated environments
3. **[Production Environment](production.md)** - Production-ready configurations
4. **[China Optimization](china-optimization.md)** - Optimized for China networks
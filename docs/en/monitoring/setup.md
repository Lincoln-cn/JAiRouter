# Monitoring Setup Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


This document describes how to set up and configure the monitoring features of JAiRouter, including basic configuration, monitoring stack deployment, and verification steps.

## Environment Requirements

### System Requirements
- **Operating System**: Windows 10+, Linux, macOS
- **Java**: 17+
- **Docker**: 20.10+ (for monitoring stack deployment)
- **Memory**: At least 4GB available memory
- **Disk**: At least 10GB available space

### Network Ports
- **JAiRouter**: 8080 (application port), 8081 (management port)
- **Prometheus**: 9090
- **Grafana**: 3000
- **AlertManager**: 9093

## Quick Deployment

### Method 1: One-click Deployment Script

#### Windows PowerShell
```powershell
# Run deployment script
.\scripts\setup-monitoring.ps1

# Run with parameters
.\scripts\setup-monitoring.ps1 -Environment prod -EnableAlerts
```

#### Linux/macOS
```bash
# Give execution permission to script
chmod +x scripts/setup-monitoring.sh

# Run deployment script
./scripts/setup-monitoring.sh

# Run with parameters
./scripts/setup-monitoring.sh --environment prod --enable-alerts
```

### Method 2: Manual Deployment

#### 1. Create necessary directories
```bash
mkdir -p monitoring/data/{prometheus,grafana,alertmanager}
```

#### 2. Start monitoring stack
```bash
# Start complete monitoring stack
docker-compose -f docker-compose-monitoring.yml up -d

# Or use Make command
make compose-up-monitoring
```

#### 3. Verify service status
```bash
# Check service status
docker-compose -f docker-compose-monitoring.yml ps

# Check service logs
docker-compose -f docker-compose-monitoring.yml logs
```

## Basic Configuration

### JAiRouter Application Configuration

Enable monitoring in [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml):

```yaml
# Monitoring metrics configuration
monitoring:
  metrics:
    # Enable monitoring
    enabled: true
    
    # Metric prefix
    prefix: "jairouter"
    
    # Metric collection interval
    collection-interval: 10s
    
    # Enabled metric categories
    enabled-categories:
      - system      # System metrics
      - business    # Business metrics
      - infrastructure  # Infrastructure metrics
    
    # Custom tags
    custom-tags:
      environment: "${spring.profiles.active:default}"
      version: "@project.version@"

# Spring Actuator configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  
  endpoint:
    health:
      show-details: always
    prometheus:
      cache:
        time-to-live: 10s
  
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
```

### Prometheus Configuration

Create `monitoring/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "rules/*.yml"

scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
    honor_labels: true

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

### Grafana Configuration

#### Data Source Auto Configuration
Create `monitoring/grafana/provisioning/datasources/prometheus.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

#### Dashboard Auto Import
Create `monitoring/grafana/provisioning/dashboards/jairouter-dashboards.yml`:

```yaml
apiVersion: 1

providers:
  - name: 'jairouter'
    orgId: 1
    folder: 'JAiRouter'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

## Docker Compose Configuration

### Complete Monitoring Stack Configuration

Create [docker-compose-monitoring.yml](file://D:\IdeaProjects\model-router\docker-compose-monitoring.yml):

```yaml
version: '3.8'

services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - MONITORING_METRICS_ENABLED=true
    volumes:
      - ./config:/app/config
    networks:
      - monitoring
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus:/etc/prometheus
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=30d'
      - '--storage.tsdb.retention.size=10GB'
      - '--web.enable-lifecycle'
      - '--web.enable-admin-api'
    networks:
      - monitoring
    depends_on:
      - jairouter

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=jairouter2024
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_INSTALL_PLUGINS=grafana-piechart-panel
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    networks:
      - monitoring
    depends_on:
      - prometheus

  alertmanager:
    image: prom/alertmanager:latest
    ports:
      - "9093:9093"
    volumes:
      - ./monitoring/alertmanager:/etc/alertmanager
      - alertmanager_data:/alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
      - '--web.external-url=http://localhost:9093'
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:
  alertmanager_data:

networks:
  monitoring:
    driver: bridge
```

## Verify Installation

### 1. Check Service Status

```bash
# Check all service status
docker-compose -f docker-compose-monitoring.yml ps

# Check JAiRouter health status
curl http://localhost:8080/actuator/health

# Check Prometheus target status
curl http://localhost:9090/api/v1/targets
```

### 2. Verify Metrics Collection

```bash
# Check metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Check specific metrics
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

### 3. Verify Grafana Dashboard

1. Visit http://localhost:3000
2. Log in with admin/jairouter2024
3. Check data source connection status
4. Verify dashboard displays data correctly

## Environment-Specific Configuration

### Development Environment

```yaml
# application-dev.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
    performance:
      async-processing: false

management:
  endpoints:
    web:
      exposure:
        include: "*"
```

### Production Environment

```yaml
# application-prod.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
    performance:
      async-processing: true
      batch-size: 500
    security:
      data-masking: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  security:
    enabled: true
```

### Test Environment

```yaml
# application-test.yml
monitoring:
  metrics:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health
```

## Security Configuration

### Endpoint Security

```yaml
management:
  server:
    port: 8081
    address: 127.0.0.1
  endpoints:
    web:
      path-mapping:
        prometheus: /metrics
  security:
    enabled: true

spring:
  security:
    user:
      name: admin
      password: ${ACTUATOR_PASSWORD:admin}
      roles: ACTUATOR
```

### Network Security

```yaml
# Restrict Prometheus access
networks:
  monitoring:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## Performance Optimization

### Metrics Sampling Configuration

```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1    # 10% sampling
      backend-metrics: 0.5    # 50% sampling
      infrastructure-metrics: 0.1
      system-metrics: 0.5
```

### Asynchronous Processing Configuration

```yaml
monitoring:
  metrics:
    performance:
      async-processing: true
      async-thread-pool-size: 4
      batch-size: 100
      buffer-size: 1000
      processing-timeout: 5s
```

### Memory Optimization Configuration

```yaml
monitoring:
  metrics:
    memory:
      cache-size: 10000
      cache-expiry: 5m
      memory-threshold: 80
      low-memory-sampling-rate: 0.1
```

## Troubleshooting

### Common Issues

#### 1. Metrics Endpoint Unreachable
**Symptom**: Accessing `/actuator/prometheus` returns 404

**Solution**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
```

#### 2. Grafana Cannot Connect to Prometheus
**Symptom**: Dashboard shows "No data"

**Solution**:
```bash
# Check network connectivity
docker exec grafana curl http://prometheus:9090/api/v1/query?query=up

# Check Prometheus configuration
docker exec prometheus cat /etc/prometheus/prometheus.yml
```

#### 3. Monitoring Data is Empty
**Symptom**: Prometheus endpoint returns empty data

**Solution**:
```yaml
monitoring:
  metrics:
    enabled: true
    enabled-categories:
      - system
      - business
      - infrastructure
```

### Log Checking

```bash
# Check JAiRouter logs
docker logs jairouter

# Check Prometheus logs
docker logs prometheus

# Check Grafana logs
docker logs grafana
```

## Maintenance Operations

### Backup Configuration

```bash
# Backup configuration files
tar -czf monitoring-config-backup-$(date +%Y%m%d).tar.gz monitoring/

# Backup Grafana data
docker exec grafana tar -czf /tmp/grafana-backup.tar.gz /var/lib/grafana
docker cp grafana:/tmp/grafana-backup.tar.gz ./grafana-backup-$(date +%Y%m%d).tar.gz
```

### Update Monitoring Stack

```bash
# Pull latest images
docker-compose -f docker-compose-monitoring.yml pull

# Restart services
docker-compose -f docker-compose-monitoring.yml up -d
```

### Clean Up Old Data

```bash
# Clean up unused Docker volumes
docker volume prune

# Clean up old Prometheus data (use with caution)
docker exec prometheus rm -rf /prometheus/01*
```

## Next Steps

After setup is complete, you can:

1. [Configure Grafana Dashboards](dashboards.md)
2. [Set up Alert Rules](alerts.md)
3. [Learn about Available Metrics](metrics.md)
4. [Perform Performance Optimization](performance.md)

---

**Note**: Before deploying in production environments, ensure appropriate security measures and resource limits are configured.

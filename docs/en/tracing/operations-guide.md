# Operations Guide

This document provides a complete guide for operating the JAiRouter distributed tracing system in production environments.

## Production Deployment

### Environment Preparation

#### System Requirements
- **JVM**: OpenJDK 17 or higher
- **Memory**: Minimum 4GB, recommended 8GB+
- **CPU**: 4 cores or more
- **Disk**: SSD storage, at least 50GB available space

#### Dependency Services
```yaml
# docker-compose.yml example
version: '3.8'
services:
  jairouter:
    image: jairouter:latest
    environment:
      - JAIROUTER_TRACING_ENABLED=true
      - JAIROUTER_TRACING_EXPORTER_TYPE=otlp
    depends_on:
      - otel-collector
      
  otel-collector:
    image: otel/opentelemetry-collector:latest
    ports:
      - "4317:4317"
    volumes:
      - ./otel-config.yaml:/etc/config.yaml
```

### Production Configuration

#### Basic Configuration
```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter-prod"
    service-version: "${app.version}"
    environment: "production"
    
    # Sampling configuration
    sampling:
      strategy: "adaptive"
      adaptive:
        base-sample-rate: 0.01      # 1% base sampling
        max-traces-per-second: 100
        error-sample-rate: 1.0      # 100% error sampling
        slow-request-threshold: 3000
    
    # Export configuration
    exporter:
      type: "otlp"
      batch-size: 512
      export-timeout: 10s
      max-queue-size: 2048
      
    # Memory management
    memory:
      max-spans: 50000
      cleanup-interval: 30s
      span-ttl: 300s
      
    # Security configuration
    security:
      enabled: true
      sensitive-headers:
        - "Authorization"
        - "Cookie"
        - "X-API-Key"
```

#### JVM Tuning
```bash
# Production JVM parameters
-Xmx8g -Xms8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+UnlockDiagnosticVMOptions
-XX:+LogVMOutput
-XX:LogFile=/var/log/jairouter/gc.log
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
```

## Monitoring and Alerting

### Prometheus Metrics Configuration

#### Metric Collection
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter-tracing'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

#### Key Metrics
```promql
# Tracing export success rate
rate(jairouter_tracing_spans_exported_total[5m]) / 
rate(jairouter_tracing_spans_created_total[5m])

# Average response time
jairouter_tracing_request_duration_seconds_sum / 
jairouter_tracing_request_duration_seconds_count

# Memory usage ratio
jairouter_tracing_memory_used_ratio

# Error rate
rate(jairouter_tracing_errors_total[5m])
```

### Alert Rules

```yaml
# tracing-alerts.yml
groups:
  - name: jairouter_tracing
    rules:
      - alert: TracingExportFailureHigh
        expr: rate(jairouter_tracing_export_errors_total[5m]) > 0.05
        for: 2m
        labels:
          severity: warning
          service: jairouter
        annotations:
          summary: "High tracing data export failure rate"
          description: "Tracing data export failure rate exceeded 5% in the last 5 minutes"
          
      - alert: TracingMemoryUsageHigh
        expr: jairouter_tracing_memory_used_ratio > 0.85
        for: 1m
        labels:
          severity: critical
          service: jairouter
        annotations:
          summary: "High tracing system memory usage"
          
      - alert: TracingSlowRequests
        expr: histogram_quantile(0.95, jairouter_tracing_request_duration_seconds_bucket) > 5
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "95% of request processing time exceeds 5 seconds"
```

### Grafana Dashboard

#### Core Panel Configuration
```json
{
  "dashboard": {
    "title": "JAiRouter Tracing Monitoring",
    "panels": [
      {
        "title": "Request Tracing Overview",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(jairouter_tracing_requests_total[5m])",
            "legendFormat": "RPS"
          }
        ]
      },
      {
        "title": "Tracing Data Export Status",
        "type": "timeseries",
        "targets": [
          {
            "expr": "rate(jairouter_tracing_spans_exported_total[5m])",
            "legendFormat": "Export Success"
          },
          {
            "expr": "rate(jairouter_tracing_export_errors_total[5m])",
            "legendFormat": "Export Failed"
          }
        ]
      }
    ]
  }
}
```

## Capacity Planning

### Memory Planning

#### Span Memory Estimation
```bash
# Each Span occupies approximately 2KB of memory
# 1000 requests per second, 10% sampling rate, 5-minute Span TTL
# Memory requirement = 1000 * 0.1 * 300 * 2KB ≈ 60MB

# Recommended configuration
jairouter:
  tracing:
    memory:
      max-spans: 100000  # Adjust based on memory capacity
      span-ttl: 300s     # 5-minute TTL
```

#### Dynamic Adjustment Strategy
```yaml
jairouter:
  tracing:
    memory:
      # Memory pressure threshold
      memory-threshold: 0.8
      
      # Auto cleanup configuration
      auto-cleanup:
        enabled: true
        trigger-threshold: 0.85
        target-threshold: 0.7
```

### Storage Planning

#### Log Storage
```yaml
# logback-spring.xml
<configuration>
    <appender name="TRACING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/jairouter/tracing.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>/var/log/jairouter/tracing.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>
</configuration>
```

## Security Operations

### Data Sanitization Check

```bash
# Regularly check if sensitive data is properly sanitized
grep -r "password\|token\|secret" /var/log/jairouter/tracing.log

# Check sensitive information in configuration
curl -s http://localhost:8080/actuator/configprops | \
  jq '.jairouter.tracing.security.sensitive_headers'
```

### Access Control Audit

```yaml
# Enable security audit
jairouter:
  tracing:
    security:
      audit:
        enabled: true
        log-access: true
        log-config-changes: true
        retention-days: 90
```

### Encryption Configuration Management

```bash
# Manage sensitive configuration using environment variables
export JAIROUTER_TRACING_EXPORTER_OTLP_HEADERS_API_KEY="your-api-key"

# Or use Kubernetes Secret
kubectl create secret generic tracing-config \
  --from-literal=api-key=your-api-key
```

## Performance Tuning

### Real-time Performance Monitoring

```bash
# Monitoring script example
#!/bin/bash
while true; do
    echo "=== $(date) ==="
    
    # CPU usage
    echo "CPU: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)"
    
    # Memory usage
    echo "Memory: $(free -m | awk 'NR==2{printf "%.1f%%", $3*100/$2}')"
    
    # Tracing metrics
    curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.spans.active | \
      jq '.measurements[0].value'
    
    sleep 30
done
```

### Automated Tuning

```yaml
# Configure automatic tuning strategy
jairouter:
  tracing:
    auto-tuning:
      enabled: true
      
      # Reduce sampling rate when CPU usage exceeds 80%
      cpu-threshold: 80
      sampling-rate-adjustment: 0.5
      
      # Trigger cleanup when memory usage exceeds 85%
      memory-threshold: 85
      cleanup-aggressive: true
```

## Backup and Recovery

### Configuration Backup

```bash
# Daily configuration backup script
#!/bin/bash
DATE=$(date +%Y%m%d)
BACKUP_DIR="/backup/jairouter-config"

# Backup current configuration
mkdir -p $BACKUP_DIR
curl -s http://localhost:8080/actuator/configprops > \
  $BACKUP_DIR/config-$DATE.json

# Keep 30 days of backups
find $BACKUP_DIR -name "config-*.json" -mtime +30 -delete
```

### Tracing Data Backup

```yaml
# Configure tracing data export to long-term storage
jairouter:
  tracing:
    exporter:
      backup:
        enabled: true
        location: "/backup/tracing-data"
        retention-days: 90
        compression: true
```

## Upgrade and Maintenance

### Rolling Upgrade Strategy

```bash
# Rolling upgrade script
#!/bin/bash

# 1. Health check
curl -f http://localhost:8080/actuator/health/tracing || exit 1

# 2. Export current configuration
curl -s http://localhost:8080/actuator/configprops > /tmp/pre-upgrade-config.json

# 3. Perform upgrade
docker-compose pull jairouter
docker-compose up -d jairouter

# 4. Post-upgrade verification
sleep 30
curl -f http://localhost:8080/actuator/health/tracing || {
    echo "Upgrade failed, rolling back..."
    docker-compose down
    # Rollback logic
}
```

### Maintenance Window Operations

```bash
# Maintenance mode script
#!/bin/bash

case $1 in
    "enter")
        # Enter maintenance mode
        echo "Entering maintenance mode..."
        
        # Reduce sampling rate to reduce load
        curl -X PUT http://localhost:8080/api/admin/tracing/sampling-rate \
             -H "Content-Type: application/json" \
             -d '{"rate": 0.01}'
        
        # Wait for current spans to be processed
        sleep 60
        ;;
        
    "exit")
        # Exit maintenance mode
        echo "Exiting maintenance mode..."
        
        # Restore normal sampling rate
        curl -X PUT http://localhost:8080/api/admin/tracing/sampling-rate \
             -H "Content-Type: application/json" \
             -d '{"rate": 0.1}'
        ;;
esac
```

## Emergency Response

### Common Emergency Scenarios

#### 1. Tracing System Overload
```bash
# Emergency sampling rate reduction
curl -X PUT http://localhost:8080/api/admin/tracing/emergency-config \
     -d '{"sampling_rate": 0.001, "reason": "system_overload"}'

# Temporarily disable tracing
curl -X POST http://localhost:8080/api/admin/tracing/disable \
     -d '{"duration": "1h", "reason": "emergency"}'
```

#### 2. Exporter Failure
```yaml
# Switch to backup exporter
jairouter:
  tracing:
    exporter:
      fallback:
        enabled: true
        type: "logging"  # Temporarily use log export
```

#### 3. Memory Leak
```bash
# Force GC and memory cleanup
curl -X POST http://localhost:8080/actuator/gc
curl -X POST http://localhost:8080/api/admin/tracing/force-cleanup
```

### Emergency Contact

Establish emergency response procedures:
1. **Monitoring Alerts** → Automatic notification to operations team
2. **Issue Classification** → Determine impact scope and priority  
3. **Emergency Handling** → Execute predefined emergency scripts
4. **Issue Follow-up** → Record and analyze root causes

## Best Practices Summary

### 1. Monitoring Strategy
- Set multi-level alerts (warning, critical, emergency)
- Regularly check tracing data integrity
- Monitor system resource usage trends

### 2. Performance Optimization
- Adjust sampling rate based on business needs
- Regularly clean up expired data
- Properly configure batch processing size

### 3. Security Control
- Regularly review sensitive data filtering rules
- Enable configuration change audit logs
- Implement principle of least privilege

### 4. Disaster Recovery
- Establish backup and recovery procedures
- Prepare emergency response plans
- Regularly conduct failure drills

## Next Steps

- [Troubleshooting](troubleshooting.md) - Detailed problem diagnosis and solutions
- [Performance Tuning](performance-tuning.md) - In-depth performance optimization guide
- [Developer Integration](../development/tracing-integration.md) - Developer integration documentation
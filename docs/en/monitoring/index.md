# Monitoring Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


JAiRouter provides comprehensive monitoring capabilities to help you track performance, health, and usage patterns of your AI model routing gateway.

## Monitoring Overview

JAiRouter offers multiple monitoring approaches:

1. **[Setup](setup.md)** - Configure monitoring infrastructure
2. **[Dashboards](dashboards.md)** - Grafana dashboards and visualizations
3. **[Alerts](alerts.md)** - Alert configuration and notifications
4. **[Troubleshooting](troubleshooting.md)** - Monitoring-based troubleshooting

## Built-in Monitoring Features

### Health Checks

JAiRouter provides multiple health check endpoints:

```bash
# Overall application health
curl http://localhost:8080/actuator/health

# Detailed health information
curl http://localhost:8080/actuator/health/detailed

# Readiness probe (for Kubernetes)
curl http://localhost:8080/actuator/health/readiness

# Liveness probe (for Kubernetes)
curl http://localhost:8080/actuator/health/liveness
```

### Metrics Collection

JAiRouter exposes metrics in multiple formats:

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# JSON metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/http.server.requests
```

### Application Information

Get detailed application information:

```bash
# Application info
curl http://localhost:8080/actuator/info

# Environment details
curl http://localhost:8080/actuator/env

# Configuration properties
curl http://localhost:8080/actuator/configprops
```

## Key Metrics

### Request Metrics

| Metric | Description | Type |
|--------|-------------|------|
| `http.server.requests` | HTTP request duration and count | Timer |
| `jairouter.requests.total` | Total requests by service type | Counter |
| `jairouter.requests.duration` | Request processing time | Timer |
| `jairouter.requests.errors` | Error count by type | Counter |

### Load Balancer Metrics

| Metric | Description | Type |
|--------|-------------|------|
| `jairouter.loadbalancer.requests` | Requests per instance | Counter |
| `jairouter.loadbalancer.active_connections` | Active connections per instance | Gauge |
| `jairouter.loadbalancer.instance_health` | Instance health status | Gauge |

### Rate Limiter Metrics

| Metric | Description | Type |
|--------|-------------|------|
| `jairouter.ratelimit.requests.allowed` | Allowed requests | Counter |
| `jairouter.ratelimit.requests.denied` | Denied requests | Counter |
| `jairouter.ratelimit.tokens.available` | Available tokens | Gauge |

### Circuit Breaker Metrics

| Metric | Description | Type |
|--------|-------------|------|
| `jairouter.circuitbreaker.state` | Circuit breaker state | Gauge |
| `jairouter.circuitbreaker.failures` | Failure count | Counter |
| `jairouter.circuitbreaker.successes` | Success count | Counter |

## Quick Monitoring Setup

### Docker Compose with Monitoring Stack

```yaml
version: '3.8'
services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
    volumes:
      - ./config:/app/config:ro
    environment:
      - MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus,info

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-storage:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro

volumes:
  grafana-storage:
```

### Prometheus Configuration

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
```

### Grafana Data Source

```yaml
# monitoring/grafana/datasources/prometheus.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

## Custom Metrics

### Adding Custom Metrics

JAiRouter allows you to add custom metrics:

```java
@Component
public class CustomMetrics {
    private final MeterRegistry meterRegistry;
    private final Counter customRequestCounter;
    private final Timer customProcessingTimer;

    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.customRequestCounter = Counter.builder("jairouter.custom.requests")
            .description("Custom request counter")
            .register(meterRegistry);
        this.customProcessingTimer = Timer.builder("jairouter.custom.processing.time")
            .description("Custom processing time")
            .register(meterRegistry);
    }
}
```

### Business Metrics

Track business-specific metrics:

```bash
# Model usage statistics
curl http://localhost:8080/actuator/metrics/jairouter.model.usage

# Token consumption
curl http://localhost:8080/actuator/metrics/jairouter.tokens.consumed

# Cost tracking
curl http://localhost:8080/actuator/metrics/jairouter.cost.total
```

## Alerting

### Basic Alert Rules

```yaml
# monitoring/alert-rules.yml
groups:
  - name: jairouter
    rules:
      - alert: JAiRouterDown
        expr: up{job="jairouter"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter instance is down"
          description: "JAiRouter instance {{ $labels.instance }} has been down for more than 1 minute."

      - alert: HighErrorRate
        expr: rate(jairouter_requests_errors_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors per second."

      - alert: CircuitBreakerOpen
        expr: jairouter_circuitbreaker_state == 1
        for: 30s
        labels:
          severity: warning
        annotations:
          summary: "Circuit breaker is open"
          description: "Circuit breaker for {{ $labels.service }} is open."
```

### Notification Channels

Configure alert notifications:

```yaml
# alertmanager.yml
global:
  smtp_smarthost: 'localhost:587'
  smtp_from: 'alerts@jairouter.com'

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'web.hook'

receivers:
  - name: 'web.hook'
    email_configs:
      - to: 'admin@jairouter.com'
        subject: 'JAiRouter Alert: {{ .GroupLabels.alertname }}'
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          {{ end }}
```

## Log Monitoring

### Structured Logging

Configure structured logging for better monitoring:

```yaml
# application.yml
logging:
  level:
    org.unreal.modelrouter: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  file:
    name: logs/jairouter.log
```

### Log Aggregation

Use ELK stack or similar for log aggregation:

```yaml
# filebeat.yml
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /app/logs/*.log
  fields:
    service: jairouter
  fields_under_root: true

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "jairouter-logs-%{+yyyy.MM.dd}"
```

## Performance Monitoring

### JVM Metrics

Monitor JVM performance:

```bash
# Heap memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Garbage collection
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# Thread count
curl http://localhost:8080/actuator/metrics/jvm.threads.live
```

### Application Performance

Track application-specific performance:

```bash
# Connection pool metrics
curl http://localhost:8080/actuator/metrics/hikaricp.connections

# HTTP client metrics
curl http://localhost:8080/actuator/metrics/http.client.requests

# Cache metrics (if using cache)
curl http://localhost:8080/actuator/metrics/cache.gets
```

## Monitoring Best Practices

### 1. Set Up Proper Alerting

- Alert on symptoms, not causes
- Use appropriate thresholds
- Avoid alert fatigue

### 2. Monitor Key Business Metrics

- Request success rate
- Response time percentiles
- Model usage patterns
- Cost metrics

### 3. Use Dashboards Effectively

- Create role-specific dashboards
- Include both technical and business metrics
- Use appropriate time ranges

### 4. Regular Health Checks

- Implement comprehensive health checks
- Monitor dependencies
- Use circuit breakers appropriately

## Troubleshooting with Monitoring

### High Response Times

1. Check load balancer metrics
2. Examine backend service health
3. Review rate limiting settings
4. Analyze JVM metrics

### High Error Rates

1. Check circuit breaker status
2. Review backend service logs
3. Examine request patterns
4. Verify configuration

### Memory Issues

1. Monitor JVM heap usage
2. Check for memory leaks
3. Review garbage collection metrics
4. Analyze thread usage

## Next Steps

1. **[Setup](setup.md)** - Set up monitoring infrastructure
2. **[Dashboards](dashboards.md)** - Create monitoring dashboards
3. **[Alerts](alerts.md)** - Configure alerting rules
4. **[Troubleshooting](troubleshooting.md)** - Use monitoring for troubleshooting
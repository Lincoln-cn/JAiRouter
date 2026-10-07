# Monitoring API

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->

JAiRouter provides comprehensive monitoring APIs for health checks, metrics collection, and system status monitoring.

## Overview

The Monitoring API includes:

- **Health Checks** - Service and instance health status
- **Metrics** - Performance and usage statistics  
- **System Status** - Overall system health and configuration

All monitoring endpoints are available under the `/actuator/*` path and provide real-time insights into your JAiRouter deployment.

## Health Check Endpoints

### System Health

Get overall system health status:

```http
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 91943821312,
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    },
    "tracing": {
      "status": "UP"
    },
    "sharedState": {
      "status": "UP"
    },
    "rbacEndpointCoverage": {
      "status": "UP",
      "details": {
        "missing": 0
      }
    }
  }
}
```

> ⚠️ Component names are the registered health indicators: `tracing` (`TracingHealthIndicator.java:28`),
> `sharedState` (`SharedStateHealthIndicator.java:31`), `rbacEndpointCoverage`
> (`RbacEndpointCoverageHealthIndicator.java:27`) and `jwtRedisHealthIndicator`
> (`JwtRedisHealthMonitor.java:21`), plus Spring Boot's standard `diskSpace` etc.
> There is **no** `modelRouter` component; the `activeInstances` / `totalInstances` /
> `circuitBreakerStatus` structure does not exist in the code.

### Detailed Health Information

Get detailed health information including all components:

```http
GET /actuator/health
```

> ⚠️ There is **no `/actuator/health/detailed` endpoint**: only the `liveness` and
> `readiness` groups are defined (`config/base/monitoring-base.yml:70-84`). To see every
> component, call `GET /actuator/health` under the dev profile (`show-details: always`).

**Response:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 91943821312,
        "threshold": 10485760,
        "exists": true
      }
    }
  }
}
```

> ⚠️ The original document also listed a `modelRouter` component here (with
> `services.*.instances[]` detail) — **no such component exists in the code**, so it has
> been removed; see the note at the top of this section for the real health components.

## Metrics Endpoints

### Application Metrics

Get Prometheus-format metrics:

```http
GET /actuator/prometheus
```

**Response:**
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 2.38026752E8
jvm_memory_used_bytes{area="heap",id="PS Survivor Space",} 1048576.0
jvm_memory_used_bytes{area="heap",id="PS Old Gen",} 4.2991616E7

# HELP jairouter_requests_total Total number of requests
# TYPE jairouter_requests_total counter
jairouter_requests_total{service="chat",method="POST",status="200",} 1247.0
jairouter_requests_total{service="chat",method="POST",status="500",} 23.0
jairouter_requests_total{service="chat",method="POST",status="200",} 1156.0
jairouter_requests_total{service="chat",method="POST",status="500",} 18.0

# HELP jairouter_request_duration_seconds Request duration in seconds
# TYPE jairouter_request_duration_seconds histogram
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="0.1",} 234.0
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="0.5",} 892.0
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="1.0",} 1156.0
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="+Inf",} 1270.0

# HELP jairouter_circuit_breaker_state Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
# TYPE jairouter_circuit_breaker_state gauge
jairouter_circuit_breaker_state{service="chat",method="POST",} 0.0
jairouter_circuit_breaker_state{service="chat",method="POST",} 0.0

# HELP jairouter_rate_limit_remaining Rate limit remaining requests
# TYPE jairouter_rate_limit_remaining gauge
jairouter_rate_limit_remaining{service="chat",client_ip="192.168.1.100",} 45.0
jairouter_rate_limit_remaining{service="chat",client_ip="192.168.1.101",} 38.0
```

### Metrics Summary

Get human-readable metrics summary:

```http
GET /actuator/metrics
```

**Response:**
```json
{
  "names": [
    "jvm.memory.used",
    "jvm.memory.max", 
    "jvm.gc.pause",
    "http.server.requests",
    "jairouter_requests_total",
    "jairouter_request_duration_seconds",
    "jairouter_circuit_breaker_state",
    "jairouter_rate_limit_remaining",
    "jairouter_loadbalancer_selections_total"
  ]
}
```

### Specific Metric Details

Get details for a specific metric:

```http
GET /actuator/metrics/jairouter_requests_total
```

**Response:**
```json
{
  "name": "jairouter_requests_total",
  "description": "Total number of requests processed by model router",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 2647.0
    }
  ],
  "availableTags": [
    {
      "tag": "service",
      "values": ["chat", "embedding", "rerank", "tts", "stt", "imgGen", "imgEdit"]
    },
    {
      "tag": "method", 
      "values": ["POST", "GET"]
    },
    {
      "tag": "status",
      "values": ["200", "400", "500"]
    }
  ]
}
```

## System Information

### Application Info

Get application information:

```http
GET /actuator/info
```

**Response:**
```json
{
  "app": {
    "name": "JAiRouter",
    "version": "3.2.2",
    "description": "AI Model Service Router and Load Balancer"
  },
  "build": {
    "version": "3.2.2",
    "artifact": "model-router",
    "name": "model-router",
    "group": "org.unreal",
    "time": "2025-08-19T08:15:30.123Z"
  },
  "git": {
    "branch": "main",
    "commit": {
      "id": "3418d3f6",
      "time": "2025-08-19T08:00:00Z"
    }
  },
  "java": {
    "version": "17.0.8",
    "vendor": "Eclipse Adoptium"
  }
}
```

> ⚠️ The `build` / `git` sections require the `build-info` goal in `pom.xml` (not configured
> today), so the default `/actuator/info` does not return them; the output above is
> illustrative. The current project version is 3.2.2.

### Environment Information

Get environment and configuration details:

```http
GET /actuator/env
```

> ⚠️ `env` is **not in the default exposure list** (`config/base/monitoring-base.yml:60` exposes
> only health / info / metrics / prometheus / jairouter-metrics / error-tracking; see
> `application-prod.yml:40` for prod). Add `env` to
> `management.endpoints.web.exposure.include` first if you need it.

**Response:**
```json
{
  "activeProfiles": ["default"],
  "propertySources": [
    {
      "name": "server.ports",
      "properties": {
        "local.server.port": {
          "value": 8080
        }
      }
    },
    {
      "name": "applicationConfig: [classpath:/application.yml]",
      "properties": {
        "model-router.load-balancer.default-strategy": {
          "value": "ROUND_ROBIN"
        },
        "model-router.rate-limit.default-algorithm": {
          "value": "TOKEN_BUCKET"
        },
        "model-router.circuit-breaker.failure-threshold": {
          "value": 5
        }
      }
    }
  ]
}
```

> ⚠️ The output above is illustrative: every configuration key in this project lives under the
> `jairouter.*` prefix — there are **no `model-router.*` keys** (no match anywhere under
> `src/main/resources`).

## Custom Monitoring Endpoints

The gateway exposes the following custom monitoring endpoints:

### System Health Status

Get the overall system health status:

```http
GET /api/monitoring/health
```

### Monitoring Configuration

Get the current monitoring configuration:

```http
GET /api/monitoring/config
```

### Circuit Breaker Statistics

Get circuit breaker statistics:

```http
GET /api/monitoring/circuit-breaker/stats
```

### Degradation Status

Get the service degradation status:

```http
GET /api/monitoring/degradation/status
```

### Error Statistics

Get error statistics:

```http
GET /api/monitoring/errors/stats
```

### Cache Statistics

Get cache statistics:

```http
GET /api/monitoring/cache/stats
```

> **Note**: These are the custom monitoring endpoints JAiRouter (currently 3.2.2) actually
> provides; each maps to `MonitoringController.java:65,226,240,268,309,390,426`. See the
> [Management API](management-api.md) for more endpoints.

## Monitoring Integration

### Prometheus Integration

JAiRouter exposes metrics in Prometheus format at `/actuator/prometheus`. Configure Prometheus to scrape these metrics:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### Grafana Dashboard

Import the JAiRouter Grafana dashboard for visualization:

- **Dashboard ID**: Coming soon
- **Metrics**: Request rate, response time, error rate, circuit breaker status
- **Alerts**: High error rate, circuit breaker open, instance down

### Health Check Integration

Configure external monitoring tools to check JAiRouter health:

```bash
# Simple health check
curl -f http://localhost:8080/actuator/health || exit 1

# Detailed health check with specific component
curl -f http://localhost:8080/actuator/health/tracing || exit 1
```

## Monitoring Best Practices

### Key Metrics to Monitor

1. **Request Metrics**
   - Request rate (requests/second)
   - Response time (p50, p95, p99)
   - Error rate (percentage)

2. **Instance Health**
   - Instance availability
   - Health check response time
   - Circuit breaker state

3. **Resource Usage**
   - JVM memory usage
   - CPU utilization
   - Disk space

4. **Rate Limiting**
   - Rate limit utilization
   - Rejected requests
   - Client-specific limits

### Alerting Rules

Set up alerts for critical conditions:

```yaml
# Prometheus alerting rules
groups:
  - name: jairouter
    rules:
      - alert: HighErrorRate
        expr: rate(jairouter_requests_total{status="500"}[5m]) / rate(jairouter_requests_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          
      - alert: CircuitBreakerOpen
        expr: jairouter_circuit_breaker_state > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker is open"
          
      - alert: InstanceDown
        expr: up{job="jairouter"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter instance is down"
```

### Log Monitoring

Monitor application logs for:

- Error patterns
- Performance issues
- Configuration changes
- Security events

```bash
# Tail logs with filtering
tail -f logs/application.log | grep -E "(ERROR|WARN|Circuit|Rate)"
```

## Troubleshooting

### Common Issues

1. **High Response Time**
   - Check instance health
   - Review load balancer distribution
   - Monitor resource usage

2. **Circuit Breaker Open**
   - Check instance connectivity
   - Review error logs
   - Verify instance configuration

3. **Rate Limit Exceeded**
   - Review rate limit configuration
   - Check client request patterns
   - Consider increasing limits

### Debug Endpoints

Enable debug logging for detailed monitoring:

```yaml
# application.yml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: DEBUG
```

Access debug information:

```http
GET /actuator/loggers/org.unreal.modelrouter
```

> ⚠️ `loggers` is **not in the default exposure list** — add it to
> `management.endpoints.web.exposure.include` first.

## Security Considerations

### Monitoring Endpoint Security

Secure monitoring endpoints in production:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when-authorized
  security:
    enabled: true
```

### Sensitive Information

Avoid exposing sensitive data in metrics:

- API keys
- Internal URLs
- User information
- Configuration secrets

## Next Steps

- **[Management API](management-api.md)** - Configuration management
- **[Universal API](universal-api.md)** - OpenAI-compatible endpoints
- **[OpenAPI Specification](openapi-spec.md)** - Interactive API documentation
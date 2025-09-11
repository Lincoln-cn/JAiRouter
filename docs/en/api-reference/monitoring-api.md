# Monitoring API

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: latest  
> **作者**: Lincoln
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
    "modelRouter": {
      "status": "UP",
      "details": {
        "activeInstances": 5,
        "totalInstances": 8,
        "circuitBreakerStatus": "CLOSED"
      }
    }
  }
}
```

### Detailed Health Information

Get detailed health information including all components:

```http
GET /actuator/health/detailed
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
    "modelRouter": {
      "status": "UP",
      "details": {
        "services": {
          "chat": {
            "totalInstances": 3,
            "healthyInstances": 2,
            "instances": [
              {
                "id": "ollama-1",
                "url": "http://localhost:11434",
                "status": "UP",
                "lastCheck": "2025-08-19T10:30:00Z",
                "responseTime": 45
              },
              {
                "id": "ollama-2", 
                "url": "http://localhost:11435",
                "status": "UP",
                "lastCheck": "2025-08-19T10:30:00Z",
                "responseTime": 52
              },
              {
                "id": "ollama-3",
                "url": "http://localhost:11436", 
                "status": "DOWN",
                "lastCheck": "2025-08-19T10:29:45Z",
                "error": "Connection timeout"
              }
            ]
          },
          "embedding": {
            "totalInstances": 2,
            "healthyInstances": 2,
            "instances": [
              {
                "id": "xinference-1",
                "url": "http://localhost:9997",
                "status": "UP",
                "lastCheck": "2025-08-19T10:30:00Z",
                "responseTime": 38
              },
              {
                "id": "xinference-2",
                "url": "http://localhost:9998",
                "status": "UP", 
                "lastCheck": "2025-08-19T10:30:00Z",
                "responseTime": 41
              }
            ]
          }
        }
      }
    }
  }
}
```

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

# HELP model_router_requests_total Total number of requests
# TYPE model_router_requests_total counter
model_router_requests_total{service="chat",instance="ollama-1",status="success",} 1247.0
model_router_requests_total{service="chat",instance="ollama-1",status="error",} 23.0
model_router_requests_total{service="chat",instance="ollama-2",status="success",} 1156.0
model_router_requests_total{service="chat",instance="ollama-2",status="error",} 18.0

# HELP model_router_request_duration_seconds Request duration in seconds
# TYPE model_router_request_duration_seconds histogram
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="0.1",} 234.0
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="0.5",} 892.0
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="1.0",} 1156.0
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="+Inf",} 1270.0

# HELP model_router_circuit_breaker_state Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
# TYPE model_router_circuit_breaker_state gauge
model_router_circuit_breaker_state{service="chat",instance="ollama-1",} 0.0
model_router_circuit_breaker_state{service="chat",instance="ollama-2",} 0.0

# HELP model_router_rate_limit_remaining Rate limit remaining requests
# TYPE model_router_rate_limit_remaining gauge
model_router_rate_limit_remaining{service="chat",client_ip="192.168.1.100",} 45.0
model_router_rate_limit_remaining{service="chat",client_ip="192.168.1.101",} 38.0
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
    "model.router.requests.total",
    "model.router.request.duration",
    "model.router.circuit.breaker.state",
    "model.router.rate.limit.remaining",
    "model.router.load.balancer.weight"
  ]
}
```

### Specific Metric Details

Get details for a specific metric:

```http
GET /actuator/metrics/model.router.requests.total
```

**Response:**
```json
{
  "name": "model.router.requests.total",
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
      "values": ["chat", "embedding", "rerank", "tts", "stt", "image"]
    },
    {
      "tag": "instance", 
      "values": ["ollama-1", "ollama-2", "xinference-1", "xinference-2"]
    },
    {
      "tag": "status",
      "values": ["success", "error", "timeout", "circuit_breaker"]
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
    "version": "1.0.0",
    "description": "AI Model Service Router and Load Balancer"
  },
  "build": {
    "version": "1.0.0",
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

### Environment Information

Get environment and configuration details:

```http
GET /actuator/env
```

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

## Custom Monitoring Endpoints

### Service Instance Status

Get status of all service instances:

```http
GET /api/monitoring/instances
```

**Response:**
```json
{
  "services": {
    "chat": {
      "instances": [
        {
          "id": "ollama-1",
          "url": "http://localhost:11434",
          "adapter": "OLLAMA",
          "status": "HEALTHY",
          "lastHealthCheck": "2025-08-19T10:30:00Z",
          "responseTime": 45,
          "successRate": 0.982,
          "requestCount": 1270,
          "errorCount": 23,
          "circuitBreakerState": "CLOSED",
          "weight": 1.0
        },
        {
          "id": "ollama-2", 
          "url": "http://localhost:11435",
          "adapter": "OLLAMA",
          "status": "HEALTHY",
          "lastHealthCheck": "2025-08-19T10:30:00Z", 
          "responseTime": 52,
          "successRate": 0.985,
          "requestCount": 1174,
          "errorCount": 18,
          "circuitBreakerState": "CLOSED",
          "weight": 1.0
        },
        {
          "id": "ollama-3",
          "url": "http://localhost:11436",
          "adapter": "OLLAMA", 
          "status": "UNHEALTHY",
          "lastHealthCheck": "2025-08-19T10:29:45Z",
          "error": "Connection timeout",
          "circuitBreakerState": "OPEN",
          "weight": 0.0
        }
      ]
    }
  }
}
```

### Load Balancer Statistics

Get load balancer performance statistics:

```http
GET /api/monitoring/load-balancer
```

**Response:**
```json
{
  "services": {
    "chat": {
      "strategy": "ROUND_ROBIN",
      "totalRequests": 2444,
      "distribution": {
        "ollama-1": {
          "requests": 1270,
          "percentage": 52.0,
          "avgResponseTime": 45
        },
        "ollama-2": {
          "requests": 1174,
          "percentage": 48.0,
          "avgResponseTime": 52
        }
      }
    },
    "embedding": {
      "strategy": "LEAST_CONNECTIONS", 
      "totalRequests": 856,
      "distribution": {
        "xinference-1": {
          "requests": 428,
          "percentage": 50.0,
          "avgResponseTime": 38
        },
        "xinference-2": {
          "requests": 428,
          "percentage": 50.0,
          "avgResponseTime": 41
        }
      }
    }
  }
}
```

### Rate Limit Status

Get current rate limit status:

```http
GET /api/monitoring/rate-limit
```

**Response:**
```json
{
  "services": {
    "chat": {
      "algorithm": "TOKEN_BUCKET",
      "globalLimit": {
        "capacity": 1000,
        "remaining": 847,
        "refillRate": 100,
        "nextRefill": "2025-08-19T10:30:10Z"
      },
      "clientLimits": [
        {
          "clientIp": "192.168.1.100",
          "remaining": 45,
          "capacity": 50,
          "lastRequest": "2025-08-19T10:29:58Z"
        },
        {
          "clientIp": "192.168.1.101", 
          "remaining": 38,
          "capacity": 50,
          "lastRequest": "2025-08-19T10:29:59Z"
        }
      ]
    }
  }
}
```

### Circuit Breaker Status

Get circuit breaker status for all instances:

```http
GET /api/monitoring/circuit-breaker
```

**Response:**
```json
{
  "instances": [
    {
      "id": "ollama-1",
      "service": "chat",
      "state": "CLOSED",
      "failureCount": 2,
      "failureThreshold": 5,
      "successThreshold": 3,
      "timeout": 60000,
      "lastFailure": "2025-08-19T10:25:30Z",
      "nextRetry": null
    },
    {
      "id": "ollama-2",
      "service": "chat", 
      "state": "CLOSED",
      "failureCount": 1,
      "failureThreshold": 5,
      "successThreshold": 3,
      "timeout": 60000,
      "lastFailure": "2025-08-19T10:20:15Z",
      "nextRetry": null
    },
    {
      "id": "ollama-3",
      "service": "chat",
      "state": "OPEN",
      "failureCount": 8,
      "failureThreshold": 5,
      "successThreshold": 3,
      "timeout": 60000,
      "lastFailure": "2025-08-19T10:29:45Z",
      "nextRetry": "2025-08-19T10:30:45Z"
    }
  ]
}
```

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
curl -f http://localhost:8080/actuator/health/modelRouter || exit 1
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
        expr: rate(model_router_requests_total{status="error"}[5m]) / rate(model_router_requests_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          
      - alert: CircuitBreakerOpen
        expr: model_router_circuit_breaker_state > 0
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
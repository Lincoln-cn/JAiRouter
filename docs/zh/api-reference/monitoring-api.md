# 监控 API

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: latest  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 提供全面的监控 API，用于健康检查、指标收集和系统状态监控。

## 概述

监控 API 包括：

- **健康检查** - 服务和实例健康状态
- **指标** - 性能和使用统计
- **系统状态** - 整体系统健康和配置

所有监控端点都在 `/actuator/*` 路径下提供，为您的 JAiRouter 部署提供实时洞察。

## 健康检查端点

### 系统健康

获取整体系统健康状态：

```http
GET /actuator/health
```

**响应：**
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

### 详细健康信息

获取包括所有组件的详细健康信息：

```http
GET /actuator/health/detailed
```

**响应：**
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
                "error": "连接超时"
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

## 指标端点

### 应用指标

获取 Prometheus 格式的指标：

```http
GET /actuator/prometheus
```

**响应：**
```
# HELP jvm_memory_used_bytes 已使用内存量
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space",} 2.38026752E8
jvm_memory_used_bytes{area="heap",id="PS Survivor Space",} 1048576.0
jvm_memory_used_bytes{area="heap",id="PS Old Gen",} 4.2991616E7

# HELP model_router_requests_total 请求总数
# TYPE model_router_requests_total counter
model_router_requests_total{service="chat",instance="ollama-1",status="success",} 1247.0
model_router_requests_total{service="chat",instance="ollama-1",status="error",} 23.0
model_router_requests_total{service="chat",instance="ollama-2",status="success",} 1156.0
model_router_requests_total{service="chat",instance="ollama-2",status="error",} 18.0

# HELP model_router_request_duration_seconds 请求持续时间（秒）
# TYPE model_router_request_duration_seconds histogram
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="0.1",} 234.0
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="0.5",} 892.0
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="1.0",} 1156.0
model_router_request_duration_seconds_bucket{service="chat",instance="ollama-1",le="+Inf",} 1270.0

# HELP model_router_circuit_breaker_state 熔断器状态 (0=关闭, 1=打开, 2=半开)
# TYPE model_router_circuit_breaker_state gauge
model_router_circuit_breaker_state{service="chat",instance="ollama-1",} 0.0
model_router_circuit_breaker_state{service="chat",instance="ollama-2",} 0.0

# HELP model_router_rate_limit_remaining 限流剩余请求数
# TYPE model_router_rate_limit_remaining gauge
model_router_rate_limit_remaining{service="chat",client_ip="192.168.1.100",} 45.0
model_router_rate_limit_remaining{service="chat",client_ip="192.168.1.101",} 38.0
```

### 指标摘要

获取人类可读的指标摘要：

```http
GET /actuator/metrics
```

**响应：**
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

### 特定指标详情

获取特定指标的详细信息：

```http
GET /actuator/metrics/model.router.requests.total
```

**响应：**
```json
{
  "name": "model.router.requests.total",
  "description": "模型路由器处理的请求总数",
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

## 系统信息

### 应用信息

获取应用程序信息：

```http
GET /actuator/info
```

**响应：**
```json
{
  "app": {
    "name": "JAiRouter",
    "version": "1.0.0",
    "description": "AI 模型服务路由器和负载均衡器"
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

### 环境信息

获取环境和配置详情：

```http
GET /actuator/env
```

**响应：**
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

## 自定义监控端点

### 服务实例状态

获取所有服务实例的状态：

```http
GET /api/monitoring/instances
```

**响应：**
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
          "error": "连接超时",
          "circuitBreakerState": "OPEN",
          "weight": 0.0
        }
      ]
    }
  }
}
```

### 负载均衡器统计

获取负载均衡器性能统计：

```http
GET /api/monitoring/load-balancer
```

**响应：**
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

### 限流状态

获取当前限流状态：

```http
GET /api/monitoring/rate-limit
```

**响应：**
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

### 熔断器状态

获取所有实例的熔断器状态：

```http
GET /api/monitoring/circuit-breaker
```

**响应：**
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

## 监控集成

### Prometheus 集成

JAiRouter 在 `/actuator/prometheus` 端点暴露 Prometheus 格式的指标。配置 Prometheus 抓取这些指标：

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### Grafana 仪表板

导入 JAiRouter Grafana 仪表板进行可视化：

- **仪表板 ID**: 即将推出
- **指标**: 请求速率、响应时间、错误率、熔断器状态
- **告警**: 高错误率、熔断器打开、实例宕机

### 健康检查集成

配置外部监控工具检查 JAiRouter 健康状态：

```bash
# 简单健康检查
curl -f http://localhost:8080/actuator/health || exit 1

# 特定组件的详细健康检查
curl -f http://localhost:8080/actuator/health/modelRouter || exit 1
```

## 监控最佳实践

### 关键监控指标

1. **请求指标**
   - 请求速率（请求/秒）
   - 响应时间（p50、p95、p99）
   - 错误率（百分比）

2. **实例健康**
   - 实例可用性
   - 健康检查响应时间
   - 熔断器状态

3. **资源使用**
   - JVM 内存使用
   - CPU 利用率
   - 磁盘空间

4. **限流**
   - 限流利用率
   - 被拒绝的请求
   - 客户端特定限制

### 告警规则

为关键条件设置告警：

```yaml
# Prometheus 告警规则
groups:
  - name: jairouter
    rules:
      - alert: 高错误率
        expr: rate(model_router_requests_total{status="error"}[5m]) / rate(model_router_requests_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "检测到高错误率"
          
      - alert: 熔断器打开
        expr: model_router_circuit_breaker_state > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "熔断器已打开"
          
      - alert: 实例宕机
        expr: up{job="jairouter"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 实例已宕机"
```

### 日志监控

监控应用日志以发现：

- 错误模式
- 性能问题
- 配置变更
- 安全事件

```bash
# 过滤日志尾随
tail -f logs/application.log | grep -E "(ERROR|WARN|Circuit|Rate)"
```

## 故障排除

### 常见问题

1. **高响应时间**
   - 检查实例健康状态
   - 查看负载均衡器分布
   - 监控资源使用

2. **熔断器打开**
   - 检查实例连接性
   - 查看错误日志
   - 验证实例配置

3. **超出限流限制**
   - 查看限流配置
   - 检查客户端请求模式
   - 考虑增加限制

### 调试端点

启用调试日志以获得详细监控：

```yaml
# application.yml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: DEBUG
```

访问调试信息：

```http
GET /actuator/loggers/org.unreal.modelrouter
```

## 安全考虑

### 监控端点安全

在生产环境中保护监控端点：

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

### 敏感信息

避免在指标中暴露敏感数据：

- API 密钥
- 内部 URL
- 用户信息
- 配置机密

## 下一步

- **[管理 API](management-api.md)** - 配置管理
- **[统一 API](universal-api.md)** - OpenAI 兼容端点
- **[OpenAPI 规范](openapi-spec.md)** - 交互式 API 文档
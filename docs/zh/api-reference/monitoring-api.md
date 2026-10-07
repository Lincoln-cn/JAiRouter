# 监控 API

<!-- 版本信息 -->
> **文档版本**: 1.1.0  
> **最后更新**: 2026-09-15  
> **Git 提交**: 61384b4a  
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

> ⚠️ 组件名以实际注册的 health indicator 为准：`tracing`（`TracingHealthIndicator.java:28`）、
> `sharedState`（`SharedStateHealthIndicator.java:31`）、`rbacEndpointCoverage`
> （`RbacEndpointCoverageHealthIndicator.java:27`）、`jwtRedisHealthIndicator`
> （`JwtRedisHealthMonitor.java:21`），另有 Spring Boot 标准的 `diskSpace` 等。
> **没有** `modelRouter` 这个组件；原文里的 `activeInstances` / `totalInstances` /
> `circuitBreakerStatus` 结构在代码中不存在。

### 详细健康信息

获取包括所有组件的详细健康信息：

```http
GET /actuator/health
```

> ⚠️ **没有 `/actuator/health/detailed` 端点**：只定义了 `liveness` / `readiness` 两个
> group（`config/base/monitoring-base.yml:70-84`）。要看全部组件详情，请在 dev profile
> 下直接请求 `GET /actuator/health`（该 profile 配了 `show-details: always`）。

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
    }
  }
}
```

> ⚠️ 原文档在这里还列了一个 `modelRouter` 组件（含 `services.*.instances[]` 明细）——
> **该组件在代码里不存在**，示例已删除；真实注册的 health 组件见本节开头的说明。

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

# HELP jairouter_requests_total 请求总数
# TYPE jairouter_requests_total counter
jairouter_requests_total{service="chat",method="POST",status="200",} 1247.0
jairouter_requests_total{service="chat",method="POST",status="500",} 23.0
jairouter_requests_total{service="chat",method="POST",status="200",} 1156.0
jairouter_requests_total{service="chat",method="POST",status="500",} 18.0

# HELP jairouter_request_duration_seconds 请求持续时间（秒）
# TYPE jairouter_request_duration_seconds histogram
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="0.1",} 234.0
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="0.5",} 892.0
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="1.0",} 1156.0
jairouter_request_duration_seconds_bucket{service="chat",method="POST",le="+Inf",} 1270.0

# HELP jairouter_circuit_breaker_state 熔断器状态 (0=关闭, 1=打开, 2=半开)
# TYPE jairouter_circuit_breaker_state gauge
jairouter_circuit_breaker_state{service="chat",method="POST",} 0.0
jairouter_circuit_breaker_state{service="chat",method="POST",} 0.0

# HELP jairouter_rate_limit_remaining 限流剩余请求数
# TYPE jairouter_rate_limit_remaining gauge
jairouter_rate_limit_remaining{service="chat",client_ip="192.168.1.100",} 45.0
jairouter_rate_limit_remaining{service="chat",client_ip="192.168.1.101",} 38.0
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
    "jairouter_requests_total",
    "jairouter_request_duration_seconds",
    "jairouter_circuit_breaker_state",
    "jairouter_rate_limit_remaining",
    "jairouter_loadbalancer_selections_total"
  ]
}
```

### 特定指标详情

获取特定指标的详细信息：

```http
GET /actuator/metrics/jairouter_requests_total
```

**响应：**
```json
{
  "name": "jairouter_requests_total",
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
    "version": "3.2.2",
    "description": "AI 模型服务路由器和负载均衡器"
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

> ⚠️ `build` / `git` 段需要 pom 里启用 `build-info` 目标（当前 `pom.xml` 未配置），
> 默认的 `/actuator/info` 不会返回这两段；上面是示意输出。当前工程版本为 3.2.2。

### 环境信息

获取环境和配置详情：

```http
GET /actuator/env
```

> ⚠️ `env` **不在默认暴露清单内**（`config/base/monitoring-base.yml:60` 只暴露 health / info /
> metrics / prometheus / jairouter-metrics / error-tracking；prod 见 `application-prod.yml:40`）。
> 需要它时先把 `env` 加进 `management.endpoints.web.exposure.include`。

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

> ⚠️ 上面是示意输出：本项目的配置键全部在 `jairouter.*` 前缀下，
> **不存在 `model-router.*` 键**（全仓 `src/main/resources` 无匹配）。

## 自定义监控端点

### 系统健康状态

获取系统整体健康状态：

```http
GET /api/monitoring/health
```

### 监控配置

获取当前监控配置：

```http
GET /api/monitoring/config
```

### 熔断器统计

获取熔断器统计信息：

```http
GET /api/monitoring/circuit-breaker/stats
```

### 降级状态

获取服务降级状态：

```http
GET /api/monitoring/degradation/status
```

### 错误统计

获取错误统计信息：

```http
GET /api/monitoring/errors/stats
```

### 缓存统计

获取缓存统计信息：

```http
GET /api/monitoring/cache/stats
```

> **注意**: 以上端点为 JAiRouter（当前版本 3.2.2）实际提供的自定义监控端点，逐条对应
> `MonitoringController.java:65,226,240,268,309,390,426`。更多端点请参考 [管理 API](management-api.md) 文档。

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
curl -f http://localhost:8080/actuator/health/tracing || exit 1
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
        expr: rate(jairouter_requests_total{status="500"}[5m]) / rate(jairouter_requests_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "检测到高错误率"
          
      - alert: 熔断器打开
        expr: jairouter_circuit_breaker_state > 0
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

> ⚠️ `loggers` **不在默认暴露清单内**，需先加进
> `management.endpoints.web.exposure.include` 才能访问。

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
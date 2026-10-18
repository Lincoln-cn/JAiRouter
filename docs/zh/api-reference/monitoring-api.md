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

🔑 **这些运维端点需要管理员凭据**：下列 `/actuator/**` 调用走**正常认证链** —— 只有 `/actuator/health`（含
> `liveness` / `readiness` 子路径）、`/actuator/info`、`/actuator/prometheus` 允许匿名（供 k8s 探针与
> Prometheus 抓取），其余都需要 ADMIN 角色。**不带凭据会得到 `401 AUTH_MISSING`。**
> （修复前即使带了 ADMIN JWT 也会 401：认证链被整段排除，与 `hasRole("ADMIN")` 互相抵消，见 issue #340。）
>
> 取 JWT 与调用示例（本机实测通过）：
>
> ```bash
> TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/jwt/login \
>   -H 'Content-Type: application/json' \
>   -d '{"username":"admin","password":"<你的管理员密码>"}' | jq -r .data.token)
> curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/actuator/metrics | jq
> ```

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
---

## 只读监控面：37 个未在前端调用、也未文档化的管理端点

这些端点**没有前端入口**，此前也没有文档（`#454` 的对账结果）。按维护者裁定，它们作为**只读监控 / 运维诊断面保留**，在此补齐说明。

> **授权来源说明**：这些端点**没有方法级 `@PreAuthorize`**，鉴权由 `/api/**` 的 `PermissionAuthorizationManager` 按 `PermissionRuleRegistry` 的 URL 规则统一裁决。下表的权限码就是**当前实际生效**的规则，不是设计意图。

### 动态指标注册（MetricRegistrationController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `DELETE` | `/api/monitoring/metrics/batch` | `monitoring:metrics:write` |
| `GET` | `/api/monitoring/metrics/category/{}` | `monitoring:metrics:read` |
| `POST` | `/api/monitoring/metrics/cleanup` | `monitoring:metrics:write` |
| `GET` | `/api/monitoring/metrics/exists/{}` | `monitoring:metrics:read` |
| `GET` | `/api/monitoring/metrics/list` | `monitoring:metrics:read` |
| `GET` | `/api/monitoring/metrics/metadata` | `monitoring:metrics:read` |
| `GET` | `/api/monitoring/metrics/metadata/{}` | `monitoring:metrics:read` |
| `PUT` | `/api/monitoring/metrics/metadata/{}` | `monitoring:metrics:write` |
| `POST` | `/api/monitoring/metrics/register` | `monitoring:metrics:write` |
| `POST` | `/api/monitoring/metrics/register/batch` | `monitoring:metrics:write` |
| `GET` | `/api/monitoring/metrics/search` | `monitoring:metrics:read` |
| `GET` | `/api/monitoring/metrics/statistics` | `monitoring:metrics:read` |
| `POST` | `/api/monitoring/metrics/validate` | `monitoring:metrics:write` |
| `DELETE` | `/api/monitoring/metrics/{}` | `monitoring:metrics:write` |
| `PUT` | `/api/monitoring/metrics/{}/configuration` | `monitoring:metrics:write` |

### 调用历史（ApiCallHistoryController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `DELETE` | `/api/call-history/cleanup` | `callhistory:view（同码，见 #514）` |
| `GET` | `/api/call-history/count` | `callhistory:view` |
| `GET` | `/api/call-history/errors` | `callhistory:view` |
| `GET` | `/api/call-history/recent` | `callhistory:view` |
| `POST` | `/api/call-history/record` | `**摄取面豁免**（`RbacExemptEndpoints`，#454）` |
| `GET` | `/api/call-history/trace/{}` | `callhistory:view` |

### 路由监控（RoutingMonitorController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `PUT` | `/api/v1/routing-monitor/config` | `lb:config:write` |
| `DELETE` | `/api/v1/routing-monitor/history/{}` | `lb:config:write` |
| `GET` | `/api/v1/routing-monitor/history/{}` | `monitoring:routing:read` |
| `GET` | `/api/v1/routing-monitor/stats/{}` | `monitoring:routing:read` |

### 熔断历史（CircuitBreakerConfigController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `GET` | `/api/config/circuit-breaker/history/instance` | `config:circuitbreaker:read` |
| `GET` | `/api/config/circuit-breaker/history/service-type` | `config:circuitbreaker:read` |
| `GET` | `/api/config/circuit-breaker/history/time-range` | `config:circuitbreaker:read` |

### 适配器模板（AdapterTemplateController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `GET` | `/api/config/adapter/templates/{}` | `config:adapters:read` |
| `POST` | `/api/config/adapter/templates/{}/create` | `config:adapters:write` |

### 异常管理（ExceptionManagementController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `GET` | `/api/exceptions/dashboard` | `monitoring:exceptions:read` |
| `GET` | `/api/exceptions/recent/{}` | `monitoring:exceptions:read` |

### 慢查询分析（SlowQueryAnalysisController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `GET` | `/api/monitoring/slow-queries/stats/{}` | `monitoring:slowquery:read` |
| `GET` | `/api/monitoring/slow-queries/trends` | `monitoring:slowquery:read` |

### 健康状态 SSE（HealthStatusSseController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `GET` | `/api/health-status/stream` | 已认证即可（`/api/health-status/**` → `authenticated()`） |

### 限流监控（RateLimitMonitorController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `GET` | `/api/rate-limiter/status` | `rl:monitoring:read` |

### 状态持久化（StatePersistenceManagementController）

| 方法 | 路径 | 生效的权限码 |
|---|---|---|
| `POST` | `/api/state-persistence/test/save-circuit-breaker/{}` | `config:persistence:write` |

### 两个需要注意的口径

1. **`/api/monitoring/metrics/**` 的写操作已有显式规则** —— 读 `monitoring:metrics:read`、写 `monitoring:metrics:write`（`#454` 补）。此前写操作没有规则、只靠 `RbacUnmatchedPolicy` 的默认 `DENY_ALL`（仅 `ROLE_ADMIN`）兜住，属「碰巧安全」；现在「谁能注册指标」是一条可授的权限，`OPERATOR` 模板按「所有 `:read`/`:write` 码」自动获得它。
2. **`/api/call-history/**` 的读 / 写 / 删共用 `callhistory:view`** —— `POST /record`（写入）与 `DELETE /cleanup`（删除）都被「查看」权限放行，见 `#514`。


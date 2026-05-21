# 管理 API

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 提供完整的管理 API，用于动态配置管理、服务实例管理、监控配置等。所有管理 API 使用 `/api` 前缀。

## 目录

- [服务配置管理](#服务配置管理)
- [实例管理](#实例管理)
- [服务类型管理](#服务类型管理)
- [认证管理](#认证管理)
- [安全管理](#安全管理)
- [监控管理](#监控管理)
- [追踪管理](#追踪管理)
- [负载均衡管理](#负载均衡管理)
- [熔断器与限流](#熔断器与限流)
- [模型统计](#模型统计)
- [令牌使用](#令牌使用)
- [配置版本管理](#配置版本管理)

---

## 服务配置管理

### 基础路径: `/api/services`

管理服务配置，包括负载均衡、限流和熔断器设置。

#### `GET /api/services`
获取所有服务配置。

**响应示例:**
```json
[
  {
    "serviceType": "chat",
    "loadBalancer": "random",
    "rateLimit": {
      "enabled": true,
      "algorithm": "token_bucket",
      "capacity": 100
    },
    "circuitBreaker": {
      "enabled": true,
      "failureThreshold": 5
    }
  }
]
```

#### `GET /api/services/{serviceType}`
获取指定服务类型的配置。

**路径参数:**
- `serviceType` (string): 服务类型 (chat, embedding, rerank, tts, stt, imgGen, imgEdit)

#### `POST /api/services/{serviceType}`
创建或更新服务配置。

**请求体示例:**
```json
{
  "loadBalancer": "round_robin",
  "rateLimit": {
    "enabled": true,
    "algorithm": "token_bucket",
    "capacity": 50
  },
  "circuitBreaker": {
    "enabled": true,
    "failureThreshold": 3
  }
}
```

#### `DELETE /api/services/{serviceType}`
删除服务配置。

---

## 实例管理

### 基础路径: `/api/instances`

管理服务实例（模型端点）。

#### `GET /api/instances`
获取所有实例。

#### `GET /api/instances/service/{serviceConfigId}`
获取指定服务配置的所有实例。

**路径参数:**
- `serviceConfigId` (long): 服务配置 ID

**响应示例:**
```json
[
  {
    "id": 1,
    "name": "qwen2:7b",
    "baseUrl": "http://localhost:8000",
    "apiKey": "sk-xxx",
    "weight": 1,
    "enabled": true,
    "adapter": "ollama",
    "healthStatus": "healthy"
  }
]
```

#### `GET /api/instances/{id}`
根据 ID 获取指定实例。

#### `POST /api/instances/service/{serviceConfigId}`
向服务配置添加新实例。

**请求体示例:**
```json
{
  "name": "qwen2:7b",
  "baseUrl": "http://localhost:8000",
  "apiKey": "sk-xxx",
  "weight": 1,
  "enabled": true,
  "adapter": "ollama"
}
```

#### `PUT /api/instances/{id}`
更新实例。

#### `DELETE /api/instances/{id}`
删除实例。

#### `POST /api/instances/{id}/health`
触发实例健康检查。

---

## 服务类型管理

### 基础路径: `/api/config/type`

管理服务类型及其模型。

#### `GET /api/config/type`
获取所有服务类型配置。

#### `GET /api/config/type/services`
获取所有可用的服务类型。

**响应示例:**
```json
["chat", "embedding", "rerank", "tts", "stt", "imgGen", "imgEdit"]
```

#### `GET /api/config/type/services/{serviceType}`
获取指定服务类型的配置。

#### `POST /api/config/type/services/{serviceType}`
创建新的服务类型配置。

#### `PUT /api/config/type/services/{serviceType}`
更新服务类型配置。

#### `DELETE /api/config/type/services/{serviceType}`
删除服务类型配置。

#### `GET /api/config/type/{serviceType}/models`
获取服务类型下的所有模型。

#### `POST /api/config/type/reset`
重置所有配置为默认值。

---

## 认证管理

### JWT 令牌管理

#### 基础路径: `/api/auth/jwt`

#### `POST /api/auth/jwt/login`
登录并获取 JWT 令牌。

**请求体示例:**
```json
{
  "username": "admin",
  "password": "your-password"
}
```

**响应示例:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 3600
}
```

#### `POST /api/auth/jwt/refresh`
使用刷新令牌刷新访问令牌。

#### `POST /api/auth/jwt/revoke`
撤销当前令牌。

#### `POST /api/auth/jwt/revoke/batch`
批量撤销多个令牌。

#### `POST /api/auth/jwt/validate`
验证 JWT 令牌。

#### `GET /api/auth/jwt/blacklist/stats`
获取 JWT 黑名单统计。

#### `GET /api/auth/jwt/tokens`
分页获取所有 JWT 令牌。

#### `GET /api/auth/jwt/tokens/{tokenId}`
获取指定令牌的详情。

#### `POST /api/auth/jwt/cleanup`
触发过期令牌清理。

#### `GET /api/auth/jwt/cleanup/stats`
获取清理统计。

### API 密钥管理

#### 基础路径: `/api/auth/api-keys`

#### `GET /api/auth/api-keys`
列出所有 API 密钥。

#### `GET /api/auth/api-keys/{keyId}`
获取指定 API 密钥的详情。

#### `POST /api/auth/api-keys`
创建新的 API 密钥。

**请求体示例:**
```json
{
  "name": "my-api-key",
  "permissions": ["read", "write"],
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

#### `PUT /api/auth/api-keys/{keyId}`
更新 API 密钥。

#### `DELETE /api/auth/api-keys/{keyId}`
删除 API 密钥。

#### `PATCH /api/auth/api-keys/{keyId}/disable`
禁用 API 密钥。

#### `PATCH /api/auth/api-keys/{keyId}/enable`
启用 API 密钥。

#### `POST /api/auth/api-keys/{keyId}/reset`
重置 API 密钥（生成新密钥值）。

#### `POST /api/auth/api-keys/{keyId}/rotate`
轮换 API 密钥。

#### `GET /api/auth/api-keys/export`
导出 API 密钥。

#### `POST /api/auth/api-keys/import`
导入 API 密钥。

### JWT 账户管理

#### 基础路径: `/api/security/jwt/accounts`

#### `GET /api/security/jwt/accounts`
列出所有 JWT 账户。

#### `GET /api/security/jwt/accounts/{username}`
获取指定账户的详情。

#### `POST /api/security/jwt/accounts`
创建新的 JWT 账户。

#### `PUT /api/security/jwt/accounts/{username}`
更新 JWT 账户。

#### `DELETE /api/security/jwt/accounts/{username}`
删除 JWT 账户。

#### `POST /api/security/jwt/accounts/{username}/verify`
验证账户凭据。

#### `PATCH /api/security/jwt/accounts/{username}/status`
更新账户状态。

---

## 安全管理

### 安全审计

#### 基础路径: `/api/security/audit`

#### `GET /api/security/audit/logs`
分页获取审计日志。

#### `POST /api/security/audit/logs/query`
带过滤条件查询审计日志。

#### `GET /api/security/audit/statistics`
获取审计统计。

#### `DELETE /api/security/audit/logs/cleanup`
清理旧审计日志。

#### `GET /api/security/audit/alerts/check`
检查安全告警。

#### `GET /api/security/audit/alerts/statistics`
获取告警统计。

#### `POST /api/security/audit/alerts/reset`
重置告警状态。

### 扩展安全审计

#### 基础路径: `/api/security/audit/extended`

#### `GET /api/security/audit/extended/jwt-tokens`
获取 JWT 令牌审计记录。

#### `GET /api/security/audit/extended/api-keys`
获取 API 密钥审计记录。

#### `GET /api/security/audit/extended/security-events`
获取安全事件。

#### `POST /api/security/audit/extended/query`
查询扩展审计数据。

#### `GET /api/security/audit/extended/reports/security`
获取安全报告。

#### `GET /api/security/audit/extended/users/{userId}/events`
获取指定用户的事件。

#### `GET /api/security/audit/extended/ip-addresses/{ipAddress}/events`
获取指定 IP 地址的事件。

#### `POST /api/security/audit/extended/events/batch`
批量查询事件。

#### `POST /api/security/audit/extended/test-data/generate`
生成测试审计数据。

#### `GET /api/security/audit/extended/statistics/extended`
获取扩展统计。

### 安全黑名单

#### 基础路径: `/api/security/blacklist`

#### `GET /api/security/blacklist/list`
获取黑名单条目。

#### `GET /api/security/blacklist/stats`
获取黑名单统计。

#### `GET /api/security/blacklist/{id}`
获取指定黑名单条目。

#### `POST /api/security/blacklist/add`
添加条目到黑名单。

#### `POST /api/security/blacklist/batch-add`
批量添加条目到黑名单。

#### `DELETE /api/security/blacklist/{id}`
从黑名单移除条目。

#### `GET /api/security/blacklist/check`
检查条目是否在黑名单中。

#### `POST /api/security/blacklist/cleanup`
清理过期黑名单条目。

---

## 监控管理

### 基础路径: `/api/monitoring`

#### `GET /api/monitoring/config`
获取当前监控配置。

**响应示例:**
```json
{
  "enabled": true,
  "prefix": "jairouter",
  "collectionInterval": "PT30S",
  "enabledCategories": ["request", "system", "custom"],
  "sampling": {
    "enabled": true,
    "rate": 0.1
  }
}
```

#### `PUT /api/monitoring/config/enabled`
启用或禁用监控。

#### `PUT /api/monitoring/config/prefix`
更新指标前缀。

#### `PUT /api/monitoring/config/collection-interval`
更新采集间隔。

#### `PUT /api/monitoring/config/categories`
更新启用的类别。

#### `PUT /api/monitoring/config/custom-tags`
更新自定义标签。

#### `GET /api/monitoring/config/snapshot`
获取配置快照。

#### `GET /api/monitoring/health`
获取监控系统健康状态。

#### `GET /api/monitoring/errors/stats`
获取错误统计。

#### `POST /api/monitoring/errors/reset`
重置错误状态。

#### `GET /api/monitoring/degradation/status`
获取降级状态。

#### `POST /api/monitoring/degradation/level`
设置降级级别。

**请求体示例:**
```json
{
  "level": "PARTIAL"
}
```

**可用级别:** `NORMAL`, `PARTIAL`, `MINIMAL`, `DISABLED`

#### `POST /api/monitoring/degradation/auto-mode`
启用/禁用自动降级模式。

#### `POST /api/monitoring/degradation/force-recovery`
强制恢复到正常模式。

#### `GET /api/monitoring/cache/stats`
获取缓存统计。

#### `POST /api/monitoring/cache/clear`
清空缓存。

#### `GET /api/monitoring/circuit-breaker/stats`
获取熔断器统计。

#### `POST /api/monitoring/circuit-breaker/force-open`
强制打开熔断器。

#### `POST /api/monitoring/circuit-breaker/force-close`
强制关闭熔断器。

---

## 追踪管理

### 追踪执行器

#### 基础路径: `/api/tracing/actuator`

#### `GET /api/tracing/actuator/status`
获取追踪状态。

#### `GET /api/tracing/actuator/health`
获取追踪健康状态。

#### `GET /api/tracing/actuator/config`
获取追踪配置。

#### `PUT /api/tracing/actuator/config`
更新追踪配置。

#### `POST /api/tracing/actuator/sampling/refresh`
刷新采样配置。

#### `GET /api/tracing/actuator/stats`
获取追踪统计。

#### `POST /api/tracing/actuator/enable`
启用追踪。

#### `POST /api/tracing/actuator/disable`
禁用追踪。

#### `GET /api/tracing/actuator/export`
导出追踪数据。

#### `POST /api/tracing/actuator/clear-cache`
清空追踪缓存。

### 追踪查询

#### 基础路径: `/api/tracing/query`

#### `GET /api/tracing/query/trace/{traceId}`
根据 ID 获取追踪。

#### `GET /api/tracing/query/search`
搜索追踪。

#### `GET /api/tracing/query/recent`
获取最近追踪。

#### `GET /api/tracing/query/services`
获取被追踪的服务。

#### `GET /api/tracing/query/statistics`
获取追踪统计。

#### `POST /api/tracing/query/export`
导出追踪数据。

#### `POST /api/tracing/query/cleanup`
清理旧追踪。

#### `GET /api/tracing/query/operations`
获取操作列表。

#### `GET /api/tracing/query/health`
获取查询健康状态。

#### `GET /api/tracing/query/performance/stats`
获取性能统计。

#### `GET /api/tracing/query/performance/latency`
获取延迟指标。

#### `GET /api/tracing/query/performance/errors`
获取错误指标。

#### `GET /api/tracing/query/performance/throughput`
获取吞吐量指标。

### 追踪性能

#### 基础路径: `/api/tracing/performance`

#### `GET /api/tracing/performance/stats`
获取性能统计。

#### `GET /api/tracing/performance/processing-stats`
获取处理统计。

#### `GET /api/tracing/performance/memory-stats`
获取内存统计。

#### `GET /api/tracing/performance/health`
获取性能健康状态。

#### `GET /api/tracing/performance/bottlenecks`
识别瓶颈。

#### `GET /api/tracing/performance/suggestions`
获取优化建议。

#### `GET /api/tracing/performance/report`
获取性能报告。

#### `POST /api/tracing/performance/optimize`
触发优化。

#### `POST /api/tracing/performance/tuning`
应用性能调优。

#### `POST /api/tracing/performance/memory/gc`
触发垃圾回收。

#### `POST /api/tracing/performance/memory/check`
检查内存状态。

#### `POST /api/tracing/performance/processing/flush`
刷新处理缓冲区。

#### `GET /api/tracing/performance/metrics/dashboard`
获取仪表板指标。

#### `GET /api/tracing/performance/alerts/active`
获取活动告警。

### 追踪安全

#### 基础路径: `/api/config/tracing/security`

#### `GET /api/config/tracing/security/sanitization/sensitive-fields`
获取敏感字段脱敏配置。

#### `POST /api/config/tracing/security/sanitization/sensitive-fields`
添加敏感字段。

#### `DELETE /api/config/tracing/security/sanitization/sensitive-fields`
移除敏感字段。

#### `GET /api/config/tracing/security/access/history/{username}`
获取用户访问历史。

#### `DELETE /api/config/tracing/security/access/cache/{username}`
清除用户访问缓存。

#### `DELETE /api/config/tracing/security/access/cache`
清除所有访问缓存。

#### `POST /api/config/tracing/security/encryption/rotate-key/{traceId}`
轮换追踪的加密密钥。

#### `POST /api/config/tracing/security/encryption/cleanup`
清理加密数据。

#### `DELETE /api/config/tracing/security/encryption/data/{traceId}`
删除追踪的加密数据。

#### `GET /api/config/tracing/security/overview`
获取安全概览。

---

## 负载均衡管理

### 基础路径: `/api/loadbalancer`

#### `GET /api/loadbalancer/status`
获取所有服务的负载均衡状态。

#### `GET /api/loadbalancer/status/{serviceType}`
获取指定服务的负载均衡状态。

#### `GET /api/loadbalancer/config/global`
获取全局负载均衡配置。

#### `GET /api/loadbalancer/config/{serviceType}`
获取服务的负载均衡配置。

#### `PUT /api/loadbalancer/config/{serviceType}`
更新负载均衡配置。

**请求体示例:**
```json
{
  "strategy": "round_robin",
  "weights": {
    "instance1": 2,
    "instance2": 1
  }
}
```

#### `GET /api/loadbalancer/strategies`
获取可用的负载均衡策略。

**响应示例:**
```json
["random", "round_robin", "weighted", "least_connections"]
```

#### `GET /api/loadbalancer/stats`
获取负载均衡统计。

---

## 熔断器与限流

### 熔断器

#### 基础路径: `/api/services/{serviceType}/circuitbreaker`

#### `GET /api/services/{serviceType}/circuitbreaker`
获取服务的熔断器配置。

**响应示例:**
```json
{
  "enabled": true,
  "failureThreshold": 5,
  "successThreshold": 3,
  "timeout": 30000,
  "state": "CLOSED"
}
```

#### `PUT /api/services/{serviceType}/circuitbreaker`
更新熔断器配置。

### 限流

#### 基础路径: `/api/services/{serviceType}/ratelimit`

#### `GET /api/services/{serviceType}/ratelimit`
获取服务的限流配置。

**响应示例:**
```json
{
  "enabled": true,
  "algorithm": "token_bucket",
  "capacity": 100,
  "refillRate": 10
}
```

#### `PUT /api/services/{serviceType}/ratelimit`
更新限流配置。

---

## 模型统计

### 基础路径: `/api/model-stats`

#### `GET /api/model-stats/summary`
获取模型统计摘要。

#### `GET /api/model-stats/models`
获取所有模型统计。

#### `GET /api/model-stats/service-types/{serviceType}`
获取服务类型的统计。

#### `GET /api/model-stats/models/{serviceType}/{modelName}`
获取指定模型的统计。

#### `GET /api/model-stats/top/active`
获取最活跃的模型。

#### `GET /api/model-stats/unhealthy`
获取不健康的模型。

#### `GET /api/model-stats/grouped-by-service-type`
获取按服务类型分组的统计。

#### `GET /api/model-stats/trend`
获取使用趋势。

#### `POST /api/model-stats/refresh`
刷新统计。

#### `DELETE /api/model-stats/clear`
清除所有统计。

---

## 令牌使用

### 基础路径: `/api/token-usage`

#### `POST /api/token-usage/record`
记录令牌使用。

**请求体示例:**
```json
{
  "modelName": "qwen2:7b",
  "serviceType": "chat",
  "inputTokens": 100,
  "outputTokens": 50,
  "requestId": "req-123"
}
```

#### `POST /api/token-usage/record/batch`
批量记录令牌使用。

#### `GET /api/token-usage/statistics`
获取令牌使用统计。

#### `GET /api/token-usage/recent`
获取最近的令牌使用。

#### `GET /api/token-usage/recent/{modelName}`
获取指定模型的最近使用。

#### `GET /api/token-usage/top/models`
获取令牌使用量最高的模型。

#### `GET /api/token-usage/top/services`
获取令牌使用量最高的服务。

#### `GET /api/token-usage/dashboard`
获取令牌使用仪表板数据。

#### `DELETE /api/token-usage/cleanup`
清理旧使用记录。

---

## 配置版本管理

### 基础路径: `/api/config/version`

#### `GET /api/config/version`
获取所有配置版本。

#### `GET /api/config/version/{version}`
获取指定版本。

#### `DELETE /api/config/version/{version}`
删除版本。

#### `GET /api/config/version/current`
获取当前版本。

#### `POST /api/config/version/apply/{version}`
应用指定版本。

#### `GET /api/config/version/info`
获取版本信息。

#### `GET /api/config/version/compare/{sourceVersion}/{targetVersion}`
比较两个版本。

#### `GET /api/config/version/compare/{version}`
与当前版本比较。

---

## 配置验证

### 基础路径: `/api/config`

#### `GET /api/config/sources`
获取配置源。

#### `GET /api/config/validation-rules`
获取验证规则。

#### `GET /api/config/environment-variables`
获取环境变量配置。

---

## 适配器配置

### 基础路径: `/api/config/adapter`

#### `GET /api/config/adapter`
获取适配器配置和能力。

---

## 错误响应

所有 API 端点遵循一致的错误响应格式:

```json
{
  "success": false,
  "message": "错误描述",
  "error": {
    "code": "ERROR_CODE",
    "details": "详细错误信息"
  }
}
```

### 常见错误码

| 错误码 | 描述 |
|--------|------|
| `NOT_FOUND` | 资源未找到 |
| `VALIDATION_ERROR` | 请求数据无效 |
| `UNAUTHORIZED` | 需要认证 |
| `FORBIDDEN` | 权限不足 |
| `CONFLICT` | 资源冲突 |
| `INTERNAL_ERROR` | 服务器错误 |

---

## 认证

所有管理 API 需要认证。使用以下方式之一:

### JWT 令牌
```
请求头: Jairouter_Token: <your-jwt-token>
```

### API 密钥
```
请求头: X-API-Key: <your-api-key>
```

---

*最后更新: 2026-05-21*

# Management API

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter provides a complete set of management APIs for dynamic configuration management, service instance management, monitoring configuration, and more. All management APIs use the `/api` prefix.

## Table of Contents

- [Service Configuration Management](#service-configuration-management)
- [Instance Management](#instance-management)
- [Service Type Management](#service-type-management)
- [Authentication Management](#authentication-management)
- [Security Management](#security-management)
- [Monitoring Management](#monitoring-management)
- [Tracing Management](#tracing-management)
- [Load Balancer Management](#load-balancer-management)
- [Circuit Breaker & Rate Limit](#circuit-breaker--rate-limit)
- [Model Statistics](#model-statistics)
- [Token Usage](#token-usage)
- [Configuration Version Management](#configuration-version-management)

---

## Service Configuration Management

### Base Path: `/api/services`

Manage service configurations including load balancer, rate limit, and circuit breaker settings.

#### `GET /api/services`
Get all service configurations.

**Response Example:**
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
Get configuration for a specific service type.

**Path Parameters:**
- `serviceType` (string): Service type (chat, embedding, rerank, tts, stt, imgGen, imgEdit)

#### `POST /api/services/{serviceType}`
Create or update service configuration.

**Request Body Example:**
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
Delete a service configuration.

---

## Instance Management

### Base Path: `/api/instances`

Manage service instances (model endpoints).

#### `GET /api/instances`
Get all instances.

#### `GET /api/instances/service/{serviceConfigId}`
Get all instances for a specific service configuration.

**Path Parameters:**
- `serviceConfigId` (long): Service configuration ID

**Response Example:**
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
Get a specific instance by ID.

#### `POST /api/instances/service/{serviceConfigId}`
Add a new instance to a service configuration.

**Request Body Example:**
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
Update an instance.

#### `DELETE /api/instances/{id}`
Delete an instance.

#### `POST /api/instances/{id}/health`
Trigger health check for an instance.

---

## Service Type Management

### Base Path: `/api/config/type`

Manage service types and their models.

#### `GET /api/config/type`
Get all service type configurations.

#### `GET /api/config/type/services`
Get all available service types.

**Response Example:**
```json
["chat", "embedding", "rerank", "tts", "stt", "imgGen", "imgEdit"]
```

#### `GET /api/config/type/services/{serviceType}`
Get configuration for a specific service type.

#### `POST /api/config/type/services/{serviceType}`
Create a new service type configuration.

#### `PUT /api/config/type/services/{serviceType}`
Update a service type configuration.

#### `DELETE /api/config/type/services/{serviceType}`
Delete a service type configuration.

#### `GET /api/config/type/{serviceType}/models`
Get all models under a service type.

#### `POST /api/config/type/reset`
Reset all configurations to default values.

---

## Authentication Management

### JWT Token Management

#### Base Path: `/api/auth/jwt`

#### `POST /api/auth/jwt/login`
Login and obtain JWT token.

**Request Body Example:**
```json
{
  "username": "admin",
  "password": "your-password"
}
```

**Response Example:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 3600
}
```

#### `POST /api/auth/jwt/refresh`
Refresh access token using refresh token.

#### `POST /api/auth/jwt/revoke`
Revoke current token.

#### `POST /api/auth/jwt/revoke/batch`
Revoke multiple tokens in batch.

#### `POST /api/auth/jwt/validate`
Validate a JWT token.

#### `GET /api/auth/jwt/blacklist/stats`
Get JWT blacklist statistics.

#### `GET /api/auth/jwt/tokens`
Get all JWT tokens with pagination.

#### `GET /api/auth/jwt/tokens/{tokenId}`
Get details of a specific token.

#### `POST /api/auth/jwt/cleanup`
Trigger cleanup of expired tokens.

#### `GET /api/auth/jwt/cleanup/stats`
Get cleanup statistics.

### API Key Management

#### Base Path: `/api/auth/api-keys`

#### `GET /api/auth/api-keys`
List all API keys.

#### `GET /api/auth/api-keys/{keyId}`
Get details of a specific API key.

#### `POST /api/auth/api-keys`
Create a new API key.

**Request Body Example:**
```json
{
  "name": "my-api-key",
  "permissions": ["read", "write"],
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

#### `PUT /api/auth/api-keys/{keyId}`
Update an API key.

#### `DELETE /api/auth/api-keys/{keyId}`
Delete an API key.

#### `PATCH /api/auth/api-keys/{keyId}/disable`
Disable an API key.

#### `PATCH /api/auth/api-keys/{keyId}/enable`
Enable an API key.

#### `POST /api/auth/api-keys/{keyId}/reset`
Reset an API key (generate new key value).

#### `POST /api/auth/api-keys/{keyId}/rotate`
Rotate an API key.

#### `GET /api/auth/api-keys/export`
Export API keys.

#### `POST /api/auth/api-keys/import`
Import API keys.

### JWT Account Management

#### Base Path: `/api/security/jwt/accounts`

#### `GET /api/security/jwt/accounts`
List all JWT accounts.

#### `GET /api/security/jwt/accounts/{username}`
Get details of a specific account.

#### `POST /api/security/jwt/accounts`
Create a new JWT account.

#### `PUT /api/security/jwt/accounts/{username}`
Update a JWT account.

#### `DELETE /api/security/jwt/accounts/{username}`
Delete a JWT account.

#### `POST /api/security/jwt/accounts/{username}/verify`
Verify account credentials.

#### `PATCH /api/security/jwt/accounts/{username}/status`
Update account status.

---

## Security Management

### Security Audit

#### Base Path: `/api/security/audit`

#### `GET /api/security/audit/logs`
Get audit logs with pagination.

#### `POST /api/security/audit/logs/query`
Query audit logs with filters.

#### `GET /api/security/audit/statistics`
Get audit statistics.

#### `DELETE /api/security/audit/logs/cleanup`
Cleanup old audit logs.

#### `GET /api/security/audit/alerts/check`
Check for security alerts.

#### `GET /api/security/audit/alerts/statistics`
Get alert statistics.

#### `POST /api/security/audit/alerts/reset`
Reset alert status.

### Extended Security Audit

#### Base Path: `/api/security/audit/extended`

#### `GET /api/security/audit/extended/jwt-tokens`
Get JWT token audit records.

#### `GET /api/security/audit/extended/api-keys`
Get API key audit records.

#### `GET /api/security/audit/extended/security-events`
Get security events.

#### `POST /api/security/audit/extended/query`
Query extended audit data.

#### `GET /api/security/audit/extended/reports/security`
Get security reports.

#### `GET /api/security/audit/extended/users/{userId}/events`
Get events for a specific user.

#### `GET /api/security/audit/extended/ip-addresses/{ipAddress}/events`
Get events for a specific IP address.

#### `POST /api/security/audit/extended/events/batch`
Batch query events.

#### `POST /api/security/audit/extended/test-data/generate`
Generate test audit data.

#### `GET /api/security/audit/extended/statistics/extended`
Get extended statistics.

### Security Blacklist

#### Base Path: `/api/security/blacklist`

#### `GET /api/security/blacklist/list`
Get blacklist entries.

#### `GET /api/security/blacklist/stats`
Get blacklist statistics.

#### `GET /api/security/blacklist/{id}`
Get a specific blacklist entry.

#### `POST /api/security/blacklist/add`
Add entry to blacklist.

#### `POST /api/security/blacklist/batch-add`
Batch add entries to blacklist.

#### `DELETE /api/security/blacklist/{id}`
Remove entry from blacklist.

#### `GET /api/security/blacklist/check`
Check if an entry is blacklisted.

#### `POST /api/security/blacklist/cleanup`
Cleanup expired blacklist entries.

---

## Monitoring Management

### Base Path: `/api/monitoring`

#### `GET /api/monitoring/config`
Get current monitoring configuration.

**Response Example:**
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
Enable or disable monitoring.

#### `PUT /api/monitoring/config/prefix`
Update metric prefix.

#### `PUT /api/monitoring/config/collection-interval`
Update collection interval.

#### `PUT /api/monitoring/config/categories`
Update enabled categories.

#### `PUT /api/monitoring/config/custom-tags`
Update custom tags.

#### `GET /api/monitoring/config/snapshot`
Get configuration snapshot.

#### `GET /api/monitoring/health`
Get monitoring system health.

#### `GET /api/monitoring/errors/stats`
Get error statistics.

#### `POST /api/monitoring/errors/reset`
Reset error status.

#### `GET /api/monitoring/degradation/status`
Get degradation status.

#### `POST /api/monitoring/degradation/level`
Set degradation level.

**Request Body Example:**
```json
{
  "level": "PARTIAL"
}
```

**Available Levels:** `NORMAL`, `PARTIAL`, `MINIMAL`, `DISABLED`

#### `POST /api/monitoring/degradation/auto-mode`
Enable/disable auto degradation mode.

#### `POST /api/monitoring/degradation/force-recovery`
Force recovery to normal mode.

#### `GET /api/monitoring/cache/stats`
Get cache statistics.

#### `POST /api/monitoring/cache/clear`
Clear cache.

#### `GET /api/monitoring/circuit-breaker/stats`
Get circuit breaker statistics.

#### `POST /api/monitoring/circuit-breaker/force-open`
Force open circuit breaker.

#### `POST /api/monitoring/circuit-breaker/force-close`
Force close circuit breaker.

---

## Tracing Management

### Tracing Actuator

#### Base Path: `/api/tracing/actuator`

#### `GET /api/tracing/actuator/status`
Get tracing status.

#### `GET /api/tracing/actuator/health`
Get tracing health.

#### `GET /api/tracing/actuator/config`
Get tracing configuration.

#### `PUT /api/tracing/actuator/config`
Update tracing configuration.

#### `POST /api/tracing/actuator/sampling/refresh`
Refresh sampling configuration.

#### `GET /api/tracing/actuator/stats`
Get tracing statistics.

#### `POST /api/tracing/actuator/enable`
Enable tracing.

#### `POST /api/tracing/actuator/disable`
Disable tracing.

#### `GET /api/tracing/actuator/export`
Export tracing data.

#### `POST /api/tracing/actuator/clear-cache`
Clear tracing cache.

### Tracing Query

#### Base Path: `/api/tracing/query`

#### `GET /api/tracing/query/trace/{traceId}`
Get trace by ID.

#### `GET /api/tracing/query/search`
Search traces.

#### `GET /api/tracing/query/recent`
Get recent traces.

#### `GET /api/tracing/query/services`
Get traced services.

#### `GET /api/tracing/query/statistics`
Get tracing statistics.

#### `POST /api/tracing/query/export`
Export trace data.

#### `POST /api/tracing/query/cleanup`
Cleanup old traces.

#### `GET /api/tracing/query/operations`
Get operation list.

#### `GET /api/tracing/query/health`
Get query health.

#### `GET /api/tracing/query/performance/stats`
Get performance statistics.

#### `GET /api/tracing/query/performance/latency`
Get latency metrics.

#### `GET /api/tracing/query/performance/errors`
Get error metrics.

#### `GET /api/tracing/query/performance/throughput`
Get throughput metrics.

### Tracing Performance

#### Base Path: `/api/tracing/performance`

#### `GET /api/tracing/performance/stats`
Get performance stats.

#### `GET /api/tracing/performance/processing-stats`
Get processing statistics.

#### `GET /api/tracing/performance/memory-stats`
Get memory statistics.

#### `GET /api/tracing/performance/health`
Get performance health.

#### `GET /api/tracing/performance/bottlenecks`
Identify bottlenecks.

#### `GET /api/tracing/performance/suggestions`
Get optimization suggestions.

#### `GET /api/tracing/performance/report`
Get performance report.

#### `POST /api/tracing/performance/optimize`
Trigger optimization.

#### `POST /api/tracing/performance/tuning`
Apply performance tuning.

#### `POST /api/tracing/performance/memory/gc`
Trigger garbage collection.

#### `POST /api/tracing/performance/memory/check`
Check memory status.

#### `POST /api/tracing/performance/processing/flush`
Flush processing buffers.

#### `GET /api/tracing/performance/metrics/dashboard`
Get dashboard metrics.

#### `GET /api/tracing/performance/alerts/active`
Get active alerts.

### Tracing Security

#### Base Path: `/api/config/tracing/security`

#### `GET /api/config/tracing/security/sanitization/sensitive-fields`
Get sensitive fields for sanitization.

#### `POST /api/config/tracing/security/sanitization/sensitive-fields`
Add sensitive field.

#### `DELETE /api/config/tracing/security/sanitization/sensitive-fields`
Remove sensitive field.

#### `GET /api/config/tracing/security/access/history/{username}`
Get access history for a user.

#### `DELETE /api/config/tracing/security/access/cache/{username}`
Clear access cache for a user.

#### `DELETE /api/config/tracing/security/access/cache`
Clear all access cache.

#### `POST /api/config/tracing/security/encryption/rotate-key/{traceId}`
Rotate encryption key for a trace.

#### `POST /api/config/tracing/security/encryption/cleanup`
Cleanup encryption data.

#### `DELETE /api/config/tracing/security/encryption/data/{traceId}`
Delete encryption data for a trace.

#### `GET /api/config/tracing/security/overview`
Get security overview.

---

## Load Balancer Management

### Base Path: `/api/loadbalancer`

#### `GET /api/loadbalancer/status`
Get load balancer status for all services.

#### `GET /api/loadbalancer/status/{serviceType}`
Get load balancer status for a specific service.

#### `GET /api/loadbalancer/config/global`
Get global load balancer configuration.

#### `GET /api/loadbalancer/config/{serviceType}`
Get load balancer configuration for a service.

#### `PUT /api/loadbalancer/config/{serviceType}`
Update load balancer configuration.

**Request Body Example:**
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
Get available load balancing strategies.

**Response Example:**
```json
["random", "round_robin", "weighted", "least_connections"]
```

#### `GET /api/loadbalancer/stats`
Get load balancer statistics.

---

## Circuit Breaker & Rate Limit

### Circuit Breaker

#### Base Path: `/api/services/{serviceType}/circuitbreaker`

#### `GET /api/services/{serviceType}/circuitbreaker`
Get circuit breaker configuration for a service.

**Response Example:**
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
Update circuit breaker configuration.

### Rate Limit

#### Base Path: `/api/services/{serviceType}/ratelimit`

#### `GET /api/services/{serviceType}/ratelimit`
Get rate limit configuration for a service.

**Response Example:**
```json
{
  "enabled": true,
  "algorithm": "token_bucket",
  "capacity": 100,
  "refillRate": 10
}
```

#### `PUT /api/services/{serviceType}/ratelimit`
Update rate limit configuration.

---

## Model Statistics

### Base Path: `/api/model-stats`

#### `GET /api/model-stats/summary`
Get model statistics summary.

#### `GET /api/model-stats/models`
Get all model statistics.

#### `GET /api/model-stats/service-types/{serviceType}`
Get statistics for a service type.

#### `GET /api/model-stats/models/{serviceType}/{modelName}`
Get statistics for a specific model.

#### `GET /api/model-stats/top/active`
Get top active models.

#### `GET /api/model-stats/unhealthy`
Get unhealthy models.

#### `GET /api/model-stats/grouped-by-service-type`
Get statistics grouped by service type.

#### `GET /api/model-stats/trend`
Get usage trend.

#### `POST /api/model-stats/refresh`
Refresh statistics.

#### `DELETE /api/model-stats/clear`
Clear all statistics.

---

## Token Usage

### Base Path: `/api/token-usage`

#### `POST /api/token-usage/record`
Record token usage.

**Request Body Example:**
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
Batch record token usage.

#### `GET /api/token-usage/statistics`
Get token usage statistics.

#### `GET /api/token-usage/recent`
Get recent token usage.

#### `GET /api/token-usage/recent/{modelName}`
Get recent usage for a model.

#### `GET /api/token-usage/top/models`
Get top models by token usage.

#### `GET /api/token-usage/top/services`
Get top services by token usage.

#### `GET /api/token-usage/dashboard`
Get token usage dashboard data.

#### `DELETE /api/token-usage/cleanup`
Cleanup old usage records.

---

## Configuration Version Management

### Base Path: `/api/config/version`

#### `GET /api/config/version`
Get all configuration versions.

#### `GET /api/config/version/{version}`
Get a specific version.

#### `DELETE /api/config/version/{version}`
Delete a version.

#### `GET /api/config/version/current`
Get current version.

#### `POST /api/config/version/apply/{version}`
Apply a specific version.

#### `GET /api/config/version/info`
Get version information.

#### `GET /api/config/version/compare/{sourceVersion}/{targetVersion}`
Compare two versions.

#### `GET /api/config/version/compare/{version}`
Compare a version with current.

---

## Configuration Validation

### Base Path: `/api/config`

#### `GET /api/config/sources`
Get configuration sources.

#### `GET /api/config/validation-rules`
Get validation rules.

#### `GET /api/config/environment-variables`
Get environment variable configuration.

---

## Adapter Configuration

### Base Path: `/api/config/adapter`

#### `GET /api/config/adapter`
Get adapter configuration and capabilities.

---

## Instance Configs (Legacy)

### Base Path: `/api/instance-configs`

#### `GET /api/instance-configs/service/{serviceConfigId}`
Get instance configs for a service.

#### `POST /api/instance-configs/service/{serviceConfigId}`
Create instance config.

#### `PUT /api/instance-configs/{id}`
Update instance config.

#### `DELETE /api/instance-configs/{id}`
Delete instance config.

---

## Error Responses

All API endpoints follow a consistent error response format:

```json
{
  "success": false,
  "message": "Error description",
  "error": {
    "code": "ERROR_CODE",
    "details": "Detailed error information"
  }
}
```

### Common Error Codes

| Code | Description |
|------|-------------|
| `NOT_FOUND` | Resource not found |
| `VALIDATION_ERROR` | Invalid request data |
| `UNAUTHORIZED` | Authentication required |
| `FORBIDDEN` | Permission denied |
| `CONFLICT` | Resource conflict |
| `INTERNAL_ERROR` | Server error |

---

## Authentication

All management APIs require authentication. Use one of the following methods:

### JWT Token
```
Header: Jairouter_Token: <your-jwt-token>
```

### API Key
```
Header: X-API-Key: <your-api-key>
```

---

*Last updated: 2026-05-21*

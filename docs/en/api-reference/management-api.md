# Management API

<!-- 版本信息 -->
> **Doc Version**: 2.0.0
> **Last Updated**: 2026-05-21
> **Git Commit**: 61384b4a
> **Author**: Lincoln
<!-- /版本信息 -->

JAiRouter provides a complete set of management APIs for dynamic configuration management, service instance management, monitoring configuration, and more. All management APIs use the `/api` prefix.

## Table of Contents

- [Service Type Management](#service-type-management)
- [Authentication Management](#authentication-management)
- [Security Management](#security-management)
- [Monitoring Management](#monitoring-management)
- [Tracing Management](#tracing-management)
- [Load Balancer Management](#load-balancer-management)
- [Circuit Breaker & Rate Limit](#circuit-breaker-rate-limit)
- [Model Statistics](#model-statistics)
- [Token Usage](#token-usage)
- [Configuration Version Management](#configuration-version-management)
- [Quota Management](#quota-management)
- [API Key Quota Ops](#apikey-quota-ops)
- [PII / Sanitization Management](#sanitization-mgmt)

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

#### `GET /api/monitoring/metrics-pipeline/circuit-breaker/stats`
Get statistics for the **metrics pipeline** circuit breaker (`MetricsCircuitBreaker`).

> The legacy path `GET /api/monitoring/circuit-breaker/stats` is a compatibility alias.

#### `POST /api/monitoring/metrics-pipeline/circuit-breaker/force-open`
Force open the **metrics pipeline** circuit breaker.

> Scope: this only affects the monitoring metrics processing pipeline. It does **not** affect
> the per-instance routing circuit breaker (`CircuitBreakerManager`) and never puts an instance
> into the open state; the response message states this scope explicitly.
> The legacy path `POST /api/monitoring/circuit-breaker/force-open` is a compatibility alias.

#### `POST /api/monitoring/metrics-pipeline/circuit-breaker/force-close`
Force close the **metrics pipeline** circuit breaker.

> Scope: same as above, metrics pipeline only.
> The legacy path `POST /api/monitoring/circuit-breaker/force-close` is a compatibility alias.

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

## Circuit Breaker & Rate Limit {#circuit-breaker-rate-limit}

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

## Quota Management

### Base Paths: `/api/config/quota`, `/api/monitoring/quota`

Manage the quota ledger's runtime configuration and observe quota status.

#### `GET /api/config/quota`

Get the current quota configuration snapshot (including runtime overrides).

**Permission:** `config:quota:read`

**Response Example:**
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "failOpen": true,
    "windows": ["MINUTE", "HOUR", "DAY", "MONTH"],
    "flushIntervalSeconds": 60,
    "retention": {
      "minute": "1d",
      "hour": "2d",
      "day": "35d",
      "month": "13mo"
    },
    "distributed": {
      "enabled": false,
      "keyPrefix": "jairouter:quota",
      "timeoutMs": 50,
      "degradeToLocal": true
    },
    "backendName": "local",
    "hotEditableFields": ["enabled", "failOpen", "windows"],
    "restartRequiredFields": ["distributed.enabled", "distributed.keyPrefix", "distributed.timeoutMs", "distributed.degradeToLocal", "retention.*", "flushIntervalSeconds"]
  }
}
```

#### `PUT /api/config/quota`

Update quota configuration at runtime (partial update; only non-null fields take effect).

**Permission:** `config:quota:write`

**Request Body:**
```json
{
  "enabled": true,
  "failOpen": true,
  "windows": ["MINUTE", "HOUR", "DAY"],
  "flushIntervalSeconds": null,
  "distributedEnabled": null,
  "distributedKeyPrefix": null,
  "distributedTimeoutMs": null,
  "distributedDegradeToLocal": null,
  "retention": null
}
```

- Hot-editable fields: `enabled`, `failOpen`, `windows`
- Restart-required fields (`distributedEnabled` / `distributedKeyPrefix` / `distributedTimeoutMs` / `distributedDegradeToLocal` / `retention` / `flushIntervalSeconds`): if any non-null value is sent, the request returns HTTP 400 + `errorCode=RESTART_REQUIRED` with the specific field names in the message
- All-null body → HTTP 400 + `errorCode=INVALID_REQUEST`

**Error Response Example:**
```json
{
  "success": false,
  "message": "以下字段需重启生效，不支持热改: distributed.enabled, retention.*；请移除这些字段后重试",
  "errorCode": "RESTART_REQUIRED"
}
```

#### `GET /api/monitoring/quota/status`

Query the quota ledger runtime status.

**Permission:** `monitoring:quota:read`

**Response Example:**
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "backendName": "local",
    "degraded": false,
    "degradedReason": "",
    "failOpen": true,
    "windows": ["MINUTE", "HOUR", "DAY", "MONTH"],
    "distributed": {
      "enabled": false,
      "keyPrefix": "jairouter:quota",
      "timeoutMs": 50,
      "degradeToLocal": true
    }
  }
}
```

`redisProbe` and `counterMetrics` are present only when `distributed.enabled=true`.

#### `GET /api/monitoring/quota/usage`

Query quota usage (read-only, no write side effects).

**Permission:** `monitoring:quota:read`

**Query Parameters:** `tenantId`, `apiKeyId`, `userId`, `serviceType`, `model`, `window` (all optional)

**Response Example:**
```json
{
  "success": true,
  "data": [
    {
      "dimensions": {
        "tenantId": "",
        "apiKeyId": "key-abc",
        "userId": "",
        "serviceType": "chat",
        "model": "gpt-4o"
      },
      "window": "DAY",
      "windowStart": "2026-09-06T00:00:00",
      "requestCount": 42,
      "tokenCount": 15000
    }
  ]
}
```

When the quota ledger is disabled, `data=[]` and message=`配额账本未启用`.

---

## API Key Quota Ops {#apikey-quota-ops}

### Base Path: `/api/auth/api-keys`

**Permission:** ADMIN (`security:apikeys:manage`)

Limit semantics: **0 = unlimited**; `quotaAlertThreshold` is a ratio **0.0–1.0** (e.g. 0.8 = 80%).

#### `GET /api/auth/api-keys/{keyId}/quota`

Quota detail for one key (usage, percents, `remainingRequests` / `remainingTokens`; unlimited → remaining = -1).

#### `PUT /api/auth/api-keys/{keyId}/quota`

Update **quota fields only** (partial; null fields unchanged).

**Request body:**
```json
{
  "dailyRequestLimit": 1000,
  "dailyTokenLimit": 100000,
  "rateLimitPerMinute": 60,
  "quotaAlertThreshold": 0.8
}
```

#### `GET /api/auth/api-keys/quota/alerts`

Keys that crossed the alert threshold.

#### `GET /api/auth/api-keys/quota/overview`

Quota overview for all keys.

#### `POST /api/auth/api-keys/{keyId}/quota/reset`

Reset one key's daily counters and rate limit.

#### `POST /api/auth/api-keys/quota/batch-reset`

**Request body:** `{ "keyIds": ["key-a", "key-b"] }` → `{ "requested": 2, "reset": 2 }`

#### `POST /api/auth/api-keys/quota/reset-all`

Reset all keys' daily quotas and rate limits.

---

## PII / Sanitization Management {#sanitization-mgmt}

### Base Path: `/api/config/sanitization`

**Permission:** `security:sanitization:manage` (ADMIN)

Two surfaces apply — **record-side** `sanitizeForStorage` (masking for call-history / log / trace persistence, not controlled by the response switch) and the gateway response filter (`jairouter.security.sanitization.response.*`, off by default and on in the prod profile). The response scope is governed by `response.scope`: management paths such as `/api/**` and `/actuator/**` are excluded by default, while non-streaming JSON under `/v1/**` and SSE frames are covered. This endpoint configures the response side only (the request-side configuration surface was removed in 3.5.0).

#### `GET /api/config/sanitization`

Snapshot of the response sub-config, `ruleCount`, and design notes.

#### `PUT /api/config/sanitization`

Hot-update the response sub-config (non-null fields) and rebuild in-memory rules.

**Request body example:**
```json
{
  "response": {
    "enabled": true,
    "piiPatterns": ["\\d{11}"],
    "sensitiveWords": ["password"],
    "maskingChar": "*"
  }
}
```

#### `GET /api/config/sanitization/rules`

List active rules.

#### `POST /api/config/sanitization/test`

Dry-run. If `contentType` is missing/`application/json` but the sample **does not look like JSON**, masking runs as `text/plain`.

**Request:** `{ "sample": "phone 13800138000", "contentType": "text/plain" }`

**Response:** `{ "before", "after", "matchedRuleIds", "contentType" }`

See [Data Sanitization](../security/data-sanitization.md).

---

## Adapter Configuration

### Base Path: `/api/config/adapter`

#### `GET /api/config/adapter`
Get adapter configuration and capabilities.

---

## Instance Management

### Base Path: `/api/config/instance`

These are the **current** endpoints (implemented by `ServiceTypeInstanceController`). The older
`/api/instance-configs` controller was removed in **v3.0.2** — see the changelog entry "Removed 4 deprecated
controllers"; if you still call that path you will get `404`.

All of them need authentication: send the JWT in the **`Jairouter_Token`** header (configurable via
`jairouter.security.jwt.jwt-header`) — it is **not** `Authorization: Bearer`. Without it every call below
returns `401 {"error":{"code":"AUTH_MISSING"}}`.

#### `GET /api/config/instance/{serviceType}`
List every instance of a service type. Returns `RouterResponse<List<ServiceInstanceDTO>>`; a service type
with no configuration yields `200` with an empty list (not `404`).

#### `POST /api/config/instance/{serviceType}`
Add one instance. Body = `CreateServiceInstanceRequest`: `name`, `baseUrl`, `path`, `weight`, `status`,
`adapter`, `headers`, `tags`. Returns the created `ServiceInstanceDTO`, whose `id` (a database `Long`) is
what the update and delete calls below need.

#### `PUT /api/config/instance/{serviceType}/{instanceId}`
Update an instance **by its database id** (`instanceId` = the `id` from the list call), with the same body
shape as `POST`.

#### `DELETE /api/config/instance/{serviceType}/{instanceId}`
Delete an instance by its database id.

#### Per-instance rate limit and circuit breaker
```
GET|PUT /api/config/instance/{serviceType}/{instanceId}/rate-limit
GET|PUT /api/config/instance/{serviceType}/{instanceId}/circuit-breaker
GET     /api/config/instance/circuit-breaker/states        (deprecated)
POST    /api/config/instance/circuit-breaker/reset         (compatibility alias)
POST    /api/config/instance/circuit-breaker/clear-all     (deprecated)
```

Circuit breaker **runtime state** is now managed by the monitor API, which joins the live breaker
snapshot with the instance metadata (name, service type, upstream URL, thresholds, last failure):

```
GET  /api/v1/circuit-breaker-monitor/instances                    # cb:monitoring:read
POST /api/v1/circuit-breaker-monitor/instances/{instanceId}/reset # config:circuitbreaker:write
POST /api/v1/circuit-breaker-monitor/instances/reset              # config:circuitbreaker:write
```

`{instanceId}` here is the **runtime instance UUID** (the `instanceId` column), not the database
`Long` id used by the update/delete calls above. Reset moves the state machine to `CLOSED`, clears
the counters, writes a `MANUAL_RESET` history record, and deletes the persisted state so a restart
cannot restore `OPEN`. A single reset returns
`{previousState, currentState, persistedStateDeleted, triggerReason}`; a batch reset accepts
`{instanceIds}` or `{serviceType, states}` and returns `{matched, reset[]}`. An unknown instance
returns `404 NOT_FOUND` instead of a silent success.

> Migration notes: `GET .../states` returns a flat `Map<key, state>` without counters or names and is
> kept only for compatibility; `POST .../clear-all` has no scope and is superseded by
> `POST /api/v1/circuit-breaker-monitor/instances/reset`; the former
> `POST /api/config/instance/{serviceType}/{instanceId}/circuit-breaker/reset` was removed because it
> fed a database `Long` id into a runtime map keyed by the instance UUID, so it never matched.

A full add → update → delete round trip (these commands were executed against a running instance and all
returned `200`):

```bash
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMeOnFirstStartup123456"}' \
  http://localhost:8080/api/auth/jwt/login | jq -r .data.token)

# 1. add
curl -X POST -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":2,"adapter":"normal"}' \
  http://localhost:8080/api/config/instance/chat

# 2. list, and take the database id of the instance you just added
curl -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat
ID=$(curl -s -H "Jairouter_Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="qwen2:7b") | .id')

# 3. update / 4. delete by that id
curl -X PUT -H "Jairouter_Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":3,"adapter":"normal"}' \
  "http://localhost:8080/api/config/instance/chat/$ID"
curl -X DELETE -H "Jairouter_Token: $TOKEN" "http://localhost:8080/api/config/instance/chat/$ID"
```

⚠️ Do not use a `/type/`, `/add/`, `/update/`, `/del/` or `/info/` sub-path: those shapes were never routes
and Spring matches them against `/{serviceType}/{instanceId}` instead, which answers `405`.

---

## Error Responses

All API endpoints follow a consistent error response format:

> 📖 **Complete error code reference**: [Error Codes Reference](./error-codes.md)
>
> `/api/**` uses the **flat** `RouterResponse` field `errorCode` — there is **no** `error` object and **no** `details` field (`RouterResponse.java:15-21`).

```json
{
  "success": false,
  "message": "Error description",
  "errorCode": "ERROR_CODE",
  "data": null
}
```

### Common Error Codes

| Code | Description | HTTP status |
|------|-------------|-------------|
| `NOT_FOUND` | Resource not found | 404 |
| `INVALID_REQUEST` | Invalid request data | 400 |
| `INVALID_API_KEY` | Authentication required | 401 |
| `FORBIDDEN` | Permission denied | 403 |
| `CONFLICT_STATE` | Resource conflict | 409 |
| `RATE_LIMIT_EXCEEDED` | Too many requests | 429 |
| `INTERNAL_ERROR` | Server error | 500 |

**Authentication-related error codes**:

| Code | Description |
|------|-------------|
| `INVALID_API_KEY` | Invalid API Key |
| `EXPIRED_API_KEY` | API Key expired |
| `JWT_INVALID` | Invalid JWT token |
| `EXPIRED_JWT_TOKEN` | JWT token expired |

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

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
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "errorCode": null
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

### SLA access {#sla-access}

Two surfaces: the anonymous endpoints the SLA tool calls, and the admin endpoints that maintain its accounts in the console.

**Why a separate credential**: the SLA tool has to run unattended for months, while access tokens last 60 minutes and the refresh
window is one day (`jwt.yml`) — so the status quo stores an admin password in the customer environment. With a one-time bootstrap
token that is exchanged for a long-lived refresh credential, the secret appears exactly once, the principal is bound to a dedicated
role template (`SLA_READONLY` / `SLA_OPERATOR`), and revocation takes effect immediately.

#### Base path: `/api/auth/sla` (anonymous, no `Authorization` header)

These two endpoints sit outside the authentication chain: they need none of `Authorization`,
`X-Jairouter-Token` or `X-API-Key`, and sending one changes nothing (the credential check happens
inside the service rather than in the authentication layer).

##### `POST /api/auth/sla/bootstrap`

Exchange a one-time bootstrap token for long-lived credentials.

```json
{ "token": "one-time token plaintext" }
```

Response `data`:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "refresh credential plaintext",
  "expiresIn": 3600,
  "subject": "sla:builder"
}
```

- The token lives 15 minutes by default and is **single-use** (concurrent double-spend is caught by row-level optimistic locking;
  the loser gets "SLA bootstrap token has already been used").
- The token may be bound to a caller IP in advance; a mismatch returns 403.
- A disabled account returns 403.
- The plaintext appears only at issuance and in this response; the database stores SHA-256.
- `expiresIn` is in **seconds** (default 3600); the same applies to `refresh`.
- Bootstrap TTL defaults to 15 minutes and is capped at 60 (a larger value is clamped on issue); the token is
  **single-use** (concurrent double-spend is caught by the row-level optimistic lock).
- The source IP can be bound up front: a single IPv4/IPv6 literal or an IPv4 CIDR such as `10.0.0.0/24`;
  a mismatch returns `403`.
- Failure semantics: missing / invalid / already used / revoked / expired token ⇒ `401`; bound IP mismatch or
  disabled account ⇒ `403`; unknown account ⇒ `404`.

##### `POST /api/auth/sla/refresh`

Exchange a refresh credential for a new access token.

```json
{ "refreshToken": "refresh credential plaintext" }
```

Response `data`: `{ "accessToken": "...", "expiresIn": 3600 }`.

- The refresh credential lives 90 days by default and rolls forward on every use (`lastUsedAt`, `expiresAt`, `clientIp` updated),
  but rolling never passes the **absolute lifetime** (365 days after first issue); past it the call fails with
  "absolute lifetime reached" and a new bootstrap token is required.
- A changed source IP does **not** block the refresh (NAT egress and dual-stack hosts move legitimately); it only logs a
  warning and records a `REFRESH_IP_CHANGED` audit entry.
- A missing or blank `refreshToken` returns `401` instead of falling through to an internal error.
- Revoked ⇒ "SLA refresh credential has been revoked"; expired ⇒ "SLA refresh credential has expired".
- A suspended login principal (credentials revoked or account disabled) ⇒ `403`, and the principal is **not**
  revived automatically: reconnecting requires a newly issued bootstrap token.

#### Base path: `/api/security/sla` (needs `system:sla:read` / `system:sla:manage`)

Every endpoint in this group runs its blocking database work on the `boundedElastic` pool instead of the
Netty event loop. `/api/auth/sla/**` and `/api/security/sla/**` share one per-source-IP rate-limit bucket
(120 requests per minute and 1000 per hour by default, keys `jairouter.auth.sla-rate-limit.per-minute` and
`.per-hour`); exceeding it returns `429`.

##### `GET /api/security/sla/accounts`

Pages through SLA accounts with `page` (zero-based), `size` (1–100, default 20) and `keyword` (name substring):

```
GET /api/security/sla/accounts?page=0&size=20&keyword=build
```

The response has two parts: `page` is the current page (`content` rows carry `accountId`, `name`,
`roleTemplate`, `enabled`, `createdBy`, `createdAt`, `lastSeenAt`, `activeRefreshTokens`,
`unusedBootstrapTokens`, alongside `totalElements` / `totalPages` / `hasNext`), and `summary` holds
platform-wide totals (`accounts` / `enabled` / `refreshTokens` / `pendingTokens`).

The summary ships with the list because summing the current page no longer equals the total once paginated.
`pendingTokens` counts only tokens that are unused, not revoked and not expired. Requires `system:sla:read`.

##### `POST /api/security/sla/accounts`

```json
{ "name": "builder", "roleTemplate": "SLA_READONLY" }
```

`roleTemplate` must be `SLA_READONLY` or `SLA_OPERATOR`; a duplicate name returns a conflict. Requires `system:sla:manage`.

##### `POST /api/security/sla/accounts/{accountId}/bootstrap`

Issues a one-time bootstrap token (**the response is the only place the plaintext appears**).

```json
{ "ttlMinutes": 15, "allowedIp": "10.0.0.8" }
```

Both fields are optional (defaults: 15 minutes, any IP). `ttlMinutes` is clamped to 60; `allowedIp` accepts a single
IPv4/IPv6 literal or an IPv4 CIDR, and a malformed value returns `400` instead of being stored. Response:
`{ "bootstrapToken": "...", "expiresAt": "...", "expiresIn": 900 }`.

##### `POST /api/security/sla/accounts/{accountId}/revoke`

Revokes every credential of that account and returns `{ "revoked": 2 }` (the number of refresh credentials revoked in this call).
One call does three things:

1. every refresh credential gets `revoked_at`, so the next refresh fails immediately;
2. every unconsumed bootstrap token is invalidated as well (also flagged with `revoked_at`, never deleted, so the audit trail survives);
3. the matching `sla:<name>` principal in `jwt_accounts` is set to `enabled=false`.

Step 3 makes **already-issued access tokens fail on their very next request** (the principal is re-checked on every request)
instead of waiting for them to expire. The trade-off is that revoking cannot be used for a zero-downtime "issue new, then revoke old"
rotation: after a revoke the account has to be issued a new bootstrap token to come back.

> Reconnect in the order "revoke, then issue": a new bootstrap token restores the principal, so doing it the other way around lets
> old access tokens come back to life for the remainder of their lifetime.

##### `POST /api/security/sla/accounts/{accountId}/enabled`

Enables or disables the account.

```json
{ "enabled": false }
```

`enabled` must be given explicitly (a missing field returns `400`; it never defaults to disabling). Disabling equals
"revoke plus seal": every refresh credential is revoked, unconsumed bootstrap tokens are invalidated and the `jwt_accounts`
principal is suspended, so issued access tokens stop working immediately. Enabling only restores the account switch and
does **not** restore the principal — a bootstrap token still has to be issued before the SLA can reconnect.
Returns the updated account view with the same fields as the list endpoint. Requires `system:sla:manage`.

##### `DELETE /api/security/sla/accounts/{accountId}`

Deletes the account together with its bootstrap tokens, refresh credentials and the corresponding `sla:<name>` principal in `jwt_accounts`.

#### Credential cleanup

Expired and revoked rows in both credential tables are reclaimed once an hour by a built-in task. It deletes only rows that
are already expired or revoked **and** older than the retention window, so anything inside the window is still available for
audit comparison and troubleshooting:

| Key | Default | Meaning |
| --- | --- | --- |
| `jairouter.security.sla.retention-days` | `30` | How many days expired or revoked rows are kept |
| `jairouter.security.sla.cleanup-cron` | `0 40 * * * ?` | Cleanup schedule (minute 40 of every hour) |

Cleanup runs a single `DELETE ... WHERE` per table instead of loading everything into memory.

### Login Protection {#login-guard}

After the failure threshold is exceeded the login endpoint returns `429` with `RATE_LIMITED_LOGIN`; these endpoints expose the thresholds, allow changing
them, and release existing locks.

#### Base path: `/api/security/login-guard`

| Method | Path | Description | Permission |
| --- | --- | --- | --- |
| GET | `/api/security/login-guard` | Effective thresholds plus who changed them and when | `security:loginguard:read` |
| PUT | `/api/security/login-guard` | Change the four thresholds (persisted + hot-applied + audited) | `security:loginguard:manage` |
| GET | `/api/security/login-guard/locked` | Principals still inside the lock window | `security:loginguard:read` |
| POST | `/api/security/login-guard/unlock` | Release locks (no body releases everything) | `security:loginguard:manage` |

The PUT body must carry all four values:

```json
{
  "maxFailuresPerPrincipal": 5,
  "maxFailuresPerIp": 30,
  "windowMinutes": 15,
  "lockMinutes": 15
}
```

**Where the values come from**: `jairouter.auth.login-bruteforce.*` in `config/auth/login-guard.yml` is only the initial value used when the table has no row yet
(persisted at first startup with `updatedBy=system`). Once a row exists the database wins on every startup — the same rule as `jwt_accounts`. Editing the YAML
therefore has no effect on an initialized deployment; change thresholds through this page or these endpoints.

**Unlock semantics**: `{"username": "admin", "ip": "127.0.0.1"}` releases only that pair; `username` alone releases that user on every IP; `ip` alone releases
that IP (including its cross-user `ip:` counter); an empty object clears everything. Responds with `{"data": {"cleared": 2}}`.

**Locked principals live in instance memory**: with several replicas each one counts independently, so this page and `/locked` only reflect the current instance.
Releasing a user everywhere requires calling each instance, or waiting for `lockMinutes` to elapse. Saving new thresholds does **not** unlock existing
principals (relaxing a limit is not the same as granting access).

### API Key Management

#### Base Path: `/api/config/ratelimit`

Read and adjust the switches and thresholds of the per-API-Key per-minute limit (L1) and the end-user limit (L2) at runtime. Both only change **in-memory properties**; nothing is written back to the YAML under `config/`, so a restart reverts to the configuration files.

#### `GET /api/config/ratelimit`

Returns the currently effective rate-limit configuration snapshot.

**Permission code:** `config:ratelimit:read`

**Example response:**
```json
{
  "success": true,
  "data": {
    "apiKey": {
      "enabled": true,
      "localCacheSize": 50000
    },
    "endUser": {
      "enabled": false,
      "requestsPerMinute": 60,
      "headerEnabled": true,
      "missingIdentity": "SKIP",
      "localCacheSize": 50000,
      "fingerprintLength": 16
    },
    "distributed": {
      "enabled": false,
      "timeoutMs": 50,
      "apiKeyBackend": "local",
      "endUserBackend": "local"
    },
    "hotEditableFields": ["apiKey.enabled", "endUser.enabled", "endUser.requestsPerMinute", "endUser.headerEnabled", "endUser.missingIdentity"],
    "restartRequiredFields": ["distributed.enabled", "distributed.timeoutMs", "apiKey.localCacheSize", "endUser.localCacheSize", "endUser.fingerprintLength"]
  }
}
```

#### `PUT /api/config/ratelimit`

Updates the rate-limit configuration at runtime (partial update; only non-null fields apply).

**Permission code:** `config:ratelimit:write`

**Request body:**
```json
{
  "apiKeyEnabled": null,
  "endUserEnabled": true,
  "endUserRequestsPerMinute": 120,
  "endUserHeaderEnabled": null,
  "endUserMissingIdentity": "REJECT",
  "distributedEnabled": null,
  "distributedTimeoutMs": null,
  "apiKeyLocalCacheSize": null,
  "endUserLocalCacheSize": null,
  "endUserFingerprintLength": null
}
```

- Hot-editable: `apiKeyEnabled`, `endUserEnabled`, `endUserRequestsPerMinute`, `endUserHeaderEnabled`, `endUserMissingIdentity`
- Restart required (`distributedEnabled` / `distributedTimeoutMs` / `apiKeyLocalCacheSize` / `endUserLocalCacheSize` / `endUserFingerprintLength`): a non-null value returns HTTP 400 + `errorCode=RESTART_REQUIRED`, and **the whole request is rejected** (no partial write)
- `endUserMissingIdentity` accepts only `SKIP` / `REJECT` (case-insensitive); anything else → HTTP 400 + `errorCode=INVALID_REQUEST`
- `endUserRequestsPerMinute` must be ≥ 1, otherwise HTTP 400 + `errorCode=INVALID_REQUEST`
- An entirely empty body → HTTP 400 + `errorCode=INVALID_REQUEST`

### Base Path: `/api/auth/api-keys`

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
    "windows": ["HOUR", "DAY", "MONTH"],
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
  "windows": ["HOUR", "DAY", "MONTH"],
  "flushIntervalSeconds": null,
  "distributedEnabled": null,
  "distributedKeyPrefix": null,
  "distributedTimeoutMs": null,
  "distributedDegradeToLocal": null,
  "retention": null
}
```

- Hot-editable fields: `enabled`, `failOpen`, `windows`
  (since v3.8.6 the ledger no longer judges the per-minute rate: a `MINUTE` entry in `windows` is not counted,
  the per-minute ceiling belongs to the `config:ratelimit` limiters)
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
    "windows": ["HOUR", "DAY", "MONTH"],
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

All of them need authentication: send the JWT in the **`X-Jairouter-Token`** header (configurable via
`jairouter.security.jwt.jwt-header`) — it is **not** `Authorization: Bearer`. Without it every call below
returns `401 {"error":{"code":"AUTH_MISSING"}}`.

#### `GET /api/config/instance/{serviceType}`
List every instance of a service type. Returns `RouterResponse<List<ServiceInstanceDTO>>`; a service type
with no configuration yields `200` with an empty list (not `404`).

#### `POST /api/config/instance/{serviceType}`
Add one instance. Body = `CreateServiceInstanceRequest`: `name`, `baseUrl`, `path`, `weight`, `status`,
`adapter`, `headers`, `tags`. Returns the created `ServiceInstanceDTO`, whose `instanceId` field (a UUID)
is the canonical identifier for the update and delete calls below.

#### `PUT /api/config/instance/{serviceType}/{instanceId}`
Update an instance **by its `instanceId` (UUID)**, with the same body shape as `POST`. For compatibility
with v2.5~v3.7 callers the database `Long` id form is still accepted (issue #1009).

#### `DELETE /api/config/instance/{serviceType}/{instanceId}`
Delete an instance by the same identifier (`instanceId` UUID, or the legacy database id).

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

`{instanceId}` here is the **runtime instance UUID** (the `instanceId` column). Since issue #1009 every
`{instanceId}` path parameter uses this same UUID as the canonical form, and the database `Long` id
form remains accepted as a compatibility path. Reset moves the state machine to `CLOSED`, clears
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
curl -X POST -H "X-Jairouter-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":2,"adapter":"normal"}' \
  http://localhost:8080/api/config/instance/chat

# 2. list, and take the database id of the instance you just added
curl -H "X-Jairouter-Token: $TOKEN" http://localhost:8080/api/config/instance/chat
ID=$(curl -s -H "X-Jairouter-Token: $TOKEN" http://localhost:8080/api/config/instance/chat \
  | jq -r '.data[] | select(.name=="qwen2:7b") | .id')

# 3. update / 4. delete by that id
curl -X PUT -H "X-Jairouter-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"qwen2:7b","baseUrl":"http://gpu-server:8080","path":"/v1/chat/completions","weight":3,"adapter":"normal"}' \
  "http://localhost:8080/api/config/instance/chat/$ID"
curl -X DELETE -H "X-Jairouter-Token: $TOKEN" "http://localhost:8080/api/config/instance/chat/$ID"
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
Header: X-Jairouter-Token: <your-jwt-token>
```

### API Key
```
Header: X-API-Key: <your-api-key>
```

---

*Last updated: 2026-05-21*

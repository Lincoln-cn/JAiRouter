# State Persistence Configuration

> Document Version: 2.6.11
> Applicable Version: v2.6.x
> Last Updated: 2026-06-09

---

## Overview

JAiRouter supports state persistence functionality since v2.4.4, implementing a **three-tier fallback strategy**:

```
Redis (Tier 1) → H2 (Tier 2) → File (Tier 3)
```

- **Tier 1 (Redis)**: Highest priority, for distributed shared state
- **Tier 2 (H2)**: Default fallback layer, using existing StoreManager framework
- **Tier 3 (File)**: Ultimate fallback, for extreme scenarios

---

## Configuration File

### Basic Configuration

Configuration file location: `src/main/resources/config/persistence/state-persistence-base.yml`

```yaml
jairouter:
  persistence:
    # Enable state persistence
    enabled: true

    # Redis configuration (Tier 1)
    redis:
      enabled: false
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0

    # H2 configuration (Tier 2) - Default layer
    h2:
      enabled: true
      # Uses StoreManager's H2 database

    # File configuration (Tier 3) - Fallback layer
    file:
      enabled: true
      path: ./data/state

    # Sync configuration
    sync:
      interval: 30000  # Sync interval (ms)
      recoveryTimeout: 10000  # Recovery timeout (ms)
```

---

## State Types

Supported state types for persistence:

| State Type | Description | State Data |
|------------|-------------|------------|
| `CIRCUIT_BREAKER` | Circuit breaker state | State, failure count, success count, last failure time |
| `LOAD_BALANCER` | Load balancer state | Instance weights, active connections |
| `RATE_LIMITER` | Rate limiter state | Token count, algorithm type, config parameters |
| `MODEL_STATS` | Model statistics | Request count, success count, latency stats |
| `CUSTOM` | Custom state | User-defined arbitrary state |

---

## API Endpoints

### State Persistence Management API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/state-persistence/status` | GET | Get storage tier status |
| `/api/state-persistence/details` | GET | Get all state details |
| `/api/state-persistence/sync` | POST | Manual sync trigger |
| `/api/state-persistence/recover` | POST | Recover all states |
| `/api/state-persistence/recover/{type}/{key}` | POST | Recover single state |
| `/api/state-persistence/rate-limiter/recover/{limiterId}` | POST | Recover rate limiter state |

### Example Requests

**Get storage tier status**:
```bash
curl -X GET http://localhost:8080/api/state-persistence/status \
  -H "Authorization: Bearer {token}"
```

**Response example**:
```json
{
  "success": true,
  "data": {
    "activeTier": "h2",
    "tiers": {
      "redis": false,
      "h2": true,
      "file": true
    },
    "stats": {
      "circuitBreakerCount": 5,
      "loadBalancerCount": 6,
      "rateLimiterCount": 0
    }
  }
}
```

---

## Performance Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| Single state save | < 100ms | H2 layer latency |
| Redis state save | < 50ms | Redis layer latency |
| Batch save throughput | > 100 ops/s | 100 states |
| Recovery time | < 10s | Startup recovery |
| Fallback switch | < 1s | Tier switch latency |

---

## Best Practices

### 1. Production Environment Recommended Configuration

```yaml
jairouter:
  persistence:
    enabled: true
    redis:
      enabled: true  # Recommended for production
    h2:
      enabled: true  # As fallback layer
    file:
      enabled: true  # As ultimate fallback
```

### 2. Development Environment Configuration

```yaml
jairouter:
  persistence:
    enabled: true
    redis:
      enabled: false  # Optional for development
    h2:
      enabled: true
    file:
      enabled: false  # Optional for development
```

### 3. Monitoring Recommendations

- Monitor `activeTier` changes to detect fallback switches
- Monitor `pendingSyncCount` to ensure normal state sync
- Regularly check health status to ensure storage tier availability

---

## Failure Recovery

### Automatic Fallback

When a tier is unavailable, the system automatically switches to the next tier:

1. Redis unavailable → Automatic switch to H2
2. H2 unavailable → Automatic switch to File
3. All tiers unavailable → Return error

### Manual Recovery

```bash
# Recover all states
curl -X POST http://localhost:8080/api/state-persistence/recover

# Recover single circuit breaker state
curl -X POST http://localhost:8080/api/state-persistence/recover/circuit_breaker/ollama-1

# Recover rate limiter state
curl -X POST http://localhost:8080/api/state-persistence/rate-limiter/recover/global
```

---

## Related Documentation

- [Circuit Breaker Configuration](circuit-breaker.md)
- [Rate Limiting Configuration](rate-limiting.md)
- [Store Configuration](store-config.md)

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-04-26 | Initial state persistence configuration document |
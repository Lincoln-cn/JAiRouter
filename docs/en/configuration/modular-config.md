# Modular Configuration Guide

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

## Overview

JAiRouter adopts a **modular configuration management** approach, splitting complex configurations into multiple independent configuration files by function and service module. This design improves configuration maintainability, readability, and reusability.

## Configuration Module Structure (v2.8.x)

### Directory Structure

```
config/
├── common/              # Common infrastructure
│   ├── server.yml       # Server configuration
│   ├── webclient.yml    # WebClient configuration
│   └── logging.yml      # Logging configuration
├── config-service/      # Configuration service
│   └── core.yml         # ConfigurationService settings
├── router/              # Router service
│   ├── adapter.yml      # Adapter configuration
│   ├── loadbalancer.yml # Load balancer configuration
│   ├── ratelimit.yml    # Rate limiting configuration
│   ├── circuitbreaker.yml # Circuit breaker configuration
│   ├── fallback.yml     # Fallback configuration
│   └── services.yml     # Service instance configuration
├── auth/                # Authentication service
│   ├── jwt.yml          # JWT authentication configuration
│   ├── api-key.yml      # API key configuration
│   ├── audit.yml        # Audit configuration
│   └── sanitization.yml # Data sanitization configuration
├── monitor/             # Monitoring service
│   ├── slow-query-alerts.yml
│   ├── error-tracking.yml
│   └── persistence-monitoring-base.yml
├── tracing/             # Tracing service
│   └── tracing-base.yml
├── persistence/         # Persistence service
│   └── state-persistence-base.yml
├── base/                # Base configurations (legacy)
│   ├── server-base.yml
│   └── model-services-base.yml
└── security/            # Security configurations (legacy)
    ├── security-base.yml
    ├── persistence-base.yml
    └── audit-base.yml
```

### Module Categories

| Module | Directory | Purpose |
|--------|-----------|---------|
| **Common** | `config/common/` | Shared infrastructure (server, webclient, logging) |
| **Config Service** | `config/config-service/` | Configuration management service |
| **Router** | `config/router/` | Routing, load balancing, rate limiting, circuit breaker |
| **Auth** | `config/auth/` | Authentication (JWT, API Key), audit, sanitization |
| **Monitor** | `config/monitor/` | Performance monitoring, error tracking |
| **Tracing** | `config/tracing/` | Distributed tracing |
| **Persistence** | `config/persistence/` | State persistence |

---

## Main Configuration Import

### application.yml

```yaml
spring:
  config:
    import:
      # External configuration (highest priority)
      - optional:file:/app/config/application.yml
      - optional:file:/app/config/auth/jwt.yml
      - optional:file:/app/config/router/services.yml

      # common module
      - classpath:config/common/server.yml
      - classpath:config/common/webclient.yml
      - classpath:config/common/logging.yml

      # config-service module
      - classpath:config/config-service/core.yml

      # router module
      - classpath:config/router/adapter.yml
      - classpath:config/router/loadbalancer.yml
      - classpath:config/router/ratelimit.yml
      - classpath:config/router/circuitbreaker.yml
      - classpath:config/router/fallback.yml
      - classpath:config/router/services.yml

      # auth module
      - classpath:config/auth/jwt.yml
      - classpath:config/auth/api-key.yml
      - classpath:config/auth/audit.yml
      - classpath:config/auth/sanitization.yml

      # monitor module
      - classpath:config/base/monitoring-base.yml
      - classpath:config/monitor/slow-query-alerts.yml
      - classpath:config/monitor/error-tracking.yml
      - classpath:config/monitor/persistence-monitoring-base.yml

      # tracing module
      - classpath:config/tracing/tracing-base.yml

      # persistence module
      - classpath:config/persistence/state-persistence-base.yml
```

---

## Configuration Priority

Configuration loading follows this priority order (higher priority overrides lower priority):

1. Command-line arguments (highest priority)
2. Environment variables
3. External configuration files (`/app/config/`)
4. Environment-specific configuration files (`application-{profile}.yml`)
5. Module configuration files (`config/{module}/*.yml`)
6. Base configuration modules (lowest priority)

---

## Module Configuration Details

### Common Module

#### Server Configuration (`config/common/server.yml`)

```yaml
server:
  port: 8080                    # Service port
  compression:
    enabled: true               # Enable response compression
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # Enable HTTP/2
```

#### WebClient Configuration (`config/common/webclient.yml`)

```yaml
webclient:
  connect-timeout: 10000        # Connection timeout (ms)
  read-timeout: 60000           # Read timeout (ms)
  write-timeout: 60000          # Write timeout (ms)
  max-in-memory-size: 10MB      # Max in-memory buffer size
  pool:
    max-connections: 500        # Maximum connections
    acquire-timeout: 10000      # Acquire timeout (ms)
```

#### Logging Configuration (`config/common/logging.yml`)

```yaml
logging:
  level:
    root: INFO
    org.unreal.modelrouter: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/jairouter.log
    max-size: 10MB
    max-history: 30
```

---

### Router Module

#### Adapter Configuration (`config/router/adapter.yml`)

```yaml
jairouter:
  adapter:
    default-adapter: "ollama"           # Default adapter type
    connect-timeout: 10000              # Connection timeout (ms)
    read-timeout: 60000                 # Read timeout (ms)
```

#### Load Balancer Configuration (`config/router/loadbalancer.yml`)

```yaml
jairouter:
  loadbalancer:
    default-strategy: "random"          # random, round_robin, weighted
    health-check-interval: 30000        # Health check interval (ms)
```

#### Rate Limiting Configuration (`config/router/ratelimit.yml`)

```yaml
jairouter:
  ratelimit:
    enabled: true
    default-algorithm: "token_bucket"   # token_bucket, leaky_bucket
    default-capacity: 100               # Default bucket capacity
    default-refill-rate: 10             # Tokens per second
```

#### Circuit Breaker Configuration (`config/router/circuitbreaker.yml`)

```yaml
jairouter:
  circuitbreaker:
    enabled: true
    failure-threshold: 5                # Failures to trigger open
    success-threshold: 3                # Successes to close
    timeout: 30000                      # Open state timeout (ms)
```

---

### Auth Module

#### JWT Configuration (`config/auth/jwt.yml`)

```yaml
jairouter:
  security:
    jwt:
      enabled: false                    # Enable JWT authentication
      jwt-header: "Jairouter_Token"     # Custom JWT header
      secret: "${JWT_SECRET:}"          # JWT secret (use env var)
      algorithm: "HS256"                # Signing algorithm
      expiration-minutes: 60            # Access token expiration
      refresh-expiration-days: 7        # Refresh token expiration
      issuer: "jairouter"               # Token issuer
      blacklist-enabled: true           # Enable token blacklist
```

#### API Key Configuration (`config/auth/api-key.yml`)

```yaml
jairouter:
  security:
    api-key:
      enabled: true                     # Enable API key authentication
      header-name: "X-API-Key"          # API key header name
      hash-algorithm: "SHA-256"         # Key hashing algorithm
```

#### Audit Configuration (`config/auth/audit.yml`)

```yaml
jairouter:
  security:
    audit:
      enabled: true                     # Enable audit logging
      include-request-body: true        # Log request bodies
      include-response-body: false      # Log response bodies
      max-body-length: 1024             # Max body length to log
```

#### Data Sanitization Configuration (`config/auth/sanitization.yml`)

```yaml
jairouter:
  security:
    sanitization:
      enabled: true                     # Enable data sanitization
      mask-sensitive-fields:            # Fields to mask
        - "password"
        - "secret"
        - "token"
        - "api-key"
```

---

### Monitor Module

#### Slow Query Alerts (`config/monitor/slow-query-alerts.yml`)

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        enabled: true
        min-interval-ms: 300000         # Alert cooldown
```

#### Error Tracking (`config/monitor/error-tracking.yml`)

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: false                    # Disabled by default
      aggregation-window-minutes: 5     # Aggregation window
      sanitization:
        enabled: true
```

---

### Tracing Module

#### Tracing Configuration (`config/tracing/tracing-base.yml`)

```yaml
jairouter:
  tracing:
    enabled: false
    service-name: "jairouter"
    sampling-rate: 1.0                  # Sampling rate (0.0 - 1.0)
    export-enabled: false
    exporter-type: "otlp"               # otlp, jaeger, zipkin
    endpoint: "http://localhost:4317"
```

---

### Persistence Module

#### State Persistence (`config/persistence/state-persistence-base.yml`)

```yaml
jairouter:
  persistence:
    enabled: true
    primary-storage: "h2"               # h2, redis, memory
    fallback-storage: "memory"
    cleanup:
      enabled: true
      schedule: "0 */5 * * * ?"         # Every 5 minutes
      retention-days: 7
```

---

## Environment-Specific Configuration

### Development (`application-dev.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: dev

logging:
  level:
    root: DEBUG

jairouter:
  security:
    jwt:
      enabled: false                    # Disable JWT in dev
  monitoring:
    enabled: false                      # Disable monitoring in dev
```

### Production (`application-prod.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: prod

logging:
  level:
    root: INFO

jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"           # Must be set via env var
  monitoring:
    enabled: true
```

---

## External Configuration (Docker)

Mount external configuration files to override defaults:

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v /path/to/config:/app/config \
  -e JWT_SECRET="your-secret-key" \
  sodlinken/jairouter:latest
```

### External Configuration Structure

```
/app/config/
├── application.yml      # Override main config
├── auth/
│   └── jwt.yml          # Override JWT config
└── router/
    └── services.yml     # Override service instances
```

---

## Configuration Best Practices

1. **Use environment variables** for sensitive values (secrets, passwords)
2. **Use external configuration** for Docker deployments
3. **Use profile-specific configuration** for different environments
4. **Keep module configurations** focused and independent
5. **Document custom configurations** in your deployment guide

---

## Troubleshooting

### Configuration Not Taking Effect

1. Check configuration file paths
2. Confirm environment configuration files are loaded
3. Verify configuration priority order
4. Check for syntax errors

### Configuration Conflicts

1. Understand configuration priority rules
2. Check for duplicate configuration items
3. Confirm environment variables don't override expected values
4. Use `--debug` flag to view configuration loading process

---

*Last updated: 2026-05-21*

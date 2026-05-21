# Application Configuration

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

This document details the basic application configuration of JAiRouter, including server configuration, WebClient configuration, monitoring configuration, and more.

## Configuration File Locations

| Location | Description |
|----------|-------------|
| `src/main/resources/application.yml` | Main configuration file |
| `src/main/resources/config/{module}/*.yml` | Module configuration files |
| `/app/config/*.yml` | External configuration (Docker deployment) |
| `application-{profile}.yml` | Environment-specific configuration |

## Modular Configuration Structure (v2.8.x)

JAiRouter adopts a **modular configuration management** approach, splitting complex configurations into multiple independent configuration files by function and service module.

### Configuration Module Overview

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

### Configuration Import

The main `application.yml` imports all module configurations:

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

### Configuration Priority

Configuration loading follows this priority order (higher priority overrides lower priority):

1. Command-line arguments (highest priority)
2. Environment variables
3. External configuration files (`/app/config/`)
4. Environment-specific configuration files (`application-{profile}.yml`)
5. Module configuration files (`config/{module}/*.yml`)
6. Base configuration modules (lowest priority)

---

## Server Configuration

### File: `config/common/server.yml`

```yaml
server:
  port: 8080                    # Service port
  compression:
    enabled: true               # Enable response compression
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # Enable HTTP/2
```

### Advanced Server Configuration

```yaml
server:
  netty:
    connection-timeout: 45s     # Netty connection timeout
    h2c-max-content-length: 0   # H2C maximum content length
```

---

## WebClient Configuration

### File: `config/common/webclient.yml`

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

---

## Logging Configuration

### File: `config/common/logging.yml`

```yaml
logging:
  level:
    root: INFO
    org.unreal.modelrouter: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/jairouter.log
    max-size: 10MB
    max-history: 30
```

---

## Authentication Configuration

### JWT Configuration

#### File: `config/auth/jwt.yml`

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

### API Key Configuration

#### File: `config/auth/api-key.yml`

```yaml
jairouter:
  security:
    api-key:
      enabled: true                     # Enable API key authentication
      header-name: "X-API-Key"          # API key header name
      hash-algorithm: "SHA-256"         # Key hashing algorithm
```

---

## Router Configuration

### Adapter Configuration

#### File: `config/router/adapter.yml`

```yaml
jairouter:
  adapter:
    default-adapter: "ollama"           # Default adapter type
    connect-timeout: 10000              # Connection timeout (ms)
    read-timeout: 60000                 # Read timeout (ms)
```

### Load Balancer Configuration

#### File: `config/router/loadbalancer.yml`

```yaml
jairouter:
  loadbalancer:
    default-strategy: "random"          # Default: random, round_robin, weighted
    health-check-interval: 30000        # Health check interval (ms)
```

### Rate Limiting Configuration

#### File: `config/router/ratelimit.yml`

```yaml
jairouter:
  ratelimit:
    enabled: true
    default-algorithm: "token_bucket"   # token_bucket, leaky_bucket
    default-capacity: 100               # Default bucket capacity
    default-refill-rate: 10             # Tokens per second
```

### Circuit Breaker Configuration

#### File: `config/router/circuitbreaker.yml`

```yaml
jairouter:
  circuitbreaker:
    enabled: true
    failure-threshold: 5                # Failures to trigger open
    success-threshold: 3                # Successes to close
    timeout: 30000                      # Open state timeout (ms)
```

---

## Monitoring Configuration

### File: `config/base/monitoring-base.yml`

```yaml
jairouter:
  monitoring:
    enabled: true
    prefix: "jairouter"                 # Metric prefix
    collection-interval: "PT30S"        # Collection interval
    enabled-categories:
      - request
      - system
      - custom
```

---

## Tracing Configuration

### File: `config/tracing/tracing-base.yml`

```yaml
jairouter:
  tracing:
    enabled: false
    sampling-rate: 1.0                  # Sampling rate (0.0 - 1.0)
    export-enabled: false
    exporter-type: "otlp"               # otlp, jaeger, zipkin
    endpoint: "http://localhost:4317"
```

---

## Persistence Configuration

### File: `config/persistence/state-persistence-base.yml`

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

jairouter:
  security:
    jwt:
      enabled: false                    # Disable JWT in dev
  monitoring:
    enabled: false                      # Disable monitoring in dev

logging:
  level:
    root: DEBUG
```

### Production (`application-prod.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: prod

jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"           # Must be set via env var
  monitoring:
    enabled: true

logging:
  level:
    root: INFO
```

---

## External Configuration (Docker)

When deploying with Docker, you can mount external configuration files:

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

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing secret | (required in prod) |
| `INITIAL_ADMIN_PASSWORD` | Initial admin password | (required in prod) |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `REDIS_PASSWORD` | Redis password | (empty) |
| `SERVER_PORT` | Server port | 8080 |

---

## Configuration Best Practices

1. **Use environment variables** for sensitive values (secrets, passwords)
2. **Use external configuration** for Docker deployments
3. **Use profile-specific configuration** for different environments
4. **Keep module configurations** focused and independent
5. **Document custom configurations** in your deployment guide

---

*Last updated: 2026-05-21*

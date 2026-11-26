# JWT Token Persistence Configuration Guide

<!-- Version Information -->
> **Document Version**: 2.9.5  
> **Last Updated**: 2026-06-09  
> **Configuration Path**: `src/main/resources/config/security/persistence-base.yml`  
> **Author**: JAiRouter Team
<!-- /Version Information -->

## Overview

JWT Token Persistence provides comprehensive token lifecycle management, including persistent storage, blacklist management, automatic cleanup, and security auditing. This guide covers all configuration options for the JWT persistence system.

## Configuration Structure

JWT persistence configuration follows the modular configuration approach used throughout JAiRouter:

```yaml
# Main application configuration
spring:
  config:
    import:
      - classpath:config/security/security-base.yml
      - classpath:config/security/persistence-base.yml  # JWT persistence config
      - classpath:config/monitor/persistence-monitoring-base.yml
```

## Core Configuration

### Basic Persistence Configuration

```yaml
jairouter:
  security:
    jwt:
      # Token persistence configuration
      persistence:
        enabled: false  # Default: disabled, enable per environment

        # Composite storage strategy (Redis + StoreManager)
        composite:
          enabled: true  # Enable composite storage (Redis + StoreManager)

        # Data synchronization
        sync:
          enabled: true  # Enable data synchronization

        # Startup recovery
        startup-recovery:
          enabled: true  # Enable data recovery on startup

        # Cleanup configuration
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # Cron expression: daily at 2 AM
          retention-days: 30       # Keep tokens for 30 days
          batch-size: 1000        # Process 1000 tokens per batch

        # Redis storage: only a switch. Connection settings come from spring.data.redis.*
        # (or the REDIS_HOST / REDIS_PORT / REDIS_PASSWORD environment variables).
        redis:
          enabled: false          # Default: disabled, enable when using Redis
```

> **Only the keys above are read.** The only storage implementation is JPA (H2 is just the
> database JPA uses); `redis` / `memory` as standalone storage backends were never implemented.
> Keys such as `primary-storage`, `fallback-storage`, `memory.*`, `redis.key-prefix` /
> `default-ttl` / `connection-timeout` / `retry-attempts` / `serialization-format` are not
> configurable today. Compare with `src/main/resources/config/security/persistence-base.yml`.


### Blacklist Configuration

```yaml
jairouter:
  security:
    jwt:
      # Blacklist persistence configuration
      blacklist:
        persistence:
          enabled: false          # Default: disabled

        # Composite storage strategy (Redis + StoreManager)
        composite:
          enabled: true  # Enable composite storage (Redis + StoreManager)

        # Redis blacklist: only a switch; connection settings come from spring.data.redis.*
        redis:
          enabled: false
```

### Security Audit Configuration

```yaml
jairouter:
  security:
    # Enhanced audit configuration
    audit:
      enabled: true
      log-level: "INFO"
      include-request-body: false
      include-response-body: false
      retention-days: 90
      
      
      
      
```

## Environment-Specific Configuration

### Development Environment

```yaml
# application-dev.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        composite:
          enabled: true
        cleanup:
          schedule: "0 */5 * * * ?"  # Every 5 minutes for testing
          retention-days: 1          # Short retention for development
      blacklist:
        persistence:
          enabled: true
    
    # Development audit configuration
    audit:
      retention-days: 7          # Short retention for development
```

### Production Environment

```yaml
# application-prod.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        composite:
          enabled: true
        redis:
          enabled: true   # Use Redis as the shared store in production
      blacklist:
        persistence:
          enabled: true
        redis:
          enabled: true
    
    # Production audit configuration
    audit:
      enabled: true
      retention-days: 180   # Longer retention for production

# Monitoring configuration (prefix is jairouter.security.monitoring, not jairouter.monitoring)
jairouter:
  security:
    monitoring:
      jwt-persistence:
        enabled: true
```

### Staging Environment

```yaml
# application-staging.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        redis:
          enabled: true
        cleanup:
          retention-days: 7      # Shorter retention for staging
      blacklist:
        persistence:
          enabled: true
        redis:
          enabled: true
    
    # Staging audit configuration
    audit:
      enabled: true
      retention-days: 30
```

## Monitoring Configuration

### Metrics Configuration

```yaml
# config/monitor/persistence-monitoring-base.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: jairouter
    export:
      prometheus:
        enabled: true

# Custom monitoring (prefix is jairouter.security.monitoring)
jairouter:
  security:
    monitoring:
      jwt-persistence:
        enabled: false  # Default: disabled, enable per environment
        metrics:
          token-operations:
            histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
          blacklist-operations:
            histogram-buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1.0]
          storage-health:
            include-storages: [redis, memory, composite]
        health-checks:
          enabled: true
          check-interval: 60  # seconds
```

> There is no `jwt-tokens` / `jwt-blacklist` actuator endpoint (the codebase has no
> `@Endpoint` for them) and no `management.health.jwt-persistence` property. Metrics and
> health are exported through Micrometer under `/actuator/prometheus` and `/actuator/health`,
> which require `ROLE_ADMIN` (see `/actuator/**` in `SecurityConfiguration`).

### Health Check Configuration

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
      show-components: always
```

The `jwtRedisHealthIndicator` is contributed automatically, and only when **both**
`jairouter.security.jwt.persistence.redis.enabled` and
`jairouter.security.jwt.blacklist.persistence.redis.enabled` are `true` — with either one
missing the bean is not created at all.

## Redis Configuration

### Basic Redis Setup

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 2000ms
```

### Redis Cluster Configuration

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - ${REDIS_NODE1:localhost:7001}
          - ${REDIS_NODE2:localhost:7002}
          - ${REDIS_NODE3:localhost:7003}
        max-redirects: 3
      lettuce:
        cluster:
          refresh:
            adaptive: true
            period: 30s
```

### Redis Sentinel Configuration

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - ${REDIS_SENTINEL1:localhost:26379}
          - ${REDIS_SENTINEL2:localhost:26380}
          - ${REDIS_SENTINEL3:localhost:26381}
```

## Security Configuration

### Key Security Settings

```yaml
jairouter:
  security:
    jwt:
      persistence:
        composite:
          enabled: true   # Keep the shared store authoritative
        redis:
          enabled: true

spring:
  data:
    redis:
      password: ${REDIS_PASSWORD}   # Never hardcode; inject from the environment
      ssl:
        enabled: true               # Enable for cross-network deployments
```

> Redis key naming and (de)serialization are handled internally. `key-prefix`,
> `serialization-format` and `memory.*` are **not** configurable properties today.

### Access Control

```yaml
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,metrics,prometheus
```

> Management endpoints are protected by Spring Security, not by `management.security.*`:
> `SecurityConfiguration` requires `ROLE_ADMIN` for `/actuator/**`, while
> `/actuator/health`, `/actuator/health/**`, `/actuator/info` and `/actuator/prometheus`
> stay anonymous. `management.security.enabled` / `management.security.roles` are Spring
> Boot 1.x keys and have no effect here.

## Performance Tuning

### Storage Footprint

```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          batch-size: 2000        # Larger batches for better throughput
          retention-days: 15      # Shorter retention for high-volume systems
```

### Redis Optimization

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50    # More connections for high concurrency
          max-idle: 20
          min-idle: 10
```

### Cleanup Optimization

```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          schedule: "0 */30 * * * ?"  # More frequent cleanup
          batch-size: 5000            # Larger batches
          retention-days: 7           # Shorter retention
```

## Troubleshooting Configuration

### Debug Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.auth.security.jwt: DEBUG
    org.unreal.modelrouter.auth.audit: DEBUG
    org.unreal.modelrouter.auth.security.persistence: DEBUG
    org.springframework.data.redis: DEBUG
```

### Health Check Configuration

```yaml
management:
  endpoint:
    health:
      show-details: always  # Show detailed health information
      show-components: always
```

To make the JWT Redis health indicator report, enable the Redis-backed stores
(`jairouter.security.jwt.persistence.redis.enabled=true` **and**
`jairouter.security.jwt.blacklist.persistence.redis.enabled=true`).

### Metrics for Troubleshooting

```yaml
jairouter:
  security:
    monitoring:
      jwt-persistence:
        enabled: true
        health-checks:
          enabled: true
          check-interval: 10  # More frequent health checks (seconds)
```

## Configuration Validation

### Required Environment Variables

For production deployment, ensure these environment variables are set:

```bash
# JWT Configuration
export JWT_SECRET="your-production-jwt-secret-key"
export JWT_EXPIRATION_MINUTES=15

# Redis Configuration
export REDIS_HOST="your-redis-host"
export REDIS_PORT=6379
export REDIS_PASSWORD="your-redis-password"
```

> `JWT_REFRESH_EXPIRATION_DAYS`, `SECURITY_AUDIT_ENABLED` and `SECURITY_AUDIT_RETENTION_DAYS`
> are **not** read by the code — no environment variables with those names are referenced
> anywhere. The refresh-token window is the plain config value
> `jairouter.security.jwt.refresh-expiration-days` (defaults to 1 day), and audit settings
> live under `jairouter.security.audit.*` in `config/security/security-base.yml`.

### Configuration Validation Script

Create a validation script to check configuration:

```bash
#!/bin/bash
# validate-jwt-persistence-config.sh

echo "Validating JWT Persistence Configuration..."

# Check required environment variables
required_vars=("JWT_SECRET" "REDIS_HOST")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "ERROR: Required environment variable $var is not set"
        exit 1
    fi
done

# Check Redis connectivity
if ! redis-cli -h "$REDIS_HOST" -p "${REDIS_PORT:-6379}" ping > /dev/null 2>&1; then
    echo "WARNING: Cannot connect to Redis at $REDIS_HOST:${REDIS_PORT:-6379}"
fi

echo "Configuration validation completed"
```

## Migration Guide

### Enabling Persistence on Existing System

1. **Backup Current Configuration**
```bash
cp application.yml application.yml.backup
```

2. **Update Configuration Gradually**
```yaml
# Start with the JPA store only
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
```

3. **Enable Redis After Testing**
```yaml
# Move token and blacklist storage to the shared Redis store
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        redis:
          enabled: true
      blacklist:
        persistence:
          enabled: true
        redis:
          enabled: true
```

4. **Enable Full Auditing**
```yaml
# Enable comprehensive auditing
jairouter:
  security:
    audit:
      enabled: true
```

## Best Practices

### Security Best Practices

1. **Never Log Sensitive Data**
```yaml
jairouter:
  security:
    audit:
      include-request-body: false
      include-response-body: false
```

2. **Use Strong Redis Security**
```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD}  # Always use password
      ssl:
        enabled: true              # Use SSL in production
```

3. **Bound the Token Store**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          retention-days: 30     # Expired rows are removed, not kept forever
          batch-size: 1000
```

### Performance Best Practices

1. **Optimize Cleanup Schedule**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          schedule: "0 0 2 * * ?"  # Run during low-traffic hours
          batch-size: 1000         # Balance memory and performance
```

2. **Match Retention to Token Lifetime**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          retention-days: 30  # Keep at least as long as the longest token lifetime
```

3. **Monitor Performance**
```yaml
jairouter:
  security:
    monitoring:
      jwt-persistence:
        enabled: true
        health-checks:
          check-interval: 30  # Regular health monitoring (seconds)
```

## Related Documentation

- [JWT Authentication Configuration](../security/jwt-authentication.md)
- [Security Monitoring](../monitoring/index.md)
- [Redis Configuration](../deployment/production.md#redis-setup)
- [Performance Tuning](../troubleshooting/performance.md)
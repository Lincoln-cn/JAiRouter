# JWT Token Persistence Configuration Guide

<!-- Version Information -->
> **Document Version**: 1.0.0  
> **Last Updated**: 2025-01-15  
> **Git Commit**: -  
> **Author**: System
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
      - classpath:config/monitoring/persistence-monitoring-base.yml
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
        primary-storage: redis    # Options: redis, memory
        fallback-storage: memory  # Options: memory
        
        # Cleanup configuration
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # Cron expression: daily at 2 AM
          retention-days: 30       # Keep tokens for 30 days
          batch-size: 1000        # Process 1000 tokens per batch
        
        # Memory storage configuration
        memory:
          max-tokens: 50000       # Maximum tokens in memory
          cleanup-threshold: 0.8  # Cleanup when 80% full
          lru-enabled: true       # Use LRU eviction policy
          
        # Redis storage configuration
        redis:
          key-prefix: "jwt:"      # Redis key prefix
          default-ttl: 3600       # Default TTL in seconds
          connection-timeout: 5000 # Connection timeout in ms
          retry-attempts: 3       # Retry attempts for failed operations
          serialization-format: "json"  # Options: json, binary
```

### Blacklist Configuration

```yaml
jairouter:
  security:
    jwt:
      # Blacklist persistence configuration
      blacklist:
        persistence:
          enabled: false          # Default: disabled
          primary-storage: redis  # Options: redis, memory
          fallback-storage: memory
          max-memory-size: 10000  # Maximum blacklist entries in memory
          cleanup-interval: 3600  # Cleanup interval in seconds (1 hour)
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
      
      # JWT operations auditing
      jwt-operations:
        enabled: true
        log-token-details: false  # Security: don't log full tokens
        log-user-agent: true
        log-ip-address: true
      
      # API Key operations auditing
      api-key-operations:
        enabled: true
        log-key-details: false   # Security: don't log full keys
        log-usage-patterns: true
        log-ip-address: true
      
      # Security events auditing
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 10
          token-revoke-per-minute: 5
          api-key-usage-per-minute: 100
      
      # Audit storage configuration
      storage:
        type: "file"              # Options: file, database
        file-path: "logs/security-audit.log"
        rotation:
          max-file-size: "100MB"
          max-files: 30
        # Optional: database storage
        database:
          enabled: false
          table-name: "security_audit_events"
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
        primary-storage: memory  # Use memory for development
        fallback-storage: memory
        cleanup:
          schedule: "0 */5 * * * ?"  # Every 5 minutes for testing
          retention-days: 1          # Short retention for development
      blacklist:
        persistence:
          enabled: true
          primary-storage: memory
    
    # Development audit configuration
    audit:
      jwt-operations:
        enabled: true
        log-token-details: true  # OK to log details in development
      api-key-operations:
        enabled: true
        log-key-details: true
      retention-days: 7          # Short retention for development

# Enable debug endpoints
management:
  endpoint:
    jwt-tokens:
      enabled: true
    jwt-blacklist:
      enabled: true
```

### Production Environment

```yaml
# application-prod.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: redis   # Use Redis for production
        fallback-storage: memory
        redis:
          connection-timeout: 3000
          retry-attempts: 5
      blacklist:
        persistence:
          enabled: true
          primary-storage: redis
          max-memory-size: 50000
    
    # Production audit configuration
    audit:
      enabled: true
      jwt-operations:
        enabled: true
        log-token-details: false  # Security: no token details in production
      api-key-operations:
        enabled: true
        log-key-details: false
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 20  # Higher threshold for production
          token-revoke-per-minute: 10
          api-key-usage-per-minute: 500
      storage:
        type: "file"
        # Consider database storage for production
        database:
          enabled: false  # Enable if needed
      retention-days: 180   # Longer retention for production

# Monitoring configuration
jairouter:
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
        primary-storage: redis
        fallback-storage: memory
        cleanup:
          retention-days: 7      # Shorter retention for staging
      blacklist:
        persistence:
          enabled: true
          primary-storage: redis
    
    # Staging audit configuration
    audit:
      enabled: true
      jwt-operations:
        enabled: true
        log-token-details: false
      api-key-operations:
        enabled: true
        log-key-details: false
      security-events:
        enabled: true
        suspicious-activity-detection: true
      retention-days: 30

# Enable debug endpoints for staging
management:
  endpoint:
    jwt-tokens:
      enabled: true
    jwt-blacklist:
      enabled: true
```

## Monitoring Configuration

### Metrics Configuration

```yaml
# config/monitoring/persistence-monitoring-base.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,jwt-tokens,jwt-blacklist
  metrics:
    tags:
      application: jairouter
    export:
      prometheus:
        enabled: true
  endpoint:
    jwt-tokens:
      enabled: false  # Default: disabled, enable per environment
    jwt-blacklist:
      enabled: false

# Custom monitoring metrics
jairouter:
  monitoring:
    jwt-persistence:
      enabled: false  # Default: disabled, enable per environment
      metrics:
        token-operations:
          enabled: true
          histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
        blacklist-operations:
          enabled: true
          histogram-buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1.0]
        storage-health:
          enabled: true
          check-interval: 30  # seconds
```

### Health Check Configuration

```yaml
management:
  health:
    jwt-persistence:
      enabled: true
    redis:
      enabled: true
  endpoint:
    health:
      show-details: when-authorized
      show-components: always
```

## Redis Configuration

### Basic Redis Setup

```yaml
spring:
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
        # Security settings
        redis:
          key-prefix: "jwt:"      # Namespace isolation
          serialization-format: "json"  # Avoid binary for security
        memory:
          lru-enabled: true       # Prevent memory exhaustion
          max-tokens: 50000       # Hard limit
    
    audit:
      # Security audit settings
      jwt-operations:
        log-token-details: false  # Never log full tokens
      api-key-operations:
        log-key-details: false   # Never log full keys
      security-events:
        enabled: true            # Always enable security events
        suspicious-activity-detection: true
```

### Access Control

```yaml
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,metrics
        exclude: jwt-tokens,jwt-blacklist  # Restrict sensitive endpoints
  endpoint:
    jwt-tokens:
      enabled: false  # Only enable in development/staging
    jwt-blacklist:
      enabled: false
  security:
    enabled: true
    roles: ADMIN  # Require admin role for management endpoints
```

## Performance Tuning

### Memory Optimization

```yaml
jairouter:
  security:
    jwt:
      persistence:
        memory:
          max-tokens: 100000      # Adjust based on available memory
          cleanup-threshold: 0.75 # Cleanup earlier to prevent OOM
          lru-enabled: true       # Enable LRU eviction
        cleanup:
          batch-size: 2000        # Larger batches for better performance
          retention-days: 15      # Shorter retention for high-volume systems
```

### Redis Optimization

```yaml
jairouter:
  security:
    jwt:
      persistence:
        redis:
          connection-timeout: 2000  # Faster timeout for high-performance
          retry-attempts: 2         # Fewer retries for faster failover
          serialization-format: "binary"  # Better performance than JSON
          default-ttl: 1800        # Shorter TTL for high-volume systems

spring:
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
      blacklist:
        persistence:
          cleanup-interval: 1800      # More frequent blacklist cleanup
```

## Troubleshooting Configuration

### Debug Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
    org.unreal.modelrouter.security.audit: DEBUG
    org.unreal.modelrouter.security.persistence: DEBUG
    org.springframework.data.redis: DEBUG
```

### Health Check Configuration

```yaml
management:
  health:
    jwt-persistence:
      enabled: true
    redis:
      enabled: true
  endpoint:
    health:
      show-details: always  # Show detailed health information
      show-components: always
```

### Metrics for Troubleshooting

```yaml
jairouter:
  monitoring:
    jwt-persistence:
      enabled: true
      metrics:
        token-operations:
          enabled: true
        blacklist-operations:
          enabled: true
        storage-health:
          enabled: true
          check-interval: 10  # More frequent health checks
```

## Configuration Validation

### Required Environment Variables

For production deployment, ensure these environment variables are set:

```bash
# JWT Configuration
export JWT_SECRET="your-production-jwt-secret-key"
export JWT_EXPIRATION_MINUTES=15
export JWT_REFRESH_EXPIRATION_DAYS=30

# Redis Configuration
export REDIS_HOST="your-redis-host"
export REDIS_PORT=6379
export REDIS_PASSWORD="your-redis-password"

# Security Configuration
export SECURITY_AUDIT_ENABLED=true
export SECURITY_AUDIT_RETENTION_DAYS=180
```

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
# Start with memory-only persistence
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: memory
        fallback-storage: memory
```

3. **Enable Redis After Testing**
```yaml
# Upgrade to Redis with memory fallback
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: redis
        fallback-storage: memory
```

4. **Enable Full Auditing**
```yaml
# Enable comprehensive auditing
jairouter:
  security:
    audit:
      enabled: true
      jwt-operations:
        enabled: true
      api-key-operations:
        enabled: true
```

## Best Practices

### Security Best Practices

1. **Never Log Sensitive Data**
```yaml
jairouter:
  security:
    audit:
      jwt-operations:
        log-token-details: false  # Never log full tokens
      api-key-operations:
        log-key-details: false   # Never log full keys
```

2. **Use Strong Redis Security**
```yaml
spring:
  redis:
    password: ${REDIS_PASSWORD}  # Always use password
    ssl: true                   # Use SSL in production
```

3. **Limit Memory Usage**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        memory:
          max-tokens: 50000       # Set reasonable limits
          cleanup-threshold: 0.8  # Cleanup before exhaustion
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

2. **Use Appropriate TTL**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        redis:
          default-ttl: 3600  # Match or exceed token expiration
```

3. **Monitor Performance**
```yaml
jairouter:
  monitoring:
    jwt-persistence:
      enabled: true
      metrics:
        storage-health:
          check-interval: 30  # Regular health monitoring
```

## Related Documentation

- [JWT Authentication Configuration](../security/jwt-authentication.md)
- [Security Monitoring](../monitoring/index.md)
- [Redis Configuration](../deployment/production.md#redis-setup)
- [Performance Tuning](../troubleshooting/performance.md)
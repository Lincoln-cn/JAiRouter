﻿# Application Configuration

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->

This document details the basic application configuration of JAiRouter, including server configuration, WebClient configuration, monitoring configuration, and more.

## Configuration File Locations

- **Main Configuration File**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **Environment Configuration**: `application-{profile}.yml`
- **External Configuration**: `config/application.yml` (optional)
- **Modular Configuration**: `config/{module}/*.yml`

## Modular Configuration Explanation

JAiRouter adopts a modular configuration management approach, splitting complex configurations into multiple independent configuration files by function. This design improves configuration maintainability, readability, and reusability.

### Configuration Structure

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/base/server-base.yml
      - classpath:config/base/model-services-base.yml
      - classpath:config/base/monitoring-base.yml
      - classpath:config/tracing/tracing-base.yml
      - classpath:config/security/security-base.yml
      - classpath:config/monitoring/slow-query-alerts.yml
      - classpath:config/monitoring/error-tracking.yml
```

### Configuration Module Categories

1. **Base Configuration Modules** (`config/base/`)
   - [server-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/base/server-base.yml) - Server base configuration
   - [model-services-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/base/model-services-base.yml) - Model services configuration
   - [monitoring-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/base/monitoring-base.yml) - Monitoring base configuration

2. **Feature Configuration Modules** (`config/{feature}/`)
   - [tracing/tracing-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/tracing/tracing-base.yml) - Tracing feature configuration
   - [security/security-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/security/security-base.yml) - Security feature configuration
   - [monitoring/slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml) - Slow query alert configuration
   - [monitoring/error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml) - Error tracking configuration

3. **Environment Configuration Files** (`application-{profile}.yml`)
   - [application-dev.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml) - Development environment configuration
   - [application-staging.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-staging.yml) - Staging environment configuration
   - [application-prod.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-prod.yml) - Production environment configuration
   - [application-legacy.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-legacy.yml) - Legacy compatibility configuration

### Configuration Priority

Configuration loading follows this priority order (higher priority overrides lower priority):

1. Base configuration modules (lowest priority)
2. Feature configuration modules
3. Environment-specific configuration files
4. External configuration files
5. Environment variables
6. Command-line arguments (highest priority)

## Server Configuration

### Basic Server Configuration

```yaml
server:
  port: 8080                    # Service port
  servlet:
    context-path: /             # Application context path
  compression:
    enabled: true               # Enable response compression
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # Enable HTTP/2
```

### Advanced Server Configuration

```yaml
server:
  tomcat:
    threads:
      max: 200                  # Maximum thread count
      min-spare: 10             # Minimum spare threads
    connection-timeout: 20000   # Connection timeout (milliseconds)
    max-connections: 8192       # Maximum connections
    accept-count: 100           # Waiting queue length
  netty:
    connection-timeout: 45s     # Netty connection timeout
    h2c-max-content-length: 0   # H2C maximum content length
```

## WebClient Configuration

JAiRouter uses WebClient for backend service calls, supporting detailed connection configurations:

```yaml
webclient:
  connection-timeout: 10s       # Connection timeout
  read-timeout: 30s             # Read timeout
  write-timeout: 30s            # Write timeout
  max-in-memory-size: 10MB      # Maximum in-memory buffer size
  
  # Connection pool configuration
  connection-pool:
    max-connections: 500        # Maximum connections
    max-idle-time: 20s          # Maximum idle time
    max-life-time: 60s          # Connection maximum lifetime
    pending-acquire-timeout: 45s # Connection acquisition timeout
    evict-in-background: 120s   # Background eviction interval
  
  # SSL configuration
  ssl:
    enabled: false              # Enable SSL
    trust-all: false            # Trust all certificates
    key-store: classpath:keystore.p12
    key-store-password: password
    trust-store: classpath:truststore.p12
    trust-store-password: password
```

### WebClient Performance Tuning

```yaml
webclient:
  # Optimized configuration for high-concurrency scenarios
  connection-pool:
    max-connections: 1000       # Increase maximum connections
    max-idle-time: 30s          # Appropriately increase idle time
    pending-acquire-timeout: 60s # Increase connection acquisition timeout
  
  # Optimization for large file transfers
  max-in-memory-size: 50MB      # Increase memory buffer
  read-timeout: 120s            # Increase read timeout
  write-timeout: 120s           # Increase write timeout
```

## Monitoring Configuration

### Basic Monitoring Configuration

```yaml
monitoring:
  metrics:
    enabled: true               # Enable metric collection
    prefix: "jairouter"         # Metric prefix
    collection-interval: 10s    # Collection interval
    
    # Enabled metric categories
    enabled-categories:
      - system                  # System metrics
      - business                # Business metrics
      - infrastructure          # Infrastructure metrics
    
    # Custom tags
    custom-tags:
      environment: "production"
      version: "1.0.0"
      datacenter: "us-west-1"
```

### Advanced Monitoring Configuration

```yaml
monitoring:
  metrics:
    # Metric sampling configuration
    sampling:
      request-metrics: 1.0      # Request metric sampling rate (0.0-1.0)
      backend-metrics: 1.0      # Backend call metric sampling rate
      infrastructure-metrics: 0.1 # Infrastructure metric sampling rate
    
    # Performance optimization configuration
    performance:
      async-processing: true    # Asynchronous metric processing
      batch-size: 100           # Batch processing size
      buffer-size: 1000         # Buffer size
      flush-interval: 5s        # Flush interval
    
    # Metric filtering configuration
    filters:
      exclude-paths:            # Excluded paths
        - "/actuator/health"
        - "/favicon.ico"
      include-status-codes:     # Included status codes
        - 2xx
        - 4xx
        - 5xx
```

## Spring Actuator Configuration

### Basic Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,jairouter-metrics
      base-path: /actuator      # Endpoint base path
    enabled-by-default: true    # Enable all endpoints by default
  
  endpoint:
    health:
      show-details: always      # Show health check details
      show-components: always   # Show component status
      cache:
        time-to-live: 10s       # Health check cache time
    
    info:
      enabled: true             # Enable info endpoint
    
    metrics:
      enabled: true             # Enable metrics endpoint
    
    prometheus:
      enabled: true             # Enable Prometheus endpoint
      cache:
        time-to-live: 10s       # Prometheus metric cache time
```

### Security Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info    # Expose only basic endpoints
  
  endpoint:
    health:
      show-details: when-authorized # Details visible only to authorized users
  
  security:
    enabled: true               # Enable security control
    roles: ADMIN,ACTUATOR       # Allowed roles
```

## Prometheus Integration Configuration

```
management:
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
  endpoint:
    prometheus:
      enabled: true
```

## Usage Guide

### Starting Different Environments

```bash
# Start development environment
java -jar app.jar --spring.profiles.active=dev

# Start staging environment
java -jar app.jar --spring.profiles.active=staging

# Start production environment
java -jar app.jar --spring.profiles.active=prod

# Start legacy compatibility mode
java -jar app.jar --spring.profiles.active=legacy
```

### Modifying Configuration

1. **Base Configuration Modification**: Edit corresponding files in the [config/base/](file://d:/IdeaProjects/model-router/src/main/resources/config/base/) directory
2. **Feature Enable/Disable**: Edit corresponding feature configuration files
3. **Environment Differences**: Edit corresponding environment configuration files
4. **Sensitive Configuration**: Use environment variables for injection

### Configuration Best Practices

1. **Modularization Principle**: Split configurations into independent modules by function
2. **Environment Separation**: Use environment configuration files to override base configurations
3. **Sensitive Information Protection**: Inject sensitive configurations via environment variables
4. **Version Control**: Include configuration files in version control
5. **Documentation Synchronization**: Keep configurations consistent with documentation

## Troubleshooting

### Configuration Not Taking Effect

1. Check if configuration file paths are correct
2. Confirm if environment configuration files are loaded correctly
3. Verify configuration priority order
4. Check for syntax errors

### Configuration Conflicts

1. Understand configuration priority rules
2. Check for duplicate configuration items
3. Confirm if environment variables override expected configurations
4. Use `--debug` parameter to view configuration loading process

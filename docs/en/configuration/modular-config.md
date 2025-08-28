# Modular Configuration Guide

## Overview

JAiRouter adopts a modular configuration management approach, splitting complex configurations into multiple independent configuration files by function. This design improves configuration maintainability, readability, and reusability.

## Configuration Structure

### Main Configuration File

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

## Configuration Priority

Configuration loading follows this priority order (higher priority overrides lower priority):

1. Base configuration modules (lowest priority)
2. Feature configuration modules
3. Environment-specific configuration files
4. External configuration files
5. Environment variables
6. Command-line arguments (highest priority)

## Base Configuration Modules Details

### Server Base Configuration (server-base.yml)

Contains server port, storage path, WebClient base configuration, etc.:

```yaml
server:
  port: 8080

store:
  type: file
  path: "config/"

webclient:
  connection-timeout: 10s
  read-timeout: 30s
  write-timeout: 30s
  max-in-memory-size: 10MB
```

### Model Services Base Configuration (model-services-base.yml)

Contains core business configurations such as load balancing, rate limiting, and circuit breaking:

```yaml
model:
  load-balance:
    type: random
  adapter: gpustack
  rate-limit:
    enabled: true
    algorithm: "token-bucket"
  circuit-breaker:
    enabled: true
    failureThreshold: 5
```

### Monitoring Base Configuration (monitoring-base.yml)

Contains observability configurations such as monitoring metrics and management endpoints:

```yaml
monitoring:
  enabled: true
  metrics:
    enabled: true
    prefix: "jairouter"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,jairouter-metrics
```

## Feature Configuration Modules Details

### Tracing Configuration (tracing-base.yml)

Complete configuration for distributed tracing:

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    sampling:
      strategy: "parent_based_traceid_ratio"
      ratio: 1.0
    exporter:
      type: "logging"
```

### Security Configuration (security-base.yml)

Base configuration template for security features:

```yaml
jairouter:
  security:
    enabled: false  # Disabled by default, enabled as needed per environment
    api-key:
      enabled: false
      header-name: "X-API-Key"
    jwt:
      enabled: false
      secret: ""
```

### Slow Query Alert Configuration (slow-query-alerts.yml)

Performance monitoring and alert configuration:

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        enabled: true
        min-interval-ms: 300000
```

### Error Tracking Configuration (error-tracking.yml)

Error monitoring and analysis configuration:

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: false  # Disabled by default, enabled as needed per environment
      aggregation-window-minutes: 5
      sanitization:
        enabled: true
```

## Environment Configuration Files Details

### Development Environment (application-dev.yml)

Configuration optimized for development and testing:

```yaml
# Enable detailed logging
logging:
  level:
    org.unreal.modelrouter: DEBUG

# Enable Swagger documentation
springdoc:
  swagger-ui:
    path: /swagger-ui.html

jairouter:
  security:
    enabled: true  # Enable security features for testing in development environment
```

### Staging Environment (application-staging.yml)

Configuration close to production environment:

```yaml
# Detailed logging configuration
logging:
  level:
    root: INFO
    org.unreal.modelrouter.security: DEBUG

jairouter:
  security:
    enabled: true  # Enable security features for testing in staging environment
```

### Production Environment (application-prod.yml)

Configuration optimized for production environment:

```yaml
# Minimize log output
logging:
  level:
    root: WARN

jairouter:
  security:
    enabled: false  # Security features disabled by default in production, explicitly enable as needed
```

### Legacy Compatibility Environment (application-legacy.yml)

Configuration to ensure smooth upgrade of legacy deployments:

```yaml
jairouter:
  security:
    enabled: false  # All security features disabled by default in legacy compatibility mode
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
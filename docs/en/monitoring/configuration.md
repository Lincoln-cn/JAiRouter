# Monitoring Configuration Reference

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This document provides a complete configuration reference for the JAiRouter monitoring system, including detailed descriptions, default values, and usage examples for all configuration options.

## Configuration File Structure

### Main Configuration File

JAiRouter monitoring configuration is primarily defined in [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml):

```yaml
# Monitoring configuration
monitoring:
  metrics:
    # Basic configuration
    enabled: true
    prefix: "jairouter"
    collection-interval: 10s
    
    # Metric categories
    enabled-categories:
      - system
      - business
      - infrastructure
    
    # Custom tags
    custom-tags:
      environment: "${spring.profiles.active:default}"
      version: "@project.version@"
    
    # Sampling configuration
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
      infrastructure-metrics: 1.0
    
    # Performance configuration
    performance:
      async-processing: true
      batch-size: 500
      buffer-size: 2000
    
    # Memory configuration
    memory:
      cache-size: 10000
      cache-expiry: 5m
    
    # Security configuration
    security:
      data-masking: false
      mask-labels: []

# Spring Actuator configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  
  endpoint:
    health:
      show-details: always
    prometheus:
      cache:
        time-to-live: 10s
  
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
```

## Basic Configuration

### monitoring.metrics.enabled

**Type**: Boolean  
**Default Value**: `true`  
**Description**: Whether to enable monitoring metric collection

```yaml
monitoring:
  metrics:
    enabled: true  # Enable monitoring
    # enabled: false  # Disable monitoring
```

**Environment Variable**: `MONITORING_METRICS_ENABLED`

### monitoring.metrics.prefix

**Type**: String  
**Default Value**: `"jairouter"`  
**Description**: Metric name prefix used to distinguish metrics from different applications

```yaml
monitoring:
  metrics:
    prefix: "jairouter"        # Default prefix
    # prefix: "my-app"         # Custom prefix
    # prefix: ""               # No prefix
```

### monitoring.metrics.collection-interval

**Type**: Duration  
**Default Value**: `10s`  
**Description**: Metric collection interval

```yaml
monitoring:
  metrics:
    collection-interval: 10s   # 10 seconds
    # collection-interval: 5s  # 5 seconds (more frequent)
    # collection-interval: 30s # 30 seconds (less frequent)
```

## Metric Category Configuration

### monitoring.metrics.enabled-categories

**Type**: List<String>  
**Default Value**: `["system", "business", "infrastructure"]`  
**Description**: Enabled metric categories

```yaml
monitoring:
  metrics:
    enabled-categories:
      - system          # System metrics (JVM, HTTP, etc.)
      - business        # Business metrics (model calls, user sessions, etc.)
      - infrastructure  # Infrastructure metrics (load balancing, rate limiting, circuit breaking, etc.)
```

**Available Values**:
- `system`: System metrics such as JVM memory, GC, HTTP requests
- `business`: Business metrics such as model calls, user sessions, business processes
- `infrastructure`: Infrastructure metrics such as load balancing, rate limiting, circuit breaking, health checks

## Custom Tags Configuration

### monitoring.metrics.custom-tags

**Type**: Map<String, String>  
**Default Value**: `{}`  
**Description**: Custom tags added to all metrics

```yaml
monitoring:
  metrics:
    custom-tags:
      environment: "${spring.profiles.active:default}"
      version: "@project.version@"
      region: "us-west-1"
      datacenter: "dc1"
      team: "platform"
```

**Notes**:
- Tag values support Spring expressions and placeholders
- Avoid using high-cardinality tags (such as user ID, IP address)
- It is recommended that the number of tags does not exceed 10

## Sampling Configuration

### monitoring.metrics.sampling

**Type**: Object  
**Description**: Metric sampling rate configuration to control the frequency of metric collection

```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 1.0        # Request metric sampling rate (100%)
      backend-metrics: 1.0        # Backend call metric sampling rate
      infrastructure-metrics: 1.0 # Infrastructure metric sampling rate
      system-metrics: 1.0         # System metric sampling rate
      debug-metrics: 0.1          # Debug metric sampling rate (10%)
```

**Sampling Rate Explanation**:
- `1.0`: 100% sampling, collect all metrics
- `0.5`: 50% sampling, randomly collect half of the metrics
- `0.1`: 10% sampling, randomly collect one-tenth of the metrics
- `0.0`: 0% sampling, do not collect metrics

**Environment-Specific Configuration**:
```yaml
# Development environment - full sampling for debugging
monitoring:
  metrics:
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0

# Production environment - reduce sampling rate to reduce overhead
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
```

## Performance Configuration

### monitoring.metrics.performance

**Type**: Object  
**Description**: Performance-related configuration

```yaml
monitoring:
  metrics:
    performance:
      # Asynchronous processing configuration
      async-processing: true
      async-thread-pool-size: 4
      async-thread-pool-max-size: 8
      async-queue-capacity: 1000
      
      # Batch processing configuration
      batch-size: 500
      batch-timeout: 1s
      
      # Buffer configuration
      buffer-size: 2000
      buffer-flush-interval: 5s
      
      # Processing timeout configuration
      processing-timeout: 5s
```

#### async-processing

**Type**: Boolean  
**Default Value**: `true`  
**Description**: Whether to enable asynchronous metric processing

```yaml
monitoring:
  metrics:
    performance:
      async-processing: true   # Enable asynchronous processing (recommended)
      # async-processing: false # Synchronous processing (for debugging)
```

#### batch-size

**Type**: Integer  
**Default Value**: `500`  
**Description**: Batch processing size, the number of metric events processed at once

```yaml
monitoring:
  metrics:
    performance:
      batch-size: 500    # Default batch size
      # batch-size: 100  # Small batch, low latency
      # batch-size: 1000 # Large batch, high throughput
```

#### buffer-size

**Type**: Integer  
**Default Value**: `2000`  
**Description**: Buffer size, the queue capacity for pending metric events

```yaml
monitoring:
  metrics:
    performance:
      buffer-size: 2000   # Default buffer size
      # buffer-size: 5000 # Large buffer, handle burst traffic
      # buffer-size: 1000 # Small buffer, save memory
```

## Memory Configuration

### monitoring.metrics.memory

**Type**: Object  
**Description**: Memory usage related configuration

```yaml
monitoring:
  metrics:
    memory:
      # Cache configuration
      cache-size: 10000
      cache-expiry: 5m
      cache-cleanup-interval: 1m
      
      # Memory threshold configuration
      memory-threshold: 80
      low-memory-sampling-rate: 0.1
      
      # Object pool configuration
      object-pool-enabled: true
      object-pool-size: 1000
```

#### cache-size

**Type**: Integer  
**Default Value**: `10000`  
**Description**: Metric cache size

#### cache-expiry

**Type**: Duration  
**Default Value**: `5m`  
**Description**: Cache expiration time

#### memory-threshold

**Type**: Integer  
**Default Value**: `80`  
**Description**: Memory usage threshold (percentage), low memory mode is enabled when exceeded

## Security Configuration

### monitoring.metrics.security

**Type**: Object  
**Description**: Security-related configuration

```yaml
monitoring:
  metrics:
    security:
      # Data masking
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key
        - session_id
      
      # IP address masking
      ip-masking: true
      ip-mask-pattern: "xxx.xxx.xxx.xxx"
      
      # Sensitive metric filtering
      sensitive-metrics-filter: true
      filtered-metrics:
        - "*.password.*"
        - "*.secret.*"
        - "*.token.*"
```

#### data-masking

**Type**: Boolean  
**Default Value**: `false`  
**Description**: Whether to enable data masking

#### mask-labels

**Type**: List<String>  
**Default Value**: `[]`  
**Description**: List of tag names that need to be masked

## Spring Actuator Configuration

### management.endpoints.web.exposure.include

**Type**: String  
**Default Value**: `"health,info"`  
**Description**: List of exposed endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
        # include: "*"  # Expose all endpoints (development environment only)
```

### management.endpoint.prometheus.cache.time-to-live

**Type**: Duration  
**Default Value**: `10s`  
**Description**: Prometheus endpoint cache time

```yaml
management:
  endpoint:
    prometheus:
      cache:
        time-to-live: 10s  # 10 second cache
        # time-to-live: 0s # Disable cache
        # time-to-live: 60s # 1 minute cache
```

### management.metrics.export.prometheus

**Type**: Object  
**Description**: Prometheus export configuration

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
        pushgateway:
          enabled: false
          base-url: http://localhost:9091
```

## Environment-Specific Configuration

### Development Environment Configuration

```yaml
# application-dev.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
      infrastructure-metrics: 1.0
    performance:
      async-processing: false  # For easier debugging
      batch-size: 100
    security:
      data-masking: false

management:
  endpoints:
    web:
      exposure:
        include: "*"  # Expose all endpoints in development environment
  endpoint:
    prometheus:
      cache:
        time-to-live: 1s  # Reduce cache time for easier testing
```

### Test Environment Configuration

```yaml
# application-test.yml
monitoring:
  metrics:
    enabled: true
    prefix: "test_jairouter"
    sampling:
      request-metrics: 0.1  # Reduce sampling rate to minimize test interference
      backend-metrics: 0.5
    performance:
      async-processing: true
      batch-size: 50
    memory:
      cache-size: 1000

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

### Production Environment Configuration

```yaml
# application-prod.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
      infrastructure-metrics: 0.1
      system-metrics: 0.5
    performance:
      async-processing: true
      batch-size: 1000
      buffer-size: 5000
    memory:
      cache-size: 20000
      memory-threshold: 85
      low-memory-sampling-rate: 0.01
    security:
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key
      ip-masking: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      cache:
        time-to-live: 30s
  security:
    enabled: true
```

## Dynamic Configuration

### Runtime Configuration Updates

JAiRouter supports runtime dynamic updates of monitoring configurations:

```bash
# Update sampling rate
curl -X POST http://localhost:8080/actuator/monitoring/config \
  -H "Content-Type: application/json" \
  -d '{
    "sampling": {
      "request-metrics": 0.5,
      "backend-metrics": 0.8
    }
  }'

# Enable/disable metric categories
curl -X POST http://localhost:8080/actuator/monitoring/categories \
  -H "Content-Type: application/json" \
  -d '{
    "enabled-categories": ["system", "business"]
  }'

# Update performance configuration
curl -X POST http://localhost:8080/actuator/monitoring/performance \
  -H "Content-Type: application/json" \
  -d '{
    "batch-size": 200,
    "buffer-size": 1000
  }'
```

### Configuration File Hot Reload

Supports updating monitoring configurations through configuration files:

```yaml
# config/monitoring-override.yml
monitoring:
  metrics:
    sampling:
      request-metrics: 0.3
    performance:
      batch-size: 200
```

The system will automatically detect configuration file changes and apply the new configuration.

## Configuration Validation

### Configuration Syntax Validation

```bash
# Validate YAML syntax
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=classpath:/application.yml --spring.profiles.active=test"
```

### Configuration Validity Check

```bash
# Check current configuration
curl http://localhost:8080/actuator/monitoring/config

# Check metric collection status
curl http://localhost:8080/actuator/monitoring/status

# Verify endpoint accessibility
curl http://localhost:8080/actuator/prometheus
```

## Configuration Best Practices

### 1. Environment-Specific Configuration

- **Development Environment**: Enable all metrics for easier debugging
- **Test Environment**: Reduce sampling rate to minimize test interference
- **Production Environment**: Balance performance and monitoring accuracy

### 2. Performance Optimization Configuration

```yaml
# High-performance configuration
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1
    performance:
      async-processing: true
      batch-size: 1000
      buffer-size: 5000
    memory:
      cache-size: 20000
```

### 3. Security Configuration

```yaml
# Security configuration
monitoring:
  metrics:
    security:
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key

management:
  security:
    enabled: true
  server:
    port: 8081
    address: 127.0.0.1
```

### 4. Monitoring Configuration

```yaml
# Monitoring system configuration
monitoring:
  metrics:
    custom-tags:
      monitoring_version: "1.0"
    enabled-categories:
      - system
      - monitoring  # Metrics of the monitoring system itself
```

## Troubleshooting Configuration

### Debug Configuration

```yaml
# Enable debug mode
logging:
  level:
    org.unreal.modelrouter.monitoring: DEBUG
    io.micrometer: DEBUG

monitoring:
  metrics:
    debug:
      enabled: true
      log-metrics: true
      log-interval: 30s
```

### Problem Diagnosis Configuration

```yaml
# Diagnosis configuration
monitoring:
  metrics:
    diagnostics:
      enabled: true
      collect-jvm-metrics: true
      collect-system-metrics: true
      health-check-interval: 10s
```

## Configuration Templates

### Basic Template

```yaml
# Basic monitoring configuration template
monitoring:
  metrics:
    enabled: true
    prefix: "jairouter"
    enabled-categories:
      - system
      - business
      - infrastructure
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
    performance:
      async-processing: true
      batch-size: 500

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      cache:
        time-to-live: 10s
```

### High-Performance Template

```yaml
# High-performance monitoring configuration template
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
      infrastructure-metrics: 0.1
    performance:
      async-processing: true
      batch-size: 1000
      buffer-size: 5000
    memory:
      cache-size: 20000
      memory-threshold: 85
```

### Security Template

```yaml
# Security monitoring configuration template
monitoring:
  metrics:
    enabled: true
    security:
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key
      ip-masking: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  security:
    enabled: true
  server:
    port: 8081
    address: 127.0.0.1
```

## Related Documentation

- [Monitoring Setup Guide](setup.md)
- [Performance Optimization Guide](performance.md)
- [Troubleshooting Guide](troubleshooting.md)
- [Monitoring Metrics Reference](metrics.md)

---

**Tip**: It is recommended to choose an appropriate configuration template based on the actual environment and requirements, and continuously optimize configuration parameters based on system operation conditions.

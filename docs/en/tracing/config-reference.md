# Configuration Reference

This document provides complete configuration reference for JAiRouter's distributed tracing feature.

## Basic Configuration

### Enable Tracing

```yaml
jairouter:
  tracing:
    enabled: true                    # Whether to enable tracing, default: false
    service-name: "jairouter"       # Service name, default: "model-router"
    service-version: "1.0.0"        # Service version, default: "unknown"
```

### Basic Configuration Items

| Configuration Item | Type | Default Value | Description |
|--------|------|---------|------|
| `enabled` | boolean | `false` | Whether to enable tracing |
| `service-name` | string | `"model-router"` | Service name for identifying the tracing source |
| `service-version` | string | `"unknown"` | Service version number |
| `environment` | string | `"development"` | Runtime environment identifier |

## Sampling Configuration

### Ratio Sampling

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 0.1                     # Sampling rate 0.0-1.0, default: 1.0
```

### Rule Sampling

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "rule"
      rules:
        - service: "jairouter"       # Service name matching
          operation: "*"             # Operation name matching (supports wildcards)
          sample-rate: 0.1          # Sampling rate for this rule
          
        - path-pattern: "/api/v1/*"  # URL path pattern matching
          method: "POST"             # HTTP method matching
          sample-rate: 0.5
          
        - header-name: "X-Debug"     # Request header matching
          header-value: "true"
          sample-rate: 1.0           # 100% sampling for debug requests
          
        - error-only: true           # Sampling only for error requests
          sample-rate: 1.0
```

#### Rule Matching Priority

1. **Exact Matching** - Exact matching rules have the highest priority
2. **Wildcard Matching** - Pattern matching using `*` and `?`
3. **Default Rule** - Fallback sampling rate

#### Supported Matching Conditions

| Condition | Type | Description | Example |
|------|------|------|------|
| `service` | string | Service name | `"jairouter"` |
| `operation` | string | Operation name | `"POST /api/v1/chat"` |
| `path-pattern` | string | URL path pattern | `"/api/v1/*"` |
| `method` | string | HTTP method | `"GET"`, `"POST"` |
| `header-name` | string | Request header name | `"X-Debug"` |
| `header-value` | string | Request header value | `"true"` |
| `error-only` | boolean | Error requests only | `true` |
| `status-code` | int | HTTP status code | `500` |

### Adaptive Sampling

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        max-traces-per-second: 100   # Maximum traces per second, default: 100
        base-sample-rate: 0.01       # Base sampling rate, default: 0.1
        error-sample-rate: 1.0       # Error sampling rate, default: 1.0
        slow-request-threshold: 5000 # Slow request threshold (ms), default: 3000
        slow-request-sample-rate: 0.8 # Slow request sampling rate, default: 0.5
        burst-protection: true       # Burst protection, default: true
        adjustment-interval: 30s     # Adjustment interval, default: 60s
```

#### Adaptive Algorithm Description

- **Load Awareness**: Dynamically adjust sampling rate based on current system load
- **Error Priority**: Error requests get higher sampling priority  
- **Slow Query Detection**: Automatically increase sampling rate for slow requests
- **Burst Protection**: Protect system performance in high-concurrency scenarios

## Exporter Configuration

### Log Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "logging"
      logging:
        level: "INFO"                # Log level, default: INFO
        format: "json"               # Format: json/text, default: json
        include-resource: true       # Include resource information, default: true
```

### Jaeger Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://localhost:14268/api/traces"  # Jaeger collector endpoint
        timeout: 10s                 # Connection timeout, default: 10s
        compression: "gzip"          # Compression method: none/gzip, default: gzip
        headers:                     # Custom request headers
          "Authorization": "Bearer token"
```

### Zipkin Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "zipkin"
      zipkin:
        endpoint: "http://localhost:9411/api/v2/spans"
        timeout: 10s
        compression: "gzip"
```

### OTLP Exporter

```yaml
jairouter:
  tracing:
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://localhost:4317"  # OTLP endpoint
        protocol: "grpc"             # Protocol: grpc/http/protobuf
        timeout: 30s                 # Timeout, default: 10s
        compression: "gzip"          # Compression method
        headers:                     # Custom headers
          "api-key": "your-api-key"
        tls:
          enabled: false             # Whether to enable TLS
          cert-path: "/path/to/cert" # Certificate path
          key-path: "/path/to/key"   # Key path
```

### Batch Processing Configuration

```yaml
jairouter:
  tracing:
    exporter:
      batch-size: 512              # Batch size, default: 512
      export-timeout: 30s          # Export timeout, default: 30s
      max-queue-size: 2048         # Maximum queue size, default: 2048
      schedule-delay: 5s           # Schedule delay, default: 5s
```

## Memory Management Configuration

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 10000             # Maximum Span count, default: 10000
      cleanup-interval: 60s        # Cleanup interval, default: 60s
      span-ttl: 300s               # Span TTL, default: 300s
      memory-threshold: 0.8        # Memory threshold, default: 0.8
      gc-pressure-threshold: 0.7   # GC pressure threshold, default: 0.7
      
      cache:
        initial-capacity: 1000     # Cache initial capacity
        maximum-size: 50000        # Cache maximum size
        expire-after-write: 10m    # Expire after write time
        expire-after-access: 5m    # Expire after access time
```

## Performance Configuration

### Async Processing

```yaml
jairouter:
  tracing:
    async:
      enabled: true                # Enable async processing, default: true
      core-pool-size: 2            # Core thread count, default: 2
      max-pool-size: 10            # Maximum thread count, default: 10
      queue-capacity: 1000         # Queue capacity, default: 1000
      keep-alive: 60s              # Thread keep-alive time, default: 60s
      thread-name-prefix: "tracing-" # Thread name prefix
```

### Reactive Configuration

```yaml
jairouter:
  tracing:
    reactive:
      enabled: true                # Enable reactive support, default: true
      context-propagation: true    # Context propagation, default: true
      scheduler-hook: true         # Scheduler hook, default: true
```

## Security Configuration

### Sensitive Data Filtering

```yaml
jairouter:
  tracing:
    security:
      enabled: true
      sensitive-headers:           # Sensitive request headers (will not be recorded)
        - "Authorization"
        - "Cookie"
        - "X-API-Key"
      sensitive-params:            # Sensitive parameters
        - "password"
        - "token"
        - "secret"
      mask-pattern: "***"          # Mask pattern, default: "***"
      
      encryption:
        enabled: false             # Enable encrypted storage
        algorithm: "AES-256-GCM"   # Encryption algorithm
        key-rotation-interval: 24h # Key rotation interval
```

### Access Control

```yaml
jairouter:
  tracing:
    security:
      rbac:
        enabled: false             # Enable role-based access control
        admin-roles:               # Administrator roles
          - "ADMIN"
          - "SYSTEM"
        viewer-roles:              # Viewer roles
          - "USER"
          - "VIEWER"
```

## Integration Configuration

### Actuator Integration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus,tracing"
  endpoint:
    tracing:
      enabled: true

jairouter:
  tracing:
    actuator:
      health-check: true           # Enable health check
      metrics-collection: true     # Enable metrics collection
      info-contribution: true      # Enable info contribution
```

### MDC Integration

```yaml
jairouter:
  tracing:
    mdc:
      enabled: true                # Enable MDC integration, default: true
      trace-id-key: "traceId"      # TraceId key name, default: "traceId"
      span-id-key: "spanId"        # SpanId key name, default: "spanId"
      service-name-key: "service"  # Service name key name, default: "service"
      clear-on-completion: true    # Clear on completion, default: true
```

## Environment-Specific Configuration

### Development Environment

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      strategy: "ratio"
      ratio: 1.0                   # 100% sampling for debugging
    exporter:
      type: "logging"              # Use log exporter
    memory:
      max-spans: 1000              # Smaller memory footprint
```

### Test Environment

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      strategy: "rule"
      rules:
        - operation: "*test*"
          sample-rate: 1.0         # 100% sampling for test cases
        - error-only: true
          sample-rate: 1.0         # Complete sampling for error scenarios
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://jaeger:14268/api/traces"
```

### Production Environment

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      strategy: "adaptive"         # Use adaptive sampling
      adaptive:
        base-sample-rate: 0.01     # 1% base sampling
        max-traces-per-second: 100
    exporter:
      type: "otlp"                 # Use standard OTLP protocol
      otlp:
        endpoint: "https://collector.example.com:4317"
        protocol: "grpc"
        tls:
          enabled: true
    security:
      enabled: true                # Enable security features
    memory:
      max-spans: 50000             # Larger memory configuration
      cleanup-interval: 30s        # More frequent cleanup
```

## Configuration Validation

### Configuration Check Commands

```bash
# Validate configuration syntax
java -jar jairouter.jar --spring.config.location=application.yml --validate-config-only

# Check tracing configuration
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing'
```

### Common Configuration Errors

1. **Sampling Rate Configuration Error**
   ```yaml
   # ❌ Wrong: Sampling rate out of range
   sampling:
     ratio: 1.5
   
   # ✅ Correct: Sampling rate within 0.0-1.0 range
   sampling:
     ratio: 1.0
   ```

2. **Exporter Endpoint Configuration Error**
   ```yaml
   # ❌ Wrong: Incorrect endpoint format
   exporter:
     jaeger:
       endpoint: "localhost:14268"
   
   # ✅ Correct: Complete URL
   exporter:
     jaeger:
       endpoint: "http://localhost:14268/api/traces"
   ```

## Configuration Best Practices

1. **Gradual Enablement**: Start with low sampling rate and gradually increase
2. **Environment Isolation**: Use different configuration strategies for different environments  
3. **Performance Monitoring**: Regularly check the performance impact of the tracing system
4. **Security Considerations**: Enable sensitive data filtering in production environments
5. **Capacity Planning**: Configure appropriate memory and export parameters based on expected traffic

## Next Steps

- [Usage Guide](usage-guide.md) - Learn how to use tracing features
- [Performance Tuning](performance-tuning.md) - Optimize tracing performance
- [Troubleshooting](troubleshooting.md) - Solve configuration issues
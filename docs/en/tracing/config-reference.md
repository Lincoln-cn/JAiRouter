# Configuration Reference

This document provides complete configuration reference for JAiRouter's distributed tracing feature.

## Configuration File Structure

JAiRouter uses a modular configuration management approach, with tracing configuration located in a separate configuration file:

- **Main Configuration File**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **Tracing Configuration File**: [src/main/resources/config/tracing/tracing-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/tracing/tracing-base.yml)
- **Environment Configuration Files**: [src/main/resources/application-{profile}.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml)

## Modular Configuration Explanation

Tracing configuration has been separated from the main configuration file and is imported through the `spring.config.import` mechanism:

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/tracing/tracing-base.yml
```

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

```yaml
jairouter:
  tracing:
    performance:
      async-processing: true       # Asynchronous processing, default: true
      batch-size: 512              # Batch size, default: 512
      buffer-size: 2048            # Buffer size, default: 2048
      flush-interval: 5s           # Flush interval, default: 5s
      max-queue-size: 2048         # Maximum queue size, default: 2048
      schedule-delay: 5s           # Schedule delay, default: 5s
```

## Component Configuration

### WebFlux Configuration

```yaml
jairouter:
  tracing:
    components:
      webflux:
        enabled: true
        capture-request-headers: []
        capture-response-headers: []
        capture-request-parameters: []
```

### WebClient Configuration

```yaml
jairouter:
  tracing:
    components:
      webclient:
        enabled: true
        capture-request-headers: []
        capture-response-headers: []
```

### Database Configuration

```yaml
jairouter:
  tracing:
    components:
      database:
        enabled: true
        capture-statement: false
        capture-parameters: false
```

### Redis Configuration

```yaml
jairouter:
  tracing:
    components:
      redis:
        enabled: true
```

### Rate Limiter Configuration

```yaml
jairouter:
  tracing:
    components:
      rate-limiter:
        enabled: true
        capture-algorithm: true
        capture-quota: true
        capture-decision: true
        capture-statistics: true
```

### Circuit Breaker Configuration

```yaml
jairouter:
  tracing:
    components:
      circuit-breaker:
        enabled: true
        capture-state: true
        capture-state-changes: true
        capture-statistics: true
        capture-failure-rate: true
```

### Load Balancer Configuration

```yaml
jairouter:
  tracing:
    components:
      load-balancer:
        enabled: true
        capture-strategy: true
        capture-selection: true
        capture-statistics: true
```

## Security Configuration

```yaml
jairouter:
  tracing:
    security:
      sanitization:
        enabled: true
        inherit-global-rules: true
        additional-patterns: []
      access-control:
        restrict-trace-access: true
        allowed-roles: []
```

## Monitoring Configuration

```yaml
jairouter:
  tracing:
    monitoring:
      self-monitoring: true
      metrics:
        enabled: true
        prefix: "jairouter.tracing"
        traces:
          enabled: true
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]
        exporter:
          enabled: true
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0]
      health:
        enabled: true
      alerts:
        enabled: true
        trace-processing-failures: 10
        export-failures: 5
        buffer-pressure: 80
```

## Environment Configuration Overrides

Different environments can override tracing configuration through corresponding environment configuration files:

### Development Environment (application-dev.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 1.0  # 100% sampling in development environment
    logging:
      level: "DEBUG"
```

### Production Environment (application-prod.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 0.1  # 10% sampling in production environment
    exporter:
      type: "otlp"
      otlp:
        endpoint: "${OTLP_ENDPOINT:http://localhost:4317}"
```

## Best Practices

### Configuration Management

1. **Base Configuration**: Define common configurations in [tracing-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/tracing/tracing-base.yml)
2. **Environment Differences**: Override specific configurations in corresponding environment configuration files
3. **Sensitive Information**: Use environment variables to inject sensitive configurations such as exporter endpoints and authentication information

### Sampling Strategy

1. **Development Environment**: Recommended to use 100% sampling for debugging
2. **Production Environment**: Adjust sampling rate according to system load to avoid performance impact
3. **Critical Paths**: Use rule sampling to ensure tracing of important business

### Performance Optimization

1. **Batch Processing**: Properly configure batch processing parameters to balance latency and throughput
2. **Memory Management**: Adjust memory configuration according to system resources
3. **Component Selection**: Enable only the components that need tracing to reduce overhead
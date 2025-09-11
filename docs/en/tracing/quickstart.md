# Quick Start

This guide will help you quickly enable and configure the distributed tracing feature of JAiRouter.

## Prerequisites

- Java 17 or higher
- JAiRouter service is running normally
- Basic understanding of YAML configuration files

## Basic Configuration

### 1. Enable Tracing

Add tracing configuration in `application.yml`:

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    service-version: "1.0.0"
```

### 2. Configure Sampling Strategy

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    sampling:
      strategy: "ratio"  # Optional values: ratio, rule, adaptive
      ratio: 1.0         # 100% sampling (recommended for development)
```

### 3. Select Exporter

#### Console Log Export (Recommended for Development)

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    exporter:
      type: "logging"
```

#### Jaeger Export (Recommended for Production)

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://localhost:14268/api/traces"
```

#### OTLP Export (Standard Protocol)

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://localhost:4317"
        protocol: "grpc"  # or "http/protobuf"
```

## Verify Configuration

### 1. Start Service

```bash
# Using Maven to start
mvn spring-boot:run

# Or using Docker
docker-compose up
```

### 2. Check Tracing Logs

After startup, you should see tracing logs similar to the following in the console:

```json
{
  "timestamp": "2024-01-15T10:30:15.123Z",
  "level": "INFO",
  "service": "jairouter",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "message": "Request processed successfully",
  "duration": 150,
  "http.method": "POST",
  "http.url": "/api/v1/chat/completions"
}
```

### 3. Send Test Request

```bash
# Send API request
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### 4. View Tracing Data

Depending on your configured exporter type:

#### Log Export
Check tracing information in application logs:

```bash
# View latest tracing logs
tail -f logs/application.log | grep traceId
```

#### Jaeger UI
Open browser and visit `http://localhost:16686`, search for service name "jairouter".

#### OTLP Collector
Check your OTEL collector configuration and backend storage.

## Configure Sampling Strategy

### Ratio Sampling (Development Environment)

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 1.0  # 100% sampling for development debugging
```

### Rule Sampling (Production Environment)

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "rule"
      rules:
        - service: "jairouter"
          operation: "*"
          sample-rate: 0.1  # 10% sampling
        - service: "jairouter"
          operation: "POST /api/v1/chat/completions"
          sample-rate: 0.5  # 50% sampling for critical interfaces
        - path-pattern: "/health*"
          sample-rate: 0.0  # No sampling for health checks
```

### Adaptive Sampling (Recommended)

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        max-traces-per-second: 100
        base-sample-rate: 0.1
        error-sample-rate: 1.0  # 100% sampling for error requests
```

## Performance Tuning

### 1. Async Export Configuration

```yaml
jairouter:
  tracing:
    exporter:
      batch-size: 100
      export-timeout: 30s
      max-queue-size: 2048
```

### 2. Memory Management

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 10000
      cleanup-interval: 60s
      span-ttl: 300s
```

### 3. Dynamic Sampling Rate Adjustment

It is recommended to start with a low sampling rate in production environments:

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        base-sample-rate: 0.01  # 1% base sampling
        max-traces-per-second: 50
```

## Integration Monitoring

### 1. Integration with Prometheus

The tracing system automatically exposes monitoring metrics:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus"
  metrics:
    export:
      prometheus:
        enabled: true
```

Visit `http://localhost:8080/actuator/prometheus` to view tracing-related metrics.

### 2. Key Metrics

- `jairouter_tracing_spans_created_total` - Total number of created Spans
- `jairouter_tracing_spans_exported_total` - Total number of exported Spans
- `jairouter_tracing_sampling_rate` - Current sampling rate
- `jairouter_tracing_export_duration` - Export duration

## Common Issues

### Q: Tracing data is not exported

**Check items:**
1. Confirm `jairouter.tracing.enabled=true`
2. Check if exporter configuration is correct
3. Verify network connection (Jaeger/OTLP endpoint)
4. Check error messages in application logs

### Q: Performance impact is too large

**Optimization suggestions:**
1. Reduce sampling rate: `sampling.ratio: 0.1`
2. Enable async export
3. Adjust batch size
4. Monitor memory usage

### Q: Tracing context is lost

**Troubleshooting steps:**
1. Check if async operations correctly propagate context
2. Confirm the execution order of custom filters
3. Verify WebFlux configuration

## Next Steps

- [Configuration Reference](config-reference.md) - View all configuration options
- [Usage Guide](usage-guide.md) - Learn advanced usage techniques
- [Troubleshooting](troubleshooting.md) - Solve common problems
- [Performance Tuning](performance-tuning.md) - Optimize tracing performance
- [Development Integration](../development/tracing-integration.md) - Developer guide
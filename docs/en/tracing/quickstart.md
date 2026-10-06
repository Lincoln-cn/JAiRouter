# Quick Start

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-06-15
> **Git Commit**: 933eeadf
> **Author**: Lincoln
<!-- /版本信息 -->

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
      strategy: "parent_based_traceid_ratio"  # Options: parent_based_traceid_ratio, ratio, rule, adaptive
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
        # Checked in issue #159: there is no protocol knob (OtlpConfig only has endpoint /
        # timeout / headers / compression); OTLP runs over gRPC.
        endpoint: "http://localhost:4317"
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
      # Checked in issue #159: there is no strategy key (no backing field); the ratio is it.
      ratio: 1.0  # 100% sampling for development debugging
```

### Rule Sampling (Production Environment)

```yaml
jairouter:
  tracing:
    sampling:
      # Checked in issue #159: strategy: "rule" has no backing field. A rule is condition + ratio
      # (condition is an attribute expression supporting >= / <= / == / !=, see
      # RuleBasedSamplingStrategy.matchesRule); the five old keys do not exist.
      # Requests matching no rule fall back to sampling.ratio.
      rules:
        - condition: "http.status_code >= 500"                 # Error requests: always sample
          ratio: 1.0
        - condition: "http.route == /api/v1/chat/completions"  # Critical route: 50%
          ratio: 0.5
```

### Adaptive Sampling (Recommended)

```yaml
jairouter:
  tracing:
    sampling:
      # Checked in issue #159: adaptive sampling has no separate strategy switch (use
      # adaptive.enabled), and the three old keys do not exist.
      adaptive:
        enabled: true
        target-spans-per-second: 100  # Target spans per second
        min-ratio: 0.1                # Lower bound of the sampling ratio
        max-ratio: 1.0                # Upper bound of the sampling ratio
        adjustment-interval: 30       # Adjustment interval (seconds)
```

## Performance Tuning

### 1. Async Export Configuration

```yaml
jairouter:
  tracing:
    # Checked in issue #159: these three belong to the OpenTelemetry batch processor, not under
    # exporter, and batch-size is the wrong name (the field is max-export-batch-size).
    open-telemetry:
      sdk:
        trace:
          processors:
            batch:
              max-export-batch-size: 100
              export-timeout: 30s
              max-queue-size: 2048
```

### 2. Memory Management

```yaml
jairouter:
  tracing:
    # Checked in issue #159: the real path is performance.memory, and cleanup-interval /
    # span-ttl do not exist (the fields are max-spans-in-memory / memory-limit-mb / gc-interval).
    performance:
      memory:
        max-spans-in-memory: 10000
        memory-limit-mb: 100
        gc-interval: 60s
```

### 3. Dynamic Sampling Rate Adjustment

It is recommended to start with a low sampling rate in production environments:

```yaml
jairouter:
  tracing:
    sampling:
      adaptive:
        enabled: true
        target-spans-per-second: 50
        min-ratio: 0.01    # 1% lower bound of the sampling ratio
        max-ratio: 1.0
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
- [Usage Guide](usage-guide.md) - API reference and best practices
- [Troubleshooting](troubleshooting.md) - Solve common problems
- [Performance Tuning](performance-tuning.md) - Optimize tracing performance
# Troubleshooting

This document provides diagnosis and solutions for common issues with the JAiRouter distributed tracing feature.

## Tracing Data Issues

### 1. Missing Tracing Data

**Symptoms**:
- No traceId and spanId in logs
- No tracing data in monitoring panels
- Exporter not receiving tracing information

**Diagnosis Steps**:

```bash
# 1. Check if tracing is enabled
curl http://localhost:8080/actuator/health/tracing

# 2. Check configuration
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing'

# 3. Check sampling rate
curl http://localhost:8080/actuator/metrics/jairouter.tracing.sampling.rate
```

**Common Causes and Solutions**:

| Cause | Solution |
|------|----------|
| Tracing not enabled | Set `jairouter.tracing.enabled=true` |
| Sampling rate too low | Temporarily set `sampling.ratio=1.0` for testing |
| Exporter configuration error | Check exporter endpoint and authentication configuration |
| Filter order issue | Ensure TracingWebFilter is at the front of the filter chain |

### 2. Partial Data Loss

**Symptoms**:
- Only some requests have tracing data
- Child Spans missing
- Async operations have no tracing information

**Solutions**:

```yaml
# Temporarily increase sampling rate for debugging
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 1.0
    
    # Enable debug logs
    logging:
      level: DEBUG
```

## Performance Issues

### 1. Tracing Causing Performance Degradation

**Symptoms**:
- Significantly increased response time
- Rising CPU usage
- High memory usage

**Performance Analysis**:

```bash
# View tracing-related metrics
curl -s http://localhost:8080/actuator/metrics | grep tracing

# Check GC situation
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause

# View thread usage
curl -s http://localhost:8080/actuator/metrics/jvm.threads.live
```

**Optimization Measures**:

```yaml
jairouter:
  tracing:
    # Reduce sampling rate
    sampling:
      ratio: 0.1
    
    # Enable async processing
    async:
      enabled: true
      core-pool-size: 4
    
    # Optimize batch processing
    exporter:
      batch-size: 512
      export-timeout: 5s
```

### 2. Memory Leak

**Symptoms**:
- Continuously growing heap memory
- OutOfMemoryError occurs
- Frequent GC but memory not released

**Troubleshooting Steps**:

```bash
# 1. Check Span count
curl http://localhost:8080/actuator/metrics/jairouter.tracing.spans.active

# 2. Check memory usage
jmap -histo <pid> | grep Span

# 3. Generate heap dump
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
```

**Solutions**:

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 5000              # Limit Span count
      cleanup-interval: 15s        # More frequent cleanup
      span-ttl: 60s               # Shorter TTL
```

## Configuration Issues

### 1. Configuration Not Taking Effect

**Symptoms**:
- No changes after modifying configuration
- Configuration validation fails
- Configuration errors at startup

**Check Configuration Syntax**:

```bash
# Validate YAML syntax
python -c "import yaml; yaml.safe_load(open('application.yml'))"

# Check configuration binding
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing'
```

**Common Configuration Errors**:

```yaml
# ❌ Wrong configuration
jairouter:
  tracing:
    sampling:
      ratio: 1.5                   # Out of range [0.0, 1.0]
    exporter:
      endpoint: "localhost:4317"   # Missing protocol

# ✅ Correct configuration  
jairouter:
  tracing:
    sampling:
      ratio: 1.0
    exporter:
      endpoint: "http://localhost:4317"
```

### 2. Dynamic Configuration Update Failure

**Diagnostic Methods**:

```bash
# Check configuration service status
curl http://localhost:8080/actuator/health/config

# View configuration history
curl http://localhost:8080/api/admin/config/history
```

## Exporter Issues

### 1. Jaeger Connection Failure

**Error Message**:
```
Failed to export spans to Jaeger: Connection refused
```

**Resolution Steps**:

```bash
# 1. Check Jaeger service status
curl http://localhost:14268/api/traces

# 2. Verify network connection
telnet localhost 14268

# 3. Check firewall settings
netstat -an | grep 14268
```

**Configuration Adjustment**:

```yaml
jairouter:
  tracing:
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://jaeger:14268/api/traces"  # Use service name
        timeout: 30s                                # Increase timeout
        retry-enabled: true                         # Enable retry
```

### 2. OTLP Export Errors

**Common Errors**:

| Error | Cause | Solution |
|------|------|----------|
| `UNAUTHENTICATED` | Authentication failure | Check API key configuration |
| `RESOURCE_EXHAUSTED` | Insufficient quota | Reduce sampling rate or contact service provider |
| `DEADLINE_EXCEEDED` | Timeout | Increase export timeout |

## Context Propagation Issues

### 1. Context Loss in Reactive Streams

**Issue Manifestation**:
- No traceId in async operations
- Child Span creation fails
- MDC information missing

**Solutions**:

```java
// ✅ Correct reactive context propagation
return Mono.just(data)
    .flatMap(this::processAsync)
    .contextWrite(Context.of("tracing", TracingContext.current()));

// ❌ Wrong usage - no context propagation
return Mono.just(data)
    .flatMap(this::processAsync);
```

### 2. Context Loss in Thread Pools

**Configure Thread Pool Context Propagation**:

```java
@Bean
public TaskExecutor tracingTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setTaskDecorator(new TracingTaskDecorator());
    return executor;
}
```

## Debugging Tips

### 1. Enable Debug Logs

```yaml
logging:
  level:
    org.unreal.modelrouter.tracing: DEBUG
    io.opentelemetry: DEBUG
```

### 2. Use Debug Endpoints

```bash
# View current active Spans
curl http://localhost:8080/actuator/tracing/active-spans

# View tracing statistics
curl http://localhost:8080/actuator/tracing/stats

# Force export all Spans
curl -X POST http://localhost:8080/actuator/tracing/flush
```

### 3. Local Testing Tools

```bash
# Use curl to test tracing
curl -H "X-Trace-Debug: true" http://localhost:8080/api/v1/chat/completions

# Check tracing information in response headers
curl -I http://localhost:8080/health
```

## Monitoring Alerts

### 1. Key Metrics Monitoring

```yaml
# Prometheus alert rules
groups:
  - name: tracing_alerts
    rules:
      - alert: TracingExportFailure
        expr: rate(jairouter_tracing_export_errors_total[5m]) > 0.1
        labels:
          severity: warning
          
      - alert: TracingMemoryHigh
        expr: jairouter_tracing_memory_used_ratio > 0.8
        labels:
          severity: critical
```

### 2. Health Checks

```bash
# Set up tracing health check
curl http://localhost:8080/actuator/health/tracing

# Expected response
{
  "status": "UP",
  "details": {
    "exporter": "healthy",
    "sampling": "active",
    "memory": "normal"
  }
}
```

## Common Error Codes

| Error Code | Description | Solution |
|--------|------|----------|
| `TRACING_001` | Tracing service not initialized | Check configuration and restart service |
| `TRACING_002` | Sampling strategy configuration error | Validate sampling configuration syntax |
| `TRACING_003` | Exporter connection failure | Check network and endpoint configuration |
| `TRACING_004` | Insufficient memory | Increase memory or adjust configuration |
| `TRACING_005` | Context propagation failure | Check async operation implementation |

## Getting Support

If you encounter unresolved issues:

1. **View Logs**: Enable DEBUG level logs for detailed information
2. **Check Configuration**: Use actuator endpoints to validate configuration
3. **Performance Analysis**: Use JVM tools to analyze performance issues
4. **Community Support**: Submit issue reports in GitHub Issues

## Next Steps

- [Performance Tuning](performance-tuning.md) - Optimize tracing system performance
- [Operations Guide](operations-guide.md) - Production environment operations best practices
- [Configuration Reference](config-reference.md) - View detailed configuration options
# Performance Tuning

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-06-08
> **Git Commit**: b9fa976b
> **Author**: Lincoln
<!-- /版本信息 -->

This document provides performance tuning guides and best practices for the JAiRouter distributed tracing system.

## Sampling Strategy Optimization

### Production Environment Recommended Configuration (v2.7.9+)

```yaml
jairouter:
  tracing:
    sampling:
      # Sampling strategy (real since issue #234): ratio (default, uses the ratio only) / rule
      # (matches rules against span attributes, falling back to ratio) / adaptive (uses the
      # adaptive.* bounds; needs adaptive.enabled=true). The old `default-ratio` key does not
      # exist and was removed.
      strategy: ratio
      ratio: 0.1                       # 10% sampling rate (v2.7.9+ optimization: reduced from 1.0)
      
      # Paths that should always be sampled
      always-sample:
        - "/api/v1"
        - "/api/security"
      
      # Paths that should never be sampled
      never-sample:
        - "/health"
        - "/actuator"
      
      # Adaptive sampling configuration (optional)
      adaptive:
        enabled: false                  # Enable adaptive sampling
        target-spans-per-second: 1000   # Target spans per second
        min-ratio: 0.1                  # Minimum sampling ratio
        max-ratio: 1.0                  # Maximum sampling ratio
        adjustment-interval: 30         # Adjustment interval in seconds
```

### Adaptive Sampling Strategy

When enabled, adaptive sampling dynamically adjusts the sampling rate based on system load:

```java
// AdaptiveSamplingStrategy adjusts based on:
// - Request frequency per span name
// - System load thresholds
// - Configurable min/max ratios

// Configuration in TracingConfiguration.SamplingConfig.AdaptiveConfig:
// - enabled: false by default
// - targetSpansPerSecond: 1000
// - minRatio: 0.1
// - maxRatio: 1.0
// - adjustmentInterval: 30 seconds
```

## Memory Management Optimization

### Memory Configuration

```yaml
jairouter:
  tracing:
    performance:
      # Memory management configuration
      memory:
        max-spans-in-memory: 10000     # Maximum spans kept in memory
        memory-limit-mb: 100           # Memory limit in MB
        gc-interval: 60s               # Garbage collection interval
      
      # Buffer configuration
      buffer:
        size: 1024                     # Buffer size
        flush-interval: 5s             # Flush interval
        max-wait-time: 30s             # Maximum wait time for flush
```

### Automatic Memory Cleanup

The system automatically cleans up expired spans based on the configured memory limits:

```java
// MemoryConfig default values:
// - maxSpansInMemory: 10000
// - memoryLimitMb: 100
// - gcInterval: 60 seconds

// When memory threshold is exceeded:
// 1. Oldest spans are evicted first
// 2. GC is triggered based on gcInterval
// 3. Metrics are emitted for monitoring
```

## Async Processing Optimization

### Thread Pool Configuration

```yaml
jairouter:
  tracing:
    performance:
      # Thread pool configuration
      thread-pool:
        core-size: 2                   # Core thread count (the boundedElastic threadCap, i.e. the maximum)
        queue-capacity: 1000           # Queue capacity
        keep-alive: 60s                # Idle-thread TTL (the boundedElastic ttlSeconds)
        thread-name-prefix: "tracing-" # Thread name prefix

      # Async processing
      async-processing: true           # Enable async processing
```

> This pool is Reactor's `Schedulers.newBoundedElastic(threadCap, queuedTaskCap, prefix, ttlSeconds)`,
> which has **no separate core/max**: `core-size` *is* the cap. `thread-pool.max-size` is therefore only
> echoed by `/actuator/info` and **has no effect**; `tracing-base.yml` no longer lists it (issue #220).

### Batch Processing Configuration

```yaml
jairouter:
  tracing:
    performance:
      # The batch processor parameters live here — OpenTelemetry's BatchSpanProcessor is driven by
      # exactly these values (see OpenTelemetryAutoConfiguration#tracerProvider); there is no
      # second set of switches.
      #
      # Checked in issue #224: there used to be an open-telemetry.sdk.trace.processors.batch.*
      # section (schedule-delay / max-queue-size / max-export-batch-size / export-timeout) above,
      # which **no production code read** — tuning it did nothing. The fields and the yml keys are
      # gone.
      batch:
        size: 100                      # Max spans per export batch (setMaxExportBatchSize)
        timeout: 5s                    # Export timeout, also used as the schedule delay
        max-concurrent-batches: 3      # Application-level batch concurrency
      buffer:
        size: 8192                     # Queue capacity (setMaxQueueSize reads buffer.size)
```

## Exporter Optimization

### Exporter Configuration

```yaml
jairouter:
  tracing:
    exporter:
      type: "otlp"                     # jaeger, zipkin, otlp, logging
      
      # OTLP exporter (recommended)
      otlp:
        endpoint: "http://localhost:4317"
        timeout: 10s
        compression: "gzip"            # Enable compression
        headers: {}
      
      # Jaeger exporter (deprecated, use OTLP instead)
      jaeger:
        endpoint: "http://localhost:14268/api/traces"
        timeout: 10s
      
      # Zipkin exporter
      zipkin:
        endpoint: "http://localhost:9411/api/v2/spans"
        timeout: 10s
      
      # Logging exporter (for debugging)
      logging:
        enabled: false
        level: "INFO"
```

## JVM Tuning

### Recommended JVM Parameters

```bash
# Production environment JVM parameters
-Xmx4g -Xms4g                     # Heap memory configuration
-XX:+UseG1GC                      # Use G1 garbage collector
-XX:MaxGCPauseMillis=200          # Maximum GC pause time
-XX:+UnlockExperimentalVMOptions
-XX:+UseJVMCICompiler             # Enable JVMCI compiler (optional)

# For tracing-heavy workloads, consider:
-XX:InitiatingHeapOccupancyPercent=35  # Earlier GC trigger
-XX:G1HeapRegionSize=16m               # Larger regions for throughput
```

## Monitoring Metrics

### Key Performance Metrics

```bash
# CPU usage
jairouter_tracing_cpu_usage

# Memory usage
jairouter_tracing_memory_used_bytes

# Span processing latency
jairouter_tracing_span_processing_duration_seconds

# Export success rate
jairouter_tracing_export_success_rate

# Queue size
jairouter_tracing_queue_size

# Active spans
jairouter_tracing_active_spans
```

### Monitoring Configuration

```yaml
jairouter:
  tracing:
    monitoring:
      metrics:
        traces:
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]  # Processing-latency buckets (seconds)
      health:
        enabled: true
        check-interval: 30s
      alerts:
        thresholds:
          memory-usage: 0.8         # Heap usage > 80% is reported as a memory bottleneck
          export-latency-p99: 5000  # P99 for trace.export > 5 s is reported as an operation bottleneck
```

> Removed: `self-monitoring`, `metrics.enabled`, `metrics.prefix`, `metrics.traces.enabled`,
> `metrics.exporter.*`, `alerts.enabled`, `alerts.thresholds.export-failure-rate` and `queue-size`
> — these keys bind but have no reader anywhere in the codebase (the `monitoring` section is not even
> echoed to `/actuator/info`), so keeping them only made it look like editing them did something
> (issue #224).

## Troubleshooting

### Performance Issue Diagnosis

1. **Check if sampling rate is too high**
   - Verify `jairouter.tracing.sampling.ratio` is appropriate for your traffic
   - v2.7.9+ default is 0.1 (10%), reduced from 1.0 (100%)

2. **Monitor memory usage**
   - Check `jairouter_tracing_memory_used_bytes`
   - Adjust `performance.memory.max-spans-in-memory` if needed

3. **Analyze GC frequency and duration**
   - Use JVM GC logging: `-Xlog:gc*:file=gc.log`
   - Consider increasing heap size if GC is frequent

4. **Check exporter response time**
   - Monitor `jairouter_tracing_export_success_rate`
   - Verify network connectivity to OTLP/Jaeger endpoint

5. **Verify queue pressure**
   - Check `jairouter_tracing_queue_size`
   - Increase `performance.thread-pool.queue-capacity` if needed

### Optimization Suggestions

- **Adjust sampling rate** based on actual load (v2.7.9+ optimized default: 10%)
- **Enable async processing** to reduce blocking (`async-processing: true`)
- **Configure batch processing** for efficient export
- **Monitor system resource usage** with built-in metrics
- **Use OTLP exporter** with gzip compression for best performance
- **Regularly clean up expired data** with configured GC interval

## Component-Specific Optimization

### HTTP Tracing

```yaml
jairouter:
  tracing:
    components:
      http:
        enabled: true
        capture-headers: true
        capture-body: false           # Disable for performance
        max-body-size: 1024
        excluded-paths:               # Exclude high-traffic paths
          - "/health"
          - "/metrics"
```

### Database Tracing

> Checked in issue #224: **this configuration does not exist**. `components.database`
> (`enabled` / `capture-sql` / `max-sql-length`) has no consumer anywhere in the code base — it was
> a knob that did nothing — so the fields and the `tracing-base.yml` keys were removed. Database-side
> observability should be implemented (a consumer first) before the configuration comes back.

### Rate Limiter & Circuit Breaker Tracing

```yaml
jairouter:
  tracing:
    components:
      rate-limiter:
        enabled: true
        capture-algorithm: true
        capture-statistics: true
      
      circuit-breaker:
        enabled: true
        capture-state: true
        capture-statistics: true
```

> Deleted (issue #224): `capture-quota` / `capture-decision` (rate limiter),
> `capture-state-changes` / `capture-failure-rate` (circuit breaker),
> `capture-selection` / `capture-candidates` (load balancer). These fields have no consumer and are not
> even echoed to `/actuator/info` — changing them has no observable effect. The `capture-*` flags that
> remain exist only so that echo can be produced; they do not change collection behavior either.

## Next Steps

- [Troubleshooting](troubleshooting.md) - Solve common performance issues
- [Operations Guide](operations-guide.md) - Production environment operations practices
- [Configuration Reference](config-reference.md) - Complete configuration options

# Performance Tuning

This document provides performance tuning guides and best practices for the JAiRouter distributed tracing system.

## Sampling Strategy Optimization

### Production Environment Recommended Configuration (v2.7.9+)

```yaml
jairouter:
  tracing:
    sampling:
      # Sampling strategy: parent_based_traceid_ratio, rule_based, adaptive
      strategy: "parent_based_traceid_ratio"
      ratio: 0.1                       # 10% sampling rate (v2.7.9+ optimization: reduced from 1.0)
      default-ratio: 0.1               # Default ratio for new services
      
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
        core-size: 2                   # Core thread count
        max-size: 8                    # Maximum thread count
        queue-capacity: 1000           # Queue capacity
        keep-alive: 60s                # Thread keep-alive time
        thread-name-prefix: "tracing-" # Thread name prefix
      
      # Async processing
      async-processing: true           # Enable async processing
```

### Batch Processing Configuration

```yaml
jairouter:
  tracing:
    # OpenTelemetry batch processor configuration
    open-telemetry:
      sdk:
        trace:
          processors:
            batch:
              schedule-delay: 5s       # Schedule delay
              max-queue-size: 2048     # Maximum queue size
              max-export-batch-size: 512  # Batch size for export
              export-timeout: 30s      # Export timeout
    
    performance:
      # Application-level batch configuration
      batch:
        size: 100                      # Batch size
        timeout: 5s                    # Batch timeout
        max-concurrent-batches: 3      # Maximum concurrent batches
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
      self-monitoring: true         # Enable self-monitoring
      metrics:
        enabled: true
        prefix: "jairouter.tracing"
        traces:
          enabled: true
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]
        exporter:
          enabled: true
      health:
        enabled: true
        check-interval: 30s
      alerts:
        enabled: true
        thresholds:
          export-failure-rate: 0.1  # Alert if > 10% export failures
          export-latency-p99: 5000  # Alert if P99 > 5 seconds
          memory-usage: 0.8         # Alert if > 80% memory usage
          queue-size: 0.9           # Alert if queue > 90% full
```

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

```yaml
jairouter:
  tracing:
    components:
      database:
        enabled: false                # Disable if not needed
        capture-sql: false            # Be careful with sensitive data
        max-sql-length: 1000
```

### Rate Limiter & Circuit Breaker Tracing

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
      
      circuit-breaker:
        enabled: true
        capture-state: true
        capture-state-changes: true
        capture-statistics: true
        capture-failure-rate: true
```

## Next Steps

- [Troubleshooting](troubleshooting.md) - Solve common performance issues
- [Operations Guide](operations-guide.md) - Production environment operations practices
- [Configuration Reference](config-reference.md) - Complete configuration options

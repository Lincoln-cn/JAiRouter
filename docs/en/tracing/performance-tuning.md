# Performance Tuning

This document provides performance tuning guides and best practices for the JAiRouter distributed tracing system.

## Sampling Strategy Optimization

### Production Environment Recommended Configuration

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        base-sample-rate: 0.01        # 1% base sampling
        max-traces-per-second: 100    # Limit traces per second
        error-sample-rate: 1.0        # 100% error sampling
        slow-request-threshold: 3000  # 3-second slow request threshold
        slow-request-sample-rate: 0.5 # 50% sampling for slow requests
```

### Dynamic Sampling Rate Adjustment

```java
@Component
public class DynamicSamplingController {
    
    @Scheduled(fixedRate = 60000) // Adjust every minute
    public void adjustSamplingRate() {
        SystemMetrics metrics = systemMonitor.getCurrentMetrics();
        
        if (metrics.getCpuUsage() > 80) {
            // Reduce sampling rate when CPU usage is high
            samplingManager.updateRate(0.01);
        } else if (metrics.getErrorRate() > 0.05) {
            // Increase sampling rate when error rate is high
            samplingManager.updateRate(0.1);
        }
    }
}
```

## Memory Management Optimization

### Memory Configuration

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 10000               # Maximum Span count
      cleanup-interval: 30s          # Cleanup interval
      span-ttl: 180s                # Span TTL
      memory-threshold: 0.7         # Memory threshold
      
      cache:
        maximum-size: 50000         # Maximum cache size
        expire-after-write: 5m      # Expire after write
```

### Automatic Memory Cleanup

```java
@Component
public class TracingMemoryManager {
    
    @Scheduled(fixedRate = 30000)
    public void performCleanup() {
        MemoryUsage usage = getMemoryUsage();
        
        if (usage.getUsedRatio() > 0.8) {
            // Clean up expired Spans
            int cleaned = cleanExpiredSpans();
            log.info("Cleaned up {} expired Spans", cleaned);
        }
    }
}
```

## Async Processing Optimization

### Async Export Configuration

```yaml
jairouter:
  tracing:
    async:
      enabled: true
      core-pool-size: 4             # Core thread count
      max-pool-size: 16            # Maximum thread count
      queue-capacity: 2000         # Queue capacity
      
    exporter:
      batch-size: 512              # Batch size
      export-timeout: 10s          # Export timeout
      schedule-delay: 2s           # Schedule delay
```

## JVM Tuning

### Recommended JVM Parameters

```bash
# Production environment JVM parameters
-Xmx4g -Xms4g                     # Heap memory configuration
-XX:+UseG1GC                      # Use G1 garbage collector
-XX:MaxGCPauseMillis=200         # Maximum GC pause time
-XX:+UnlockExperimentalVMOptions
-XX:+UseJVMCICompiler            # Enable JVMCI compiler
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
```

## Troubleshooting

### Performance Issue Diagnosis

1. **Check if sampling rate is too high**
2. **Monitor memory usage** 
3. **Analyze GC frequency and duration**
4. **Check exporter response time**

### Optimization Suggestions

- Adjust sampling rate based on actual load
- Enable async processing to reduce blocking
- Regularly clean up expired data
- Monitor system resource usage

## Next Steps

- [Troubleshooting](troubleshooting.md) - Solve common performance issues
- [Operations Guide](operations-guide.md) - Production environment operations practices
# Monitoring Performance Optimization Guide

This document describes how to optimize the performance of the JAiRouter monitoring system, reduce the impact of monitoring on application performance, and improve the efficiency of the monitoring system itself.

## Performance Impact Assessment

### Monitoring Overhead Analysis

The impact of monitoring functionality on system performance is mainly reflected in the following aspects:

| Component | Performance Impact | Typical Overhead | Optimization Potential |
|-----------|--------------------|------------------|------------------------|
| Metric Collection | CPU, Memory | 2-5% CPU, 50-100MB Memory | High |
| Metric Exposure | Network, CPU | 1-2% CPU, Network Bandwidth | Medium |
| Asynchronous Processing | Memory, Threads | 20-50MB Memory, 2-4 Threads | High |
| Caching Mechanism | Memory | 10-30MB Memory | Medium |

### Performance Benchmark Testing

#### Test Environment Configuration
```yaml
# Test Configuration
monitoring:
  metrics:
    enabled: true
    performance:
      async-processing: true
      batch-size: 500
      buffer-size: 2000
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
```

#### Benchmark Test Results
```bash
# Run Performance Test
./mvnw test -Dtest=MonitoringPerformanceBenchmarkTest

# Typical Results
- No Monitoring: 1000 RPS, 50ms P95 Latency
- With Monitoring: 950 RPS, 52ms P95 Latency
- Performance Impact: 5% throughput decrease, 4% latency increase
```

## Metric Collection Optimization

### Sampling Strategy Optimization

#### 1. Hierarchical Sampling Configuration
```yaml
monitoring:
  metrics:
    sampling:
      # Keep high sampling rate for core business metrics
      request-metrics: 1.0
      model-call-metrics: 1.0
      
      # Infrastructure metrics can have lower sampling rate
      infrastructure-metrics: 0.1
      system-metrics: 0.5
      
      # Debug metrics only enabled when needed
      debug-metrics: 0.01
```

#### 2. Dynamic Sampling Rate Adjustment
```yaml
monitoring:
  metrics:
    adaptive-sampling:
      enabled: true
      # Dynamically adjust based on system load
      cpu-threshold: 80
      memory-threshold: 85
      # Sampling rate under high load
      high-load-sampling-rate: 0.1
```

#### 3. Conditional Sampling
```java
@Component
public class ConditionalMetricsCollector {
    
    @Value("${monitoring.metrics.sampling.request-metrics:1.0}")
    private double requestSamplingRate;
    
    public void recordRequest(HttpServletRequest request) {
        // Only fully sample important paths
        if (isImportantPath(request.getRequestURI()) || 
            Math.random() < requestSamplingRate) {
            metricsCollector.recordRequest(request);
        }
    }
    
    private boolean isImportantPath(String path) {
        return path.startsWith("/v1/") || 
               path.contains("/chat/") || 
               path.contains("/embedding/");
    }
}
```

### Asynchronous Processing Optimization

#### 1. Asynchronous Processing Configuration
```yaml
monitoring:
  metrics:
    performance:
      # Enable asynchronous processing
      async-processing: true
      
      # Thread pool configuration
      async-thread-pool-size: 4
      async-thread-pool-max-size: 8
      async-queue-capacity: 1000
      
      # Batch processing configuration
      batch-size: 500
      batch-timeout: 1s
      
      # Buffer configuration
      buffer-size: 2000
      buffer-flush-interval: 5s
```

#### 2. Asynchronous Processing Implementation
```java
@Component
public class AsyncMetricsProcessor {
    
    private final BlockingQueue<MetricEvent> eventQueue = 
        new ArrayBlockingQueue<>(bufferSize);
    
    private final ScheduledExecutorService executor = 
        Executors.newScheduledThreadPool(threadPoolSize);
    
    @PostConstruct
    public void startProcessing() {
        executor.scheduleAtFixedRate(this::processBatch, 0, batchTimeout, TimeUnit.SECONDS);
    }
    
    public void submitEvent(MetricEvent event) {
        if (!eventQueue.offer(event)) {
            // Handle queue full strategy
            handleQueueFull(event);
        }
    }
    
    private void processBatch() {
        List<MetricEvent> batch = new ArrayList<>();
        eventQueue.drainTo(batch, batchSize);
        
        if (!batch.isEmpty()) {
            processBatchEvents(batch);
        }
    }
}
```

### Memory Optimization

#### 1. Memory Usage Configuration
```yaml
monitoring:
  metrics:
    memory:
      # Metric cache configuration
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

#### 2. Memory Monitoring and Adaptation
```java
@Component
public class MemoryAwareMetricsCollector {
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    public void recordMetric(String name, double value, String... tags) {
        if (isMemoryPressureHigh()) {
            // Reduce sampling rate under high memory pressure
            if (Math.random() < lowMemorySamplingRate) {
                doRecordMetric(name, value, tags);
            }
        } else {
            doRecordMetric(name, value, tags);
        }
    }
    
    private boolean isMemoryPressureHigh() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        return usageRatio > memoryThreshold;
    }
}
```

## Prometheus Optimization

### Scrape Configuration Optimization

#### 1. Scrape Interval Optimization
```yaml
# prometheus.yml
global:
  scrape_interval: 30s  # Increase global scrape interval
  evaluation_interval: 30s

scrape_configs:
  - job_name: 'jairouter'
    scrape_interval: 15s  # Keep shorter interval for important services
    scrape_timeout: 10s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['jairouter:8080']
    
    # Metric relabeling to reduce storage
    metric_relabel_configs:
      # Drop unnecessary metrics
      - source_labels: [__name__]
        regex: 'jvm_classes_.*'
        action: drop
      
      # Drop high cardinality labels
      - regex: 'client_ip'
        action: labeldrop
```

#### 2. Storage Optimization
```yaml
# prometheus.yml startup parameters
command:
  - '--config.file=/etc/prometheus/prometheus.yml'
  - '--storage.tsdb.path=/prometheus'
  - '--storage.tsdb.retention.time=15d'  # Reduce retention time
  - '--storage.tsdb.retention.size=5GB'   # Limit storage size
  - '--storage.tsdb.min-block-duration=2h'
  - '--storage.tsdb.max-block-duration=25h'
  - '--storage.tsdb.wal-compression'      # Enable WAL compression
```

### Recording Rules Optimization

#### 1. Pre-aggregation of Common Queries
```yaml
# rules/jairouter-recording-rules.yml
groups:
  - name: jairouter.recording
    interval: 30s
    rules:
      # Basic metric pre-aggregation
      - record: jairouter:request_rate_5m
        expr: sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:error_rate_5m
        expr: sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:response_time_p95_5m
        expr: histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
      
      # Pre-aggregation by service
      - record: jairouter:request_rate_by_service_5m
        expr: sum by (service) (rate(jairouter_requests_total[5m]))
      
      - record: jairouter:error_rate_by_service_5m
        expr: sum by (service) (rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum by (service) (rate(jairouter_requests_total[5m]))
```

#### 2. Hierarchical Recording Rules
```yaml
groups:
  # High-frequency basic rules
  - name: jairouter.recording.basic
    interval: 15s
    rules:
      - record: jairouter:up
        expr: up{job="jairouter"}
      
      - record: jairouter:request_rate_1m
        expr: sum(rate(jairouter_requests_total[1m]))
  
  # Medium-frequency aggregation rules
  - name: jairouter.recording.aggregate
    interval: 60s
    rules:
      - record: jairouter:request_rate_5m
        expr: sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:backend_health_ratio
        expr: sum(jairouter_backend_health) / count(jairouter_backend_health)
  
  # Low-frequency complex rules
  - name: jairouter.recording.complex
    interval: 300s
    rules:
      - record: jairouter:sla_availability_24h
        expr: avg_over_time(jairouter:up[24h])
```

## Grafana Optimization

### Query Optimization

#### 1. Using Recording Rules
```promql
# Before optimization: Complex query
sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100

# After optimization: Using pre-aggregation
jairouter:error_rate_5m * 100
```

#### 2. Query Cache Configuration
```yaml
# grafana.ini
[caching]
enabled = true

[query_caching]
enabled = true
ttl = 60s
max_cache_size_mb = 100
```

#### 3. Panel Optimization
```json
{
  "targets": [
    {
      "expr": "jairouter:request_rate_5m",
      "interval": "30s",
      "maxDataPoints": 100,
      "refId": "A"
    }
  ],
  "options": {
    "reduceOptions": {
      "values": false,
      "calcs": ["lastNotNull"]
    }
  }
}
```

### Dashboard Optimization

#### 1. Panel Count Control
- No more than 20 panels per dashboard
- Use row folding to organize panels
- Avoid displaying too many data series in a single panel

#### 2. Refresh Interval Optimization
```json
{
  "refresh": "30s",  // Increase refresh interval
  "time": {
    "from": "now-1h",  // Reduce default time range
    "to": "now"
  }
}
```

## Network Optimization

### Metric Transmission Optimization

#### 1. Compression Configuration
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    params:
      format: ['prometheus']
    honor_labels: true
    compression: 'gzip'  # Enable compression
```

#### 2. Batch Transmission
```java
@Component
public class BatchMetricsExporter {
    
    private final List<Metric> metricsBatch = new ArrayList<>();
    private final int batchSize = 1000;
    
    @Scheduled(fixedDelay = 5000)
    public void exportBatch() {
        if (metricsBatch.size() >= batchSize) {
            exportMetrics(new ArrayList<>(metricsBatch));
            metricsBatch.clear();
        }
    }
}
```

### Network Connection Optimization

#### 1. Connection Pool Configuration
```yaml
monitoring:
  metrics:
    http-client:
      connection-pool-size: 10
      connection-timeout: 5s
      read-timeout: 10s
      keep-alive: true
```

#### 2. Local Caching
```java
@Component
public class CachedMetricsCollector {
    
    private final Cache<String, Double> metricsCache = 
        Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    
    public void recordMetric(String name, double value) {
        // Check cache to avoid duplicate calculations
        Double cachedValue = metricsCache.getIfPresent(name);
        if (cachedValue == null || Math.abs(cachedValue - value) > threshold) {
            metricsCache.put(name, value);
            doRecordMetric(name, value);
        }
    }
}
```

## Monitoring System Monitoring

### Monitoring Performance Metrics

#### 1. Monitoring Overhead Metrics
```yaml
# Add metrics for the monitoring system itself
- record: monitoring:collection_duration_seconds
  expr: histogram_quantile(0.95, rate(jairouter_metrics_collection_duration_seconds_bucket[5m]))

- record: monitoring:memory_usage_bytes
  expr: jvm_memory_used_bytes{area="heap", application="jairouter"}

- record: monitoring:cpu_usage_percent
  expr: rate(process_cpu_seconds_total{application="jairouter"}[5m]) * 100
```

#### 2. Performance Alerts
```yaml
groups:
  - name: monitoring.performance
    rules:
      - alert: MonitoringHighCPUUsage
        expr: monitoring:cpu_usage_percent > 10
        for: 5m
        annotations:
          summary: "Monitoring function CPU usage too high"
      
      - alert: MonitoringHighMemoryUsage
        expr: monitoring:memory_usage_bytes > 200 * 1024 * 1024
        for: 5m
        annotations:
          summary: "Monitoring function memory usage too high"
```

### Automatic Performance Tuning

#### 1. Adaptive Configuration
```java
@Component
public class AdaptiveMonitoringConfig {
    
    @Scheduled(fixedDelay = 60000)
    public void adjustConfiguration() {
        double cpuUsage = getCurrentCPUUsage();
        double memoryUsage = getCurrentMemoryUsage();
        
        if (cpuUsage > 80 || memoryUsage > 85) {
            // Reduce sampling rate
            reduceSamplingRate();
            // Increase batch size
            increaseBatchSize();
            // Reduce metric categories
            disableNonCriticalMetrics();
        } else if (cpuUsage < 50 && memoryUsage < 60) {
            // Restore normal configuration
            restoreNormalConfiguration();
        }
    }
}
```

## Environment-Specific Optimization

### Development Environment Optimization

```yaml
# application-dev.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 1.0  # Keep full sampling in development
    performance:
      async-processing: false  # Simplify debugging
      batch-size: 100
```

### Test Environment Optimization

```yaml
# application-test.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.1  # Reduce sampling rate to minimize interference
    performance:
      async-processing: true
      batch-size: 1000
```

### Production Environment Optimization

```yaml
# application-prod.yml
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
      low-memory-sampling-rate: 0.01
```

## Performance Testing

### Benchmark Testing

#### 1. Performance Test Script
```bash
#!/bin/bash

echo "Starting performance benchmark test..."

# Baseline test without monitoring
echo "Testing performance without monitoring..."
curl -X POST http://localhost:8080/actuator/monitoring/disable
ab -n 10000 -c 100 http://localhost:8080/v1/chat/completions > baseline.txt

# Test with monitoring enabled
echo "Testing performance with monitoring enabled..."
curl -X POST http://localhost:8080/actuator/monitoring/enable
ab -n 10000 -c 100 http://localhost:8080/v1/chat/completions > monitoring.txt

# Analyze results
python analyze_performance.py baseline.txt monitoring.txt
```

#### 2. Performance Analysis Script
```python
import re

def analyze_performance(baseline_file, monitoring_file):
    def parse_ab_output(filename):
        with open(filename, 'r') as f:
            content = f.read()
        
        rps = re.search(r'Requests per second:\s+(\d+\.\d+)', content)
        latency = re.search(r'Time per request:\s+(\d+\.\d+)', content)
        
        return float(rps.group(1)), float(latency.group(1))
    
    baseline_rps, baseline_latency = parse_ab_output(baseline_file)
    monitoring_rps, monitoring_latency = parse_ab_output(monitoring_file)
    
    rps_impact = (baseline_rps - monitoring_rps) / baseline_rps * 100
    latency_impact = (monitoring_latency - baseline_latency) / baseline_latency * 100
    
    print(f"Performance Impact Analysis:")
    print(f"Throughput Impact: {rps_impact:.2f}%")
    print(f"Latency Impact: {latency_impact:.2f}%")
```

### Continuous Performance Monitoring

#### 1. Performance Regression Detection
```yaml
# Add performance check in CI/CD
- name: Performance Regression Test
  run: |
    ./scripts/performance-test.sh
    if [ $PERFORMANCE_IMPACT -gt 10 ]; then
      echo "Performance impact exceeds 10%, build failed"
      exit 1
    fi
```

#### 2. Performance Trend Monitoring
```promql
# Monitor performance trends
increase(jairouter_request_duration_seconds_sum[24h]) / increase(jairouter_request_duration_seconds_count[24h])
```

## Best Practices Summary

### Configuration Optimization Checklist

- [ ] Enable asynchronous processing
- [ ] Configure appropriate sampling rates
- [ ] Use Recording Rules for pre-aggregation
- [ ] Optimize Prometheus storage configuration
- [ ] Limit Grafana panel count
- [ ] Enable compression and caching
- [ ] Monitor monitoring system performance
- [ ] Conduct regular performance testing

### Performance Optimization Principles

1. **Progressive Optimization**: Start with optimizations that have the greatest impact
2. **Monitor Optimization Effects**: Measure performance impact after each optimization
3. **Balance Accuracy and Performance**: Find balance between monitoring accuracy and performance
4. **Environment-Specific Configuration**: Use different optimization strategies for different environments
5. **Continuous Monitoring**: Establish performance monitoring and alerting mechanisms

## Related Documentation

- [Monitoring Setup Guide](setup.md)
- [Troubleshooting Guide](troubleshooting.md)
- [Monitoring Metrics Reference](metrics.md)
- [Testing Guide](testing.md)

---

**Important Reminder**: Performance optimization is an ongoing process that requires continuous adjustment and optimization of configurations based on actual usage.

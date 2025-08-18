# Performance Troubleshooting

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


This document provides diagnostic methods and optimization strategies for JAiRouter performance issues.

## Performance Monitoring Metrics

### Key Performance Indicators (KPI)

| Metric Category | Metric Name | Normal Range | Alert Threshold |
|----------------|-------------|--------------|-----------------|
| **Response Time** | P95 Response Time | < 2s | > 5s |
| **Throughput** | Requests Per Second (RPS) | > 100 | < 50 |
| **Error Rate** | 4xx/5xx Error Rate | < 1% | > 5% |
| **Resource Usage** | CPU Usage | < 70% | > 85% |
| **Resource Usage** | Memory Usage | < 80% | > 90% |
| **Connections** | Active Connections | < 1000 | > 2000 |

### Monitoring Metric Collection

```bash
# Get response time metrics
curl -s http://localhost:8080/actuator/metrics/jairouter.request.duration | jq

# Get request statistics
curl -s http://localhost:8080/actuator/metrics/jairouter.requests.total | jq

# Get JVM metrics
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq

# Get system metrics
curl -s http://localhost:8080/actuator/metrics/system.cpu.usage | jq
```

## Performance Issue Classification

### 1. Slow Response Time

#### Symptom Identification
- API response time exceeds 5 seconds
- User feedback about slow system response
- P95 response time continuously increasing

#### Diagnostic Steps

**1. Identify Bottleneck Location**
```bash
# Check component response times
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/v1/chat/completions

# curl-format.txt content:
#     time_namelookup:  %{time_namelookup}\n
#        time_connect:  %{time_connect}\n
#     time_appconnect:  %{time_appconnect}\n
#    time_pretransfer:  %{time_pretransfer}\n
#       time_redirect:  %{time_redirect}\n
#  time_starttransfer:  %{time_starttransfer}\n
#                     ----------\n
#          time_total:  %{time_total}\n
```

**2. Analyze Request Chain**
```bash
# Enable request tracing
java -Dlogging.level.org.unreal.modelrouter=DEBUG \
     -jar target/model-router-*.jar

# Analyze timestamps in logs
grep "Processing request" logs/jairouter-debug.log | tail -10
```

**3. Check Backend Service Performance**
```bash
# Directly test backend service
time curl -X POST http://backend:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"test","messages":[{"role":"user","content":"hello"}]}'
```

#### Optimization Strategies

**Connection Pool Optimization**
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 200        # Increase connection pool size
        max-idle-time: 60s         # Extend idle time
        max-life-time: 300s        # Extend connection lifecycle
      connect-timeout: 5s          # Connection timeout
      response-timeout: 30s        # Response timeout
```

**Load Balancer Optimization**
```yaml
model:
  services:
    chat:
      load-balance:
        type: least-connections    # Use least connections strategy
      timeout: 30s                 # Set reasonable timeout
```

**Cache Strategy**
```yaml
model:
  cache:
    enabled: true
    ttl: 300s                     # 5-minute cache
    max-size: 1000               # Maximum cache entries
```

### 2. Insufficient Throughput

#### Symptom Identification
- RPS below expectations
- System unable to handle high concurrent requests
- Request queue backlog

#### Diagnostic Steps

**1. Stress Testing**
```bash
# Use Apache Bench for stress testing
ab -n 1000 -c 50 -H "Content-Type: application/json" \
   -p request.json http://localhost:8080/v1/chat/completions

# Use wrk for stress testing
wrk -t12 -c400 -d30s --script=post.lua http://localhost:8080/v1/chat/completions
```

**2. Thread Pool Analysis**
```bash
# Get thread pool status
curl -s http://localhost:8080/actuator/metrics/executor.active | jq
curl -s http://localhost:8080/actuator/metrics/executor.queue.remaining | jq

# Generate thread dump
jstack <pid> > threads.dump
```

**3. Resource Usage Analysis**
```bash
# CPU usage
top -p <pid>

# Memory usage
jstat -gc <pid> 5s

# I/O usage
iotop -p <pid>
```

#### Optimization Strategies

**Thread Pool Tuning**
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8              # Core thread count
        max-size: 32              # Maximum thread count
        queue-capacity: 200       # Queue capacity
        keep-alive: 60s           # Thread keep-alive time
```

**Reactor Tuning**
```yaml
spring:
  webflux:
    netty:
      worker-threads: 16          # Worker thread count
      initial-buffer-size: 128    # Initial buffer size
      max-buffer-size: 1024       # Maximum buffer size
```

**Asynchronous Processing**
```yaml
model:
  async:
    enabled: true
    thread-pool-size: 16
    queue-capacity: 1000
```

### 3. High Memory Usage

#### Symptom Identification
- JVM heap memory continuously growing
- Frequent Full GC
- OutOfMemoryError exceptions

#### Diagnostic Steps

**1. Memory Usage Analysis**
```bash
# Check memory usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq
curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq

# Check GC status
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq
curl -s http://localhost:8080/actuator/metrics/jvm.gc.memory.allocated | jq
```

**2. Heap Dump Analysis**
```bash
# Generate heap dump
jcmd <pid> GC.run_finalization
jmap -dump:format=b,file=heap.hprof <pid>

# Analyze heap dump using Eclipse MAT or VisualVM
```

**3. Memory Leak Detection**
```bash
# Enable memory leak detection
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -jar target/model-router-*.jar
```

#### Optimization Strategies

**JVM Parameter Tuning**
```bash
# G1GC configuration
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:G1HeapRegionSize=16m \
     -XX:G1NewSizePercent=20 \
     -XX:G1MaxNewSizePercent=30 \
     -XX:InitiatingHeapOccupancyPercent=45 \
     -jar target/model-router-*.jar
```

**Memory Management Optimization**
```yaml
model:
  memory:
    # Rate limiter cleanup
    rate-limiter-cleanup:
      enabled: true
      interval: 5m
      inactive-threshold: 30m
    
    # Cache management
    cache:
      max-size: 1000
      expire-after-write: 10m
      expire-after-access: 5m
```

**Object Pooling**
```yaml
spring:
  webflux:
    httpclient:
      pool:
        # Enable object pooling
        use-object-pooling: true
        pool-size: 100
```

### 4. High CPU Usage

#### Symptom Identification
- CPU usage consistently above 85%
- High system load
- Increased response time

#### Diagnostic Steps

**1. CPU Hotspot Analysis**
```bash
# Check CPU usage
top -H -p <pid>

# Generate CPU performance profile
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=cpu-profile.jfr \
     -jar target/model-router-*.jar

# Analyze performance data
jfr print --events CPULoad cpu-profile.jfr
```

**2. Thread Analysis**
```bash
# Generate thread dump
jstack <pid> > threads.dump

# Analyze high CPU threads
top -H -p <pid>  # Find high CPU thread ID
printf "%x\n" <thread-id>  # Convert to hexadecimal
grep <hex-thread-id> threads.dump  # Search in dump
```

**3. Code Hotspot Analysis**
```bash
# Enable compilation logging
java -XX:+PrintCompilation \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintInlining \
     -jar target/model-router-*.jar
```

#### Optimization Strategies

**Algorithm Optimization**
```java
// Optimized load balancing algorithm
@Component
public class OptimizedRoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public ServiceInstance selectInstance(List<ServiceInstance> instances, String clientInfo) {
        if (instances.isEmpty()) {
            return null;
        }
        
        // Optimize modulo operation using bitwise operations
        int index = counter.getAndIncrement() & (instances.size() - 1);
        return instances.get(index);
    }
}
```

**Concurrency Optimization**
```yaml
model:
  concurrency:
    # Limit concurrent processing
    max-concurrent-requests: 1000
    
    # Use lock-free data structures
    lock-free-structures: true
    
    # Batch processing optimization
    batch-processing:
      enabled: true
      batch-size: 100
      timeout: 100ms
```

**Cache Optimization**
```java
// Use Caffeine high-performance cache
@Configuration
public class CacheConfiguration {
    
    @Bean
    public Cache<String, Object> responseCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }
}
```

## Performance Tuning Best Practices

### 1. JVM Tuning

#### Heap Memory Configuration
```bash
# Production environment recommended configuration
java -Xms4g -Xmx4g \                    # Fixed heap size to avoid dynamic adjustment
     -XX:+UseG1GC \                     # Use G1 garbage collector
     -XX:MaxGCPauseMillis=100 \         # Maximum GC pause time
     -XX:G1HeapRegionSize=32m \         # G1 region size
     -XX:G1NewSizePercent=20 \          # Young generation ratio
     -XX:G1MaxNewSizePercent=30 \       # Maximum young generation ratio
     -XX:InitiatingHeapOccupancyPercent=45 \  # GC trigger threshold
     -XX:+UnlockExperimentalVMOptions \ # Enable experimental options
     -XX:+UseStringDeduplication \      # String deduplication
     -jar target/model-router-*.jar
```

#### GC Log Configuration
```bash
# GC log configuration
-Xlog:gc*:gc.log:time,tags \
-XX:+UseGCLogFileRotation \
-XX:NumberOfGCLogFiles=5 \
-XX:GCLogFileSize=10M
```

### 2. Application Layer Tuning

#### Connection Pool Configuration
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 500           # Adjust based on backend service count
        max-idle-time: 30s            # Idle connection keep time
        max-life-time: 300s           # Connection maximum lifecycle
        pending-acquire-timeout: 10s  # Connection acquisition timeout
        pending-acquire-max-count: 1000  # Waiting queue size
```

#### Reactive Configuration
```yaml
spring:
  webflux:
    netty:
      worker-threads: 16              # Worker thread count = CPU cores * 2
      initial-buffer-size: 128        # Initial buffer
      max-buffer-size: 1024          # Maximum buffer
      connection-timeout: 5s          # Connection timeout
```

### 3. Monitoring and Alerting

#### Performance Monitoring Configuration
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s                     # Metric collection interval
    distribution:
      percentiles-histogram:
        http.server.requests: true    # Enable response time histogram
      percentiles:
        http.server.requests: 0.5,0.95,0.99  # Percentiles
```

#### Alert Rules
```yaml
# prometheus-alerts.yml
groups:
  - name: jairouter.performance
    rules:
      - alert: HighResponseTime
        expr: histogram_quantile(0.95, jairouter_request_duration_seconds_bucket) > 5
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter response time too high"
          description: "P95 response time: {{ $value }}s"
      
      - alert: HighCPUUsage
        expr: system_cpu_usage > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter CPU usage too high"
          description: "CPU usage: {{ $value | humanizePercentage }}"
      
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter memory usage too high"
          description: "Memory usage: {{ $value | humanizePercentage }}"
```

### 4. Capacity Planning

#### Performance Benchmark Testing
```bash
#!/bin/bash
# performance-benchmark.sh

echo "=== JAiRouter Performance Benchmark ==="

# Warm-up
echo "Warm-up phase..."
ab -n 100 -c 10 http://localhost:8080/v1/chat/completions

# Benchmark test
echo "Benchmark test..."
ab -n 1000 -c 50 -g benchmark.dat http://localhost:8080/v1/chat/completions

# Stress test
echo "Stress test..."
ab -n 5000 -c 200 -g stress.dat http://localhost:8080/v1/chat/completions

# Generate report
echo "Generating performance report..."
gnuplot -e "
set terminal png;
set output 'performance-report.png';
set title 'JAiRouter Performance Test';
set xlabel 'Request Number';
set ylabel 'Response Time (ms)';
plot 'benchmark.dat' using 9 with lines title 'Benchmark',
     'stress.dat' using 9 with lines title 'Stress Test'
"

echo "=== Performance testing completed ==="
```

#### Capacity Assessment
```bash
# Calculate theoretical maximum RPS
# RPS = 1000ms / average response time(ms) * concurrent connections

# Example calculation:
# Average response time: 100ms
# Concurrent connections: 200
# Theoretical maximum RPS = 1000 / 100 * 200 = 2000

# Consider safety factor (70%)
# Recommended actual RPS = 2000 * 0.7 = 1400
```

## Performance Optimization Checklist

### Pre-deployment Checklist
- [ ] JVM parameters optimized
- [ ] Connection pool configuration reasonable
- [ ] Cache strategy configured
- [ ] Monitoring metrics enabled
- [ ] Alert rules set

### Runtime Monitoring
- [ ] Response time within normal range
- [ ] CPU usage < 80%
- [ ] Memory usage < 85%
- [ ] GC pause time < 200ms
- [ ] Error rate < 1%

### Regular Optimization
- [ ] Analyze performance trends
- [ ] Identify performance bottlenecks
- [ ] Adjust configuration parameters
- [ ] Update optimization strategies
- [ ] Verify optimization effectiveness

## Performance Testing Tools

### 1. Apache Bench (ab)
```bash
# Basic test
ab -n 1000 -c 50 http://localhost:8080/v1/chat/completions

# POST request test
ab -n 1000 -c 50 -p request.json -T application/json http://localhost:8080/v1/chat/completions
```

### 2. wrk
```bash
# Install wrk
# Ubuntu: sudo apt-get install wrk
# macOS: brew install wrk

# Basic test
wrk -t12 -c400 -d30s http://localhost:8080/v1/chat/completions

# Using script
wrk -t12 -c400 -d30s --script=post.lua http://localhost:8080/v1/chat/completions
```

### 3. JMeter
```xml
<!-- JMeter test plan example -->
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan testname="JAiRouter Performance Test">
      <elementProp name="TestPlan.arguments" elementType="Arguments" guiclass="ArgumentsPanel">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
      <stringProp name="TestPlan.user_define_classpath"></stringProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
  </hashTree>
</jmeterTestPlan>
```

By following these performance optimization guidelines, you can ensure that JAiRouter provides stable and efficient services in production environments.

# 性能调优

本文档提供 JAiRouter 分布式追踪系统的性能调优指南和最佳实践。

## 采样策略优化

### 生产环境推荐配置 (v2.7.9+)

```yaml
jairouter:
  tracing:
    sampling:
      # 采样策略: parent_based_traceid_ratio, rule_based, adaptive
      strategy: "parent_based_traceid_ratio"
      ratio: 0.1                       # 10% 采样率 (v2.7.9+ 优化: 从 1.0 降低)
      default-ratio: 0.1               # 新服务的默认采样率
      
      # 始终采样的路径
      always-sample:
        - "/api/v1"
        - "/api/security"
      
      # 从不采样的路径
      never-sample:
        - "/health"
        - "/actuator"
      
      # 自适应采样配置 (可选)
      adaptive:
        enabled: false                  # 启用自适应采样
        target-spans-per-second: 1000   # 目标每秒 Span 数量
        min-ratio: 0.1                  # 最小采样率
        max-ratio: 1.0                  # 最大采样率
        adjustment-interval: 30         # 调整间隔 (秒)
```

### 自适应采样策略

启用后，自适应采样根据系统负载动态调整采样率：

```java
// AdaptiveSamplingStrategy 根据以下因素调整:
// - 每个 span 名称的请求频率
// - 系统负载阈值
// - 可配置的最小/最大采样率

// TracingConfiguration.SamplingConfig.AdaptiveConfig 配置:
// - enabled: 默认 false
// - targetSpansPerSecond: 1000
// - minRatio: 0.1
// - maxRatio: 1.0
// - adjustmentInterval: 30 秒
```

## 内存管理优化

### 内存配置

```yaml
jairouter:
  tracing:
    performance:
      # 内存管理配置
      memory:
        max-spans-in-memory: 10000     # 内存中最大 Span 数量
        memory-limit-mb: 100           # 内存限制 (MB)
        gc-interval: 60s               # 垃圾回收间隔
      
      # 缓冲区配置
      buffer:
        size: 1024                     # 缓冲区大小
        flush-interval: 5s             # 刷新间隔
        max-wait-time: 30s             # 最大等待刷新时间
```

### 自动内存清理

系统根据配置的内存限制自动清理过期的 Span：

```java
// MemoryConfig 默认值:
// - maxSpansInMemory: 10000
// - memoryLimitMb: 100
// - gcInterval: 60 秒

// 当超过内存阈值时:
// 1. 优先清除最旧的 Span
// 2. 根据 gcInterval 触发 GC
// 3. 发送监控指标
```

## 异步处理优化

### 线程池配置

```yaml
jairouter:
  tracing:
    performance:
      # 线程池配置
      thread-pool:
        core-size: 2                   # 核心线程数
        max-size: 8                    # 最大线程数
        queue-capacity: 1000           # 队列容量
        keep-alive: 60s                # 线程保活时间
        thread-name-prefix: "tracing-" # 线程名前缀
      
      # 异步处理
      async-processing: true           # 启用异步处理
```

### 批处理配置

```yaml
jairouter:
  tracing:
    # OpenTelemetry 批处理器配置
    open-telemetry:
      sdk:
        trace:
          processors:
            batch:
              schedule-delay: 5s       # 调度延迟
              max-queue-size: 2048     # 最大队列大小
              max-export-batch-size: 512  # 导出批大小
              export-timeout: 30s      # 导出超时
    
    performance:
      # 应用级批处理配置
      batch:
        size: 100                      # 批处理大小
        timeout: 5s                    # 批处理超时
        max-concurrent-batches: 3      # 最大并发批次数
```

## 导出器优化

### 导出器配置

```yaml
jairouter:
  tracing:
    exporter:
      type: "otlp"                     # jaeger, zipkin, otlp, logging
      
      # OTLP 导出器 (推荐)
      otlp:
        endpoint: "http://localhost:4317"
        timeout: 10s
        compression: "gzip"            # 启用压缩
        headers: {}
      
      # Jaeger 导出器 (已弃用，建议使用 OTLP)
      jaeger:
        endpoint: "http://localhost:14268/api/traces"
        timeout: 10s
      
      # Zipkin 导出器
      zipkin:
        endpoint: "http://localhost:9411/api/v2/spans"
        timeout: 10s
      
      # 日志导出器 (用于调试)
      logging:
        enabled: false
        level: "INFO"
```

## JVM 调优

### 推荐 JVM 参数

```bash
# 生产环境 JVM 参数
-Xmx4g -Xms4g                     # 堆内存配置
-XX:+UseG1GC                      # 使用 G1 垃圾收集器
-XX:MaxGCPauseMillis=200          # 最大 GC 暂停时间
-XX:+UnlockExperimentalVMOptions
-XX:+UseJVMCICompiler             # 启用 JVMCI 编译器 (可选)

# 对于追踪密集型工作负载，建议:
-XX:InitiatingHeapOccupancyPercent=35  # 更早触发 GC
-XX:G1HeapRegionSize=16m               # 更大的区域以提高吞吐量
```

## 监控指标

### 关键性能指标

```bash
# CPU 使用率
jairouter_tracing_cpu_usage

# 内存使用
jairouter_tracing_memory_used_bytes

# Span 处理延迟
jairouter_tracing_span_processing_duration_seconds

# 导出成功率
jairouter_tracing_export_success_rate

# 队列大小
jairouter_tracing_queue_size

# 活跃 Span 数
jairouter_tracing_active_spans
```

### 监控配置

```yaml
jairouter:
  tracing:
    monitoring:
      self-monitoring: true         # 启用自监控
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
          export-failure-rate: 0.1  # 导出失败率 > 10% 时告警
          export-latency-p99: 5000  # P99 延迟 > 5 秒时告警
          memory-usage: 0.8         # 内存使用 > 80% 时告警
          queue-size: 0.9           # 队列使用 > 90% 时告警
```

## 故障排除

### 性能问题诊断

1. **检查采样率是否过高**
   - 验证 `jairouter.tracing.sampling.ratio` 是否适合您的流量
   - v2.7.9+ 默认为 0.1 (10%)，从 1.0 (100%) 降低

2. **监控内存使用**
   - 检查 `jairouter_tracing_memory_used_bytes`
   - 必要时调整 `performance.memory.max-spans-in-memory`

3. **分析 GC 频率和耗时**
   - 使用 JVM GC 日志: `-Xlog:gc*:file=gc.log`
   - 如果 GC 频繁，考虑增加堆大小

4. **检查导出器响应时间**
   - 监控 `jairouter_tracing_export_success_rate`
   - 验证到 OTLP/Jaeger 端点的网络连接

5. **验证队列压力**
   - 检查 `jairouter_tracing_queue_size`
   - 必要时增加 `performance.thread-pool.queue-capacity`

### 优化建议

- **根据实际负载调整采样率** (v2.7.9+ 优化默认值: 10%)
- **启用异步处理** 减少阻塞 (`async-processing: true`)
- **配置批处理** 提高导出效率
- **监控系统资源使用** 使用内置指标
- **使用 OTLP 导出器** 配合 gzip 压缩获得最佳性能
- **定期清理过期数据** 配置 GC 间隔

## 组件特定优化

### HTTP 追踪

```yaml
jairouter:
  tracing:
    components:
      http:
        enabled: true
        capture-headers: true
        capture-body: false           # 禁用以提高性能
        max-body-size: 1024
        excluded-paths:               # 排除高流量路径
          - "/health"
          - "/metrics"
```

### 数据库追踪

```yaml
jairouter:
  tracing:
    components:
      database:
        enabled: false                # 不需要时禁用
        capture-sql: false            # 注意敏感数据
        max-sql-length: 1000
```

### 限流器与熔断器追踪

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

## 下一步

- [故障排除](troubleshooting.md) - 解决常见性能问题
- [运维指南](operations-guide.md) - 生产环境运维实践
- [配置参考](config-reference.md) - 完整配置选项

# 性能调优

本文档提供 JAiRouter 分布式追踪系统的性能调优指南和最佳实践。

## 采样策略优化

### 生产环境推荐配置

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        base-sample-rate: 0.01        # 1% 基础采样
        max-traces-per-second: 100    # 限制每秒追踪数
        error-sample-rate: 1.0        # 错误100%采样
        slow-request-threshold: 3000  # 3秒慢请求阈值
        slow-request-sample-rate: 0.5 # 慢请求50%采样
```

### 采样率动态调整

```java
@Component
public class DynamicSamplingController {
    
    @Scheduled(fixedRate = 60000) // 每分钟调整一次
    public void adjustSamplingRate() {
        SystemMetrics metrics = systemMonitor.getCurrentMetrics();
        
        if (metrics.getCpuUsage() > 80) {
            // 高CPU使用时降低采样率
            samplingManager.updateRate(0.01);
        } else if (metrics.getErrorRate() > 0.05) {
            // 高错误率时提高采样率
            samplingManager.updateRate(0.1);
        }
    }
}
```

## 内存管理优化

### 内存配置

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 10000               # 最大Span数量
      cleanup-interval: 30s          # 清理间隔
      span-ttl: 180s                # Span生存时间
      memory-threshold: 0.7         # 内存阈值
      
      cache:
        maximum-size: 50000         # 缓存最大大小
        expire-after-write: 5m      # 写入后过期
```

### 自动内存清理

```java
@Component
public class TracingMemoryManager {
    
    @Scheduled(fixedRate = 30000)
    public void performCleanup() {
        MemoryUsage usage = getMemoryUsage();
        
        if (usage.getUsedRatio() > 0.8) {
            // 清理过期 Span
            int cleaned = cleanExpiredSpans();
            log.info("清理了 {} 个过期 Span", cleaned);
        }
    }
}
```

## 异步处理优化

### 异步导出配置

```yaml
jairouter:
  tracing:
    async:
      enabled: true
      core-pool-size: 4             # 核心线程数
      max-pool-size: 16            # 最大线程数
      queue-capacity: 2000         # 队列容量
      
    exporter:
      batch-size: 512              # 批处理大小
      export-timeout: 10s          # 导出超时
      schedule-delay: 2s           # 调度延迟
```

## JVM 调优

### 推荐 JVM 参数

```bash
# 生产环境 JVM 参数
-Xmx4g -Xms4g                     # 堆内存配置
-XX:+UseG1GC                      # 使用 G1 垃圾收集器
-XX:MaxGCPauseMillis=200         # 最大 GC 暂停时间
-XX:+UnlockExperimentalVMOptions
-XX:+UseJVMCICompiler            # 启用 JVMCI 编译器
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
```

## 故障排除

### 性能问题诊断

1. **检查采样率是否过高**
2. **监控内存使用情况** 
3. **分析 GC 频率和耗时**
4. **检查导出器响应时间**

### 优化建议

- 根据实际负载调整采样率
- 启用异步处理减少阻塞
- 定期清理过期数据
- 监控系统资源使用

## 下一步

- [故障排除](troubleshooting.md) - 解决常见性能问题
- [运维指南](operations-guide.md) - 生产环境运维实践
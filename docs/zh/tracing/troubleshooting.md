# 故障排除

本文档提供 JAiRouter 分布式追踪功能常见问题的诊断和解决方案。

## 追踪数据问题

### 1. 追踪数据缺失

**症状描述**：
- 日志中看不到 traceId 和 spanId
- 监控面板没有追踪数据
- 导出器没有收到追踪信息

**诊断步骤**：

```bash
# 1. 检查追踪功能是否启用
curl http://localhost:8080/actuator/health/tracing

# 2. 检查配置
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing'

# 3. 检查采样率
curl http://localhost:8080/actuator/metrics/jairouter.tracing.sampling.rate
```

**常见原因和解决方案**：

| 原因 | 解决方案 |
|------|----------|
| 追踪未启用 | 设置 `jairouter.tracing.enabled=true` |
| 采样率过低 | 临时设置 `sampling.ratio=1.0` 进行测试 |
| 导出器配置错误 | 检查导出器端点和认证配置 |
| 过滤器顺序问题 | 确保 TracingWebFilter 在过滤器链前端 |

### 2. 部分数据丢失

**症状描述**：
- 只有部分请求有追踪数据
- 子 Span 缺失
- 异步操作没有追踪信息

**解决方案**：

```yaml
# 临时提高采样率进行调试
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 1.0
    
    # 启用调试日志
    logging:
      level: DEBUG
```

## 性能问题

### 1. 追踪导致性能下降

**症状描述**：
- 响应时间明显增加
- CPU 使用率上升
- 内存使用量过高

**性能分析**：

```bash
# 查看追踪相关指标
curl -s http://localhost:8080/actuator/metrics | grep tracing

# 检查 GC 情况
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause

# 查看线程使用情况
curl -s http://localhost:8080/actuator/metrics/jvm.threads.live
```

**优化措施**：

```yaml
jairouter:
  tracing:
    # 降低采样率
    sampling:
      ratio: 0.1
    
    # 启用异步处理
    async:
      enabled: true
      core-pool-size: 4
    
    # 优化批处理
    exporter:
      batch-size: 512
      export-timeout: 5s
```

### 2. 内存泄漏

**症状描述**：
- 堆内存持续增长
- 出现 OutOfMemoryError
- GC 频繁但内存不释放

**排查步骤**：

```bash
# 1. 检查 Span 数量
curl http://localhost:8080/actuator/metrics/jairouter.tracing.spans.active

# 2. 检查内存使用
jmap -histo <pid> | grep Span

# 3. 生成堆转储
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
```

**解决方案**：

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 5000              # 限制 Span 数量
      cleanup-interval: 15s        # 更频繁清理
      span-ttl: 60s               # 更短的 TTL
```

## 配置问题

### 1. 配置不生效

**症状描述**：
- 修改配置后没有变化
- 配置验证失败
- 启动时配置错误

**检查配置语法**：

```bash
# 验证 YAML 语法
python -c "import yaml; yaml.safe_load(open('application.yml'))"

# 检查配置绑定
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing'
```

**常见配置错误**：

```yaml
# ❌ 错误配置
jairouter:
  tracing:
    sampling:
      ratio: 1.5                   # 超出范围 [0.0, 1.0]
    exporter:
      endpoint: "localhost:4317"   # 缺少协议

# ✅ 正确配置  
jairouter:
  tracing:
    sampling:
      ratio: 1.0
    exporter:
      endpoint: "http://localhost:4317"
```

### 2. 动态配置更新失败

**诊断方法**：

```bash
# 检查配置服务状态
curl http://localhost:8080/actuator/health/config

# 查看配置历史
curl http://localhost:8080/api/admin/config/history
```

## 导出器问题

### 1. Jaeger 连接失败

**错误信息**：
```
Failed to export spans to Jaeger: Connection refused
```

**解决步骤**：

```bash
# 1. 检查 Jaeger 服务状态
curl http://localhost:14268/api/traces

# 2. 验证网络连接
telnet localhost 14268

# 3. 检查防火墙设置
netstat -an | grep 14268
```

**配置调整**：

```yaml
jairouter:
  tracing:
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://jaeger:14268/api/traces"  # 使用服务名
        timeout: 30s                                # 增加超时时间
        retry-enabled: true                         # 启用重试
```

### 2. OTLP 导出错误

**常见错误**：

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `UNAUTHENTICATED` | 认证失败 | 检查 API 密钥配置 |
| `RESOURCE_EXHAUSTED` | 配额不足 | 降低采样率或联系服务提供商 |
| `DEADLINE_EXCEEDED` | 超时 | 增加导出超时时间 |

## 上下文传播问题

### 1. 响应式流中上下文丢失

**问题表现**：
- 异步操作中没有 traceId
- 子 Span 创建失败
- MDC 信息缺失

**解决方案**：

```java
// ✅ 正确的响应式上下文传播
return Mono.just(data)
    .flatMap(this::processAsync)
    .contextWrite(Context.of("tracing", TracingContext.current()));

// ❌ 错误的用法 - 没有传播上下文
return Mono.just(data)
    .flatMap(this::processAsync);
```

### 2. 线程池中上下文丢失

**配置线程池上下文传播**：

```java
@Bean
public TaskExecutor tracingTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setTaskDecorator(new TracingTaskDecorator());
    return executor;
}
```

## 调试技巧

### 1. 启用调试日志

```yaml
logging:
  level:
    org.unreal.modelrouter.tracing: DEBUG
    io.opentelemetry: DEBUG
```

### 2. 使用调试端点

```bash
# 查看当前活跃 Span
curl http://localhost:8080/actuator/tracing/active-spans

# 查看追踪统计信息
curl http://localhost:8080/actuator/tracing/stats

# 强制导出所有 Span
curl -X POST http://localhost:8080/actuator/tracing/flush
```

### 3. 本地测试工具

```bash
# 使用 curl 测试追踪
curl -H "X-Trace-Debug: true" http://localhost:8080/api/v1/chat/completions

# 检查响应头中的追踪信息
curl -I http://localhost:8080/health
```

## 监控告警

### 1. 关键指标监控

```yaml
# Prometheus 告警规则
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

### 2. 健康检查

```bash
# 设置追踪健康检查
curl http://localhost:8080/actuator/health/tracing

# 预期响应
{
  "status": "UP",
  "details": {
    "exporter": "healthy",
    "sampling": "active",
    "memory": "normal"
  }
}
```

## 常见错误码

| 错误码 | 描述 | 解决方案 |
|--------|------|----------|
| `TRACING_001` | 追踪服务未初始化 | 检查配置并重启服务 |
| `TRACING_002` | 采样策略配置错误 | 验证采样配置语法 |
| `TRACING_003` | 导出器连接失败 | 检查网络和端点配置 |
| `TRACING_004` | 内存不足 | 增加内存或调整配置 |
| `TRACING_005` | 上下文传播失败 | 检查异步操作实现 |

## 获取支持

如果遇到无法解决的问题：

1. **查看日志**：启用 DEBUG 级别日志获取详细信息
2. **检查配置**：使用 actuator 端点验证配置
3. **性能分析**：使用 JVM 工具分析性能问题
4. **社区支持**：在 GitHub Issues 中提交问题报告

## 下一步

- [性能调优](performance-tuning.md) - 优化追踪系统性能
- [运维指南](operations-guide.md) - 生产环境运维最佳实践
- [配置参考](config-reference.md) - 查看详细配置选项
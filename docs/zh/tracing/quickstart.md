# 快速开始

本指南将帮助您快速启用和配置 JAiRouter 的分布式追踪功能。

## 前提条件

- Java 17 或更高版本
- JAiRouter 服务已正常运行
- 对 YAML 配置文件有基本了解

## 基础配置

### 1. 启用追踪功能

在 `application.yml` 中添加追踪配置：

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    service-version: "1.0.0"
```

### 2. 配置采样策略

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    sampling:
      strategy: "ratio"  # 可选值：ratio, rule, adaptive
      ratio: 1.0         # 100% 采样（开发环境推荐）
```

### 3. 选择导出器

#### 控制台日志导出（推荐用于开发）

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    exporter:
      type: "logging"
```

#### Jaeger 导出（推荐用于生产）

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

#### OTLP 导出（标准协议）

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://localhost:4317"
        protocol: "grpc"  # 或 "http/protobuf"
```

## 验证配置

### 1. 启动服务

```bash
# 使用 Maven 启动
mvn spring-boot:run

# 或使用 Docker
docker-compose up
```

### 2. 检查追踪日志

启动后，您应该在控制台看到类似的追踪日志：

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

### 3. 发送测试请求

```bash
# 发送 API 请求
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### 4. 查看追踪数据

根据您配置的导出器类型：

#### 日志导出
检查应用日志中的追踪信息：

```bash
# 查看最新的追踪日志
tail -f logs/application.log | grep traceId
```

#### Jaeger UI
打开浏览器访问 `http://localhost:16686`，搜索服务名称 "jairouter"。

#### OTLP 收集器
检查您的 OTEL 收集器配置和后端存储。

## 配置采样策略

### 比率采样（开发环境）

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 1.0  # 100% 采样，用于开发调试
```

### 规则采样（生产环境）

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "rule"
      rules:
        - service: "jairouter"
          operation: "*"
          sample-rate: 0.1  # 10% 采样
        - service: "jairouter"
          operation: "POST /api/v1/chat/completions"
          sample-rate: 0.5  # 关键接口 50% 采样
        - path-pattern: "/health*"
          sample-rate: 0.0  # 健康检查不采样
```

### 自适应采样（推荐）

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        max-traces-per-second: 100
        base-sample-rate: 0.1
        error-sample-rate: 1.0  # 错误请求 100% 采样
```

## 性能调优

### 1. 异步导出配置

```yaml
jairouter:
  tracing:
    exporter:
      batch-size: 100
      export-timeout: 30s
      max-queue-size: 2048
```

### 2. 内存管理

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 10000
      cleanup-interval: 60s
      span-ttl: 300s
```

### 3. 采样率动态调整

生产环境建议从低采样率开始：

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        base-sample-rate: 0.01  # 1% 基础采样
        max-traces-per-second: 50
```

## 集成监控

### 1. 与 Prometheus 集成

追踪系统会自动暴露监控指标：

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

访问 `http://localhost:8080/actuator/prometheus` 查看追踪相关指标。

### 2. 关键指标

- `jairouter_tracing_spans_created_total` - 创建的 Span 总数
- `jairouter_tracing_spans_exported_total` - 导出的 Span 总数
- `jairouter_tracing_sampling_rate` - 当前采样率
- `jairouter_tracing_export_duration` - 导出耗时

## 常见问题

### Q: 追踪数据没有导出

**检查项目：**
1. 确认 `jairouter.tracing.enabled=true`
2. 检查导出器配置是否正确
3. 验证网络连接（Jaeger/OTLP 端点）
4. 查看应用日志中的错误信息

### Q: 性能影响过大

**优化建议：**
1. 降低采样率：`sampling.ratio: 0.1`
2. 启用异步导出
3. 调整批处理大小
4. 监控内存使用情况

### Q: 追踪上下文丢失

**排查步骤：**
1. 检查异步操作是否正确传播上下文
2. 确认自定义过滤器的执行顺序
3. 验证 WebFlux 配置

## 下一步

- [配置参考](config-reference.md) - 查看所有配置选项
- [使用指南](usage-guide.md) - 了解高级使用技巧
- [故障排除](troubleshooting.md) - 解决常见问题
- [性能调优](performance-tuning.md) - 优化追踪性能
- [开发集成](../development/tracing-integration.md) - 开发者指南
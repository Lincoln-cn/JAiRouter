# 配置参考

本文档提供 JAiRouter 分布式追踪功能的完整配置参考。

## 基础配置

### 启用追踪

```yaml
jairouter:
  tracing:
    enabled: true                    # 是否启用追踪功能，默认: false
    service-name: "jairouter"       # 服务名称，默认: "model-router"
    service-version: "1.0.0"        # 服务版本，默认: "unknown"
```

### 基本配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|---------|------|
| `enabled` | boolean | `false` | 是否启用追踪功能 |
| `service-name` | string | `"model-router"` | 服务名称，用于标识追踪源 |
| `service-version` | string | `"unknown"` | 服务版本号 |
| `environment` | string | `"development"` | 运行环境标识 |

## 采样配置

### 比率采样

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 0.1                     # 采样率 0.0-1.0，默认: 1.0
```

### 规则采样

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "rule"
      rules:
        - service: "jairouter"       # 服务名称匹配
          operation: "*"             # 操作名称匹配（支持通配符）
          sample-rate: 0.1          # 该规则的采样率
          
        - path-pattern: "/api/v1/*"  # URL路径匹配
          method: "POST"             # HTTP方法匹配
          sample-rate: 0.5
          
        - header-name: "X-Debug"     # 请求头匹配
          header-value: "true"
          sample-rate: 1.0           # 调试请求100%采样
          
        - error-only: true           # 仅对错误请求采样
          sample-rate: 1.0
```

#### 规则匹配优先级

1. **精确匹配** - 完全匹配的规则优先级最高
2. **通配符匹配** - 使用 `*` 和 `?` 的模式匹配
3. **默认规则** - 兜底采样率

#### 支持的匹配条件

| 条件 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `service` | string | 服务名称 | `"jairouter"` |
| `operation` | string | 操作名称 | `"POST /api/v1/chat"` |
| `path-pattern` | string | URL路径模式 | `"/api/v1/*"` |
| `method` | string | HTTP方法 | `"GET"`, `"POST"` |
| `header-name` | string | 请求头名称 | `"X-Debug"` |
| `header-value` | string | 请求头值 | `"true"` |
| `error-only` | boolean | 仅错误请求 | `true` |
| `status-code` | int | HTTP状态码 | `500` |

### 自适应采样

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        max-traces-per-second: 100   # 每秒最大追踪数，默认: 100
        base-sample-rate: 0.01       # 基础采样率，默认: 0.1
        error-sample-rate: 1.0       # 错误采样率，默认: 1.0
        slow-request-threshold: 5000 # 慢请求阈值(ms)，默认: 3000
        slow-request-sample-rate: 0.8 # 慢请求采样率，默认: 0.5
        burst-protection: true       # 突发保护，默认: true
        adjustment-interval: 30s     # 调整间隔，默认: 60s
```

#### 自适应算法说明

- **负载感知**：根据当前系统负载动态调整采样率
- **错误优先**：错误请求获得更高的采样优先级  
- **慢查询检测**：自动提高慢请求的采样率
- **突发保护**：在高并发场景下保护系统性能

## 导出器配置

### 日志导出器

```yaml
jairouter:
  tracing:
    exporter:
      type: "logging"
      logging:
        level: "INFO"                # 日志级别，默认: INFO
        format: "json"               # 格式：json/text，默认: json
        include-resource: true       # 包含资源信息，默认: true
```

### Jaeger 导出器

```yaml
jairouter:
  tracing:
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://localhost:14268/api/traces"  # Jaeger收集器端点
        timeout: 10s                 # 连接超时，默认: 10s
        compression: "gzip"          # 压缩方式：none/gzip，默认: gzip
        headers:                     # 自定义请求头
          "Authorization": "Bearer token"
```

### Zipkin 导出器

```yaml
jairouter:
  tracing:
    exporter:
      type: "zipkin"
      zipkin:
        endpoint: "http://localhost:9411/api/v2/spans"
        timeout: 10s
        compression: "gzip"
```

### OTLP 导出器

```yaml
jairouter:
  tracing:
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://localhost:4317"  # OTLP端点
        protocol: "grpc"             # 协议：grpc/http/protobuf
        timeout: 30s                 # 超时时间，默认: 10s
        compression: "gzip"          # 压缩方式
        headers:                     # 自定义头部
          "api-key": "your-api-key"
        tls:
          enabled: false             # 是否启用TLS
          cert-path: "/path/to/cert" # 证书路径
          key-path: "/path/to/key"   # 密钥路径
```

### 批处理配置

```yaml
jairouter:
  tracing:
    exporter:
      batch-size: 512              # 批处理大小，默认: 512
      export-timeout: 30s          # 导出超时，默认: 30s
      max-queue-size: 2048         # 最大队列大小，默认: 2048
      schedule-delay: 5s           # 调度延迟，默认: 5s
```

## 内存管理配置

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 10000             # 最大Span数量，默认: 10000
      cleanup-interval: 60s        # 清理间隔，默认: 60s
      span-ttl: 300s               # Span生存时间，默认: 300s
      memory-threshold: 0.8        # 内存阈值，默认: 0.8
      gc-pressure-threshold: 0.7   # GC压力阈值，默认: 0.7
      
      cache:
        initial-capacity: 1000     # 缓存初始容量
        maximum-size: 50000        # 缓存最大大小
        expire-after-write: 10m    # 写入后过期时间
        expire-after-access: 5m    # 访问后过期时间
```

## 性能配置

### 异步处理

```yaml
jairouter:
  tracing:
    async:
      enabled: true                # 启用异步处理，默认: true
      core-pool-size: 2            # 核心线程数，默认: 2
      max-pool-size: 10            # 最大线程数，默认: 10
      queue-capacity: 1000         # 队列容量，默认: 1000
      keep-alive: 60s              # 线程保活时间，默认: 60s
      thread-name-prefix: "tracing-" # 线程名前缀
```

### 响应式配置

```yaml
jairouter:
  tracing:
    reactive:
      enabled: true                # 启用响应式支持，默认: true
      context-propagation: true    # 上下文传播，默认: true
      scheduler-hook: true         # 调度器钩子，默认: true
```

## 安全配置

### 敏感数据过滤

```yaml
jairouter:
  tracing:
    security:
      enabled: true
      sensitive-headers:           # 敏感请求头（不会被记录）
        - "Authorization"
        - "Cookie"
        - "X-API-Key"
      sensitive-params:            # 敏感参数
        - "password"
        - "token"
        - "secret"
      mask-pattern: "***"          # 掩码模式，默认: "***"
      
      encryption:
        enabled: false             # 启用加密存储
        algorithm: "AES-256-GCM"   # 加密算法
        key-rotation-interval: 24h # 密钥轮换间隔
```

### 访问控制

```yaml
jairouter:
  tracing:
    security:
      rbac:
        enabled: false             # 启用基于角色的访问控制
        admin-roles:               # 管理员角色
          - "ADMIN"
          - "SYSTEM"
        viewer-roles:              # 查看者角色
          - "USER"
          - "VIEWER"
```

## 集成配置

### Actuator 集成

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus,tracing"
  endpoint:
    tracing:
      enabled: true

jairouter:
  tracing:
    actuator:
      health-check: true           # 启用健康检查
      metrics-collection: true     # 启用指标收集
      info-contribution: true      # 启用信息贡献
```

### MDC 集成

```yaml
jairouter:
  tracing:
    mdc:
      enabled: true                # 启用MDC集成，默认: true
      trace-id-key: "traceId"      # TraceId键名，默认: "traceId"
      span-id-key: "spanId"        # SpanId键名，默认: "spanId"
      service-name-key: "service"  # 服务名键名，默认: "service"
      clear-on-completion: true    # 完成时清理，默认: true
```

## 环境特定配置

### 开发环境

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      strategy: "ratio"
      ratio: 1.0                   # 100% 采样便于调试
    exporter:
      type: "logging"              # 使用日志导出器
    memory:
      max-spans: 1000              # 较小的内存占用
```

### 测试环境

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      strategy: "rule"
      rules:
        - operation: "*test*"
          sample-rate: 1.0         # 测试用例 100% 采样
        - error-only: true
          sample-rate: 1.0         # 错误场景完全采样
    exporter:
      type: "jaeger"
      jaeger:
        endpoint: "http://jaeger:14268/api/traces"
```

### 生产环境

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      strategy: "adaptive"         # 使用自适应采样
      adaptive:
        base-sample-rate: 0.01     # 1% 基础采样
        max-traces-per-second: 100
    exporter:
      type: "otlp"                 # 使用标准 OTLP 协议
      otlp:
        endpoint: "https://collector.example.com:4317"
        protocol: "grpc"
        tls:
          enabled: true
    security:
      enabled: true                # 启用安全功能
    memory:
      max-spans: 50000             # 更大的内存配置
      cleanup-interval: 30s        # 更频繁的清理
```

## 配置验证

### 配置检查命令

```bash
# 验证配置语法
java -jar jairouter.jar --spring.config.location=application.yml --validate-config-only

# 检查追踪配置
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing'
```

### 常见配置错误

1. **采样率配置错误**
   ```yaml
   # ❌ 错误：采样率超出范围
   sampling:
     ratio: 1.5
   
   # ✅ 正确：采样率在 0.0-1.0 范围内
   sampling:
     ratio: 1.0
   ```

2. **导出器端点配置错误**
   ```yaml
   # ❌ 错误：端点格式不正确
   exporter:
     jaeger:
       endpoint: "localhost:14268"
   
   # ✅ 正确：包含完整的URL
   exporter:
     jaeger:
       endpoint: "http://localhost:14268/api/traces"
   ```

## 配置最佳实践

1. **渐进式启用**：从低采样率开始，逐步增加
2. **环境隔离**：不同环境使用不同的配置策略  
3. **性能监控**：定期检查追踪系统的性能影响
4. **安全考虑**：生产环境务必启用敏感数据过滤
5. **容量规划**：根据预期流量配置合适的内存和导出参数

## 下一步

- [使用指南](usage-guide.md) - 了解如何使用追踪功能
- [性能调优](performance-tuning.md) - 优化追踪性能
- [故障排除](troubleshooting.md) - 解决配置问题
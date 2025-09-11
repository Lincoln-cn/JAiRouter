# 配置参考

本文档提供 JAiRouter 分布式追踪功能的完整配置参考。

## 配置文件结构

JAiRouter 使用模块化的配置管理方式，追踪配置位于独立的配置文件中：

- **主配置文件**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **追踪配置文件**: [src/main/resources/config/tracing/tracing-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/tracing/tracing-base.yml)
- **环境配置文件**: [src/main/resources/application-{profile}.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml)

## 模块化配置说明

追踪配置已从主配置文件中分离，通过 `spring.config.import` 机制导入：

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/tracing/tracing-base.yml
```

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

```yaml
jairouter:
  tracing:
    performance:
      async-processing: true       # 异步处理，默认: true
      batch-size: 512              # 批处理大小，默认: 512
      buffer-size: 2048            # 缓冲区大小，默认: 2048
      flush-interval: 5s           # 刷新间隔，默认: 5s
      max-queue-size: 2048         # 最大队列大小，默认: 2048
      schedule-delay: 5s           # 调度延迟，默认: 5s
```

## 组件配置

### WebFlux 配置

```yaml
jairouter:
  tracing:
    components:
      webflux:
        enabled: true
        capture-request-headers: []
        capture-response-headers: []
        capture-request-parameters: []
```

### WebClient 配置

```yaml
jairouter:
  tracing:
    components:
      webclient:
        enabled: true
        capture-request-headers: []
        capture-response-headers: []
```

### 数据库配置

```yaml
jairouter:
  tracing:
    components:
      database:
        enabled: true
        capture-statement: false
        capture-parameters: false
```

### Redis 配置

```yaml
jairouter:
  tracing:
    components:
      redis:
        enabled: true
```

### 限流器配置

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
```

### 熔断器配置

```yaml
jairouter:
  tracing:
    components:
      circuit-breaker:
        enabled: true
        capture-state: true
        capture-state-changes: true
        capture-statistics: true
        capture-failure-rate: true
```

### 负载均衡器配置

```yaml
jairouter:
  tracing:
    components:
      load-balancer:
        enabled: true
        capture-strategy: true
        capture-selection: true
        capture-statistics: true
```

## 安全配置

```yaml
jairouter:
  tracing:
    security:
      sanitization:
        enabled: true
        inherit-global-rules: true
        additional-patterns: []
      access-control:
        restrict-trace-access: true
        allowed-roles: []
```

## 监控配置

```yaml
jairouter:
  tracing:
    monitoring:
      self-monitoring: true
      metrics:
        enabled: true
        prefix: "jairouter.tracing"
        traces:
          enabled: true
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]
        exporter:
          enabled: true
          histogram-buckets: [0.1, 0.5, 1.0, 2.0, 5.0, 10.0]
      health:
        enabled: true
      alerts:
        enabled: true
        trace-processing-failures: 10
        export-failures: 5
        buffer-pressure: 80
```

## 环境配置覆盖

不同环境可以通过对应的环境配置文件覆盖追踪配置：

### 开发环境 (application-dev.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 1.0  # 开发环境100%采样
    logging:
      level: "DEBUG"
```

### 生产环境 (application-prod.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 0.1  # 生产环境10%采样
    exporter:
      type: "otlp"
      otlp:
        endpoint: "${OTLP_ENDPOINT:http://localhost:4317}"
```

## 最佳实践

### 配置管理

1. **基础配置**：在 [tracing-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/tracing/tracing-base.yml) 中定义通用配置
2. **环境差异**：在对应的环境配置文件中覆盖特定配置
3. **敏感信息**：使用环境变量注入敏感配置，如导出器端点、认证信息等

### 采样策略

1. **开发环境**：建议使用100%采样以便调试
2. **生产环境**：根据系统负载调整采样率，避免性能影响
3. **关键路径**：对重要业务使用规则采样确保追踪

### 性能优化

1. **批处理**：合理配置批处理参数以平衡延迟和吞吐量
2. **内存管理**：根据系统资源调整内存配置
3. **组件选择**：仅启用需要追踪的组件以减少开销
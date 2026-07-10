# 监控配置参考

<!-- 版本信息 -->
> **文档版本**: 1.0.2  
> **最后更新**: 2026-05-21  
> **Git 提交**: 61384b4a  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档提供 JAiRouter 监控系统的完整配置参考，包括所有配置选项的详细说明、默认值和使用示例。

## 配置文件结构

### 主配置文件

JAiRouter 监控配置主要在 `application.yml` 中定义：

```yaml
# 监控配置
monitoring:
  metrics:
    # 基础配置
    enabled: true
    prefix: "jairouter"
    collection-interval: 10s
    
    # 指标类别
    enabled-categories:
      - system
      - business
      - infrastructure
    
    # 自定义标签
    custom-tags:
      environment: "${spring.profiles.active:default}"
      version: "@project.version@"
    
    # 采样配置
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
      infrastructure-metrics: 1.0
    
    # 性能配置
    performance:
      async-processing: true
      batch-size: 500
      buffer-size: 2000
    
    # 内存配置
    memory:
      cache-size: 10000
      cache-expiry: 5m
    
    # 安全配置
    security:
      data-masking: false
      mask-labels: []

# Spring Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  
  endpoint:
    health:
      show-details: always
    prometheus:
      cache:
        time-to-live: 10s
  
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
```

## 基础配置

### monitoring.metrics.enabled

**类型**: Boolean  
**默认值**: `true`  
**描述**: 是否启用监控指标收集功能

```yaml
monitoring:
  metrics:
    enabled: true  # 启用监控
    # enabled: false  # 禁用监控
```

**环境变量**: `MONITORING_METRICS_ENABLED`

### monitoring.metrics.prefix

**类型**: String  
**默认值**: `"jairouter"`  
**描述**: 指标名称前缀，用于区分不同应用的指标

```yaml
monitoring:
  metrics:
    prefix: "jairouter"        # 默认前缀
    # prefix: "my-app"         # 自定义前缀
    # prefix: ""               # 无前缀
```

### monitoring.metrics.collection-interval

**类型**: Duration  
**默认值**: `10s`  
**描述**: 指标收集间隔

```yaml
monitoring:
  metrics:
    collection-interval: 10s   # 10 秒
    # collection-interval: 5s  # 5 秒（更频繁）
    # collection-interval: 30s # 30 秒（较少频繁）
```

## 指标类别配置

### monitoring.metrics.enabled-categories

**类型**: List<String>  
**默认值**: `["system", "business", "infrastructure"]`  
**描述**: 启用的指标类别

```yaml
monitoring:
  metrics:
    enabled-categories:
      - system          # 系统指标（JVM、HTTP等）
      - business        # 业务指标（模型调用、用户会话等）
      - infrastructure  # 基础设施指标（负载均衡、限流、熔断等）
```

**可选值**:
- `system`: JVM 内存、GC、HTTP 请求等系统指标
- `business`: 模型调用、用户会话、业务流程等业务指标
- `infrastructure`: 负载均衡、限流、熔断、健康检查等基础设施指标

## 自定义标签配置

### monitoring.metrics.custom-tags

**类型**: Map<String, String>  
**默认值**: `{}`  
**描述**: 添加到所有指标的自定义标签

```yaml
monitoring:
  metrics:
    custom-tags:
      environment: "${spring.profiles.active:default}"
      version: "@project.version@"
      region: "us-west-1"
      datacenter: "dc1"
      team: "platform"
```

**注意事项**:
- 标签值支持 Spring 表达式和占位符
- 避免使用高基数标签（如用户 ID、IP 地址）
- 标签数量建议不超过 10 个

## 采样配置

### monitoring.metrics.sampling

**类型**: Object  
**描述**: 指标采样率配置，用于控制指标收集的频率

```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 1.0        # 请求指标采样率（100%）
      backend-metrics: 1.0        # 后端调用指标采样率
      infrastructure-metrics: 1.0 # 基础设施指标采样率
      system-metrics: 1.0         # 系统指标采样率
      debug-metrics: 0.1          # 调试指标采样率（10%）
```

**采样率说明**:
- `1.0`: 100% 采样，收集所有指标
- `0.5`: 50% 采样，随机收集一半指标
- `0.1`: 10% 采样，随机收集十分之一指标
- `0.0`: 0% 采样，不收集指标

**环境特定配置**:
```yaml
# 开发环境 - 全量采样便于调试
monitoring:
  metrics:
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0

# 生产环境 - 降低采样率减少开销
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
```

## 性能配置

### monitoring.metrics.performance

**类型**: Object  
**描述**: 性能相关配置

```yaml
monitoring:
  metrics:
    performance:
      # 异步处理配置
      async-processing: true
      async-thread-pool-size: 4
      async-thread-pool-max-size: 8
      async-queue-capacity: 1000
      
      # 批处理配置
      batch-size: 500
      batch-timeout: 1s
      
      # 缓冲区配置
      buffer-size: 2000
      buffer-flush-interval: 5s
      
      # 处理超时配置
      processing-timeout: 5s
```

#### async-processing

**类型**: Boolean  
**默认值**: `true`  
**描述**: 是否启用异步指标处理

```yaml
monitoring:
  metrics:
    performance:
      async-processing: true   # 启用异步处理（推荐）
      # async-processing: false # 同步处理（调试时使用）
```

#### batch-size

**类型**: Integer  
**默认值**: `500`  
**描述**: 批处理大小，一次处理的指标事件数量

```yaml
monitoring:
  metrics:
    performance:
      batch-size: 500    # 默认批大小
      # batch-size: 100  # 小批量，低延迟
      # batch-size: 1000 # 大批量，高吞吐
```

#### buffer-size

**类型**: Integer  
**默认值**: `2000`  
**描述**: 缓冲区大小，待处理指标事件的队列容量

```yaml
monitoring:
  metrics:
    performance:
      buffer-size: 2000   # 默认缓冲区大小
      # buffer-size: 5000 # 大缓冲区，处理突发流量
      # buffer-size: 1000 # 小缓冲区，节省内存
```

## 内存配置

### monitoring.metrics.memory

**类型**: Object  
**描述**: 内存使用相关配置

```yaml
monitoring:
  metrics:
    memory:
      # 缓存配置
      cache-size: 10000
      cache-expiry: 5m
      cache-cleanup-interval: 1m
      
      # 内存阈值配置
      memory-threshold: 80
      low-memory-sampling-rate: 0.1
      
      # 对象池配置
      object-pool-enabled: true
      object-pool-size: 1000
```

#### cache-size

**类型**: Integer  
**默认值**: `10000`  
**描述**: 指标缓存大小

#### cache-expiry

**类型**: Duration  
**默认值**: `5m`  
**描述**: 缓存过期时间

#### memory-threshold

**类型**: Integer  
**默认值**: `80`  
**描述**: 内存使用阈值（百分比），超过后启用低内存模式

## 安全配置

### monitoring.metrics.security

**类型**: Object  
**描述**: 安全相关配置

```yaml
monitoring:
  metrics:
    security:
      # 数据脱敏
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key
        - session_id
      
      # IP 地址脱敏
      ip-masking: true
      ip-mask-pattern: "xxx.xxx.xxx.xxx"
      
      # 敏感指标过滤
      sensitive-metrics-filter: true
      filtered-metrics:
        - "*.password.*"
        - "*.secret.*"
        - "*.token.*"
```

#### data-masking

**类型**: Boolean  
**默认值**: `false`  
**描述**: 是否启用数据脱敏

#### mask-labels

**类型**: List<String>  
**默认值**: `[]`  
**描述**: 需要脱敏的标签名称列表

## Spring Actuator 配置

### management.endpoints.web.exposure.include

**类型**: String  
**默认值**: `"health,info"`  
**描述**: 暴露的端点列表

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
        # include: "*"  # 暴露所有端点（仅开发环境）
```

### management.endpoint.prometheus.cache.time-to-live

**类型**: Duration  
**默认值**: `10s`  
**描述**: Prometheus 端点缓存时间

```yaml
management:
  endpoint:
    prometheus:
      cache:
        time-to-live: 10s  # 10 秒缓存
        # time-to-live: 0s # 禁用缓存
        # time-to-live: 60s # 1 分钟缓存
```

### management.metrics.export.prometheus

**类型**: Object
**描述**: Prometheus 导出配置

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
        pushgateway:
          enabled: false
          base-url: http://localhost:9091
```

## 指标批量上报配置

JAiRouter 支持将 Micrometer 指标批量上报到外部系统，包括 HTTP 端点（如 Prometheus Pushgateway、InfluxDB）和本地文件。

### 配置项说明

#### monitoring.metrics.export.http

**类型**: Object
**描述**: HTTP 端点批量上报配置

```yaml
monitoring:
  metrics:
    export:
      http:
        enabled: false                    # 是否启用 HTTP 上报
        url: ""                           # 上报端点 URL
        timeout: 5000                     # 请求超时时间（毫秒）
        format: prometheus                # 上报格式：prometheus 或 json
```

**字段说明**:

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `enabled` | Boolean | false | 是否启用 HTTP 上报 |
| `url` | String | "" | 上报端点 URL，例如 `http://localhost:9091/metrics/job/jairouter` |
| `timeout` | Integer | 5000 | HTTP 请求超时时间（毫秒） |
| `format` | String | prometheus | 上报格式，支持 `prometheus`（Prometheus 文本格式）或 `json`（通用 JSON 格式） |

**Prometheus Pushgateway 示例**:

```yaml
# Prometheus Pushgateway 示例配置
monitoring:
  metrics:
    export:
      http:
        enabled: true
        url: "http://your-pushgateway-host:9091/metrics/job/jairouter/instance/${HOSTNAME}"
        timeout: 5000
        format: prometheus
```

**InfluxDB 示例配置**:

```yaml
# InfluxDB 示例配置
monitoring:
  metrics:
    export:
      http:
        enabled: true
        url: "http://your-influxdb-host:8086/api/v2/write?org=myorg&bucket=metrics"
        timeout: 10000
        format: json
```

#### monitoring.metrics.export.file

**类型**: Object
**描述**: 文件批量上报配置

```yaml
monitoring:
  metrics:
    export:
      file:
        enabled: false                    # 是否启用文件上报
        path: "./logs/metrics"            # 指标文件存储路径
        format: json                      # 文件格式：json 或 prometheus
        max-size-mb: 100                  # 单文件最大大小（MB）
```

**字段说明**:

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `enabled` | Boolean | false | 是否启用文件上报 |
| `path` | String | "./logs/metrics" | 指标文件存储目录路径 |
| `format` | String | json | 文件格式，支持 `json` 或 `prometheus` |
| `max-size-mb` | Integer | 100 | 单个指标文件最大大小（MB），超过后自动滚动创建新文件 |

**JSON 格式输出示例**:

```json
{
  "metrics": [
    {
      "name": "jairouter_requests_total",
      "type": "counter",
      "value": 12345.0,
      "tags": {"service": "chat", "status": "200"},
      "timestamp": 1719123456789
    }
  ],
  "timestamp": "2026-06-23T10:30:00Z",
  "source": "jairouter",
  "count": 1
}
```

**Prometheus 格式输出示例**:

```text
jairouter_requests_total{service="chat",status="200"} 12345.0 1719123456789
jairouter_backend_latency_seconds{adapter="gpustack",operation="chat"} 0.256 1719123456789
```

### 批量上报原理

`MetricsBatchReporter` 组件实现了高效的指标批量上报机制：

1. **收集阶段**：每 10 秒从 `MeterRegistry` 收集指标到内存缓冲区
2. **聚合阶段**：指标在缓冲区中聚合，减少上报次数
3. **上报阶段**：每分钟批量上报一次，支持 HTTP 和文件两种方式
4. **性能提升**：上报次数从 N 次/秒降低到 1 次/分钟，网络开销降低约 95%

### 上报目标切换

可通过 API 或配置动态切换上报目标：

```java
// 切换到 HTTP 上报
metricsBatchReporter.setReportTarget(ReportTarget.HTTP);

// 切换到文件上报
metricsBatchReporter.setReportTarget(ReportTarget.FILE);

// 切换到日志上报（默认）
metricsBatchReporter.setReportTarget(ReportTarget.LOG);
```

### 完整配置示例

```yaml
monitoring:
  metrics:
    enabled: true
    prefix: "jairouter"
    
    # 批量上报配置
    export:
      # HTTP 上报（如 Prometheus Pushgateway）
      http:
        enabled: true
        url: "http://pushgateway:9091/metrics/job/jairouter"
        timeout: 5000
        format: prometheus
      
      # 文件上报（备份或离线分析）
      file:
        enabled: true
        path: "./logs/metrics"
        format: json
        max-size-mb: 100
```

## 环境特定配置

### 开发环境配置

```yaml
# application-dev.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
      infrastructure-metrics: 1.0
    performance:
      async-processing: false  # 便于调试
      batch-size: 100
    security:
      data-masking: false

management:
  endpoints:
    web:
      exposure:
        include: "*"  # 开发环境暴露所有端点
  endpoint:
    prometheus:
      cache:
        time-to-live: 1s  # 减少缓存时间便于测试
```

### 测试环境配置

```yaml
# application-test.yml
monitoring:
  metrics:
    enabled: true
    prefix: "test_jairouter"
    sampling:
      request-metrics: 0.1  # 降低采样率减少测试干扰
      backend-metrics: 0.5
    performance:
      async-processing: true
      batch-size: 50
    memory:
      cache-size: 1000

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

### 生产环境配置

```yaml
# application-prod.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
      infrastructure-metrics: 0.1
      system-metrics: 0.5
    performance:
      async-processing: true
      batch-size: 1000
      buffer-size: 5000
    memory:
      cache-size: 20000
      memory-threshold: 85
      low-memory-sampling-rate: 0.01
    security:
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key
      ip-masking: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      cache:
        time-to-live: 30s
  security:
    enabled: true
```

## 动态配置

### 运行时配置更新

JAiRouter 支持运行时动态更新监控配置：

```bash
# 更新采样率
curl -X POST http://localhost:8080/actuator/monitoring/config \
  -H "Content-Type: application/json" \
  -d '{
    "sampling": {
      "request-metrics": 0.5,
      "backend-metrics": 0.8
    }
  }'

# 启用/禁用指标类别
curl -X POST http://localhost:8080/actuator/monitoring/categories \
  -H "Content-Type: application/json" \
  -d '{
    "enabled-categories": ["system", "business"]
  }'

# 更新性能配置
curl -X POST http://localhost:8080/actuator/monitoring/performance \
  -H "Content-Type: application/json" \
  -d '{
    "batch-size": 200,
    "buffer-size": 1000
  }'
```

### 配置文件热重载

支持通过配置文件更新监控配置：

```yaml
# config/monitoring-override.yml
monitoring:
  metrics:
    sampling:
      request-metrics: 0.3
    performance:
      batch-size: 200
```

系统会自动检测配置文件变化并应用新配置。

## 配置验证

### 配置语法验证

```bash
# 验证 YAML 语法
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=classpath:/application.yml --spring.profiles.active=test"
```

### 配置有效性检查

```bash
# 检查当前配置
curl http://localhost:8080/actuator/monitoring/config

# 检查指标收集状态
curl http://localhost:8080/actuator/monitoring/status

# 验证端点可访问性
curl http://localhost:8080/actuator/prometheus
```

## 配置最佳实践

### 1. 分环境配置

- **开发环境**: 启用所有指标，便于调试
- **测试环境**: 降低采样率，减少测试干扰
- **生产环境**: 平衡性能和监控精度

### 2. 性能优化配置

```yaml
# 高性能配置
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1
    performance:
      async-processing: true
      batch-size: 1000
      buffer-size: 5000
    memory:
      cache-size: 20000
```

### 3. 安全配置

```yaml
# 安全配置
monitoring:
  metrics:
    security:
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key

management:
  security:
    enabled: true
  server:
    port: 8081
    address: 127.0.0.1
```

### 4. 监控配置

```yaml
# 监控监控系统
monitoring:
  metrics:
    custom-tags:
      monitoring_version: "1.0"
    enabled-categories:
      - system
      - monitoring  # 监控系统自身的指标
```

## 故障排查配置

### 调试配置

```yaml
# 启用调试模式
logging:
  level:
    org.unreal.modelrouter.monitoring: DEBUG
    io.micrometer: DEBUG

monitoring:
  metrics:
    debug:
      enabled: true
      log-metrics: true
      log-interval: 30s
```

### 问题诊断配置

```yaml
# 诊断配置
monitoring:
  metrics:
    diagnostics:
      enabled: true
      collect-jvm-metrics: true
      collect-system-metrics: true
      health-check-interval: 10s
```

## 配置模板

### 基础模板

```yaml
# 基础监控配置模板
monitoring:
  metrics:
    enabled: true
    prefix: "jairouter"
    enabled-categories:
      - system
      - business
      - infrastructure
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
    performance:
      async-processing: true
      batch-size: 500

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      cache:
        time-to-live: 10s
```

### 高性能模板

```yaml
# 高性能监控配置模板
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
      memory-threshold: 85
```

### 安全模板

```yaml
# 安全监控配置模板
monitoring:
  metrics:
    enabled: true
    security:
      data-masking: true
      mask-labels:
        - user_id
        - client_ip
        - api_key
      ip-masking: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  security:
    enabled: true
  server:
    port: 8081
    address: 127.0.0.1
```

## 相关文档

- [监控设置指南](setup.md)
- [性能优化指南](performance.md)
- [故障排查指南](troubleshooting.md)
- [监控指标参考](metrics.md)

---

**提示**: 建议根据实际环境和需求选择合适的配置模板，并根据系统运行情况持续优化配置参数。
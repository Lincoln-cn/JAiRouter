# 配置参考

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-06-02
> **Git 提交**: b305e6de
> **作者**: Lincoln
<!-- /版本信息 -->

本文档提供 JAiRouter 分布式追踪功能的完整配置参考。

## 配置文件结构

JAiRouter 使用模块化的配置管理方式，追踪配置位于独立的配置文件中：

- **主配置文件**: `src/main/resources/application.yml`
- **追踪配置文件**: `src/main/resources/config/tracing/tracing-base.yml`
- **环境配置文件**: `src/main/resources/application-{profile}.yml`

## 模块化配置说明

追踪配置已从主配置文件中分离，通过 `spring.config.import` 机制导入：

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/tracing/tracing-base.yml
```

> **权威来源**：本页的键名以 `src/main/resources/config/tracing/tracing-base.yml` 为准 —— 那份文件
> 有 `TracingConfigBindingGuardTest` 守着「键必须能绑到字段」。本页已于 issue #231 按代码逐条核对过
> 键名/路径与"能力是否存在"，但仍有两类信息没有逐行复核（见文末「本页的已知缺口」）。
>
> ⚠️ **注意区分「键存在」与「调了有用」**：tracing 配置里有一批字段**能绑定但没有任何消费方**
> （issue #224 / #231 都在处理），本页已在相关小节就地标注。

## 基础配置

### 启用追踪

```yaml
jairouter:
  tracing:
    enabled: true                    # 是否启用追踪功能，默认: true
    service-name: "jairouter"       # 服务名称，默认: "jairouter"
    service-version: "1.0.0"        # 服务版本，默认: "1.0.0"
```

### 基本配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|---------|------|
| `enabled` | boolean | `true` | 是否启用追踪功能 |
| `service-name` | string | `"jairouter"` | 服务名称，用于标识追踪源 |
| `service-version` | string | `"1.0.0"` | 服务版本号 |
| `service-namespace` | string | `"production"` | 服务命名空间（**原先这张表写的 `environment` 字段不存在**，见 issue #231） |

## 采样配置

### 比率采样

```yaml
jairouter:
  tracing:
    sampling:
      # 核对（issue #231）：没有 strategy 这个键，采样率就是 ratio（字段默认 1.0，
      # 仓库 tracing-base.yml 配的是 0.1）。
      ratio: 0.1                     # 采样率 0.0-1.0
```

### 规则采样

```yaml
jairouter:
  tracing:
    sampling:
      # 核对（issue #231）：没有 strategy 键；一条规则只有两个字段 —— condition 与 ratio。
      rules:
        - condition: "http.status_code >= 500"   # 属性表达式，支持 >= / <= / == / !=
          ratio: 1.0                             # 命中时按此采样率
        - condition: "http.route == /api/v1/chat/completions"
          ratio: 0.5
```

**匹配语义**（`RuleBasedSamplingStrategy.matchesRule` / `SamplingConfigurationValidator.validateRuleCondition`）：
按 `condition` 里的比较运算符（`>=`、`<=`、`==`、`!=`）与 span 属性逐条比较，**按声明顺序取第一条命中**的规则；
`condition` 里没有这些运算符会被判为疑似无效并给出告警。

> ⚠️ 现状（issue #231 实测核对）：`SamplingStrategyManager#refreshStrategies` 目前把 `ratio_based` 与
> `rule_based` 都注册成同一个 `traceIdRatioBased(ratio)`，**并没有读取 `rules`** —— 也就是说规则采样
> "配得进去、但当前不参与采样决策"。是否需要接线由 issue #231 跟踪。
>
> 原先这张表（`service` / `operation` / `path-pattern` / `method` / `header-name` / `header-value` /
> `error-only` / `status-code`）八个条件在代码里都不存在，已删除。


### 自适应采样

```yaml
jairouter:
  tracing:
    sampling:
      # 核对（issue #231）：没有 strategy 键；自适应采样只有下面五个字段。
      adaptive:
        enabled: false                # 是否启用，默认: false
        target-spans-per-second: 1000 # 目标每秒 Span 数，默认: 1000
        min-ratio: 0.1                # 采样率下限，默认: 0.1
        max-ratio: 1.0                # 采样率上限，默认: 1.0
        adjustment-interval: 30       # 调整间隔（**整数秒**），默认: 30
```

> ⚠️ 现状（issue #231 实测核对）：`AdaptiveSamplingStrategy` 这个类**只被测试实例化过**，
> 生产链路上 `SamplingStrategyManager` 只把全局 `ratio` 接到采样器（`adaptive.enabled` 为真时也只是
> 用 `min-ratio`/`max-ratio` 对 `ratio` 做一次区间夹取，是一段静态值，不是自适应闭环）。
> 因此上面这四个字段目前**只被校验与 `/actuator/info` 回显**，不改变采样行为。
> 接线与否由 issue #231 跟踪。

## 导出器配置

### 日志导出器

```yaml
jairouter:
  tracing:
    exporter:
      type: "logging"
      logging:
        # 核对（issue #231）：LoggingExporterConfig 只有 enabled / level 两个字段，
        # 原 format / include-resource 不存在，已删除。
        enabled: false               # 是否启用该导出器，默认: false
        level: "INFO"                # 日志级别，默认: INFO
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
        # 核对（issue #231）：JaegerConfig 只有 endpoint / timeout / headers，没有 compression
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
        # 核对（issue #231）：ZipkinConfig 只有 endpoint / timeout，没有 compression
```

### OTLP 导出器

```yaml
jairouter:
  tracing:
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://localhost:4317"  # OTLP端点（gRPC）
        timeout: 10s                 # 超时时间，默认: 10s
        compression: "gzip"          # 压缩方式
        headers:                     # 自定义头部
          "api-key": "your-api-key"
        # 核对（issue #231）：OtlpConfig 只有 endpoint / timeout / compression / headers，
        # 原 protocol 与整段 tls.* 都不存在，已删除。
```

### 批处理配置

```yaml
jairouter:
  tracing:
    # 核对（issue #231）：批处理器参数**在 performance 下**，不是 open-telemetry 下。
    # 这里原先写的 open-telemetry.sdk.trace.processors.batch.* 确实能绑定，
    # 但没有任何生产代码读它 —— 与实际装配的键完全对不上，字段已在 #224 删除。
    # 真实装配见 OpenTelemetryAutoConfiguration#tracerProvider。
    performance:
      batch:
        size: 2048                   # 最大导出一批的 Span 数（setMaxExportBatchSize），默认: 100
        timeout: 30s                 # 导出超时（同时用作调度延迟），默认: 5s
      buffer:
        size: 8192                   # 队列上限（setMaxQueueSize 取的就是它），默认: 1024
```

## 内存管理配置

```yaml
jairouter:
  tracing:
    performance:
      # 核对（issue #231）：真实路径是 performance.memory，且只有下面三个字段。
      # 原 max-spans / cleanup-interval / span-ttl / memory-threshold / gc-pressure-threshold
      # 以及整段 cache.* 都不存在，已删除。
      memory:
        max-spans-in-memory: 10000   # 内存中最大 Span 数量，默认: 10000
        memory-limit-mb: 100         # 内存上限(MB)，越过即触发回收，默认: 100
        gc-interval: 60s             # 内存压力检查间隔，默认: 60s
```

## 性能配置

```yaml
jairouter:
  tracing:
    performance:
      async-processing: true       # 异步处理，默认: true
      # 异步处理线程池（issue #215：原文档写的 batch-size / buffer-size / max-queue-size
      # 在 performance 下都不是真实字段，照它配置不会生效）
      # 内存/性能监控两个调度器的上限（由构造器参数 @Value 读取，没有对应字段）
      scheduler:
        thread-cap: 2              # 线程上限，默认: 2
        queue-capacity: 100        # 队列上限，默认: 100

      thread-pool:
        core-size: 8               # boundedElastic 的 threadCap（即线程上限），默认: 2
        queue-capacity: 8192       # 队列上限，默认: 1000
        keep-alive: 60s            # 空闲线程存活时间（TTL），默认: 60s（#220 才接上消费方）
        thread-name-prefix: "tracing-"  # 线程名前缀，默认: "tracing-"
        # 注意：类里还有 max-size，但 boundedElastic 只有一个上限，它只被 /actuator/info 回显
      batch:
        size: 2048                 # 批大小，默认: 2048
        timeout: 30s               # 批超时，默认: 30s
        max-concurrent-batches: 3  # 同时进行的批次数上限，默认: 3
      buffer:
        size: 8192                 # 缓冲区大小，默认: 8192
        flush-interval: 5s         # 刷新间隔，默认: 5s
        max-wait-time: 30s         # 未攒满批次时最长再等多久，默认: 30s
```

## 组件配置

### HTTP 配置

```yaml
jairouter:
  tracing:
    components:
      http:
        enabled: true
        # 以下四个字段目前只被 /actuator/info 回显，不改变采集行为（issue #224）
        capture-headers: true
        capture-body: false
        excluded-paths: []
```

> 核对（issue #231 / #224）：原先这里的 **WebFlux / WebClient / 数据库 / Redis 四段配置在
> `TracingComponentConfig` 里都不存在**（该类只有 http / load-balancer / rate-limiter /
> circuit-breaker；database / cache / messaging 三段因零消费方已在 #224 删除），已全部移除。

### 限流器配置

```yaml
jairouter:
  tracing:
    components:
      rate-limiter:
        # enabled 是真正的行为开关（TracingWrapperFactory 读它）；下面这些 capture-* 目前只被
        # /actuator/info 回显或完全没人读（issue #224），调它们不改变行为。
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
      # 整段开关（无字段、靠 @ConditionalOnProperty，缺省即开启）
      enabled: true
      sanitization:
        enabled: true
        inherit-global-rules: true
        additional-patterns: []
        sensitive-attributes: []      # 参与脱敏的属性名（#224 核对：真实字段）
      access-control:
        # 还有 access-control.enabled（条件键，无字段）；下面两项会回显到 /actuator/info
        restrict-trace-access: true
        allowed-roles: []
      # 核对（issue #231 / #224）：encryption.* 与 audit.* 两段的字段目前**没有任何行为消费方**
      # （encryption 的 enabled/algorithm/key-size 仅回显，audit 整段无人读），故不在此登记；
      # 是否接线或删除见 issue #224。
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
          success-rate: true
          latency: true
          queue-size: true
      health:
        enabled: true
        check-interval: 30s          # 导出器健康检查周期（@Scheduled 也读它），默认: 30s
        failure-threshold: 3         # 连续失败多少次判不健康，默认: 3
        recovery-threshold: 2        # 连续成功多少次判恢复，默认: 2
      alerts:
        enabled: true
        thresholds:
          export-failure-rate: 0.1   # 导出失败率阈值，默认: 0.1
          export-latency-p99: 5000   # P99 导出延迟阈值(ms)，默认: 5000
          memory-usage: 0.8          # 内存使用率阈值，默认: 0.8
          queue-size: 0.9            # 队列使用率阈值，默认: 0.9
```

> 核对（issue #231）：`alerts` 下的 `trace-processing-failures` / `export-failures` /
> `buffer-pressure` 三个键在 #215 就已被删除（无对应字段），这里改为真实的 `thresholds.*`；
> `metrics.exporter` 原来的 `histogram-buckets` 同样不存在，改为真实的 `success-rate` / `latency` /
> `queue-size`。另外 `traces.histogram-buckets` 虽能绑定，但目前没有消费方（issue #224）。

## 环境配置覆盖

不同环境可以通过对应的环境配置文件覆盖追踪配置：

### 开发环境 (application-dev.yml)

```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 1.0  # 开发环境100%采样

# 核对（issue #231）：tracing 段下没有 logging.level 字段，调试用 Spring 自己的日志级别
logging:
  level:
    org.unreal.modelrouter.monitor.tracing: DEBUG
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

1. **基础配置**：在 `tracing-base.yml` 中定义通用配置
2. **环境差异**：在对应的环境配置文件中覆盖特定配置
3. **敏感信息**：使用环境变量注入敏感配置，如导出器端点、认证信息等

### 采样策略

1. **开发环境**：建议使用100%采样以便调试
2. **生产环境**：根据系统负载调整采样率，避免性能影响
3. **关键路径**：对重要业务使用规则采样确保追踪

### 性能优化

1. **批处理**：`performance.batch.*` 与 `performance.buffer.size` 同时决定导出批次行为，
   两者一起调
2. **内存管理**：`performance.memory.memory-limit-mb` 是回收的触发点，`gc-interval` 决定检查频率
3. **组件选择**：只有 `components.{load-balancer,rate-limiter,circuit-breaker}.enabled` 是真正
   生效的开关

## 本页的已知缺口（issue #231）

本页修的是**键名/路径**与**能力是否存在**这两类问题（原始清单见 issue #231）。以下内容尚未逐行复核：

- 各键在散文里描述的**默认值**：本页注明的是 config 类的字段默认值，而仓库的
  `tracing-base.yml` 在若干键上另有取值（例如 `sampling.ratio` 类默认 1.0、yml 配 0.1），
  两者不一致时以你实际加载的配置文件为准；
- 告警示例（PromQL）、命令行与 curl 示例；
- 「最佳实践」一节属经验建议，不含可机械核对的事实。

另外，核对过程中发现**两个能力层面的缺口**（不是文档问题，已分别留痕）：

1. `sampling.rules` 与 `sampling.adaptive.*`：配置能绑定，但
   `SamplingStrategyManager#refreshStrategies` 只把全局 `ratio` 接到采样器上 ⇒ 规则采样与自适应采样
   当前不参与决策；
2. `security.audit.*`、`security.encryption.*`：整段没有行为消费方（issue #224）。

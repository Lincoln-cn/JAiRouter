# 监控指标参考

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档详细描述了 JAiRouter 系统中所有可用的监控指标，包括指标定义、标签说明、使用场景和查询示例。

## 指标分类

JAiRouter 监控指标按功能分为以下几个类别：

| 类别 | 前缀 | 描述 | 用途 |
|------|------|------|------|
| 系统指标 | `jairouter_system_*` | JVM、HTTP、系统资源相关指标 | 系统健康监控 |
| 业务指标 | `jairouter_business_*` | AI 模型服务、用户请求相关指标 | 业务分析和优化 |
| 基础设施指标 | `jairouter_infrastructure_*` | 负载均衡、限流、熔断相关指标 | 基础设施监控 |
| 请求指标 | `jairouter_requests_*` | HTTP 请求统计指标 | 性能和可用性监控 |
| 后端调用指标 | `jairouter_backend_*` | 后端适配器调用指标 | 后端服务监控 |

## HTTP 请求指标

### jairouter_requests_total
**类型**: Counter  
**描述**: HTTP 请求总数统计  
**单位**: 次数

**标签**:
- `service`: 服务类型 (chat, embedding, rerank, tts, stt, image)
- `method`: HTTP 方法 (GET, POST, PUT, DELETE)
- `status`: HTTP 状态码 (200, 400, 404, 500 等)
- `path`: 请求路径
- `client_type`: 客户端类型 (web, mobile, api)

**使用场景**:
- 计算请求率和吞吐量
- 分析服务使用模式
- 监控错误率

**查询示例**:
```promql
# 总请求率 (RPS)
sum(rate(jairouter_requests_total[5m]))

# 按服务类型分组的请求率
sum by (service) (rate(jairouter_requests_total[5m]))

# 错误率计算
sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100

# 成功率计算
sum(rate(jairouter_requests_total{status=~"2.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100
```

### jairouter_request_duration_seconds
**类型**: Histogram  
**描述**: HTTP 请求响应时间分布  
**单位**: 秒

**标签**:
- `service`: 服务类型
- `method`: HTTP 方法
- `status`: HTTP 状态码

**Buckets**: 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, +Inf

**使用场景**:
- 监控响应时间性能
- 设置 SLA 告警
- 性能趋势分析

**查询示例**:
```promql
# P95 响应时间
histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))

# 按服务分组的 P99 响应时间
histogram_quantile(0.99, sum by (service, le) (rate(jairouter_request_duration_seconds_bucket[5m])))

# 平均响应时间
sum(rate(jairouter_request_duration_seconds_sum[5m])) / sum(rate(jairouter_request_duration_seconds_count[5m]))

# 响应时间超过 2 秒的请求比例
sum(rate(jairouter_request_duration_seconds_bucket{le="2"}[5m])) / sum(rate(jairouter_request_duration_seconds_count[5m]))
```

### jairouter_request_size_bytes
**类型**: Histogram  
**描述**: HTTP 请求大小分布  
**单位**: 字节

**标签**:
- `service`: 服务类型
- `method`: HTTP 方法

**Buckets**: 100, 1000, 10000, 100000, 1000000, 10000000, +Inf

**查询示例**:
```promql
# P95 请求大小
histogram_quantile(0.95, sum(rate(jairouter_request_size_bytes_bucket[5m])) by (le))

# 平均请求大小
sum(rate(jairouter_request_size_bytes_sum[5m])) / sum(rate(jairouter_request_size_bytes_count[5m]))

# 大请求（>1MB）的比例
sum(rate(jairouter_request_size_bytes_bucket{le="1000000"}[5m])) / sum(rate(jairouter_request_size_bytes_count[5m]))
```

### jairouter_response_size_bytes
**类型**: Histogram  
**描述**: HTTP 响应大小分布  
**单位**: 字节

**标签**:
- `service`: 服务类型
- `status`: HTTP 状态码

**查询示例**:
```promql
# P95 响应大小
histogram_quantile(0.95, sum(rate(jairouter_response_size_bytes_bucket[5m])) by (le))

# 按服务类型分组的平均响应大小
sum by (service) (rate(jairouter_response_size_bytes_sum[5m])) / sum by (service) (rate(jairouter_response_size_bytes_count[5m]))
```

## 业务指标

### jairouter_model_calls_total
**类型**: Counter  
**描述**: AI 模型调用总数  
**单位**: 次数

**标签**:
- `model_type`: 模型类型 (chat, embedding, rerank)
- `model_name`: 具体模型名称
- `provider`: 模型提供商
- `status`: 调用状态 (success, error, timeout)

**使用场景**:
- 监控模型使用情况
- 分析模型性能
- 计算成功率

**查询示例**:
```promql
# 模型调用率
sum(rate(jairouter_model_calls_total[5m]))

# 按模型类型分组的调用率
sum by (model_type) (rate(jairouter_model_calls_total[5m]))

# 模型调用成功率
sum(rate(jairouter_model_calls_total{status="success"}[5m])) / sum(rate(jairouter_model_calls_total[5m])) * 100

# 最受欢迎的模型
topk(5, sum by (model_name) (rate(jairouter_model_calls_total[5m])))
```

### jairouter_model_call_duration_seconds
**类型**: Histogram  
**描述**: 模型调用响应时间分布  
**单位**: 秒

**标签**:
- `model_type`: 模型类型
- `model_name`: 模型名称
- `provider`: 模型提供商

**Buckets**: 0.1, 0.5, 1, 2, 5, 10, 30, 60, 120, +Inf

**查询示例**:
```promql
# 模型调用 P95 响应时间
histogram_quantile(0.95, sum(rate(jairouter_model_call_duration_seconds_bucket[5m])) by (le))

# 按模型类型分组的平均响应时间
sum by (model_type) (rate(jairouter_model_call_duration_seconds_sum[5m])) / sum by (model_type) (rate(jairouter_model_call_duration_seconds_count[5m]))

# 响应最快的模型
bottomk(5, sum by (model_name) (rate(jairouter_model_call_duration_seconds_sum[5m])) / sum by (model_name) (rate(jairouter_model_call_duration_seconds_count[5m])))
```

### jairouter_user_sessions_active
**类型**: Gauge  
**描述**: 当前活跃用户会话数  
**单位**: 个数

**标签**:
- `service`: 服务类型
- `region`: 地理区域

**查询示例**:
```promql
# 总活跃会话数
sum(jairouter_user_sessions_active)

# 按地区分组的活跃会话数
sum by (region) (jairouter_user_sessions_active)

# 会话数增长率
rate(jairouter_user_sessions_active[5m])
```

### jairouter_user_sessions_total
**类型**: Counter  
**描述**: 用户会话总数  
**单位**: 次数

**标签**:
- `service`: 服务类型
- `session_type`: 会话类型 (new, returning)

**查询示例**:
```promql
# 新会话创建率
sum(rate(jairouter_user_sessions_total{session_type="new"}[5m]))

# 回访用户比例
sum(rate(jairouter_user_sessions_total{session_type="returning"}[5m])) / sum(rate(jairouter_user_sessions_total[5m])) * 100
```

## 基础设施指标

### 负载均衡指标

#### jairouter_loadbalancer_selections_total
**类型**: Counter  
**描述**: 负载均衡器选择后端实例的总次数  
**单位**: 次数

**标签**:
- `service`: 服务名称
- `strategy`: 负载均衡策略 (random, round_robin, least_connections, ip_hash)
- `selected_instance`: 被选中的实例

**查询示例**:
```promql
# 负载均衡选择率
sum(rate(jairouter_loadbalancer_selections_total[5m]))

# 按策略分组的选择分布
sum by (strategy) (rate(jairouter_loadbalancer_selections_total[5m]))

# 实例选择均匀度
stddev by (service) (sum by (service, selected_instance) (rate(jairouter_loadbalancer_selections_total[5m])))

# 最常被选中的实例
topk(5, sum by (selected_instance) (rate(jairouter_loadbalancer_selections_total[5m])))
```

### 限流器指标

#### jairouter_rate_limit_events_total
**类型**: Counter  
**描述**: 限流事件总数  
**单位**: 次数

**标签**:
- `service`: 服务名称
- `algorithm`: 限流算法 (token_bucket, leaky_bucket, sliding_window)
- `result`: 限流结果 (allowed, denied)

**查询示例**:
```promql
# 限流通过率
sum(rate(jairouter_rate_limit_events_total{result="allowed"}[5m])) / sum(rate(jairouter_rate_limit_events_total[5m])) * 100

# 限流拒绝率
sum(rate(jairouter_rate_limit_events_total{result="denied"}[5m])) / sum(rate(jairouter_rate_limit_events_total[5m])) * 100

# 按算法分组的限流效果
sum by (algorithm) (rate(jairouter_rate_limit_events_total{result="denied"}[5m]))
```

#### jairouter_rate_limit_tokens
**类型**: Gauge  
**描述**: 限流器当前可用令牌数  
**单位**: 个数

**标签**:
- `service`: 服务名称
- `algorithm`: 限流算法

**查询示例**:
```promql
# 可用令牌数
jairouter_rate_limit_tokens

# 令牌使用率
(jairouter_rate_limit_tokens_max - jairouter_rate_limit_tokens) / jairouter_rate_limit_tokens_max * 100

# 令牌耗尽的服务
jairouter_rate_limit_tokens == 0
```

### 熔断器指标

#### jairouter_circuit_breaker_state
**类型**: Gauge  
**描述**: 熔断器当前状态  
**单位**: 状态值

**标签**:
- `service`: 服务名称
- `circuit_breaker`: 熔断器名称

**值定义**:
- 0: CLOSED (正常状态)
- 1: OPEN (熔断开启)
- 2: HALF_OPEN (半开状态)

**查询示例**:
```promql
# 熔断器状态
jairouter_circuit_breaker_state

# 熔断器开启的服务数
count(jairouter_circuit_breaker_state == 1)

# 处于半开状态的熔断器
jairouter_circuit_breaker_state == 2
```

#### jairouter_circuit_breaker_events_total
**类型**: Counter  
**描述**: 熔断器事件总数  
**单位**: 次数

**标签**:
- `service`: 服务名称
- `circuit_breaker`: 熔断器名称
- `event`: 事件类型 (success, error, timeout, circuit_open, circuit_close)

**查询示例**:
```promql
# 熔断器成功率
sum(rate(jairouter_circuit_breaker_events_total{event="success"}[5m])) / sum(rate(jairouter_circuit_breaker_events_total{event=~"success|error"}[5m])) * 100

# 熔断器开启频率
sum(rate(jairouter_circuit_breaker_events_total{event="circuit_open"}[5m]))

# 熔断器恢复频率
sum(rate(jairouter_circuit_breaker_events_total{event="circuit_close"}[5m]))
```

#### jairouter_circuit_breaker_failure_rate
**类型**: Gauge  
**描述**: 熔断器当前失败率  
**单位**: 百分比

**标签**:
- `service`: 服务名称
- `circuit_breaker`: 熔断器名称

**查询示例**:
```promql
# 失败率
jairouter_circuit_breaker_failure_rate

# 失败率超过阈值的服务
jairouter_circuit_breaker_failure_rate > 0.5

# 失败率趋势
rate(jairouter_circuit_breaker_failure_rate[5m])
```

## 后端服务指标

### jairouter_backend_health
**类型**: Gauge  
**描述**: 后端服务健康状态  
**单位**: 状态值

**标签**:
- `adapter`: 适配器类型 (gpustack, ollama, vllm)
- `instance`: 实例名称
- `base_url`: 后端服务 URL

**值定义**:
- 1: 健康
- 0: 不健康

**查询示例**:
```promql
# 健康的后端实例数
sum(jairouter_backend_health)

# 不健康的后端实例
jairouter_backend_health == 0

# 按适配器分组的健康实例数
sum by (adapter) (jairouter_backend_health)

# 健康率
sum(jairouter_backend_health) / count(jairouter_backend_health) * 100
```

### jairouter_backend_calls_total
**类型**: Counter  
**描述**: 后端适配器调用总数  
**单位**: 次数

**标签**:
- `adapter`: 适配器类型 (gpustack, ollama, vllm, xinference, localai, openai)
- `instance`: 实例名称
- `method`: 调用方法
- `status`: 调用状态 (success, error, timeout)

**查询示例**:
```promql
# 后端调用率
sum(rate(jairouter_backend_calls_total[5m]))

# 按适配器分组的调用率
sum by (adapter) (rate(jairouter_backend_calls_total[5m]))

# 后端调用成功率
sum(rate(jairouter_backend_calls_total{status="success"}[5m])) / sum(rate(jairouter_backend_calls_total[5m])) * 100

# 最繁忙的后端实例
topk(5, sum by (instance) (rate(jairouter_backend_calls_total[5m])))
```

### jairouter_backend_call_duration_seconds
**类型**: Histogram  
**描述**: 后端调用响应时间分布  
**单位**: 秒

**标签**:
- `adapter`: 适配器类型
- `instance`: 实例名称
- `method`: 调用方法

**Buckets**: 0.1, 0.5, 1, 2, 5, 10, 30, 60, 120, +Inf

**查询示例**:
```promql
# 后端调用 P95 响应时间
histogram_quantile(0.95, sum(rate(jairouter_backend_call_duration_seconds_bucket[5m])) by (le))

# 按适配器分组的平均响应时间
sum by (adapter) (rate(jairouter_backend_call_duration_seconds_sum[5m])) / sum by (adapter) (rate(jairouter_backend_call_duration_seconds_count[5m]))

# 响应最快的适配器
bottomk(3, sum by (adapter) (rate(jairouter_backend_call_duration_seconds_sum[5m])) / sum by (adapter) (rate(jairouter_backend_call_duration_seconds_count[5m])))
```

## JVM 指标

### 内存指标

#### jvm_memory_used_bytes
**类型**: Gauge  
**描述**: JVM 内存使用量  
**单位**: 字节

**标签**:
- `area`: 内存区域 (heap, nonheap)
- `id`: 内存池 ID

**查询示例**:
```promql
# 堆内存使用量
jvm_memory_used_bytes{area="heap"}

# 内存使用率
jvm_memory_used_bytes / jvm_memory_max_bytes * 100

# 内存使用趋势
rate(jvm_memory_used_bytes[5m])
```

#### jvm_memory_max_bytes
**类型**: Gauge  
**描述**: JVM 最大内存容量  
**单位**: 字节

### 垃圾回收指标

#### jvm_gc_pause_seconds
**类型**: Timer  
**描述**: GC 暂停时间  
**单位**: 秒

**标签**:
- `action`: GC 动作
- `cause`: GC 原因

**查询示例**:
```promql
# 平均 GC 暂停时间
sum(rate(jvm_gc_pause_seconds_sum[5m])) / sum(rate(jvm_gc_pause_seconds_count[5m]))

# GC 频率
sum(rate(jvm_gc_pause_seconds_count[5m]))

# GC 时间占比
sum(rate(jvm_gc_pause_seconds_sum[5m])) / 60 * 100
```

### 线程指标

#### jvm_threads_live_threads
**类型**: Gauge  
**描述**: 当前活跃线程数  
**单位**: 个数

**查询示例**:
```promql
# 活跃线程数
jvm_threads_live_threads

# 线程数变化趋势
rate(jvm_threads_live_threads[5m])
```

## 连接池指标

### jairouter_backend_connection_pool_active
**类型**: Gauge  
**描述**: 后端连接池活跃连接数  
**单位**: 个数

**标签**:
- `adapter`: 适配器类型
- `instance`: 实例名称

**查询示例**:
```promql
# 连接池使用情况
jairouter_backend_connection_pool_active

# 连接池使用率
jairouter_backend_connection_pool_active / jairouter_backend_connection_pool_max * 100

# 连接池耗尽的实例
jairouter_backend_connection_pool_active >= jairouter_backend_connection_pool_max
```

## 自定义指标

### 业务特定指标

JAiRouter 支持自定义业务指标，可以通过以下方式添加：

```java
@Component
public class CustomMetrics {
    private final Counter customCounter = Counter.builder("jairouter.custom.counter")
        .description("自定义计数器")
        .tag("type", "business")
        .register(Metrics.globalRegistry);
    
    private final Gauge customGauge = Gauge.builder("jairouter.custom.gauge")
        .description("自定义仪表")
        .register(Metrics.globalRegistry, this, CustomMetrics::getValue);
    
    private final Timer customTimer = Timer.builder("jairouter.custom.timer")
        .description("自定义计时器")
        .register(Metrics.globalRegistry);
}
```

## 常用查询模式

### 错误率计算

```promql
# HTTP 错误率
sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100

# 后端调用错误率
sum(rate(jairouter_backend_calls_total{status="error"}[5m])) / sum(rate(jairouter_backend_calls_total[5m])) * 100

# 模型调用错误率
sum(rate(jairouter_model_calls_total{status!="success"}[5m])) / sum(rate(jairouter_model_calls_total[5m])) * 100
```

### 响应时间分析

```promql
# P50, P95, P99 响应时间
histogram_quantile(0.50, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.99, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))

# 按服务分组的响应时间
histogram_quantile(0.95, sum by (service, le) (rate(jairouter_request_duration_seconds_bucket[5m])))
```

### 吞吐量分析

```promql
# 总吞吐量 (RPS)
sum(rate(jairouter_requests_total[5m]))

# 按服务分组的吞吐量
sum by (service) (rate(jairouter_requests_total[5m]))

# 成功请求吞吐量
sum(rate(jairouter_requests_total{status=~"2.."}[5m]))
```

### 资源使用分析

```promql
# CPU 使用率
rate(process_cpu_seconds_total[5m]) * 100

# 内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# 连接池使用率
jairouter_backend_connection_pool_active / jairouter_backend_connection_pool_max * 100
```

### 服务健康状态

```promql
# 服务可用性
up{job="jairouter"}

# 后端服务健康状态
sum(jairouter_backend_health) / count(jairouter_backend_health) * 100

# 熔断器开启的服务
count(jairouter_circuit_breaker_state == 1)
```

## 告警查询

### 严重告警

```promql
# 服务不可用
up{job="jairouter"} == 0

# 高错误率
sum(rate(jairouter_requests_total{status=~"5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) > 0.05

# 响应时间过长
histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le)) > 5
```

### 警告告警

```promql
# 内存使用率高
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.8

# 连接池使用率高
jairouter_backend_connection_pool_active / jairouter_backend_connection_pool_max > 0.8

# GC 时间过长
sum(rate(jvm_gc_pause_seconds_sum[5m])) / sum(rate(jvm_gc_pause_seconds_count[5m])) > 0.1
```

## 容量规划查询

### 增长趋势

```promql
# 请求量增长趋势
increase(jairouter_requests_total[24h])

# 用户增长趋势
increase(jairouter_user_sessions_total[24h])

# 资源使用增长趋势
increase(jvm_memory_used_bytes[24h])
```

### 峰值分析

```promql
# 日峰值请求率
max_over_time(sum(rate(jairouter_requests_total[5m]))[24h:5m])

# 周峰值响应时间
max_over_time(histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))[7d:1h])
```

## 指标标签最佳实践

### 标签设计原则

1. **低基数**: 避免使用用户 ID、IP 地址等高基数标签
2. **有意义**: 标签应该有助于问题定位和分析
3. **一致性**: 相同含义的标签在不同指标中应该使用相同的名称
4. **层次化**: 使用层次化的标签结构便于聚合查询

### 常用标签

- `service`: 服务类型
- `method`: HTTP 方法或调用方法
- `status`: 状态码或调用结果
- `instance`: 实例标识
- `environment`: 环境标识 (dev, test, prod)
- `version`: 版本号

### 标签使用示例

```promql
# 好的查询 - 使用低基数标签
sum by (service, status) (rate(jairouter_requests_total[5m]))

# 避免的查询 - 使用高基数标签
sum by (client_ip) (rate(jairouter_requests_total[5m]))  # 可能导致高基数问题
```

## Recording Rules

为了提高查询性能，建议使用 Recording Rules 预聚合常用指标：

```yaml
# rules/jairouter-recording-rules.yml
groups:
  - name: jairouter.recording
    interval: 30s
    rules:
      - record: jairouter:request_rate_5m
        expr: sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:error_rate_5m
        expr: sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:response_time_p95_5m
        expr: histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
      
      - record: jairouter:backend_health_ratio
        expr: sum(jairouter_backend_health) / count(jairouter_backend_health)
```

## 相关文档

- [监控设置指南](setup.md)
- [Grafana 仪表板使用指南](dashboards.md)
- [告警配置指南](alerts.md)
- [故障排查指南](troubleshooting.md)

---

**提示**: 定期回顾和优化指标使用，删除不再需要的指标，避免指标膨胀影响系统性能。
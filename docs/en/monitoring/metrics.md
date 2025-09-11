# Monitoring Metrics Reference

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This document provides detailed descriptions of all available monitoring metrics in the JAiRouter system, including metric definitions, label explanations, usage scenarios, and query examples.

## Metric Categories

JAiRouter monitoring metrics are categorized by function into the following groups:

| Category | Prefix | Description | Usage |
|----------|--------|-------------|-------|
| System Metrics | `jairouter_system_*` | JVM, HTTP, and system resource related metrics | System health monitoring |
| Business Metrics | `jairouter_business_*` | AI model services and user request related metrics | Business analysis and optimization |
| Infrastructure Metrics | `jairouter_infrastructure_*` | Load balancing, rate limiting, and circuit breaker related metrics | Infrastructure monitoring |
| Request Metrics | `jairouter_requests_*` | HTTP request statistics | Performance and availability monitoring |
| Backend Call Metrics | `jairouter_backend_*` | Backend adapter call metrics | Backend service monitoring |

## HTTP Request Metrics

### jairouter_requests_total
**Type**: Counter  
**Description**: Total count of HTTP requests  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type (chat, embedding, rerank, tts, stt, image)
- [method](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L368-L368): HTTP method (GET, POST, PUT, DELETE)
- [status](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L370-L370): HTTP status code (200, 400, 404, 500, etc.)
- [path](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L23-L23): Request path
- `client_type`: Client type (web, mobile, api)

**Usage Scenarios**:
- Calculate request rate and throughput
- Analyze service usage patterns
- Monitor error rates

**Query Examples**:
```promql
# Total request rate (RPS)
sum(rate(jairouter_requests_total[5m]))

# Request rate grouped by service type
sum by (service) (rate(jairouter_requests_total[5m]))

# Error rate calculation
sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100

# Success rate calculation
sum(rate(jairouter_requests_total{status=~"2.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100
```

### jairouter_request_duration_seconds
**Type**: Histogram  
**Description**: Distribution of HTTP request response times  
**Unit**: Seconds

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type
- [method](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L368-L368): HTTP method
- [status](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L370-L370): HTTP status code

**Buckets**: 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, +Inf

**Usage Scenarios**:
- Monitor response time performance
- Set SLA alerts
- Performance trend analysis

**Query Examples**:
```promql
# P95 response time
histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))

# P99 response time grouped by service
histogram_quantile(0.99, sum by (service, le) (rate(jairouter_request_duration_seconds_bucket[5m])))

# Average response time
sum(rate(jairouter_request_duration_seconds_sum[5m])) / sum(rate(jairouter_request_duration_seconds_count[5m]))

# Proportion of requests exceeding 2 seconds
sum(rate(jairouter_request_duration_seconds_bucket{le="2"}[5m])) / sum(rate(jairouter_request_duration_seconds_count[5m]))
```

### jairouter_request_size_bytes
**Type**: Histogram  
**Description**: Distribution of HTTP request sizes  
**Unit**: Bytes

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type
- [method](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L368-L368): HTTP method

**Buckets**: 100, 1000, 10000, 100000, 1000000, 10000000, +Inf

**Query Examples**:
```promql
# P95 request size
histogram_quantile(0.95, sum(rate(jairouter_request_size_bytes_bucket[5m])) by (le))

# Average request size
sum(rate(jairouter_request_size_bytes_sum[5m])) / sum(rate(jairouter_request_size_bytes_count[5m]))

# Proportion of large requests (>1MB)
sum(rate(jairouter_request_size_bytes_bucket{le="1000000"}[5m])) / sum(rate(jairouter_request_size_bytes_count[5m]))
```

### jairouter_response_size_bytes
**Type**: Histogram  
**Description**: Distribution of HTTP response sizes  
**Unit**: Bytes

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type
- [status](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L370-L370): HTTP status code

**Query Examples**:
```promql
# P95 response size
histogram_quantile(0.95, sum(rate(jairouter_response_size_bytes_bucket[5m])) by (le))

# Average response size grouped by service type
sum by (service) (rate(jairouter_response_size_bytes_sum[5m])) / sum by (service) (rate(jairouter_response_size_bytes_count[5m]))
```

## Business Metrics

### jairouter_model_calls_total
**Type**: Counter  
**Description**: Total count of AI model calls  
**Unit**: Count

**Labels**:
- `model_type`: Model type (chat, embedding, rerank)
- `model_name`: Specific model name
- `provider`: Model provider
- [status](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L370-L370): Call status (success, error, timeout)

**Usage Scenarios**:
- Monitor model usage
- Analyze model performance
- Calculate success rate

**Query Examples**:
```promql
# Model call rate
sum(rate(jairouter_model_calls_total[5m]))

# Call rate grouped by model type
sum by (model_type) (rate(jairouter_model_calls_total[5m]))

# Model call success rate
sum(rate(jairouter_model_calls_total{status="success"}[5m])) / sum(rate(jairouter_model_calls_total[5m])) * 100

# Most popular models
topk(5, sum by (model_name) (rate(jairouter_model_calls_total[5m])))
```

### jairouter_model_call_duration_seconds
**Type**: Histogram  
**Description**: Distribution of model call response times  
**Unit**: Seconds

**Labels**:
- `model_type`: Model type
- `model_name`: Model name
- `provider`: Model provider

**Buckets**: 0.1, 0.5, 1, 2, 5, 10, 30, 60, 120, +Inf

**Query Examples**:
```promql
# P95 model call response time
histogram_quantile(0.95, sum(rate(jairouter_model_call_duration_seconds_bucket[5m])) by (le))

# Average response time grouped by model type
sum by (model_type) (rate(jairouter_model_call_duration_seconds_sum[5m])) / sum by (model_type) (rate(jairouter_model_call_duration_seconds_count[5m]))

# Fastest responding models
bottomk(5, sum by (model_name) (rate(jairouter_model_call_duration_seconds_sum[5m])) / sum by (model_name) (rate(jairouter_model_call_duration_seconds_count[5m])))
```

### jairouter_user_sessions_active
**Type**: Gauge  
**Description**: Current number of active user sessions  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type
- `region`: Geographic region

**Query Examples**:
```promql
# Total active sessions
sum(jairouter_user_sessions_active)

# Active sessions grouped by region
sum by (region) (jairouter_user_sessions_active)

# Session growth rate
rate(jairouter_user_sessions_active[5m])
```

### jairouter_user_sessions_total
**Type**: Counter  
**Description**: Total number of user sessions  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type
- `session_type`: Session type (new, returning)

**Query Examples**:
```promql
# New session creation rate
sum(rate(jairouter_user_sessions_total{session_type="new"}[5m]))

# Returning user ratio
sum(rate(jairouter_user_sessions_total{session_type="returning"}[5m])) / sum(rate(jairouter_user_sessions_total[5m])) * 100
```

## Infrastructure Metrics

### Load Balancer Metrics

#### jairouter_loadbalancer_selections_total
**Type**: Counter  
**Description**: Total number of times the load balancer selects backend instances  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service name
- [strategy](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L420-L420): Load balancing strategy (random, round_robin, least_connections, ip_hash)
- `selected_instance`: Selected instance

**Query Examples**:
```promql
# Load balancer selection rate
sum(rate(jairouter_loadbalancer_selections_total[5m]))

# Selection distribution grouped by strategy
sum by (strategy) (rate(jairouter_loadbalancer_selections_total[5m]))

# Instance selection uniformity
stddev by (service) (sum by (service, selected_instance) (rate(jairouter_loadbalancer_selections_total[5m])))

# Most frequently selected instances
topk(5, sum by (selected_instance) (rate(jairouter_loadbalancer_selections_total[5m])))
```

### Rate Limiter Metrics

#### jairouter_rate_limit_events_total
**Type**: Counter  
**Description**: Total number of rate limiting events  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service name
- [algorithm](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L396-L396): Rate limiting algorithm (token_bucket, leaky_bucket, sliding_window)
- `result`: Rate limiting result (allowed, denied)

**Query Examples**:
```promql
# Rate limit pass rate
sum(rate(jairouter_rate_limit_events_total{result="allowed"}[5m])) / sum(rate(jairouter_rate_limit_events_total[5m])) * 100

# Rate limit rejection rate
sum(rate(jairouter_rate_limit_events_total{result="denied"}[5m])) / sum(rate(jairouter_rate_limit_events_total[5m])) * 100

# Rate limiting effectiveness grouped by algorithm
sum by (algorithm) (rate(jairouter_rate_limit_events_total{result="denied"}[5m]))
```

#### jairouter_rate_limit_tokens
**Type**: Gauge  
**Description**: Current number of available tokens in the rate limiter  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service name
- [algorithm](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L396-L396): Rate limiting algorithm

**Query Examples**:
```promql
# Available tokens
jairouter_rate_limit_tokens

# Token utilization rate
(jairouter_rate_limit_tokens_max - jairouter_rate_limit_tokens) / jairouter_rate_limit_tokens_max * 100

# Services with exhausted tokens
jairouter_rate_limit_tokens == 0
```

### Circuit Breaker Metrics

#### jairouter_circuit_breaker_state
**Type**: Gauge  
**Description**: Current state of the circuit breaker  
**Unit**: State value

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service name
- `circuit_breaker`: Circuit breaker name

**Value Definition**:
- 0: CLOSED (normal state)
- 1: OPEN (circuit breaker open)
- 2: HALF_OPEN (half-open state)

**Query Examples**:
```promql
# Circuit breaker state
jairouter_circuit_breaker_state

# Number of services with open circuit breakers
count(jairouter_circuit_breaker_state == 1)

# Circuit breakers in half-open state
jairouter_circuit_breaker_state == 2
```

#### jairouter_circuit_breaker_events_total
**Type**: Counter  
**Description**: Total number of circuit breaker events  
**Unit**: Count

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service name
- `circuit_breaker`: Circuit breaker name
- [event](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L409-L409): Event type (success, error, timeout, circuit_open, circuit_close)

**Query Examples**:
```promql
# Circuit breaker success rate
sum(rate(jairouter_circuit_breaker_events_total{event="success"}[5m])) / sum(rate(jairouter_circuit_breaker_events_total{event=~"success|error"}[5m])) * 100

# Circuit breaker opening frequency
sum(rate(jairouter_circuit_breaker_events_total{event="circuit_open"}[5m]))

# Circuit breaker recovery frequency
sum(rate(jairouter_circuit_breaker_events_total{event="circuit_close"}[5m]))
```

#### jairouter_circuit_breaker_failure_rate
**Type**: Gauge  
**Description**: Current failure rate of the circuit breaker  
**Unit**: Percentage

**Labels**:
- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service name
- `circuit_breaker`: Circuit breaker name

**Query Examples**:
```promql
# Failure rate
jairouter_circuit_breaker_failure_rate

# Services with failure rate exceeding threshold
jairouter_circuit_breaker_failure_rate > 0.5

# Failure rate trend
rate(jairouter_circuit_breaker_failure_rate[5m])
```

## Backend Service Metrics

### jairouter_backend_health
**Type**: Gauge  
**Description**: Health status of backend services  
**Unit**: Status value

**Labels**:
- [adapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L381-L381): Adapter type (gpustack, ollama, vllm)
- [instance](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L432-L432): Instance name
- `base_url`: Backend service URL

**Value Definition**:
- 1: Healthy
- 0: Unhealthy

**Query Examples**:
```promql
# Number of healthy backend instances
sum(jairouter_backend_health)

# Unhealthy backend instances
jairouter_backend_health == 0

# Number of healthy instances grouped by adapter
sum by (adapter) (jairouter_backend_health)

# Health rate
sum(jairouter_backend_health) / count(jairouter_backend_health) * 100
```

### jairouter_backend_calls_total
**Type**: Counter  
**Description**: Total number of backend adapter calls  
**Unit**: Count

**Labels**:
- [adapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L381-L381): Adapter type (gpustack, ollama, vllm, xinference, localai, openai)
- [instance](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L432-L432): Instance name
- [method](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L368-L368): Call method
- [status](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L370-L370): Call status (success, error, timeout)

**Query Examples**:
```promql
# Backend call rate
sum(rate(jairouter_backend_calls_total[5m]))

# Call rate grouped by adapter
sum by (adapter) (rate(jairouter_backend_calls_total[5m]))

# Backend call success rate
sum(rate(jairouter_backend_calls_total{status="success"}[5m])) / sum(rate(jairouter_backend_calls_total[5m])) * 100

# Busiest backend instances
topk(5, sum by (instance) (rate(jairouter_backend_calls_total[5m])))
```

### jairouter_backend_call_duration_seconds
**Type**: Histogram  
**Description**: Distribution of backend call response times  
**Unit**: Seconds

**Labels**:
- [adapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L381-L381): Adapter type
- [instance](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L432-L432): Instance name
- [method](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L368-L368): Call method

**Buckets**: 0.1, 0.5, 1, 2, 5, 10, 30, 60, 120, +Inf

**Query Examples**:
```promql
# P95 backend call response time
histogram_quantile(0.95, sum(rate(jairouter_backend_call_duration_seconds_bucket[5m])) by (le))

# Average response time grouped by adapter
sum by (adapter) (rate(jairouter_backend_call_duration_seconds_sum[5m])) / sum by (adapter) (rate(jairouter_backend_call_duration_seconds_count[5m]))

# Fastest responding adapters
bottomk(3, sum by (adapter) (rate(jairouter_backend_call_duration_seconds_sum[5m])) / sum by (adapter) (rate(jairouter_backend_call_duration_seconds_count[5m])))
```

## JVM Metrics

### Memory Metrics

#### jvm_memory_used_bytes
**Type**: Gauge  
**Description**: JVM memory usage  
**Unit**: Bytes

**Labels**:
- `area`: Memory area (heap, nonheap)
- `id`: Memory pool ID

**Query Examples**:
```promql
# Heap memory usage
jvm_memory_used_bytes{area="heap"}

# Memory utilization rate
jvm_memory_used_bytes / jvm_memory_max_bytes * 100

# Memory usage trend
rate(jvm_memory_used_bytes[5m])
```

#### jvm_memory_max_bytes
**Type**: Gauge  
**Description**: Maximum JVM memory capacity  
**Unit**: Bytes

### Garbage Collection Metrics

#### jvm_gc_pause_seconds
**Type**: Timer  
**Description**: GC pause time  
**Unit**: Seconds

**Labels**:
- `action`: GC action
- `cause`: GC cause

**Query Examples**:
```promql
# Average GC pause time
sum(rate(jvm_gc_pause_seconds_sum[5m])) / sum(rate(jvm_gc_pause_seconds_count[5m]))

# GC frequency
sum(rate(jvm_gc_pause_seconds_count[5m]))

# GC time proportion
sum(rate(jvm_gc_pause_seconds_sum[5m])) / 60 * 100
```

### Thread Metrics

#### jvm_threads_live_threads
**Type**: Gauge  
**Description**: Current number of live threads  
**Unit**: Count

**Query Examples**:
```promql
# Live thread count
jvm_threads_live_threads

# Thread count trend
rate(jvm_threads_live_threads[5m])
```

## Connection Pool Metrics

### jairouter_backend_connection_pool_active
**Type**: Gauge  
**Description**: Number of active connections in backend connection pool  
**Unit**: Count

**Labels**:
- [adapter](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L381-L381): Adapter type
- [instance](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L432-L432): Instance name

**Query Examples**:
```promql
# Connection pool usage
jairouter_backend_connection_pool_active

# Connection pool utilization rate
jairouter_backend_connection_pool_active / jairouter_backend_connection_pool_max * 100

# Instances with exhausted connection pools
jairouter_backend_connection_pool_active >= jairouter_backend_connection_pool_max
```

## Custom Metrics

### Business-Specific Metrics

JAiRouter supports custom business metrics, which can be added as follows:

```java
@Component
public class CustomMetrics {
    private final Counter customCounter = Counter.builder("jairouter.custom.counter")
        .description("Custom counter")
        .tag("type", "business")
        .register(Metrics.globalRegistry);
    
    private final Gauge customGauge = Gauge.builder("jairouter.custom.gauge")
        .description("Custom gauge")
        .register(Metrics.globalRegistry, this, CustomMetrics::getValue);
    
    private final Timer customTimer = Timer.builder("jairouter.custom.timer")
        .description("Custom timer")
        .register(Metrics.globalRegistry);
}
```

## Common Query Patterns

### Error Rate Calculation

```promql
# HTTP error rate
sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) * 100

# Backend call error rate
sum(rate(jairouter_backend_calls_total{status="error"}[5m])) / sum(rate(jairouter_backend_calls_total[5m])) * 100

# Model call error rate
sum(rate(jairouter_model_calls_total{status!="success"}[5m])) / sum(rate(jairouter_model_calls_total[5m])) * 100
```

### Response Time Analysis

```promql
# P50, P95, P99 response times
histogram_quantile(0.50, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.99, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))

# Response times grouped by service
histogram_quantile(0.95, sum by (service, le) (rate(jairouter_request_duration_seconds_bucket[5m])))
```

### Throughput Analysis

```promql
# Total throughput (RPS)
sum(rate(jairouter_requests_total[5m]))

# Throughput grouped by service
sum by (service) (rate(jairouter_requests_total[5m]))

# Successful request throughput
sum(rate(jairouter_requests_total{status=~"2.."}[5m]))
```

### Resource Usage Analysis

```promql
# CPU utilization rate
rate(process_cpu_seconds_total[5m]) * 100

# Memory utilization rate
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# Connection pool utilization rate
jairouter_backend_connection_pool_active / jairouter_backend_connection_pool_max * 100
```

### Service Health Status

```promql
# Service availability
up{job="jairouter"}

# Backend service health status
sum(jairouter_backend_health) / count(jairouter_backend_health) * 100

# Services with open circuit breakers
count(jairouter_circuit_breaker_state == 1)
```

## Alert Queries

### Critical Alerts

```promql
# Service unavailable
up{job="jairouter"} == 0

# High error rate
sum(rate(jairouter_requests_total{status=~"5.."}[5m])) / sum(rate(jairouter_requests_total[5m])) > 0.05

# Long response time
histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le)) > 5
```

### Warning Alerts

```promql
# High memory usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.8

# High connection pool usage
jairouter_backend_connection_pool_active / jairouter_backend_connection_pool_max > 0.8

# Long GC time
sum(rate(jvm_gc_pause_seconds_sum[5m])) / sum(rate(jvm_gc_pause_seconds_count[5m])) > 0.1
```

## Capacity Planning Queries

### Growth Trends

```promql
# Request volume growth trend
increase(jairouter_requests_total[24h])

# User growth trend
increase(jairouter_user_sessions_total[24h])

# Resource usage growth trend
increase(jvm_memory_used_bytes[24h])
```

### Peak Analysis

```promql
# Daily peak request rate
max_over_time(sum(rate(jairouter_requests_total[5m]))[24h:5m])

# Weekly peak response time
max_over_time(histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))[7d:1h])
```

## Metric Label Best Practices

### Label Design Principles

1. **Low Cardinality**: Avoid high cardinality labels like user IDs or IP addresses
2. **Meaningful**: Labels should help with problem identification and analysis
3. **Consistent**: Labels with the same meaning should use the same name across different metrics
4. **Hierarchical**: Use hierarchical label structures to facilitate aggregation queries

### Common Labels

- [service](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L367-L367): Service type
- [method](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L368-L368): HTTP method or call method
- [status](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L370-L370): Status code or call result
- [instance](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\AsyncMetricsProcessor.java#L432-L432): Instance identifier
- [environment](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\monitoring\config\DynamicMonitoringConfigUpdater.java#L24-L24): Environment identifier (dev, test, prod)
- [version](file://springfox\documentation\service\ApiInfo.java#L8-L8): Version number

### Label Usage Examples

```promql
# Good query - using low cardinality labels
sum by (service, status) (rate(jairouter_requests_total[5m]))

# Avoided query - using high cardinality labels
sum by (client_ip) (rate(jairouter_requests_total[5m]))  # May cause high cardinality issues
```

## Recording Rules

To improve query performance, it is recommended to use Recording Rules to pre-aggregate common metrics:

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

## Related Documentation

- [Monitoring Setup Guide](setup.md)
- [Grafana Dashboard Usage Guide](dashboards.md)
- [Alert Configuration Guide](alerts.md)
- [Troubleshooting Guide](troubleshooting.md)

---

**Tip**: Regularly review and optimize metric usage, remove metrics that are no longer needed, and avoid metric inflation that could impact system performance.

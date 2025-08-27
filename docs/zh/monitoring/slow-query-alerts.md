# JAiRouter 慢查询告警功能

## 概述

JAiRouter 的慢查询告警功能是一个完整的性能监控和告警系统，能够自动检测系统中的慢操作，并根据配置的策略发送告警通知。该功能集成了分布式追踪、结构化日志记录和 Prometheus 指标导出。

## 功能特性

### 🔍 自动慢查询检测
- 基于可配置阈值的自动慢查询检测
- 支持按操作类型设置不同的检测阈值
- 实时性能指标收集和分析

### 📊 智能告警策略
- 基于频率的告警抑制，避免告警轰炸
- 支持按严重程度分级的告警策略
- 可配置的告警触发条件（最小次数、时间间隔等）

### 📈 性能分析和统计
- 详细的慢查询统计信息（次数、平均时间、最大时间等）
- 性能趋势分析和热点识别
- 操作性能的历史数据追踪

### 🔗 完整的集成支持
- 与分布式追踪系统集成，提供完整的请求链路
- 结构化日志输出，便于日志聚合分析
- Prometheus 指标导出，支持可视化和告警

## 快速开始

### 1. 启用慢查询告警

在 `application.yml` 中配置：

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        min-interval-ms: 300000  # 5分钟最小告警间隔
        min-occurrences: 3       # 3次慢查询后触发告警
        enabled-severities:
          - critical
          - warning
```

### 2. 配置操作特定阈值

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      operations:
        chat_request:
          enabled: true
          min-interval-ms: 180000   # 3分钟
          min-occurrences: 2
          enabled-severities:
            - critical
            - warning
            - info
        
        backend_adapter_call:
          enabled: true
          min-interval-ms: 120000   # 2分钟
          min-occurrences: 3
```

### 3. 查看告警状态

通过 REST API 查看告警统计：

```bash
# 获取慢查询统计
curl http://localhost:8080/api/monitoring/slow-queries/stats

# 获取告警统计
curl http://localhost:8080/api/monitoring/slow-queries/alerts/stats

# 获取告警系统状态
curl http://localhost:8080/api/monitoring/slow-queries/alerts/status
```

## 配置详解

### 全局配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | true | 是否启用慢查询告警 |
| `min-interval-ms` | long | 300000 | 最小告警间隔（毫秒） |
| `min-occurrences` | long | 3 | 触发告警的最小慢查询次数 |
| `enabled-severities` | Set<String> | [critical, warning] | 启用告警的严重程度 |
| `suppression-window-ms` | long | 3600000 | 告警抑制时间窗口 |
| `max-alerts-per-hour` | int | 10 | 每小时最大告警次数 |

### 操作特定配置

可以为不同的操作类型配置不同的告警策略：

```yaml
operations:
  chat_request:              # 聊天请求
    min-interval-ms: 180000
    min-occurrences: 2
    enabled-severities: [critical, warning, info]
  
  embedding_request:         # 嵌入请求
    min-interval-ms: 300000
    min-occurrences: 5
    enabled-severities: [critical, warning]
  
  backend_adapter_call:      # 后端适配器调用
    min-interval-ms: 120000
    min-occurrences: 3
    enabled-severities: [critical, warning]
```

### 严重程度级别

系统自动根据操作耗时与阈值的比值确定严重程度：

- **critical**: 耗时 ≥ 阈值 × 5 倍
- **warning**: 耗时 ≥ 阈值 × 3 倍
- **info**: 耗时 ≥ 阈值 × 1 倍

## 监控指标

### Prometheus 指标

慢查询告警系统导出以下 Prometheus 指标：

```prometheus
# 慢查询总数计数器
slow_query_total{operation="chat_request", severity="warning"}

# 慢查询响应时间分布
slow_query_duration_seconds{operation="chat_request"}

# 慢查询超出阈值倍数
slow_query_threshold_multiplier{operation="chat_request"}

# 慢查询告警触发计数器
slow_query_alert_triggered{operation="chat_request", severity="warning"}

# 活跃的慢查询告警
slow_query_alert_active{operation="chat_request", severity="warning"}
```

### 告警规则示例

在 Prometheus 中配置告警规则：

```yaml
groups:
  - name: jairouter.slow-query-alerts
    rules:
      - alert: JAiRouterSlowQueryDetected
        expr: increase(slow_query_total[5m]) > 0
        for: 0m
        labels:
          severity: warning
        annotations:
          summary: "检测到慢查询操作"
          description: "操作 {{ $labels.operation }} 检测到慢查询"
```

## API 接口

### 慢查询统计 API

```http
GET /api/monitoring/slow-queries/stats
```

返回所有操作的慢查询统计信息。

### 告警统计 API

```http
GET /api/monitoring/slow-queries/alerts/stats
```

返回告警系统的统计信息：

```json
{
  "totalAlertsTriggered": 42,
  "totalAlertsSuppressed": 8,
  "activeAlertKeys": 3,
  "activeOperations": ["chat_request", "embedding_request"],
  "alertTriggerRate": 0.84,
  "alertSuppressionRate": 0.16,
  "averageAlertsPerOperation": 14.0
}
```

### 告警系统状态 API

```http
GET /api/monitoring/slow-queries/alerts/status
```

返回告警系统的运行状态和健康信息。

### 重置统计 API

```http
DELETE /api/monitoring/slow-queries/stats
DELETE /api/monitoring/slow-queries/alerts/stats
```

重置相应的统计信息。

## 日志格式

### 慢查询检测日志

```json
{
  "timestamp": "2025-08-26T10:30:45.123Z",
  "level": "WARN",
  "logger": "org.unreal.modelrouter.monitoring.SlowQueryDetector",
  "message": "Slow query detected - Operation: chat_request, Duration: 2500ms, Threshold: 1000ms",
  "traceId": "1234567890abcdef",
  "spanId": "abcdef1234567890"
}
```

### 慢查询告警日志

```json
{
  "timestamp": "2025-08-26T10:30:45.456Z",
  "level": "INFO",
  "logger": "org.unreal.modelrouter.monitoring.alert.SlowQueryAlertService",
  "message": "慢查询告警已触发",
  "traceId": "1234567890abcdef",
  "spanId": "abcdef1234567890",
  "type": "business_event",
  "event": "slow_query_alert_triggered",
  "fields": {
    "alert_id": "uuid-here",
    "operation_name": "chat_request",
    "severity": "warning",
    "current_duration": 2500,
    "threshold": 1000,
    "threshold_multiplier": 2.5,
    "alert_count": 1,
    "total_occurrences": 5,
    "average_duration": 2200.0,
    "max_duration": 3000
  }
}
```

## 最佳实践

### 1. 阈值配置

- **聊天服务**: 阈值设置为 3-5 秒，考虑 AI 模型响应时间
- **嵌入服务**: 阈值设置为 1-2 秒，通常处理速度较快
- **重排序服务**: 阈值设置为 0.5-1 秒，计算相对简单
- **后端调用**: 阈值设置为网络延迟 + 预期处理时间

### 2. 告警策略

- **开发环境**: 使用较低的阈值和更频繁的告警，便于及时发现问题
- **生产环境**: 使用较高的阈值和告警抑制，避免噪声干扰
- **关键服务**: 启用所有严重程度的告警
- **辅助服务**: 只启用 critical 级别的告警

### 3. 监控集成

- 将慢查询指标集成到 Grafana 仪表板
- 配置 AlertManager 进行告警路由和通知
- 使用 ELK Stack 进行日志聚合和分析
- 定期审查和调整告警阈值

### 4. 故障排除

当收到慢查询告警时，按以下步骤排查：

1. **检查系统资源**: CPU、内存、网络使用情况
2. **分析追踪链路**: 查看完整的请求处理链路
3. **检查后端服务**: 验证后端 AI 服务的健康状态
4. **查看负载情况**: 检查是否存在负载过高的情况
5. **分析日志模式**: 查找相关的错误日志和异常

## 环境配置示例

### 开发环境

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      global:
        min-interval-ms: 60000     # 1分钟
        min-occurrences: 1
        enabled-severities: [critical, warning, info]
        max-alerts-per-hour: 30
```

### 生产环境

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      global:
        min-interval-ms: 900000    # 15分钟
        min-occurrences: 5
        enabled-severities: [critical]
        max-alerts-per-hour: 5
```

## 扩展和自定义

### 自定义告警处理器

可以通过实现自定义的告警处理器来扩展通知机制：

```java
@Component
public class CustomSlowQueryAlertHandler {
    
    @EventListener
    public void handleSlowQueryAlert(SlowQueryAlert alert) {
        // 自定义告警处理逻辑
        // 例如：发送到外部系统、写入数据库等
    }
}
```

### 集成外部监控系统

通过 Webhook 集成外部监控和告警系统：

```yaml
notification:
  enable-webhook: true
  webhook-url: "https://your-monitoring-system.com/api/alerts"
  webhook-headers:
    Authorization: "Bearer your-token"
    Content-Type: "application/json"
```

## 性能影响

慢查询告警系统设计为低开销运行：

- **CPU 开销**: < 1% 在正常负载下
- **内存开销**: < 10MB 用于统计数据存储
- **网络开销**: 最小，仅在触发告警时发送通知
- **存储开销**: 主要是日志文件，可配置轮转策略

## 常见问题

### Q: 为什么没有收到告警？

A: 检查以下配置：
1. 确认 `enabled: true`
2. 检查 `min-occurrences` 是否达到
3. 验证 `enabled-severities` 包含相应级别
4. 确认没有在抑制时间窗口内

### Q: 告警太频繁怎么办？

A: 调整以下参数：
1. 增加 `min-interval-ms`
2. 增加 `min-occurrences`
3. 减少 `max-alerts-per-hour`
4. 调整严重程度级别

### Q: 如何自定义慢查询阈值？

A: 在监控配置中设置：
```yaml
jairouter:
  monitoring:
    thresholds:
      slow-query-thresholds:
        chat_request: 5000    # 5秒
        embedding_request: 2000  # 2秒
```

通过这个完整的慢查询告警系统，JAiRouter 能够提供企业级的性能监控和告警能力，帮助开发和运维团队及时发现和解决性能问题。
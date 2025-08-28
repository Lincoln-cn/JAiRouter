# 错误追踪功能

## 概述

JAiRouter 的错误追踪功能是一个完整的错误监控和分析系统，能够自动收集、聚合和分析系统中的异常信息，提供详细的错误统计和告警功能。

## 配置文件结构

JAiRouter 使用模块化的配置管理方式，错误追踪配置位于独立的配置文件中：

- **主配置文件**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **错误追踪配置文件**: [src/main/resources/config/monitoring/error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml)
- **环境配置文件**: [src/main/resources/application-{profile}.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml)

## 模块化配置说明

错误追踪配置已从主配置文件中分离，通过 `spring.config.import` 机制导入：

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/monitoring/error-tracking.yml
```

## 功能特性

### 📊 错误收集与聚合
- 自动收集系统中的异常信息
- 按错误类型、操作等维度聚合错误信息
- 智能去重和聚合算法

### 🔍 堆栈脱敏与保护
- 自动脱敏敏感信息（密码、密钥等）
- 过滤敏感包路径
- 控制堆栈深度以保护系统信息

### 📈 指标监控
- 错误计数器和分布统计
- 错误持续时间监控
- 按错误类型和操作分组的指标

### 🚨 告警通知
- 集成 Prometheus 指标导出
- 结构化日志输出
- 可配置的告警阈值

## 快速开始

### 1. 启用错误追踪

在 [error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml) 中配置：

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: true
      aggregation-window-minutes: 5
      max-aggregations: 1000
```

### 2. 配置堆栈脱敏

```yaml
jairouter:
  monitoring:
    error-tracking:
      sanitization:
        enabled: true
        max-stack-depth: 20
        sensitive-packages:
          - "org.unreal.modelrouter.security"
          - "org.unreal.modelrouter.auth"
        sensitive-fields:
          - "password"
          - "token"
          - "secret"
```

### 3. 启用指标监控

```yaml
jairouter:
  monitoring:
    error-tracking:
      metrics:
        enabled: true
        group-by-error-type: true
        group-by-operation: true
        record-duration: true
```

## 配置详解

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | false | 是否启用错误追踪 |
| `aggregation-window-minutes` | int | 5 | 错误聚合窗口大小（分钟） |
| `max-aggregations` | int | 1000 | 最大错误聚合数量 |

### 堆栈脱敏配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `sanitization.enabled` | boolean | true | 是否启用堆栈脱敏 |
| `sanitization.max-stack-depth` | int | 20 | 最大堆栈深度 |
| `sanitization.sensitive-packages` | List<String> | [...] | 需要脱敏的包前缀 |
| `sanitization.excluded-packages` | List<String> | [...] | 需要完全过滤的包前缀 |
| `sanitization.sensitive-fields` | List<String> | [...] | 需要脱敏的字段名 |

### 指标配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `metrics.enabled` | boolean | true | 是否启用错误指标 |
| `metrics.counter-prefix` | String | "jairouter.errors" | 错误计数器前缀 |
| `metrics.group-by-error-type` | boolean | true | 是否按错误类型分组 |
| `metrics.group-by-operation` | boolean | true | 是否按操作分组 |
| `metrics.record-duration` | boolean | true | 是否记录错误持续时间 |

### 日志配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `logging.enabled` | boolean | true | 是否记录错误追踪日志 |
| `logging.level` | String | "ERROR" | 错误日志级别 |
| `logging.include-stack-trace` | boolean | true | 是否包含堆栈信息 |
| `logging.include-context` | boolean | true | 是否记录错误上下文 |

## 监控指标

### Prometheus 指标

错误追踪系统导出以下 Prometheus 指标：

```prometheus
# 错误总数计数器
jairouter_errors_total{error_type="NullPointerException", operation="chat_request"}

# 错误持续时间分布
jairouter_errors_duration_seconds{error_type="TimeoutException", operation="embedding_request"}

# 活跃错误聚合
jairouter_errors_active_aggregations{error_type="IllegalArgumentException"}

# 错误聚合数量
jairouter_errors_aggregation_count
```

### 告警规则示例

在 Prometheus 中配置告警规则：

```yaml
groups:
  - name: jairouter.error-tracking
    rules:
      - alert: JAiRouterErrorRateTooHigh
        expr: rate(jairouter_errors_total[5m]) > 10
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "错误率过高"
          description: "5分钟内错误率超过10次/分钟"
      
      - alert: JAiRouterNewErrorTypeDetected
        expr: increase(jairouter_errors_total[10m]) > 0 and changes(jairouter_errors_total[10m]) > 0
        for: 0m
        labels:
          severity: warning
        annotations:
          summary: "检测到新的错误类型"
          description: "系统中出现新的错误类型"
```

## API 接口

### 错误统计 API

```http
GET /api/monitoring/errors/stats
```

返回错误统计信息：

```json
{
  "totalErrors": 42,
  "errorTypes": {
    "NullPointerException": 15,
    "TimeoutException": 12,
    "IllegalArgumentException": 8
  },
  "topOperations": {
    "chat_request": 20,
    "embedding_request": 15
  },
  "aggregationWindowMinutes": 5,
  "activeAggregations": 3
}
```

### 错误详情 API

```http
GET /api/monitoring/errors/details
```

返回详细的错误信息：

```json
{
  "errorType": "NullPointerException",
  "operation": "chat_request",
  "count": 15,
  "firstOccurrence": "2025-08-28T10:30:45Z",
  "lastOccurrence": "2025-08-28T10:35:22Z",
  "sampleStackTrace": "java.lang.NullPointerException: ...",
  "context": {
    "userId": "user123",
    "requestId": "req-456"
  }
}
```

## 环境配置覆盖

不同环境可以通过对应的环境配置文件覆盖错误追踪配置：

### 开发环境 (application-dev.yml)

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: true
      aggregation-window-minutes: 1  # 开发环境更短的聚合窗口
      max-aggregations: 100          # 开发环境更小的聚合数量
      sanitization:
        max-stack-depth: 50          # 开发环境可以显示更多堆栈信息
      logging:
        level: "DEBUG"               # 开发环境详细日志
```

### 生产环境 (application-prod.yml)

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: true
      aggregation-window-minutes: 10  # 生产环境更长的聚合窗口
      max-aggregations: 5000          # 生产环境更大的聚合数量
      sanitization:
        max-stack-depth: 10           # 生产环境更少的堆栈信息
      logging:
        level: "ERROR"                # 生产环境只记录错误日志
```

## 最佳实践

### 配置管理

1. **基础配置**：在 [error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml) 中定义通用配置
2. **环境差异**：在对应的环境配置文件中覆盖特定配置
3. **敏感信息保护**：合理配置堆栈脱敏规则以保护系统安全

### 监控策略

1. **指标监控**：启用关键指标监控并设置合理的告警阈值
2. **日志级别**：根据环境设置合适的日志级别
3. **聚合窗口**：根据系统负载调整聚合窗口大小

### 性能优化

1. **聚合限制**：合理设置最大聚合数量避免内存溢出
2. **堆栈深度**：控制堆栈深度以减少内存使用
3. **采样率**：在高负载环境下考虑采样策略
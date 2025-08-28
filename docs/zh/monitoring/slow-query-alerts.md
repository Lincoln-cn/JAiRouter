# JAiRouter 慢查询告警功能

## 概述

JAiRouter 的慢查询告警功能是一个完整的性能监控和告警系统，能够自动检测系统中的慢操作，并根据配置的策略发送告警通知。该功能集成了分布式追踪、结构化日志记录和 Prometheus 指标导出。

## 配置文件结构

JAiRouter 使用模块化的配置管理方式，慢查询告警配置位于独立的配置文件中：

- **主配置文件**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **慢查询告警配置文件**: [src/main/resources/config/monitoring/slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml)
- **环境配置文件**: [src/main/resources/application-{profile}.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml)

## 模块化配置说明

慢查询告警配置已从主配置文件中分离，通过 `spring.config.import` 机制导入：

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/monitoring/slow-query-alerts.yml
```

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

在 [slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml) 中配置：

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

返回告警系统的当前状态：

```json
{
  "enabled": true,
  "totalOperations": 5,
  "activeAlerts": 3,
  "suppressedAlerts": 1,
  "lastAlertTime": "2025-08-28T10:30:45Z",
  "systemHealth": "HEALTHY"
}
```

## 环境配置覆盖

不同环境可以通过对应的环境配置文件覆盖慢查询告警配置：

### 开发环境 (application-dev.yml)

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        min-interval-ms: 60000    # 开发环境更短的告警间隔
        min-occurrences: 1        # 开发环境更少的触发次数
```

### 生产环境 (application-prod.yml)

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        min-interval-ms: 600000   # 生产环境更长的告警间隔
        max-alerts-per-hour: 50   # 生产环境更高的告警频率限制
```

## 最佳实践

### 配置管理

1. **基础配置**：在 [slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml) 中定义通用配置
2. **环境差异**：在对应的环境配置文件中覆盖特定配置
3. **阈值设置**：根据实际业务需求和性能测试结果设置合理的阈值

### 告警策略

1. **分级告警**：合理使用不同严重程度的告警
2. **抑制策略**：配置适当的告警抑制避免告警轰炸
3. **通知渠道**：根据告警严重程度配置不同的通知渠道

### 性能优化

1. **采样率**：根据系统负载调整采样率
2. **批处理**：合理配置批处理参数
3. **资源监控**：监控慢查询告警系统自身的资源使用情况
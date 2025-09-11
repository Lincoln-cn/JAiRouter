# JAiRouter Slow Query Alert Feature

## Overview

JAiRouter's slow query alert feature is a complete performance monitoring and alerting system that can automatically detect slow operations in the system and send alert notifications based on configured strategies. This feature integrates distributed tracing, structured logging, and Prometheus metric export.

## Configuration File Structure

JAiRouter uses a modular configuration management approach, with slow query alert configuration located in a separate configuration file:

- **Main Configuration File**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **Slow Query Alert Configuration File**: [src/main/resources/config/monitoring/slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml)
- **Environment Configuration Files**: [src/main/resources/application-{profile}.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml)

## Modular Configuration Explanation

Slow query alert configuration has been separated from the main configuration file and is imported through the `spring.config.import` mechanism:

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/monitoring/slow-query-alerts.yml
```

## Features

### 🔍 Automatic Slow Query Detection
- Automatic slow query detection based on configurable thresholds
- Support for setting different detection thresholds by operation type
- Real-time performance metric collection and analysis

### 📊 Intelligent Alert Strategy
- Frequency-based alert suppression to avoid alert flooding
- Support for severity-level alert strategies
- Configurable alert triggering conditions (minimum occurrences, time intervals, etc.)

### 📈 Performance Analysis and Statistics
- Detailed slow query statistics (count, average time, maximum time, etc.)
- Performance trend analysis and hotspot identification
- Historical data tracking of operation performance

### 🔗 Complete Integration Support
- Integration with distributed tracing systems for complete request chains
- Structured log output for easy log aggregation and analysis
- Prometheus metric export for visualization and alerting

## Quick Start

### 1. Enable Slow Query Alerts

Configure in [slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml):

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        min-interval-ms: 300000  # 5-minute minimum alert interval
        min-occurrences: 3       # Trigger alert after 3 slow queries
        enabled-severities:
          - critical
          - warning
```

### 2. Configure Operation-Specific Thresholds

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      operations:
        chat_request:
          enabled: true
          min-interval-ms: 180000   # 3 minutes
          min-occurrences: 2
          enabled-severities:
            - critical
            - warning
            - info
        
        backend_adapter_call:
          enabled: true
          min-interval-ms: 120000   # 2 minutes
          min-occurrences: 3
```

### 3. View Alert Status

Check alert statistics via REST API:

```bash
# Get slow query statistics
curl http://localhost:8080/api/monitoring/slow-queries/stats

# Get alert statistics
curl http://localhost:8080/api/monitoring/slow-queries/alerts/stats

# Get alert system status
curl http://localhost:8080/api/monitoring/slow-queries/alerts/status
```

## Configuration Details

### Global Configuration

| Configuration Item | Type | Default Value | Description |
|--------------------|------|---------------|-------------|
| [enabled](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\config\TraceConfig.java#L12-L12) | boolean | true | Whether to enable slow query alerts |
| `min-interval-ms` | long | 300000 | Minimum alert interval (milliseconds) |
| `min-occurrences` | long | 3 | Minimum slow query occurrences to trigger alert |
| `enabled-severities` | Set<String> | [critical, warning] | Enabled alert severity levels |
| `suppression-window-ms` | long | 3600000 | Alert suppression time window |
| `max-alerts-per-hour` | int | 10 | Maximum alerts per hour |

### Operation-Specific Configuration

Different alert strategies can be configured for different operation types:

```yaml
operations:
  chat_request:              # Chat request
    min-interval-ms: 180000
    min-occurrences: 2
    enabled-severities: [critical, warning, info]
  
  embedding_request:         # Embedding request
    min-interval-ms: 300000
    min-occurrences: 5
    enabled-severities: [critical, warning]
  
  backend_adapter_call:      # Backend adapter call
    min-interval-ms: 120000
    min-occurrences: 3
    enabled-severities: [critical, warning]
```

### Severity Levels

The system automatically determines severity based on the ratio of operation duration to threshold:

- **critical**: Duration ≥ threshold × 5
- **warning**: Duration ≥ threshold × 3
- **info**: Duration ≥ threshold × 1

## Monitoring Metrics

### Prometheus Metrics

The slow query alert system exports the following Prometheus metrics:

```prometheus
# Slow query total counter
slow_query_total{operation="chat_request", severity="warning"}

# Slow query response time distribution
slow_query_duration_seconds{operation="chat_request"}

# Slow query threshold multiplier
slow_query_threshold_multiplier{operation="chat_request"}

# Slow query alert trigger counter
slow_query_alert_triggered{operation="chat_request", severity="warning"}

# Active slow query alerts
slow_query_alert_active{operation="chat_request", severity="warning"}
```

### Alert Rule Example

Configure alert rules in Prometheus:

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
          summary: "Slow query operation detected"
          description: "Operation {{ $labels.operation }} detected slow query"
```

## API Endpoints

### Slow Query Statistics API

```http
GET /api/monitoring/slow-queries/stats
```

Returns slow query statistics for all operations.

### Alert Statistics API

```http
GET /api/monitoring/slow-queries/alerts/stats
```

Returns statistics for the alert system:

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

### Alert System Status API

```http
GET /api/monitoring/slow-queries/alerts/status
```

Returns the current status of the alert system:

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

## Environment Configuration Overrides

Different environments can override slow query alert configuration through corresponding environment configuration files:

### Development Environment (application-dev.yml)

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        min-interval-ms: 60000    # Shorter alert interval in development environment
        min-occurrences: 1        # Fewer occurrences to trigger in development environment
```

### Production Environment (application-prod.yml)

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        min-interval-ms: 600000   # Longer alert interval in production environment
        max-alerts-per-hour: 50   # Higher alert frequency limit in production environment
```

## Best Practices

### Configuration Management

1. **Base Configuration**: Define common configurations in [slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml)
2. **Environment Differences**: Override specific configurations in corresponding environment configuration files
3. **Threshold Setting**: Set reasonable thresholds based on actual business needs and performance test results

### Alert Strategy

1. **Severity Levels**: Properly use different severity levels of alerts
2. **Suppression Strategy**: Configure appropriate alert suppression to avoid alert flooding
3. **Notification Channels**: Configure different notification channels based on alert severity

### Performance Optimization

1. **Sampling Rate**: Adjust sampling rate according to system load
2. **Batch Processing**: Properly configure batch processing parameters
3. **Resource Monitoring**: Monitor the resource usage of the slow query alert system itself
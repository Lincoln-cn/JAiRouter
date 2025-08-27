# JAiRouter Slow Query Alert Feature

## Overview

JAiRouter's slow query alert feature is a complete performance monitoring and alerting system that can automatically detect slow operations in the system and send alert notifications based on configured strategies. This feature integrates distributed tracing, structured logging, and Prometheus metric export.

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

Configure in [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml):

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

Returns the running status and health information of the alert system.

### Reset Statistics API

```http
DELETE /api/monitoring/slow-queries/stats
DELETE /api/monitoring/slow-queries/alerts/stats
```

Resets the corresponding statistics.

## Log Format

### Slow Query Detection Log

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

### Slow Query Alert Log

```json
{
  "timestamp": "2025-08-26T10:30:45.456Z",
  "level": "INFO",
  "logger": "org.unreal.modelrouter.monitoring.alert.SlowQueryAlertService",
  "message": "Slow query alert triggered",
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

## Best Practices

### 1. Threshold Configuration

- **Chat Service**: Set threshold to 3-5 seconds, considering AI model response time
- **Embedding Service**: Set threshold to 1-2 seconds, typically faster processing
- **Reranking Service**: Set threshold to 0.5-1 seconds, relatively simple computation
- **Backend Calls**: Set threshold to network latency + expected processing time

### 2. Alert Strategy

- **Development Environment**: Use lower thresholds and more frequent alerts for timely issue detection
- **Production Environment**: Use higher thresholds and alert suppression to avoid noise interference
- **Critical Services**: Enable alerts for all severity levels
- **Auxiliary Services**: Enable only critical-level alerts

### 3. Monitoring Integration

- Integrate slow query metrics into Grafana dashboards
- Configure AlertManager for alert routing and notifications
- Use ELK Stack for log aggregation and analysis
- Regularly review and adjust alert thresholds

### 4. Troubleshooting

When receiving slow query alerts, troubleshoot using these steps:

1. **Check System Resources**: CPU, memory, network usage
2. **Analyze Trace Chain**: View complete request processing chain
3. **Check Backend Services**: Verify backend AI service health status
4. **View Load Conditions**: Check for high load situations
5. **Analyze Log Patterns**: Look for related error logs and exceptions

## Environment Configuration Examples

### Development Environment

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      global:
        min-interval-ms: 60000     # 1 minute
        min-occurrences: 1
        enabled-severities: [critical, warning, info]
        max-alerts-per-hour: 30
```

### Production Environment

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      global:
        min-interval-ms: 900000    # 15 minutes
        min-occurrences: 5
        enabled-severities: [critical]
        max-alerts-per-hour: 5
```

## Extensions and Customization

### Custom Alert Handler

Extend the notification mechanism by implementing a custom alert handler:

```java
@Component
public class CustomSlowQueryAlertHandler {
    
    @EventListener
    public void handleSlowQueryAlert(SlowQueryAlert alert) {
        // Custom alert handling logic
        // For example: send to external system, write to database, etc.
    }
}
```

### Integration with External Monitoring Systems

Integrate with external monitoring and alerting systems via Webhook:

```yaml
notification:
  enable-webhook: true
  webhook-url: "https://your-monitoring-system.com/api/alerts"
  webhook-headers:
    Authorization: "Bearer your-token"
    Content-Type: "application/json"
```

## Performance Impact

The slow query alert system is designed for low overhead operation:

- **CPU Overhead**: < 1% under normal load
- **Memory Overhead**: < 10MB for statistics storage
- **Network Overhead**: Minimal, only sends notifications when alerts are triggered
- **Storage Overhead**: Primarily log files, with configurable rotation policies

## Frequently Asked Questions

### Q: Why didn't I receive an alert?

A: Check the following configurations:
1. Confirm `enabled: true`
2. Check if `min-occurrences` has been reached
3. Verify `enabled-severities` includes the corresponding level
4. Confirm not within suppression time window

### Q: Alerts are too frequent, what should I do?

A: Adjust the following parameters:
1. Increase `min-interval-ms`
2. Increase `min-occurrences`
3. Reduce `max-alerts-per-hour`
4. Adjust severity levels

### Q: How to customize slow query thresholds?

A: Set in monitoring configuration:
```yaml
jairouter:
  monitoring:
    thresholds:
      slow-query-thresholds:
        chat_request: 5000    # 5 seconds
        embedding_request: 2000  # 2 seconds
```

With this complete slow query alert system, JAiRouter provides enterprise-level performance monitoring and alerting capabilities, helping development and operations teams promptly identify and resolve performance issues.

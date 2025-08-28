# Error Tracking Feature

## Overview

JAiRouter's error tracking feature is a complete error monitoring and analysis system that can automatically collect, aggregate, and analyze exception information in the system, providing detailed error statistics and alerting capabilities.

## Configuration File Structure

JAiRouter uses a modular configuration management approach, with error tracking configuration located in a separate configuration file:

- **Main Configuration File**: [src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **Error Tracking Configuration File**: [src/main/resources/config/monitoring/error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml)
- **Environment Configuration Files**: [src/main/resources/application-{profile}.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml)

## Modular Configuration Explanation

Error tracking configuration has been separated from the main configuration file and is imported through the `spring.config.import` mechanism:

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/monitoring/error-tracking.yml
```

## Features

### 📊 Error Collection and Aggregation
- Automatic collection of exception information in the system
- Aggregation of error information by error type, operation, and other dimensions
- Intelligent deduplication and aggregation algorithms

### 🔍 Stack Trace Sanitization and Protection
- Automatic sanitization of sensitive information (passwords, keys, etc.)
- Filtering of sensitive package paths
- Control of stack depth to protect system information

### 📈 Metric Monitoring
- Error counters and distribution statistics
- Error duration monitoring
- Metrics grouped by error type and operation

### 🚨 Alert Notification
- Integration with Prometheus metric export
- Structured log output
- Configurable alert thresholds

## Quick Start

### 1. Enable Error Tracking

Configure in [error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml):

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: true
      aggregation-window-minutes: 5
      max-aggregations: 1000
```

### 2. Configure Stack Trace Sanitization

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

### 3. Enable Metric Monitoring

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

## Configuration Details

### Basic Configuration

| Configuration Item | Type | Default Value | Description |
|--------------------|------|---------------|-------------|
| `enabled` | boolean | false | Whether to enable error tracking |
| `aggregation-window-minutes` | int | 5 | Error aggregation window size (minutes) |
| `max-aggregations` | int | 1000 | Maximum number of error aggregations |

### Stack Trace Sanitization Configuration

| Configuration Item | Type | Default Value | Description |
|--------------------|------|---------------|-------------|
| `sanitization.enabled` | boolean | true | Whether to enable stack trace sanitization |
| `sanitization.max-stack-depth` | int | 20 | Maximum stack depth |
| `sanitization.sensitive-packages` | List<String> | [...] | Package prefixes that need sanitization |
| `sanitization.excluded-packages` | List<String> | [...] | Package prefixes that need to be completely filtered |
| `sanitization.sensitive-fields` | List<String> | [...] | Field names that need sanitization |

### Metric Configuration

| Configuration Item | Type | Default Value | Description |
|--------------------|------|---------------|-------------|
| `metrics.enabled` | boolean | true | Whether to enable error metrics |
| `metrics.counter-prefix` | String | "jairouter.errors" | Error counter prefix |
| `metrics.group-by-error-type` | boolean | true | Whether to group by error type |
| `metrics.group-by-operation` | boolean | true | Whether to group by operation |
| `metrics.record-duration` | boolean | true | Whether to record error duration |

### Logging Configuration

| Configuration Item | Type | Default Value | Description |
|--------------------|------|---------------|-------------|
| `logging.enabled` | boolean | true | Whether to log error tracking |
| `logging.level` | String | "ERROR" | Error log level |
| `logging.include-stack-trace` | boolean | true | Whether to include stack trace |
| `logging.include-context` | boolean | true | Whether to include error context |

## Monitoring Metrics

### Prometheus Metrics

The error tracking system exports the following Prometheus metrics:

```prometheus
# Total error counter
jairouter_errors_total{error_type="NullPointerException", operation="chat_request"}

# Error duration distribution
jairouter_errors_duration_seconds{error_type="TimeoutException", operation="embedding_request"}

# Active error aggregations
jairouter_errors_active_aggregations{error_type="IllegalArgumentException"}

# Error aggregation count
jairouter_errors_aggregation_count
```

### Alert Rule Example

Configure alert rules in Prometheus:

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
          summary: "Error rate too high"
          description: "Error rate exceeds 10 per minute in 5 minutes"
      
      - alert: JAiRouterNewErrorTypeDetected
        expr: increase(jairouter_errors_total[10m]) > 0 and changes(jairouter_errors_total[10m]) > 0
        for: 0m
        labels:
          severity: warning
        annotations:
          summary: "New error type detected"
          description: "New error type appears in the system"
```

## API Endpoints

### Error Statistics API

```http
GET /api/monitoring/errors/stats
```

Returns error statistics:

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

### Error Details API

```http
GET /api/monitoring/errors/details
```

Returns detailed error information:

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

## Environment Configuration Overrides

Different environments can override error tracking configuration through corresponding environment configuration files:

### Development Environment (application-dev.yml)

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: true
      aggregation-window-minutes: 1  # Shorter aggregation window in development environment
      max-aggregations: 100          # Smaller aggregation count in development environment
      sanitization:
        max-stack-depth: 50          # More stack information can be displayed in development environment
      logging:
        level: "DEBUG"               # Detailed logs in development environment
```

### Production Environment (application-prod.yml)

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: true
      aggregation-window-minutes: 10  # Longer aggregation window in production environment
      max-aggregations: 5000          # Larger aggregation count in production environment
      sanitization:
        max-stack-depth: 10           # Less stack information in production environment
      logging:
        level: "ERROR"                # Only error logs in production environment
```

## Best Practices

### Configuration Management

1. **Base Configuration**: Define common configurations in [error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml)
2. **Environment Differences**: Override specific configurations in corresponding environment configuration files
3. **Sensitive Information Protection**: Properly configure stack trace sanitization rules to protect system security

### Monitoring Strategy

1. **Metric Monitoring**: Enable key metric monitoring and set reasonable alert thresholds
2. **Log Level**: Set appropriate log levels based on environment
3. **Aggregation Window**: Adjust aggregation window size based on system load

### Performance Optimization

1. **Aggregation Limits**: Reasonably set maximum aggregation count to avoid memory overflow
2. **Stack Depth**: Control stack depth to reduce memory usage
3. **Sampling Rate**: Consider sampling strategies in high-load environments
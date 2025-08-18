# Grafana Dashboard Usage Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


This guide provides detailed instructions on how to use JAiRouter's Grafana dashboards for system monitoring, performance analysis, and troubleshooting.

## Quick Start

### Accessing Grafana

1. **Start the Monitoring Stack**
   ```bash
   # Windows
   .\scripts\setup-monitoring.ps1
   
   # Linux/macOS
   ./scripts/setup-monitoring.sh
   ```

2. **Access the Interface**
   - URL: http://localhost:3000
   - Username: admin
   - Password: jairouter2024

3. **Verify Data Sources**
   - Navigate to Configuration → Data Sources
   - Confirm that the Prometheus data source status is green

## Dashboard Overview

JAiRouter provides the following pre-configured dashboards:

| Dashboard Name | Purpose | Update Frequency | Target Audience |
|----------------|---------|------------------|-----------------|
| System Overview | Overall system health and performance | 10 seconds | Operations personnel, managers |
| Business Metrics | AI model service usage | 15 seconds | Business analysts, product managers |
| Infrastructure Monitoring | Load balancing, rate limiting, circuit breaker status | 30 seconds | System engineers, operations personnel |
| Performance Analysis | Detailed performance metrics and trend analysis | 1 minute | Performance engineers, developers |
| Alert Overview | Current alert status and history | Real-time | Operations personnel, on-duty staff |

## System Overview Dashboard

### Main Panels

#### 1. System Status Overview
- **Service Status**: Displays the running status of the JAiRouter service
- **JVM Memory Usage**: Heap and non-heap memory usage
- **CPU Usage**: System and process CPU usage
- **Active Connections**: Current active HTTP connections

#### 2. Request Statistics
- **Total Request Rate**: Requests per second (RPS)
- **Response Time Distribution**: P50, P95, P99 response times
- **Status Code Distribution**: 2xx, 4xx, 5xx status code statistics
- **Error Rate**: Percentage of error requests out of total requests

#### 3. JVM Monitoring
- **Garbage Collection**: GC frequency and duration statistics
- **Thread Status**: Number of active and blocked threads
- **Class Loading**: Number of loaded classes and trends

### Usage Tips

#### Time Range Selection
```
Common time ranges:
- Last 5 minutes: Real-time monitoring
- Last 1 hour: Short-term trend analysis
- Last 24 hours: Daily operational analysis
- Last 7 days: Periodic analysis
```

#### Data Refresh Settings
- **Auto Refresh**: Select intervals of 5s, 10s, 30s, etc.
- **Manual Refresh**: Click the refresh button
- **Pause Refresh**: Click the pause button to freeze current data

#### Panel Interaction
- **Zoom**: Drag to select a time range on the chart
- **Legend**: Click legend items to hide/show corresponding data series
- **Detailed Information**: Hover over data points to view specific values

## Business Metrics Dashboard

### Core Business Metrics

#### 1. Service Type Distribution
**Query**: `sum by (service) (rate(jairouter_requests_total[5m]))`

Displays usage of various AI services:
- **Chat Service**: Chat conversation request statistics
- **Embedding Service**: Vectorization request statistics
- **Rerank Service**: Re-ranking request statistics
- **Other Services**: TTS, STT, image generation, etc.

#### 2. Model Call Success Rate
**Query**: `sum(rate(jairouter_model_calls_total{status="success"}[5m])) / sum(rate(jairouter_model_calls_total[5m])) * 100`

- Displays success rates of various backend adapters
- Shows success rate trends over time
- Sets alert thresholds (usually < 95% requires attention)

#### 3. Request Size Distribution
**Query**: `histogram_quantile(0.95, rate(jairouter_request_size_bytes_bucket[5m]))`

- P50, P95, P99 request sizes
- Displayed grouped by service type
- Identifies requests with abnormal sizes

#### 4. Response Time Analysis
**Query**: `histogram_quantile(0.95, rate(jairouter_request_duration_seconds_bucket[5m]))`

- Response time distribution by service type
- Identifies performance bottlenecks
- Compares performance across different time periods

### Business Insights Panel

#### Usage Pattern Analysis
- **Peak Times**: Identifies business peak hours
- **Service Preferences**: Analyzes most frequently used service types by users
- **Geographic Distribution**: Geographic statistics based on client IP

#### Capacity Planning
- **Growth Trends**: Long-term request volume growth trends
- **Resource Requirements**: Predicts resource needs based on usage patterns
- **Scaling Recommendations**: Provides scaling suggestions based on historical data

## Infrastructure Monitoring Dashboard

### Load Balancer Monitoring

#### 1. Load Balancing Strategy Distribution
**Query**: `sum by (strategy) (jairouter_loadbalancer_selections_total)`

Displays usage of different strategies:
- **Random**: Number of times random strategy is used
- **Round Robin**: Number of times round-robin strategy is used
- **Least Connections**: Number of times least connections strategy is used
- **IP Hash**: Number of times IP hash strategy is used

#### 2. Backend Instance Health Status
**Query**: `jairouter_backend_health`

- Real-time display of backend instance health status
- Statistics of healthy instances
- Alerts for faulty instances

#### 3. Request Distribution Uniformity
**Query**: `sum by (instance) (rate(jairouter_backend_calls_total[5m]))`

- Distribution of requests received by each instance
- Identifies load imbalance issues
- Evaluates load balancing strategy effectiveness

### Rate Limiter Monitoring

#### 1. Rate Limiter Status
**Query**: `jairouter_rate_limit_tokens`

- Available tokens for each service
- Token consumption rate
- Rate limiting trigger frequency

#### 2. Rate Limiting Event Statistics
**Query**: `sum by (result) (rate(jairouter_rate_limit_events_total[5m]))`

- Number of requests passed through
- Number of requests rate-limited
- Rate limiting pass-through rate

### Circuit Breaker Monitoring

#### 1. Circuit Breaker Status
**Query**: `jairouter_circuit_breaker_state`

Status Explanation:
- **CLOSED (0)**: Normal state
- **OPEN (1)**: Circuit breaker open state
- **HALF_OPEN (2)**: Half-open state

#### 2. Circuit Breaker Events
**Query**: `sum by (event) (rate(jairouter_circuit_breaker_events_total[5m]))`

- Successful calls
- Failed calls
- Circuit breaker triggers
- Circuit breaker recovery

## Performance Analysis Dashboard

### Response Time Analysis

#### 1. Response Time Heatmap
- Displays the density distribution of response times
- Identifies periods with performance anomalies
- Compares performance across different services

#### 2. Response Time Trends
- Long-term response time trend analysis
- Performance degradation detection
- Optimization effect verification

### Throughput Analysis

#### 1. Request Throughput
**Query**: `sum(rate(jairouter_requests_total[1m]))`

- Number of requests processed per minute
- Peak throughput records
- Capacity utilization analysis

#### 2. Backend Call Throughput
**Query**: `sum by (adapter) (rate(jairouter_backend_calls_total[1m]))`

- Call frequency of each adapter
- Backend service load analysis
- Bottleneck identification

### Resource Usage Analysis

#### 1. Memory Usage Trends
- JVM heap memory usage
- Memory leak detection
- GC impact analysis

#### 2. Connection Pool Monitoring
- Active connection count
- Connection pool utilization rate
- Connection timeout statistics

## Alert Overview Dashboard

### Current Alert Status

#### 1. Alert Summary
- Number of critical alerts
- Number of warning alerts
- Alert trend chart

#### 2. Alert Details Table
Displayed information:
- Alert name
- Trigger time
- Severity level
- Impact scope
- Handling status

### Alert History Analysis

#### 1. Alert Frequency Statistics
- Trigger frequency of various alerts
- Alert pattern recognition
- System stability assessment

#### 2. Mean Time to Recovery (MTTR)
- Average recovery time for various alerts
- Fault handling efficiency analysis
- Operations team performance evaluation

## Custom Dashboards

### Creating Custom Panels

#### 1. Adding New Panels
1. Click "Add panel" in the top right corner of the dashboard
2. Select panel type (Graph, Stat, Table, etc.)
3. Configure query and display options
4. Save the panel

#### 2. Common Query Examples

**Custom Business Metrics**:
```promql
# Request statistics for specific users
sum by (user_id) (rate(jairouter_requests_total{user_id!=""}[5m]))

# Error rate for specific time periods
sum(rate(jairouter_requests_total{status=~"4..|5.."}[1h])) / sum(rate(jairouter_requests_total[1h])) * 100

# Backend service response time comparison
histogram_quantile(0.95, sum by (adapter, le) (rate(jairouter_backend_call_duration_seconds_bucket[5m])))
```

### Dashboard Template Variables

#### 1. Environment Variable
```
Name: environment
Type: Query
Query: label_values(jairouter_requests_total, environment)
```

#### 2. Service Type Variable
```
Name: service
Type: Query
Query: label_values(jairouter_requests_total, service)
```

#### 3. Time Range Variable
```
Name: time_range
Type: Interval
Options: 1m,5m,15m,1h,6h,24h
```

### Panel Configuration Best Practices

#### 1. Color Configuration
- Use consistent color schemes
- Green indicates normal status
- Red indicates errors or alerts
- Yellow indicates warning status

#### 2. Threshold Settings
- Set reasonable alert thresholds
- Use gradient colors to display different severity levels
- Configure threshold lines for quick identification

#### 3. Units and Formatting
- Time uses seconds, milliseconds
- Size uses bytes, KB, MB
- Percentage uses percent (0-100)
- Rate uses ops/sec, req/sec

## Dashboard Management

### Import and Export

#### Exporting Dashboards
1. Open the dashboard to export
2. Click the settings icon → Export
3. Select export format (JSON)
4. Save the file

#### Importing Dashboards
1. Click "+" → Import
2. Upload JSON file or enter dashboard ID
3. Select data source
4. Click Import

### Permission Management

#### Setting Dashboard Permissions
1. Open dashboard settings
2. Select the Permissions tab
3. Add user or team permissions
4. Set permission levels (View, Edit, Admin)

### Version Control

#### Dashboard Version Management
- Grafana automatically saves dashboard versions
- Can view historical versions and changes
- Supports version rollback functionality

## Troubleshooting

### Common Issues

#### 1. Dashboard Shows "No data"
**Solutions**:
1. Check Prometheus data source connection
2. Verify query syntax
3. Confirm time range settings
4. Check if JAiRouter metric collection is working properly

#### 2. Charts Load Slowly
**Solutions**:
1. Reduce query time range
2. Simplify query expressions
3. Increase refresh interval
4. Use recording rules

#### 3. Alerts Don't Trigger
**Solutions**:
1. Check alert rule configuration
2. Verify threshold settings
3. Confirm Alertmanager configuration
4. Check notification channel settings

### Performance Optimization

#### 1. Query Optimization
- Use appropriate time ranges
- Avoid overly complex queries
- Use recording rules to pre-calculate common metrics

#### 2. Dashboard Optimization
- Limit panel count (recommended < 20)
- Use reasonable refresh intervals
- Avoid high cardinality labels

## Best Practices

### Monitoring Strategy

#### 1. Layered Monitoring
- **L1**: System-level monitoring (availability, performance)
- **L2**: Business-level monitoring (functionality, user experience)
- **L3**: Infrastructure monitoring (resources, component status)

#### 2. Dashboard Organization
- Use folders to organize dashboards
- Categorize by role and purpose
- Regularly clean up unused dashboards

### Team Collaboration

#### 1. Knowledge Sharing
- Add descriptions and documentation to dashboards
- Create operation manuals and troubleshooting procedures
- Conduct regular monitoring training

#### 2. Standardization
- Use unified naming conventions
- Maintain consistent colors and styles
- Establish dashboard templates

## Next Steps

After mastering dashboard usage, you can:

1. [Configure Alert Rules](alerts.md)
2. [Learn About Detailed Metrics](metrics.md)
3. [Perform Troubleshooting](troubleshooting.md)
4. [Optimize Monitoring Performance](performance.md)

---

**Tip**: It's recommended to regularly back up important dashboard configurations and establish an audit process for dashboard changes.

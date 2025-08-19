# Monitoring Troubleshooting Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This document provides diagnosis and solutions for common issues in the JAiRouter monitoring system, helping to quickly locate and fix monitoring-related problems.

## Quick Diagnosis

### Monitoring Health Check

Run the following commands for a quick health check:

```bash
# Windows
.\scripts\monitoring-health-check.ps1

# Linux/macOS
./scripts/monitoring-health-check.sh
```

### Manual Check Steps

```bash
# 1. Check JAiRouter service status
curl http://localhost:8080/actuator/health

# 2. Check metrics endpoint
curl http://localhost:8080/actuator/prometheus

# 3. Check Prometheus status
curl http://localhost:9090/-/healthy

# 4. Check Grafana status
curl http://localhost:3000/api/health

# 5. Check AlertManager status
curl http://localhost:9093/-/healthy
```

## Common Issue Categories

### Metrics Collection Issues

#### Issue 1: Metrics Endpoint Unreachable

**Symptoms**:
- Accessing `/actuator/prometheus` returns 404
- Prometheus targets page shows JAiRouter as down

**Diagnosis Steps**:
```bash
# Check application status
curl http://localhost:8080/actuator/health

# Check endpoint configuration
curl http://localhost:8080/actuator

# Check application logs
docker logs jairouter | grep -i actuator
```

**Solution**:
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    prometheus:
      enabled: true
```

#### Issue 2: Metrics Data Empty or Incomplete

**Symptoms**:
- Prometheus endpoint returns empty data
- Some metrics are missing

**Diagnosis Steps**:
```bash
# Check monitoring configuration
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.monitoringConfiguration'

# Check metrics registry
curl http://localhost:8080/actuator/metrics

# Check specific metrics
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

**Possible Causes and Solutions**:

**Cause 1: Metrics collection disabled**
```yaml
monitoring:
  metrics:
    enabled: true
    enabled-categories:
      - system
      - business
      - infrastructure
```

**Cause 2: Low sampling rate**
```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
```

**Cause 3: Async processing queue full**
```yaml
monitoring:
  metrics:
    performance:
      buffer-size: 2000
      batch-size: 500
```

#### Issue 3: Inaccurate Metrics Data

**Symptoms**:
- Metric values don't match actual conditions
- Metrics update delay

**Diagnosis Steps**:
```bash
# Check time synchronization
date
curl -s http://localhost:9090/api/v1/query?query=time() | jq '.data.result[0].value[1]'

# Check sampling configuration
curl http://localhost:8080/actuator/jairouter-metrics/config

# Check async processing status
curl http://localhost:8080/actuator/jairouter-metrics/status
```

**Solution**:
```yaml
monitoring:
  metrics:
    # Increase sampling rate
    sampling:
      request-metrics: 1.0
    
    # Reduce batch processing delay
    performance:
      batch-size: 100
      processing-timeout: 1s
```

### Prometheus Issues

#### Issue 4: Prometheus Cannot Scrape Metrics

**Symptoms**:
- Prometheus targets page shows errors
- Metric queries return empty results

**Diagnosis Steps**:
```bash
# 1. Check Prometheus configuration
cat monitoring/prometheus/prometheus.yml | grep -A 10 jairouter

# 2. Check network connectivity
docker exec prometheus curl http://jairouter:8080/actuator/prometheus

# 3. Check Prometheus logs
docker logs prometheus | grep -i error
```

**Solutions**:

**Network Issues**:
```yaml
# docker-compose-monitoring.yml
networks:
  monitoring:
    driver: bridge

services:
  jairouter:
    networks:
      - monitoring
  prometheus:
    networks:
      - monitoring
```

**Configuration Issues**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']  # Use service name instead of localhost
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
```

#### Issue 5: Prometheus Storage Space Insufficient

**Symptoms**:
- Prometheus logs show storage errors
- Query responses are slow

**Diagnosis Steps**:
```bash
# Check storage usage
docker exec prometheus df -h /prometheus

# Check data retention configuration
docker exec prometheus cat /etc/prometheus/prometheus.yml | grep retention
```

**Solution**:
```yaml
# prometheus.yml or startup parameters
command:
  - '--storage.tsdb.retention.time=15d'
  - '--storage.tsdb.retention.size=5GB'
  - '--storage.tsdb.path=/prometheus'
```

### Grafana Issues

#### Issue 6: Grafana Dashboard Shows "No data"

**Symptoms**:
- Dashboard panels show "No data"
- Queries return empty results

**Diagnosis Steps**:
```bash
# 1. Check data source connection
curl http://localhost:3000/api/datasources/proxy/1/api/v1/query?query=up

# 2. Check query syntax
curl "http://localhost:9090/api/v1/query?query=jairouter_requests_total"

# 3. Check time range
# Adjust time range to last 5 minutes in Grafana
```

**Solutions**:

**Data Source Configuration**:
```yaml
# grafana/provisioning/datasources/prometheus.yml
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

**Query Optimization**:
```promql
# Ensure query syntax is correct
sum(rate(jairouter_requests_total[5m]))

# Check if labels exist
label_values(jairouter_requests_total, service)
```

#### Issue 7: Grafana Dashboard Loading Slow

**Symptoms**:
- Dashboard loading time is too long
- Query timeouts

**Diagnosis Steps**:
```bash
# Check query complexity
# View query execution time in Grafana Query Inspector

# Check Prometheus performance
curl "http://localhost:9090/api/v1/query?query=prometheus_tsdb_head_samples_appended_total"
```

**Solutions**:

**Query Optimization**:
```promql
# Use recording rules to pre-aggregate data
# rules/jairouter-recording-rules.yml
groups:
  - name: jairouter.recording
    rules:
      - record: jairouter:request_rate_5m
        expr: sum(rate(jairouter_requests_total[5m]))
```

**Panel Optimization**:
- Reduce panel count (< 20 panels)
- Increase refresh interval
- Use appropriate time ranges

### AlertManager Issues

#### Issue 8: Alerts Not Triggered

**Symptoms**:
- Alert conditions met but no notifications received
- Prometheus shows alerts but AlertManager doesn't receive them

**Diagnosis Steps**:
```bash
# 1. Check alert rule status
curl http://localhost:9090/api/v1/rules

# 2. Check AlertManager receive status
curl http://localhost:9093/api/v1/alerts

# 3. Check alert rule syntax
promtool check rules monitoring/prometheus/rules/jairouter-alerts.yml
```

**Solutions**:

**Rule Configuration**:
```yaml
# prometheus.yml
rule_files:
  - "rules/*.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

**Network Connection**:
```bash
# Check connection from Prometheus to AlertManager
docker exec prometheus curl http://alertmanager:9093/-/healthy
```

#### Issue 9: Alert Notifications Not Sent

**Symptoms**:
- AlertManager receives alerts but doesn't send notifications
- Notification channels unresponsive

**Diagnosis Steps**:
```bash
# Check AlertManager configuration
amtool config show

# Check notification history
curl http://localhost:9093/api/v1/alerts

# Check silence rules
amtool silence query
```

**Solutions**:

**Email Configuration**:
```yaml
# alertmanager.yml
global:
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alerts@jairouter.com'
  smtp_auth_username: 'alerts@jairouter.com'
  smtp_auth_password: 'your-password'
```

**Routing Configuration**:
```yaml
route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default'
```

## Performance Issues

### Issue 10: Monitoring Affects Application Performance

**Symptoms**:
- Increased application response time
- Rising CPU usage
- Increased memory usage

**Diagnosis Steps**:
```bash
# Check monitoring overhead
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# Check async processing status
curl http://localhost:8080/actuator/jairouter-metrics/performance

# Compare performance differences with monitoring enabled/disabled
```

**Solutions**:

#### Performance Optimization Configuration
```yaml
monitoring:
  metrics:
    # Enable async processing
    performance:
      async-processing: true
      async-thread-pool-size: 4
      batch-size: 500
      buffer-size: 2000
    
    # Reduce sampling rate
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
      infrastructure-metrics: 0.1
    
    # Optimize cache
    memory:
      cache-size: 5000
      cache-expiry: 2m
```

### Issue 11: Poor Prometheus Query Performance

**Symptoms**:
- Long query response times
- Slow Grafana dashboard loading

**Diagnosis Steps**:
```bash
# Check Prometheus metrics
curl "http://localhost:9090/api/v1/query?query=prometheus_tsdb_head_series"

# Check query complexity
# View query execution time in Prometheus Web UI
```

**Solutions**:

**Optimize Prometheus Configuration**:
```yaml
# monitoring/prometheus/prometheus.yml
global:
  scrape_interval: 30s  # Increase scrape interval
  evaluation_interval: 30s

scrape_configs:
  - job_name: 'jairouter'
    scrape_interval: 15s  # Keep shorter interval for important services
```

**Use Recording Rules**:
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
```

## Data Consistency Issues

### Issue 12: Inconsistent Metrics Data

**Symptoms**:
- Different results when querying the same metric at different times
- Grafana and Prometheus show inconsistent data

**Diagnosis Steps**:
```bash
# Check time synchronization
ntpdate -q pool.ntp.org

# Check Prometheus data integrity
curl "http://localhost:9090/api/v1/query?query=up{job=\"jairouter\"}"

# Check Grafana cache settings
```

**Solutions**:

**Time Synchronization**:
```bash
# Synchronize system time
sudo ntpdate -s pool.ntp.org

# Configure automatic time synchronization
sudo systemctl enable ntp
```

**Cache Configuration**:
```yaml
# application.yml
management:
  endpoint:
    prometheus:
      cache:
        time-to-live: 10s
```

## Log Analysis

### Collecting Diagnostic Information

Create diagnostic script `scripts/collect-monitoring-logs.sh`:

```bash
#!/bin/bash

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_DIR="monitoring-logs-${TIMESTAMP}"

mkdir -p ${LOG_DIR}

echo "Collecting monitoring diagnostic information..."

# Collect service status
echo "=== Service Status ===" > ${LOG_DIR}/service-status.log
docker-compose -f docker-compose-monitoring.yml ps >> ${LOG_DIR}/service-status.log

# Collect configuration information
echo "=== Configuration Information ===" >> troubleshoot.log
curl -s http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.monitoringConfiguration' >> ${LOG_DIR}/config.log

# Collect metrics information
echo "=== Metrics Information ===" > ${LOG_DIR}/metrics.log
curl -s http://localhost:8080/actuator/prometheus | head -100 >> ${LOG_DIR}/metrics.log

# Collect application logs
docker logs jairouter > ${LOG_DIR}/jairouter.log 2>&1
docker logs prometheus > ${LOG_DIR}/prometheus.log 2>&1
docker logs grafana > ${LOG_DIR}/grafana.log 2>&1
docker logs alertmanager > ${LOG_DIR}/alertmanager.log 2>&1

# Collect system information
echo "=== System Information ===" > ${LOG_DIR}/system-info.log
df -h >> ${LOG_DIR}/system-info.log
free -h >> ${LOG_DIR}/system-info.log
docker stats --no-stream >> ${LOG_DIR}/system-info.log

echo "Diagnostic information collected to ${LOG_DIR} directory"
```

### Log Analysis Techniques

#### 1. JAiRouter Application Logs
```bash
# View monitoring-related errors
docker logs jairouter | grep -i "metric\|monitor\|prometheus"

# View performance-related logs
docker logs jairouter | grep -i "performance\|slow\|timeout"

# View configuration loading logs
docker logs jairouter | grep -i "config\|property"
```

#### 2. Prometheus Logs
```bash
# View scrape errors
docker logs prometheus | grep -i "scrape\|target"

# View storage-related issues
docker logs prometheus | grep -i "storage\|tsdb"

# View rule evaluation errors
docker logs prometheus | grep -i "rule\|alert"
```

#### 3. Grafana Logs
```bash
# View data source connection issues
docker logs grafana | grep -i "datasource\|proxy"

# View query errors
docker logs grafana | grep -i "query\|timeout"

# View authentication issues
docker logs grafana | grep -i "auth\|login"
```

## Recovery Procedures

### Monitoring Service Recovery

#### 1. Complete Monitoring Stack Restart
```bash
# Stop all services
docker-compose -f docker-compose-monitoring.yml down

# Clean data (caution)
docker volume prune

# Restart
docker-compose -f docker-compose-monitoring.yml up -d
```

#### 2. Individual Service Restart
```bash
# Restart JAiRouter
docker-compose -f docker-compose-monitoring.yml restart jairouter

# Restart Prometheus
docker-compose -f docker-compose-monitoring.yml restart prometheus

# Restart Grafana
docker-compose -f docker-compose-monitoring.yml restart grafana
```

#### 3. Configuration Reload
```bash
# Reload Prometheus configuration
curl -X POST http://localhost:9090/-/reload

# Reload AlertManager configuration
curl -X POST http://localhost:9093/-/reload
```

### Data Recovery

#### 1. Prometheus Data Recovery
```bash
# Restore data from backup
docker cp prometheus-backup.tar.gz prometheus:/tmp/
docker exec prometheus tar -xzf /tmp/prometheus-backup.tar.gz -C /prometheus/
```

#### 2. Grafana Configuration Recovery
```bash
# Restore dashboards
docker cp grafana-dashboards-backup.tar.gz grafana:/tmp/
docker exec grafana tar -xzf /tmp/grafana-dashboards-backup.tar.gz -C /var/lib/grafana/
```

## Preventive Measures

### Monitoring the Monitoring System

#### 1. Meta-Monitoring Configuration
```yaml
# Monitor Prometheus itself
- alert: PrometheusDown
  expr: up{job="prometheus"} == 0
  for: 1m

# Monitor Grafana
- alert: GrafanaDown
  expr: up{job="grafana"} == 0
  for: 1m

# Monitor AlertManager
- alert: AlertManagerDown
  expr: up{job="alertmanager"} == 0
  for: 1m
```

#### 2. Monitoring Data Quality
```yaml
# Monitor metrics collection delay
- alert: MetricsCollectionDelay
  expr: time() - prometheus_tsdb_head_max_time / 1000 > 300
  for: 2m

# Monitor alert rule evaluation
- alert: AlertRuleEvaluationFailure
  expr: increase(prometheus_rule_evaluation_failures_total[5m]) > 0
  for: 1m
```

### Regular Maintenance

#### 1. Health Check Schedule
```bash
# Daily health check
0 9 * * * /path/to/monitoring-health-check.sh

# Weekly performance check
0 10 * * 1 /path/to/monitoring-performance-check.sh

# Monthly configuration review
0 10 1 * * /path/to/monitoring-config-review.sh
```

#### 2. Backup Schedule
```bash
# Daily configuration backup
0 2 * * * /path/to/backup-monitoring-config.sh

# Weekly data backup
0 3 * * 0 /path/to/backup-monitoring-data.sh
```

## Contact Support

### Issue Escalation Process

1. **Self-Troubleshooting**: Use this document for initial diagnosis
2. **Collect Information**: Run diagnostic scripts to collect relevant information
3. **Community Support**: Search for similar issues in project Issues
4. **Technical Support**: Contact the development team with diagnostic information

### When Submitting Issues, Please Include

- Detailed problem description and reproduction steps
- Error logs and screenshots
- System environment information
- Configuration file contents
- Diagnostic script output

## Related Documentation

- [Monitoring Setup Guide](setup.md)
- [Grafana Dashboard Usage Guide](dashboards.md)
- [Alert Configuration Guide](alerts.md)
- [Performance Optimization Guide](performance.md)

---

**Tip**: It's recommended to establish a knowledge base for monitoring issue handling, documenting common problems and solutions to improve team troubleshooting efficiency.

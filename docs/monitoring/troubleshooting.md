# 监控故障排查指南

本文档提供 JAiRouter 监控系统常见问题的诊断和解决方案，帮助快速定位和修复监控相关问题。

## 快速诊断

### 监控健康检查

运行以下命令进行快速健康检查：

```bash
# Windows
.\scripts\monitoring-health-check.ps1

# Linux/macOS
./scripts/monitoring-health-check.sh
```

### 手动检查步骤

```bash
# 1. 检查 JAiRouter 服务状态
curl http://localhost:8080/actuator/health

# 2. 检查指标端点
curl http://localhost:8080/actuator/prometheus

# 3. 检查 Prometheus 状态
curl http://localhost:9090/-/healthy

# 4. 检查 Grafana 状态
curl http://localhost:3000/api/health

# 5. 检查 AlertManager 状态
curl http://localhost:9093/-/healthy
```

## 常见问题分类

### 指标收集问题

#### 问题 1: 指标端点无法访问

**症状**:
- 访问 `/actuator/prometheus` 返回 404
- Prometheus targets 页面显示 JAiRouter 为 down 状态

**诊断步骤**:
```bash
# 检查应用状态
curl http://localhost:8080/actuator/health

# 检查端点配置
curl http://localhost:8080/actuator

# 检查应用日志
docker logs jairouter | grep -i actuator
```

**解决方案**:
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

#### 问题 2: 指标数据为空或不完整

**症状**:
- Prometheus 端点返回空数据
- 某些指标缺失

**诊断步骤**:
```bash
# 检查监控配置
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.monitoringConfiguration'

# 检查指标注册表
curl http://localhost:8080/actuator/metrics

# 检查特定指标
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

**可能原因和解决方案**:

**原因 1: 指标收集被禁用**
```yaml
monitoring:
  metrics:
    enabled: true
    enabled-categories:
      - system
      - business
      - infrastructure
```

**原因 2: 采样率过低**
```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
```

**原因 3: 异步处理队列满**
```yaml
monitoring:
  metrics:
    performance:
      buffer-size: 2000
      batch-size: 500
```

#### 问题 3: 指标数据不准确

**症状**:
- 指标值与实际情况不符
- 指标更新延迟

**诊断步骤**:
```bash
# 检查时间同步
date
curl -s http://localhost:9090/api/v1/query?query=time() | jq '.data.result[0].value[1]'

# 检查采样配置
curl http://localhost:8080/actuator/jairouter-metrics/config

# 检查异步处理状态
curl http://localhost:8080/actuator/jairouter-metrics/status
```

**解决方案**:
```yaml
monitoring:
  metrics:
    # 提高采样率
    sampling:
      request-metrics: 1.0
    
    # 减少批处理延迟
    performance:
      batch-size: 100
      processing-timeout: 1s
```

### Prometheus 问题

#### 问题 4: Prometheus 无法抓取指标

**症状**:
- Prometheus targets 页面显示错误
- 指标查询返回空结果

**诊断步骤**:
```bash
# 1. 检查 Prometheus 配置
cat monitoring/prometheus/prometheus.yml | grep -A 10 jairouter

# 2. 检查网络连通性
docker exec prometheus curl http://jairouter:8080/actuator/prometheus

# 3. 检查 Prometheus 日志
docker logs prometheus | grep -i error
```

**解决方案**:

**网络问题**:
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

**配置问题**:
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']  # 使用服务名而不是 localhost
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
```

#### 问题 5: Prometheus 存储空间不足

**症状**:
- Prometheus 日志显示存储错误
- 查询响应缓慢

**诊断步骤**:
```bash
# 检查存储使用情况
docker exec prometheus df -h /prometheus

# 检查数据保留配置
docker exec prometheus cat /etc/prometheus/prometheus.yml | grep retention
```

**解决方案**:
```yaml
# prometheus.yml 或启动参数
command:
  - '--storage.tsdb.retention.time=15d'
  - '--storage.tsdb.retention.size=5GB'
  - '--storage.tsdb.path=/prometheus'
```

### Grafana 问题

#### 问题 6: Grafana 仪表板显示 "No data"

**症状**:
- 仪表板面板显示 "No data"
- 查询返回空结果

**诊断步骤**:
```bash
# 1. 检查数据源连接
curl http://localhost:3000/api/datasources/proxy/1/api/v1/query?query=up

# 2. 检查查询语法
curl "http://localhost:9090/api/v1/query?query=jairouter_requests_total"

# 3. 检查时间范围
# 在 Grafana 中调整时间范围到最近 5 分钟
```

**解决方案**:

**数据源配置**:
```yaml
# grafana/provisioning/datasources/prometheus.yml
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

**查询优化**:
```promql
# 确保查询语法正确
sum(rate(jairouter_requests_total[5m]))

# 检查标签是否存在
label_values(jairouter_requests_total, service)
```

#### 问题 7: Grafana 仪表板加载缓慢

**症状**:
- 仪表板加载时间过长
- 查询超时

**诊断步骤**:
```bash
# 检查查询复杂度
# 在 Grafana Query Inspector 中查看查询执行时间

# 检查 Prometheus 性能
curl "http://localhost:9090/api/v1/query?query=prometheus_tsdb_head_samples_appended_total"
```

**解决方案**:

**查询优化**:
```promql
# 使用 recording rules 预聚合数据
# rules/jairouter-recording-rules.yml
groups:
  - name: jairouter.recording
    rules:
      - record: jairouter:request_rate_5m
        expr: sum(rate(jairouter_requests_total[5m]))
```

**面板优化**:
- 减少面板数量（< 20 个）
- 增加刷新间隔
- 使用合适的时间范围

### AlertManager 问题

#### 问题 8: 告警未触发

**症状**:
- 满足告警条件但未收到通知
- Prometheus 显示告警但 AlertManager 未收到

**诊断步骤**:
```bash
# 1. 检查告警规则状态
curl http://localhost:9090/api/v1/rules

# 2. 检查 AlertManager 接收状态
curl http://localhost:9093/api/v1/alerts

# 3. 检查告警规则语法
promtool check rules monitoring/prometheus/rules/jairouter-alerts.yml
```

**解决方案**:

**规则配置**:
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

**网络连接**:
```bash
# 检查 Prometheus 到 AlertManager 的连接
docker exec prometheus curl http://alertmanager:9093/-/healthy
```

#### 问题 9: 告警通知未发送

**症状**:
- AlertManager 收到告警但未发送通知
- 通知渠道无响应

**诊断步骤**:
```bash
# 检查 AlertManager 配置
amtool config show

# 检查通知历史
curl http://localhost:9093/api/v1/alerts

# 检查静默规则
amtool silence query
```

**解决方案**:

**邮件配置**:
```yaml
# alertmanager.yml
global:
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alerts@jairouter.com'
  smtp_auth_username: 'alerts@jairouter.com'
  smtp_auth_password: 'your-password'
```

**路由配置**:
```yaml
route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default'
```

## 性能问题

### 问题 10: 监控影响应用性能

**症状**:
- 应用响应时间增加
- CPU 使用率上升
- 内存使用量增加

**诊断步骤**:
```bash
# 检查监控开销
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# 检查异步处理状态
curl http://localhost:8080/actuator/jairouter-metrics/performance

# 对比启用/禁用监控的性能差异
```

**解决方案**:

#### 性能优化配置
```yaml
monitoring:
  metrics:
    # 启用异步处理
    performance:
      async-processing: true
      async-thread-pool-size: 4
      batch-size: 500
      buffer-size: 2000
    
    # 降低采样率
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
      infrastructure-metrics: 0.1
    
    # 优化缓存
    memory:
      cache-size: 5000
      cache-expiry: 2m
```

### 问题 11: Prometheus 查询性能差

**症状**:
- 查询响应时间长
- Grafana 仪表板加载缓慢

**诊断步骤**:
```bash
# 检查 Prometheus 指标
curl "http://localhost:9090/api/v1/query?query=prometheus_tsdb_head_series"

# 检查查询复杂度
# 在 Prometheus Web UI 中查看查询执行时间
```

**解决方案**:

**优化 Prometheus 配置**:
```yaml
# monitoring/prometheus/prometheus.yml
global:
  scrape_interval: 30s  # 增加抓取间隔
  evaluation_interval: 30s

scrape_configs:
  - job_name: 'jairouter'
    scrape_interval: 15s  # 针对重要服务保持较短间隔
```

**使用 Recording Rules**:
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

## 数据一致性问题

### 问题 12: 指标数据不一致

**症状**:
- 不同时间查询同一指标结果不同
- Grafana 和 Prometheus 显示数据不一致

**诊断步骤**:
```bash
# 检查时间同步
ntpdate -q pool.ntp.org

# 检查 Prometheus 数据完整性
curl "http://localhost:9090/api/v1/query?query=up{job=\"jairouter\"}"

# 检查 Grafana 缓存设置
```

**解决方案**:

**时间同步**:
```bash
# 同步系统时间
sudo ntpdate -s pool.ntp.org

# 配置自动时间同步
sudo systemctl enable ntp
```

**缓存配置**:
```yaml
# application.yml
management:
  endpoint:
    prometheus:
      cache:
        time-to-live: 10s
```

## 日志分析

### 收集诊断信息

创建诊断脚本 `scripts/collect-monitoring-logs.sh`：

```bash
#!/bin/bash

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_DIR="monitoring-logs-${TIMESTAMP}"

mkdir -p ${LOG_DIR}

echo "收集监控诊断信息..."

# 收集服务状态
echo "=== 服务状态 ===" > ${LOG_DIR}/service-status.log
docker-compose -f docker-compose-monitoring.yml ps >> ${LOG_DIR}/service-status.log

# 收集配置信息
echo "=== 配置信息 ===" >> troubleshoot.log
curl -s http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.monitoringConfiguration' >> ${LOG_DIR}/config.log

# 收集指标信息
echo "=== 指标信息 ===" > ${LOG_DIR}/metrics.log
curl -s http://localhost:8080/actuator/prometheus | head -100 >> ${LOG_DIR}/metrics.log

# 收集应用日志
docker logs jairouter > ${LOG_DIR}/jairouter.log 2>&1
docker logs prometheus > ${LOG_DIR}/prometheus.log 2>&1
docker logs grafana > ${LOG_DIR}/grafana.log 2>&1
docker logs alertmanager > ${LOG_DIR}/alertmanager.log 2>&1

# 收集系统信息
echo "=== 系统信息 ===" > ${LOG_DIR}/system-info.log
df -h >> ${LOG_DIR}/system-info.log
free -h >> ${LOG_DIR}/system-info.log
docker stats --no-stream >> ${LOG_DIR}/system-info.log

echo "诊断信息已收集到 ${LOG_DIR} 目录"
```

### 日志分析技巧

#### 1. JAiRouter 应用日志
```bash
# 查看监控相关错误
docker logs jairouter | grep -i "metric\|monitor\|prometheus"

# 查看性能相关日志
docker logs jairouter | grep -i "performance\|slow\|timeout"

# 查看配置加载日志
docker logs jairouter | grep -i "config\|property"
```

#### 2. Prometheus 日志
```bash
# 查看抓取错误
docker logs prometheus | grep -i "scrape\|target"

# 查看存储相关问题
docker logs prometheus | grep -i "storage\|tsdb"

# 查看规则评估错误
docker logs prometheus | grep -i "rule\|alert"
```

#### 3. Grafana 日志
```bash
# 查看数据源连接问题
docker logs grafana | grep -i "datasource\|proxy"

# 查看查询错误
docker logs grafana | grep -i "query\|timeout"

# 查看认证问题
docker logs grafana | grep -i "auth\|login"
```

## 恢复程序

### 监控服务恢复

#### 1. 完全重启监控栈
```bash
# 停止所有服务
docker-compose -f docker-compose-monitoring.yml down

# 清理数据（谨慎操作）
docker volume prune

# 重新启动
docker-compose -f docker-compose-monitoring.yml up -d
```

#### 2. 单个服务重启
```bash
# 重启 JAiRouter
docker-compose -f docker-compose-monitoring.yml restart jairouter

# 重启 Prometheus
docker-compose -f docker-compose-monitoring.yml restart prometheus

# 重启 Grafana
docker-compose -f docker-compose-monitoring.yml restart grafana
```

#### 3. 配置重载
```bash
# 重载 Prometheus 配置
curl -X POST http://localhost:9090/-/reload

# 重载 AlertManager 配置
curl -X POST http://localhost:9093/-/reload
```

### 数据恢复

#### 1. Prometheus 数据恢复
```bash
# 从备份恢复数据
docker cp prometheus-backup.tar.gz prometheus:/tmp/
docker exec prometheus tar -xzf /tmp/prometheus-backup.tar.gz -C /prometheus/
```

#### 2. Grafana 配置恢复
```bash
# 恢复仪表板
docker cp grafana-dashboards-backup.tar.gz grafana:/tmp/
docker exec grafana tar -xzf /tmp/grafana-dashboards-backup.tar.gz -C /var/lib/grafana/
```

## 预防措施

### 监控监控系统

#### 1. 元监控配置
```yaml
# 监控 Prometheus 自身
- alert: PrometheusDown
  expr: up{job="prometheus"} == 0
  for: 1m

# 监控 Grafana
- alert: GrafanaDown
  expr: up{job="grafana"} == 0
  for: 1m

# 监控 AlertManager
- alert: AlertManagerDown
  expr: up{job="alertmanager"} == 0
  for: 1m
```

#### 2. 监控数据质量
```yaml
# 监控指标收集延迟
- alert: MetricsCollectionDelay
  expr: time() - prometheus_tsdb_head_max_time / 1000 > 300
  for: 2m

# 监控告警规则评估
- alert: AlertRuleEvaluationFailure
  expr: increase(prometheus_rule_evaluation_failures_total[5m]) > 0
  for: 1m
```

### 定期维护

#### 1. 健康检查计划
```bash
# 每日健康检查
0 9 * * * /path/to/monitoring-health-check.sh

# 每周性能检查
0 10 * * 1 /path/to/monitoring-performance-check.sh

# 每月配置审查
0 10 1 * * /path/to/monitoring-config-review.sh
```

#### 2. 备份计划
```bash
# 每日配置备份
0 2 * * * /path/to/backup-monitoring-config.sh

# 每周数据备份
0 3 * * 0 /path/to/backup-monitoring-data.sh
```

## 联系支持

### 问题升级流程

1. **自助排查**: 使用本文档进行初步诊断
2. **收集信息**: 运行诊断脚本收集相关信息
3. **社区支持**: 在项目 Issues 中搜索相似问题
4. **技术支持**: 联系开发团队，提供诊断信息

### 提交问题时请包含

- 问题详细描述和复现步骤
- 错误日志和截图
- 系统环境信息
- 配置文件内容
- 诊断脚本输出

## 相关文档

- [监控设置指南](setup.md)
- [Grafana 仪表板使用指南](dashboards.md)
- [告警配置指南](alerts.md)
- [性能优化指南](performance.md)

---

**提示**: 建议建立监控问题处理的知识库，记录常见问题和解决方案，提高团队的问题处理效率。
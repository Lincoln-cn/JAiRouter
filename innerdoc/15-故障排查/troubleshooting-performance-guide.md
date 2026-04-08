# JAiRouter 监控故障排查和性能调优指南

## 概述

本指南提供 JAiRouter 监控系统的故障排查方法、性能调优策略和最佳实践，帮助运维团队快速定位和解决监控相关问题。

## 目录

1. [故障排查流程](#故障排查流程)
2. [常见问题诊断](#常见问题诊断)
3. [性能调优策略](#性能调优策略)
4. [监控系统优化](#监控系统优化)
5. [告警调优](#告警调优)
6. [容量规划](#容量规划)
7. [最佳实践](#最佳实践)

## 故障排查流程

### 标准故障排查步骤

#### 1. 问题确认和分类
```bash
# 检查服务状态
curl -f http://localhost:8080/actuator/health

# 检查监控端点
curl -f http://localhost:8080/actuator/prometheus

# 检查应用日志
tail -f logs/jairouter-debug.log | grep -i "metric\|monitor\|prometheus"
```

#### 2. 数据收集
```bash
# 收集系统信息
echo "=== 系统信息 ===" > troubleshoot.log
uname -a >> troubleshoot.log
java -version >> troubleshoot.log
docker --version >> troubleshoot.log

# 收集配置信息
echo "=== 配置信息 ===" >> troubleshoot.log
curl -s http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.monitoringConfiguration' >> troubleshoot.log

# 收集指标信息
echo "=== 指标信息 ===" >> troubleshoot.log
curl -s http://localhost:8080/actuator/metrics | jq '.names[] | select(contains("jairouter"))' >> troubleshoot.log
```

#### 3. 问题定位
- 检查监控配置是否正确
- 验证网络连接和端口访问
- 分析应用日志中的错误信息
- 检查资源使用情况

#### 4. 解决方案实施
- 应用临时修复措施
- 验证修复效果
- 实施永久解决方案
- 更新文档和监控策略

## 常见问题诊断

### 问题 1: 监控端点无法访问

#### 症状
- 访问 `/actuator/prometheus` 返回 404
- Prometheus 无法抓取指标数据
- Grafana 显示 "No data"

#### 诊断步骤
```bash
# 1. 检查端点是否启用
curl http://localhost:8080/actuator

# 2. 检查 Prometheus 端点
curl -v http://localhost:8080/actuator/prometheus

# 3. 检查配置
grep -r "management.endpoints" src/main/resources/
```

#### 解决方案
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      enabled: true
```

#### 验证修复
```bash
# 重启应用后验证
curl http://localhost:8080/actuator/prometheus | head -20
```

### 问题 2: 指标数据缺失或不准确

#### 症状
- 某些指标在 Prometheus 中显示为空
- 指标值明显不正确
- 指标更新不及时

#### 诊断步骤
```bash
# 1. 检查指标收集器状态
curl http://localhost:8080/actuator/jairouter-metrics/status

# 2. 检查指标配置
curl http://localhost:8080/actuator/jairouter-metrics/config

# 3. 检查应用日志
grep -i "MetricsCollector\|MetricsError" logs/jairouter-debug.log
```

#### 常见原因和解决方案

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
      request-metrics: 1.0  # 提高采样率
      backend-metrics: 1.0
```

**原因 3: 异步处理队列满**
```yaml
monitoring:
  metrics:
    performance:
      buffer-size: 2000     # 增加缓冲区大小
      batch-size: 200       # 增加批处理大小
```

### 问题 3: 监控影响系统性能

#### 症状
- 启用监控后响应时间明显增加
- CPU 使用率异常升高
- 内存使用量持续增长

#### 性能影响分析
```bash
# 1. 对比启用监控前后的性能
# 禁用监控
curl -X POST http://localhost:8080/actuator/jairouter-metrics/config \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'

# 运行性能测试
ab -n 1000 -c 10 http://localhost:8080/v1/chat/completions

# 启用监控
curl -X POST http://localhost:8080/actuator/jairouter-metrics/config \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'

# 再次运行性能测试
ab -n 1000 -c 10 http://localhost:8080/v1/chat/completions
```

#### 性能优化配置
```yaml
monitoring:
  metrics:
    # 启用异步处理
    performance:
      async-processing: true
      async-thread-pool-size: 2
      batch-size: 500
      buffer-size: 5000
      processing-timeout: 3s
    
    # 降低采样率
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
      infrastructure-metrics: 0.05
    
    # 内存优化
    memory:
      cache-size: 5000
      cache-expiry: 2m
      memory-threshold: 85
      low-memory-sampling-rate: 0.01
```

### 问题 4: Prometheus 抓取失败

#### 症状
- Prometheus targets 页面显示 DOWN 状态
- 错误信息: "context deadline exceeded"
- 指标抓取超时

#### 诊断步骤
```bash
# 1. 检查网络连接
telnet localhost 8080

# 2. 检查端点响应时间
time curl http://localhost:8080/actuator/prometheus > /dev/null

# 3. 检查 Prometheus 配置
cat monitoring/prometheus/prometheus.yml | grep -A 10 jairouter
```

#### 解决方案

**优化 Prometheus 配置**:
```yaml
# monitoring/prometheus/prometheus.yml
scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s      # 增加抓取间隔
    scrape_timeout: 10s       # 增加超时时间
    honor_labels: true
```

**优化应用端点性能**:
```yaml
# application.yml
management:
  endpoint:
    prometheus:
      cache:
        time-to-live: 30s     # 启用缓存
  metrics:
    export:
      prometheus:
        step: 30s             # 匹配抓取间隔
```

### 问题 5: 内存泄漏

#### 症状
- JVM 堆内存持续增长
- 频繁的 Full GC
- OutOfMemoryError 异常

#### 内存分析
```bash
# 1. 生成堆转储
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
jmap -dump:format=b,file=heap.hprof <pid>

# 2. 分析内存使用
jstat -gc <pid> 5s

# 3. 检查监控相关的内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq
```

#### 内存优化策略
```yaml
monitoring:
  metrics:
    memory:
      # 限制缓存大小
      cache-size: 1000
      cache-expiry: 1m
      
      # 启用内存保护
      memory-threshold: 80
      low-memory-sampling-rate: 0.01
    
    performance:
      # 减少批处理大小
      batch-size: 100
      buffer-size: 500
      
      # 启用定期清理
      cleanup-interval: 30s
```

## 性能调优策略

### 指标收集优化

#### 1. 采样策略优化
```yaml
monitoring:
  metrics:
    sampling:
      # 根据业务重要性调整采样率
      request-metrics: 0.1      # 高频请求低采样
      backend-metrics: 0.5      # 中等采样
      infrastructure-metrics: 0.05  # 基础设施低采样
      system-metrics: 1.0       # 系统指标全采样
    
    # 动态采样配置
    dynamic-sampling:
      enabled: true
      high-load-threshold: 1000  # RPS 超过 1000 时降低采样率
      high-load-sampling-rate: 0.01
```

#### 2. 异步处理优化
```yaml
monitoring:
  metrics:
    performance:
      async-processing: true
      
      # 线程池配置
      async-thread-pool-size: 4
      async-thread-pool-queue-size: 1000
      
      # 批处理配置
      batch-size: 1000
      batch-timeout: 5s
      
      # 缓冲区配置
      buffer-size: 10000
      buffer-overflow-strategy: drop_oldest
```

#### 3. 缓存策略优化
```yaml
monitoring:
  metrics:
    cache:
      # 多级缓存
      l1-cache-size: 1000       # 内存缓存
      l1-cache-expiry: 30s
      
      l2-cache-size: 10000      # 磁盘缓存
      l2-cache-expiry: 5m
      
      # 缓存预热
      cache-warmup: true
      warmup-delay: 10s
```

### JVM 调优

#### 1. 堆内存配置
```bash
# 启动参数优化
java -Xms2g -Xmx4g \
     -XX:NewRatio=3 \
     -XX:SurvivorRatio=8 \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:G1HeapRegionSize=16m \
     -jar model-router.jar
```

#### 2. GC 调优
```bash
# G1GC 优化参数
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100
-XX:G1HeapRegionSize=32m
-XX:G1NewSizePercent=20
-XX:G1MaxNewSizePercent=30
-XX:InitiatingHeapOccupancyPercent=45
-XX:G1MixedGCCountTarget=8
-XX:G1MixedGCLiveThresholdPercent=85
```

#### 3. 监控 JVM 性能
```bash
# 启用 JVM 监控
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCApplicationStoppedTime
-Xloggc:gc.log
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=5
-XX:GCLogFileSize=10M
```

### 网络优化

#### 1. HTTP 连接池优化
```yaml
spring:
  webflux:
    # 连接池配置
    httpclient:
      pool:
        max-connections: 500
        max-idle-time: 30s
        max-life-time: 60s
      
      # 超时配置
      connect-timeout: 5s
      response-timeout: 30s
```

#### 2. Netty 优化
```yaml
spring:
  webflux:
    netty:
      # 工作线程数
      worker-threads: 8
      
      # 缓冲区大小
      initial-buffer-size: 128
      max-buffer-size: 1024
      
      # 连接超时
      connection-timeout: 5s
```

## 监控系统优化

### Prometheus 优化

#### 1. 存储优化
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  
  # 外部标签
  external_labels:
    cluster: 'jairouter-prod'
    region: 'us-west-1'

# 存储配置
storage:
  tsdb:
    retention.time: 15d
    retention.size: 50GB
    wal-compression: true
```

#### 2. 查询优化
```yaml
# 记录规则优化
rule_files:
  - "rules/jairouter-recording-rules.yml"

# jairouter-recording-rules.yml
groups:
  - name: jairouter.recording
    interval: 30s
    rules:
      # 预计算常用指标
      - record: jairouter:request_rate_5m
        expr: sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:error_rate_5m
        expr: sum(rate(jairouter_requests_total{status=~"4..|5.."}[5m])) / sum(rate(jairouter_requests_total[5m]))
      
      - record: jairouter:response_time_p95_5m
        expr: histogram_quantile(0.95, sum(rate(jairouter_request_duration_seconds_bucket[5m])) by (le))
```

#### 3. 抓取优化
```yaml
scrape_configs:
  - job_name: 'jairouter-detailed'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    scrape_timeout: 10s
    
    # 指标过滤
    metric_relabel_configs:
      # 只保留 jairouter 相关指标
      - source_labels: [__name__]
        regex: 'jairouter_.*|jvm_.*|system_.*'
        action: keep
      
      # 删除高基数标签
      - regex: 'client_ip|user_agent'
        action: labeldrop
```

### Grafana 优化

#### 1. 查询优化
```json
{
  "targets": [
    {
      "expr": "jairouter:request_rate_5m",
      "interval": "30s",
      "maxDataPoints": 1000,
      "refId": "A"
    }
  ],
  "options": {
    "reduceOptions": {
      "values": false,
      "calcs": ["lastNotNull"],
      "fields": ""
    }
  }
}
```

#### 2. 面板优化
```json
{
  "panels": [
    {
      "title": "请求率",
      "type": "stat",
      "options": {
        "reduceOptions": {
          "calcs": ["lastNotNull"]
        },
        "textMode": "auto"
      },
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "decimals": 2
        }
      }
    }
  ]
}
```

## 告警调优

### 告警规则优化

#### 1. 分级告警策略
```yaml
# alerts/jairouter-alerts.yml
groups:
  - name: jairouter.critical
    rules:
      # 严重告警 - 立即通知
      - alert: JAiRouterDown
        expr: up{job="jairouter"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 服务不可用"
          description: "JAiRouter 服务已停止响应超过 1 分钟"
      
      - alert: JAiRouterHighErrorRate
        expr: jairouter:error_rate_5m > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 错误率过高"
          description: "错误率: {{ $value | humanizePercentage }}"

  - name: jairouter.warning
    rules:
      # 警告告警 - 延迟通知
      - alert: JAiRouterHighLatency
        expr: jairouter:response_time_p95_5m > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 响应时间过长"
          description: "P95 响应时间: {{ $value }}s"
```

#### 2. 告警抑制规则
```yaml
# alertmanager.yml
inhibit_rules:
  # 服务不可用时抑制其他告警
  - source_match:
      alertname: JAiRouterDown
    target_match_re:
      alertname: JAiRouter.*
    equal: ['instance']
  
  # 高错误率时抑制延迟告警
  - source_match:
      alertname: JAiRouterHighErrorRate
    target_match:
      alertname: JAiRouterHighLatency
    equal: ['instance']
```

### 告警通知优化

#### 1. 分组和路由
```yaml
# alertmanager.yml
route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default'
  
  routes:
    # 严重告警立即通知
    - match:
        severity: critical
      receiver: 'critical-alerts'
      group_wait: 0s
      repeat_interval: 5m
    
    # 警告告警延迟通知
    - match:
        severity: warning
      receiver: 'warning-alerts'
      group_wait: 5m
      repeat_interval: 30m
```

#### 2. 通知渠道配置
```yaml
receivers:
  - name: 'critical-alerts'
    webhook_configs:
      - url: 'http://alertmanager-webhook:8080/critical'
        send_resolved: true
    email_configs:
      - to: 'oncall@company.com'
        subject: '[CRITICAL] JAiRouter Alert'
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          {{ end }}
  
  - name: 'warning-alerts'
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/...'
        channel: '#jairouter-alerts'
        title: 'JAiRouter Warning'
        text: |
          {{ range .Alerts }}
          {{ .Annotations.summary }}
          {{ end }}
```

## 容量规划

### 资源需求评估

#### 1. 监控系统资源消耗
```bash
# 监控 JAiRouter 资源使用
docker stats jairouter

# 监控 Prometheus 资源使用
docker stats prometheus

# 监控 Grafana 资源使用
docker stats grafana
```

#### 2. 存储需求计算
```bash
# 计算 Prometheus 存储需求
# 公式: 指标数量 × 采样频率 × 保留时间 × 每个样本大小

# 示例计算:
# - 指标数量: 1000
# - 采样频率: 15s (4 samples/min)
# - 保留时间: 15 days
# - 每个样本: 16 bytes

# 存储需求 = 1000 × 4 × 60 × 24 × 15 × 16 bytes ≈ 55 GB
```

#### 3. 网络带宽需求
```bash
# 计算网络带宽需求
# Prometheus 抓取带宽 = 指标数据大小 × 抓取频率

# 示例:
# - 每次抓取数据大小: 100KB
# - 抓取频率: 15s
# - 带宽需求 = 100KB / 15s ≈ 6.7 KB/s
```

### 扩容策略

#### 1. 水平扩容
```yaml
# docker-compose-ha.yml
version: '3.8'

services:
  jairouter-1:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
    labels:
      - "prometheus.scrape=true"
      - "prometheus.port=8080"
  
  jairouter-2:
    image: jairouter/model-router:latest
    ports:
      - "8081:8080"
    labels:
      - "prometheus.scrape=true"
      - "prometheus.port=8080"
  
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./monitoring/prometheus-ha.yml:/etc/prometheus/prometheus.yml
```

#### 2. 垂直扩容
```yaml
# 增加资源配置
services:
  jairouter:
    image: jairouter/model-router:latest
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '1.0'
          memory: 2G
    environment:
      - JAVA_OPTS=-Xms2g -Xmx4g
```

## 最佳实践

### 监控策略最佳实践

#### 1. 分层监控
- **基础设施层**: 服务器、网络、存储
- **平台层**: JVM、数据库、消息队列
- **应用层**: 业务指标、用户体验
- **业务层**: 关键业务流程、SLA 指标

#### 2. 指标设计原则
- **USE 方法**: Utilization, Saturation, Errors
- **RED 方法**: Rate, Errors, Duration
- **四个黄金信号**: 延迟、流量、错误、饱和度

#### 3. 告警设计原则
- **可操作性**: 每个告警都应该有明确的处理步骤
- **相关性**: 告警应该与业务影响相关
- **及时性**: 告警应该在问题影响用户前触发
- **准确性**: 避免误报和漏报

### 性能优化最佳实践

#### 1. 监控开销控制
- 监控系统的资源消耗应该 < 5% 的总资源
- 指标收集延迟应该 < 主业务延迟的 10%
- 避免高基数标签（如用户 ID、IP 地址）

#### 2. 数据保留策略
- 高精度数据保留 7-30 天
- 中精度数据保留 3-6 个月
- 低精度数据保留 1-2 年
- 使用 recording rules 预聚合数据

#### 3. 查询优化
- 使用合适的时间范围
- 避免复杂的正则表达式
- 使用 recording rules 预计算常用查询
- 限制查询结果的基数

### 运维流程最佳实践

#### 1. 变更管理
- 监控配置变更需要经过审核
- 使用版本控制管理配置文件
- 在测试环境验证配置变更
- 建立配置回滚机制

#### 2. 事件响应
- 建立清晰的事件响应流程
- 定义不同级别告警的响应时间
- 建立事件升级机制
- 进行定期的事件响应演练

#### 3. 持续改进
- 定期审查监控指标的有效性
- 分析历史事件，改进监控策略
- 收集用户反馈，优化监控体验
- 跟踪行业最佳实践，持续学习

## 工具和脚本

### 故障排查脚本

#### 1. 健康检查脚本
```bash
#!/bin/bash
# scripts/health-check.sh

echo "=== JAiRouter 健康检查 ==="

# 检查服务状态
echo "1. 检查服务状态..."
curl -f http://localhost:8080/actuator/health || echo "❌ 服务不可用"

# 检查监控端点
echo "2. 检查监控端点..."
curl -f http://localhost:8080/actuator/prometheus > /dev/null && echo "✅ Prometheus 端点正常" || echo "❌ Prometheus 端点异常"

# 检查指标数量
echo "3. 检查指标数量..."
METRIC_COUNT=$(curl -s http://localhost:8080/actuator/prometheus | grep -c "^jairouter_")
echo "JAiRouter 指标数量: $METRIC_COUNT"

# 检查内存使用
echo "4. 检查内存使用..."
MEMORY_USED=$(curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value')
MEMORY_MAX=$(curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq -r '.measurements[0].value')
MEMORY_USAGE=$(echo "scale=2; $MEMORY_USED / $MEMORY_MAX * 100" | bc)
echo "内存使用率: ${MEMORY_USAGE}%"

echo "=== 健康检查完成 ==="
```

#### 2. 性能分析脚本
```bash
#!/bin/bash
# scripts/performance-analysis.sh

echo "=== JAiRouter 性能分析 ==="

# 获取 JVM 信息
echo "1. JVM 信息..."
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq -r '.measurements[] | "\(.statistic): \(.value)"'

# 获取请求统计
echo "2. 请求统计..."
curl -s http://localhost:8080/actuator/metrics/jairouter.requests.total | jq -r '.measurements[] | "\(.statistic): \(.value)"'

# 获取响应时间
echo "3. 响应时间..."
curl -s http://localhost:8080/actuator/metrics/jairouter.request.duration | jq -r '.measurements[] | "\(.statistic): \(.value)"'

echo "=== 性能分析完成 ==="
```

### 监控配置生成器

#### 1. Prometheus 配置生成器
```python
#!/usr/bin/env python3
# scripts/generate-prometheus-config.py

import yaml
import sys

def generate_prometheus_config(instances):
    config = {
        'global': {
            'scrape_interval': '15s',
            'evaluation_interval': '15s'
        },
        'scrape_configs': [
            {
                'job_name': 'jairouter',
                'static_configs': [
                    {'targets': instances}
                ],
                'metrics_path': '/actuator/prometheus',
                'scrape_interval': '15s',
                'scrape_timeout': '10s'
            }
        ]
    }
    
    return yaml.dump(config, default_flow_style=False)

if __name__ == '__main__':
    instances = sys.argv[1:] if len(sys.argv) > 1 else ['localhost:8080']
    print(generate_prometheus_config(instances))
```

## 总结

本指南提供了 JAiRouter 监控系统的全面故障排查和性能调优方法。通过遵循这些最佳实践，可以确保监控系统的稳定性和高效性，为业务系统提供可靠的监控保障。

关键要点：
1. 建立系统化的故障排查流程
2. 实施分层的性能优化策略
3. 持续监控和改进监控系统本身
4. 建立完善的运维流程和工具

## 相关文档

- [监控配置指南](../monitoring/monitoring-configuration-guide.md)
- [Grafana 仪表板使用指南](../monitoring/grafana-dashboard-guide.md)
- [监控指标参考手册](../metrics-reference-manual.md)
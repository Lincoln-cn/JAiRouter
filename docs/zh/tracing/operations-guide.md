# 运维指南

本文档为生产环境中 JAiRouter 分布式追踪系统的运维提供完整指南。

## 生产环境部署

### 环境准备

#### 系统要求
- **JVM**: OpenJDK 17 或更高版本
- **内存**: 最小 4GB，推荐 8GB+
- **CPU**: 4 核心以上
- **磁盘**: SSD 存储，至少 50GB 可用空间

#### 依赖服务
```yaml
# docker-compose.yml 示例
version: '3.8'
services:
  jairouter:
    image: jairouter:latest
    environment:
      - JAIROUTER_TRACING_ENABLED=true
      - JAIROUTER_TRACING_EXPORTER_TYPE=otlp
    depends_on:
      - otel-collector
      
  otel-collector:
    image: otel/opentelemetry-collector:latest
    ports:
      - "4317:4317"
    volumes:
      - ./otel-config.yaml:/etc/config.yaml
```

### 生产配置

#### 基础配置
```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter-prod"
    service-version: "${app.version}"
    environment: "production"
    
    # 采样配置
    sampling:
      strategy: "adaptive"
      adaptive:
        base-sample-rate: 0.01      # 1% 基础采样
        max-traces-per-second: 100
        error-sample-rate: 1.0      # 错误 100% 采样
        slow-request-threshold: 3000
    
    # 导出配置
    exporter:
      type: "otlp"
      batch-size: 512
      export-timeout: 10s
      max-queue-size: 2048
      
    # 内存管理
    memory:
      max-spans: 50000
      cleanup-interval: 30s
      span-ttl: 300s
      
    # 安全配置
    security:
      enabled: true
      sensitive-headers:
        - "Authorization"
        - "Cookie"
        - "X-API-Key"
```

#### JVM 调优
```bash
# 生产环境 JVM 参数
-Xmx8g -Xms8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+UnlockDiagnosticVMOptions
-XX:+LogVMOutput
-XX:LogFile=/var/log/jairouter/gc.log
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
```

## 监控和告警

### Prometheus 指标配置

#### 指标收集
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'jairouter-tracing'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
```

#### 关键指标
```promql
# 追踪导出成功率
rate(jairouter_tracing_spans_exported_total[5m]) / 
rate(jairouter_tracing_spans_created_total[5m])

# 平均响应时间
jairouter_tracing_request_duration_seconds_sum / 
jairouter_tracing_request_duration_seconds_count

# 内存使用率
jairouter_tracing_memory_used_bytes / 
jairouter_tracing_memory_max_bytes

# 错误率
rate(jairouter_tracing_errors_total[5m])
```

### 告警规则

```yaml
# tracing-alerts.yml
groups:
  - name: jairouter_tracing
    rules:
      - alert: TracingExportFailureHigh
        expr: rate(jairouter_tracing_export_errors_total[5m]) > 0.05
        for: 2m
        labels:
          severity: warning
          service: jairouter
        annotations:
          summary: "追踪数据导出失败率过高"
          description: "过去5分钟内追踪数据导出失败率超过5%"
          
      - alert: TracingMemoryUsageHigh
        expr: jairouter_tracing_memory_used_ratio > 0.85
        for: 1m
        labels:
          severity: critical
          service: jairouter
        annotations:
          summary: "追踪系统内存使用率过高"
          
      - alert: TracingSlowRequests
        expr: histogram_quantile(0.95, jairouter_tracing_request_duration_seconds_bucket) > 5
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "95% 请求处理时间超过 5 秒"
```

### Grafana 仪表板

#### 核心面板配置
```json
{
  "dashboard": {
    "title": "JAiRouter 追踪监控",
    "panels": [
      {
        "title": "请求追踪概览",
        "type": "stat",
        "targets": [
          {
            "expr": "rate(jairouter_tracing_requests_total[5m])",
            "legendFormat": "RPS"
          }
        ]
      },
      {
        "title": "追踪数据导出状态",
        "type": "timeseries",
        "targets": [
          {
            "expr": "rate(jairouter_tracing_spans_exported_total[5m])",
            "legendFormat": "导出成功"
          },
          {
            "expr": "rate(jairouter_tracing_export_errors_total[5m])",
            "legendFormat": "导出失败"
          }
        ]
      }
    ]
  }
}
```

## 容量规划

### 内存规划

#### Span 内存估算
```bash
# 每个 Span 平均占用内存：约 2KB
# 每秒 1000 个请求，采样率 10%，Span TTL 5分钟
# 内存需求 = 1000 * 0.1 * 300 * 2KB ≈ 60MB

# 建议配置
jairouter:
  tracing:
    memory:
      max-spans: 100000  # 基于内存容量调整
      span-ttl: 300s     # 5分钟 TTL
```

#### 动态调整策略
```yaml
jairouter:
  tracing:
    memory:
      # 内存压力阈值
      memory-threshold: 0.8
      
      # 自动清理配置
      auto-cleanup:
        enabled: true
        trigger-threshold: 0.85
        target-threshold: 0.7
```

### 存储规划

#### 日志存储
```yaml
# logback-spring.xml
<configuration>
    <appender name="TRACING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/jairouter/tracing.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>/var/log/jairouter/tracing.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>
</configuration>
```

## 安全运维

### 数据脱敏检查

```bash
# 定期检查敏感数据是否被正确脱敏
grep -r "password\|token\|secret" /var/log/jairouter/tracing.log

# 检查配置中的敏感信息
curl -s http://localhost:8080/actuator/configprops | \
  jq '.jairouter.tracing.security.sensitive_headers'
```

### 访问控制审计

```yaml
# 启用安全审计
jairouter:
  tracing:
    security:
      audit:
        enabled: true
        log-access: true
        log-config-changes: true
        retention-days: 90
```

### 加密配置管理

```bash
# 使用环境变量管理敏感配置
export JAIROUTER_TRACING_EXPORTER_OTLP_HEADERS_API_KEY="your-api-key"

# 或使用 Kubernetes Secret
kubectl create secret generic tracing-config \
  --from-literal=api-key=your-api-key
```

## 性能调优

### 实时性能监控

```bash
# 监控脚本示例
#!/bin/bash
while true; do
    echo "=== $(date) ==="
    
    # CPU 使用率
    echo "CPU: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)"
    
    # 内存使用
    echo "Memory: $(free -m | awk 'NR==2{printf "%.1f%%", $3*100/$2}')"
    
    # 追踪指标
    curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.spans.active | \
      jq '.measurements[0].value'
    
    sleep 30
done
```

### 自动化调优

```yaml
# 配置自动调优策略
jairouter:
  tracing:
    auto-tuning:
      enabled: true
      
      # CPU 使用率超过 80% 时降低采样率
      cpu-threshold: 80
      sampling-rate-adjustment: 0.5
      
      # 内存使用率超过 85% 时触发清理
      memory-threshold: 85
      cleanup-aggressive: true
```

## 备份和恢复

### 配置备份

```bash
# 每日配置备份脚本
#!/bin/bash
DATE=$(date +%Y%m%d)
BACKUP_DIR="/backup/jairouter-config"

# 备份当前配置
mkdir -p $BACKUP_DIR
curl -s http://localhost:8080/actuator/configprops > \
  $BACKUP_DIR/config-$DATE.json

# 保留 30 天备份
find $BACKUP_DIR -name "config-*.json" -mtime +30 -delete
```

### 追踪数据备份

```yaml
# 配置追踪数据导出到长期存储
jairouter:
  tracing:
    exporter:
      backup:
        enabled: true
        location: "/backup/tracing-data"
        retention-days: 90
        compression: true
```

## 升级和维护

### 滚动升级策略

```bash
# 滚动升级脚本
#!/bin/bash

# 1. 健康检查
curl -f http://localhost:8080/actuator/health/tracing || exit 1

# 2. 导出当前配置
curl -s http://localhost:8080/actuator/configprops > /tmp/pre-upgrade-config.json

# 3. 执行升级
docker-compose pull jairouter
docker-compose up -d jairouter

# 4. 升级后验证
sleep 30
curl -f http://localhost:8080/actuator/health/tracing || {
    echo "升级失败，回滚中..."
    docker-compose down
    # 回滚逻辑
}
```

### 维护窗口操作

```bash
# 维护模式脚本
#!/bin/bash

case $1 in
    "enter")
        # 进入维护模式
        echo "进入维护模式..."
        
        # 降低采样率以减少负载
        curl -X PUT http://localhost:8080/api/admin/tracing/sampling-rate \
             -H "Content-Type: application/json" \
             -d '{"rate": 0.01}'
        
        # 等待当前 Span 处理完成
        sleep 60
        ;;
        
    "exit")
        # 退出维护模式
        echo "退出维护模式..."
        
        # 恢复正常采样率
        curl -X PUT http://localhost:8080/api/admin/tracing/sampling-rate \
             -H "Content-Type: application/json" \
             -d '{"rate": 0.1}'
        ;;
esac
```

## 应急响应

### 常见应急场景

#### 1. 追踪系统过载
```bash
# 紧急降低采样率
curl -X PUT http://localhost:8080/api/admin/tracing/emergency-config \
     -d '{"sampling_rate": 0.001, "reason": "system_overload"}'

# 临时禁用追踪
curl -X POST http://localhost:8080/api/admin/tracing/disable \
     -d '{"duration": "1h", "reason": "emergency"}'
```

#### 2. 导出器故障
```yaml
# 切换到备用导出器
jairouter:
  tracing:
    exporter:
      fallback:
        enabled: true
        type: "logging"  # 临时使用日志导出
```

#### 3. 内存泄漏
```bash
# 强制 GC 和内存清理
curl -X POST http://localhost:8080/actuator/gc
curl -X POST http://localhost:8080/api/admin/tracing/force-cleanup
```

### 应急联系方式

建立应急响应流程：
1. **监控告警** → 自动通知运维团队
2. **问题分类** → 确定影响范围和优先级  
3. **应急处理** → 执行预定义的应急脚本
4. **问题跟进** → 记录和分析根因

## 最佳实践总结

### 1. 监控策略
- 设置多层次告警（警告、严重、紧急）
- 定期检查追踪数据完整性
- 监控系统资源使用趋势

### 2. 性能优化
- 根据业务需求调整采样率
- 定期清理过期数据
- 合理配置批处理大小

### 3. 安全管控
- 定期审查敏感数据过滤规则
- 启用配置变更审计日志
- 实施最小权限原则

### 4. 容灾准备
- 建立备份和恢复流程
- 准备应急响应预案
- 定期进行故障演练

## 下一步

- [故障排除](troubleshooting.md) - 详细的问题诊断和解决方案
- [性能调优](performance-tuning.md) - 深入的性能优化指南
- [开发集成](../development/tracing-integration.md) - 开发者集成文档
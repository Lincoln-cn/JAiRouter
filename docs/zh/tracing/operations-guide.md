# 运维指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-06-15
> **Git 提交**: 933eeadf
> **作者**: Lincoln
<!-- /版本信息 -->

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
    service-namespace: "production"

    # 采样配置
    #
    # 核对（issue #159 / #234）：原 `default-ratio` 以及 adaptive 下的 base-sample-rate /
    # max-traces-per-second / error-sample-rate / slow-request-threshold **类里都没有对应字段**；
    # 而 `strategy` 这个键在 issue #234 已真实实现，取值 ratio / rule / adaptive。
    sampling:
      strategy: ratio  # 高流量生产用纯比例采样；要按规则放行错误请求就改成 rule 并配 rules
      ratio: 0.01      # 全局采样率（v2.7.9+ 默认 0.1，这里按 1% 的高流量生产取值）
      
      # 自适应采样（可选）
      adaptive:
        enabled: true                 # 打开自适应采样
        target-spans-per-second: 100  # 目标每秒 Span 数
        min-ratio: 0.01               # 采样率下限
        max-ratio: 1.0                # 采样率上限
        adjustment-interval: 30       # 调整间隔（秒）
    
    # 导出配置 (v2.7.x 优化)
    exporter:
      type: "otlp"
      otlp:
        endpoint: "http://otel-collector:4317"
        timeout: 10s
        compression: "gzip"

    # 性能配置 (v2.7.x 优化)
    performance:
      # 异步处理总开关
      async-processing: true
      # 异步处理线程池（issue #215：原 async.worker-threads / async.queue-size 是死键，
      # 调了不生效；与代码对齐的键名是 thread-pool.*）
      thread-pool:
        core-size: 8
        queue-capacity: 8192
      batch:
        timeout: 30s
        size: 2048
      buffer:
        size: 8192
      
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
    # 核对（issue #159）：路径是 performance.memory，且没有 span-ttl 这个旋钮
    #（模型里没有固定 TTL，回收由 memory-limit-mb 的压力判定 + gc-interval 触发）。
    performance:
      memory:
        max-spans-in-memory: 100000  # 基于内存容量调整
        memory-limit-mb: 100
        gc-interval: 60s
```

#### 动态调整策略
```yaml
# 核对（issue #159）：memory-threshold 与 auto-cleanup 这两组键没有对应字段；
# 内存压力由 memory-limit-mb 直接决定（越过即回收），检查频率由 gc-interval 控制。
jairouter:
  tracing:
    performance:
      memory:
        memory-limit-mb: 80   # 降低上限，更早触发回收
        gc-interval: 30s      # 检查更频繁
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
        # 核对（issue #159）：原 log-access / log-config-changes / retention-days 三个键
        # 没有对应字段，已按字段实名改正。
        #
        # ⚠️ 再核对（issue #224）：`security.audit`（含 storage）整段的字段**当前仍无任何消费方** ——
        # 键名对了，但审计能力本身没有落地，配了不生效。是否删除整段待裁决，见 issue #224。
        enabled: true
        audit-data-access: true
        audit-config-changes: true
        storage:
          audit-log-retention: 90d
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

> 核对（issue #159）：**没有 `jairouter.tracing.auto-tuning` 这一段** —— 全仓无对应字段，
> 原示例照抄不会生效。现有的"自动"行为只有两处可调：`sampling.adaptive.*`（按目标 Span 速率
> 自动调整采样率）与 `performance.memory.gc-interval`（定期检查内存压力），见上文「内存规划」。

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

> 核对（issue #159）：**没有 `exporter.backup` 这一段** —— 追踪数据没有内置的备份/归档能力
> （全仓无对应字段）。长期留存请让 `exporter.type` 指向带存储的后端（OTLP 后端 / 日志采集），
> 或按「配置备份」一节对数据库做常规备份。

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
# 核对（issue #159）：没有 exporter.fallback 这些键。要做等效的降级，
# 直接把导出器类型换成 logging 即可（logging 导出器是真实存在的）。
jairouter:
  tracing:
    exporter:
      type: "logging"
      logging:
        enabled: true
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
- [使用指南](usage-guide.md) - API 参考和最佳实践
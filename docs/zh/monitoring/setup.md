# 监控设置指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档介绍如何设置和配置 JAiRouter 的监控功能，包括基础配置、监控栈部署和验证步骤。

## 环境要求

### 系统要求
- **操作系统**: Windows 10+, Linux, macOS
- **Java**: 17+
- **Docker**: 20.10+ (用于监控栈部署)
- **内存**: 至少 4GB 可用内存
- **磁盘**: 至少 10GB 可用空间

### 网络端口
- **JAiRouter**: 8080 (应用端口), 8081 (管理端口)
- **Prometheus**: 9090
- **Grafana**: 3000
- **AlertManager**: 9093

## 快速部署

### 方法一：一键部署脚本

#### Windows PowerShell
```powershell
# 运行部署脚本
.\scripts\setup-monitoring.ps1

# 带参数运行
.\scripts\setup-monitoring.ps1 -Environment prod -EnableAlerts
```

#### Linux/macOS
```bash
# 给脚本执行权限
chmod +x scripts/setup-monitoring.sh

# 运行部署脚本
./scripts/setup-monitoring.sh

# 带参数运行
./scripts/setup-monitoring.sh --environment prod --enable-alerts
```

### 方法二：手动部署

#### 1. 创建必要目录
```bash
mkdir -p monitoring/data/{prometheus,grafana,alertmanager}
```

#### 2. 启动监控栈
```bash
# 启动完整监控栈
docker-compose -f docker-compose-monitoring.yml up -d

# 或者使用 Make 命令
make compose-up-monitoring
```

#### 3. 验证服务状态
```bash
# 检查服务状态
docker-compose -f docker-compose-monitoring.yml ps

# 检查服务日志
docker-compose -f docker-compose-monitoring.yml logs
```

## 基础配置

### JAiRouter 应用配置

在 `application.yml` 中启用监控功能：

```yaml
# 监控指标配置
monitoring:
  metrics:
    # 启用监控功能
    enabled: true
    
    # 指标前缀
    prefix: "jairouter"
    
    # 指标收集间隔
    collection-interval: 10s
    
    # 启用的指标类别
    enabled-categories:
      - system      # 系统指标
      - business    # 业务指标
      - infrastructure  # 基础设施指标
    
    # 自定义标签
    custom-tags:
      environment: "${spring.profiles.active:default}"
      version: "@project.version@"

# Spring Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  
  endpoint:
    health:
      show-details: always
    prometheus:
      cache:
        time-to-live: 10s
  
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
```

### Prometheus 配置

创建 `monitoring/prometheus/prometheus.yml`：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "rules/*.yml"

scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
    honor_labels: true

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

### Grafana 配置

#### 数据源自动配置
创建 `monitoring/grafana/provisioning/datasources/prometheus.yml`：

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

#### 仪表板自动导入
创建 `monitoring/grafana/provisioning/dashboards/jairouter-dashboards.yml`：

```yaml
apiVersion: 1

providers:
  - name: 'jairouter'
    orgId: 1
    folder: 'JAiRouter'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

## Docker Compose 配置

### 完整监控栈配置

创建 `docker-compose-monitoring.yml`：

```yaml
version: '3.8'

services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - MONITORING_METRICS_ENABLED=true
    volumes:
      - ./config:/app/config
    networks:
      - monitoring
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus:/etc/prometheus
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=30d'
      - '--storage.tsdb.retention.size=10GB'
      - '--web.enable-lifecycle'
      - '--web.enable-admin-api'
    networks:
      - monitoring
    depends_on:
      - jairouter

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=jairouter2024
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_INSTALL_PLUGINS=grafana-piechart-panel
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    networks:
      - monitoring
    depends_on:
      - prometheus

  alertmanager:
    image: prom/alertmanager:latest
    ports:
      - "9093:9093"
    volumes:
      - ./monitoring/alertmanager:/etc/alertmanager
      - alertmanager_data:/alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
      - '--web.external-url=http://localhost:9093'
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:
  alertmanager_data:

networks:
  monitoring:
    driver: bridge
```

## 验证安装

### 1. 检查服务状态

```bash
# 检查所有服务状态
docker-compose -f docker-compose-monitoring.yml ps

# 检查 JAiRouter 健康状态
curl http://localhost:8080/actuator/health

# 检查 Prometheus 目标状态
curl http://localhost:9090/api/v1/targets
```

### 2. 验证指标收集

```bash
# 检查指标端点
curl http://localhost:8080/actuator/prometheus

# 检查特定指标
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

### 3. 验证 Grafana 仪表板

1. 访问 http://localhost:3000
2. 使用 admin/jairouter2024 登录
3. 检查数据源连接状态
4. 验证仪表板是否正常显示数据

## 环境特定配置

### 开发环境

```yaml
# application-dev.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
    performance:
      async-processing: false

management:
  endpoints:
    web:
      exposure:
        include: "*"
```

### 生产环境

```yaml
# application-prod.yml
monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.1
      backend-metrics: 0.5
    performance:
      async-processing: true
      batch-size: 500
    security:
      data-masking: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  security:
    enabled: true
```

### 测试环境

```yaml
# application-test.yml
monitoring:
  metrics:
    enabled: false

management:
  endpoints:
    web:
      exposure:
        include: health
```

## 安全配置

### 端点安全

```yaml
management:
  server:
    port: 8081
    address: 127.0.0.1
  endpoints:
    web:
      path-mapping:
        prometheus: /metrics
  security:
    enabled: true

spring:
  security:
    user:
      name: admin
      password: ${ACTUATOR_PASSWORD:admin}
      roles: ACTUATOR
```

### 网络安全

```yaml
# 限制 Prometheus 访问
networks:
  monitoring:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## 性能优化

### 指标采样配置

```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1    # 10% 采样
      backend-metrics: 0.5    # 50% 采样
      infrastructure-metrics: 0.1
      system-metrics: 0.5
```

### 异步处理配置

```yaml
monitoring:
  metrics:
    performance:
      async-processing: true
      async-thread-pool-size: 4
      batch-size: 100
      buffer-size: 1000
      processing-timeout: 5s
```

### 内存优化配置

```yaml
monitoring:
  metrics:
    memory:
      cache-size: 10000
      cache-expiry: 5m
      memory-threshold: 80
      low-memory-sampling-rate: 0.1
```

## 故障排查

### 常见问题

#### 1. 指标端点无法访问
**症状**: 访问 `/actuator/prometheus` 返回 404

**解决方案**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
```

#### 2. Grafana 无法连接 Prometheus
**症状**: 仪表板显示 "No data"

**解决方案**:
```bash
# 检查网络连通性
docker exec grafana curl http://prometheus:9090/api/v1/query?query=up

# 检查 Prometheus 配置
docker exec prometheus cat /etc/prometheus/prometheus.yml
```

#### 3. 监控数据为空
**症状**: Prometheus 端点返回空数据

**解决方案**:
```yaml
monitoring:
  metrics:
    enabled: true
    enabled-categories:
      - system
      - business
      - infrastructure
```

### 日志检查

```bash
# 检查 JAiRouter 日志
docker logs jairouter

# 检查 Prometheus 日志
docker logs prometheus

# 检查 Grafana 日志
docker logs grafana
```

## 维护操作

### 备份配置

```bash
# 备份配置文件
tar -czf monitoring-config-backup-$(date +%Y%m%d).tar.gz monitoring/

# 备份 Grafana 数据
docker exec grafana tar -czf /tmp/grafana-backup.tar.gz /var/lib/grafana
docker cp grafana:/tmp/grafana-backup.tar.gz ./grafana-backup-$(date +%Y%m%d).tar.gz
```

### 更新监控栈

```bash
# 拉取最新镜像
docker-compose -f docker-compose-monitoring.yml pull

# 重启服务
docker-compose -f docker-compose-monitoring.yml up -d
```

### 清理旧数据

```bash
# 清理 Docker 未使用的卷
docker volume prune

# 清理 Prometheus 旧数据（谨慎操作）
docker exec prometheus rm -rf /prometheus/01*
```

## 下一步

设置完成后，您可以：

1. [配置 Grafana 仪表板](dashboards.md)
2. [设置告警规则](alerts.md)
3. [了解可用指标](metrics.md)
4. [进行性能优化](performance.md)

---

**注意**: 在生产环境中部署前，请确保已经配置了适当的安全措施和资源限制。
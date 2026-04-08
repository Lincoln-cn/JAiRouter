# JAiRouter 监控功能配置指南

## 概述

JAiRouter 提供了全面的监控指标收集和 Prometheus 集成功能，本文档详细介绍如何配置和使用这些监控功能。

## 目录

1. [基础配置](#基础配置)
2. [指标类别配置](#指标类别配置)
3. [性能优化配置](#性能优化配置)
4. [Prometheus 集成配置](#prometheus-集成配置)
5. [动态配置管理](#动态配置管理)
6. [安全配置](#安全配置)

## 基础配置

### application.yml 监控配置

```yaml
# 监控指标配置
monitoring:
  metrics:
    # 启用监控功能
    enabled: true
    
    # 指标前缀，用于区分不同应用的指标
    prefix: "jairouter"
    
    # 指标收集间隔
    collection-interval: 10s
    
    # 启用的指标类别
    enabled-categories:
      - system      # 系统指标（JVM、HTTP等）
      - business    # 业务指标（模型调用、服务统计等）
      - infrastructure  # 基础设施指标（负载均衡、限流、熔断等）
    
    # 自定义标签，会添加到所有指标中
    custom-tags:
      environment: "production"
      version: "1.0.0"
      region: "us-west-1"
```

### Spring Actuator 增强配置

```yaml
# Spring Actuator 配置
management:
  endpoints:
    web:
      exposure:
        # 暴露的端点
        include: health,info,metrics,prometheus,jairouter-metrics
      base-path: /actuator
  
  endpoint:
    health:
      show-details: always
      show-components: always
    prometheus:
      cache:
        time-to-live: 10s
  
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
    tags:
      application: jairouter
      environment: ${spring.profiles.active:default}
```

## 指标类别配置

### 系统指标配置

系统指标自动收集，包括：
- JVM 内存使用情况
- 垃圾回收统计
- 线程池状态
- HTTP 请求统计

```yaml
monitoring:
  metrics:
    system:
      # JVM 指标收集间隔
      jvm-collection-interval: 30s
      
      # HTTP 指标详细程度
      http-detail-level: full  # full, basic, minimal
      
      # 线程池监控
      thread-pool-monitoring: true
```

### 业务指标配置

```yaml
monitoring:
  metrics:
    business:
      # 模型调用统计
      model-call-tracking: true
      
      # 请求大小统计
      request-size-tracking: true
      
      # 响应时间分布统计
      response-time-histogram: true
      
      # 用户会话跟踪
      session-tracking: false
```

### 基础设施指标配置

```yaml
monitoring:
  metrics:
    infrastructure:
      # 负载均衡器指标
      load-balancer-metrics: true
      
      # 限流器指标
      rate-limiter-metrics: true
      
      # 熔断器指标
      circuit-breaker-metrics: true
      
      # 健康检查指标
      health-check-metrics: true
```

## 性能优化配置

### 指标采样配置

```yaml
monitoring:
  metrics:
    # 指标采样配置
    sampling:
      # 请求指标采样率 (0.0-1.0)
      request-metrics: 1.0
      
      # 后端调用指标采样率
      backend-metrics: 1.0
      
      # 基础设施指标采样率
      infrastructure-metrics: 0.1
      
      # 系统指标采样率
      system-metrics: 0.5
```

### 异步处理配置

```yaml
monitoring:
  metrics:
    performance:
      # 启用异步指标处理
      async-processing: true
      
      # 异步处理线程池大小
      async-thread-pool-size: 4
      
      # 批量处理大小
      batch-size: 100
      
      # 缓冲区大小
      buffer-size: 1000
      
      # 处理超时时间
      processing-timeout: 5s
```

### 内存优化配置

```yaml
monitoring:
  metrics:
    memory:
      # 指标数据缓存大小
      cache-size: 10000
      
      # 缓存过期时间
      cache-expiry: 5m
      
      # 内存使用阈值（超过后启用采样）
      memory-threshold: 80
      
      # 低内存时的采样率
      low-memory-sampling-rate: 0.1
```

## Prometheus 集成配置

### Prometheus 服务器配置

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
    params:
      format: ['prometheus']
    
    # 指标重新标记
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        replacement: 'jairouter-main'
      
      - source_labels: [__name__]
        regex: 'jairouter_.*'
        target_label: __name__
        replacement: '${1}'

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

### Docker Compose 监控栈

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
      - '--storage.tsdb.retention.time=200h'
      - '--web.enable-lifecycle'
    networks:
      - monitoring

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:

networks:
  monitoring:
    driver: bridge
```

## 动态配置管理

### 配置热重载

JAiRouter 支持运行时动态更新监控配置，无需重启服务。

#### 通过 REST API 更新配置

```bash
# 更新指标采样率
curl -X POST http://localhost:8080/actuator/jairouter-metrics/config \
  -H "Content-Type: application/json" \
  -d '{
    "sampling": {
      "request-metrics": 0.5,
      "backend-metrics": 0.8
    }
  }'

# 启用/禁用指标类别
curl -X POST http://localhost:8080/actuator/jairouter-metrics/categories \
  -H "Content-Type: application/json" \
  -d '{
    "enabled-categories": ["system", "business"]
  }'
```

#### 通过配置文件更新

修改 `config/monitoring-override.yml`：

```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 0.3
    performance:
      batch-size: 200
```

系统会自动检测配置文件变化并应用新配置。

### 配置验证

```bash
# 验证当前配置
curl http://localhost:8080/actuator/jairouter-metrics/config

# 验证指标收集状态
curl http://localhost:8080/actuator/jairouter-metrics/status
```

## 安全配置

### 端点安全

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  security:
    enabled: true
    roles: ACTUATOR

spring:
  security:
    user:
      name: admin
      password: ${ACTUATOR_PASSWORD:admin}
      roles: ACTUATOR
```

### 网络安全

```yaml
management:
  server:
    # 使用独立端口
    port: 8081
    # 绑定到内网地址
    address: 127.0.0.1
  endpoints:
    web:
      # 限制访问路径
      path-mapping:
        prometheus: /metrics
```

### 指标数据脱敏

```yaml
monitoring:
  metrics:
    security:
      # 启用数据脱敏
      data-masking: true
      
      # 敏感标签脱敏
      mask-labels:
        - user_id
        - client_ip
        - api_key
      
      # IP 地址脱敏
      ip-masking: true
```

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
```

### 测试环境

```yaml
# application-test.yml
monitoring:
  metrics:
    enabled: false
```

## 配置验证和测试

### 配置语法验证

```bash
# 验证 YAML 语法
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.config.location=classpath:/application.yml --spring.profiles.active=test --spring.boot.admin.context-path=/admin"
```

### 指标端点测试

```bash
# 测试 Prometheus 端点
curl http://localhost:8080/actuator/prometheus

# 测试健康检查
curl http://localhost:8080/actuator/health

# 测试指标详情
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

## 常见配置问题

### 问题 1：指标端点无法访问

**症状**：访问 `/actuator/prometheus` 返回 404

**解决方案**：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
```

### 问题 2：指标数据为空

**症状**：Prometheus 端点返回空数据

**解决方案**：
```yaml
monitoring:
  metrics:
    enabled: true
    enabled-categories:
      - system
      - business
      - infrastructure
```

### 问题 3：性能影响过大

**症状**：启用监控后系统响应变慢

**解决方案**：
```yaml
monitoring:
  metrics:
    sampling:
      request-metrics: 0.1
    performance:
      async-processing: true
      batch-size: 1000
```

## 配置最佳实践

1. **分环境配置**：为不同环境使用不同的采样率和缓存配置
2. **安全优先**：生产环境必须启用端点安全和数据脱敏
3. **性能监控**：定期检查监控功能对系统性能的影响
4. **渐进式启用**：新环境先启用基础指标，逐步增加详细指标
5. **配置版本化**：将监控配置纳入版本控制系统

## 下一步

- [Grafana 仪表板使用指南](grafana-dashboard-guide.md)
- [故障排查和性能调优文档](../troubleshooting/troubleshooting-performance-guide.md)
- [监控指标参考手册](../metrics-reference-manual.md)
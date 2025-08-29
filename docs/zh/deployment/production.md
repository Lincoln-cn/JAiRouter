# 生产环境部署

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档详细介绍如何在生产环境中部署 JAiRouter，包括高可用架构、负载均衡配置、监控告警、备份恢复等企业级部署方案。

## 生产环境概述

### 架构特点

- **高可用性**：多实例部署，自动故障转移
- **负载均衡**：多层负载均衡，流量分发
- **监控告警**：全方位监控，及时告警
- **安全加固**：多层安全防护
- **备份恢复**：完整的备份和恢复策略

### 部署架构

```
graph TB
    subgraph "外部访问层"
        A[CDN/WAF]
        B[DNS负载均衡]
    end
    
    subgraph "负载均衡层"
        C[HAProxy/Nginx]
        D[HAProxy/Nginx备用]
    end
    
    subgraph "应用层"
        E[JAiRouter实例1]
        F[JAiRouter实例2]
        G[JAiRouter实例3]
        H[JAiRouter实例N]
    end
    
    subgraph "AI服务层"
        I[GPUStack集群]
        J[Ollama集群]
        K[VLLM集群]
        L[OpenAI API]
    end
    
    subgraph "数据层"
        M[配置存储]
        N[日志存储]
        O[监控数据]
    end
    
    subgraph "监控层"
        P[Prometheus集群]
        Q[Grafana]
        R[AlertManager]
        S[日志聚合]
    end
    
    A --> B
    B --> C
    B --> D
    C --> E
    C --> F
    D --> G
    D --> H
    
    E --> I
    F --> J
    G --> K
    H --> L
    
    E --> M
    F --> N
    G --> O
    
    P --> Q
    P --> R
    S --> P
```

## 系统要求

### 硬件要求

| 组件 | 最低配置 | 推荐配置 | 高性能配置 | 备注 |
|------|----------|----------|------------|------|
| **负载均衡器** | 2C4G | 4C8G | 8C16G | 主备模式，至少 2 台 |
| **应用服务器** | 4C8G | 8C16G | 16C32G | 至少 3 台，支持故障转移 |
| **数据库服务器** | 4C8G | 8C16G | 16C32G | 主从模式，读写分离 |
| **监控服务器** | 2C4G | 4C8G | 8C16G | 独立部署，避免影响业务 |
| **存储** | 100GB SSD | 500GB NVMe | 1TB+ NVMe | RAID 配置，数据冗余 |
| **网络** | 1Gbps | 10Gbps | 25Gbps | 双网卡绑定，网络冗余 |

### 软件要求

| 软件 | 版本 | 用途 |
|------|------|------|
| **操作系统** | Ubuntu 20.04+ / CentOS 8+ | 服务器操作系统 |
| **Docker** | 20.10+ | 容器运行时 |
| **Docker Compose** | 2.0+ | 容器编排 |
| **HAProxy** | 2.4+ | 负载均衡 |
| **Nginx** | 1.20+ | 反向代理 |
| **Prometheus** | 2.30+ | 监控系统 |
| **Grafana** | 8.0+ | 可视化监控 |

## 高可用架构部署

### 1. 负载均衡器配置

#### HAProxy 配置

创建 `/etc/haproxy/haproxy.cfg`：

```bash
global
    daemon
    maxconn 4096
    log stdout local0
    
defaults
    mode http
    timeout connect 5000ms
    timeout client 50000ms
    timeout server 50000ms
    option httplog
    option dontlognull
    option redispatch
    retries 3

# 统计页面
stats enable
stats uri /stats
stats refresh 30s
stats admin if TRUE

# 前端配置
frontend jairouter_frontend
    bind *:80
    bind *:443 ssl crt /etc/ssl/certs/jairouter.pem
    redirect scheme https if !{ ssl_fc }
    
    # 健康检查
    acl health_check path_beg /health
    use_backend health_backend if health_check
    
    default_backend jairouter_backend

# 后端配置
backend jairouter_backend
    balance roundrobin
    option httpchk GET /actuator/health
    http-check expect status 200
    
    server jairouter1 10.0.1.10:8080 check inter 5s fall 3 rise 2
    server jairouter2 10.0.1.11:8080 check inter 5s fall 3 rise 2
    server jairouter3 10.0.1.12:8080 check inter 5s fall 3 rise 2

backend health_backend
    server health 127.0.0.1:8080 check
```

#### Nginx 配置

创建 `/etc/nginx/sites-available/jairouter`：

```nginx
upstream jairouter_backend {
    least_conn;
    server 10.0.1.10:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.11:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.12:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    listen 443 ssl http2;
    server_name jairouter.example.com;
    
    # SSL 配置
    ssl_certificate /etc/ssl/certs/jairouter.crt;
    ssl_certificate_key /etc/ssl/private/jairouter.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512;
    
    # 安全头
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    
    # 日志配置
    access_log /var/log/nginx/jairouter_access.log;
    error_log /var/log/nginx/jairouter_error.log;
    
    # 代理配置
    location / {
        proxy_pass http://jairouter_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时配置
        proxy_connect_timeout 30s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
        
        # 缓冲配置
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;
    }
    
    # 健康检查
    location /health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
    
    # 监控端点
    location /actuator {
        proxy_pass http://jairouter_backend;
        allow 10.0.0.0/8;
        deny all;
    }
}
```

### 2. 应用服务器部署

#### Docker Compose 生产配置

创建 `docker-compose.prod.yml`：

```yaml
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter-${INSTANCE_ID:-1}
    hostname: jairouter-${INSTANCE_ID:-1}
    restart: unless-stopped
    
    ports:
      - "${PORT:-8080}:8080"
    
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
      - INSTANCE_ID=${INSTANCE_ID:-1}
      - CLUSTER_NODES=${CLUSTER_NODES}
    
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
      - ./config-store:/app/config-store
      - /etc/localtime:/etc/localtime:ro
    
    networks:
      - jairouter-network
    
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
    
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "3"
    
    security_opt:
      - no-new-privileges:true
    
    ulimits:
      nofile:
        soft: 65536
        hard: 65536

networks:
  jairouter-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

#### 多实例部署脚本

创建 `deploy-cluster.sh`：

```bash
#!/bin/bash

# 配置参数
INSTANCES=3
BASE_PORT=8080
CLUSTER_NODES=""

# 生成集群节点列表
for i in $(seq 1 $INSTANCES); do
    if [ $i -eq 1 ]; then
        CLUSTER_NODES="jairouter-$i:$((BASE_PORT + i - 1))"
    else
        CLUSTER_NODES="$CLUSTER_NODES,jairouter-$i:$((BASE_PORT + i - 1))"
    fi
done

echo "部署 JAiRouter 集群，节点: $CLUSTER_NODES"

# 部署各个实例
for i in $(seq 1 $INSTANCES); do
    echo "部署实例 $i..."
    
    INSTANCE_ID=$i \
    PORT=$((BASE_PORT + i - 1)) \
    CLUSTER_NODES=$CLUSTER_NODES \
    docker-compose -f docker-compose.prod.yml up -d
    
    sleep 10
done

echo "集群部署完成"

# 验证部署
echo "验证集群状态..."
for i in $(seq 1 $INSTANCES); do
    port=$((BASE_PORT + i - 1))
    if curl -f http://localhost:$port/actuator/health > /dev/null 2>&1; then
        echo "实例 $i (端口 $port): 健康"
    else
        echo "实例 $i (端口 $port): 异常"
    fi
done
```

### 3. 配置管理

#### 生产环境配置

创建 `config/application-prod.yml`：

```yaml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20000
    max-connections: 8192
    accept-count: 100

spring:
  application:
    name: jairouter
  profiles:
    active: prod

# 生产环境安全配置
jairouter:
  security:
    # 生产环境安全功能（默认关闭，需要显式启用）
    enabled: true
    
    # 生产环境 API Key 特定配置
    api-key:
      enabled: true
      default-expiration-days: 365
      cache-expiration-seconds: 3600  # 1小时缓存
      keys:
        # 生产环境API Key通过环境变量配置
        - key-id: "prod-admin-key"
          key-value: "${PROD_ADMIN_API_KEY:}"
          description: "生产环境管理员API密钥"
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "${PROD_ADMIN_KEY_EXPIRES:2025-12-31T23:59:59}"
          enabled: true
          metadata:
            environment: "production"
            created-by: "system"
            security-level: "high"
        
        - key-id: "prod-service-key"
          key-value: "${PROD_SERVICE_API_KEY:}"
          description: "生产环境服务API密钥"
          permissions: ["read", "write"]
          expires-at: "${PROD_SERVICE_KEY_EXPIRES:2025-12-31T23:59:59}"
          enabled: true
          metadata:
            environment: "production"
            created-by: "system"
            security-level: "medium"
        
        - key-id: "prod-readonly-key"
          key-value: "${PROD_READONLY_API_KEY:}"
          description: "生产环境只读API密钥"
          permissions: ["read"]
          expires-at: "${PROD_READONLY_KEY_EXPIRES:2025-12-31T23:59:59}"
          enabled: true
          metadata:
            environment: "production"
            created-by: "system"
            security-level: "low"
    
    # 生产环境 JWT 配置
    jwt:
      enabled: true
      secret: "${PROD_JWT_SECRET:}"
      issuer: "jairouter-prod"
      blacklist-cache:
        max-size: 50000  # 生产环境更大的黑名单缓存
    
    # 生产环境数据脱敏配置（严格模式）
    sanitization:
      request:
        enabled: true
        log-sanitization: false  # 生产环境不记录脱敏详情
        whitelist-users: []      # 生产环境不设置白名单用户
        whitelist-ips: []        # 生产环境不设置白名单IP
      
      response:
        enabled: true
        log-sanitization: false
    
    # 生产环境审计配置
    audit:
      enabled: true
      include-request-body: false   # 生产环境不包含请求体
      include-response-body: false  # 生产环境不包含响应体
      retention-days: 365  # 生产环境长期保留
      alert-enabled: true
      
      alert-thresholds:
        auth-failures-per-minute: 5      # 生产环境更严格的阈值
        sanitization-operations-per-minute: 200
        suspicious-requests-per-minute: 10
      
      event-types:
        authentication-success: false    # 生产环境不记录成功认证（减少日志量）
        sanitization-applied: false      # 生产环境不记录脱敏操作（减少日志量）
      
      storage:
        file-path: "logs/prod-security-audit.log"
        rotation:
          max-file-size: "500MB"  # 生产环境更大的文件大小
          max-files: 100
    
    # 生产环境监控配置
    monitoring:
      enabled: true
      metrics:
        security-events:
          by-user: false  # 生产环境不按用户统计（隐私考虑）
      
      alerts:
        enabled: true
        thresholds:
          authentication-failure-rate: 0.05  # 生产环境更严格的失败率阈值（5%）
          sanitization-error-rate: 0.01      # 生产环境更严格的错误率阈值（1%）
          response-time-p99: 500              # 生产环境更严格的响应时间要求
          max-requests-per-minute: 5000       # 生产环境更高的请求量阈值
        
        notifications:
          email:
            enabled: true
            recipients: 
              - "${SECURITY_ALERT_EMAIL:}"
          webhook:
            enabled: true
            url: "${SECURITY_ALERT_WEBHOOK:}"
          log:
            enabled: true
    
    # 生产环境性能配置
    performance:
      authentication:
        thread-pool-size: 20  # 生产环境更大的线程池
        timeout-ms: 3000      # 生产环境更短的超时时间
      
      sanitization:
        thread-pool-size: 10
        streaming-threshold: 2097152  # 2MB
        regex-cache-size: 500         # 生产环境更大的缓存
      
      cache:
        redis:
          enabled: true  # 生产环境启用Redis缓存
          host: "${REDIS_HOST:localhost}"
          port: "${REDIS_PORT:6379}"
          password: "${REDIS_PASSWORD:}"
          database: 0
          timeout: 2000
          pool:
            max-active: 20  # 生产环境更大的连接池
            max-idle: 10
            min-idle: 5
        local:
          enabled: true
          api-key:
            max-size: 10000  # 生产环境更大的本地缓存
            expire-after-write: 3600
          jwt-blacklist:
            max-size: 100000
            expire-after-write: 86400

# 生产环境监控配置覆盖
management:
  endpoint:
    health:
      show-details: when-authorized  # 生产环境仅授权用户可见健康详情
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
      environment: production
      instance: ${INSTANCE_ID:unknown}

# 日志配置
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    org.springframework.web: INFO
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  file:
    name: /app/logs/jairouter.log
    max-size: 500MB
    max-history: 30
    total-size-cap: 10GB
```

## 监控和告警

### 1. Prometheus 配置

创建 `monitoring/prometheus.yml`：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "rules/*.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093

scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: 
        - 'jairouter-1:8080'
        - 'jairouter-2:8080'
        - 'jairouter-3:8080'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
  
  - job_name: 'haproxy'
    static_configs:
      - targets: ['haproxy:8404']
    metrics_path: '/metrics'
  
  - job_name: 'nginx'
    static_configs:
      - targets: ['nginx-exporter:9113']
  
  - job_name: 'node'
    static_configs:
      - targets: 
        - 'node-exporter-1:9100'
        - 'node-exporter-2:9100'
        - 'node-exporter-3:9100'
```

### 2. 告警规则

创建 `monitoring/rules/jairouter.yml`：

```yaml
groups:
  - name: jairouter.rules
    rules:
      # 服务可用性告警
      - alert: JAiRouterDown
        expr: up{job="jairouter"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 实例宕机"
          description: "JAiRouter 实例 {{ $labels.instance }} 已宕机超过 1 分钟"
      
      # 高错误率告警
      - alert: JAiRouterHighErrorRate
        expr: rate(http_server_requests_total{status=~"5.."}[5m]) / rate(http_server_requests_total[5m]) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 错误率过高"
          description: "JAiRouter 实例 {{ $labels.instance }} 错误率超过 5%"
      
      # 响应时间告警
      - alert: JAiRouterHighLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 响应时间过长"
          description: "JAiRouter 实例 {{ $labels.instance }} 95% 响应时间超过 2 秒"
      
      # 内存使用告警
      - alert: JAiRouterHighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 内存使用率过高"
          description: "JAiRouter 实例 {{ $labels.instance }} 堆内存使用率超过 80%"
      
      # 限流告警
      - alert: JAiRouterHighRateLimitRejection
        expr: rate(jairouter_ratelimit_rejected_total[5m]) / rate(jairouter_ratelimit_requests_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 限流拒绝率过高"
          description: "JAiRouter 实例 {{ $labels.instance }} 限流拒绝率超过 10%"
      
      # 熔断器告警
      - alert: JAiRouterCircuitBreakerOpen
        expr: jairouter_circuitbreaker_state == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 熔断器开启"
          description: "JAiRouter 实例 {{ $labels.instance }} 服务 {{ $labels.service }} 熔断器已开启"
```

### 3. AlertManager 配置

创建 `monitoring/alertmanager.yml`：

```yaml
global:
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alerts@example.com'
  smtp_auth_username: 'alerts@example.com'
  smtp_auth_password: 'password'

route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'web.hook'
  routes:
    - match:
        severity: critical
      receiver: 'critical-alerts'
    - match:
        severity: warning
      receiver: 'warning-alerts'

receivers:
  - name: 'web.hook'
    webhook_configs:
      - url: 'http://webhook-server:5001/webhook'
        send_resolved: true

  - name: 'critical-alerts'
    email_configs:
      - to: 'ops-team@example.com'
        subject: '[CRITICAL] JAiRouter Alert'
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          {{ end }}
    webhook_configs:
      - url: 'http://webhook-server:5001/critical'
        send_resolved: true

  - name: 'warning-alerts'
    email_configs:
      - to: 'dev-team@example.com'
        subject: '[WARNING] JAiRouter Alert'
        body: |
          {{ range .Alerts }}
          Alert: {{ .Annotations.summary }}
          Description: {{ .Annotations.description }}
          {{ end }}

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'cluster', 'service']
```

### 4. Grafana 仪表板

创建 `monitoring/grafana/dashboards/jairouter-overview.json`：

```json
{
  "dashboard": {
    "id": null,
    "title": "JAiRouter Overview",
    "tags": ["jairouter"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_total[5m])) by (instance)",
            "legendFormat": "{{instance}}"
          }
        ],
        "yAxes": [
          {
            "label": "Requests/sec"
          }
        ]
      },
      {
        "id": 2,
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, instance))",
            "legendFormat": "95th percentile - {{instance}}"
          },
          {
            "expr": "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, instance))",
            "legendFormat": "50th percentile - {{instance}}"
          }
        ],
        "yAxes": [
          {
            "label": "Seconds"
          }
        ]
      },
      {
        "id": 3,
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_total{status=~\"5..\"}[5m])) by (instance) / sum(rate(http_server_requests_total[5m])) by (instance)",
            "legendFormat": "Error Rate - {{instance}}"
          }
        ],
        "yAxes": [
          {
            "label": "Percentage",
            "max": 1,
            "min": 0
          }
        ]
      },
      {
        "id": 4,
        "title": "JVM Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{area=\"heap\"} / jvm_memory_max_bytes{area=\"heap\"}",
            "legendFormat": "Heap Usage - {{instance}}"
          }
        ],
        "yAxes": [
          {
            "label": "Percentage",
            "max": 1,
            "min": 0
          }
        ]
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

## 安全配置

### 1. 网络安全

#### 防火墙配置

```bash
# Ubuntu/Debian
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow from 10.0.0.0/8 to any port 8080
ufw enable

# CentOS/RHEL
firewall-cmd --permanent --add-service=ssh
firewall-cmd --permanent --add-service=http
firewall-cmd --permanent --add-service=https
firewall-cmd --permanent --add-rich-rule="rule family='ipv4' source address='10.0.0.0/8' port protocol='tcp' port='8080' accept"
firewall-cmd --reload
```

#### SSL/TLS 配置

```bash
# 生成自签名证书（测试用）
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/ssl/private/jairouter.key \
  -out /etc/ssl/certs/jairouter.crt

# 或使用 Let's Encrypt
certbot --nginx -d jairouter.example.com
```

### 2. 应用安全

#### 环境变量管理

创建 `.env.prod`：

```bash
# API 密钥
OPENAI_API_KEY=sk-your-openai-api-key
ANTHROPIC_API_KEY=your-anthropic-api-key

# 数据库密码
DB_PASSWORD=your-secure-password

# JWT 密钥
JWT_SECRET=your-jwt-secret-key

# 监控密码
GRAFANA_ADMIN_PASSWORD=your-grafana-password
```

#### 容器安全

```yaml
# docker-compose.prod.yml 安全配置
services:
  jairouter:
    security_opt:
      - no-new-privileges:true
    read_only: true
    tmpfs:
      - /tmp
    user: "1001:1001"
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
```

### 3. 认证与授权配置

创建 `config/application-security.yml`：

```yaml
# 安全配置
security:
  # API Key 配置
  api-key:
    enabled: true
    header: X-API-Key
    # API 密钥存储路径
    file: /app/config/api-keys.yml
  
  # JWT 配置
  jwt:
    enabled: true
    secret: ${JWT_SECRET:dev-jwt-secret-key-for-development-only-not-for-production}
    algorithm: HS256
    expiration-minutes: 60
    issuer: jairouter
    # 账户配置
    accounts:
      - username: admin
        password: ${ADMIN_PASSWORD:admin}
        roles: [ADMIN, USER]
        enabled: true
      - username: user
        password: ${USER_PASSWORD:user}
        roles: [USER]
        enabled: true

  # CORS 配置
  cors:
    allowed-origins: "*"
    allowed-methods: "*"
    allowed-headers: "*"
    allow-credentials: false

# HTTPS 配置
server:
  port: 8443
  ssl:
    enabled: true
    key-store: /app/config/keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:password}
    key-store-type: PKCS12
    key-alias: jairouter
```

创建 `config/api-keys.yml`：

```yaml
# API 密钥配置
api-keys:
  - name: "service-a"
    key: "sk-service-a-key-here"
    permissions:
      - "chat:read"
      - "embedding:read"
    enabled: true
  
  - name: "service-b"
    key: "sk-service-b-key-here"
    permissions:
      - "chat:*"
      - "embedding:*"
    enabled: true
```

### 4. 安全审计日志

创建 `config/application-audit.yml`：

```yaml
# 安全审计配置
audit:
  enabled: true
  log-level: INFO
  # 审计日志文件
  file: /app/logs/audit.log
  # 审计事件类型
  events:
    - AUTHENTICATION_SUCCESS
    - AUTHENTICATION_FAILURE
    - ACCESS_DENIED
    - API_KEY_USAGE
  # 敏感操作监控
  sensitive-operations:
    - CONFIG_UPDATE
    - SERVICE_MANAGEMENT
    - USER_MANAGEMENT
```

## 日志配置

### 1. 日志级别配置

创建 `config/application-logging.yml`：

```yaml
# 日志配置
logging:
  level:
    # 核心组件日志级别
    org.unreal.modelrouter: INFO
    org.unreal.modelrouter.security: DEBUG
    org.unreal.modelrouter.tracing: DEBUG
    org.unreal.modelrouter.monitoring: DEBUG
    
    # Spring 框架日志级别
    org.springframework: WARN
    org.springframework.web: INFO
    org.springframework.security: INFO
    
    # Web 客户端日志级别
    org.springframework.web.reactive.function.client: WARN
    
    # 数据库相关日志
    org.hibernate: WARN
    com.zaxxer.hikari: WARN
    
  # 控制台日志配置
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  
  # 文件日志配置
  file:
    name: /app/logs/jairouter.log
    max-size: 500MB
    max-history: 30
    total-size-cap: 10GB
  
  # Logback 配置
  logback:
    rollingpolicy:
      max-file-size: 500MB
      max-history: 30
      total-size-cap: 10GB
      clean-history-on-start: true
```

### 2. 结构化日志配置

创建 `config/application-structured-logging.yml`：

```yaml
# 结构化日志配置
logging:
  level:
    org.unreal.modelrouter: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  
  file:
    name: /app/logs/jairouter.log
  
  # JSON 格式日志配置
  structured:
    enabled: true
    format: json
    fields:
      timestamp: "@timestamp"
      level: "level"
      logger: "logger"
      message: "message"
      thread: "thread"
      traceId: "traceId"
      spanId: "spanId"
      service: "service"
      instanceId: "instanceId"

# 结构化日志输出示例
# {
#   "@timestamp": "2024-01-15T10:00:00.123Z",
#   "level": "INFO",
#   "logger": "org.unreal.modelrouter.ModelRouterApplication",
#   "message": "Application started successfully",
#   "thread": "main",
#   "traceId": "abc123def456",
#   "service": "jairouter",
#   "instanceId": "jairouter-1"
# }
```

### 3. 日志轮转配置

创建 `/etc/logrotate.d/jairouter`：

```bash
# 日志轮转配置
/path/to/jairouter/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 jairouter jairouter
    postrotate
        docker exec jairouter-1 kill -USR1 1
        docker exec jairouter-2 kill -USR1 1
        docker exec jairouter-3 kill -USR1 1
    endscript
}

# 审计日志轮转配置
/path/to/jairouter/logs/audit.log {
    daily
    rotate 90
    compress
    delaycompress
    missingok
    notifempty
    create 0644 jairouter jairouter
}
```

### 4. 日志监控与告警

创建 `monitoring/rules/jairouter-logging.yml`：

```yaml
# 日志监控告警规则
groups:
  - name: jairouter.logging.rules
    rules:
      # 错误日志告警
      - alert: JAiRouterErrorLogs
        expr: rate(jairouter_log_errors_total[5m]) > 10
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 错误日志过多"
          description: "JAiRouter 实例 {{ $labels.instance }} 错误日志速率超过 10 条/分钟"
      
      # 安全事件告警
      - alert: JAiRouterSecurityEvents
        expr: rate(jairouter_security_events_total[5m]) > 5
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 安全事件"
          description: "JAiRouter 实例 {{ $labels.instance }} 安全事件速率超过 5 条/分钟"
      
      # 认证失败告警
      - alert: JAiRouterAuthFailures
        expr: rate(jairouter_auth_failures_total[5m]) > 20
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 认证失败过多"
          description: "JAiRouter 实例 {{ $labels.instance }} 认证失败速率超过 20 次/分钟"
```

## 备份和恢复

### 1. 配置备份

创建 `backup-config.sh`：

```bash
#!/bin/bash

BACKUP_DIR="/backup/jairouter"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="jairouter_config_$DATE.tar.gz"

# 创建备份目录
mkdir -p $BACKUP_DIR

# 备份配置文件
tar -czf $BACKUP_DIR/$BACKUP_FILE \
  config/ \
  docker-compose.prod.yml \
  monitoring/ \
  scripts/

# 保留最近 30 天的备份
find $BACKUP_DIR -name "jairouter_config_*.tar.gz" -mtime +30 -delete

echo "配置备份完成: $BACKUP_DIR/$BACKUP_FILE"
```

### 2. 数据备份

创建 `backup-data.sh`：

```bash
#!/bin/bash

BACKUP_DIR="/backup/jairouter"
DATE=$(date +%Y%m%d_%H%M%S)

# 备份配置存储
docker exec jairouter-1 tar -czf - /app/config-store | \
  cat > $BACKUP_DIR/config-store_$DATE.tar.gz

# 备份日志（最近7天）
find logs/ -name "*.log" -mtime -7 | \
  tar -czf $BACKUP_DIR/logs_$DATE.tar.gz -T -

# 备份监控数据
docker exec prometheus tar -czf - /prometheus | \
  cat > $BACKUP_DIR/prometheus_$DATE.tar.gz

echo "数据备份完成"
```

### 3. 自动备份

创建 crontab 任务：

```bash
# 编辑 crontab
crontab -e

# 添加备份任务
0 2 * * * /path/to/backup-config.sh
0 3 * * * /path/to/backup-data.sh
```

### 4. 恢复流程

创建 `restore.sh`：

```bash
#!/bin/bash

BACKUP_FILE=$1
RESTORE_DIR="/tmp/jairouter_restore"

if [ -z "$BACKUP_FILE" ]; then
    echo "用法: $0 <backup_file>"
    exit 1
fi

# 停止服务
docker-compose -f docker-compose.prod.yml down

# 创建恢复目录
mkdir -p $RESTORE_DIR
cd $RESTORE_DIR

# 解压备份文件
tar -xzf $BACKUP_FILE

# 恢复配置文件
cp -r config/ /path/to/jairouter/
cp docker-compose.prod.yml /path/to/jairouter/
cp -r monitoring/ /path/to/jairouter/

# 启动服务
cd /path/to/jairouter
docker-compose -f docker-compose.prod.yml up -d

echo "恢复完成"
```

## 性能优化

### 1. JVM 调优

```bash
# 生产环境 JVM 参数
JAVA_OPTS="
-Xms2g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/
-XX:+UseStringDeduplication
-XX:+OptimizeStringConcat
-Djava.security.egd=file:/dev/./urandom
"
```

### 2. 系统调优

```bash
# 内核参数优化
cat >> /etc/sysctl.conf << EOF
# 网络优化
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_keepalive_time = 1200
net.ipv4.tcp_max_tw_buckets = 5000

# 文件描述符
fs.file-max = 2097152
fs.nr_open = 2097152

# 虚拟内存
vm.swappiness = 10
vm.dirty_ratio = 15
vm.dirty_background_ratio = 5
EOF

sysctl -p
```

### 3. 容器优化

```yaml
# docker-compose.prod.yml 性能优化
services:
  jairouter:
    ulimits:
      nofile:
        soft: 65536
        hard: 65536
      nproc:
        soft: 32768
        hard: 32768
    
    sysctls:
      - net.core.somaxconn=65535
      - net.ipv4.tcp_keepalive_time=1200
    
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 4G
        reservations:
          cpus: '2.0'
          memory: 2G
```

## 运维管理

### 1. 健康检查脚本

创建 `health-check.sh`：

```bash
#!/bin/bash

INSTANCES=("jairouter-1:8080" "jairouter-2:8080" "jairouter-3:8080")
FAILED=0

echo "JAiRouter 集群健康检查 - $(date)"
echo "=================================="

for instance in "${INSTANCES[@]}"; do
    if curl -f -s http://$instance/actuator/health > /dev/null; then
        echo "✓ $instance - 健康"
    else
        echo "✗ $instance - 异常"
        FAILED=$((FAILED + 1))
    fi
done

echo "=================================="
echo "总实例数: ${#INSTANCES[@]}"
echo "健康实例: $((${#INSTANCES[@]} - FAILED))"
echo "异常实例: $FAILED"

if [ $FAILED -gt 0 ]; then
    exit 1
fi
```

### 2. 日志轮转

创建 `/etc/logrotate.d/jairouter`：

```bash
/path/to/jairouter/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 jairouter jairouter
    postrotate
        docker exec jairouter-1 kill -USR1 1
        docker exec jairouter-2 kill -USR1 1
        docker exec jairouter-3 kill -USR1 1
    endscript
}
```

### 3. 监控脚本

创建 `monitor.sh`：

```bash
#!/bin/bash

# 检查容器状态
echo "容器状态:"
docker ps --filter "name=jairouter" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 检查资源使用
echo -e "\n资源使用:"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"

# 检查日志错误
echo -e "\n最近错误日志:"
docker logs --since="1h" jairouter-1 2>&1 | grep -i error | tail -5
```

## 故障排查

### 1. 常见问题诊断

```bash
# 检查服务状态
systemctl status docker
docker-compose ps

# 检查网络连通性
curl -v http://localhost:8080/actuator/health
telnet jairouter-1 8080

# 检查资源使用
top
free -h
df -h

# 检查日志
docker logs jairouter-1 --tail 100
tail -f logs/jairouter.log
```

### 2. 性能问题排查

```bash
# JVM 性能分析
docker exec jairouter-1 jstack 1
docker exec jairouter-1 jstat -gc 1 5s

# 网络性能分析
iftop
netstat -i
ss -tuln

# 磁盘 I/O 分析
iotop
iostat -x 1
```

### 3. 故障恢复流程

1. **识别问题**：通过监控告警或健康检查发现问题
2. **隔离故障**：从负载均衡器中移除故障实例
3. **诊断原因**：分析日志、指标和系统状态
4. **修复问题**：重启服务、修复配置或扩容资源
5. **验证恢复**：确认服务正常后重新加入负载均衡
6. **总结改进**：记录故障原因和改进措施

## 最佳实践

### 1. 部署策略

- 使用蓝绿部署或滚动更新
- 实施金丝雀发布
- 配置自动回滚机制
- 建立完整的测试流程

### 2. 监控策略

- 设置多层监控（基础设施、应用、业务）
- 配置合理的告警阈值
- 建立故障响应流程
- 定期进行监控系统维护

### 3. 安全策略

- 定期更新系统和依赖
- 实施最小权限原则
- 配置网络隔离
- 建立安全审计机制

### 4. 运维策略

- 自动化部署和运维流程
- 建立完整的备份恢复机制
- 定期进行故障演练
- 持续优化性能和成本

## 下一步

完成生产环境部署后，您可以：

- **[监控指南](../monitoring/index.md)** - 深入了解监控配置
- **[故障排查](../troubleshooting/index.md)** - 学习故障诊断技能
- **[性能调优](../troubleshooting/performance.md)** - 优化系统性能
- **[API 参考](../api-reference/index.md)** - 了解管理 API
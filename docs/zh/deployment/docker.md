﻿# Docker 部署指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 提供完整的 Docker 化部署方案，支持多环境配置和容器编排。本文档详细介绍如何使用 Docker 部署 JAiRouter，包括单机部署、集群部署和监控集成。

## Docker 部署概述

### 核心特性

- **多阶段构建**：优化镜像大小，生产镜像约 200MB
- **多环境支持**：开发、测试、生产环境独立配置
- **中国网络优化**：专门优化的阿里云 Maven 镜像构建
- **安全最佳实践**：非 root 用户，最小权限运行
- **健康检查**：内置应用健康监控和自动恢复
- **监控集成**：完整的 Prometheus + Grafana 监控栈
- **日志管理**：结构化日志和日志轮转
- **配置管理**：支持动态配置和热更新

### 镜像信息

| 镜像类型 | 标签 | 大小 | 用途 |
|----------|------|------|------|
| **生产镜像** | `latest`, `v1.0.0` | ~200MB | 生产环境 |
| **开发镜像** | `dev`, `v1.0.0-dev` | ~220MB | 开发调试 |
| **中国镜像** | `china`, `v1.0.0-china` | ~200MB | 中国用户优化 |

## 快速开始

### 1. 拉取镜像

```
# 拉取最新生产镜像
docker pull sodlinken/jairouter:latest

# 拉取指定版本
docker pull sodlinken/jairouter:v1.0.0

# 中国用户（使用阿里云镜像）
docker pull registry.cn-hangzhou.aliyuncs.com/sodlinken/jairouter:latest

# 验证镜像
docker images | grep sodlinken/jairouter
```

### 2. 基础运行

```
# 最简单的运行方式
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# 验证部署
curl http://localhost:8080/actuator/health
```

### 3. 带配置运行

```
# 挂载配置文件运行
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  sodlinken/jairouter:latest
```

## 镜像构建

### 构建方式选择

| 构建方式 | 适用用户 | 命令 | 特点 | 构建时间 |
|----------|----------|------|------|----------|
| **中国加速** | 中国用户 | `./scripts/docker-build-china.sh` | 使用阿里云 Maven 镜像，速度提升 5-10 倍 | ~3-5 分钟 |
| **标准构建** | 国际用户 | `./scripts/docker-build.sh` | 使用 Maven Central，稳定可靠 | ~8-15 分钟 |
| **Maven 构建** | 开发者 | `mvn dockerfile:build -Pdocker` | 集成构建流程，支持多 profile | ~5-10 分钟 |
| **Jib 构建** | 高级用户 | `mvn jib:dockerBuild -Pjib` | 无需 Docker，更快构建，支持分层 | ~2-4 分钟 |

### 1. 使用构建脚本（推荐）

#### 中国用户（推荐）

```
# 使用中国优化构建脚本
./scripts/docker-build-china.sh

# 或者手动构建
mvn clean package -Pchina
docker build -f Dockerfile.china -t sodlinken/jairouter:latest .
```

#### 国际用户

```
# 使用标准构建脚本
./scripts/docker-build.sh

# 或者手动构建
mvn clean package
docker build -t sodlinken/jairouter:latest .
```

### 2. 使用 Maven 插件

```
# 使用 Dockerfile 插件
mvn clean package dockerfile:build -Pdocker

# 使用 Jib 插件（无需 Docker）
mvn clean package jib:dockerBuild -Pjib

# 构建并推送到注册表
mvn clean package jib:build -Pjib \
  -Djib.to.image=your-registry/sodlinken/jairouter:latest
```

### 3. 多环境构建

```
# 构建开发环境镜像
docker build -f Dockerfile.dev -t sodlinken/jairouter:dev .

# 构建生产环境镜像
docker build -f Dockerfile -t sodlinken/jairouter:prod .

# 构建中国优化镜像
docker build -f Dockerfile.china -t sodlinken/jairouter:china .
```

## 容器运行

### 1. 生产环境运行

```
docker run -d \
  --name jairouter-prod \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC" \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/config-store:/app/config-store \
  --restart unless-stopped \
  --health-cmd="curl -f http://localhost:8080/actuator/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-retries=3 \
  sodlinken/jairouter:latest
```

### 2. 开发环境运行

```
docker run -d \
  --name jairouter-dev \
  -p 8080:8080 \
  -p 5005:5005 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JAVA_OPTS="-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  -v $(pwd)/config:/app/config \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/src:/app/src:ro \
  sodlinken/jairouter:dev
```

### 3. 使用运行脚本

```
# Windows PowerShell
.\scripts\docker-run.ps1 prod latest

# Linux/macOS Bash
./scripts/docker-run.sh prod latest

# 开发环境
./scripts/docker-run.sh dev latest
```

## Docker Compose 部署

### 1. 基础 Compose 配置

创建 [docker-compose.yml](file://d:\IdeaProjects\model-router\docker-compose.yml)：

```
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
      - ./config-store:/app/config-store
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - jairouter-network

networks:
  jairouter-network:
    driver: bridge
```

### 2. 带监控的 Compose 配置

创建 `docker-compose.monitoring.yml`：

```
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    restart: unless-stopped
    networks:
      - monitoring
    depends_on:
      - prometheus

  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
    networks:
      - monitoring

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:

networks:
  monitoring:
    driver: bridge
```

### 3. 开发环境 Compose 配置

创建 `docker-compose.dev.yml`：

```
version: '3.8'

services:
  jairouter-dev:
    build:
      context: .
      dockerfile: Dockerfile.dev
    container_name: jairouter-dev
    ports:
      - "8080:8080"
      - "5005:5005"  # 调试端口
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - JAVA_OPTS=-Xms256m -Xmx512m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    volumes:
      - ./config:/app/config
      - ./logs:/app/logs
      - ./src:/app/src:ro  # 源码挂载（热重载）
    networks:
      - dev-network

networks:
  dev-network:
    driver: bridge
```

### 4. 运行 Compose

```
# 启动基础服务
docker-compose up -d

# 启动带监控的服务
docker-compose -f docker-compose.monitoring.yml up -d

# 启动开发环境
docker-compose -f docker-compose.dev.yml up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f jairouter

# 停止服务
docker-compose down
```

## 环境配置

### 1. 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring 激活的配置文件 |
| `JAVA_OPTS` | 见下表 | JVM 参数 |
| `SERVER_PORT` | `8080` | 应用端口 |
| `MANAGEMENT_PORT` | `8081` | 管理端口（可选） |

### 2. JVM 参数配置

| 环境 | 内存配置 | GC 配置 | 其他参数 |
|------|----------|---------|----------|
| **生产** | `-Xms512m -Xmx1024m` | `-XX:+UseG1GC` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |
| **开发** | `-Xms256m -Xmx512m` | `-XX:+UseG1GC` | `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` |
| **测试** | `-Xms256m -Xmx512m` | `-XX:+UseG1GC` | `-XX:+HeapDumpOnOutOfMemoryError` |

### 3. 目录挂载

| 容器路径 | 宿主机路径 | 用途 | 权限 |
|----------|------------|------|------|
| `/app/config` | `./config` | 配置文件 | 只读 |
| `/app/logs` | `./logs` | 日志文件 | 读写 |
| `/app/config-store` | `./config-store` | 配置存储 | 读写 |
| `/app/src` | `./src` | 源码（开发环境） | 只读 |

## 网络配置

### 1. 端口映射

```
# 基础端口映射
-p 8080:8080    # 应用端口

# 开发环境端口映射
-p 8080:8080    # 应用端口
-p 5005:5005    # 调试端口

# 监控端口映射
-p 9090:9090    # Prometheus
-p 3000:3000    # Grafana
```

### 2. 网络模式

```
# 桥接网络（默认）
networks:
  - jairouter-network

# 主机网络
network_mode: host

# 自定义网络
networks:
  custom-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## 健康检查

### 1. 容器健康检查

```
# Docker 运行时健康检查
docker run -d \
  --health-cmd="curl -f http://localhost:8080/actuator/health || exit 1" \
  --health-interval=30s \
  --health-timeout=10s \
  --health-retries=3 \
  --health-start-period=60s \
  sodlinken/jairouter:latest
```

### 2. Compose 健康检查

```
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

### 3. 健康检查验证

```
# 查看健康状态
docker ps --format "table {{.Names}}\t{{.Status}}"

# 查看健康检查日志
docker inspect jairouter --format='{{json .State.Health}}'

# 手动健康检查
curl http://localhost:8080/actuator/health
```

## 监控集成

### 1. Prometheus 配置

创建 `monitoring/prometheus.yml`：

```
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'jairouter'
    static_configs:
      - targets: ['jairouter:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

### 2. Grafana 仪表板

创建 `monitoring/grafana/dashboards/jairouter.json`：

```
{
  "dashboard": {
    "title": "JAiRouter Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_total[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      }
    ]
  }
}
```

### 3. 启动监控栈

```
# 启动完整监控栈
docker-compose -f docker-compose.monitoring.yml up -d

# 访问监控界面
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

## 日志管理

### 1. 日志配置

创建 `config/application-logging.yml`：

```
# 日志配置
logging:
  level:
    # 核心组件日志级别
    org.unreal.modelrouter: INFO
    org.unreal.modelrouter.security: DEBUG
    org.unreal.modelrouter.tracing: DEBUG
    
    # Spring 框架日志级别
    org.springframework: WARN
    org.springframework.web: INFO
    org.springframework.security: INFO
    
    # Web 客户端日志级别
    org.springframework.web.reactive.function.client: DEBUG
    
  # 控制台日志配置
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
  
  # 文件日志配置
  file:
    name: /app/logs/jairouter.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 10GB
  
  # Logback 配置
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 10GB
```

### 2. 日志查看

```
# 查看实时日志
docker logs -f jairouter

# 查看最近的日志
docker logs --tail 100 jairouter

# 查看特定时间的日志
docker logs --since "2024-01-15T10:00:00" jairouter

# 导出日志
docker logs jairouter > jairouter.log 2>&1

# 在容器内查看日志文件
docker exec jairouter cat /app/logs/jairouter.log
```

### 3. 日志轮转

```
# 配置 logrotate
cat > /etc/logrotate.d/docker-jairouter << EOF
/var/lib/docker/containers/*/*-json.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0644 root root
}
EOF

# Docker Compose 中的日志配置
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "3"
```

### 4. 结构化日志

创建 `config/application-structured-logging.yml`：

```
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

# 结构化日志输出示例
# {
#   "@timestamp": "2024-01-15T10:00:00.123Z",
#   "level": "INFO",
#   "logger": "org.unreal.modelrouter.ModelRouterApplication",
#   "message": "Application started successfully",
#   "thread": "main",
#   "traceId": "abc123def456"
# }
```

## 性能优化

### 1. 资源限制

```
services:
  jairouter:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
```

### 2. 容器优化

```
# 使用多阶段构建减小镜像大小
# 使用 .dockerignore 排除不必要文件
# 使用非 root 用户运行
# 启用健康检查
```

### 3. 网络优化

```
# 使用自定义网络
networks:
  jairouter-network:
    driver: bridge
    driver_opts:
      com.docker.network.bridge.name: jairouter0
      com.docker.network.driver.mtu: 1500
```

## 故障排查

### 1. 常见问题

#### 容器启动失败

```
# 查看容器状态
docker ps -a --filter "name=jairouter"

# 查看启动日志
docker logs jairouter

# 检查端口占用
netstat -tulpn | grep 8080

# 检查镜像是否存在
docker images | grep sodlinken/jairouter
```

#### 健康检查失败

```
# 手动执行健康检查
curl -v http://localhost:8080/actuator/health

# 检查容器内部
docker exec -it jairouter sh

# 查看应用日志
docker exec jairouter cat /app/logs/jairouter.log
```

#### 配置文件问题

```
# 检查配置文件挂载
docker exec jairouter ls -la /app/config

# 查看配置文件内容
docker exec jairouter cat /app/config/application.yml

# 验证配置文件格式
docker exec jairouter java -jar app.jar --spring.config.location=/app/config/application.yml --dry-run
```

### 2. 调试工具

```
# 进入容器调试
docker exec -it jairouter sh

# 查看进程状态
docker exec jairouter ps aux

# 查看网络连接
docker exec jairouter netstat -tulpn

# 查看系统资源
docker stats jairouter
```

### 3. 性能分析

```
# 查看容器资源使用
docker stats --no-stream jairouter

# 查看容器详细信息
docker inspect jairouter

# 查看镜像层信息
docker history sodlinken/jairouter:latest
```

## 安全配置

### 1. 容器安全

```
# 使用非 root 用户运行容器
docker run -d \
  --user 1001:1001 \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# 设置只读文件系统（除必要目录外）
docker run -d \
  --read-only \
  --tmpfs /tmp \
  --tmpfs /app/logs \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# 限制容器能力
docker run -d \
  --cap-drop ALL \
  --cap-add NET_BIND_SERVICE \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# 设置安全选项
docker run -d \
  --security-opt no-new-privileges:true \
  --security-opt seccomp=profile.json \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest
```

### 2. 网络安全

```
# docker-compose.yml 中的网络安全配置
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    container_name: jairouter
    ports:
      - "8080:8080"
    # 限制容器网络访问
    networks:
      - jairouter-network
    # 设置内部网络，无法访问外网
    networks:
      jairouter-network:
        internal: true

networks:
  jairouter-network:
    driver: bridge
```

### 3. 密钥管理

```
# 使用 Docker secrets 管理敏感信息
echo "your-api-key" | docker secret create jairouter-api-key -

# 在 swarm 模式下使用 secrets
docker service create \
  --name jairouter \
  --secret jairouter-api-key \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# 在 docker-compose 中使用 secrets
version: '3.8'

services:
  jairouter:
    image: sodlinken/jairouter:latest
    secrets:
      - jairouter-api-key
    environment:
      - API_KEY_FILE=/run/secrets/jairouter-api-key

secrets:
  jairouter-api-key:
    file: ./secrets/api-key.txt
```

### 4. 应用安全配置

创建 `config/application-security.yml`：

```
# 安全配置
security:
  # API Key 配置
  api-key:
    enabled: true
    header: X-API-Key
    keys:
      - name: default
        value: your-api-key-here
  
  # JWT 配置
  jwt:
    enabled: true
    secret: your-jwt-secret-key
    algorithm: HS256
    expiration-minutes: 60
    issuer: jairouter
    accounts:
      - username: admin
        password: admin-password
        roles: [ADMIN, USER]
        enabled: true
      - username: user
        password: user-password
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
    key-store: classpath:keystore.p12
    key-store-password: password
    key-store-type: PKCS12
    key-alias: jairouter
```

## 最佳实践

### 1. 镜像管理

- 使用多阶段构建减小镜像大小
- 定期更新基础镜像
- 使用镜像扫描工具检查漏洞
- 为不同环境构建不同镜像

### 2. 容器运行

- 使用健康检查确保服务可用
- 配置合适的资源限制
- 使用卷挂载持久化数据
- 配置日志轮转

### 3. 监控告警

- 集成 Prometheus 监控
- 配置 Grafana 仪表板
- 设置关键指标告警
- 定期检查容器健康状态

### 4. 安全考虑

- 使用非 root 用户运行
- 定期扫描镜像漏洞
- 配置网络隔离
- 使用密钥管理工具

## 下一步

完成 Docker 部署后，您可以：

- **[Kubernetes 部署](kubernetes.md)** - 扩展到 K8s 集群
- **[生产环境部署](production.md)** - 配置高可用生产环境
- **[监控指南](../monitoring/index.md)** - 设置完整的监控体系
- **[故障排查](../troubleshooting/index.md)** - 学习故障诊断和解决
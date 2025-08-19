# 中国网络环境优化部署

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档专门针对中国网络环境提供优化的部署方案，包括网络加速、镜像优化、依赖加速等配置，帮助中国用户获得更好的部署和运行体验。

## 中国优化概述

### 优化特性

- **Maven 镜像加速**：使用阿里云 Maven 镜像，依赖下载速度提升 5-10 倍
- **Docker 镜像加速**：配置国内 Docker 镜像源，镜像拉取更快
- **网络连接优化**：针对中国网络环境的连接超时和重试配置
- **CDN 加速**：使用国内 CDN 服务加速静态资源访问
- **DNS 优化**：配置国内 DNS 服务器，提升域名解析速度

### 网络环境挑战

| 挑战 | 影响 | 优化方案 |
|------|------|----------|
| **Maven 依赖下载慢** | 构建时间长，经常超时 | 使用阿里云 Maven 镜像 |
| **Docker 镜像拉取慢** | 部署时间长，可能失败 | 配置国内镜像源 |
| **网络连接不稳定** | 服务调用失败率高 | 优化超时和重试配置 |
| **DNS 解析慢** | 服务发现延迟 | 使用国内 DNS 服务 |
| **跨境网络延迟** | API 调用响应慢 | 使用国内 AI 服务提供商 |

## Maven 构建优化

### 1. 阿里云 Maven 镜像配置

JAiRouter 提供专门的中国优化构建配置：

#### settings-china.xml 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
  
  <mirrors>
    <!-- 阿里云 Maven 中央仓库镜像 -->
    <mirror>
      <id>aliyun-central</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Central</name>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
    
    <!-- 阿里云 Maven 公共仓库镜像 -->
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
    
    <!-- 阿里云 Spring 仓库镜像 -->
    <mirror>
      <id>aliyun-spring</id>
      <mirrorOf>spring-milestones,spring-snapshots</mirrorOf>
      <name>Aliyun Spring</name>
      <url>https://maven.aliyun.com/repository/spring</url>
    </mirror>
  </mirrors>
  
  <profiles>
    <profile>
      <id>china</id>
      <repositories>
        <repository>
          <id>aliyun-central</id>
          <url>https://maven.aliyun.com/repository/central</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </repository>
        
        <repository>
          <id>aliyun-spring</id>
          <url>https://maven.aliyun.com/repository/spring</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
      
      <pluginRepositories>
        <pluginRepository>
          <id>aliyun-plugin</id>
          <url>https://maven.aliyun.com/repository/central</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  
  <activeProfiles>
    <activeProfile>china</activeProfile>
  </activeProfiles>
</settings>
```

### 2. 中国优化构建脚本

#### Windows PowerShell 脚本

创建 `scripts/docker-build-china.ps1`：

```powershell
#!/usr/bin/env pwsh
# JAiRouter 中国优化 Docker 构建脚本

param(
    [string]$Tag = "latest",
    [string]$Profile = "china"
)

Write-Host "开始构建 JAiRouter (中国优化版本)..." -ForegroundColor Green
Write-Host "标签: $Tag" -ForegroundColor Yellow
Write-Host "配置: $Profile" -ForegroundColor Yellow

# 检查 Docker 是否运行
try {
    docker version | Out-Null
} catch {
    Write-Error "Docker 未运行或未安装"
    exit 1
}

# 构建应用
Write-Host "步骤 1: 使用中国镜像构建应用..." -ForegroundColor Cyan
try {
    .\mvnw.cmd clean package -P$Profile -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven 构建失败"
    }
} catch {
    Write-Error "Maven 构建失败: $_"
    exit 1
}

# 构建 Docker 镜像
Write-Host "步骤 2: 构建 Docker 镜像..." -ForegroundColor Cyan
try {
    docker build -f Dockerfile.china -t "jairouter/model-router:$Tag" .
    if ($LASTEXITCODE -ne 0) {
        throw "Docker 构建失败"
    }
} catch {
    Write-Error "Docker 构建失败: $_"
    exit 1
}

# 验证镜像
Write-Host "步骤 3: 验证镜像..." -ForegroundColor Cyan
$imageSize = docker images jairouter/model-router:$Tag --format "{{.Size}}"
Write-Host "镜像大小: $imageSize" -ForegroundColor Green

Write-Host "构建完成!" -ForegroundColor Green
Write-Host "镜像: jairouter/model-router:$Tag" -ForegroundColor Yellow
Write-Host "运行命令: docker run -d -p 8080:8080 jairouter/model-router:$Tag" -ForegroundColor Yellow
```

#### Linux/macOS Bash 脚本

创建 `scripts/docker-build-china.sh`：

```bash
#!/bin/bash
# JAiRouter 中国优化 Docker 构建脚本

set -e

TAG=${1:-latest}
PROFILE=${2:-china}

echo "开始构建 JAiRouter (中国优化版本)..."
echo "标签: $TAG"
echo "配置: $PROFILE"

# 检查 Docker 是否运行
if ! docker version >/dev/null 2>&1; then
    echo "错误: Docker 未运行或未安装"
    exit 1
fi

# 构建应用
echo "步骤 1: 使用中国镜像构建应用..."
./mvnw clean package -P$PROFILE -DskipTests

# 构建 Docker 镜像
echo "步骤 2: 构建 Docker 镜像..."
docker build -f Dockerfile.china -t "jairouter/model-router:$TAG" .

# 验证镜像
echo "步骤 3: 验证镜像..."
IMAGE_SIZE=$(docker images jairouter/model-router:$TAG --format "{{.Size}}")
echo "镜像大小: $IMAGE_SIZE"

echo "构建完成!"
echo "镜像: jairouter/model-router:$TAG"
echo "运行命令: docker run -d -p 8080:8080 jairouter/model-router:$TAG"
```

### 3. pom.xml 中国优化配置

在 `pom.xml` 中添加中国优化 profile：

```xml
<profiles>
    <profile>
        <id>china</id>
        <properties>
            <maven.compiler.source>17</maven.compiler.source>
            <maven.compiler.target>17</maven.compiler.target>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
            <!-- 跳过一些耗时的检查以加快构建 -->
            <checkstyle.skip>true</checkstyle.skip>
            <spotbugs.skip>true</spotbugs.skip>
            <jacoco.skip>true</jacoco.skip>
        </properties>
        
        <repositories>
            <repository>
                <id>aliyun-central</id>
                <url>https://maven.aliyun.com/repository/central</url>
                <releases>
                    <enabled>true</enabled>
                </releases>
                <snapshots>
                    <enabled>false</enabled>
                </snapshots>
            </repository>
        </repositories>
        
        <pluginRepositories>
            <pluginRepository>
                <id>aliyun-plugin</id>
                <url>https://maven.aliyun.com/repository/central</url>
                <releases>
                    <enabled>true</enabled>
                </releases>
                <snapshots>
                    <enabled>false</enabled>
                </snapshots>
            </pluginRepository>
        </pluginRepositories>
    </profile>
</profiles>
```

## Docker 镜像优化

### 1. Docker 镜像源配置

#### 配置 Docker 镜像加速器

创建或编辑 `/etc/docker/daemon.json`：

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com",
    "https://ccr.ccs.tencentyun.com"
  ],
  "insecure-registries": [],
  "debug": false,
  "experimental": false,
  "features": {
    "buildkit": true
  }
}
```

重启 Docker 服务：

```bash
# Ubuntu/Debian
sudo systemctl restart docker

# CentOS/RHEL
sudo systemctl restart docker

# Windows
# 重启 Docker Desktop
```

### 2. 中国优化 Dockerfile

`Dockerfile.china` 已经针对中国网络环境进行了优化：

```dockerfile
# 多阶段构建 Dockerfile for JAiRouter (China Optimized)
# 使用阿里云Maven镜像加速构建
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制阿里云Maven配置
COPY settings-china.xml /root/.m2/settings.xml

# 复制构建文件
COPY pom.xml .
COPY src ./src
COPY checkstyle.xml .
COPY spotbugs-security-include.xml .
COPY spotbugs-security-exclude.xml .

# 构建应用程序（使用china profile）
RUN mvn clean package -Pchina -DskipTests

# 运行阶段 - 使用阿里云镜像
FROM registry.cn-hangzhou.aliyuncs.com/acs/openjdk:17-jre-alpine

LABEL maintainer="JAiRouter Team"
LABEL description="JAiRouter - AI Model Service Routing and Load Balancing Gateway (China Optimized)"
LABEL version="1.0-SNAPSHOT"

# 创建应用用户
RUN addgroup -g 1001 jairouter && \
    adduser -D -s /bin/sh -u 1001 -G jairouter jairouter

WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs /app/config /app/config-store && \
    chown -R jairouter:jairouter /app

# 复制JAR文件
COPY --from=builder /app/target/model-router-*.jar app.jar

# 设置环境变量
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

EXPOSE 8080

USER jairouter

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## 网络连接优化

### 1. 应用配置优化

创建 `config/application-china.yml`：

```yaml
# 中国网络环境优化配置
server:
  port: 8080
  tomcat:
    connection-timeout: 30000  # 增加连接超时时间
    max-connections: 8192
    threads:
      max: 200
      min-spare: 10

# WebClient 网络优化
webclient:
  connection-timeout: 15s      # 增加连接超时
  read-timeout: 120s          # 增加读取超时
  write-timeout: 60s          # 增加写入超时
  max-in-memory-size: 50MB
  connection-pool:
    max-connections: 500      # 减少连接池大小
    max-idle-time: 60s        # 增加空闲时间
    pending-acquire-timeout: 90s  # 增加获取连接超时

# 重试配置
retry:
  max-attempts: 5             # 增加重试次数
  backoff:
    initial-interval: 2s      # 增加初始退避时间
    max-interval: 30s         # 增加最大退避时间
    multiplier: 2.0

# 熔断器配置（更宽松的阈值）
circuit-breaker:
  failure-threshold: 10       # 增加失败阈值
  recovery-timeout: 120000    # 增加恢复超时
  success-threshold: 5        # 增加成功阈值
  timeout: 60000             # 增加请求超时

# 健康检查优化
management:
  health:
    defaults:
      enabled: true
    diskspace:
      enabled: true
      threshold: 10GB
  endpoint:
    health:
      cache:
        time-to-live: 30s     # 增加健康检查缓存时间

# 日志配置
logging:
  level:
    org.springframework.web.reactive.function.client: DEBUG
    org.unreal.modelrouter.adapter: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
```

### 2. DNS 优化配置

#### 系统 DNS 配置

编辑 `/etc/resolv.conf`：

```bash
# 使用国内 DNS 服务器
nameserver 223.5.5.5      # 阿里云 DNS
nameserver 119.29.29.29   # 腾讯 DNS
nameserver 114.114.114.114 # 114 DNS
nameserver 8.8.8.8        # Google DNS (备用)

# DNS 选项优化
options timeout:2 attempts:3 rotate single-request-reopen
```

#### Docker 容器 DNS 配置

在 `docker-compose.china.yml` 中配置：

```yaml
version: '3.8'

services:
  jairouter:
    image: jairouter/model-router:china
    container_name: jairouter-china
    dns:
      - 223.5.5.5      # 阿里云 DNS
      - 119.29.29.29   # 腾讯 DNS
      - 114.114.114.114 # 114 DNS
    dns_search:
      - localdomain
    dns_opt:
      - timeout:2
      - attempts:3
    environment:
      - SPRING_PROFILES_ACTIVE=china
      - JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.net.preferIPv4Stack=true
    ports:
      - "8080:8080"
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
    restart: unless-stopped
    networks:
      - china-network

networks:
  china-network:
    driver: bridge
    driver_opts:
      com.docker.network.bridge.name: jairouter-china
```

## AI 服务提供商优化

### 1. 国内 AI 服务配置

配置使用国内 AI 服务提供商，减少跨境网络延迟：

```yaml
# config/services-china.yml
model:
  services:
    chat:
      instances:
        # 阿里云通义千问
        - name: "qwen-turbo"
          base-url: "https://dashscope.aliyuncs.com"
          path: "/api/v1/services/aigc/text-generation/generation"
          weight: 3
          headers:
            Authorization: "Bearer ${DASHSCOPE_API_KEY}"
          timeout: 60s
          
        # 百度文心一言
        - name: "ernie-bot"
          base-url: "https://aip.baidubce.com"
          path: "/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
          weight: 2
          headers:
            Content-Type: "application/json"
          timeout: 60s
          
        # 腾讯混元
        - name: "hunyuan"
          base-url: "https://hunyuan.tencentcloudapi.com"
          path: "/v1/chat/completions"
          weight: 2
          headers:
            Authorization: "Bearer ${TENCENT_API_KEY}"
          timeout: 60s
          
        # 智谱 ChatGLM
        - name: "chatglm"
          base-url: "https://open.bigmodel.cn"
          path: "/api/paas/v4/chat/completions"
          weight: 2
          headers:
            Authorization: "Bearer ${ZHIPU_API_KEY}"
          timeout: 60s
    
    embedding:
      instances:
        # 阿里云文本嵌入
        - name: "text-embedding-v1"
          base-url: "https://dashscope.aliyuncs.com"
          path: "/api/v1/services/embeddings/text-embedding/text-embedding"
          weight: 1
          headers:
            Authorization: "Bearer ${DASHSCOPE_API_KEY}"
```

### 2. 网络代理配置

如果需要访问海外 AI 服务，可以配置代理：

```yaml
# 代理配置
proxy:
  enabled: true
  http:
    host: proxy.example.com
    port: 8080
    username: ${PROXY_USERNAME}
    password: ${PROXY_PASSWORD}
  https:
    host: proxy.example.com
    port: 8080
    username: ${PROXY_USERNAME}
    password: ${PROXY_PASSWORD}
  no-proxy:
    - localhost
    - 127.0.0.1
    - "*.aliyuncs.com"
    - "*.baidubce.com"
    - "*.tencentcloudapi.com"
```

## 监控优化

### 1. 国内监控服务集成

配置使用国内监控服务：

```yaml
# docker-compose.monitoring-china.yml
version: '3.8'

services:
  jairouter:
    image: jairouter/model-router:china
    # ... 其他配置
    
  prometheus:
    image: registry.cn-hangzhou.aliyuncs.com/acs/prometheus:latest
    container_name: prometheus-china
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus-china.yml:/etc/prometheus/prometheus.yml:ro
    networks:
      - monitoring-china
      
  grafana:
    image: registry.cn-hangzhou.aliyuncs.com/acs/grafana:latest
    container_name: grafana-china
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_INSTALL_PLUGINS=grafana-piechart-panel,grafana-worldmap-panel
    networks:
      - monitoring-china

networks:
  monitoring-china:
    driver: bridge
```

### 2. 告警通知优化

配置国内通知服务：

```yaml
# monitoring/alertmanager-china.yml
global:
  # 使用国内 SMTP 服务
  smtp_smarthost: 'smtp.qq.com:587'
  smtp_from: 'alerts@example.com'
  smtp_auth_username: 'alerts@example.com'
  smtp_auth_password: '${QQ_MAIL_PASSWORD}'

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'china-alerts'

receivers:
  - name: 'china-alerts'
    email_configs:
      - to: 'ops@example.com'
        subject: '[JAiRouter] {{ .GroupLabels.alertname }}'
        body: |
          {{ range .Alerts }}
          告警: {{ .Annotations.summary }}
          描述: {{ .Annotations.description }}
          时间: {{ .StartsAt.Format "2006-01-02 15:04:05" }}
          {{ end }}
    
    # 企业微信通知
    wechat_configs:
      - corp_id: '${WECHAT_CORP_ID}'
        agent_id: '${WECHAT_AGENT_ID}'
        api_secret: '${WECHAT_API_SECRET}'
        to_user: '@all'
        message: |
          JAiRouter 告警通知
          {{ range .Alerts }}
          告警: {{ .Annotations.summary }}
          {{ end }}
    
    # 钉钉通知
    webhook_configs:
      - url: '${DINGTALK_WEBHOOK_URL}'
        send_resolved: true
        title: 'JAiRouter 告警'
        text: |
          {{ range .Alerts }}
          告警: {{ .Annotations.summary }}
          描述: {{ .Annotations.description }}
          {{ end }}
```

## 部署脚本优化

### 1. 一键部署脚本

创建 `deploy-china.sh`：

```bash
#!/bin/bash
# JAiRouter 中国优化一键部署脚本

set -e

echo "JAiRouter 中国优化部署脚本"
echo "=========================="

# 检查系统环境
check_environment() {
    echo "检查系统环境..."
    
    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        echo "错误: Docker 未安装"
        echo "请先安装 Docker: https://docs.docker.com/engine/install/"
        exit 1
    fi
    
    # 检查 Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo "错误: Docker Compose 未安装"
        echo "请先安装 Docker Compose: https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    echo "✓ 系统环境检查通过"
}

# 配置 Docker 镜像加速
configure_docker_mirror() {
    echo "配置 Docker 镜像加速..."
    
    DAEMON_JSON="/etc/docker/daemon.json"
    if [ ! -f "$DAEMON_JSON" ]; then
        sudo mkdir -p /etc/docker
        sudo tee $DAEMON_JSON > /dev/null <<EOF
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ]
}
EOF
        sudo systemctl restart docker
        echo "✓ Docker 镜像加速配置完成"
    else
        echo "✓ Docker 镜像加速已配置"
    fi
}

# 构建镜像
build_image() {
    echo "构建 JAiRouter 中国优化镜像..."
    
    if [ -f "scripts/docker-build-china.sh" ]; then
        chmod +x scripts/docker-build-china.sh
        ./scripts/docker-build-china.sh
    else
        echo "使用 Maven 构建..."
        ./mvnw clean package -Pchina -DskipTests
        docker build -f Dockerfile.china -t jairouter/model-router:china .
    fi
    
    echo "✓ 镜像构建完成"
}

# 部署应用
deploy_application() {
    echo "部署 JAiRouter 应用..."
    
    # 创建必要的目录
    mkdir -p config logs config-store
    
    # 复制配置文件
    if [ ! -f "config/application-china.yml" ]; then
        cp config/application.yml config/application-china.yml
        echo "✓ 配置文件已复制"
    fi
    
    # 启动应用
    docker-compose -f docker-compose.china.yml up -d
    
    echo "✓ 应用部署完成"
}

# 验证部署
verify_deployment() {
    echo "验证部署状态..."
    
    # 等待应用启动
    echo "等待应用启动..."
    sleep 30
    
    # 检查健康状态
    if curl -f -s http://localhost:8080/actuator/health > /dev/null; then
        echo "✓ 应用健康检查通过"
        echo "✓ JAiRouter 部署成功!"
        echo ""
        echo "访问地址:"
        echo "  应用: http://localhost:8080"
        echo "  健康检查: http://localhost:8080/actuator/health"
        echo "  API 文档: http://localhost:8080/swagger-ui/index.html"
    else
        echo "✗ 应用健康检查失败"
        echo "请检查日志: docker logs jairouter-china"
        exit 1
    fi
}

# 主流程
main() {
    check_environment
    configure_docker_mirror
    build_image
    deploy_application
    verify_deployment
}

# 执行主流程
main "$@"
```

### 2. Windows 部署脚本

创建 `deploy-china.ps1`：

```powershell
#!/usr/bin/env pwsh
# JAiRouter 中国优化一键部署脚本 (Windows)

param(
    [switch]$SkipBuild = $false,
    [switch]$Monitoring = $false
)

Write-Host "JAiRouter 中国优化部署脚本 (Windows)" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green

# 检查系统环境
function Test-Environment {
    Write-Host "检查系统环境..." -ForegroundColor Cyan
    
    # 检查 Docker
    try {
        docker version | Out-Null
        Write-Host "✓ Docker 已安装" -ForegroundColor Green
    } catch {
        Write-Error "Docker 未安装或未运行"
        Write-Host "请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop"
        exit 1
    }
    
    # 检查 Docker Compose
    try {
        docker-compose version | Out-Null
        Write-Host "✓ Docker Compose 已安装" -ForegroundColor Green
    } catch {
        Write-Error "Docker Compose 未安装"
        exit 1
    }
}

# 构建镜像
function Build-Image {
    if ($SkipBuild) {
        Write-Host "跳过镜像构建" -ForegroundColor Yellow
        return
    }
    
    Write-Host "构建 JAiRouter 中国优化镜像..." -ForegroundColor Cyan
    
    if (Test-Path "scripts\docker-build-china.ps1") {
        & "scripts\docker-build-china.ps1"
    } else {
        Write-Host "使用 Maven 构建..." -ForegroundColor Yellow
        .\mvnw.cmd clean package -Pchina -DskipTests
        docker build -f Dockerfile.china -t jairouter/model-router:china .
    }
    
    Write-Host "✓ 镜像构建完成" -ForegroundColor Green
}

# 部署应用
function Deploy-Application {
    Write-Host "部署 JAiRouter 应用..." -ForegroundColor Cyan
    
    # 创建必要的目录
    @("config", "logs", "config-store") | ForEach-Object {
        if (!(Test-Path $_)) {
            New-Item -ItemType Directory -Path $_ | Out-Null
        }
    }
    
    # 复制配置文件
    if (!(Test-Path "config\application-china.yml")) {
        Copy-Item "config\application.yml" "config\application-china.yml"
        Write-Host "✓ 配置文件已复制" -ForegroundColor Green
    }
    
    # 选择 Compose 文件
    $composeFile = if ($Monitoring) { "docker-compose.monitoring-china.yml" } else { "docker-compose.china.yml" }
    
    # 启动应用
    docker-compose -f $composeFile up -d
    
    Write-Host "✓ 应用部署完成" -ForegroundColor Green
}

# 验证部署
function Test-Deployment {
    Write-Host "验证部署状态..." -ForegroundColor Cyan
    
    # 等待应用启动
    Write-Host "等待应用启动..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
    
    # 检查健康状态
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "✓ 应用健康检查通过" -ForegroundColor Green
            Write-Host "✓ JAiRouter 部署成功!" -ForegroundColor Green
            Write-Host ""
            Write-Host "访问地址:" -ForegroundColor Yellow
            Write-Host "  应用: http://localhost:8080" -ForegroundColor White
            Write-Host "  健康检查: http://localhost:8080/actuator/health" -ForegroundColor White
            Write-Host "  API 文档: http://localhost:8080/swagger-ui/index.html" -ForegroundColor White
            
            if ($Monitoring) {
                Write-Host "  Prometheus: http://localhost:9090" -ForegroundColor White
                Write-Host "  Grafana: http://localhost:3000 (admin/admin)" -ForegroundColor White
            }
        }
    } catch {
        Write-Error "应用健康检查失败"
        Write-Host "请检查日志: docker logs jairouter-china" -ForegroundColor Red
        exit 1
    }
}

# 主流程
function Main {
    Test-Environment
    Build-Image
    Deploy-Application
    Test-Deployment
}

# 执行主流程
Main
```

## 性能调优

### 1. JVM 参数优化

针对中国网络环境的 JVM 参数优化：

```bash
# 中国网络环境 JVM 优化参数
JAVA_OPTS="
-Xms1g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/app/logs/

# 网络优化
-Djava.net.preferIPv4Stack=true
-Djava.net.useSystemProxies=true
-Dnetworkaddress.cache.ttl=60
-Dnetworkaddress.cache.negative.ttl=10

# 连接池优化
-Dhttp.maxConnections=50
-Dhttp.keepAlive=true
-Dhttp.maxRedirects=3

# 安全优化
-Djava.security.egd=file:/dev/./urandom
-Djava.awt.headless=true
"
```

### 2. 系统参数优化

```bash
# 网络参数优化
cat >> /etc/sysctl.conf << EOF
# TCP 优化
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_intvl = 30
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_tw_reuse = 1

# 连接数优化
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 65535

# 缓冲区优化
net.core.rmem_default = 262144
net.core.rmem_max = 16777216
net.core.wmem_default = 262144
net.core.wmem_max = 16777216
EOF

sysctl -p
```

## 故障排查

### 1. 网络连接问题

```bash
# 检查网络连通性
ping -c 4 maven.aliyun.com
ping -c 4 registry.cn-hangzhou.aliyuncs.com

# 检查 DNS 解析
nslookup maven.aliyun.com
dig maven.aliyun.com

# 检查端口连通性
telnet maven.aliyun.com 443
nc -zv maven.aliyun.com 443

# 检查代理设置
echo $http_proxy
echo $https_proxy
```

### 2. 构建问题排查

```bash
# 检查 Maven 配置
./mvnw help:effective-settings

# 检查依赖下载
./mvnw dependency:resolve -X

# 清理并重新构建
./mvnw clean
rm -rf ~/.m2/repository
./mvnw package -Pchina -DskipTests
```

### 3. 运行时问题排查

```bash
# 检查容器状态
docker ps --filter "name=jairouter"

# 检查容器日志
docker logs jairouter-china --tail 100

# 检查网络连接
docker exec jairouter-china netstat -tulpn

# 检查 DNS 解析
docker exec jairouter-china nslookup baidu.com
```

## 最佳实践

### 1. 网络优化建议

- 使用国内镜像源和 CDN 服务
- 配置合理的超时和重试参数
- 使用连接池和长连接
- 配置本地 DNS 缓存

### 2. 部署建议

- 选择合适的服务器地域（华东、华北等）
- 使用 SSD 存储提升 I/O 性能
- 配置监控和告警
- 定期备份配置和数据

### 3. 运维建议

- 监控网络质量和延迟
- 定期更新依赖和镜像
- 配置日志轮转和清理
- 建立故障响应流程

## 下一步

完成中国优化部署后，您可以：

- **[Docker 部署](docker.md)** - 了解完整的 Docker 部署方案
- **[生产环境部署](production.md)** - 配置高可用生产环境
- **[监控指南](../monitoring/index.md)** - 设置完整的监控体系
- **[故障排查](../troubleshooting/index.md)** - 学习故障诊断和解决
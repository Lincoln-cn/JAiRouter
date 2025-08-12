# JAiRouter Docker 部署指南

## 📋 概述

JAiRouter 支持多种 Docker 部署方式，包括传统的 Dockerfile 构建和现代的 Jib 插件构建。本文档提供了完整的 Docker 部署指南。

## 🐳 Docker 支持特性

### 1. 多阶段构建
- **构建阶段**: 使用 Maven 镜像编译应用
- **运行阶段**: 使用轻量级 JRE 镜像运行应用
- **镜像优化**: 最小化最终镜像大小

### 2. 多环境支持
- **生产环境**: 优化的生产配置
- **开发环境**: 包含调试支持的开发配置
- **环境隔离**: 不同环境使用不同的配置和资源限制

### 3. 安全最佳实践
- **非 root 用户**: 使用专用用户运行应用
- **最小权限**: 只暴露必要的端口和目录
- **健康检查**: 内置应用健康检查

## 🛠️ 构建方式

### 方式一: 传统 Dockerfile 构建

#### 1.1 生产环境构建
```bash
# 使用脚本构建（推荐）
./scripts/docker-build.sh prod

# 或手动构建
mvn clean package -DskipTests
docker build -t jairouter/model-router:latest .
```

#### 1.2 开发环境构建
```bash
# 使用脚本构建（推荐）
./scripts/docker-build.sh dev

# 或手动构建
mvn clean package
docker build -f Dockerfile.dev -t jairouter/model-router:dev .
```

### 方式二: Maven Dockerfile 插件
```bash
# 构建镜像
mvn clean package dockerfile:build -Pdocker

# 构建并推送镜像
mvn clean package dockerfile:build dockerfile:push -Pdocker
```

### 方式三: Jib 插件构建（推荐）
```bash
# 构建到本地 Docker
mvn clean package jib:dockerBuild -Pjib

# 构建并推送到注册表
mvn clean package jib:build -Pjib
```

## 🚀 运行方式

### 方式一: 使用脚本运行（推荐）

#### 1.1 生产环境
```bash
./scripts/docker-run.sh prod latest
```

#### 1.2 开发环境
```bash
./scripts/docker-run.sh dev latest
```

### 方式二: 直接 Docker 命令

#### 2.1 生产环境
```bash
docker run -d \
  --name jairouter-prod \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/config-store:/app/config-store \
  --restart unless-stopped \
  jairouter/model-router:latest
```

#### 2.2 开发环境
```bash
docker run -d \
  --name jairouter-dev \
  -p 8080:8080 \
  -p 5005:5005 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -v $(pwd)/config:/app/config \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/config-store:/app/config-store \
  jairouter/model-router:dev
```

### 方式三: Docker Compose

#### 3.1 生产环境
```bash
# 启动应用
docker-compose up -d

# 启动应用和监控
docker-compose --profile monitoring up -d
```

#### 3.2 开发环境
```bash
docker-compose -f docker-compose.dev.yml up -d
```

## 📊 镜像信息

### 镜像标签规范
| 标签格式 | 说明 | 示例 |
|----------|------|------|
| `latest` | 最新生产版本 | `jairouter/model-router:latest` |
| `{version}` | 指定版本 | `jairouter/model-router:1.0-SNAPSHOT` |
| `{version}-dev` | 开发版本 | `jairouter/model-router:1.0-SNAPSHOT-dev` |

### 镜像大小对比
| 构建方式 | 基础镜像 | 大小 | 特点 |
|----------|----------|------|------|
| Dockerfile | eclipse-temurin:17-jre-alpine | ~200MB | 传统构建，完全控制 |
| Jib | eclipse-temurin:17-jre-alpine | ~180MB | 优化构建，无需 Docker |

## 🔧 配置说明

### 环境变量
| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring 激活的配置文件 |
| `JAVA_OPTS` | 见下表 | JVM 参数 |
| `SERVER_PORT` | `8080` | 应用端口 |

### JVM 参数配置
| 环境 | 内存配置 | GC配置 | 其他参数 |
|------|----------|--------|----------|
| **生产** | `-Xms512m -Xmx1024m` | `-XX:+UseG1GC` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |
| **开发** | `-Xms256m -Xmx512m` | `-XX:+UseG1GC` | `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` |

### 目录挂载
| 容器路径 | 宿主机路径 | 用途 | 权限 |
|----------|------------|------|------|
| `/app/config` | `./config` | 配置文件 | 只读 |
| `/app/logs` | `./logs` | 日志文件 | 读写 |
| `/app/config-store` | `./config-store` | 配置存储 | 读写 |

## 🔍 健康检查

### 内置健康检查
```bash
# 检查应用健康状态
curl http://localhost:8080/actuator/health

# 检查容器健康状态
docker ps --filter "name=jairouter" --format "table {{.Names}}\t{{.Status}}"
```

### 健康检查配置
- **检查间隔**: 30秒
- **超时时间**: 10秒
- **启动等待**: 60秒
- **重试次数**: 3次

## 📈 监控集成

### Prometheus 监控
```bash
# 启动应用和监控栈
docker-compose --profile monitoring up -d

# 访问监控界面
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

### 监控指标
- 应用性能指标
- JVM 内存和 GC 指标
- HTTP 请求指标
- 自定义业务指标

## 🛡️ 安全配置

### 容器安全
- 使用非 root 用户 (UID: 1001)
- 最小化镜像攻击面
- 只暴露必要端口
- 只读挂载配置文件

### 网络安全
- 使用自定义网络
- 限制容器间通信
- 配置防火墙规则

## 🚨 故障排查

### 常见问题

#### 1. 容器启动失败
```bash
# 查看容器日志
docker logs jairouter-prod

# 检查容器状态
docker ps -a --filter "name=jairouter"

# 进入容器调试
docker exec -it jairouter-prod sh
```

#### 2. 健康检查失败
```bash
# 手动执行健康检查
curl -v http://localhost:8080/actuator/health

# 检查应用日志
docker logs --tail 50 jairouter-prod

# 检查端口占用
netstat -tlnp | grep 8080
```

#### 3. 配置文件问题
```bash
# 检查配置文件挂载
docker exec jairouter-prod ls -la /app/config

# 检查配置文件内容
docker exec jairouter-prod cat /app/config/application.yml
```

### 日志分析
```bash
# 实时查看日志
docker logs -f jairouter-prod

# 查看最近的错误日志
docker logs jairouter-prod 2>&1 | grep ERROR

# 导出日志到文件
docker logs jairouter-prod > jairouter.log 2>&1
```

## 📝 最佳实践

### 1. 镜像构建
- 使用多阶段构建减小镜像大小
- 利用 Docker 缓存加速构建
- 定期更新基础镜像

### 2. 容器运行
- 使用健康检查确保服务可用
- 配置合适的资源限制
- 使用卷挂载持久化数据

### 3. 生产部署
- 使用 Docker Compose 或 Kubernetes
- 配置日志轮转和监控
- 实施备份和恢复策略

### 4. 安全考虑
- 定期扫描镜像漏洞
- 使用最小权限原则
- 配置网络隔离

## 🔄 CI/CD 集成

### GitHub Actions 示例
```yaml
name: Docker Build and Push

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]

jobs:
  docker:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build with Maven
      run: mvn clean package -DskipTests
    
    - name: Build Docker image
      run: docker build -t jairouter/model-router:${{ github.sha }} .
    
    - name: Push to registry
      run: |
        echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
        docker push jairouter/model-router:${{ github.sha }}
```

## 📚 相关命令速查

### 构建命令
```bash
# 传统构建
./scripts/docker-build.sh prod
./scripts/docker-build.sh dev

# Maven 插件构建
mvn clean package dockerfile:build -Pdocker
mvn clean package jib:dockerBuild -Pjib
```

### 运行命令
```bash
# 脚本运行
./scripts/docker-run.sh prod
./scripts/docker-run.sh dev

# Compose 运行
docker-compose up -d
docker-compose -f docker-compose.dev.yml up -d
```

### 管理命令
```bash
# 查看镜像
docker images | grep jairouter

# 查看容器
docker ps --filter "name=jairouter"

# 清理资源
docker system prune -f
docker image prune -f
```
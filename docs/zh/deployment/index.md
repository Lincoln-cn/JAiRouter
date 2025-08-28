# 部署指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 提供多种部署方式，支持从开发环境到生产环境的完整部署方案。本指南将帮助您选择合适的部署方式并完成部署配置。

## 部署概述

### 支持的部署方式

| 部署方式 | 复杂度 | 适用场景 | 推荐度 |
|----------|--------|----------|--------|
| **Docker 部署** | 低 | 开发、测试、生产 | ⭐⭐⭐⭐⭐ |
| **Kubernetes 部署** | 高 | 大规模生产环境 | ⭐⭐⭐⭐ |
| **传统部署** | 中 | 特殊环境要求 | ⭐⭐⭐ |
| **云平台部署** | 中 | 云原生环境 | ⭐⭐⭐⭐ |

### 部署架构

```
graph TB
    subgraph "负载均衡层"
        A[Nginx/HAProxy]
    end
    
    subgraph "应用层"
        B[JAiRouter 实例 1]
        C[JAiRouter 实例 2]
        D[JAiRouter 实例 N]
    end
    
    subgraph "AI 服务层"
        E[GPUStack]
        F[Ollama]
        G[VLLM]
        H[OpenAI API]
    end
    
    subgraph "监控层"
        I[Prometheus]
        J[Grafana]
        K[AlertManager]
    end
    
    A --> B
    A --> C
    A --> D
    
    B --> E
    B --> F
    C --> G
    C --> H
    D --> E
    D --> G
    
    B --> I
    C --> I
    D --> I
    I --> J
    I --> K
```

## 部署方式选择

### 1. Docker 部署（推荐）

**适用场景**：
- 开发和测试环境
- 中小规模生产环境
- 快速原型验证
- 容器化环境

**优势**：
- 部署简单，一键启动
- 环境隔离，依赖管理简单
- 支持多环境配置
- 内置健康检查和监控

### 2. Kubernetes 部署

**适用场景**：
- 大规模生产环境
- 需要自动扩缩容
- 微服务架构
- 云原生环境

**优势**：
- 自动扩缩容
- 高可用性
- 服务发现和负载均衡
- 滚动更新

### 3. 生产环境部署

**适用场景**：
- 企业级生产环境
- 高可用性要求
- 大规模并发访问
- 严格的 SLA 要求

**特点**：
- 多实例部署
- 负载均衡配置
- 监控和告警
- 备份和恢复

## 系统要求

### 硬件要求

| 组件 | 最低要求 | 推荐配置 | 高性能配置 |
|------|----------|----------|------------|
| **CPU** | 2 cores | 4 cores | 8+ cores |
| **内存** | 2GB | 4GB | 8GB+ |
| **存储** | 10GB | 20GB SSD | 50GB+ NVMe |
| **网络** | 100Mbps | 1Gbps | 10Gbps |

### 软件要求

| 软件 | 版本要求 | 用途 |
|------|----------|------|
| **Docker** | 20.10+ | 容器运行时 |
| **Docker Compose** | 2.0+ | 容器编排 |
| **Kubernetes** | 1.20+ | 容器编排（可选） |
| **Java** | 17+ | 传统部署 |

## 部署指南

### [Docker 部署](docker.md)
完整的 Docker 部署指南，包括：
- 单机和集群 Docker 部署
- Docker Compose 多环境配置
- 容器监控和日志管理
- 性能优化和安全配置
- 故障排查和最佳实践

### [Kubernetes 部署](kubernetes.md)
企业级 Kubernetes 集群部署：
- K8s 集群基础部署
- Helm Chart 包管理
- 自动扩缩容和滚动更新
- 服务网格和监控集成
- 高可用和故障恢复

### [生产环境部署](production.md)
企业级高可用生产部署：
- 多层高可用架构设计
- 负载均衡和故障转移
- 全方位监控告警体系
- 完整备份恢复策略
- 安全加固和性能调优

### [中国网络优化部署](china-optimization.md)
针对中国网络环境的专项优化：
- Maven 和 Docker 镜像加速
- 网络连接和 DNS 优化
- 国内 AI 服务提供商集成
- 一键部署脚本和工具
- 网络问题故障排查

## 快速开始

### 1. Docker 快速部署

```
# 拉取镜像
docker pull jairouter/model-router:latest

# 启动容器
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  jairouter/model-router:latest

# 验证部署
curl http://localhost:8080/actuator/health
```

### 2. Docker Compose 部署

```
# 下载配置文件
wget https://raw.githubusercontent.com/your-org/jairouter/main/docker-compose.yml

# 启动服务
docker-compose up -d

# 查看状态
docker-compose ps
```

### 3. 中国用户快速部署

```
# 使用中国优化镜像
docker pull registry.cn-hangzhou.aliyuncs.com/jairouter/model-router:latest

# 启动容器
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  registry.cn-hangzhou.aliyuncs.com/jairouter/model-router:latest
```

## 部署检查清单

### 部署前检查

- [ ] 系统资源满足要求
- [ ] 网络连通性正常
- [ ] 依赖软件已安装
- [ ] 配置文件已准备
- [ ] 安全策略已配置

### 部署后验证

- [ ] 应用启动成功
- [ ] 健康检查通过
- [ ] API 接口正常
- [ ] 监控指标正常
- [ ] 日志输出正常

### 生产环境检查

- [ ] 高可用配置生效
- [ ] 负载均衡正常
- [ ] 监控告警配置
- [ ] 备份策略执行
- [ ] 安全扫描通过

## 安全配置指南

JAiRouter 提供了多层次的安全防护机制，包括：

### 认证与授权
- **API Key 认证**：适用于服务间通信的简单认证机制
- **JWT 认证**：适用于用户认证的完整解决方案
- **角色访问控制**：基于角色的细粒度访问控制

### 网络安全
- **HTTPS 支持**：通过 TLS 加密保护数据传输
- **防火墙配置**：限制不必要的端口访问
- **请求过滤**：防止恶意请求和攻击

### 应用安全
- **输入验证**：防止注入攻击和恶意输入
- **敏感信息保护**：配置加密和密钥管理
- **安全头设置**：防止常见的 Web 攻击

## 日志配置指南

JAiRouter 支持灵活的日志配置，包括：

### 日志级别
- **TRACE**：最详细的日志信息，用于调试
- **DEBUG**：详细的调试信息
- **INFO**：一般信息性消息
- **WARN**：警告信息
- **ERROR**：错误信息

### 日志格式
- **控制台输出**：适用于开发环境的简洁格式
- **文件日志**：适用于生产环境的详细格式
- **结构化日志**：JSON 格式，便于日志分析和处理

### 日志管理
- **日志轮转**：自动管理日志文件大小和数量
- **日志压缩**：节省存储空间
- **日志归档**：长期存储重要日志

## 故障排查

### 常见问题

1. **应用启动失败**
   ```bash
   # 检查端口占用
   netstat -tulpn | grep 8080
   
   # 查看容器日志
   docker logs jairouter
   ```

2. **健康检查失败**
   ```bash
   # 手动健康检查
   curl -v http://localhost:8080/actuator/health
   
   # 检查应用状态
   docker ps --filter "name=jairouter"
   ```

3. **性能问题**
   ```bash
   # 查看资源使用
   docker stats jairouter
   
   # 查看应用指标
   curl http://localhost:8080/actuator/metrics
   ```

### 调试工具

```bash
# 进入容器调试
docker exec -it jairouter sh

# 查看配置
docker exec jairouter cat /app/config/application.yml

# 查看日志
docker logs -f jairouter
```

## 最佳实践

### 1. 安全配置

- 使用非 root 用户运行
- 配置防火墙规则
- 启用 HTTPS
- 定期更新依赖

### 2. 性能优化

- 合理配置 JVM 参数
- 使用连接池
- 启用缓存
- 配置负载均衡

### 3. 监控告警

- 设置关键指标监控
- 配置告警规则
- 建立故障响应流程
- 定期检查监控系统

### 4. 运维自动化

- 使用 CI/CD 流水线
- 自动化部署脚本
- 配置管理工具
- 自动化测试

## 下一步

选择适合您的部署方式：

- **[Docker 部署](docker.md)** - 快速开始，适合大多数场景
- **[Kubernetes 部署](kubernetes.md)** - 大规模生产环境
- **[生产环境部署](production.md)** - 高可用生产配置
- **[中国优化部署](china-optimization.md)** - 中国用户专用优化
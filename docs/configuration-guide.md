# JAiRouter 配置指南

## 目录

1. [配置概述](#config-overview)
2. [配置加载优先级](#config-loading-priority)
3. [模块化配置结构](#modular-config-structure)
4. [环境变量配置](#env-config)
5. [外部配置文件](#external-config)
6. [多环境配置](#multi-env-config)
7. [配置校验机制](#config-validation)
8. [配置API](#config-api)
9. [迁移指南](#migration-guide)

---

## 配置概述 {#config-overview}

JAiRouter v2.8.x 采用模块化配置架构，将配置按服务模块分离，支持外部配置文件和环境变量覆盖。

### 主要特性

- **模块化配置**：按 auth/router/monitor/persistence/common 分离
- **外部配置支持**：Docker 部署时挂载 `/app/config/`
- **敏感配置分离**：强制使用环境变量
- **配置校验机制**：启动时自动校验配置完整性
- **配置来源追踪**：日志输出配置加载来源

---

## 配置加载优先级 {#config-loading-priority}

配置按以下优先级加载（高优先级覆盖低优先级）：

| 优先级 | 来源 | 示例 | 适用场景 |
|--------|------|------|---------|
| 1 | 命令行参数 | `--server.port=8081` | 临时调试 |
| 2 | 系统属性 | `-Dserver.port=8081` | JVM 参数 |
| 3 | 环境变量 | `SERVER_PORT=8081` | **生产部署** |
| 4 | 外部配置文件 | `/app/config/application.yml` | **Docker 部署** |
| 5 | 环境特定配置 | `application-prod.yml` | 环境差异化 |
| 6 | 模块配置文件 | `config/router/loadbalancer.yml` | 模块默认值 |
| 7 | 默认配置 | `application.yml` | 框架默认值 |

### 最佳实践

- **生产环境**：使用环境变量（优先级 3）配置敏感信息
- **Docker 部署**：使用外部配置文件（优先级 4）配置服务参数
- **开发环境**：使用环境特定配置（优先级 5）

---

## 模块化配置结构 {#modular-config-structure}

v2.8.x 配置目录结构：

```
src/main/resources/config/
├── auth/                    # 认证模块
│   ├── jwt.yml              # JWT 配置
│   ├── api-key.yml          # API Key 配置
│   ├── audit.yml            # 安全审计配置
│   └── sanitization.yml     # 数据脱敏配置
├── router/                  # 路由模块
│   ├── adapter.yml          # 适配器配置
│   ├── loadbalancer.yml     # 负载均衡配置
│   ├── ratelimit.yml        # 限流配置
│   ├── circuitbreaker.yml   # 熔断器配置
│   ├── fallback.yml         # 降级配置
│   └── services.yml         # 服务实例配置
├── common/                  # 公共模块
│   ├── server.yml           # 服务器配置
│   ├── webclient.yml        # WebClient 配置
│   └── logging.yml          # 日志配置
├── config-service/          # 配置服务模块
│   └── core.yml             # 配置服务核心
├── monitor/                 # 监控模块
│   ├── slow-query-alerts.yml    # 慢查询告警
│   ├── error-tracking.yml       # 错误追踪
│   └── persistence-monitoring-base.yml  # 持久化监控
├── persistence/             # 持久化模块
│   └── state-persistence-base.yml  # 状态持久化
├── tracing/                 # 追踪模块
│   └── tracing-base.yml     # 分布式追踪
└── base/                    # 基础配置（向后兼容）
    ├── model-services-base.yml   # 模型服务基础
    ├── monitoring-base.yml       # 监控基础
    ├── security-base.yml         # 安全基础
    └── persistence-base.yml      # 持久化基础
```

---

## 环境变量配置 {#env-config}

### 必须配置的环境变量

| 变量名 | 说明 | 最小长度 | 示例 |
|--------|------|---------|------|
| `JWT_SECRET` | JWT 密钥 | 32 字符 | `openssl rand -base64 32` |
| `INITIAL_ADMIN_PASSWORD` | 初始管理员密码 | 8 字符 | 首次启动后修改 |

### 可选环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `GPUSTACK_API_TOKEN` | GPUStack API Token | 无 |
| `JWT_EXPIRATION_MINUTES` | JWT 过期时间（分钟） | 60 |
| `JWT_REFRESH_EXPIRATION_DAYS` | 刷新令牌过期时间（天） | 7 |
| `REDIS_HOST` | Redis 主机 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `REDIS_PASSWORD` | Redis 密码 | 无 |
| `SERVER_PORT` | 服务器端口 | 8080 |

### 配置示例

```bash
# .env 文件示例
JWT_SECRET=your-very-secure-jwt-secret-key-at-least-32-characters-long
INITIAL_ADMIN_PASSWORD=change-me-after-first-login
GPUSTACK_API_TOKEN=your-gpustack-api-token
JWT_EXPIRATION_MINUTES=60
SERVER_PORT=8080
```

---

## 外部配置文件 {#external-config}

### Docker 部署配置

1. 创建外部配置目录：
```bash
mkdir -p config-external/auth config-external/router config-external/common
```

2. 配置 Docker Compose：
```yaml
volumes:
  - ./config-external:/app/config:ro
  - ./logs:/app/logs
  - ./data:/app/data
```

3. 外部配置示例（`config-external/router/services.yml`）：
```yaml
model:
  services:
    chat:
      instances:
        - name: "qwen3:4b"
          base-url: "http://172.16.30.6:9090"
          headers:
            Authorization: "Bearer ${GPUSTACK_API_TOKEN:}"
```

---

## 多环境配置 {#multi-env-config}

### 环境配置文件

- `application-dev.yml` - 开发环境（93 行，精简）
- `application-staging.yml` - 预发布环境（68 行，精简）
- `application-prod.yml` - 生产环境（110 行，精简）

### 激活环境

```bash
# 命令行
java -jar app.jar --spring.profiles.active=prod

# 环境变量
SPRING_PROFILES_ACTIVE=prod

# Docker Compose
environment:
  - SPRING_PROFILES_ACTIVE=prod
```

---

## 配置校验机制 {#config-validation}

### 启动时校验

应用启动时自动校验：

1. **敏感配置校验**
   - JWT_SECRET 是否存在
   - JWT_SECRET 长度 ≥32 字符
   - 是否为硬编码值

2. **完整性校验**
   - server.port 范围 1-65535
   - load-balance.type 合法值
   - circuit-breaker 阈值范围

### 校验 API

```bash
# 查看配置来源
curl http://localhost:8080/api/config/sources

# 查看校验规则
curl http://localhost:8080/api/config/validation-rules

# 查看环境变量指南
curl http://localhost:8080/api/config/environment-variables
```

---

## 配置API {#config-api}

| API | 说明 |
|-----|------|
| `/api/config/sources` | 配置加载来源信息 |
| `/api/config/validation-rules` | 配置校验规则定义 |
| `/api/config/environment-variables` | 环境变量指南 |

---

## 迁移指南 {#migration-guide}

### 从 v2.7.x 迁移到 v2.8.x

1. **配置文件结构变化**
   - 原 `application.yml` 已拆分为模块化配置
   - 原 `config/monitoring/` 迁移到 `config/monitor/`

2. **环境配置精简**
   - 环境配置文件大幅精简（-62%）
   - 基础配置由模块配置提供

3. **敏感配置处理**
   - 移除硬编码占位符（`your-api-token-here`）
   - 强制使用环境变量

4. **迁移步骤**
   ```bash
   # 1. 设置环境变量
   export JWT_SECRET="your-secret-key"
   export INITIAL_ADMIN_PASSWORD="your-password"
   
   # 2. 配置外部文件（可选）
   cp config-external/application.yml /app/config/
   
   # 3. 启动验证
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

---

## 相关文档

- [外部配置使用指南](https://github.com/Lincoln-cn/JAiRouter/tree/master/config-external/README.md) - GitHub 上的配置示例
- [环境变量模板](https://github.com/Lincoln-cn/JAiRouter/blob/master/.env.example) - GitHub 上的 .env.example
- [应用配置](zh/configuration/application-config.md) - 应用配置详解
- [动态配置](zh/configuration/dynamic-config.md) - 动态配置管理

---

*文档版本: v2.8.x*
*最后更新: 2026-05-18*
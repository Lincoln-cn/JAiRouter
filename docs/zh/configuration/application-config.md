# 应用配置

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

本文档详细介绍 JAiRouter 的基础应用配置，包括服务器配置、WebClient 配置、监控配置等。

## 配置文件位置

| 位置 | 描述 |
|------|------|
| `src/main/resources/application.yml` | 主配置文件 |
| `src/main/resources/config/{module}/*.yml` | 模块配置文件 |
| `/app/config/*.yml` | 外部配置（Docker 部署） |
| `application-{profile}.yml` | 环境特定配置 |

## 模块化配置结构 (v2.8.x)

JAiRouter 采用**模块化配置管理**方式，将复杂的配置按功能和服务模块拆分为多个独立的配置文件。

### 配置模块概览

```
config/
├── common/              # 公共基础设施
│   ├── server.yml       # 服务器配置
│   ├── webclient.yml    # WebClient 配置
│   └── logging.yml      # 日志配置
├── config-service/      # 配置服务
│   └── core.yml         # ConfigurationService 设置
├── router/              # 路由服务
│   ├── adapter.yml      # 适配器配置
│   ├── loadbalancer.yml # 负载均衡配置
│   ├── ratelimit.yml    # 限流配置
│   ├── circuitbreaker.yml # 熔断器配置
│   ├── fallback.yml     # 降级配置
│   └── services.yml     # 服务实例配置
├── auth/                # 认证服务
│   ├── jwt.yml          # JWT 认证配置
│   ├── api-key.yml      # API 密钥配置
│   ├── audit.yml        # 审计配置
│   └── sanitization.yml # 数据脱敏配置
├── monitor/             # 监控服务
│   ├── slow-query-alerts.yml
│   ├── error-tracking.yml
│   └── persistence-monitoring-base.yml
├── tracing/             # 链路追踪服务
│   └── tracing-base.yml
├── persistence/         # 持久化服务
│   └── state-persistence-base.yml
├── base/                # 基础配置（遗留）
│   ├── server-base.yml
│   └── model-services-base.yml
└── security/            # 安全配置（遗留）
    ├── security-base.yml
    ├── persistence-base.yml
    └── audit-base.yml
```

### 配置导入

主配置文件 `application.yml` 导入所有模块配置：

```yaml
spring:
  config:
    import:
      # 外部配置（最高优先级）
      - optional:file:/app/config/application.yml
      - optional:file:/app/config/auth/jwt.yml
      - optional:file:/app/config/router/services.yml

      # common 模块
      - classpath:config/common/server.yml
      - classpath:config/common/webclient.yml
      - classpath:config/common/logging.yml

      # config-service 模块
      - classpath:config/config-service/core.yml

      # router 模块
      - classpath:config/router/adapter.yml
      - classpath:config/router/loadbalancer.yml
      - classpath:config/router/ratelimit.yml
      - classpath:config/router/circuitbreaker.yml
      - classpath:config/router/fallback.yml
      - classpath:config/router/services.yml

      # auth 模块
      - classpath:config/auth/jwt.yml
      - classpath:config/auth/api-key.yml
      - classpath:config/auth/audit.yml
      - classpath:config/auth/sanitization.yml

      # monitor 模块
      - classpath:config/base/monitoring-base.yml
      - classpath:config/monitor/slow-query-alerts.yml
      - classpath:config/monitor/error-tracking.yml
      - classpath:config/monitor/persistence-monitoring-base.yml

      # tracing 模块
      - classpath:config/tracing/tracing-base.yml

      # persistence 模块
      - classpath:config/persistence/state-persistence-base.yml
```

### 配置优先级

配置加载遵循以下优先级顺序（高优先级覆盖低优先级）：

1. 命令行参数（最高优先级）
2. 环境变量
3. 外部配置文件（`/app/config/`）
4. 环境特定配置文件（`application-{profile}.yml`）
5. 模块配置文件（`config/{module}/*.yml`）
6. 基础配置模块（最低优先级）

---

## 服务器配置

### 文件：`config/common/server.yml`

```yaml
server:
  port: 8080                    # 服务端口
  compression:
    enabled: true               # 启用响应压缩
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # 启用 HTTP/2
```

### 高级服务器配置

```yaml
server:
  netty:
    connection-timeout: 45s     # Netty 连接超时
    h2c-max-content-length: 0   # H2C 最大内容长度
```

---

## WebClient 配置

### 文件：`config/common/webclient.yml`

```yaml
webclient:
  connect-timeout: 10000        # 连接超时（毫秒）
  read-timeout: 60000           # 读取超时（毫秒）
  write-timeout: 60000          # 写入超时（毫秒）
  max-in-memory-size: 10MB      # 内存缓冲区最大值
  pool:
    max-connections: 500        # 最大连接数
    acquire-timeout: 10000      # 获取连接超时（毫秒）
```

---

## 日志配置

### 文件：`config/common/logging.yml`

```yaml
logging:
  level:
    root: INFO
    org.unreal.modelrouter: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/jairouter.log
    max-size: 10MB
    max-history: 30
```

---

## 认证配置

### JWT 配置

#### 文件：`config/auth/jwt.yml`

```yaml
jairouter:
  security:
    jwt:
      enabled: false                    # 启用 JWT 认证
      jwt-header: "Jairouter_Token"     # 自定义 JWT 头
      secret: "${JWT_SECRET:}"          # JWT 密钥（使用环境变量）
      algorithm: "HS256"                # 签名算法
      expiration-minutes: 60            # 访问令牌过期时间
      refresh-expiration-days: 7        # 刷新令牌过期时间
      issuer: "jairouter"               # 令牌签发者
      blacklist-enabled: true           # 启用令牌黑名单
```

### API 密钥配置

#### 文件：`config/auth/api-key.yml`

```yaml
jairouter:
  security:
    api-key:
      enabled: true                     # 启用 API 密钥认证
      header-name: "X-API-Key"          # API 密钥头名称
      hash-algorithm: "SHA-256"         # 密钥哈希算法
```

---

## 路由配置

### 适配器配置

#### 文件：`config/router/adapter.yml`

```yaml
jairouter:
  adapter:
    default-adapter: "ollama"           # 默认适配器类型
    connect-timeout: 10000              # 连接超时（毫秒）
    read-timeout: 60000                 # 读取超时（毫秒）
```

### 负载均衡配置

#### 文件：`config/router/loadbalancer.yml`

```yaml
jairouter:
  loadbalancer:
    default-strategy: "random"          # 默认策略：random, round_robin, weighted
    health-check-interval: 30000        # 健康检查间隔（毫秒）
```

### 限流配置

#### 文件：`config/router/ratelimit.yml`

```yaml
jairouter:
  ratelimit:
    enabled: true
    default-algorithm: "token_bucket"   # token_bucket, leaky_bucket
    default-capacity: 100               # 默认令牌桶容量
    default-refill-rate: 10             # 每秒补充令牌数
```

### 熔断器配置

#### 文件：`config/router/circuitbreaker.yml`

```yaml
jairouter:
  circuitbreaker:
    enabled: true
    failure-threshold: 5                # 触发熔断的失败次数
    success-threshold: 3                # 恢复所需成功次数
    timeout: 30000                      # 熔断状态超时（毫秒）
```

---

## 监控配置

### 文件：`config/base/monitoring-base.yml`

```yaml
jairouter:
  monitoring:
    enabled: true
    prefix: "jairouter"                 # 指标前缀
    collection-interval: "PT30S"        # 采集间隔
    enabled-categories:
      - request
      - system
      - custom
```

---

## 链路追踪配置

### 文件：`config/tracing/tracing-base.yml`

```yaml
jairouter:
  tracing:
    enabled: false
    sampling-rate: 1.0                  # 采样率（0.0 - 1.0）
    export-enabled: false
    exporter-type: "otlp"               # otlp, jaeger, zipkin
    endpoint: "http://localhost:4317"
```

---

## 持久化配置

### 文件：`config/persistence/state-persistence-base.yml`

```yaml
jairouter:
  persistence:
    enabled: true
    primary-storage: "h2"               # h2, redis, memory
    fallback-storage: "memory"
    cleanup:
      enabled: true
      schedule: "0 */5 * * * ?"         # 每 5 分钟执行
      retention-days: 7
```

---

## 环境特定配置

### 开发环境（`application-dev.yml`）

```yaml
spring:
  config:
    activate:
      on-profile: dev

jairouter:
  security:
    jwt:
      enabled: false                    # 开发环境禁用 JWT
  monitoring:
    enabled: false                      # 开发环境禁用监控

logging:
  level:
    root: DEBUG
```

### 生产环境（`application-prod.yml`）

```yaml
spring:
  config:
    activate:
      on-profile: prod

jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"           # 必须通过环境变量设置
  monitoring:
    enabled: true

logging:
  level:
    root: INFO
```

---

## 外部配置（Docker）

使用 Docker 部署时，可以挂载外部配置文件：

```bash
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v /path/to/config:/app/config \
  -e JWT_SECRET="your-secret-key" \
  sodlinken/jairouter:latest
```

### 外部配置结构

```
/app/config/
├── application.yml      # 覆盖主配置
├── auth/
│   └── jwt.yml          # 覆盖 JWT 配置
└── router/
    └── services.yml     # 覆盖服务实例配置
```

---

## 环境变量

| 变量 | 描述 | 默认值 |
|------|------|--------|
| `JWT_SECRET` | JWT 签名密钥 | （生产环境必填） |
| `INITIAL_ADMIN_PASSWORD` | 初始管理员密码 | （生产环境必填） |
| `REDIS_HOST` | Redis 主机 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `REDIS_PASSWORD` | Redis 密码 | （空） |
| `SERVER_PORT` | 服务器端口 | 8080 |

---

## 配置最佳实践

1. **使用环境变量**存储敏感值（密钥、密码）
2. **使用外部配置**进行 Docker 部署
3. **使用环境特定配置**区分不同环境
4. **保持模块配置**专注和独立
5. **在部署指南中记录**自定义配置

---

*最后更新：2026-05-21*

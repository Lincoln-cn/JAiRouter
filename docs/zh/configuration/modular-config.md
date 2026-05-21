# 模块化配置指南

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 采用**模块化配置管理**方式，将复杂的配置按功能和服务模块拆分为多个独立的配置文件。这种设计提高了配置的可维护性、可读性和可重用性。

## 配置模块结构 (v2.8.x)

### 目录结构

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

### 模块分类

| 模块 | 目录 | 用途 |
|------|------|------|
| **Common** | `config/common/` | 共享基础设施（服务器、WebClient、日志） |
| **Config Service** | `config/config-service/` | 配置管理服务 |
| **Router** | `config/router/` | 路由、负载均衡、限流、熔断器 |
| **Auth** | `config/auth/` | 认证（JWT、API Key）、审计、脱敏 |
| **Monitor** | `config/monitor/` | 性能监控、错误追踪 |
| **Tracing** | `config/tracing/` | 分布式链路追踪 |
| **Persistence** | `config/persistence/` | 状态持久化 |

---

## 主配置导入

### application.yml

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

---

## 配置优先级

配置加载遵循以下优先级顺序（高优先级覆盖低优先级）：

1. 命令行参数（最高优先级）
2. 环境变量
3. 外部配置文件（`/app/config/`）
4. 环境特定配置文件（`application-{profile}.yml`）
5. 模块配置文件（`config/{module}/*.yml`）
6. 基础配置模块（最低优先级）

---

## 模块配置详解

### Common 模块

#### 服务器配置（`config/common/server.yml`）

```yaml
server:
  port: 8080                    # 服务端口
  compression:
    enabled: true               # 启用响应压缩
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # 启用 HTTP/2
```

#### WebClient 配置（`config/common/webclient.yml`）

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

#### 日志配置（`config/common/logging.yml`）

```yaml
logging:
  level:
    root: INFO
    org.unreal.modelrouter: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/jairouter.log
    max-size: 10MB
    max-history: 30
```

---

### Router 模块

#### 适配器配置（`config/router/adapter.yml`）

```yaml
jairouter:
  adapter:
    default-adapter: "ollama"           # 默认适配器类型
    connect-timeout: 10000              # 连接超时（毫秒）
    read-timeout: 60000                 # 读取超时（毫秒）
```

#### 负载均衡配置（`config/router/loadbalancer.yml`）

```yaml
jairouter:
  loadbalancer:
    default-strategy: "random"          # random, round_robin, weighted
    health-check-interval: 30000        # 健康检查间隔（毫秒）
```

#### 限流配置（`config/router/ratelimit.yml`）

```yaml
jairouter:
  ratelimit:
    enabled: true
    default-algorithm: "token_bucket"   # token_bucket, leaky_bucket
    default-capacity: 100               # 默认令牌桶容量
    default-refill-rate: 10             # 每秒补充令牌数
```

#### 熔断器配置（`config/router/circuitbreaker.yml`）

```yaml
jairouter:
  circuitbreaker:
    enabled: true
    failure-threshold: 5                # 触发熔断的失败次数
    success-threshold: 3                # 恢复所需成功次数
    timeout: 30000                      # 熔断状态超时（毫秒）
```

---

### Auth 模块

#### JWT 配置（`config/auth/jwt.yml`）

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

#### API 密钥配置（`config/auth/api-key.yml`）

```yaml
jairouter:
  security:
    api-key:
      enabled: true                     # 启用 API 密钥认证
      header-name: "X-API-Key"          # API 密钥头名称
      hash-algorithm: "SHA-256"         # 密钥哈希算法
```

#### 审计配置（`config/auth/audit.yml`）

```yaml
jairouter:
  security:
    audit:
      enabled: true                     # 启用审计日志
      include-request-body: true        # 记录请求体
      include-response-body: false      # 记录响应体
      max-body-length: 1024             # 最大记录长度
```

#### 数据脱敏配置（`config/auth/sanitization.yml`）

```yaml
jairouter:
  security:
    sanitization:
      enabled: true                     # 启用数据脱敏
      mask-sensitive-fields:            # 需要脱敏的字段
        - "password"
        - "secret"
        - "token"
        - "api-key"
```

---

### Monitor 模块

#### 慢查询告警（`config/monitor/slow-query-alerts.yml`）

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        enabled: true
        min-interval-ms: 300000         # 告警冷却时间
```

#### 错误追踪（`config/monitor/error-tracking.yml`）

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: false                    # 默认关闭
      aggregation-window-minutes: 5     # 聚合窗口
      sanitization:
        enabled: true
```

---

### Tracing 模块

#### 链路追踪配置（`config/tracing/tracing-base.yml`）

```yaml
jairouter:
  tracing:
    enabled: false
    service-name: "jairouter"
    sampling-rate: 1.0                  # 采样率（0.0 - 1.0）
    export-enabled: false
    exporter-type: "otlp"               # otlp, jaeger, zipkin
    endpoint: "http://localhost:4317"
```

---

### Persistence 模块

#### 状态持久化（`config/persistence/state-persistence-base.yml`）

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

logging:
  level:
    root: DEBUG

jairouter:
  security:
    jwt:
      enabled: false                    # 开发环境禁用 JWT
  monitoring:
    enabled: false                      # 开发环境禁用监控
```

### 生产环境（`application-prod.yml`）

```yaml
spring:
  config:
    activate:
      on-profile: prod

logging:
  level:
    root: INFO

jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"           # 必须通过环境变量设置
  monitoring:
    enabled: true
```

---

## 外部配置（Docker）

挂载外部配置文件覆盖默认值：

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

## 配置最佳实践

1. **使用环境变量**存储敏感值（密钥、密码）
2. **使用外部配置**进行 Docker 部署
3. **使用环境特定配置**区分不同环境
4. **保持模块配置**专注和独立
5. **在部署指南中记录**自定义配置

---

## 故障排除

### 配置未生效

1. 检查配置文件路径
2. 确认环境配置文件是否加载
3. 验证配置优先级顺序
4. 检查是否有语法错误

### 配置冲突

1. 理解配置优先级规则
2. 检查是否有重复配置项
3. 确认环境变量是否覆盖了预期配置
4. 使用 `--debug` 参数查看配置加载过程

---

*最后更新：2026-05-21*

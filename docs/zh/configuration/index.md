# 配置指南

<!-- 版本信息 -->
> **文档版本**: 1.0.2  
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 提供灵活的配置选项以满足各种部署场景。本指南涵盖从基本设置到高级功能的所有配置方面。

## 模块化配置说明

从 v1.0.0 版本开始，JAiRouter 采用模块化配置结构，将不同功能的配置分离到独立的配置文件中，以提高可维护性和可读性。

### 配置文件结构

```
src/main/resources/
├── application.yml                    # 主配置：只保留 spring.config.import 清单
├── config/
│   ├── common/                        # server / webclient / logging / scheduling / cluster
│   ├── config-service/core.yml        # 配置服务（动态配置后端）
│   ├── router/                        # adapter / loadbalancer / ratelimit / circuitbreaker /
│   │                                  # fallback / services / rules / pools
│   ├── auth/                          # jwt / api-key / audit / sanitization
│   ├── monitor/                       # slow-query-alerts / error-tracking /
│   │                                  # persistence-monitoring-base
│   ├── tracing/tracing-base.yml       # 链路追踪
│   ├── persistence/state-persistence-base.yml   # 状态持久化
│   ├── base/                          # server-base / model-services-base / monitoring-base
│   └── security/                      # security-base / persistence-base
├── application-dev.yml                # 开发环境
├── application-staging.yml            # 预发布环境
├── application-prod.yml               # 生产环境
├── application-migrate.yml            # 数据迁移 profile
└── application-legacy.yml             # 向后兼容环境
```

### 配置导入机制

主配置文件 `application.yml` 通过 `spring.config.import` 机制导入各个模块配置：

```yaml
# application.yml —— 以此为准（当前 28 项）
spring:
  config:
    import:
      - classpath:config/common/server.yml
      - classpath:config/common/webclient.yml
      - classpath:config/common/logging.yml
      - classpath:config/common/scheduling.yml
      - classpath:config/common/cluster.yml
      - classpath:config/config-service/core.yml
      - classpath:config/router/adapter.yml
      - classpath:config/router/loadbalancer.yml
      - classpath:config/router/ratelimit.yml
      - classpath:config/router/circuitbreaker.yml
      - classpath:config/router/fallback.yml
      - classpath:config/router/services.yml
      - classpath:config/router/rules.yml
      - classpath:config/router/pools.yml
      - classpath:config/auth/jwt.yml
      - classpath:config/auth/api-key.yml
      - classpath:config/auth/audit.yml
      - classpath:config/auth/sanitization.yml
      - classpath:config/base/monitoring-base.yml
      - classpath:config/monitor/slow-query-alerts.yml
      - classpath:config/monitor/error-tracking.yml
      - classpath:config/monitor/persistence-monitoring-base.yml
      - classpath:config/tracing/tracing-base.yml
      - classpath:config/persistence/state-persistence-base.yml
      - classpath:config/base/server-base.yml
      - classpath:config/base/model-services-base.yml
      - classpath:config/security/security-base.yml
      - classpath:config/security/persistence-base.yml
```

> 注：上面的目录树与清单是**按仓库实际内容核对过的**，不是示意。两者若发生漂移，
> 以 `src/main/resources/application.yml` 为准。

这种方式使得配置更加清晰，便于维护和扩展。

## 配置概览

JAiRouter 支持两种主要配置方式：

1. **静态配置**: 在 YAML 文件中定义，启动时加载
2. **动态配置**: 通过 REST API 在运行时更新

## 配置层次结构

配置按以下顺序加载（后面的源会覆盖前面的）：

1. 默认配置（嵌入在 JAR 中）
2. `application.yml`（classpath）
3. `./application.yml`（当前目录）
4. `./config/application.yml`（config 目录）
5. 环境变量
6. 命令行参数
7. 动态配置（运行时更新）

## 基本结构

```
server:
  port: 8080

model:
  services:
    <服务类型>:
      load-balance:
        type: <策略>
      rate-limit:
        algorithm: <算法>
        # ... 限流设置
      circuit-breaker:
        enabled: true
        # ... 熔断器设置
      fallback:
        strategy: <回退类型>
        # ... 回退设置
      instances:
        - name: <模型名称>
          baseUrl: <服务URL>
          path: <API路径>
          weight: <负载均衡权重>
          # ... 实例特定设置

store:
  type: <存储后端>
  # ... 存储设置
```

## 服务类型

JAiRouter 支持以下服务类型：

| 服务类型 | 描述 | 默认路径 |
|----------|------|----------|
| `chat` | 聊天对话 | `/v1/chat/completions` |
| `embedding` | 文本嵌入 | `/v1/embeddings` |
| `rerank` | 文本重排 | `/v1/rerank` |
| `tts` | 语音合成 | `/v1/audio/speech` |
| `stt` | 语音识别 | `/v1/audio/transcriptions` |
| `imgGen` | 图像生成 | `/v1/images/generations` |
| `imgEdit` | 图像编辑 | `/v1/images/edits` |

## 配置部分

### 1. 负载均衡

配置请求如何在服务实例之间分发：

```
model:
  services:
    chat:
      load-balance:
        type: round-robin  # random, round-robin, least-connections, ip-hash
```

**可用策略：**

- **random**: 随机选择实例
- **round-robin**: 按顺序轮询实例
- **least-connections**: 路由到活跃连接最少的实例
- **ip-hash**: 基于客户端 IP 哈希的一致性路由

### 2. 限流

控制请求速率以防止服务过载：

```
model:
  services:
    chat:
      rate-limit:
        algorithm: token-bucket
        capacity: 100          # 桶中最大令牌数
        rate: 10               # 每秒添加的令牌数
        client-ip-enable: true # 启用基于客户端IP的限流
```

**可用算法：**

- **token-bucket**: 允许突发流量直到桶容量
- **leaky-bucket**: 平滑的恒定速率限流
- **sliding-window**: 基于时间窗口的限流
- **warm-up**: 逐渐增加限流速率

### 3. 熔断器

使用熔断器模式防止级联故障：

```
model:
  services:
    chat:
      circuit-breaker:
        enabled: true
        failure-threshold: 5      # 打开熔断器前的失败次数
        recovery-timeout: 30000   # 尝试恢复前的时间（毫秒）
        success-threshold: 3      # 关闭熔断器需要的成功次数
```

### 4. 回退策略

定义服务不可用时的回退行为：

```
model:
  services:
    chat:
      fallback:
        strategy: default
        message: "服务暂时不可用"
        # 或者
        strategy: cache
        cache-ttl: 300000  # 缓存TTL（毫秒）
```

**可用类型：**

- **default**: 返回预定义消息
- **cache**: 返回缓存的响应
- **none**: 无回退（返回错误）

### 5. 服务实例

定义实际的服务端点：

```
model:
  services:
    chat:
      instances:
        - name: "qwen2.5:7b"
          baseUrl: "http://server1:11434"
          path: "/v1/chat/completions"
          weight: 2
          timeout: 30000
          headers:
            Authorization: "Bearer token"
            Custom-Header: "value"
        - name: "qwen2.5:14b"
          baseUrl: "http://server2:11434"
          path: "/v1/chat/completions"
          weight: 1
```

**实例属性：**

- `name`: 模型名称标识符
- `baseUrl`: 服务基础URL
- `path`: API端点路径
- `weight`: 负载均衡权重（越高获得越多流量）
- `timeout`: 请求超时时间（毫秒）
- `headers`: 请求中包含的自定义头部

### 6. 存储配置

配置动态配置的持久化方式：

```
store:
  type: file              # memory 或 file
  path: ./config          # 文件存储目录
  auto-backup: true       # 启用自动备份
  backup-interval: 3600   # 备份间隔（秒）
```

**存储类型：**

- **memory**: 内存存储（重启后丢失）
- **file**: 基于文件的存储（重启后保持）

## 环境特定配置

JAiRouter 支持多种环境配置文件：

- **开发环境**: `application-dev.yml`
- **预发布环境**: `application-staging.yml`
- **生产环境**: `application-prod.yml`
- **兼容模式**: `application-legacy.yml`
- **安全示例**: `application-security-example.yml`

环境配置文件只包含与基础配置的差异部分，遵循 Spring Boot 的配置覆盖机制。

### 各 profile 的必填环境变量

| Profile | 必填环境变量 | 缺失时的行为 |
| --- | --- | --- |
| `dev` / `default` | 无 | JWT 密钥与管理员口令回退为随机值，并打印在启动日志里（重启即换新值） |
| `staging` | `JWT_SECRET` | `jairouter.security.jwt.secret` 解析为空串，`@Size(min = 32)` 绑定校验失败，进程以退出码 1 结束 |
| `prod` | `JWT_SECRET` + `INITIAL_ADMIN_PASSWORD` | 启动门闩（`StartupSecurityGate`）直接拒绝启动 |

所有 profile 都只读 `JWT_SECRET` 与（管理员口令）`INITIAL_ADMIN_PASSWORD`。曾经的三个 profile 别名
`DEV_JWT_SECRET` / `STAGING_JWT_SECRET` / `PROD_JWT_SECRET` 以及备用名 `ADMIN_PASSWORD` 已移除（#674）：
它们与配置绑定读的不是同一处，同时设置会让「检查的凭据」与「实际生效的凭据」分叉。仍然设置这些旧名字时，
启动日志会给出明确的 warn。

完整清单（含存储、Redis、上游凭据与多副本开关）见[环境变量参考](../deployment/environment-variables.md)。

## 环境变量

使用环境变量覆盖配置，例如：

```bash
# 服务器端口（Spring 宽松绑定到 server.port）
export SERVER_PORT=8080

# 关闭 JWT 认证（等价于 jairouter.security.jwt.enabled=false）
export JAiROUTER_SECURITY_JWT_ENABLED=false

# 指向外部 Redis（等价于 spring.data.redis.host / port）
export SPRING_DATA_REDIS_HOST=redis.internal
export SPRING_DATA_REDIS_PORT=6379
```

变量名与配置键的对应关系、哪些变量必须设置、各 profile 的默认差异，见
[环境变量参考](../deployment/environment-variables.md)。

> 旧版本文档此处示例的 `MODEL_LOAD_BALANCE_TYPE` / `MODEL_RATE_LIMIT_TYPE` 在配置树中不存在，
> 设置它们不会有任何效果。

## 命令行参数

通过命令行参数覆盖配置：

```
java -jar jairouter.jar \
  --server.port=8080 \
  --jairouter.security.audit.log-level=DEBUG \
  --spring.profiles.active=prod
```

## 配置验证

JAiRouter 在启动时验证配置。常见验证错误：

### 缺少必需字段

```
# ❌ 无效 - 缺少 baseUrl
model:
  services:
    chat:
      instances:
        - name: "model"
          path: "/v1/chat/completions"

# ✅ 有效
model:
  services:
    chat:
      instances:
        - name: "model"
          baseUrl: "http://localhost:11434"
          path: "/v1/chat/completions"
```

### 无效配置值

```
# ❌ 无效 - 不支持的负载均衡类型
model:
  services:
    chat:
      load-balance:
        type: invalid-type

# ✅ 有效
model:
  services:
    chat:
      load-balance:
        type: round-robin
```

## 配置最佳实践

### 1. 使用有意义的名称

```
# ✅ 好 - 描述性名称
model:
  services:
    chat:
      instances:
        - name: "qwen2.5-7b-fast"
          baseUrl: "http://fast-gpu-server:11434"
        - name: "qwen2.5-14b-accurate"
          baseUrl: "http://high-memory-server:11434"
```

### 2. 设置适当的超时

```
# ✅ 好 - 合理的超时
model:
  services:
    chat:
      instances:
        - name: "model"
          baseUrl: "http://server:11434"
          timeout: 30000  # 聊天30秒
    embedding:
      instances:
        - name: "embedding"
          baseUrl: "http://server:11434"
          timeout: 10000  # 嵌入10秒
```

### 3. 配置健康检查

```
# ✅ 好 - 显式声明探活方式与节奏（默认值见 config/router/health-check.yml）
jairouter:
  health-check:
    mode: http          # tcp 只探端口；http 还会校验状态码（<500 算健康）
    interval-ms: 30000  # 上一轮结束后再等这么久才跑下一轮
    timeout-ms: 5000    # 单实例探活超时
    parallelism: 4      # 同时检查几个服务类型
```

> 早期文档里的 `model.load-balance.health-check.*`（enabled / interval / timeout / failure-threshold /
> success-threshold）从未被绑定过，设了也不生效；v3.5.4 起健康检查统一由 `jairouter.health-check.*` 控制。

### 4. 使用权重进行渐进式部署

```
# ✅ 好 - 使用权重渐进式部署
model:
  services:
    chat:
      instances:
        - name: "stable-model-v1"
          baseUrl: "http://stable-server:11434"
          weight: 9  # 90% 流量
        - name: "new-model-v2"
          baseUrl: "http://new-server:11434"
          weight: 1  # 10% 流量
```

## 下一步

- [应用配置](application-config.md) - 详细的应用设置
- [动态配置](dynamic-config.md) - 运行时配置管理
- [负载均衡](load-balancing.md) - 负载均衡策略
- [限流配置](rate-limiting.md) - 限流算法
- [熔断器](circuit-breaker.md) - 熔断器配置
- [存储配置](store-config.md) - 存储和自动合并配置
- [版本管理](version-management.md) - 配置版本管理：应用 vs 回滚
- [KV缓存](kv-cache.md) - 前缀缓存友好网关：粘性路由与缓存指标（v2.9.0）
- [响应缓存](response-cache.md) - 完整响应缓存：确定性请求命中直接复用响应、跳过下游；P1 增流式 SSE 缓存 + 失效 API + 限流短路（v2.9.10）

# 配置指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-28
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 提供灵活的配置选项以满足各种部署场景。本指南涵盖从基本设置到高级功能的所有配置方面。

## 模块化配置说明

从 v1.0.0 版本开始，JAiRouter 采用模块化配置结构，将不同功能的配置分离到独立的配置文件中，以提高可维护性和可读性。

### 配置文件结构

```
src/main/resources/
├── application.yml                    # 主配置文件，导入其他模块
├── config/
│   ├── base/
│   │   ├── server-base.yml           # 服务器基础配置
│   │   ├── model-services-base.yml    # 模型服务配置
│   │   └── monitoring-base.yml       # 监控基础配置
│   ├── security/
│   │   └── security-base.yml         # 安全功能配置
│   ├── tracing/
│   │   └── tracing-base.yml          # 追踪功能配置
│   └── monitoring/
│       ├── slow-query-alerts.yml     # 慢查询告警配置
│       └── error-tracking.yml        # 错误追踪配置
├── application-dev.yml               # 开发环境配置
├── application-staging.yml           # 预发布环境配置
├── application-prod.yml              # 生产环境配置
├── application-legacy.yml            # 向后兼容配置
└── application-security-example.yml  # 安全配置示例
```

### 配置导入机制

主配置文件 [application.yml](file://D:/IdeaProjects/model-router/src/main/resources/application.yml) 通过 `spring.config.import` 机制导入各个模块配置：

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/base/server-base.yml
      - classpath:config/base/model-services-base.yml
      - classpath:config/base/monitoring-base.yml
      - classpath:config/tracing/tracing-base.yml
      - classpath:config/security/security-base.yml
      - classpath:config/monitoring/slow-query-alerts.yml
      - classpath:config/monitoring/error-tracking.yml
```

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

```yaml
server:
  port: 8080

model:
  services:
    <服务类型>:
      load-balance:
        type: <策略>
      rate-limit:
        type: <算法>
        # ... 限流设置
      circuit-breaker:
        enabled: true
        # ... 熔断器设置
      fallback:
        type: <回退类型>
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

```yaml
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

```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100          # 桶中最大令牌数
        refill-rate: 10        # 每秒添加的令牌数
        client-ip-enable: true # 启用基于客户端IP的限流
```

**可用算法：**

- **token-bucket**: 允许突发流量直到桶容量
- **leaky-bucket**: 平滑的恒定速率限流
- **sliding-window**: 基于时间窗口的限流
- **warm-up**: 逐渐增加限流速率

### 3. 熔断器

使用熔断器模式防止级联故障：

```yaml
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

```yaml
model:
  services:
    chat:
      fallback:
        type: default
        message: "服务暂时不可用"
        # 或者
        type: cache
        ttl: 300000  # 缓存TTL（毫秒）
```

**可用类型：**

- **default**: 返回预定义消息
- **cache**: 返回缓存的响应
- **none**: 无回退（返回错误）

### 5. 服务实例

定义实际的服务端点：

```yaml
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

```yaml
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

- **开发环境**: [application-dev.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-dev.yml)
- **预发布环境**: [application-staging.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-staging.yml)
- **生产环境**: [application-prod.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-prod.yml)
- **兼容模式**: [application-legacy.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-legacy.yml)
- **安全示例**: [application-security-example.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-security-example.yml)

环境配置文件只包含与基础配置的差异部分，遵循 Spring Boot 的配置覆盖机制。

## 环境变量

使用环境变量覆盖配置：

```bash
# 服务器端口
export SERVER_PORT=8080

# 模型服务配置
export MODEL_LOAD_BALANCE_TYPE=round-robin
export MODEL_RATE_LIMIT_TYPE=token-bucket
```

## 命令行参数

通过命令行参数覆盖配置：

```bash
java -jar jairouter.jar \
  --server.port=8080 \
  --model.load-balance.type=round-robin \
  --spring.profiles.active=prod
```

## 配置验证

JAiRouter 在启动时验证配置。常见验证错误：

### 缺少必需字段

```yaml
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

```yaml
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

```yaml
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

```yaml
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

```yaml
# ✅ 好 - 启用健康监控
model:
  load-balance:
    health-check:
      enabled: true
      interval: 30000
      timeout: 5000
```

### 4. 使用权重进行渐进式部署

```yaml
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
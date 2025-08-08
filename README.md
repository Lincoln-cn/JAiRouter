# JAiRouter

JAiRouter 是一个基于 Spring Boot 的模型服务路由和负载均衡网关，用于统一管理和路由各种 AI 模型服务（如 Chat、Embedding、Rerank、TTS 等），支持多种负载均衡策略、限流、熔断、健康检查、动态配置更新等功能。

[English Introduction](README-EN.md)

---

## ✨ 核心特性

| 特性类别 | 支持内容 |
|----------|----------|
| **统一 API 网关** | 支持 OpenAI 兼容格式，统一 `/v1/*` 接口 |
| **服务类型** | Chat、Embedding、Rerank、TTS、STT、Image Generation、Image Editing |
| **负载均衡策略** | Random、Round Robin、Least Connections、IP Hash |
| **限流算法** | Token Bucket、Leaky Bucket、Sliding Window、Warm Up |
| **熔断机制** | 支持失败阈值、恢复检测、降级策略 |
| **健康检查** | 每服务独立状态接口，支持自动剔除不可用实例 |
| **适配器支持** | GPUStack、Ollama、VLLM、Xinference、LocalAI、OpenAI |
| **动态配置更新** | 支持运行时更新服务实例、权重、限流、熔断等配置 |
| **配置持久化** | 支持内存存储和文件存储两种后端 |
| **测试覆盖** | 包含负载均衡、限流、熔断、控制器等单元测试 |

---

## 🧱 项目结构

```
src/main/java/org/unreal/modelrouter
├── adapter              # 适配器模块：统一不同后端服务的调用方式
│   ├── impl             # 各适配器实现：GpuStackAdapter、OllamaAdapter 等
├── checker              # 健康检查模块：服务状态监控与剔除
├── circuitbreaker       # 熔断器模块：失败保护机制
├── config               # 配置模块：加载、合并、动态更新配置
├── controller           # Web 控制器：统一请求入口与状态接口
├── dto                  # 请求/响应数据结构定义
├── exception            # 全局异常处理
├── factory              # 组件工厂：动态创建负载均衡器、限流器等
├── fallback             # 降级策略：默认响应、缓存等
├── loadbalancer         # 负载均衡模块：四种策略实现
├── model                # 配置模型与注册中心
├── ratelimit            # 限流模块：多种算法实现
├── store                # 配置存储模块：内存与文件持久化支持
├── util                 # 工具类：IP 获取、网络工具等
└── ModelRouterApplication.java  # 启动类

src/main/resources
├── application.yml      # 主配置文件
└── logback.xml          # 日志配置

src/test/java/org/unreal/moduler
├── CircuitBreakerTest.java
├── LoadBalancerTest.java
├── ModelManagerControllerTest.java
├── ModelServiceRegistryTest.java
├── RateLimiterTest.java
├── UniversalControllerTest.java
```

---

## 🧪 测试模块说明

| 测试类 | 功能覆盖 |
|--------|----------|
| `CircuitBreakerTest` | 熔断器状态切换、失败恢复、降级策略测试 |
| `LoadBalancerTest` | 各负载均衡策略（随机、轮询、最少连接、IP Hash）行为验证 |
| `ModelManagerControllerTest` | 动态配置更新接口测试 |
| `ModelServiceRegistryTest` | 服务注册、实例选择、权重生效测试 |
| `RateLimiterTest` | 限流算法正确性、并发限流行为测试 |
| `UniversalControllerTest` | 各服务接口转发、响应格式验证 |

---

## ⚙️ 配置说明

JAiRouter 支持两种配置方式：

- **静态配置**：通过 `application.yml` 文件定义服务、实例、限流、熔断等参数；
- **动态配置**：通过 REST API 在运行时动态增删改服务实例，无需重启服务。

---

### ✅ 方式一：配置文件 `application.yml`

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `model.services.<type>` | 定义某类服务的全局行为 | `chat`, `embedding`, `tts` 等 |
| `instances` | 每个服务下的模型实例列表 | 支持权重、路径、限流等 |
| `load-balance.type` | 负载均衡策略 | `random`, `round-robin`, `least-connections`, `ip-hash` |
| `rate-limit` | 限流配置 | 支持 `token-bucket`, `leaky-bucket`, `sliding-window` |
| `client-ip-enable` | 是否启用基于客户端 IP 的独立限流 | `true`/`false` |
| `circuit-breaker` | 熔断配置 | 失败阈值、恢复时间、成功阈值 |
| `fallback` | 降级策略 | 支持 `default`, `cache` |
| `store.type` | 配置持久化方式 | `memory` 或 `file` |
| `store.path` | 文件存储路径（仅在 `type=file` 时生效） | `config/` |

> 📌 示例详见 [application.yml 示例](./src/main/resources/application.yml)

---

✅ 已根据最新的 `ServiceInstanceController.java` 接口路径，更新 **动态配置接口文档** 如下：

---

### ✅ 方式二：动态配置接口

> 接口前缀统一为：`/api/config/instance`

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取实例列表 | `GET` | `/api/config/instance/type/{serviceType}` | 获取指定服务下的所有实例 |
| 获取单个实例详情 | `GET` | `/api/config/instance/info/{serviceType}` | 需带 `modelName` 和 `baseUrl` 参数 |
| 添加实例 | `POST` | `/api/config/instance/add/{serviceType}` | 添加一个模型实例 |
| 更新实例 | `PUT` | `/api/config/instance/update/{serviceType}` | 需传 `UpdateInstanceDTO`，包含 `instanceId` |
| 删除实例 | `DELETE` | `/api/config/instance/del/{serviceType}` | 需带 `modelName` 和 `baseUrl` 参数 |

---

#### ✅ 示例接口调用

##### 1. 获取实例列表
```http
GET /api/config/instance/type/chat
```

##### 2. 获取单个实例详情
```http
GET /api/config/instance/info/chat?modelName=qwen3:1.7B&baseUrl=http://172.16.30.6:9090
```

##### 3. 添加实例
```http
POST /api/config/instance/add/chat
Content-Type: application/json

{
  "name": "qwen3:7B",
  "baseUrl": "http://172.16.30.7:9090",
  "path": "/v1/chat/completions",
  "weight": 2
}
```

##### 4. 更新实例
```http
PUT /api/config/instance/update/chat
Content-Type: application/json

{
  "instanceId": "qwen3:7B@http://172.16.30.7:9090",
  "instance": {
    "name": "qwen3:7B",
    "baseUrl": "http://172.16.30.8:9090",
    "path": "/v1/chat/completions",
    "weight": 3
  }
}
```

##### 5. 删除实例
```http
DELETE /api/config/instance/del/chat?modelName=qwen3:7B&baseUrl=http://172.16.30.8:9090
```

---

如需集成前端控制台或自动化脚本，可直接使用以上接口进行服务实例的热更新。
---

### ✅ 配置优先级说明

| 优先级 | 来源 | 是否支持热更新 |
|--------|------|----------------|
| 高 | 动态接口配置 | ✅ |
| 低 | `application.yml` | ❌（需重启） |

> 🔁 当动态配置与静态配置冲突时，**以动态配置为准**，并会持久化到本地文件（如配置了 `store.type=file`）。



- **配置持久化**：支持内存和文件两种后端，通过 `store.type=memory|file` 配置。

---

## 🧩 模块职责补充说明

| 模块 | 职责说明 |
|------|----------|
| `adapter` | 将不同后端（如 Ollama、VLLM）统一封装为 OpenAI 格式调用 |
| `checker` | 定期检测服务健康状态，自动剔除不可用实例 |
| `circuitbreaker` | 防止服务雪崩，支持失败阈值、恢复检测、降级策略 |
| `config` | 加载 YAML 配置，支持运行时热更新 |
| `fallback` | 当服务熔断或限流时，提供默认响应或缓存响应 |
| `store` | 配置持久化抽象，支持内存与本地文件两种实现 |
| `util` | 提供 IP 获取、URL 构造、请求转发等通用工具 |

---

## 📦 依赖版本

- **JDK**：17+
- **Spring Boot**：3.5.x
- **Spring WebFlux**：响应式 Web 框架
- **Reactor Core**：响应式编程支持

---

## 🚀 启动与部署

```bash
# 编译
./mvnw clean package

# 运行
java -jar target/model-router-*.jar

# 配置文件路径
java -jar target/model-router-*.jar --spring.config.location=classpath:/application.yml
```

---

## 📌 开发计划（更新状态）

| 阶段 | 状态 | 内容 |
|------|------|------|
| 0.1.0 | ✅ | 基础网关、适配器、负载均衡、健康检查 |
| 0.2.0 | ✅ | 限流、熔断、降级、配置持久化、动态更新接口 |
| 0.3.0 | 🚧 | 监控指标、Prometheus 集成、告警通知 |
| 0.4.0 | 📋 | 多租户支持、认证鉴权、日志追踪 |

---

如需进一步扩展，请查看 [DeepWiki 文档](https://deepwiki.com/Lincoln-cn/JAiRouter) 或提交 Issue 参与共建。

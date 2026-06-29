# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter - AI 模型网关" width="380">
</p>

<p align="center">
  <strong>生产级 AI 模型网关</strong>
</p>

<p align="center">
  支持 OpenAI 兼容 API，为 Ollama、vLLM、GPUStack、Xinference 等<br>
  提供统一路由、负载均衡和故障转移能力
</p>

<p align="center">
  <a href="https://github.com/Lincoln-cn/JAiRouter/stargazers">
    <img src="https://img.shields.io/github/stars/Lincoln-cn/JAiRouter?style=flat-square&logo=github" alt="GitHub stars">
  </a>
  <a href="https://hub.docker.com/r/sodlinken/jairouter">
    <img src="https://img.shields.io/docker/pulls/sodlinken/jairouter?style=flat-square&logo=docker" alt="Docker Pulls">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/Lincoln-cn/JAiRouter?style=flat-square" alt="License">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/releases">
    <img src="https://img.shields.io/github/v/release/Lincoln-cn/JAiRouter?style=flat-square" alt="Release">
  </a>
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="https://jairouter.com">文档</a> •
  <a href="https://jairouter.com/zh/">中文文档</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">讨论</a>
</p>

---

## 快速开始

```bash
# 使用 Docker 启动（无需配置）
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# 打开 Web 控制台：http://localhost:8080/admin
# 默认账号：admin / ChangeMeOnFirstStartup123456
```

---

## JAiRouter 是什么？

JAiRouter 是一个 **生产级 AI 模型网关**，提供统一的 OpenAI 兼容 API，用于管理多个 LLM 后端。它内置负载均衡、限流、熔断和故障转移功能——让您专注于构建应用，而非管理基础设施。

### 核心优势

| 问题 | JAiRouter 解决方案 |
|------|-------------------|
| 多个模型端点需要管理 | 统一的 API 入口 |
| 服务故障时手动切换 | 自动熔断故障转移 |
| 每个服务单独实现认证 | 内置 JWT + API Key |
| 分散的日志和监控指标 | 统一可观测性 |
| 修改配置需重启服务 | Web 控制台热更新 |

### 核心功能

- **🔌 OpenAI 兼容 API** — 直接替换 OpenAI SDK、LangChain、LlamaIndex
- **⚖️ 智能负载均衡** — 轮询、加权、最少连接、IP Hash、一致性哈希
- **🛡️ 流量控制** — 令牌桶、漏桶、滑动窗口算法
- **🔥 熔断降级** — 自动故障转移，可配置阈值和恢复策略
- **🔐 双认证体系** — JWT + API Key 双重认证，审计日志
- **📊 可观测性** — Prometheus 指标、OpenTelemetry 追踪、实时仪表盘
- **💾 状态持久化** — Redis / H2 / 文件存储，支持分布式部署
- **🎛️ Web 控制台** — 可视化管理、版本控制、配置回滚

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                           您的应用                               │
│                 (OpenAI SDK / LangChain / LlamaIndex)           │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼ OpenAI 兼容 API
┌─────────────────────────────────────────────────────────────────┐
│                        JAiRouter 网关                            │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  路由   │ │负载均衡 │ │  限流   │ │  熔断   │ │  认证   │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  可观测性与持久化                         │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
       ┌──────────────┬───────────┼───────────┬──────────────┐
       ▼              ▼           ▼           ▼              ▼
   ┌───────┐     ┌───────┐   ┌───────┐   ┌──────────┐   ┌───────┐
   │Ollama │     │ vLLM  │   │GPUStack│  │Xinference│   │ OpenAI │
   └───────┘     └───────┘   └───────┘   └──────────┘   └───────┘
```

---

## 支持的 AI 后端

| 后端 | Chat | Embedding | Rerank | TTS | STT | Image | 说明 |
|------|:----:|:---------:|:------:|:---:|:---:|:-----:|------|
| **Ollama** | ✅ | ✅ | - | - | - | - | 本地推理 |
| **vLLM** | ✅ | ✅ | - | - | - | - | 高吞吐量 |
| **GPUStack** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 功能完整 |
| **Xinference** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 多模型支持 |
| **LocalAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ | OpenAI 兼容 |
| **OpenAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ | 云端兜底 |

---

## 使用示例

### Python + OpenAI SDK

```python
from openai import OpenAI

# 指向 JAiRouter 而非 OpenAI
client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # JAiRouter 处理认证
)

# 使用任意配置的后端模型
response = client.chat.completions.create(
    model="llama3.2",  # 自动路由到 Ollama、vLLM 或 GPUStack
    messages=[{"role": "user", "content": "你好！"}]
)
print(response.choices[0].message.content)
```

### 添加模型后端

```bash
# 通过 API 添加 Ollama 实例
curl -X POST http://localhost:8080/api/config/instance/add/chat \
  -H "Content-Type: application/json" \
  -H "Jairouter_token: your-jwt-token" \
  -d '{
    "name": "llama3.2",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "weight": 1
  }'
```

---

## 为什么选择 JAiRouter？

### vs Nginx
Nginx 是通用 Web 服务器。JAiRouter **专为 AI/LLM 工作负载设计**，提供 OpenAI 兼容路由、熔断和模型感知的负载均衡。

### vs One-API
One-API 专注于 API Key 管理和计费。JAiRouter 专注于**本地模型网关**，提供高级弹性模式和可观测性。

### vs LangChain
LangChain 是应用框架。JAiRouter 是**基础设施层**，位于 LangChain 之下，提供路由、故障转移和监控。

| 功能 | JAiRouter | Nginx | One-API | LangChain |
|------|:---------:|:-----:|:-------:|:---------:|
| OpenAI 兼容 | ✅ | ❌ | ✅ | ✅ |
| 负载均衡 | ✅ | ✅ | ✅ | ❌ |
| 熔断降级 | ✅ | ❌ | ❌ | ❌ |
| 限流 | ✅ | ✅ | ✅ | ❌ |
| Web 控制台 | ✅ | ❌ | ✅ | ❌ |
| 配置热更新 | ✅ | ❌ | ✅ | ❌ |
| 版本控制 | ✅ | ❌ | ❌ | ❌ |
| OpenTelemetry | ✅ | ❌ | ❌ | ✅ |

---

## 性能测试

与直接访问后端服务的性能对比：

| 场景 | 直接访问 Ollama | 通过 JAiRouter | 额外开销 |
|------|----------------|---------------|----------|
| 单次请求 | 1.2s | 1.21s | <1% |
| 100 并发 | 45s | 48s | ~6% |
| 开启限流 | 不支持 | 可配置 | - |
| 开启熔断 | 不支持 | 自动故障转移 | - |

> 测试环境：Ubuntu 22.04, 16核, 32GB RAM, Ollama 0.1.27

---

## 文档资源

| 资源 | 链接 |
|------|------|
| 📖 **完整文档** | https://jairouter.com |
| 📘 **API 参考** | http://localhost:8080/swagger-ui |
| 🚀 **部署指南** | https://jairouter.com/zh/deployment/ |
| 🔧 **配置说明** | https://jairouter.com/zh/configuration/ |
| 📊 **监控配置** | https://jairouter.com/zh/monitoring/ |

---

## 发展路线

- [x] 核心网关功能
- [x] 多后端适配器（Ollama、vLLM、GPUStack、Xinference、LocalAI）
- [x] 多策略负载均衡
- [x] 多算法限流
- [x] 自动熔断降级
- [x] Web 管理控制台
- [x] JWT + API Key 认证
- [x] OpenTelemetry 分布式追踪
- [x] 配置版本控制
- [ ] 自定义适配器插件系统
- [ ] GraphQL API 支持

> **LTS 版本**：v2.6.11 是长期支持版本，维护至 2028-05。

---

## 参与贡献

欢迎参与贡献！请查看 [贡献指南](https://jairouter.com/zh/development/contributing/)。

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter/modelrouter
mvn clean package -DskipTests
java -jar target/modelrouter.jar
```

---

## 获取支持

- **文档**：https://jairouter.com
- **问题反馈**：[GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **讨论交流**：[GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)

---

## 许可证

JAiRouter 基于 [Apache 2.0 License](LICENSE) 开源。

---

<p align="center">
  <strong>如果这个项目对您有帮助，请给一个 ⭐️ Star！</strong>
</p>

<p align="center">
  由 JAiRouter 团队用 ❤️ 构建
</p>

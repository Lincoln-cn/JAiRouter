# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter" width="380">
</p>

<p align="center">
  <strong>AI 模型服务统一网关</strong>
</p>

<p align="center">
  <strong>一行代码接入 Ollama、vLLM、GPUStack 等所有模型服务</strong>
</p>

<p align="center">
  <a href="https://github.com/Lincoln-cn/JAiRouter/stargazers">
    <img src="https://img.shields.io/github/stars/Lincoln-cn/JAiRouter?style=flat-square&logo=github" alt="GitHub stars">
  </a>
  <a href="https://hub.docker.com/r/sodlinken/jairouter">
    <img src="https://img.shields.io/docker/pulls/sodlinken/jairouter?style=flat-square&logo=docker" alt="Docker Pulls">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/Lincoln-cn/JAiRouter?style=flat-square" alt="License">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/releases">
    <img src="https://img.shields.io/github/v/release/Lincoln-cn/JAiRouter?style=flat-square" alt="Release">
  </a>
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="https://jairouter.com">文档</a> •
  <a href="https://jairouter.com/en/">English Docs</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">讨论</a>
</p>

---

## 立即开始

```bash
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest
```

打开 http://localhost:8080 访问 Web 控制台。

就这么简单，无需任何配置。

---

## JAiRouter 是什么？

JAiRouter 是一个 **AI 模型服务统一网关**，提供：

- **OpenAI 兼容 API** — 使用任意 OpenAI SDK，零代码改动
- **智能负载均衡** — 自动请求分发，健康检查
- **限流与熔断** — 保护服务免受过载
- **Web 控制台** — 可视化管理服务、实例、监控

```
┌─────────────────────────────────────────────────────────────────┐
│                           您的应用                               │
│                    (OpenAI SDK / LangChain / HTTP)              │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼ OpenAI API
┌─────────────────────────────────────────────────────────────────┐
│                         JAiRouter                                │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  路由   │ │负载均衡 │ │  限流   │ │  熔断   │ │  认证   │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
       ┌──────────────┬───────────┼───────────┬──────────────┐
       ▼              ▼           ▼           ▼              ▼
   ┌───────┐     ┌───────┐   ┌───────┐   ┌───────┐     ┌───────┐
   │Ollama │     │ vLLM  │   │GPUStack│  │Xinference│  │OpenAI │
   └───────┘     └───────┘   └───────┘   └───────┘     └───────┘
```

---

## 为什么选择 JAiRouter？

| 没有 JAiRouter | 使用 JAiRouter |
|---------------|----------------|
| 每个模型单独配置 endpoint | 统一的 API 入口 |
| 服务宕机时手动切换 | 自动熔断故障转移 |
| 每个服务单独实现认证 | 内置 JWT + API Key |
| 分散的日志和监控指标 | 统一的 Dashboard |
| 修改配置需重启服务 | Web 控制台热更新 |

---

## 功能特性

| 功能 | 描述 |
|------|------|
| 🔌 **OpenAI 兼容** | 所有 `/v1/*` 端点与 OpenAI SDK 完全兼容 |
| ⚖️ **负载均衡** | 轮询、加权、最少连接、IP Hash、一致性哈希 |
| 🛡️ **流量控制** | 令牌桶、漏桶、滑动窗口算法 |
| 🔥 **熔断降级** | 自动故障转移，可配置阈值 |
| 🔐 **双认证体系** | JWT + API Key 双重认证 |
| 📊 **可观测性** | Prometheus 指标、OpenTelemetry 追踪 |
| 💾 **状态持久化** | Redis / 文件存储，支持集群部署 |
| 🎛️ **Web 控制台** | 仪表板、服务管理、版本控制 |

---

## 支持的后端服务

| 后端 | Chat | Embedding | Rerank | TTS | STT | Image |
|------|:----:|:---------:|:------:|:---:|:---:|:-----:|
| **Ollama** | ✅ | ✅ | - | - | - | - |
| **vLLM** | ✅ | ✅ | - | - | - | - |
| **GPUStack** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Xinference** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **LocalAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ |
| **OpenAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ |

---

## 快速开始

### 1. 启动 JAiRouter

```bash
# 使用 Docker
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# 使用 Docker Compose
curl -O https://raw.githubusercontent.com/Lincoln-cn/JAiRouter/main/docker-compose.yml
docker compose up -d
```

### 2. 访问 Web 控制台

浏览器打开 http://localhost:8080/admin

**默认登录凭证：**
- 用户名：`admin`
- 密码：`ChangeMeOnFirstStartup123456`（开发环境默认值）

> 💡 **安全提示**：生产环境建议通过环境变量设置强密码：
> ```bash
> docker run -d --name jairouter -p 8080:8080 \
>   -e INITIAL_ADMIN_PASSWORD="YourSecurePassword!" \
>   sodlinken/jairouter:latest
> ```

### 3. 配置第一个服务

通过 Web 控制台或 API：

```bash
# 添加 Ollama 实例
curl -X POST http://localhost:8080/api/config/instance/add/chat \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.2",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "weight": 1
  }'
```

### 4. 调用 API

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # JAiRouter 处理认证
)

response = client.chat.completions.create(
    model="llama3.2",
    messages=[{"role": "user", "content": "你好！"}]
)
print(response.choices[0].message.content)
```

---

## 文档资源

| 资源 | 链接 |
|------|------|
| 📖 **完整文档** | https://jairouter.com |
| 📘 **API 参考** | http://localhost:8080/swagger-ui |
| 🚀 **部署指南** | [docs/deployment](docs/deployment) |
| 🔧 **配置说明** | [docs/configuration](docs/configuration) |
| 📊 **监控配置** | [docs/monitoring](docs/monitoring) |

---

## 性能测试

与直接访问 Ollama 的性能对比：

| 场景 | 直接访问 Ollama | 通过 JAiRouter | 额外开销 |
|------|----------------|---------------|----------|
| 单次请求 | 1.2s | 1.21s | <1% |
| 100 并发 | 45s | 48s | ~6% |
| 开启限流 | 不支持 | 可配置 | - |
| 开启熔断 | 不支持 | 自动故障转移 | - |

> 测试环境：Ubuntu 22.04, 16核, 32GB RAM, Ollama 0.1.27

---

## 功能对比

| 功能 | JAiRouter | Nginx | One-API | LangChain |
|------|:---------:|:-----:|:-------:|:---------:|
| OpenAI 兼容 | ✅ | ❌ | ✅ | ✅ |
| 负载均衡 | ✅ | ✅ | ✅ | ❌ |
| 限流 | ✅ | ✅ | ✅ | ❌ |
| 熔断 | ✅ | ❌ | ❌ | ❌ |
| Web 控制台 | ✅ | ❌ | ✅ | ❌ |
| 配置热更新 | ✅ | ❌ | ✅ | ❌ |
| 版本控制 | ✅ | ❌ | ❌ | ❌ |
| 分布式追踪 | ✅ | ❌ | ❌ | ✅ |

---

## 发展路线

- [x] 核心网关功能
- [x] 多后端适配器
- [x] 负载均衡与限流
- [x] 熔断降级
- [x] Web 管理控制台
- [x] JWT + API Key 认证
- [x] 分布式追踪
- [x] 配置版本控制
- [ ] 自定义适配器插件系统
- [ ] GraphQL API 支持

> **LTS 版本**：v2.6.11 是最终长期支持版本，维护至 2028-05。

---

## 参与贡献

欢迎参与贡献！请查看 [贡献指南](https://jairouter.com/zh/development/contributing/)。

```bash
# 克隆并构建
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter
mvn clean package -DskipTests

# 本地运行
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

# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter Logo" width="400">
</p>

<p align="center">
  <strong>AI 模型服务统一网关 | 一行代码接入所有模型</strong>
</p>

<p align="center">
  <a href="https://github.com/Lincoln-cn/JAiRouter/stargazers">
    <img src="https://img.shields.io/github/stars/Lincoln-cn/JAiRouter?style=social" alt="GitHub stars">
  </a>
  <a href="https://hub.docker.com/r/sodlinken/jairouter">
    <img src="https://img.shields.io/docker/pulls/sodlinken/jairouter" alt="Docker Pulls">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/Lincoln-cn/JAiRouter" alt="License">
  </a>
  <a href="https://deepwiki.com/Lincoln-cn/JAiRouter">
    <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki">
  </a>
</p>

<p align="center">
  <a href="README-ZH.md">中文文档</a> •
  <a href="https://jairouter.com">在线文档</a> •
  <a href="https://jairouter.com/en/">English Docs</a>
</p>

---

> ⚠️ **LTS 版本公告**: `v2.6.11` 是最终长期支持版本，将在 **24 个月内**（至 2028-05）持续提供安全补丁和关键修复。v3.0 微服务架构因社区需求有限已无限期推迟。

---

## 🎯 为什么选择 JAiRouter？

**一个网关，统一管理所有 AI 模型服务**

| 对比项 | 没有 JAiRouter | 使用 JAiRouter |
|--------|---------------|----------------|
| **多模型管理** | 每个服务独立配置 endpoint | 统一 OpenAI 兼容 API `/v1/*` |
| **负载均衡** | 手动切换或 Nginx 配置 | 5种策略自动分发 + 健康检查 |
| **故障转移** | 服务中断等待恢复 | 自动熔断 + 快速恢复 |
| **访问控制** | 各自实现认证逻辑 | JWT + API Key 双认证体系 |
| **监控追踪** | 分散的日志和指标 | 统一 Dashboard + Prometheus |
| **配置变更** | 重启服务生效 | Web 控制台动态更新 |

---

## 🏗️ 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                        │
│    OpenAI SDK │ Langchain │ 自定义 HTTP 客户端                              │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │ OpenAI 兼容 API
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JAiRouter 网关                                     │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │ 负载均衡    │ │ 限流熔断    │ │ 认证鉴权    │ │ 追踪监控    │           │
│  │ 5种策略     │ │ Token Bucket│ │ JWT+API Key │ │ OpenTelemetry│           │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘           │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                    多后端适配器                                   │       │
│  │  GPUStack │ Ollama │ vLLM │ Xinference │ LocalAI │ OpenAI      │       │
│  └─────────────────────────────────────────────────────────────────┘       │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────┐           ┌───────────────┐           ┌───────────────┐
│   Ollama      │           │    vLLM       │           │  GPUStack     │
│ localhost:11434│          │ localhost:8000│           │ localhost:80  │
└───────────────┘           └───────────────┘           └───────────────┘
```

---

## ⚡ 1分钟快速体验

无需配置后端服务，即刻体验 Web 控制台：

```bash
# 拉取并启动
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# 访问控制台
open http://localhost:8080
```

**默认登录凭证**：
- 用户名：`admin`
- 密码：查看容器日志获取（`docker logs jairouter | grep password`）

---

## 🧭 功能概览（Web 控制台）

| 模块 | 功能 | 说明 |
|------|------|------|
| 🔍 **仪表板** | Dashboard | 实时展示系统状态、服务健康度、请求趋势、异常统计 |
| ⚙️ **服务管理** | 服务/实例配置 | 动态配置 AI 服务类型、适配器、负载均衡策略 |
| 📦 **版本管理** | 配置版本控制 | 创建、应用、回滚、对比配置版本 |
| 🔐 **安全管理** | API Key / JWT | 密钥生命周期管理、审计日志、黑名单 |
| 📊 **追踪管理** | 分布式追踪 | 追踪搜索、性能分析、采样配置 |
| 👤 **系统管理** | 账户管理 | 管理员账户、权限分配、操作日志 |

---

## 🚀 核心特性

| 特性 | 描述 |
|------|------|
| **OpenAI 兼容 API** | 所有 `/v1/*` 端点与 OpenAI SDK 完全兼容 |
| **多后端适配** | GPUStack、Ollama、vLLM、Xinference、LocalAI |
| **智能负载均衡** | 轮询、加权、最少连接、一致性哈希、IP Hash |
| **流量控制** | Token Bucket、Leaky Bucket、Sliding Window |
| **熔断降级** | 自动故障检测、快速恢复、Fallback 响应 |
| **状态持久化** | Redis / 文件存储，支持集群部署 |
| **配置版本控制** | Git 风格的版本管理，一键回滚 |

---

## 🚀 完整部署

### 方式一：Docker（推荐生产环境）

```bash
# 1. 生成安全密钥
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# 2. 设置环境变量
export JWT_SECRET="<生成的密钥>"
export INITIAL_ADMIN_PASSWORD="<强密码>"

# 3. 启动服务
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET="$JWT_SECRET" \
  -e INITIAL_ADMIN_PASSWORD="$INITIAL_ADMIN_PASSWORD" \
  -v jairouter-data:/app/data \
  sodlinken/jairouter:latest
```

### 方式二：Docker Compose

```yaml
version: '3.8'
services:
  jairouter:
    image: sodlinken/jairouter:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JWT_SECRET=your-jwt-secret-at-least-32-characters
      - INITIAL_ADMIN_PASSWORD=YourStrongPassword123!
    volumes:
      - jairouter-data:/app/data
volumes:
  jairouter-data:
```

### 方式三：Kubernetes

```bash
# 使用 Helm Chart
helm install jairouter ./helm/jairouter \
  --set jwt.secret="your-jwt-secret" \
  --set admin.password="YourStrongPassword123!"
```

---

## 📚 文档资源

| 资源 | 链接 |
|------|------|
| 📖 **在线文档** | https://jairouter.com |
| 📘 **API 文档** | http://localhost:8080/swagger-ui/index.html |
| 🐙 **GitHub** | https://github.com/Lincoln-cn/JAiRouter |
| 🐳 **Docker Hub** | https://hub.docker.com/r/sodlinken/jairouter |

---

## 🔧 支持的服务类型

| 类型 | 端点 | 说明 |
|------|------|------|
| Chat Completions | `/v1/chat/completions` | 对话补全 |
| Embeddings | `/v1/embeddings` | 文本嵌入 |
| Rerank | `/v1/rerank` | 文本重排 |
| Text-to-Speech | `/v1/audio/speech` | 语音合成 |
| Speech-to-Text | `/v1/audio/transcriptions` | 语音识别 |
| Image Generation | `/v1/images/generations` | 图像生成 |

---

## 📌 开发路线图

<details>
<summary><strong>查看完整路线图</strong></summary>

### ✅ Phase 1-5: 已完成 (v0.x - v2.6.x)

| 阶段 | 版本范围 | 主要内容 |
|------|---------|---------|
| 基础功能 | v0.1.0 - v1.2.5 | 网关核心、Docker化、H2支持 |
| 安全管理 | v1.5.6 - v1.9.6 | JWT/API Key、审计日志、监控体系 |
| 代码重构 | v2.0.0 - v2.3.3 | 性能优化、组件拆分 |
| 状态持久化 | v2.5.0 - v2.5.15 | Redis/File 持久化 |
| 代码质量 | v2.6.1 - **v2.6.11 LTS** | Checkstyle清理、最终LTS版本 |

### ⏸️ Phase 6-7: 无限期推迟

v2.7-v3.0 微服务架构转型因社区需求有限，已无限期推迟。当前 LTS 分支将专注于稳定性维护。

</details>

---

## 🤝 贡献与支持

- **问题反馈**: [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **贡献指南**: [Contributing](https://jairouter.com/zh/development/contributing/)
- **讨论交流**: [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)

---

## 📄 许可证

本项目采用 [Apache 2.0 License](LICENSE) 开源协议。

---

<p align="center">
  <strong>如果 JAiRouter 对您有帮助，请给个 ⭐️ Star 支持一下！</strong>
</p>

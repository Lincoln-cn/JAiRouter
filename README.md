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
  <a href="README-ZH.md">中文</a> •
  <a href="https://jairouter.com">文档</a> •
  <a href="https://jairouter.com/en/">English</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">讨论</a>
</p>

---

## Get started

```bash
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest
```

### Access Web Console

Open http://localhost:8080/admin in your browser.

**Default Login Credentials:**
- Username: `admin`
- Password: `ChangeMeOnFirstStartup123456` (development default)

> 💡 **Security Note**: For production, set a strong password via environment variable:
> ```bash
> docker run -d --name jairouter -p 8080:8080 \
>   -e INITIAL_ADMIN_PASSWORD="YourSecurePassword!" \
>   sodlinken/jairouter:latest
> ```

That's it. No configuration needed.

---

## What is JAiRouter?

JAiRouter is a **unified gateway for AI model services** that provides:

- **OpenAI-compatible API** — Use any OpenAI SDK, zero code changes
- **Smart Load Balancing** — Automatic request distribution with health checks
- **Rate Limiting & Circuit Breaking** — Protect your services from overload
- **Web Console** — Visual management for services, instances, and monitoring

```
┌─────────────────────────────────────────────────────────────────┐
│                         Your Application                         │
│                    (OpenAI SDK / LangChain / HTTP)              │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼ OpenAI API
┌─────────────────────────────────────────────────────────────────┐
│                         JAiRouter                                │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ Router  │ │Balance  │ │Limit    │ │Circuit  │ │  Auth   │   │
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

## Why JAiRouter?

| Without JAiRouter | With JAiRouter |
|-------------------|----------------|
| Configure each model endpoint separately | Single unified API endpoint |
| Manual failover when services go down | Automatic circuit breaker |
| Implement auth for each service | JWT + API Key built-in |
| Scattered logs and metrics | Centralized dashboard |
| Restart service to update config | Web console, hot reload |

---

## Features

| Feature | Description |
|---------|-------------|
| 🔌 **OpenAI Compatible** | All `/v1/*` endpoints work with OpenAI SDKs |
| ⚖️ **Load Balancing** | Round-robin, weighted, least-connections, IP-hash, consistent-hash |
| 🛡️ **Rate Limiting** | Token bucket, leaky bucket, sliding window algorithms |
| 🔥 **Circuit Breaker** | Auto failover with configurable thresholds |
| 🔐 **Authentication** | JWT + API Key dual authentication |
| 📊 **Observability** | Prometheus metrics, OpenTelemetry tracing |
| 💾 **Persistence** | Redis / File storage for cluster deployment |
| 🎛️ **Web Console** | Dashboard, service management, version control |

---

## Supported Backends

| Backend | Chat | Embedding | Rerank | TTS | STT | Image |
|---------|:----:|:---------:|:------:|:---:|:---:|:-----:|
| **Ollama** | ✅ | ✅ | - | - | - | - |
| **vLLM** | ✅ | ✅ | - | - | - | - |
| **GPUStack** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Xinference** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **LocalAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ |
| **OpenAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ |

---

## Quick Start

### 1. Start JAiRouter

```bash
# Using Docker
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# Using Docker Compose
curl -O https://raw.githubusercontent.com/Lincoln-cn/JAiRouter/main/docker-compose.yml
docker compose up -d
```

### 2. Access Web Console

Open http://localhost:8080 in your browser.

Default credentials:
- Username: `admin`
- Password: Check logs with `docker logs jairouter | grep password`

### 3. Configure Your First Service

Via Web Console or API:

```bash
# Add an Ollama instance
curl -X POST http://localhost:8080/api/config/instance/add/chat \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.2",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "weight": 1
  }'
```

### 4. Make API Calls

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # JAiRouter handles auth
)

response = client.chat.completions.create(
    model="llama3.2",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

---

## Documentation

| Resource | Link |
|----------|------|
| 📖 **Full Documentation** | https://jairouter.com |
| 📘 **API Reference** | http://localhost:8080/swagger-ui |
| 🚀 **Deployment Guide** | [docs/deployment](docs/deployment) |
| 🔧 **Configuration** | [docs/configuration](docs/configuration) |
| 📊 **Monitoring** | [docs/monitoring](docs/monitoring) |

---

## Benchmarks

Performance comparison with direct Ollama access:

| Scenario | Direct Ollama | Via JAiRouter | Overhead |
|----------|---------------|---------------|----------|
| Single request | 1.2s | 1.21s | <1% |
| 100 concurrent | 45s | 48s | ~6% |
| With rate limiting | N/A | Configurable | - |
| With circuit breaker | N/A | Auto failover | - |

> Benchmarks run on: Ubuntu 22.04, 16 cores, 32GB RAM, Ollama 0.1.27

---

## Comparison

| Feature | JAiRouter | Nginx | One-API | LangChain |
|---------|:---------:|:-----:|:-------:|:---------:|
| OpenAI Compatible | ✅ | ❌ | ✅ | ✅ |
| Load Balancing | ✅ | ✅ | ✅ | ❌ |
| Rate Limiting | ✅ | ✅ | ✅ | ❌ |
| Circuit Breaker | ✅ | ❌ | ❌ | ❌ |
| Web Console | ✅ | ❌ | ✅ | ❌ |
| Config Hot Reload | ✅ | ❌ | ✅ | ❌ |
| Version Control | ✅ | ❌ | ❌ | ❌ |
| Distributed Tracing | ✅ | ❌ | ❌ | ✅ |

---

## Roadmap

- [x] Core gateway functionality
- [x] Multiple backend adapters
- [x] Load balancing & rate limiting
- [x] Circuit breaker
- [x] Web management console
- [x] JWT + API Key authentication
- [x] Distributed tracing
- [x] Configuration version control
- [ ] Plugin system for custom adapters
- [ ] GraphQL API support

> **LTS Release**: v2.6.11 is the final Long-Term Support version, maintained until 2028-05.

---

## Contributing

We welcome contributions! Please see our [Contributing Guide](https://jairouter.com/zh/development/contributing/).

```bash
# Clone and build
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter
mvn clean package -DskipTests

# Run locally
java -jar target/modelrouter.jar
```

---

## Support

- **Documentation**: https://jairouter.com
- **Issues**: [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)

---

## License

JAiRouter is released under the [Apache 2.0 License](LICENSE).

---

<p align="center">
  <strong>Star ⭐ this repo if you find it useful!</strong>
</p>

<p align="center">
  Made with ❤️ by the JAiRouter Team
</p>

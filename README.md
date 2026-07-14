# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter - AI Model Gateway" width="380">
</p>

<p align="center">
  <strong>Production-Ready AI Model Gateway</strong>
</p>

<p align="center">
  OpenAI-compatible API for unified routing, load balancing & failover<br>
  across Ollama, vLLM, GPUStack, Xinference, and more
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
  <a href="README-ZH.md">中文</a> •
  <a href="https://jairouter.com">Docs</a> •
  <a href="https://jairouter.com/en/">English Docs</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">Discussions</a>
</p>

---

## Quick Start

```bash
# Start with Docker (no configuration needed)
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# Open Web Console: http://localhost:8080/admin
# Default: admin / ChangeMeOnFirstStartup123456
```

---

## What is JAiRouter?

JAiRouter is a **production-ready AI model gateway** that provides a unified, OpenAI-compatible API for managing multiple LLM backends. It handles load balancing, rate limiting, circuit breaking, and failover — so you can focus on building applications, not managing infrastructure.

### Key Benefits

| Problem | JAiRouter Solution |
|---------|-------------------|
| Multiple model endpoints to manage | Single unified API endpoint |
| Manual failover when services fail | Automatic circuit breaker |
| Implementing auth for each service | JWT + API Key built-in |
| Scattered logs and metrics | Centralized observability |
| Service restart for config changes | Hot reload via Web Console |

### Core Features

- **🔌 OpenAI-Compatible API** — Drop-in replacement for OpenAI SDK, LangChain, LlamaIndex
- **⚖️ Smart Load Balancing** — Round-robin, weighted, least-connections, IP-hash, consistent-hash
- **🛡️ Rate Limiting** — Token bucket, leaky bucket, sliding window algorithms
- **🔥 Circuit Breaker** — Auto failover with configurable thresholds and recovery
- **🔐 Authentication** — JWT + API Key dual authentication with audit logging
- **📊 Observability** — Prometheus metrics, OpenTelemetry tracing, real-time dashboards
- **💾 Persistence** — Redis / H2 / File storage for distributed deployment
- **🎛️ Web Console** — Visual management, version control, configuration rollback

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Your Application                         │
│                 (OpenAI SDK / LangChain / LlamaIndex)           │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼ OpenAI-Compatible API
┌─────────────────────────────────────────────────────────────────┐
│                         JAiRouter Gateway                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ Routing │ │ Balance │ │  Limit  │ │ Circuit │ │   Auth  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Observability & Persistence                 │   │
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

## Supported AI Backends

| Backend | Chat | Embedding | Rerank | TTS | STT | Image | Notes |
|---------|:----:|:---------:|:------:|:---:|:---:|:-----:|-------|
| **Ollama** | ✅ | ✅ | - | - | - | - | Local inference |
| **vLLM** | ✅ | ✅ | - | - | - | - | High-throughput |
| **GPUStack** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Full-featured |
| **Xinference** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Multi-model |
| **LocalAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ | OpenAI-compatible |
| **OpenAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ | Cloud fallback |

---

## Usage Example

### Python with OpenAI SDK

```python
from openai import OpenAI

# Point to JAiRouter instead of OpenAI
client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # JAiRouter handles authentication
)

# Use any model from your configured backends
response = client.chat.completions.create(
    model="llama3.2",  # Routed to Ollama, vLLM, or GPUStack
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

### Add a Model Backend

```bash
# Via API - Add Ollama instance
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

## Why Choose JAiRouter?

### vs Nginx
Nginx is a general-purpose web server. JAiRouter is **purpose-built for AI/LLM workloads** with OpenAI-compatible routing, circuit breaking, and model-aware load balancing.

### vs One-API
One-API focuses on API key management and billing. JAiRouter focuses on **local model gateway** with advanced resilience patterns and observability.

### vs LangChain
LangChain is an application framework. JAiRouter is an **infrastructure layer** that works beneath LangChain to provide routing, failover, and monitoring.

| Feature | JAiRouter | Nginx | One-API | LangChain |
|---------|:---------:|:-----:|:-------:|:---------:|
| OpenAI Compatible | ✅ | ❌ | ✅ | ✅ |
| Load Balancing | ✅ | ✅ | ✅ | ❌ |
| Circuit Breaker | ✅ | ❌ | ❌ | ❌ |
| Rate Limiting | ✅ | ✅ | ✅ | ❌ |
| Web Console | ✅ | ❌ | ✅ | ❌ |
| Config Hot Reload | ✅ | ❌ | ✅ | ❌ |
| Version Control | ✅ | ❌ | ❌ | ❌ |
| OpenTelemetry | ✅ | ❌ | ❌ | ✅ |

---

## Benchmarks

Performance overhead compared to direct backend access:

| Scenario | Direct Ollama | Via JAiRouter | Overhead |
|----------|---------------|---------------|----------|
| Single request | 1.2s | 1.21s | <1% |
| 100 concurrent | 45s | 48s | ~6% |
| With rate limiting | N/A | Configurable | - |
| With circuit breaker | N/A | Auto failover | - |

> Benchmarks: Ubuntu 22.04, 16 cores, 32GB RAM, Ollama 0.1.27

---

## Documentation

| Resource | Link |
|----------|------|
| 📖 **Full Documentation** | https://jairouter.com |
| 📘 **API Reference** | http://localhost:8080/swagger-ui |
| 🚀 **Deployment Guide** | https://jairouter.com/en/deployment/ |
| 🔧 **Configuration** | https://jairouter.com/en/configuration/ |
| 📊 **Monitoring** | https://jairouter.com/en/monitoring/ |

---

## Roadmap

- [x] Core gateway functionality
- [x] Multiple backend adapters (Ollama, vLLM, GPUStack, Xinference, LocalAI)
- [x] Load balancing with multiple strategies
- [x] Rate limiting algorithms
- [x] Circuit breaker with auto recovery
- [x] Web management console
- [x] JWT + API Key authentication
- [x] OpenTelemetry distributed tracing
- [x] Configuration version control
- [x] Docker image optimization (Alpine/Distroless)
- [x] API Key quota management
- [x] Call history persistence
- [x] RBAC role-based access control
- [ ] Plugin system for custom adapters
- [ ] GraphQL API support

> **Current Release**: v2.7.11 | **LTS Release**: v2.6.11 (maintained until 2028-05)

---

## Contributing

We welcome contributions! See [Contributing Guide](https://jairouter.com/en/development/contributing/).

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter/modelrouter
mvn clean package -DskipTests
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

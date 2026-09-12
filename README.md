# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter — AI Model Gateway" width="180">
</p>

<p align="center">
  <strong>All your LLM backends behind one OpenAI-compatible API.</strong>
</p>

<p align="center">
  Ollama · vLLM · GPUStack · Xinference · OpenAI · Claude · Gemini<br>
  Unified routing · load balancing · rate limiting · circuit breaking · failover · visual console
</p>

<p align="center">
  <a href="https://github.com/Lincoln-cn/JAiRouter/releases">
    <img src="https://img.shields.io/github/v/release/Lincoln-cn/JAiRouter?style=flat-square" alt="Latest release">
  </a>
  <a href="https://hub.docker.com/r/sodlinken/jairouter">
    <img src="https://img.shields.io/docker/pulls/sodlinken/jairouter?style=flat-square&logo=docker" alt="Docker Pulls">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/Lincoln-cn/JAiRouter?style=flat-square" alt="License">
  </a>
</p>

<p align="center">
  <a href="README-ZH.md">中文</a> •
  <a href="https://jairouter.com">Docs</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">Discussions</a>
</p>

- **One endpoint for every model** — replace direct calls to Ollama, vLLM, GPUStack, OpenAI, Claude, Gemini with a single OpenAI-compatible `base_url`. Existing OpenAI SDK, LangChain and LlamaIndex code keeps working unchanged.
- **Built for local inference clusters** — load balancing across instances (including latency-aware and tag-based selection), circuit breaking, and failover that retries on a *different* healthy instance when one dies.
- **Made for teams** — Web console with hot reload, config versioning & rollback, RBAC, audit logging, encrypted call records and full observability. Change routing without restarting anything.

## Try it in 3 minutes

```bash
# Start the gateway (zero configuration)
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# Web console:  http://localhost:8080/admin
# Default login: admin / ChangeMeOnFirstStartup123456
```

Point any OpenAI-compatible client at it:

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # JAiRouter handles authentication
)

response = client.chat.completions.create(
    model="llama3.2",  # any model from your configured backends
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

Add your own backends — Ollama, vLLM, GPUStack or any cloud provider — in the console under **Instance Management**. Changes apply via hot reload, no restart required.

<p align="center">
  <img src="screenshots/dashboard-en-US-light.png" alt="JAiRouter dashboard" width="720">
  <br/>
  <em>Management console — real-time service, instance and system metrics</em>
</p>

<details>
<summary><b>More screenshots</b></summary>

<p align="center">
  <img src="screenshots/service-management-en-US-light.png" alt="Service Management" width="720">
  <br/>
  <em>Service management — adapters and load-balancing strategies per service</em>
</p>

<p align="center">
  <img src="screenshots/instance-management-en-US-light.png" alt="Instance Management" width="720">
  <img src="screenshots/adapter-management-en-US-light.png" alt="Adapter Management" width="720">
  <br/>
  <em>Instance & adapter management</em>
</p>

<p align="center">
  <img src="screenshots/rule-management-en-US-light.png" alt="Rule Engine" width="720">
  <br/>
  <em>Visual routing rules with hit statistics, priority drag-and-drop and templates</em>
</p>

<p align="center">
  <img src="screenshots/call-history-dashboard-en-US-light.png" alt="Call History Analytics" width="720">
  <br/>
  <em>Call history analytics — success rate, latency and token usage trends</em>
</p>

<p align="center">
  <img src="screenshots/extra-slow-query-analysis-en-US-light.png" alt="Slow Query Analysis" width="720">
  <br/>
  <em>Slow-query analysis — hotspots, alert status and operation breakdown</em>
</p>

<p align="center">
  <img src="screenshots/playground-chat-en-US-light.png" alt="AI Playground" width="720">
  <br/>
  <em>AI playground — chat, embedding, rerank, audio and image test benches</em>
</p>

<p align="center">
  <img src="screenshots/dashboard-en-US-dark.png" alt="Dashboard (dark theme)" width="720">
  <br/>
  <em>Built-in dark theme — the console is fully themeable and bilingual (zh-CN / en-US)</em>
</p>

</details>

## What is JAiRouter?

JAiRouter is a **production-ready AI model gateway** for teams that run their own inference infrastructure (Ollama, vLLM, GPUStack, Xinference) alongside cloud providers. It exposes every backend through one unified, OpenAI-compatible API and adds the resilience and governance layer you would otherwise build yourself: load balancing, rate limiting, circuit breaking, failover, RBAC, audit logging and observability.

| Problem | JAiRouter Solution |
|---------|-------------------|
| Multiple model endpoints to manage | Single unified API endpoint |
| Manual failover when a service fails | Automatic circuit breaker |
| Implementing auth for each service | JWT + API Key built-in |
| Scattered logs and metrics | Centralized observability |
| Service restart for config changes | Hot reload via Web Console |

### Core Features

- **🔌 OpenAI-Compatible API** — Drop-in replacement for OpenAI SDK, LangChain, LlamaIndex
- **🔧 Configurable Adapters** — Add new AI providers (DeepSeek, Zhipu, etc.) via config or Web UI, no code needed
- **⚖️ Smart Load Balancing** — Round-robin, weighted, least-connections, IP-hash, consistent-hash, EWMA latency-aware
- **🎯 Rule Engine & Tag Routing** — Visual conditional routing (model name, service type, request header, client IP, weight, instance tags)
- **🛡️ Rate Limiting** — Token bucket, leaky bucket, sliding window algorithms
- **🔥 Circuit Breaker** — Auto failover with configurable thresholds, plus request-level failover that retries on another healthy instance
- **🔐 Authentication & RBAC** — JWT + API Key with audit logging and data-driven RBAC (44 permission codes, 4 role templates, menu & URL authorization)
- **📊 Observability** — Prometheus metrics, OpenTelemetry tracing, real-time dashboards, call-history analytics
- **🔒 Record Governance** — Three recording levels (metadata-only / desensitized summary / AES-256-GCM-encrypted full content)
- **⚡ Response Cache** — Deterministic requests reuse the downstream response and skip the backend (opt-in, tenant-isolated keys)
- **💾 Persistence** — Redis / H2 / File storage for distributed deployment
- **🎛️ Web Console** — Visual management, version control, configuration rollback, dark/light theme

## Supported AI Backends

<details>
<summary><b>Built-in adapters</b> — chat, embedding, rerank, TTS/STT and image coverage</summary>

| Backend | Chat | Embedding | Rerank | TTS | STT | Image | Notes |
|---------|:----:|:---------:|:------:|:---:|:---:|:-----:|-------|
| **Ollama** | ✅ | ✅ | - | - | - | - | Local inference |
| **vLLM** | ✅ | ✅ | - | - | - | - | High-throughput |
| **GPUStack** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Full-featured |
| **Xinference** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Multi-model |
| **LocalAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ | OpenAI-compatible |
| **OpenAI** | ✅ | ✅ | - | ✅ | ✅ | ✅ | Cloud fallback |
| **Anthropic Claude** | ✅ | - | - | - | - | - | Native Claude API |
| **Google Gemini** | ✅ | - | - | - | - | - | Native Gemini API |

</details>

<details>
<summary><b>Configurable adapters</b> — any OpenAI-compatible provider, no code required</summary>

| Provider | Configuration |
|----------|--------------|
| **DeepSeek** | `adapter-definitions: deepseek: type: openai-compatible` |
| **Zhipu (GLM)** | `adapter-definitions: zhipu: type: openai-compatible` |
| **Moonshot** | `adapter-definitions: moonshot: type: openai-compatible` |
| **Qwen (Tongyi)** | `adapter-definitions: qwen: type: openai-compatible` |
| **Baichuan** | `adapter-definitions: baichuan: type: openai-compatible` |
| **Minimax** | `adapter-definitions: minimax: type: openai-compatible` |

> 📖 See the [Adapter Configuration Guide](https://jairouter.com/configuration/adapter-config/) for details.

</details>

## Why Choose JAiRouter?

- **vs running backends directly** — no shared resilience, no central keys, no usage visibility. JAiRouter gives one endpoint, automatic failover and a full audit trail.
- **vs One-API / new-api** — those focus on distributing and billing API keys for shared accounts. JAiRouter targets the gateway in front of **your own local inference cluster**: multi-instance load balancing, circuit breaking, rule routing, RBAC and observability for self-hosted Ollama/vLLM/GPUStack. The two approaches complement each other.
- **vs Nginx** — Nginx is a general-purpose web server. JAiRouter is **purpose-built for AI/LLM workloads** with OpenAI-compatible routing, circuit breaking and model-aware load balancing.
- **vs LangChain** — LangChain is an application framework. JAiRouter is the **infrastructure layer beneath it**, providing routing, failover and monitoring.

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

## Architecture

<details>
<summary><b>How requests flow through the gateway</b></summary>

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

</details>

## Documentation

| Resource | Link |
|----------|------|
| Full Documentation | https://jairouter.com |
| Deployment Guide | https://jairouter.com/en/deployment/ |
| Configuration | https://jairouter.com/en/configuration/ |
| Monitoring | https://jairouter.com/en/monitoring/ |
| API Reference | http://localhost:8080/swagger-ui |

## Roadmap

**Released** (latest: v2.9.11)

- [x] Core gateway with OpenAI-compatible API
- [x] Built-in + configurable adapters (Ollama, vLLM, GPUStack, Xinference, LocalAI, OpenAI, Claude, Gemini, …)
- [x] Multi-strategy load balancing (round-robin, weighted, least-connections, IP-hash, consistent-hash, EWMA latency-aware)
- [x] Rate limiting (token bucket, leaky bucket, sliding window)
- [x] Circuit breaker + request-level failover (retry on another healthy instance)
- [x] Visual rule engine: conditional routing, service-level dynamic rate limiting, tag routing
- [x] JWT + API Key authentication with audit logging
- [x] Data-driven RBAC (44 permission codes, 4 role templates, menu & URL authorization)
- [x] Call history with record governance (metadata-only / summary / AES-256-GCM full) and analytics dashboards
- [x] Response cache (deterministic reuse; opt-in, tenant-isolated)
- [x] Prometheus metrics + OpenTelemetry distributed tracing
- [x] Configuration version control & rollback
- [x] Web console with hot reload and dark/light theme
- [x] Docker image optimization (Alpine/Distroless)

**In progress** (not yet released)

- [ ] Response cache P1: streaming SSE cache, invalidation API, rate-limit short-circuit

> **Current Release**: v3.0.3 | **LTS Release**: v2.6.11 (maintained until 2028-05)

## Contributing

Contributions are welcome — see the [Contributing Guide](https://jairouter.com/en/development/contributing/).

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter/modelrouter
mvn clean package -DskipTests
java -jar target/modelrouter.jar
```

## Support

- **Documentation**: https://jairouter.com
- **Issues**: [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)

## License

JAiRouter is released under the [Apache 2.0 License](LICENSE).

---

<p align="center">
  <strong>If JAiRouter saves you time, <a href="https://github.com/Lincoln-cn/JAiRouter/stargazers">star it on GitHub</a> ⭐</strong>
  <br/>
  <em>Questions? Open an issue or start a discussion — we answer.</em>
</p>

<p align="center">
  Maintained by Lincoln-cn · Apache-2.0
</p>

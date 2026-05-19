# JAiRouter

<p align="center">
  <strong>AI Model Service Unified Gateway</strong><br>
  <em>Connect to all models with a single line of code</em>
</p>

<p align="center">
  <a href="getting-started/quick-start.md">
    <img src="https://img.shields.io/badge/Quick_Start-🚀-blue?style=for-the-badge" alt="Quick Start">
  </a>
  <a href="configuration/index.md">
    <img src="https://img.shields.io/badge/Configuration-📖-green?style=for-the-badge" alt="Configuration">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter">
    <img src="https://img.shields.io/badge/GitHub-🐙-black?style=for-the-badge" alt="GitHub">
  </a>
</p>

---

## 🎯 Core Value

| Feature | Description |
|---------|-------------|
| **🔌 Unified API** | OpenAI-compatible interface, seamlessly connect to Ollama, vLLM, GPUStack, etc. |
| **⚖️ Smart Load Balancing** | 5 strategies for automatic request distribution, health checks to remove failed nodes |
| **🛡️ Traffic Control** | Rate limiting, circuit breaking, degradation for service stability |
| **🔐 Dual Authentication** | JWT + API Key with audit logs and blacklist support |
| **📊 Full-chain Tracing** | OpenTelemetry integration, performance bottlenecks at a glance |
| **💾 State Persistence** | Redis / file storage, supports high-availability cluster deployment |

---

## 🏗️ Architecture Overview

```
┌─────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
│   OpenAI SDK    │────▶│     JAiRouter       │────▶│    Ollama       │
│   Langchain     │     │  • Load Balancing   │     │    vLLM         │
│   HTTP Client   │     │  • Rate Limiting    │     │    GPUStack     │
└─────────────────┘     │  • Authentication   │     │    Xinference   │
                        └─────────────────────┘     └─────────────────┘
```

---

## ⚡ 1-Minute Quick Start

```bash
# Start the service
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# Access the console
open http://localhost:8080
```

---

## 📚 Quick Navigation

| I want to... | Recommended Docs |
|--------------|------------------|
| Get started quickly | [Quick Start Guide](getting-started/quick-start.md) |
| Deploy in production | [Deployment Guide](deployment/index.md) |
| Configure services | [Configuration Guide](configuration/index.md) |
| Integrate via API | [API Reference](api-reference/index.md) |
| Set up monitoring | [Monitoring Guide](monitoring/index.md) |
| Troubleshoot issues | [Troubleshooting](troubleshooting/index.md) |

---

## 📈 Project Status

[![GitHub stars](https://img.shields.io/github/stars/Lincoln-cn/JAiRouter?style=social)](https://github.com/Lincoln-cn/JAiRouter/stargazers)
[![Docker Pulls](https://img.shields.io/docker/pulls/sodlinken/jairouter)](https://hub.docker.com/r/sodlinken/jairouter)
[![License](https://img.shields.io/github/license/Lincoln-cn/JAiRouter)](https://github.com/Lincoln-cn/JAiRouter/blob/main/LICENSE)

> **LTS Release**: v2.6.11 is the final Long-Term Support version, maintained until 2028-05

---

## 🔧 Supported Backends

| Backend | Description |
|---------|-------------|
| **GPUStack** | GPUStack private model service |
| **Ollama** | Local LLM runtime |
| **vLLM** | High-performance LLM inference engine |
| **Xinference** | Distributed model inference framework |
| **LocalAI** | OpenAI-compatible local inference |
| **OpenAI** | Official OpenAI API |

---

## 🤝 Community Support

- [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues) - Bug reports
- [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions) - Discussions
- [Contributing Guide](development/contributing.md) - Join development

---

<p align="center">
If JAiRouter helps you, please give it a ⭐️ <a href="https://github.com/Lincoln-cn/JAiRouter">Star</a>!
</p>

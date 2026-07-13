# JAiRouter

<p align="center">
  <strong>AI 模型服务统一网关</strong><br>
  <em>一行代码接入所有模型</em>
</p>

<p align="center">
  <a href="getting-started/quick-start.md">
    <img src="https://img.shields.io/badge/快速开始-🚀-blue?style=for-the-badge" alt="快速开始">
  </a>
  <a href="configuration/index.md">
    <img src="https://img.shields.io/badge/配置指南-📖-green?style=for-the-badge" alt="配置指南">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter">
    <img src="https://img.shields.io/badge/GitHub-🐙-black?style=for-the-badge" alt="GitHub">
  </a>
</p>

---

## 🎯 核心价值

| 特性 | 描述 |
|------|------|
| **🔌 统一 API** | OpenAI 兼容接口，无缝对接 Ollama、vLLM、GPUStack 等 |
| **⚖️ 智能负载均衡** | 5 种策略自动分发请求，健康检查自动剔除故障节点 |
| **🛡️ 流量控制** | 限流、熔断、降级，保障服务稳定性 |
| **🔐 双认证体系** | JWT + API Key，支持审计日志和黑名单 |
| **📊 全链路追踪** | OpenTelemetry 集成，性能瓶颈一目了然 |
| **💾 状态持久化** | Redis / 文件存储，支持集群高可用部署 |

---

## 🏗️ 架构概览

```
┌─────────────────┐     ┌─────────────────────┐     ┌─────────────────┐
│   OpenAI SDK    │────▶│     JAiRouter       │────▶│    Ollama       │
│   Langchain     │     │  • 负载均衡          │     │    vLLM         │
│   HTTP Client   │     │  • 限流熔断          │     │    GPUStack     │
└─────────────────┘     │  • 认证鉴权          │     │    Xinference   │
                        └─────────────────────┘     └─────────────────┘
```

---

## ⚡ 1分钟快速体验

```bash
# 启动服务
docker run -d --name jairouter -p 8080:8080 sodlinken/jairouter:latest

# 访问控制台
open http://localhost:8080
```

---

## 📚 快速导航

| 我想要... | 推荐文档 |
|-----------|----------|
| 快速体验 | [快速开始指南](getting-started/quick-start.md) |
| 生产部署 | [部署指南](deployment/index.md) |
| 配置服务 | [配置指南](configuration/index.md) |
| API 集成 | [API 参考](api-reference/index.md) |
| 监控告警 | [监控指南](monitoring/index.md) |
| 故障排查 | [常见问题](troubleshooting/index.md) |

---

## 📈 项目状态

[![GitHub stars](https://img.shields.io/github/stars/Lincoln-cn/JAiRouter?style=social)](https://github.com/Lincoln-cn/JAiRouter/stargazers)
[![Docker Pulls](https://img.shields.io/docker/pulls/sodlinken/jairouter)](https://hub.docker.com/r/sodlinken/jairouter)
[![License](https://img.shields.io/github/license/Lincoln-cn/JAiRouter)](https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE)

> **当前版本**: v2.7.8 (2026-07-10) | **LTS 版本**: v2.6.11 (维护至 2028-05)

---

## 🔧 支持的后端服务

| 后端 | 说明 |
|------|------|
| **GPUStack** | GPUStack 私有模型服务 |
| **Ollama** | 本地大模型运行工具 |
| **vLLM** | 高性能 LLM 推理引擎 |
| **Xinference** | 分布式模型推理框架 |
| **LocalAI** | OpenAI 兼容本地推理 |
| **OpenAI** | OpenAI 官方 API |

---

## 🤝 社区支持

- [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues) - 问题反馈
- [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions) - 讨论交流
- [贡献指南](development/contributing.md) - 参与开发

---

<p align="center">
如果 JAiRouter 对您有帮助，请给个 ⭐️ <a href="https://github.com/Lincoln-cn/JAiRouter">Star</a> 支持一下！
</p>

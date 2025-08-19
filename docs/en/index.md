# JAiRouter

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter is a Spring Boot-based AI model service routing and load balancing gateway that provides unified access to various AI model services through OpenAI-compatible APIs.

## Core Features

- **Unified API Interface**: Provides OpenAI-compatible `/v1/*` API endpoints
- **Multiple Backend Support**: Supports GPUStack, Ollama, VLLM, Xinference, LocalAI, OpenAI and other backend adapters
- **Intelligent Load Balancing**: Supports Random, Round Robin, Least Connections, IP Hash and other load balancing strategies
- **Traffic Control**: Provides Token Bucket, Leaky Bucket, Sliding Window and other rate limiting algorithms
- **Fault Protection**: Built-in circuit breaker mechanism with fault detection and automatic recovery
- **Health Monitoring**: Automatic detection and removal of unhealthy service instances
- **Dynamic Configuration**: Supports runtime configuration updates without service restart

## Supported Service Types

- **Chat Completions**
- **Embeddings**
- **Rerank**
- **Text-to-Speech**
- **Speech-to-Text**
- **Image Generation**
- **Image Editing**

## Quick Start

1. [Installation Guide](getting-started/installation.md) - Learn how to install and configure JAiRouter
2. [Quick Start](getting-started/quick-start.md) - Get started with JAiRouter quickly
3. [Configuration Guide](configuration/index.md) - Detailed configuration instructions and examples

## Architecture Features

- **High Performance**: Reactive architecture based on Spring WebFlux, supporting high concurrency
- **High Availability**: Built-in health checks, circuit breakers and failover mechanisms
- **Easy to Extend**: Plugin-based adapter architecture supporting custom backend services
- **Easy to Operate**: Comprehensive monitoring metrics and management interfaces

## Community & Support

- [GitHub Repository](https://github.com/Lincoln-cn/JAiRouter)
- [Issue Reporting](https://github.com/Lincoln-cn/JAiRouter/issues)
- [Contributing Guide](development/contributing.md)

---

Welcome to JAiRouter! If you encounter any issues during use, please check our [Troubleshooting Guide](troubleshooting/index.md) or submit an issue on GitHub.
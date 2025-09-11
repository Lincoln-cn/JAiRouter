# JAiRouter

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 是一个基于 Spring Boot 的 AI 模型服务路由和负载均衡网关，通过 OpenAI 兼容的 API 提供对各种 AI 模型服务的统一访问。

## 核心功能

- **统一 API 接口**: 提供与 OpenAI 兼容的 `/v1/*` API 端点
- **多后端支持**: 支持 GPUStack、Ollama、VLLM、Xinference、LocalAI、OpenAI 等多种后端适配器
- **智能负载均衡**: 支持随机、轮询、最少连接、IP 哈希等多种负载均衡策略
- **流量控制**: 提供令牌桶、漏桶、滑动窗口等多种限流算法
- **故障保护**: 内置熔断器机制，提供故障检测和自动恢复
- **健康监控**: 自动检测和移除不健康的服务实例
- **动态配置**: 支持运行时配置更新，无需重启服务

## 支持的服务类型

- **聊天对话** (Chat Completions)
- **文本嵌入** (Embeddings)
- **文本重排** (Rerank)
- **语音合成** (Text-to-Speech)
- **语音识别** (Speech-to-Text)
- **图像生成** (Image Generation)
- **图像编辑** (Image Editing)

## 快速开始

1. [安装指南](getting-started/installation.md) - 了解如何安装和配置 JAiRouter
2. [快速开始](getting-started/quick-start.md) - 快速上手使用 JAiRouter
3. [配置指南](configuration/index.md) - 详细的配置说明和示例

## 架构特点

- **高性能**: 基于 Spring WebFlux 的响应式架构，支持高并发处理
- **高可用**: 内置健康检查、熔断器和故障转移机制
- **易扩展**: 插件化的适配器架构，支持自定义后端服务
- **易运维**: 完善的监控指标和管理接口

## 社区与支持

- [GitHub 仓库](https://github.com/Lincoln-cn/JAiRouter)
- [问题反馈](https://github.com/Lincoln-cn/JAiRouter/issues)
- [贡献指南](development/contributing.md)

---

欢迎使用 JAiRouter！如果您在使用过程中遇到任何问题，请查看我们的[故障排查指南](troubleshooting/index.md)或在 GitHub 上提交问题。
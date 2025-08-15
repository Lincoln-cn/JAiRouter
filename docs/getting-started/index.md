# 快速开始

欢迎使用 JAiRouter！本指南将帮助您从零开始，快速掌握 JAiRouter AI 模型路由网关的安装、配置和使用。

## 🚀 JAiRouter 简介

JAiRouter 是一个基于 Spring Boot 的 AI 模型服务路由和负载均衡网关，为多种 AI 服务提供统一的 OpenAI 兼容 API 接口。

### 核心特性

- 🎯 **统一接口**: 提供 OpenAI 兼容的 `/v1/*` API
- ⚖️ **智能负载均衡**: 支持多种负载均衡策略
- 🛡️ **流量保护**: 内置限流、熔断、降级机制
- 📊 **实时监控**: 完整的监控指标和健康检查
- 🔧 **动态配置**: 支持运行时配置更新
- 🌐 **多后端支持**: 兼容 Ollama、GPUStack、VLLM 等

## 📚 学习路径

我们为不同需求的用户提供了优化的学习路径：

### 🏃‍♂️ 快速体验（5分钟）
适合想要快速了解 JAiRouter 功能的用户：
1. **[快速开始](quick-start.md)** - 一键启动并体验核心功能

### 🎯 深入学习（30分钟）
适合准备在项目中使用 JAiRouter 的用户：
1. **[安装指南](installation.md)** - 详细的安装和构建说明
2. **[快速开始](quick-start.md)** - 5分钟快速体验
3. **[第一步](first-steps.md)** - 深入配置和生产准备

### 🏗️ 生产部署（60分钟）
适合需要部署到生产环境的用户：
1. 完成上述所有步骤
2. **[配置指南](../configuration/index.md)** - 详细配置参数
3. **[部署指南](../deployment/index.md)** - 生产环境部署
4. **[监控指南](../monitoring/index.md)** - 监控和告警设置

## 🎯 选择您的起点

根据您的情况选择合适的起点：

| 情况 | 推荐路径 | 预计时间 |
|------|----------|----------|
| **第一次接触 JAiRouter** | [快速开始](quick-start.md) | 5分钟 |
| **需要安装 JAiRouter** | [安装指南](installation.md) | 10-15分钟 |
| **准备生产环境使用** | [第一步](first-steps.md) | 30分钟 |
| **已有经验，查找特定信息** | [配置指南](../configuration/index.md) | 按需 |

## 🔧 系统要求

### 最低要求
| 组件 | 版本要求 | 说明 |
|------|----------|------|
| **JDK** | 17+ | 仅本地运行需要 |
| **Docker** | 20.10+ | 容器化部署（推荐） |
| **内存** | 512MB+ | 最小运行内存 |
| **磁盘** | 1GB+ | 包含日志和配置空间 |

### 支持的操作系统
- ✅ **Windows**: 10/11, Server 2019+
- ✅ **Linux**: Ubuntu 18.04+, CentOS 7+, RHEL 7+
- ✅ **macOS**: 10.15+

## 🚀 立即开始

### 方式一：快速体验（推荐新手）
如果您想快速了解 JAiRouter 的功能：

```bash
# 一键启动（需要 Docker）
docker run -d --name jairouter -p 8080:8080 jairouter/model-router:latest
```

然后访问 **[快速开始](quick-start.md)** 继续体验。

### 方式二：完整安装
如果您需要详细了解安装选项和构建过程：

👉 **[安装指南](installation.md)** - 查看所有安装方式

### 方式三：直接深入
如果您已经安装好 JAiRouter：

👉 **[第一步](first-steps.md)** - 开始深入配置

## 💡 需要帮助？

- 📖 查看 [故障排查](../troubleshooting/index.md)
- 🐛 提交 [GitHub Issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- 💬 加入社区讨论
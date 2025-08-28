﻿# JAiRouter

JAiRouter 是一个基于 Spring Boot 的模型服务路由和负载均衡网关，用于统一管理和路由各种 AI 模型服务（如
Chat、Embedding、Rerank、TTS 等），支持多种负载均衡策略、限流、熔断、健康检查、动态配置更新等功能。

[English Introduction](README-EN.md)

---
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Lincoln-cn/JAiRouter)

## ✨ 核心特性

| 特性类别          | 支持内容                                                         |
|---------------|--------------------------------------------------------------|
| **统一 API 网关** | 支持 OpenAI 兼容格式，统一 `/v1/*` 接口                                 |
| **服务类型**      | Chat、Embedding、Rerank、TTS、STT、Image Generation、Image Editing |
| **负载均衡策略**    | Random、Round Robin、Least Connections、IP Hash                 |
| **限流算法**      | Token Bucket、Leaky Bucket、Sliding Window、Warm Up             |
| **熔断机制**      | 支持失败阈值、恢复检测、降级策略                                             |
| **健康检查**      | 每服务独立状态接口，支持自动剔除不可用实例，定时清理不活跃限流器                             |
| **适配器支持**     | GPUStack、Ollama、VLLM、Xinference、LocalAI、OpenAI               |
| **动态配置更新**    | 支持运行时更新服务实例、权重、限流、熔断等配置                                      |
| **配置持久化**     | 支持内存存储和文件存储两种后端，配置文件自动合并                                     |
| **测试覆盖**      | 包含负载均衡、限流、熔断、控制器等单元测试                                        |

---

## 📚 在线文档

完整的项目文档已迁移至 GitHub Pages，可在线访问：

- [中文文档](https://lincoln-cn.github.io/JAiRouter)
- [English Documentation](https://lincoln-cn.github.io/JAiRouter/en/)

文档内容包括：

- 快速开始指南
- 详细配置说明
- API 参考
- 部署指南
- 监控配置
- 开发指南
- 故障排查

---

## 🚀 快速开始

```
# 拉取最新镜像
docker pull sodlinken/jairouter:latest

# 运行容器
docker run -p 8080:8080 sodlinken/jairouter:latest
```

3. 访问服务

```bash
curl http://localhost:8080/actuator/health
```

### 传统方式部署

构建项目

```bash
./mvnw clean package
```

启动服务

```bash
java -jar target/model-router-*.jar
```

访问服务

```bash
curl http://localhost:8080/actuator/health
```

## 📘 API 文档

启动项目后，可通过以下地址访问自动生成的 API 文档：

- **Swagger UI**: http://127.0.0.1:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://127.0.0.1:8080/v3/api-docs

## 📌 开发计划（更新状态）

| 阶段    | 状态 | 内容                               |
|-------|----|----------------------------------|
| 0.1.0 | ✅  | 基础网关、适配器、负载均衡、健康检查               |
| 0.2.0 | ✅  | 限流、熔断、降级、配置持久化、动态更新接口            |
| 0.2.1 | ✅  | 定时清理任务、内存优化、客户端IP限流增强、配置文件自动合并   |
| 0.3.0 | ✅  | Docker 容器化、多环境部署、监控集成            |
| 0.3.1 | ✅  | 中国使用alibaba mvn源加速镜像构建           |  
| 0.4.0 | ✅  | 监控指标、Prometheus 集成、告警通知          |
| 0.5.0 | ✅  | 对项目中涉及到的所有文档，使用github pages 进行管理 |
| 0.6.0 | ✅ | 认证鉴权                             |
| 0.7.0 | 🚧 | 日志追踪                             |
| 0.8.0 | 📋 | docker hub 发布自动打包发布镜像            |
| 0.9.0 | 📋 | 增强监控仪表板和用户管理功能                   |
| 1.0.0 | 📋 | 企业级部署指南                          |

---

如需进一步扩展，请查看 [DeepWiki 文档](https://deepwiki.com/Lincoln-cn/JAiRouter) 或提交 Issue 参与共建。

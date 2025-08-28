﻿# JAiRouter

JAiRouter is a Spring Boot-based model service routing and load balancing gateway designed to centrally manage and route various AI model services (such as Chat, Embedding, Rerank, TTS, etc.), supporting multiple load balancing strategies, rate limiting, circuit breaking, health checks, dynamic configuration updates, and more.

[English Introduction](README-EN.md)

---

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Lincoln-cn/JAiRouter)

## ✨ Core Features

| Feature Category       | Supported Content                                                                 |
|------------------------|-----------------------------------------------------------------------------------|
| **Unified API Gateway**| Supports OpenAI-compatible format, unified `/v1/*` interface                      |
| **Service Types**      | Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing                |
| **Load Balancing Strategies** | Random, Round Robin, Least Connections, IP Hash                           |
| **Rate Limiting Algorithms** | Token Bucket, Leaky Bucket, Sliding Window, Warm Up                     |
| **Circuit Breaker**    | Supports failure threshold, recovery detection, fallback strategies               |
| **Health Check**       | Independent status interface per service, supports automatic removal of unavailable instances, scheduled cleanup of inactive rate limiters |
| **Adapter Support**    | GPUStack, Ollama, VLLM, Xinference, LocalAI, OpenAI                              |
| **Dynamic Configuration Update** | Supports runtime updates for service instances, weights, rate limits, circuit breakers, etc. |
| **Configuration Persistence** | Supports both in-memory and file-based storage backends, automatic configuration file merging |
| **Test Coverage**      | Includes unit tests for load balancing, rate limiting, circuit breaking, controllers, etc. |

---

## 📚 Online Documentation

The complete project documentation has been migrated to GitHub Pages and can be accessed online:

- [中文文档](https://lincoln-cn.github.io/JAiRouter)
- [English Documentation](https://lincoln-cn.github.io/JAiRouter/en/)

Documentation includes:

- Quick Start Guide
- Detailed Configuration Instructions
- API Reference
- Deployment Guide
- Monitoring Configuration
- Development Guide
- Troubleshooting

---

## 🚀 Quick Start

```
# Pull the latest image
docker pull sodlinken/jairouter:latest

# Run the container
docker run -p 8080:8080 sodlinken/jairouter:latest
```

### Traditional Deployment

Build the project

```bash
./mvnw clean package
```

Start the service

```bash
java -jar target/model-router-*.jar
```

Access the service

```bash
curl http://localhost:8080/actuator/health
```

## 📘 API Documentation

After starting the project, you can access the auto-generated API documentation at:

- **Swagger UI**: http://127.0.0.1:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://127.0.0.1:8080/v3/api-docs

## 📌 Development Roadmap (Status Update)

| Phase | Status | Content                                                  |
|-------|--------|----------------------------------------------------------|
| 0.1.0 | ✅     | Basic gateway, adapters, load balancing, health checks   |
| 0.2.0 | ✅     | Rate limiting, circuit breaking, fallback, config persistence, dynamic update API |
| 0.2.1 | ✅     | Scheduled cleanup tasks, memory optimization, enhanced client IP rate limiting, auto-merge config files |
| 0.3.0 | ✅     | Docker containerization, multi-environment deployment, monitoring integration |
| 0.3.1 | ✅     | Use Alibaba Maven repository to accelerate image builds in China |
| 0.4.0 | ✅     | Monitoring metrics, Prometheus integration, alert notifications |
| 0.5.0 | ✅     | Manage all project-related documentation using GitHub Pages |
| 0.6.0 | 🚧     | Authentication and authorization                         |
| 0.7.0 | 📋     | Log tracing                                              |

---

For further extensions, please refer to the [DeepWiki Documentation](https://deepwiki.com/Lincoln-cn/JAiRouter) or submit an Issue to contribute.

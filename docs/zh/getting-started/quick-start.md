# 快速开始

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本指南将在 5 分钟内帮您快速体验 JAiRouter 的核心功能，无需复杂配置即可开始使用。

## 🎯 学习目标

完成本指南后，您将能够：
- ✅ 启动 JAiRouter 服务
- ✅ 配置第一个 AI 模型服务
- ✅ 发送 API 请求并获得响应
- ✅ 体验负载均衡和限流功能

## 📋 前提条件

选择以下任一方式：

### 方式一：Docker（推荐新手）
- ✅ 已安装 Docker 20.10+
- ✅ 无需安装 Java 环境

### 方式二：本地运行
- ✅ 已安装 Java 17+
- ✅ 下载了 JAiRouter JAR 文件

### 可选：AI 模型服务
- 如果您已有 AI 模型服务（如 Ollama、GPUStack），可以直接配置
- 如果没有，我们将在步骤中提供测试用的模拟服务

> 💡 **提示**: 如果您还没有安装 JAiRouter，请先查看 [安装指南](installation.md)。

## 🚀 步骤 1：启动 JAiRouter

### 方式一：Docker 一键启动（推荐）

```bash
# 拉取并运行 JAiRouter
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  sodlinken/jairouter:latest

# 检查运行状态
docker ps --filter "name=jairouter"
```

**预期输出**：
```
CONTAINER ID   IMAGE                           COMMAND                  CREATED         STATUS         PORTS                    NAMES
abc123def456   sodlinken/jairouter:latest   "java -jar /app/mode…"   2 seconds ago   Up 1 second    0.0.0.0:8080->8080/tcp   jairouter
```

### 方式二：JAR 文件直接运行

```bash
# 如果已下载 JAR 文件
java -jar model-router.jar

# 或指定配置文件
java -jar model-router.jar --spring.config.location=classpath:/application.yml
```

**预期输出**：
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.5.x)

2025-01-15 10:00:00.000  INFO --- [           main] o.u.m.ModelRouterApplication            : Started ModelRouterApplication in 3.456 seconds
```

> 💡 **故障排查**: 如果端口 8080 被占用，可以使用 `-p 8081:8080` 映射到其他端口。

## ✅ 步骤 2：验证服务启动

### 检查服务健康状态

```bash
# 检查健康状态
curl http://localhost:8080/actuator/health
```

**预期响应**：
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### 检查服务信息

```bash
# 查看应用信息
curl http://localhost:8080/actuator/info
```

**预期响应**：
```json
{
  "app": {
    "name": "JAiRouter",
    "version": "0.3.1",
    "description": "AI Model Service Router and Load Balancer"
  }
}
```

> ✅ **成功标志**: 看到 `"status": "UP"` 表示服务启动成功！

## 📚 步骤 3：探索 API 文档

### 访问 Swagger UI

在浏览器中打开：
```
http://localhost:8080/swagger-ui/index.html
```

您将看到交互式 API 文档，包含三大类接口：

| 接口类别 | 路径前缀 | 功能描述 |
|----------|----------|----------|
| **统一 API** | `/v1/*` | OpenAI 兼容的 AI 服务接口 |
| **配置管理** | `/api/config/*` | 动态配置管理接口 |
| **监控运维** | `/actuator/*` | 健康检查和监控指标 |

### 核心 API 预览

- **Chat 对话**: `POST /v1/chat/completions`
- **文本嵌入**: `POST /v1/embeddings`
- **添加实例**: `POST /api/config/instance/add/{type}`
- **查看实例**: `GET /api/config/instance/type/{type}`

> 💡 **提示**: 在 Swagger UI 中可以直接测试 API，无需额外工具。

## ⚙️ 步骤 4：配置第一个 AI 服务

我们提供两种配置方式，推荐使用 API 方式进行动态配置。

### 方式一：API 动态配置（推荐）

假设您有一个运行在 `http://localhost:11434` 的 Ollama 服务：

```bash
# 添加 Chat 服务实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.2:3b",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "weight": 1
  }'
```

**预期响应**：
```json
{
  "success": true,
  "message": "Instance added successfully",
  "instanceId": "llama3.2:3b@http://localhost:11434"
}
```

### 方式二：配置文件（适合批量配置）

创建配置目录和文件：

```bash
# 创建配置目录
mkdir -p config

# 创建配置文件
cat > config/model-router-config@1.json << 'EOF'
{
  "services": {
    "chat": {
      "instances": [
        {
          "name": "llama3.2:3b",
          "baseUrl": "http://localhost:11434",
          "path": "/v1/chat/completions",
          "weight": 1
        }
      ],
      "loadBalance": {
        "type": "round-robin"
      }
    }
  }
}
EOF
```

然后重启 JAiRouter：

```bash
# Docker 重启（挂载配置目录）
docker stop jairouter
docker rm jairouter
docker run -d \
  --name jairouter \
  -p 8080:8080 \
  -v $(pwd)/config:/app/config:ro \
  sodlinken/jairouter:latest

# JAR 重启
# 停止当前进程（Ctrl+C），然后重新运行
java -jar model-router.jar
```

### 没有 AI 服务？使用模拟服务

如果您暂时没有可用的 AI 服务，可以配置一个模拟服务进行测试：

```bash
# 添加模拟服务实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mock-model",
    "baseUrl": "http://httpbin.org",
    "path": "/json",
    "weight": 1
  }'
```

> 💡 **提示**: 模拟服务仅用于测试路由功能，不会返回真实的 AI 响应。

## 步骤 5：测试 AI 服务

### 测试 Chat 接口

```bash
curl -X POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.2:3b",
    "messages": [
      {
        "role": "user",
        "content": "Hello, how are you?"
      }
    ],
    "max_tokens": 100
  }'
```

### 预期响应

```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "llama3.2:3b",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! I'm doing well, thank you for asking. How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 9,
    "completion_tokens": 20,
    "total_tokens": 29
  }
}
```

## 步骤 6：查看服务状态

### 检查实例列表

```bash
# 获取 Chat 服务的所有实例
curl "http://localhost:8080/api/config/instance/type/chat"
```

### 查看监控指标

```bash
# 查看应用指标
curl "http://localhost:8080/actuator/metrics"

# 查看特定指标（如请求计数）
curl "http://localhost:8080/actuator/metrics/http.server.requests"
```

## 步骤 7：体验负载均衡

添加第二个服务实例来体验负载均衡：

```bash
# 添加另一个实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.2:1b",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "weight": 2
  }'
```

现在发送多个请求，JAiRouter 会根据配置的负载均衡策略（默认为轮询）分发请求。

## 步骤 8：体验限流功能

JAiRouter 内置了多种限流算法。让我们快速测试一下：

```bash
# 快速发送多个请求
for i in {1..10}; do
  curl -X POST "http://localhost:8080/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -d '{
      "model": "llama3.2:3b",
      "messages": [{"role": "user", "content": "Test request '$i'"}],
      "max_tokens": 10
    }' &
done
```

如果配置了限流，部分请求可能会收到 `429 Too Many Requests` 响应。

## 常见问题

### Q: 服务启动失败怎么办？

**A:** 检查以下几点：
1. 端口 8080 是否被占用
2. Java 版本是否为 17+
3. 查看日志获取详细错误信息

```bash
# Docker 查看日志
docker logs jairouter

# JAR 运行时日志会输出到控制台
```

### Q: 无法连接到后端 AI 服务？

**A:** 确认：
1. 后端服务是否正常运行
2. 网络连接是否正常
3. 配置的 URL 和端口是否正确

```bash
# 测试后端服务连接
curl http://localhost:11434/v1/models
```

### Q: API 调用返回错误？

**A:** 检查：
1. 请求格式是否正确
2. 模型名称是否匹配
3. 查看 JAiRouter 日志获取详细信息

## 🎉 恭喜完成快速开始！

您已经成功体验了 JAiRouter 的核心功能。现在您可以：

### 🎯 继续深入学习

| 下一步 | 内容 | 适合场景 |
|--------|------|----------|
| **[第一步指南](first-steps.md)** | 深入配置和生产准备 | 准备在项目中使用 |
| **[配置指南](../configuration/index.md)** | 详细配置参数说明 | 需要特定配置 |
| **[API 参考](../api-reference/index.md)** | 完整 API 文档 | 开发集成 |
| **[部署指南](../deployment/index.md)** | 生产环境部署 | 上线部署 |

### 🔧 解决问题

如果在快速开始过程中遇到问题：

- **服务启动问题**: 查看 [安装指南故障排查](installation.md#故障排查)
- **配置问题**: 查看 [第一步指南](first-steps.md#常见问题)
- **API 调用问题**: 查看 [故障排查文档](../troubleshooting/index.md)

### 📚 推荐学习路径

**新手用户**：
1. ✅ 快速开始（当前）
2. 👉 [第一步指南](first-steps.md) - 学习核心配置
3. 👉 [配置指南](../configuration/index.md) - 掌握高级配置

**有经验用户**：
1. ✅ 快速开始（当前）
2. 👉 [API 参考](../api-reference/index.md) - 查看接口详情
3. 👉 [部署指南](../deployment/index.md) - 生产环境部署

## 示例配置

这里提供一个完整的配置示例，包含多种服务类型：

```json
{
  "services": {
    "chat": {
      "instances": [
        {
          "name": "llama3.2:3b",
          "baseUrl": "http://localhost:11434",
          "path": "/v1/chat/completions",
          "weight": 1
        },
        {
          "name": "qwen2:7b",
          "baseUrl": "http://localhost:11434",
          "path": "/v1/chat/completions",
          "weight": 2
        }
      ],
      "loadBalance": {
        "type": "round-robin"
      },
      "rateLimit": {
        "type": "token-bucket",
        "capacity": 100,
        "refillRate": 10
      }
    },
    "embedding": {
      "instances": [
        {
          "name": "nomic-embed-text",
          "baseUrl": "http://localhost:11434",
          "path": "/v1/embeddings",
          "weight": 1
        }
      ],
      "loadBalance": {
        "type": "random"
      }
    }
  },
  "store": {
    "type": "file",
    "path": "config/"
  }
}
```

将此配置保存为 `config/model-router-config@1.json`，重启 JAiRouter 即可生效。
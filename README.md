#JAiRouter

JAiRouter 是一个基于 Spring Boot 的模型服务路由和负载均衡网关，用于统一管理和路由各种 AI 模型服务（如 Chat、Embedding、Rerank、TTS 等），支持多种负载均衡策略。

## 功能特性

- **统一 API 网关**：提供统一的 API 接入点，支持 OpenAI 兼容的接口格式
- **多模型服务支持**：支持 Chat、Embedding、Rerank、TTS、STT、Image Generation、Image Editing 等多种模型服务
- **负载均衡策略**：
    - Random（随机）
    - Round Robin（轮询）
    - Least Connections（最少连接）
    - IP Hash（一致性哈希）
- **权重支持**：所有负载均衡策略均支持实例权重配置
- **健康检查**：提供各服务状态监控接口
- **适配器支持**：支持多种后端服务适配器（GPUStack、Ollama、VLLM、Xinference、LocalAI等）

## 项目结构

```
src/main/java/org/unreal/modelrouter
├── adapter         # 适配器模块，用于对接不同的模型服务
├── checker         # 健康检查模块
├── config          # 配置类
├── controller      # 控制器
├── dto             # 数据传输对象
├── loadbalancer    # 负载均衡策略实现
├── response        # 统一响应处理
├── util            # 工具类
└── ModelRouterApplication.java  # 启动类
```

## 支持的服务类型

1. **Chat Service** (`/v1/chat/completions`)
    - 支持流式和非流式响应
    - 负载均衡策略：Least Connections

2. **Embedding Service** (`/v1/embeddings`)
    - 支持单个和批量嵌入
    - 负载均衡策略：Round Robin

3. **Rerank Service** (`/v1/rerank`)
    - 文本重排序服务
    - 负载均衡策略：IP Hash

4. **TTS Service** (`/v1/audio/speech`)
    - 文本转语音服务
    - 支持流式输出
    - 负载均衡策略：Random（默认）

5. **STT Service** (`/v1/audio/transcriptions`)
    - 语音转文本服务
    - 负载均衡策略：Random（默认）

6. **Image Generation Service** (`/v1/images/generations`)
    - 图像生成服务
    - 负载均衡策略：Random（默认）

7. **Image Editing Service** (`/v1/images/edits`)
    - 图像编辑服务
    - 负载均衡策略：Random（默认）

## 配置说明

配置文件：`src/main/resources/application.yml`

### 全局负载均衡配置

```yaml
model:
  services:
    load-balance:
      type: random              # 负载均衡策略
      hash-algorithm: "md5"     # IP Hash 算法
```

### 服务实例配置示例

```yaml
chat:
  load-balance:
    type: least-connections
  instances:
    - name: "qwen3:1.7B"
      base-url: "http://172.16.30.2:30909"
      path: "/v1-openai/chat/completions"
      weight: 1
```

## API 接口

### Chat Completion
```bash
POST /v1/chat/completions
```

### Embedding
```bash
POST /v1/embeddings
```

### Rerank
```bash
POST /v1/rerank
```

### TTS
```bash
POST /v1/audio/speech
```

### STT
```bash
POST /v1/audio/transcriptions
```

### Image Generation
```bash
POST /v1/images/generations
```

### Image Editing
```bash
POST /v1/images/edits
```

### 健康检查
各服务都提供状态检查接口：
- `/v1/chat/status`
- `/v1/embeddings/status`
- `/v1/rerank/status`
- `/v1/audio/speech/status`
- `/v1/audio/transcriptions/status`
- `/v1/images/generations/status`
- `/v1/images/edits/status`

## 负载均衡策略

1. **Random（随机）**：根据权重随机选择实例
2. **Round Robin（轮询）**：按顺序轮询选择实例，支持权重
3. **Least Connections（最少连接）**：选择当前连接数最少的实例，考虑权重因素
4. **IP Hash（一致性哈希）**：根据客户端 IP 哈希值选择实例，保证同一 IP 总是路由到同一实例

## 部署

```bash
# 编译
./mvnw clean package

# 运行
java -jar target/model-router-*.jar
```

## 依赖

- Spring Boot 2.x
- Spring WebFlux
- Reactor Core

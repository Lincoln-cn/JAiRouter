# JAiRouter

JAiRouter 是一个基于 Spring Boot 的模型服务路由和负载均衡网关，用于统一管理和路由各种 AI 模型服务（如 Chat、Embedding、Rerank、TTS 等），支持多种负载均衡策略。

[English Introduction](README-EN.md)

## Java Version
JDK version >= 17

## DeepWiki
https://deepwiki.com/Lincoln-cn/JAiRouter

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
model:
  # 全局配置
  load-balance:
    type: random # 支持: random, round-robin, least-connections, ip-hash
    hash-algorithm: "md5" # IP Hash 策略的哈希算法
  # 全局适配器配置 - 如果服务没有指定适配器，将使用此配置
  adapter: gpustack # 支持: normal, gpustack, ollama, vllm, xinference, localai
  services:
    # 聊天服务配置
    chat:
      load-balance:
        type: least-connections
      adapter: gpustack # 使用GPUStack适配器
      instances:
        - name: "qwen3:1.7B"
          base-url: "http://172.16.30.6:9090"
          path: "/v1-openai/chat/completions"
          weight: 1
        - name: "qwen3:1.7B"
          base-url: "http://172.16.30.7:9090"
          path: "/v1/chat/completions"
          weight: 1

    # Embedding 服务配置
    embedding:
      load-balance:
        type: round-robin
      instances:
        - name: "nomic-embed-text-v1.5"
          base-url: "http://172.16.30.11:9090"
          path: "/v1/embeddings"
          weight: 1
        - name: "nomic-embed-text-v1.5"
          base-url: "http://172.16.30.15:9090"
          path: "/v1/embeddings"
          weight: 1
        - name: "bge-large-zh-v1.5"
          base-url: "http://172.16.30.12:9090/"
          path: "/v1/embeddings"
          weight: 1

    # Rerank 服务配置
    rerank:
      load-balance:
        type: ip-hash
        hash-algorithm: "sha256"
      instances:
        - name: "bge-reranker-v2-m3"
          base-url: "http://172.16.30.6:9090"
          path: "/v1/rerank"
          weight: 1
        - name: "bge-reranker-v2-m3"
          base-url: "http://172.16.30.6:9090"
          path: "/v1/rerank"
          weight: 2

    # TTS 服务配置
    tts:
      load-balance:
        type: random
      instances:
        - name: "cosyvoice-300m"
          base-url: "http://172.16.30.9:9090"
          path: "/v1/audio/speech"
          weight: 1
        - name: "cosyvoice-300m"
          base-url: "http://172.16.30.8:9090"
          path: "/v1/audio/speech"
          weight: 1

    # STT 服务配置
    stt:
      load-balance:
        type: round-robin
      instances:
        - name: "faster-whisper-tiny"
          base-url: "http://172.16.30.21:9090"
          path: "/v1/audio/transcriptions"
          weight: 2
        - name: "faster-whisper-tiny"
          base-url: "http://172.16.30.21:9090"
          path: "/v1/audio/transcriptions"
          weight: 1

    imgGen:
      load-balance:
          type: round-robin
      instances:
          - name: "stable-diffusion-2-1"
            base-url: "http://172.16.30.25:9090"
            path: "/v1/images/generations"
            weight: 1

    imgEdit:
      load-balance:
        type: round-robin
      instances:
        - name: "stable-diffusion-2-1"
          base-url: "http://172.16.30.25:9090"
          path: "/v1/images/edits"
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

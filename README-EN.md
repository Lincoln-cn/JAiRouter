# JAiRouter

JAiRouter is a Spring Boot-based model service routing and load balancing gateway designed to uniformly manage and route various AI model services (such as Chat, Embedding, Rerank, TTS, etc.), supporting multiple load balancing strategies.

[切换到中文版本](README.md)

## Features

- **Unified API Gateway**: Provides a unified API access point, supporting OpenAI-compatible interface formats.
- **Multi-Model Service Support**: Supports various model services including Chat, Embedding, Rerank, TTS, STT, Image Generation, and Image Editing.
- **Load Balancing Strategies**:
    - Random
    - Round Robin
    - Least Connections
    - IP Hash (Consistent Hashing)
- **Weight Support**: All load balancing strategies support instance weight configuration.
- **Health Check**: Provides service status monitoring interfaces.
- **Adapter Support**: Supports multiple backend service adapters (GPUStack, Ollama, VLLM, Xinference, LocalAI, etc.).

## Project Structure

```
src/main/java/org/unreal/modelrouter
├── adapter         # Adapter module for connecting different model services
├── checker         # Health check module
├── config          # Configuration classes
├── controller      # Controllers
├── dto             # Data Transfer Objects
├── loadbalancer    # Load balancing strategy implementations
├── response        # Unified response handling
├── util            # Utility classes
└── ModelRouterApplication.java  # Application startup class
```

## Supported Service Types

1. **Chat Service** (`/v1/chat/completions`)
    - Supports streaming and non-streaming responses
    - Load balancing strategy: Least Connections

2. **Embedding Service** (`/v1/embeddings`)
    - Supports single and batch embeddings
    - Load balancing strategy: Round Robin

3. **Rerank Service** (`/v1/rerank`)
    - Text reranking service
    - Load balancing strategy: IP Hash

4. **TTS Service** (`/v1/audio/speech`)
    - Text-to-speech service
    - Supports streaming output
    - Load balancing strategy: Random (default)

5. **STT Service** (`/v1/audio/transcriptions`)
    - Speech-to-text service
    - Load balancing strategy: Random (default)

6. **Image Generation Service** (`/v1/images/generations`)
    - Image generation service
    - Load balancing strategy: Random (default)

7. **Image Editing Service** (`/v1/images/edits`)
    - Image editing service
    - Load balancing strategy: Random (default)

## Configuration Instructions

Configuration file: `src/main/resources/application.yml`

### Global Load Balancing Configuration

```yaml
model:
  services:
    load-balance:
      type: random              # Load balancing strategy
      hash-algorithm: "md5"     # IP Hash algorithm
```

### Service Instance Configuration Example

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

## API Endpoints

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

### Health Check
Each service provides a status check endpoint:
- `/v1/chat/status`
- `/v1/embeddings/status`
- `/v1/rerank/status`
- `/v1/audio/speech/status`
- `/v1/audio/transcriptions/status`
- `/v1/images/generations/status`
- `/v1/images/edits/status`

## Load Balancing Strategies

1. **Random**: Selects instances randomly based on weights.
2. **Round Robin**: Selects instances in sequential order, supporting weights.
3. **Least Connections**: Selects the instance with the fewest current connections, considering weight factors.
4. **IP Hash (Consistent Hashing)**: Selects instances based on client IP hash values, ensuring the same IP always routes to the same instance.

## Deployment

```bash
# Compile
./mvnw clean package

# Run
java -jar target/model-router-*.jar
```

## Dependencies

- Spring Boot 2.x
- Spring WebFlux
- Reactor Core

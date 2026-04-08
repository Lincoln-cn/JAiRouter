# Product Overview

JAiRouter is a Spring Boot-based AI model service routing and load balancing gateway that provides unified access to various AI model services through OpenAI-compatible APIs.

## Core Purpose
- Centrally manage and route AI model services (Chat, Embedding, Rerank, TTS, STT, Image Generation/Editing)
- Provide unified `/v1/*` API endpoints compatible with OpenAI format
- Support multiple backend adapters (GPUStack, Ollama, VLLM, Xinference, LocalAI, OpenAI)

## Key Capabilities
- **Load Balancing**: Random, Round Robin, Least Connections, IP Hash strategies
- **Rate Limiting**: Token Bucket, Leaky Bucket, Sliding Window, Warm Up algorithms
- **Circuit Breaking**: Failure protection with configurable thresholds and recovery
- **Health Monitoring**: Automatic detection and removal of unhealthy instances
- **Dynamic Configuration**: Runtime updates without service restart
- **Configuration Persistence**: Memory and file-based storage backends

## Target Users
Developers and organizations needing to:
- Aggregate multiple AI model providers behind a single API
- Implement intelligent routing and load balancing for AI services
- Add reliability features (rate limiting, circuit breaking) to AI model access
- Dynamically manage AI service instances without downtime
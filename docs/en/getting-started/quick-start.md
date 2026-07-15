# Quick Start

<!-- 版本信息 -->
> **Doc Version**: 1.2.0
> **最后更新**: 2026-07-14
> **Applicable Version**: v1.8.0+
> **作者**: Lincoln
<!-- /版本信息 -->

This guide will help you make your first API call to JAiRouter and understand the basic concepts.

## Learning Objectives

After completing this guide, you will be able to:
- Start JAiRouter service
- Configure your first AI model service
- Send API requests and receive responses
- Experience load balancing and rate limiting

## Prerequisites

- JAiRouter is installed and running (see [Installation Guide](installation.md))
- At least one AI model service is configured and accessible

## Step 0: Generate Secure Keys (v1.8.0+ Recommended)

**v1.8.0+ provides a key generation tool** that automatically generates secure JWT keys and admin passwords.

### Option 1: Use Docker to Run Key Generation Tool (Recommended)

```bash
# Generate JWT key (Base64 encoded)
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-key

# Generate admin password
docker run --rm sodlinken/jairouter:latest java -jar /app/modelrouter.jar --generate-password
```

### Option 2: Use System Commands (No Docker Required)

```bash
# Generate Base64 encoded JWT key (at least 32 bytes)
openssl rand -base64 32

# Generate random password (16 characters, alphanumeric)
openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 16
```

### Set Environment Variables

```bash
# Set JWT key (use the generated key)
export JWT_SECRET="your-base64-encoded-secret"

# Set admin password (use the generated password)
export INITIAL_ADMIN_PASSWORD="MyStr0ng!Pass#2026"
```

## Your First API Call

### 1. Chat Completion

Make a chat completion request using the OpenAI-compatible API:

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen2.5:7b",
    "messages": [
      {
        "role": "user",
        "content": "Hello, how are you?"
      }
    ],
    "max_tokens": 100
  }'
```

### 2. Text Embeddings

Generate text embeddings:

```bash
curl -X POST http://localhost:8080/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-ada-002",
    "input": "Hello world"
  }'
```

## Common Configuration Examples

### Online API Services

**OpenAI GPT**
```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "gpt-4o",
    "baseUrl": "https://api.openai.com",
    "path": "/v1/chat/completions",
    "adapter": "normal",
    "headers": {
      "Authorization": "Bearer sk-proj-xxxxx"
    }
  }'
```

**DeepSeek**
```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "deepseek-chat",
    "baseUrl": "https://api.deepseek.com",
    "path": "/v1/chat/completions",
    "adapter": "normal",
    "headers": {
      "Authorization": "Bearer sk-xxxxxxxx"
    }
  }'
```

**Anthropic Claude**
```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "claude-sonnet",
    "baseUrl": "https://api.anthropic.com",
    "path": "/v1/messages",
    "adapter": "claude",
    "headers": {
      "x-api-key": "sk-ant-api03-xxxxx"
    }
  }'
```

> **Note**: The Claude adapter automatically injects the `anthropic-version` header.

**Google Gemini**
```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "gemini-pro",
    "baseUrl": "https://generativelanguage.googleapis.com/v1beta/openai",
    "path": "/chat/completions",
    "adapter": "normal",
    "headers": {
      "Authorization": "Bearer AIzaSyxxxxx"
    }
  }'
```

### Local Inference Engines

**Ollama**
```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.2:3b",
    "baseUrl": "http://localhost:11434",
    "path": "/api/chat",
    "adapter": "ollama"
  }'
```

**vLLM**
```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "vllm-qwen",
    "baseUrl": "http://localhost:8000",
    "path": "/v1/chat/completions",
    "adapter": "vllm"
  }'
```

> **Tip**: For more configuration examples, see [Instance Configuration Examples](../configuration/instance-examples.md).

## View Service Status

### Check Instance List

```bash
# Get all chat service instances
curl "http://localhost:8080/api/config/instance/type/chat"
```

### View Monitoring Metrics

```bash
# View application metrics
curl "http://localhost:8080/actuator/metrics"

# View specific metrics (e.g., request count)
curl "http://localhost:8080/actuator/metrics/http.server.requests"
```

## Experience Load Balancing

Add a second service instance to experience load balancing:

```bash
# Add another instance
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "llama3.2:1b",
    "baseUrl": "http://localhost:11434",
    "path": "/v1/chat/completions",
    "adapter": "ollama",
    "weight": 2
  }'
```

Now send multiple requests, and JAiRouter will distribute them based on the configured load balancing strategy (default: round-robin).

## Next Steps

| Next Step | Content | Suitable For |
|-----------|---------|--------------|
| **[First Steps Guide](first-steps.md)** | In-depth configuration and production preparation | Ready to use in projects |
| **[Configuration Guide](../configuration/index.md)** | Detailed configuration parameters | Need specific configuration |
| **[API Reference](../api-reference/index.md)** | Complete API documentation | Development integration |
| **[Deployment Guide](../deployment/index.md)** | Production environment deployment | Going live |

## Troubleshooting

### Q: Service fails to start?

**A:** Check the following:
1. Port 8080 is not occupied
2. Java version is 17+
3. Check logs for detailed error information

### Q: Cannot connect to backend AI service?

**A:** Confirm:
1. Backend service is running normally
2. Network connection is normal
3. Configured URL and port are correct

### Q: API call returns error?

**A:** Check:
1. Request format is correct
2. Model name matches
3. Check JAiRouter logs for detailed information

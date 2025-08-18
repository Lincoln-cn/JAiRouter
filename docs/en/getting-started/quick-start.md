# Quick Start

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


This guide will help you make your first API call to JAiRouter and understand the basic concepts.

## Prerequisites

- JAiRouter is installed and running (see [Installation Guide](installation.md))
- At least one AI model service is configured and accessible

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

### 3. Text-to-Speech

Generate speech from text:

```bash
curl -X POST http://localhost:8080/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tts-1",
    "input": "Hello, this is a test.",
    "voice": "alloy"
  }' \
  --output speech.mp3
```

## Understanding the Response

A typical chat completion response looks like:

```json
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "qwen2.5:7b",
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

## Load Balancing in Action

If you have multiple instances configured, JAiRouter will automatically distribute requests:

```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin
      instances:
        - name: "qwen2.5:7b"
          baseUrl: "http://server1:11434"
          weight: 2
        - name: "qwen2.5:7b"
          baseUrl: "http://server2:11434"
          weight: 1
```

With this configuration:
- Server1 will receive ~67% of requests (weight 2)
- Server2 will receive ~33% of requests (weight 1)

## Monitoring Your Requests

### 1. Check Service Health

```bash
curl http://localhost:8080/actuator/health
```

### 2. View Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

### 3. Check Instance Status

```bash
curl http://localhost:8080/api/config/instance/type/chat
```

## Rate Limiting

JAiRouter includes built-in rate limiting. If you exceed the configured limits, you'll receive a `429 Too Many Requests` response:

```json
{
  "error": {
    "message": "Rate limit exceeded",
    "type": "rate_limit_exceeded",
    "code": "rate_limit_exceeded"
  }
}
```

## Error Handling

JAiRouter provides consistent error responses:

### Service Unavailable (503)
```json
{
  "error": {
    "message": "All service instances are unavailable",
    "type": "service_unavailable",
    "code": "no_available_instances"
  }
}
```

### Circuit Breaker Open (503)
```json
{
  "error": {
    "message": "Circuit breaker is open",
    "type": "circuit_breaker_open",
    "code": "circuit_breaker_open"
  }
}
```

## Using with OpenAI SDK

JAiRouter is compatible with OpenAI SDKs. Simply change the base URL:

### Python
```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # JAiRouter doesn't require API keys by default
)

response = client.chat.completions.create(
    model="qwen2.5:7b",
    messages=[
        {"role": "user", "content": "Hello!"}
    ]
)
```

### Node.js
```javascript
import OpenAI from 'openai';

const openai = new OpenAI({
  baseURL: 'http://localhost:8080/v1',
  apiKey: 'not-needed'
});

const response = await openai.chat.completions.create({
  model: 'qwen2.5:7b',
  messages: [{ role: 'user', content: 'Hello!' }],
});
```

## Next Steps

Now that you've made your first API calls, learn more about:

1. **[Configuration](../configuration/index.md)** - Detailed configuration options
2. **[API Reference](../api-reference/index.md)** - Complete API documentation
3. **[Deployment](../deployment/index.md)** - Production deployment guides

## Common Use Cases

### 1. A/B Testing Models
Configure multiple models and use weights to split traffic:

```yaml
model:
  services:
    chat:
      instances:
        - name: "model-a"
          baseUrl: "http://server1:11434"
          weight: 1
        - name: "model-b"
          baseUrl: "http://server2:11434"
          weight: 1
```

### 2. Fallback Strategy
Configure primary and backup services:

```yaml
model:
  services:
    chat:
      circuit-breaker:
        enabled: true
        failure-threshold: 5
      fallback:
        type: default
        message: "Service temporarily unavailable"
```

### 3. Geographic Distribution
Route requests to the nearest server based on IP:

```yaml
model:
  services:
    chat:
      load-balance:
        type: ip-hash
      instances:
        - name: "us-east"
          baseUrl: "http://us-east.example.com:11434"
        - name: "eu-west"
          baseUrl: "http://eu-west.example.com:11434"
```
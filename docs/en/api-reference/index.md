# API Reference

JAiRouter provides OpenAI-compatible APIs for various AI services, along with management APIs for configuration and monitoring.

## API Categories

### 1. Universal APIs (`/v1/*`)

OpenAI-compatible endpoints for AI services:

- **[Universal API](universal-api.md)** - Chat, embeddings, TTS, STT, image generation
- Compatible with OpenAI SDKs and tools
- Consistent request/response format

### 2. Management APIs (`/api/*`)

JAiRouter-specific management endpoints:

- **[Management API](management-api.md)** - Dynamic configuration management
- **[Monitoring API](monitoring-api.md)** - Health checks and metrics

### 3. Actuator APIs (`/actuator/*`)

Spring Boot actuator endpoints for monitoring:

- Health checks
- Metrics
- Application info

## Base URL

All APIs are served from your JAiRouter instance:

```
http://localhost:8080
```

## Authentication

By default, JAiRouter doesn't require authentication. For production deployments, consider:

- Reverse proxy with authentication (nginx, Apache)
- API gateway with authentication
- Custom authentication filters

## Rate Limiting

All APIs are subject to configured rate limits. When limits are exceeded, you'll receive:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json

{
  "error": {
    "message": "Rate limit exceeded",
    "type": "rate_limit_exceeded",
    "code": "rate_limit_exceeded"
  }
}
```

## Error Handling

JAiRouter provides consistent error responses across all APIs:

### Standard Error Format

```json
{
  "error": {
    "message": "Error description",
    "type": "error_type",
    "code": "error_code",
    "details": {
      "additional": "information"
    }
  }
}
```

### Common Error Codes

| HTTP Status | Error Type | Description |
|-------------|------------|-------------|
| 400 | `invalid_request` | Malformed request |
| 404 | `not_found` | Resource not found |
| 429 | `rate_limit_exceeded` | Rate limit exceeded |
| 500 | `internal_error` | Internal server error |
| 503 | `service_unavailable` | No available instances |
| 503 | `circuit_breaker_open` | Circuit breaker is open |

## Content Types

### Request Content Types

- `application/json` - JSON requests (most APIs)
- `multipart/form-data` - File uploads (STT, image APIs)

### Response Content Types

- `application/json` - JSON responses (most APIs)
- `audio/*` - Audio files (TTS API)
- `image/*` - Image files (image generation API)

## Request/Response Examples

### Chat Completion

**Request:**
```http
POST /v1/chat/completions
Content-Type: application/json

{
  "model": "qwen2.5:7b",
  "messages": [
    {
      "role": "user",
      "content": "Hello!"
    }
  ],
  "max_tokens": 100,
  "temperature": 0.7
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

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
        "content": "Hello! How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 9,
    "completion_tokens": 12,
    "total_tokens": 21
  }
}
```

### Embeddings

**Request:**
```http
POST /v1/embeddings
Content-Type: application/json

{
  "model": "text-embedding-ada-002",
  "input": "Hello world"
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [0.1, 0.2, 0.3, ...],
      "index": 0
    }
  ],
  "model": "text-embedding-ada-002",
  "usage": {
    "prompt_tokens": 2,
    "total_tokens": 2
  }
}
```

## Streaming Responses

JAiRouter supports streaming for chat completions:

**Request:**
```http
POST /v1/chat/completions
Content-Type: application/json

{
  "model": "qwen2.5:7b",
  "messages": [{"role": "user", "content": "Hello!"}],
  "stream": true
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"qwen2.5:7b","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"qwen2.5:7b","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"qwen2.5:7b","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":"stop"}]}

data: [DONE]
```

## SDK Compatibility

JAiRouter is compatible with OpenAI SDKs. Simply change the base URL:

### Python (openai)
```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"
)
```

### Node.js (openai)
```javascript
import OpenAI from 'openai';

const openai = new OpenAI({
  baseURL: 'http://localhost:8080/v1',
  apiKey: 'not-needed'
});
```

### curl
```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "qwen2.5:7b", "messages": [{"role": "user", "content": "Hello!"}]}'
```

## API Versioning

JAiRouter follows OpenAI's API versioning:

- Current version: `v1`
- All endpoints are prefixed with `/v1/`
- Future versions will be additive and backward-compatible

## Pagination

For APIs that return lists (like instance management), pagination is supported:

```http
GET /api/config/instance/type/chat?page=1&size=10
```

**Response:**
```json
{
  "content": [...],
  "page": {
    "number": 1,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3
  }
}
```

## CORS Support

JAiRouter supports CORS for browser-based applications:

```http
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

## Health Checks

Check API health:

```http
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

## Next Steps

- **[Universal API](universal-api.md)** - OpenAI-compatible endpoints
- **[Management API](management-api.md)** - Configuration management
- **[Monitoring API](monitoring-api.md)** - Health and metrics
- **[OpenAPI Specification](openapi-spec.md)** - Interactive API documentation
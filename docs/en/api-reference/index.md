# API Reference

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->



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

Rate limiting does **not** apply to every API. It is enforced at these points: the AI call chain
(service-level limiting inside `ServiceRequestHandler`), the `/api/auth/api-keys` prefix
(`AdminApiRateLimiter`), and the API Key validation chain
(`ApiKeyValidator.validateRateLimit`, see the
[error code reference](error-codes.md#rate-limit-error-codes)).

The 429 body shape depends on the surface, and `code` carries the code that surface actually emits:
the admin rate limiter on `/api/auth/api-keys` writes the flat body with `RATE_LIMIT_EXCEEDED`,
while the API Key validation chain writes `RATE_LIMITED_API_KEY` (both answer 429).

`/v1/**` (OpenAI surface, mapped by `V1ErrorBodyMapper`; the code below is the validation chain's):

```json
{
  "error": {
    "message": "...",
    "type": "rate_limit_error",
    "code": "RATE_LIMITED_API_KEY"
  }
}
```

`/api/auth/api-keys` (flat body, **no** `error` wrapper — see `AdminApiRateLimiter`, method
`writeRateLimited`):

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "..."
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
| 400 | `invalid_request_error` | Malformed request |
| 404 | `not_found_error` | Resource not found |
| 429 | `rate_limit_error` | Rate limit exceeded |
| 500 | `api_error` | Internal server error |
| 503 | `api_error` | No available instances |
| 503 | `api_error` | Circuit breaker is open |

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

Endpoints whose result set can grow unbounded are paginated; the rest return a plain array.
Two paginated examples:

```http
GET /api/config/circuit-breaker/history?page=0&size=20
GET /api/security/blacklist/list?page=0&size=20
```

`page` defaults to `0` and `size` to `20`. The payload is the usual `RouterResponse` envelope with a
**flat** Spring Data `Page` inside `data` — the paging fields sit next to `content`, there is **no** nested
`page` object:

**Response:** (field set taken from a live call)
```json
{
  "success": true,
  "message": "...",
  "data": {
    "content": [ ],
    "empty": true,
    "first": true,
    "last": true,
    "number": 0,
    "numberOfElements": 0,
    "size": 20,
    "pageable": { },
    "sort": { },
    "totalElements": 0,
    "totalPages": 0
  },
  "timestamp": "2026-10-13 17:31:28"
}
```

⚠️ Not every list endpoint paginates. `GET /api/config/instance/{serviceType}` (instance management)
returns the **full array** of instances and ignores `page`/`size`. See
[Instance Management](management-api.md#instance-management) for that shape.

## CORS Support

Cross-origin access is **not** enabled globally. The global chain explicitly disables it
(`SecurityConfiguration#securityWebFilterChain`: `cors(cors -> cors.disable())`) and the per-controller
`@CrossOrigin(origins = "*")` annotations are what actually apply. Those annotations sit on the
router/management controllers (`Adapter*Controller`, `PoolConfigController`, `RuleConfigController`,
`RuleTemplateController`, `ModelInfoController`, `ModelCallStatsController`, `ModelStatsController`,
`ServiceTypeController`).

⇒ Responses carry `Access-Control-Allow-Origin: *` only for those endpoints. Endpoints **without** the
annotation — e.g. `ServiceTypeInstanceController` (`/api/config/instance/**`), the auth endpoints and
`/actuator/**` — send no CORS headers, so a browser on another origin cannot call them directly.
(That is why the console is served from the same origin; add a reverse proxy or an explicit annotation if
you need cross-origin access.)

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
# API Reference

<!-- 版本信息 -->
> **Doc Version**: 1.0.2  
> **Last Updated**: 2026-05-21  
> **Git Commit**: 61384b4a  
> **Author**: Lincoln
<!-- /版本信息 -->



JAiRouter provides OpenAI-compatible APIs for various AI services, along with management APIs for configuration and monitoring.

## API Categories

### 1. Universal APIs (`/api/v1/*`)

The unified entry point for AI services (OpenAI-compatible plus audio, image and rerank endpoints):

- **[Universal API](universal-api.md)** - Chat, embeddings, rerank, TTS, STT, image generation and editing
- Compatible with OpenAI SDKs and tools (the path prefix is `/api/v1`)
- Consistent request/response format

> There is also a small set of OpenAI-native paths under `/v1/*`, covering only `models`, `chat/completions`, `embeddings` and `rerank`; the audio and image endpoints exist only under `/api/v1/*`.

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

**Authentication is on by default**: `jairouter.security.enabled` defaults to `true`
(`config/auth/jwt.yml`), so `/v1/**` requires credentials, `/api/**` goes through the permission
matrix, and only the exempt list may be called anonymously (`/actuator/health` and its sub-paths,
`/actuator/info`, `/actuator/prometheus`, the login endpoints `POST /api/auth/jwt/login` and
`POST /api/auth/jwt/validate`, `/admin/**` static assets and `/favicon.ico` — see
`SecurityConfiguration.java` and `AnonymousEndpointPaths.java`). Swagger / OpenAPI is **not** exempt
unless `jairouter.security.docs-public=true` (`ApiDocsAccessPolicy.java`).

The gateway itself accepts two credential types:

- `X-API-Key: <API key created in the console>`
- `Jairouter_Token: <JWT>`

The `Authorization` header **does not participate in gateway authentication** — it is passed through
to the downstream AI service (instance-level `headers` take precedence).

To actually turn authentication off, set `jairouter.security.enabled: false` explicitly (not
recommended for production); do not rely on "no authentication by default".

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

The error body shape **depends on the entry surface**; there is no single "one shape for all APIs"
error body.

### `/v1/**` (OpenAI surface)

```json
{
  "error": {
    "message": "Error description",
    "type": "invalid_request_error",
    "code": "INVALID_REQUEST"
  }
}
```

There is **no** `param` and **no** `details` (`V1ErrorBodyMapper#toErrorBody` writes only
`message` / `type` / `code`). On the Anthropic surface (`/v1/messages`) the same mapper omits `code`
and returns `{"type":"error","error":{"type","message"}}` instead.

### `/api/**` (console surface)

Uses `RouterResponse` (`success` / `message` / `data` / `errorCode` / `timestamp`); `@JsonInclude(NON_NULL)`
drops the fields that are `null`, so an error usually carries `success`, `message`, `errorCode` and
`timestamp`, and `data` only when one is returned:

```json
{
  "success": false,
  "message": "Error description",
  "errorCode": "INVALID_REQUEST"
}
```

**Three responses on this surface do not use that shape** — they are not exceptions to "the surface",
but to "who writes the body":

| Case | Written by | Actual body |
|------|-----------|-------------|
| Missing / invalid credential (401) | authentication filter | `{"error":{"message":…,"type":"authentication_error","code":"AUTH_MISSING"}}` |
| Credential rate-limited (429) | authentication filter | `{"error":{"message":…,"type":"rate_limit_error","code":"RATE_LIMITED_API_KEY"}}` |
| Denied by the authorization layer (403) | Spring Security default handler | `text/plain`, body `Access Denied` |

A 403 thrown by a controller (for example `BUILTIN_READONLY`) *is* `RouterResponse`, which is why
clients should key off the writer: **detect the shape by the presence of the top-level `error` field**
and fall back to `RouterResponse` otherwise.

### `/v1/**` `type` values

| HTTP Status | `type` | Description |
|-------------|--------|-------------|
| 400 / 422 / other 4xx | `invalid_request_error` | Malformed request |
| 401 | `authentication_error` | Authentication failed |
| 403 | `permission_error` | Permission denied |
| 404 | `not_found_error` | Resource not found |
| 429 | `rate_limit_error` | Rate limit / quota exceeded |
| every 5xx (including 503) | `api_error` | Gateway or downstream internal error |

Source: `V1ErrorBodyMapper#errorTypeOf`. `service_unavailable` and `circuit_breaker_open` are **not**
`type` values that exist.

## Content Types

### Request Content Types

- `application/json` - JSON requests (nearly every API, including image generation and image editing)
- `multipart/form-data` - declared only by `/api/v1/audio/transcriptions` (STT)

### Response Content Types

- `application/json` - JSON responses (nearly every API, including image generation and image editing)

> The image APIs answer with JSON (`data[].url` or `data[].b64_json`) and do **not** emit `image/*`. The TTS endpoint (`POST /api/v1/audio/speech`) returns audio bytes whose Content-Type follows the requested output format.

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
  -H "X-API-Key: $JAiRouter_API_KEY" \
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
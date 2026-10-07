# API 参考

<!-- 版本信息 -->
> **文档版本**: 1.0.3  
> **最后更新**: 2026-09-15  
> **Git 提交**: 61384b4a  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 为各种 AI 服务提供 OpenAI 兼容的 API，以及用于配置和监控的管理 API。

## API 分类

### 1. 统一 API (`/v1/*`)

AI 服务的 OpenAI 兼容端点：

- **[统一 API](universal-api.md)** - 聊天、嵌入、TTS、STT、图像生成
- 与 OpenAI SDK 和工具兼容
- 一致的请求/响应格式

### 2. 管理 API (`/api/*`)

JAiRouter 特定的管理端点：

- **[管理 API](management-api.md)** - 动态配置管理
- **[监控 API](monitoring-api.md)** - 健康检查和指标

### 3. Actuator API (`/actuator/*`)

用于监控的 Spring Boot actuator 端点：

- 健康检查
- 指标
- 应用信息

## 基础 URL

所有 API 都从您的 JAiRouter 实例提供服务：

```
http://localhost:8080
```

## 认证

**认证默认是开启的**：`jairouter.security.enabled` 默认 `true`（`config/auth/jwt.yml`），因此 `/v1/**` 需要凭据、`/api/**` 走权限校验，只有豁免清单允许匿名访问（`/actuator/health`、`/actuator/info`、`/actuator/prometheus`、登录接口、Swagger 文档、`/admin/**` 与 `/favicon.ico` 静态资源 —— 见 `SecurityConfiguration.java` 与 `AnonymousEndpointPaths.java`）。

网关自身的凭据有两种：

- `X-API-Key: <控制台创建的 API Key>`
- `Jairouter_Token: <JWT>`

`Authorization` 头**不参与网关认证**，它被透传给下游 AI 服务（实例级 `headers` 优先）。

确实要关闭认证时，请显式设置 `jairouter.security.enabled: false`（不建议用于生产），不要依赖「默认不需要认证」。

## 限流

限流**不是**对所有 API 生效，只有两处：AI 调用链路（`ServiceRequestHandler` 里的服务级限流）与 `/api/auth/api-keys` 前缀（`AdminApiRateLimiter`）。

两处的 429 响应体形状不同。

`/v1/**`（OpenAI 面，经 `V1ErrorBodyMapper`）：

```json
{
  "error": {
    "message": "...",
    "type": "rate_limit_error",
    "code": "RATE_LIMIT_EXCEEDED"
  }
}
```

`/api/auth/api-keys`（扁平体，**没有** `error` 包裹，见 `AdminApiRateLimiter` 的 `writeRateLimited`）：

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "..."
}
```

## 错误处理

错误响应的形状**按入口面不同**，并不存在「所有 API 统一」的错误体。

### `/v1/**`（OpenAI 面）

```json
{
  "error": {
    "message": "错误描述",
    "type": "invalid_request_error",
    "code": "INVALID_REQUEST"
  }
}
```

**没有** `param`、也**没有** `details`（`V1ErrorBodyMapper#toErrorBody` 只写 `message`/`type`/`code`）。

### `/api/**`（控制台面）

沿用 `RouterResponse`（`success` / `message` / `data` / `errorCode` / `timestamp`）：

```json
{
  "success": false,
  "message": "错误描述",
  "errorCode": "INVALID_REQUEST"
}
```

### `/v1/**` 的 `type` 取值

| HTTP 状态 | `type` | 描述 |
|-----------|--------|------|
| 400 / 422 / 其他 4xx | `invalid_request_error` | 请求格式错误 |
| 401 | `authentication_error` | 认证失败 |
| 403 | `permission_error` | 权限不足 |
| 404 | `not_found_error` | 资源未找到 |
| 429 | `rate_limit_error` | 超出限流 / 配额 |
| 所有 5xx（含 503） | `api_error` | 网关或下游内部错误 |

来源：`V1ErrorBodyMapper#errorTypeOf`。**不存在** `service_unavailable`、`circuit_breaker_open` 这类 `type`。

## 内容类型

### 请求内容类型

- `application/json` - JSON 请求（绝大多数 API，含图像生成与图像编辑）
- `multipart/form-data` - 只有 `/api/v1/audio/transcriptions`（STT）声明了该消费类型

### 响应内容类型

- `application/json` - JSON 响应（绝大多数 API，含图像生成与图像编辑）

> 图像 API 的响应是 JSON（`data[].url` 或 `data[].b64_json`），**不**输出 `image/*`。TTS 接口（`POST /api/v1/audio/speech`）返回音频二进制，其 Content-Type 由所选输出格式决定。

## 请求/响应示例

### 聊天对话

**请求：**
```http
POST /v1/chat/completions
Content-Type: application/json

{
  "model": "qwen2.5:7b",
  "messages": [
    {
      "role": "user",
      "content": "你好！"
    }
  ],
  "max_tokens": 100,
  "temperature": 0.7
}
```

**响应：**
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
        "content": "你好！今天我能为您做些什么吗？"
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

## 流式响应

JAiRouter 支持聊天对话的流式响应：

**请求：**
```http
POST /v1/chat/completions
Content-Type: application/json

{
  "model": "qwen2.5:7b",
  "messages": [{"role": "user", "content": "你好！"}],
  "stream": true
}
```

**响应：**
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"qwen2.5:7b","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"qwen2.5:7b","choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}

data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"qwen2.5:7b","choices":[{"index":0,"delta":{"content":"！"},"finish_reason":"stop"}]}

data: [DONE]
```

## SDK 兼容性

JAiRouter 与 OpenAI SDK 兼容。只需更改基础 URL：

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
  -d '{"model": "qwen2.5:7b", "messages": [{"role": "user", "content": "你好！"}]}'
```

## 健康检查

检查 API 健康状态：

```http
GET /actuator/health
```

**响应：**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

## 下一步

- **[统一 API](universal-api.md)** - OpenAI 兼容端点
- **[管理 API](management-api.md)** - 配置管理
- **[监控 API](monitoring-api.md)** - 健康和指标
- **[OpenAPI 规范](openapi-spec.md)** - 交互式 API 文档
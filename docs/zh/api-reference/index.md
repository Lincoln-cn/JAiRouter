# API 参考

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
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

默认情况下，JAiRouter 不需要认证。对于生产部署，请考虑：

- 带认证的反向代理（nginx、Apache）
- 带认证的 API 网关
- 自定义认证过滤器

## 限流

所有 API 都受配置的限流限制。当超过限制时，您将收到：

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json

{
  "error": {
    "message": "超出限流限制",
    "type": "rate_limit_exceeded",
    "code": "rate_limit_exceeded"
  }
}
```

## 错误处理

JAiRouter 在所有 API 中提供一致的错误响应：

### 标准错误格式

```json
{
  "error": {
    "message": "错误描述",
    "type": "错误类型",
    "code": "错误代码",
    "details": {
      "additional": "信息"
    }
  }
}
```

### 常见错误代码

| HTTP 状态 | 错误类型 | 描述 |
|-----------|----------|------|
| 400 | `invalid_request` | 请求格式错误 |
| 404 | `not_found` | 资源未找到 |
| 429 | `rate_limit_exceeded` | 超出限流限制 |
| 500 | `internal_error` | 内部服务器错误 |
| 503 | `service_unavailable` | 无可用实例 |
| 503 | `circuit_breaker_open` | 熔断器已打开 |

## 内容类型

### 请求内容类型

- `application/json` - JSON 请求（大多数 API）
- `multipart/form-data` - 文件上传（STT、图像 API）

### 响应内容类型

- `application/json` - JSON 响应（大多数 API）
- `audio/*` - 音频文件（TTS API）
- `image/*` - 图像文件（图像生成 API）

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
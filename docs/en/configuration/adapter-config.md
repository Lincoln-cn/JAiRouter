# Adapter Configuration Guide

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-07-30
> **Git Commit**: 8374858b
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

JAiRouter supports adding new adapters through configuration-driven approach, without writing code. This applies to all OpenAI-compatible APIs (such as DeepSeek, Zhipu, Moonshot, etc.).

## Supported Providers

| Provider | API Format | Supported |
|----------|------------|-----------|
| DeepSeek | OpenAI Compatible | ✅ |
| Zhipu (GLM) | OpenAI Compatible | ✅ |
| Moonshot | OpenAI Compatible | ✅ |
| Baichuan | OpenAI Compatible | ✅ |
| Qwen (Tongyi) | OpenAI Compatible | ✅ |
| Minimax | OpenAI Compatible | ✅ |
| Ollama (Local) | Ollama Compatible | ✅ |
| Extend/Override | Based on existing adapter | ✅ |
| Anthropic Claude | Claude Format | ❌ Use built-in `claude` adapter |
| Google Gemini | Gemini Format | ❌ Use built-in `gemini` adapter |

### Cross-protocol streaming: how the body is converted chunk by chunk

When the inbound call is OpenAI-style (`/v1/chat/completions`) but the upstream is Claude or Gemini, the gateway **translates each upstream event** into an OpenAI `chat.completion.chunk` (the text travels in `choices[0].delta.content`). It does not forward the raw upstream events as if they were OpenAI chunks — that produced frames without `choices`, and clients saw an empty body.

| Inbound protocol | Upstream adapter | Streaming body |
| --- | --- | --- |
| OpenAI (`/v1/chat/completions`) | OpenAI-compatible (`normal`, etc.) | Passed through as is |
| OpenAI | `claude` | Translated per chunk into `chat.completion.chunk` |
| OpenAI | `gemini` | Translated per chunk into `chat.completion.chunk` |
| Anthropic (`/v1/messages`) | `claude` | Anthropic native events passed through as is |

What is covered: text deltas and the finish reason (`stop` / `length` / `content_filter`). What is **not** covered yet — verify these if you rely on cross-protocol streaming: `tool_calls` chunks, non-text `parts` (images, audio), per-chunk `usage` reported by upstream (`usage` is still emitted once by the gateway at the end of the stream), and Claude's extended-thinking events.

---

---

## Method 1: Web UI Configuration (Recommended)

### Step 1: Navigate to Adapter Management

1. Login to JAiRouter Admin Panel
2. Click **Configuration → Adapter Management** in the left sidebar

### Step 2: Create New Adapter

Click the **「Create Adapter」** button in the top-right corner, fill in the form:

| Field | Example | Description |
|-------|---------|-------------|
| **Name** | `deepseek` | Unique identifier, referenced in instance config |
| **Type** | `OpenAI Compatible` | Select this for most cloud APIs |
| **Capabilities** | Check `Chat`, `Embedding`, `Streaming` | Based on API capabilities |
| **Header Name** | `Authorization` | Default, no need to change |
| **Header Prefix** | <code>Bearer </code> | Default, note the trailing space |

Click **「Save」** to complete.

### Step 3: Use in Instance Configuration

Navigate to **Configuration → Instance Management**, add or edit an instance:

- Select the created adapter (e.g., `deepseek`) in the **Adapter** dropdown
- Fill in **Base URL** (e.g., `https://api.deepseek.com`)
- Add authentication in **Custom Headers**: `X-API-Key: sk-your-api-key`

### Workflow

```
┌─────────────────────┐     ┌─────────────────────┐
│  1. Adapter Mgmt    │ ──→ │  2. Create adapter  │
│     Page            │     │     e.g. deepseek   │
└─────────────────────┘     └──────────┬──────────┘
                                       │
                                       ▼
┌─────────────────────┐     ┌─────────────────────┐
│  3. Instance Mgmt   │ ──→ │  4. Select adapter  │
│     Page            │     │    Set URL & key    │
└─────────────────────┘     └─────────────────────┘
```

---

## Method 2: YAML Configuration

### Step 1: Edit adapter.yml

Edit file `src/main/resources/config/router/adapter.yml`:

```yaml
model:
  adapter: gpustack  # Global default adapter

adapter-definitions:
  deepseek:
    type: openai-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### Step 2: Use in services.yml

Edit file `src/main/resources/config/router/services.yml`:

```yaml
model:
  services:
    chat:
      adapter: deepseek  # Use the deepseek adapter defined above
      instances:
        - name: deepseek-chat
          baseUrl: https://api.deepseek.com
          path: /v1/chat/completions
          headers:
            Authorization: "Bearer sk-your-api-key-here"
          weight: 1
          status: active
```

### Step 3: Restart Application

```bash
mvn spring-boot:run -Pfast
```

---

## Common Adapter Configuration Examples

### DeepSeek

```yaml
adapter-definitions:
  deepseek:
    type: openai-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### Zhipu GLM

```yaml
adapter-definitions:
  zhipu:
    type: openai-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### Moonshot

```yaml
adapter-definitions:
  moonshot:
    type: openai-compatible
    capabilities:
      chat: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

### Custom Auth Header API

```yaml
adapter-definitions:
  custom-api:
    type: openai-compatible
    capabilities:
      chat: true
      streaming: true
    auth:
      header-name: X-API-Key: # Custom auth header name
      header-prefix: ""          # No prefix
```

### Ollama Compatible (Local Deployment)

```yaml
adapter-definitions:
  my-ollama:
    type: ollama-compatible
    capabilities:
      chat: true
      embedding: true
      streaming: true
    auth:
      header-name: Authorization
      header-prefix: "Bearer "
```

> **Note**: Ollama-compatible adapters use the Ollama API format, suitable for locally deployed Ollama services or other inference engines compatible with the Ollama format.

### Extend/Override (Based on Existing Adapter)

```yaml
adapter-definitions:
  my-deepseek:
    type: extend
    parent: deepseek          # Inherit from deepseek adapter
    capabilities:
      chat: true
      embedding: true
      rerank: true            # Add rerank support
      streaming: true
    auth:
      header-name: X-API-Key: # Override authentication
      header-prefix: ""
    additional-headers:
      X-Custom-Header: "value"
```

> **Note**: Extend/Override mode allows creating variants based on existing adapters, overriding capabilities, authentication, and additional headers.

---

## Verify Configuration

### Via Web UI

1. Navigate to **Developer Tools → AI Playground → Chat Playground**
2. Select the service and model using the new adapter
3. Send a test message

### Via the startup log (recommended)

On startup, `AdapterReferenceValidator` compares `model.adapter`, every service-level `adapter` and every
instance-level `adapter` against the **registered** adapter list once:

```text
✓ adapter 引用校验通过（未发现未注册的 adapter）
```

A typo (for example `openai-compat` missing its `ible`) is reported per finding as
`scope=instance service=chat instance=qwen3:4b adapter=openai-compat`. By default it only logs; set
`jairouter.config.validation.strict-mode=true` to fail the startup instead. See
[validation rules](validation-rules.md).

### At runtime: instances whose adapter is not registered are skipped during selection

The startup check only logs by default, so a bad reference can still enter the runtime configuration
(for example when an instance's `adapter` is edited after startup). Since v3.9.1 the selection stage
drops instances whose effective adapter is not registered, instead of failing later at forward time:

```text
WARN  o.u.m.r.model.ModelServiceRegistry - 跳过 1 个生效 adapter 未注册的实例
     （service=chat, model=qwen3:4b）：qwen3:4b → no-such-adapter；
     已注册清单见 GET /api/config/adapter/list
```

- **Another instance is still usable**: the request is routed to a resolvable instance and you only
  get one extra WARN line plus one counter increment.
- **Every candidate is unresolvable**: the gateway returns `503` with the instance name and the
  adapter name in the message, instead of the misleading "all instances busy" text:

  ```json
  {"error":{"message":"No routable instances for model 'qwen3:4b' in service type 'chat':
   their adapter is not registered (qwen3:4b → no-such-adapter). 请核对 model.services 下的
   adapter 配置，已注册清单见 GET /api/config/adapter/list","code":"503"}}
  ```

Two things to watch from the operations side:

| Signal | Where |
| --- | --- |
| Which instance was skipped, and why | WARN log (`ModelServiceRegistry`), one line per batch with `instance → adapter` |
| How many times it happened | Prometheus metric `jairouter_routing_adapter_unresolvable_total{service,adapter}` |

List the registered adapters with `GET /api/config/adapter/list`. The instance query endpoint
(`GET /api/config/instance/{serviceType}`) also returns `adapterResolved` and `adapterWarning`, so you
can check whether an instance's adapter resolves without editing anything.

### Via API

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-your-api-key" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": false
  }'
```

---

## API Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/config/adapter/list` | GET | List all adapters |
| `/api/config/adapter/{name}` | GET | Get adapter details |
| `/api/config/adapter` | POST | Create adapter |
| `/api/config/adapter/{name}` | PUT | Update adapter |
| `/api/config/adapter/{name}` | DELETE | Delete adapter |
| `/api/config/adapter` | GET | List every supported adapter in the system (`AdapterController:42`) |
| `/api/config/adapter/parents` | GET | Adapters available as inheritance parents (`AdapterConfigController:377`) |
| `/api/config/adapter/{name}/test` | POST | Test an adapter's connectivity; unknown adapter => 404 `ADAPTER_NOT_FOUND` (`AdapterTestController:49`) |

### Create Adapter API Example

```bash
curl -X POST http://localhost:8080/api/config/adapter \
  -H "Content-Type: application/json" \
  -d '{
    "name": "deepseek",
    "type": "openai-compatible",
    "capabilities": {
      "chat": true,
      "embedding": true,
      "streaming": true
    },
    "auth": {
      "headerName": "Authorization",
      "headerPrefix": "Bearer "
    }
  }'
```

---

## Notes

1. **Adapter name**: Only letters, numbers, underscores and hyphens allowed
2. **Built-in adapters cannot be deleted**: `normal`, `claude`, `gemini`, `ollama`, etc.
3. **Configurable adapters can be modified anytime**: Changes take effect immediately
4. **Authentication**: Most cloud APIs use `X-Jairouter-Token: {key}`, no need to change defaults

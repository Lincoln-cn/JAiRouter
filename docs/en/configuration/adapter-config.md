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
- Add authentication in **Custom Headers**: `Authorization: Bearer sk-your-api-key`

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
      header-name: X-API-Key    # Custom auth header name
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
      header-name: X-API-Key  # Override authentication
      header-prefix: ""
    additional-headers:
      X-Custom-Header: "value"
```

> **Note**: Extend/Override mode allows creating variants based on existing adapters, overriding capabilities, authentication, and additional headers.

---

## Verify Configuration

### Via Web UI

1. Navigate to **AI Playground → Chat**
2. Select the service and model using the new adapter
3. Send a test message

### Via API

```bash
curl -X POST http://localhost:9900/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-your-api-key" \
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

### Create Adapter API Example

```bash
curl -X POST http://localhost:9900/api/config/adapter \
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
4. **Authentication**: Most cloud APIs use `Authorization: Bearer {key}`, no need to change defaults

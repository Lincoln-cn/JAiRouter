# Instance Configuration Examples

This document provides configuration examples for common AI services to help users get started quickly.

## Online API Services

### OpenAI GPT

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `gpt-4o` |
| Base URL | `https://api.openai.com` |
| Path | `/v1/chat/completions` |
| Adapter | `normal` |
| Headers | `Authorization: Bearer sk-xxx` |

```
Base URL:     https://api.openai.com
Path:         /v1/chat/completions
Header Name:  Authorization
Header Value: Bearer sk-proj-xxxxx
```

---

### DeepSeek

DeepSeek is compatible with OpenAI API format, use `normal` adapter.

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `deepseek-chat` |
| Base URL | `https://api.deepseek.com` |
| Path | `/v1/chat/completions` |
| Adapter | `normal` |
| Headers | `Authorization: Bearer sk-xxx` |

```
Base URL:     https://api.deepseek.com
Path:         /v1/chat/completions
Header Name:  Authorization
Header Value: Bearer sk-xxxxxxxx
```

---

### Anthropic Claude

Claude uses a dedicated adapter with `x-api-key` header authentication.

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `claude-sonnet` |
| Base URL | `https://api.anthropic.com` |
| Path | `/v1/messages` |
| Adapter | `claude` |
| Headers | `x-api-key: sk-ant-xxx` |

```
Base URL:     https://api.anthropic.com
Path:         /v1/messages
Header Name:  x-api-key
Header Value: sk-ant-api03-xxxxx
```

> **Note**: The `anthropic-version` header is automatically injected by the adapter.

---

### Google Gemini

Gemini is compatible with OpenAI API format, use `normal` adapter.

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `gemini-pro` |
| Base URL | `https://generativelanguage.googleapis.com/v1beta/openai` |
| Path | `/chat/completions` |
| Adapter | `normal` |
| Headers | `Authorization: Bearer xxx` |

```
Base URL:     https://generativelanguage.googleapis.com/v1beta/openai
Path:         /chat/completions
Header Name:  Authorization
Header Value: Bearer AIzaSyxxxxx
```

---

### Alibaba Cloud Bailian (Qwen)

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `qwen-plus` |
| Base URL | `https://dashscope.aliyuncs.com/compatible-mode` |
| Path | `/v1/chat/completions` |
| Adapter | `normal` |
| Headers | `Authorization: Bearer sk-xxx` |

```
Base URL:     https://dashscope.aliyuncs.com/compatible-mode
Path:         /v1/chat/completions
Header Name:  Authorization
Header Value: Bearer sk-xxxxxxxx
```

---

### Tencent Cloud Hunyuan

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `hunyuan-chat` |
| Base URL | `https://api.hunyuan.cloud.tencent.com` |
| Path | `/v1/chat/completions` |
| Adapter | `normal` |
| Headers | `Authorization: Bearer sk-xxx` |

```
Base URL:     https://api.hunyuan.cloud.tencent.com
Path:         /v1/chat/completions
Header Name:  Authorization
Header Value: Bearer sk-xxxxxxxx
```

---

## Local Inference Engines

### Ollama

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `ollama-llama3` |
| Base URL | `http://localhost:11434` |
| Path | `/api/chat` |
| Adapter | `ollama` |
| Headers | None required |

```
Base URL:     http://localhost:11434
Path:         /api/chat
Adapter:      ollama
Headers:      None required
```

---

### vLLM

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `vllm-qwen` |
| Base URL | `http://localhost:8000` |
| Path | `/v1/chat/completions` |
| Adapter | `vllm` |
| Headers | None required (unless API key configured) |

```
Base URL:     http://localhost:8000
Path:         /v1/chat/completions
Adapter:      vllm
Headers:      None required
```

---

### GPUStack

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `gpustack-llama` |
| Base URL | `http://localhost:8080` |
| Path | `/v1/chat/completions` |
| Adapter | `gpustack` |
| Headers | `Authorization: Bearer sk-xxx` (if configured) |

```
Base URL:     http://localhost:8080
Path:         /v1/chat/completions
Adapter:      gpustack
Header Name:  Authorization (optional)
Header Value: Bearer your-api-key (optional)
```

---

### Xinference

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `xinference-chat` |
| Base URL | `http://localhost:9997` |
| Path | `/v1/chat/completions` |
| Adapter | `xinference` |
| Headers | None required |

```
Base URL:     http://localhost:9997
Path:         /v1/chat/completions
Adapter:      xinference
Headers:      None required
```

---

### LocalAI

| Field | Value |
|-------|-------|
| Service Type | `chat` |
| Instance Name | `localai-chat` |
| Base URL | `http://localhost:8080` |
| Path | `/v1/chat/completions` |
| Adapter | `localai` |
| Headers | None required |

```
Base URL:     http://localhost:8080
Path:         /v1/chat/completions
Adapter:      localai
Headers:      None required
```

---

## Adapter Reference

| Adapter | Supported Services | Authentication |
|---------|-------------------|----------------|
| `normal` | OpenAI, DeepSeek, Gemini, Alibaba, Tencent | `Authorization: Bearer` |
| `claude` | Anthropic Claude | `x-api-key` |
| `ollama` | Ollama | None |
| `vllm` | vLLM | None (optional) |
| `gpustack` | GPUStack | `Authorization: Bearer` (optional) |
| `xinference` | Xinference | None |
| `localai` | LocalAI | None |

---

## Embedding Service Examples

### OpenAI Embedding

| Field | Value |
|-------|-------|
| Service Type | `embedding` |
| Instance Name | `text-embedding-3-small` |
| Base URL | `https://api.openai.com` |
| Path | `/v1/embeddings` |
| Adapter | `normal` |
| Headers | `Authorization: Bearer sk-xxx` |

### Ollama Embedding

| Field | Value |
|-------|-------|
| Service Type | `embedding` |
| Instance Name | `nomic-embed-text` |
| Base URL | `http://localhost:11434` |
| Path | `/api/embeddings` |
| Adapter | `ollama` |
| Headers | None required |

---

## FAQ

### Q: How to configure multiple OpenAI instances?

A: Add multiple instances with different names and weights. For example:

```
Instance 1: gpt-4o        Weight 5
Instance 2: gpt-4o-mini   Weight 3
Instance 3: gpt-3.5-turbo Weight 2
```

### Q: Will the API Key in headers be exposed?

A: API Keys are displayed as password fields on the page and transmitted over HTTPS. JAiRouter masks stored keys internally.

### Q: Do I need to manually configure the `anthropic-version` header for Claude?

A: No. When you select the `claude` adapter, `anthropic-version: 2023-06-01` is automatically injected.

### Q: Local inference engines don't require authentication, do I need to configure headers?

A: No. Ollama, vLLM, Xinference and other local services don't require authentication by default, leave headers empty.

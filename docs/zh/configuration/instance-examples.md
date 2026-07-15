# 常用服务配置示例

本文档提供常见 AI 服务的实例配置示例，帮助用户快速上手。

## 在线 API 服务

### OpenAI GPT

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `gpt-4o` |
| 基础 URL | `https://api.openai.com` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer sk-xxx` |

```
基础 URL:    https://api.openai.com
路径:        /v1/chat/completions
请求头名称:  Authorization
请求头值:    Bearer sk-proj-xxxxx
```

---

### DeepSeek

DeepSeek 兼容 OpenAI API 格式，使用 `normal` 适配器。

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `deepseek-chat` |
| 基础 URL | `https://api.deepseek.com` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer sk-xxx` |

```
基础 URL:    https://api.deepseek.com
路径:        /v1/chat/completions
请求头名称:  Authorization
请求头值:    Bearer sk-xxxxxxxx
```

---

### Anthropic Claude

Claude 使用专用适配器，认证方式为 `x-api-key` 头。

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `claude-sonnet` |
| 基础 URL | `https://api.anthropic.com` |
| 路径 | `/v1/messages` |
| 适配器 | `claude` |
| 请求头 | `x-api-key: sk-ant-xxx` |

```
基础 URL:    https://api.anthropic.com
路径:        /v1/messages
请求头名称:  x-api-key
请求头值:    sk-ant-api03-xxxxx
```

> **注意**：`anthropic-version` 头由适配器自动注入，无需手动配置。

---

### Google Gemini

Gemini 兼容 OpenAI API 格式，使用 `normal` 适配器。

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `gemini-pro` |
| 基础 URL | `https://generativelanguage.googleapis.com/v1beta/openai` |
| 路径 | `/chat/completions` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer xxx` |

```
基础 URL:    https://generativelanguage.googleapis.com/v1beta/openai
路径:        /chat/completions
请求头名称:  Authorization
请求头值:    Bearer AIzaSyxxxxx
```

---

### 阿里云百炼（通义千问）

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `qwen-plus` |
| 基础 URL | `https://dashscope.aliyuncs.com/compatible-mode` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer sk-xxx` |

```
基础 URL:    https://dashscope.aliyuncs.com/compatible-mode
路径:        /v1/chat/completions
请求头名称:  Authorization
请求头值:    Bearer sk-xxxxxxxx
```

---

### 腾讯云混元

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `hunyuan-chat` |
| 基础 URL | `https://api.hunyuan.cloud.tencent.com` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer sk-xxx` |

```
基础 URL:    https://api.hunyuan.cloud.tencent.com
路径:        /v1/chat/completions
请求头名称:  Authorization
请求头值:    Bearer sk-xxxxxxxx
```

---

### 百度智能云（文心一言）

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `ernie-4.0` |
| 基础 URL | `https://aip.baidubce.com` |
| 路径 | `/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-4.0-8k` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer access_token` |

> **注意**：百度 API 需要先获取 access_token，不是 API Key 直接使用。

---

## 本地推理引擎

### Ollama

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `ollama-llama3` |
| 基础 URL | `http://localhost:11434` |
| 路径 | `/api/chat` |
| 适配器 | `ollama` |
| 请求头 | 无需认证 |

```
基础 URL:    http://localhost:11434
路径:        /api/chat
适配器:      ollama
请求头:      无需配置
```

---

### vLLM

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `vllm-qwen` |
| 基础 URL | `http://localhost:8000` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `vllm` |
| 请求头 | 无需认证（除非配置了 API Key） |

```
基础 URL:    http://localhost:8000
路径:        /v1/chat/completions
适配器:      vllm
请求头:      无需配置
```

---

### GPUStack

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `gpustack-llama` |
| 基础 URL | `http://localhost:8080` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `gpustack` |
| 请求头 | `Authorization: Bearer sk-xxx`（如果配置了 API Key） |

```
基础 URL:    http://localhost:8080
路径:        /v1/chat/completions
适配器:      gpustack
请求头名称:  Authorization（可选）
请求头值:    Bearer your-api-key（可选）
```

---

### Xinference

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `xinference-chat` |
| 基础 URL | `http://localhost:9997` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `xinference` |
| 请求头 | 无需认证 |

```
基础 URL:    http://localhost:9997
路径:        /v1/chat/completions
适配器:      xinference
请求头:      无需配置
```

---

### LocalAI

| 字段 | 值 |
|------|-----|
| 服务类型 | `chat` |
| 实例名称 | `localai-chat` |
| 基础 URL | `http://localhost:8080` |
| 路径 | `/v1/chat/completions` |
| 适配器 | `localai` |
| 请求头 | 无需认证 |

```
基础 URL:    http://localhost:8080
路径:        /v1/chat/completions
适配器:      localai
请求头:      无需配置
```

---

## 适配器对照表

| 适配器 | 适用服务 | 认证方式 |
|--------|----------|----------|
| `normal` | OpenAI、DeepSeek、Gemini、阿里百炼、腾讯混元 | `Authorization: Bearer` |
| `claude` | Anthropic Claude | `x-api-key` |
| `ollama` | Ollama | 无需认证 |
| `vllm` | vLLM | 无需认证（可选） |
| `gpustack` | GPUStack | `Authorization: Bearer`（可选） |
| `xinference` | Xinference | 无需认证 |
| `localai` | LocalAI | 无需认证 |

---

## Embedding 服务配置示例

### OpenAI Embedding

| 字段 | 值 |
|------|-----|
| 服务类型 | `embedding` |
| 实例名称 | `text-embedding-3-small` |
| 基础 URL | `https://api.openai.com` |
| 路径 | `/v1/embeddings` |
| 适配器 | `normal` |
| 请求头 | `Authorization: Bearer sk-xxx` |

### Ollama Embedding

| 字段 | 值 |
|------|-----|
| 服务类型 | `embedding` |
| 实例名称 | `nomic-embed-text` |
| 基础 URL | `http://localhost:11434` |
| 路径 | `/api/embeddings` |
| 适配器 | `ollama` |
| 请求头 | 无需认证 |

---

## 常见问题

### Q: 如何同时配置多个 OpenAI 实例？

A: 添加多个实例，设置不同的名称和权重即可。例如：

```
实例 1: gpt-4o      权重 5
实例 2: gpt-4o-mini 权重 3
实例 3: gpt-3.5-turbo 权重 2
```

### Q: 请求头中的 API Key 会暴露吗？

A: API Key 在页面上以密码形式显示，在传输过程中使用 HTTPS 加密。JAiRouter 内部存储时会进行脱敏处理。

### Q: Claude 适配器的 `anthropic-version` 头需要手动配置吗？

A: 不需要。选择 `claude` 适配器后，`anthropic-version: 2023-06-01` 会自动注入。

### Q: 本地推理引擎没有认证，需要配置请求头吗？

A: 不需要。Ollama、vLLM、Xinference 等本地服务默认无需认证，请求头留空即可。

# Claude Code Integration

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-09-13
> **Applies To**: v3.1+
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

Since **v3.1**, JAiRouter exposes an **Anthropic Messages protocol endpoint** (`POST /v1/messages`).
Claude Code only needs `ANTHROPIC_BASE_URL` pointed at JAiRouter to connect **with zero code changes**:
the request goes through API Key authentication → quota → rate limiting / circuit breaking →
load balancing to any downstream instance (OpenAI / DeepSeek / vLLM / Ollama / Claude …), and the
response is translated back into Anthropic shape.

| Capability | Endpoint | Description |
|------------|----------|-------------|
| Messages (non-streaming) | `POST /v1/messages` | Downstream native JSON → Anthropic `Message` |
| Messages (streaming) | `POST /v1/messages` (`stream: true`) | Downstream OpenAI-style SSE → **Anthropic event sequence** |
| Token counting | `POST /v1/messages/count_tokens` | Local estimate `{"input_tokens": N}`, **never calls downstream** |
| Model list | `GET /v1/models` | OpenAI-shaped catalog of available models |

> **In one sentence**: `/api/v1/**` (console surface) returns `RouterResponse` envelopes;
> `/v1/**` (native surface) returns **downstream-native / protocol-native** shapes so that clients
> such as Claude Code and the OpenAI SDK can use JAiRouter directly.

## Prerequisites

1. JAiRouter is running (default `http://localhost:8080`).
2. An **API Key** has been created in the console (it authenticates both `/api/**` and `/v1/**`).
3. The target model is registered as an **instance or pool** (service type `chat`),
   e.g. `deepseek-chat` or `qwen2:7b`.
4. Downstream credentials are configured (recommended: in the instance `headers`, see "Authentication Layers").

## Quick Start

```bash
# 1) Gateway URL: ANTHROPIC_BASE_URL=http://<host>:8080 (no /v1 suffix; replace <host> with the gateway host/IP)
export ANTHROPIC_BASE_URL=http://localhost:8080

# 2) Gateway credential (API Key created in the JAiRouter console)
export ANTHROPIC_API_KEY=<JAiRouter API Key>

# 3) A model name registered in JAiRouter (must match the instance/pool name exactly; no aliases)
export ANTHROPIC_MODEL=deepseek-chat

# 4) Start Claude Code
claude
```

PowerShell:

```powershell
$env:ANTHROPIC_BASE_URL = "http://localhost:8080"
$env:ANTHROPIC_API_KEY  = "<JAiRouter API Key>"
$env:ANTHROPIC_MODEL    = "deepseek-chat"
claude
```

Claude Code sends `ANTHROPIC_API_KEY` in the **`x-api-key`** request header. HTTP header names are
case-insensitive, so `x-api-key` and `X-API-Key` are **equivalent** (both verified against the gateway).

> **Important**: inside JAiRouter, `x-api-key` is a **gateway credential**. It is consumed by the
> security layer and **never forwarded downstream**. Downstream AI service keys must be configured
> separately — see the next section.

## Authentication Layers (read this first)

A single `/v1/messages` request passes through **two** authentication layers with completely
different purposes:

| Header | Belongs to | Forwarded downstream | Notes |
|--------|-----------|----------------------|-------|
| `x-api-key` (`X-API-Key` is equivalent) | **Gateway credential** | ❌ **No** | API Key created in the JAiRouter console; used for gateway auth + service permission (`ROLE_CHAT`) |
| `Jairouter_Token` | **Gateway credential (JWT)** | ❌ No | JWT issued by the console/login endpoint; **takes precedence** when both are present |
| `Authorization` | **Downstream credential** | ✅ **Yes** | e.g. `Bearer sk-xxx` (OpenAI/DeepSeek), `Bearer <token>`; the gateway **never consumes** it |
| Instance `headers` | **Downstream credential** | ✅ Injected by gateway | Custom request headers from instance config, used to pass `Authorization` / `x-api-key` etc. downstream |

Three rules to remember:

1. **`x-api-key` = gateway credential, not forwarded downstream.** If you put a *downstream* key in
   `x-api-key`, the downstream receives a request with **no credentials** → **401 / unauthenticated failure**.
2. **Configure the downstream key in instance `headers` (recommended), or send it per request via
   `Authorization: Bearer <downstream key>`.** When both are present, **instance `headers` win**
   (instance configuration overrides the incoming `Authorization`).
3. **Credential precedence**: `Jairouter_Token` (JWT) > `X-API-Key` (API Key). `Authorization` is
   unrelated to gateway authentication; it only affects the downstream.

> **Native-surface limitation (current version)**: the service call path behind `/v1/**` only accepts
> **API Key authentication** (`ApiKeyAuthentication`). A native-surface request carrying only
> `Jairouter_Token` (no `x-api-key`) gets an **empty response** and cannot reach any downstream.
> For Claude Code, always use `ANTHROPIC_API_KEY` (i.e. `x-api-key`). The console surface `/api/**`
> is not affected.

## Three Ways to Configure Downstream Credentials

| Option | Where the downstream key lives | Use case | Pros | Notes |
|--------|-------------------------------|----------|------|-------|
| **A (recommended)** | Instance `headers` | Production / shared gateway | Keys stay centralised; clients hold nothing; instance config wins | Switch downstream keys by editing config only |
| B | Request `Authorization: Bearer <downstream key>` | Trials / per-request keys | No gateway config change | Claude Code can only attach it via `ANTHROPIC_CUSTOM_HEADERS` etc.; only one `Authorization` slot exists |
| C | Instance `headers` with a custom header name (e.g. `api-key`) | Downstream is not `Authorization`-style | Works with any downstream protocol | Verify which header the downstream expects |

### Option A: downstream key in instance `headers` (recommended)

```bash
# DeepSeek example: the downstream key lives in the instance headers (injected by the gateway)
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <JAiRouter API Key>" \
  -d '{
    "name": "deepseek-chat",
    "baseUrl": "https://api.deepseek.com",
    "path": "/v1/chat/completions",
    "adapter": "normal",
    "headers": {
      "Authorization": "Bearer <DeepSeek API Key>"
    }
  }'
```

Claude Code then only needs `ANTHROPIC_API_KEY=<JAiRouter API Key>`; the client neither stores nor
sends the downstream key.

### Option B: send the downstream key per request (pass-through `Authorization`)

Leave the instance config untouched and let the client carry the downstream key:

```bash
curl -N -X POST "http://localhost:8080/v1/messages" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -H "Authorization: Bearer <DeepSeek API Key>" \
  -d '{"model":"deepseek-chat","max_tokens":64,"messages":[{"role":"user","content":"hi"}]}'
```

If the instance already defines `Authorization`, the **instance configuration wins** (the request
header is overridden).

### Option C: custom downstream header

```bash
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <JAiRouter API Key>" \
  -d '{
    "name": "claude-sonnet",
    "baseUrl": "https://api.anthropic.com",
    "path": "/v1/messages",
    "adapter": "claude",
    "headers": {
      "x-api-key": "<Anthropic API Key>"
    }
  }'
```

> Note: this `x-api-key` lives in the **instance `headers`** and targets the downstream Anthropic API.
> It is unrelated to the `x-api-key` the client sends to the **gateway**. With the `claude` adapter,
> the `anthropic-version` header is injected automatically.

## Endpoint Examples

All examples use `x-api-key: <JAiRouter API Key>` as the gateway credential.

### Non-streaming messages

```bash
curl -X POST "http://localhost:8080/v1/messages" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "deepseek-chat",
    "max_tokens": 128,
    "system": "You are a concise assistant",
    "messages": [{"role": "user", "content": "Introduce yourself in one sentence"}]
  }'
```

Response (Anthropic `Message` shape):

```json
{
  "id": "chatcmpl-xxxxxxxx",
  "type": "message",
  "role": "assistant",
  "model": "deepseek-chat",
  "content": [{"type": "text", "text": "I am an AI assistant ..."}],
  "stop_reason": "end_turn",
  "stop_sequence": null,
  "usage": {"input_tokens": 10, "output_tokens": 15}
}
```

`usage.input_tokens` / `output_tokens` come from the downstream `usage`; they are 0 when the
downstream does not provide it.

### Streaming messages

```bash
curl -N -X POST "http://localhost:8080/v1/messages" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "deepseek-chat",
    "max_tokens": 128,
    "stream": true,
    "messages": [{"role": "user", "content": "Count to three"}]
  }'
```

`Content-Type: text/event-stream;charset=UTF-8`. The event sequence (each event = an
`event:<name>` line + a `data:<json>` line + a blank line; the SSE spec allows one space after the
colon, the gateway emits none, and clients accept both):

| Order | Event | Payload highlights |
|-------|-------|--------------------|
| 1 | `message_start` | `message.id` (gateway-generated `msg_*`), `model` (requested model), `content: []`, `stop_reason: null`, `usage.input_tokens` (estimated), `usage.output_tokens: 0` |
| 2 | `content_block_start` | `index: 0`, `content_block: {type:"text", text:""}` |
| 3…N | `content_block_delta` | `index: 0`, `delta: {type:"text_delta", text:"…"}` |
| N+1 | `content_block_stop` | `index: 0` |
| N+2 | `message_delta` | `delta: {stop_reason:"end_turn"\|"max_tokens", stop_sequence:null}`, `usage.output_tokens` (downstream usage preferred, otherwise estimated from accumulated text) |
| N+3 | `message_stop` | `{type:"message_stop"}` |

Real output sample (produced by the actual serializer, 7 events; the model returned "1" then "、2、3"):

```text
event:message_start
data:{"type":"message_start","message":{"id":"msg_4976714626ba4577a486e0772f604a87","type":"message","role":"assistant","model":"deepseek-chat","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":6,"output_tokens":0}}}

event:content_block_start
data:{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event:content_block_delta
data:{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"1"}}

event:content_block_delta
data:{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"、2、3"}}

event:content_block_stop
data:{"type":"content_block_stop","index":0}

event:message_delta
data:{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":2}}

event:message_stop
data:{"type":"message_stop"}
```

When the downstream fails mid-stream (HTTP 200 already established, SSE already started) the gateway
closes with `event:error` so the client does not just see a truncated stream:

```text
event:error
data:{"type":"error","error":{"type":"api_error","message":"…"}}
```

`error.type` is mapped from the downstream status: `401/403 → authentication_error`,
`429 → rate_limit_error`, `400/404/422 → invalid_request_error`, otherwise `api_error`.

### Token counting

```bash
curl -X POST "http://localhost:8080/v1/messages/count_tokens" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -d '{
    "model": "deepseek-chat",
    "system": "You are a concise assistant",
    "messages": [{"role": "user", "content": "Introduce yourself in one sentence"}]
  }'
```

```json
{"input_tokens": 10}
```

- The body is the same as `/v1/messages`, but `max_tokens` / `stream` / `tools` are **ignored**: only
  the **text blocks** in `system` and `messages` are counted.
- The value is **estimated locally** by the gateway (ideographic characters ≈ 2 chars/token, other
  non-whitespace ≈ 4 chars/token). No downstream call, no quota consumption, and it is **the same
  estimator** used for the streaming `message_start.usage.input_tokens`.
- The estimate is for budgeting/trimming; it is **not** the official Anthropic tokenizer output.

### Model catalog

```bash
curl -H "x-api-key: <JAiRouter API Key>" "http://localhost:8080/v1/models"
```

Returns the OpenAI shape `{"object":"list","data":[{"id":"deepseek-chat","object":"model",…}]}`;
each `id` can be used as the `model` field.

## Current Limitations

| Limitation | Description | Workaround |
|------------|-------------|------------|
| **`tools` are not mapped downstream** | `tools` / `tool_choice` in the request are **ignored** (logged at debug level), so Claude Code's **tool use / file editing / Bash execution are unavailable** — plain text chat only | Use this endpoint for text Q&A; connect to Anthropic directly when tool use is required |
| **`model` must be a registered model name** | No aliases, no automatic routing prefixes; an unregistered name fails instance selection (404/503) | List `id`s via `GET /v1/models`, or map pool names such as `auto-model` in the console |
| **Error body shapes differ between surfaces** | Gateway errors (401 auth failure, 429 quota/rate limit, 5xx) use the **`RouterResponse`** shape `{"success":false,"message":"…","errorCode":"…","timestamp":"…"}` on **both** `/v1` and `/api`; only the Anthropic endpoint's **parameter-validation** errors use the Anthropic shape `{"type":"error","error":{…}}`; non-2xx downstream responses are **passed through** verbatim | Detect the shape by the presence of the `type` field |
| **`x-api-key` is never forwarded** | See "Authentication Layers": downstream keys must go to instance `headers` or `Authorization` | Option A / B |
| **Native surface only accepts API Key auth** | Requests carrying only `Jairouter_Token` cannot reach downstream on `/v1/**` (empty response) | Always send `x-api-key` |
| **Streaming `message_start.id` / `model` come from the gateway** | `id` is a gateway-generated `msg_*` (the event is emitted before the first downstream chunk, so the downstream id is unknown yet); `model` is the **requested** model | Use the non-streaming endpoint when the real downstream `id` matters (it is passed through) |
| **Single text content block** | Streaming emits exactly one text block at `index: 0`; no `thinking` / `tool_use` blocks | — |
| **Response-cache hit (non-streaming)** | With `jairouter.response-cache.enabled=true`, a cache hit on the native surface (`/v1/**`) returns **native JSON** (fixed in v3.1 — no console envelope); the console surface (`/api/**`) still returns the `RouterResponse` envelope | Streaming requests skip the cache by default (`skip-streaming: true`); with `false`, a hit is replayed chunk-by-chunk into the event sequence |
| **`count_tokens` is an estimate** | It excludes `tools` JSON Schema, image blocks, and downstream tokenizer differences | Treat it as an upper-bound reference |

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `401 Unauthorized` | `x-api-key` is not a valid JAiRouter API Key, or a **downstream** key was put in `x-api-key` | Create/copy the console API Key; move the downstream key to instance `headers` or `Authorization` |
| Request succeeds but downstream returns 401/403 | Downstream key missing or expired (`x-api-key` is not forwarded) | Check the instance `headers`; verify with `/api/config/instance/type/chat` |
| `404` / `503` (model unavailable) | `model` not registered, instance unhealthy, or service circuit-broken | Cross-check the model name with `GET /v1/models`; check instance health and circuit breaker |
| Claude Code reports an "empty response" / no output | Only `Jairouter_Token` was sent, without `x-api-key` (native surface only accepts API Key auth) | Set `ANTHROPIC_API_KEY` |
| Tool use does not work | `tools` are not mapped downstream | See "Current Limitations" |
| `400 invalid_request_error: model 为必填字段` | The request body has no `model` | Add `model` or set `ANTHROPIC_MODEL` |
| Streaming response has no `event:` lines | Upstream/intermediate proxy buffered the SSE stream | Use `curl -N` (disable buffering); turn off response buffering on reverse proxies |
| Claude Code prints `[claude-code:unrecognized_model]` (e.g. `"deepseek-chat" isn't described by this version's model catalog`) | That Claude Code build's built-in model catalog does not know a custom model name, so auto-compact assumes the default 200k window | **It does not affect responses** (verified: exit 0 with normal text output); declare the real window, e.g. `CLAUDE_CODE_MAX_CONTEXT_TOKENS=65536`; only if needed (e.g. names using the `[1m]` suffix) consider `CLAUDE_CODE_DISABLE_UNKNOWN_MODEL_WINDOW_ENFORCEMENT=1` |

## Related Documentation

- [Quick Start](quick-start.md) — start the gateway, configure your first instance
- [API Key Management](../security/api-key-management.md) — create and rotate gateway credentials
- [Instance Configuration Examples](../configuration/instance-examples.md) — downstream templates (including the Claude adapter)
- [Universal API](../api-reference/universal-api.md) — console surface `/api/v1/**`
- [Error Codes](../api-reference/error-codes.md) — gateway error codes

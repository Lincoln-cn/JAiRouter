# Claude Code 接入

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-09-13
> **适用版本**: v3.1+
> **Git 提交**: -
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 从 **v3.1** 起提供 **Anthropic Messages 协议入口**（`POST /v1/messages`），Claude Code 只需把
`ANTHROPIC_BASE_URL` 指向 JAiRouter 即可**零改造直连**：请求经网关的 API Key 鉴权 → 配额 → 限流/熔断 →
负载均衡落到任意下游实例（OpenAI / DeepSeek / vLLM / Ollama / Claude …），响应再翻译回 Anthropic 形状。

| 能力 | 端点 | 说明 |
|------|------|------|
| 对话（非流式） | `POST /v1/messages` | 下游原生 JSON → Anthropic `Message` |
| 对话（流式） | `POST /v1/messages`（`stream: true`） | 下游 OpenAI 风格 SSE → **Anthropic 事件序列** |
| Token 计数 | `POST /v1/messages/count_tokens` | 本地估算 `{"input_tokens": N}`，**不触达下游** |
| 模型列表 | `GET /v1/models` | OpenAI 形状的可用模型目录 |

> **一句话理解**：`/api/v1/**`（控制台面）返回 `RouterResponse` 包裹体；`/v1/**`（原生面）返回**下游原生/协议原生**形状，供 Claude Code、OpenAI SDK 等客户端直接使用。

## 前置条件

1. JAiRouter 已启动（默认 `http://localhost:8080`）。
2. 已在控制台创建 **API Key**（`/api/**` 与 `/v1/**` 都用它做网关鉴权）。
3. 目标模型已注册为**实例或池**（chat 服务类型），例如 `deepseek-chat`、`qwen2:7b`。
4. 下游 AI 服务的凭据已配置好（推荐放在实例 `headers` 里，见下文「认证分层」）。

## 快速开始

```bash
# 1) 网关地址：ANTHROPIC_BASE_URL=http://<host>:8080（不要带 /v1 后缀；<host> 换成网关所在主机/IP）
export ANTHROPIC_BASE_URL=http://localhost:8080

# 2) 网关凭据（JAiRouter 控制台创建的 API Key）
export ANTHROPIC_API_KEY=<JAiRouter API Key>

# 3) 使用已在 JAiRouter 注册的模型名（必须与实例/池名称一致，无别名）
export ANTHROPIC_MODEL=deepseek-chat

# 4) 启动 Claude Code
claude
```

PowerShell：

```powershell
$env:ANTHROPIC_BASE_URL = "http://localhost:8080"
$env:ANTHROPIC_API_KEY  = "<JAiRouter API Key>"
$env:ANTHROPIC_MODEL    = "deepseek-chat"
claude
```

Claude Code 会把 `ANTHROPIC_API_KEY` 放进 **`x-api-key`** 请求头。HTTP 头名本身大小写不敏感，
所以 `x-api-key` 与 `X-API-Key` **等价**（网关侧均已实测可用）。

> **重要**：`x-api-key` 在 JAiRouter 里是**网关凭据**，由安全层消费后**不会转发给下游**。
> 下游 AI 服务的密钥必须另行配置，见下一节。

## 认证分层（务必先读）

一次 `/v1/messages` 请求会经过**两层**认证，两个密钥的用途完全不同：

| 请求头 | 归属 | 是否转发下游 | 说明 |
|--------|------|--------------|------|
| `x-api-key`（`X-API-Key` 同义） | **网关凭据** | ❌ **不转发** | JAiRouter 控制台创建的 API Key，用于网关鉴权 + 服务权限（`ROLE_CHAT`） |
| `Jairouter_Token` | **网关凭据（JWT）** | ❌ 不转发 | 控制台/登录接口签发的 JWT；与 `x-api-key` 同时存在时 **JWT 优先** |
| `Authorization` | **下游凭据** | ✅ **透传** | 例如 `Bearer sk-xxx`（OpenAI/DeepSeek）、`Bearer <token>`；网关**从不消费**它 |
| 实例 `headers` | **下游凭据** | ✅ 由网关注入 | 实例配置里的自定义请求头，用于给下游带 `Authorization` / `x-api-key` 等 |

三条必须记住的规则：

1. **`x-api-key` = 网关凭据，不会透传下游。** 把*下游*密钥放在 `x-api-key` 上，下游会收到**没有任何凭据**的请求 → **401 / 无凭据失败**。
2. **下游密钥配在实例 `headers`（推荐），或由客户端用 `Authorization: Bearer <下游密钥>` 按请求携带。** 两者同时存在时**实例级 `headers` 优先**（实例配置覆盖请求传入的 `Authorization`）。
3. **认证优先级**：`Jairouter_Token`（JWT） > `X-API-Key`（API Key）。`Authorization` 与网关认证**无关**，它只影响下游。

> **原生面限制（当前版本）**：`/v1/**` 的服务调用链只放行 **API Key 认证**（`ApiKeyAuthentication`）。
> 只带 `Jairouter_Token`、不带 `x-api-key` 的原生面请求会拿到**空响应**（无内容），无法调用下游。
> 请在 Claude Code 场景下始终使用 `ANTHROPIC_API_KEY`（即 `x-api-key`）。控制台面 `/api/**` 不受此限制。

## 三种下游凭据配置方案

| 方案 | 下游密钥放哪 | 适用场景 | 优点 | 注意 |
|------|--------------|----------|------|------|
| **A（推荐）** | 实例 `headers` | 生产/多用户共享网关 | 密钥集中管理，客户端无需持有下游密钥；实例级优先 | 改配置即可切换下游密钥 |
| B | 请求 `Authorization: Bearer <下游密钥>` | 临时试用 / 每请求换密钥 | 无需改网关配置 | Claude Code 只能通过 `ANTHROPIC_CUSTOM_HEADERS` 等方式附加；且只有 `Authorization` 一个槽位，无法区分多个下游 |
| C | 实例 `headers` 用自定义头名（如 `api-key`） | 下游不是 `Authorization` 风格 | 兼容任意下游协议 | 需确认下游认哪个头名 |

### 方案 A：下游密钥配在实例 `headers`（推荐）

```bash
# 以 DeepSeek 为例：下游密钥配置在实例 headers 上（网关注入，客户端不持有）
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

之后 Claude Code 只需 `ANTHROPIC_API_KEY=<JAiRouter API Key>` 即可，客户端**不保存也不发送**下游密钥。

### 方案 B：按请求携带下游密钥（透传 `Authorization`）

不改实例配置，客户端自己带下游密钥：

```bash
curl -N -X POST "http://localhost:8080/v1/messages" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -H "Authorization: Bearer <DeepSeek API Key>" \
  -d '{"model":"deepseek-chat","max_tokens":64,"messages":[{"role":"user","content":"hi"}]}'
```

实例若已配置 `Authorization`，则以**实例配置为准**（请求头被覆盖）。

### 方案 C：自定义下游请求头

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

> 注意：这里的 `x-api-key` 位于**实例 `headers`**，是给下游 Anthropic 用的；它与客户端发给**网关**的
> `x-api-key` 是两回事，互不干扰。选择 `claude` 适配器时 `anthropic-version` 头会自动注入。

## 接口示例

以下示例统一使用 `x-api-key: <JAiRouter API Key>` 作为网关凭据。

### 非流式对话

```bash
curl -X POST "http://localhost:8080/v1/messages" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "deepseek-chat",
    "max_tokens": 128,
    "system": "你是一个简洁的助手",
    "messages": [{"role": "user", "content": "用一句话介绍你自己"}]
  }'
```

响应（Anthropic `Message` 形状）：

```json
{
  "id": "chatcmpl-xxxxxxxx",
  "type": "message",
  "role": "assistant",
  "model": "deepseek-chat",
  "content": [{"type": "text", "text": "我是一个 AI 助手……"}],
  "stop_reason": "end_turn",
  "stop_sequence": null,
  "usage": {"input_tokens": 10, "output_tokens": 15}
}
```

`usage.input_tokens` / `output_tokens` 来自下游 `usage`；下游未提供时为 0。

### 流式对话

```bash
curl -N -X POST "http://localhost:8080/v1/messages" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "deepseek-chat",
    "max_tokens": 128,
    "stream": true,
    "messages": [{"role": "user", "content": "数到三"}]
  }'
```

`Content-Type: text/event-stream;charset=UTF-8`，事件序列如下（每事件 = `event:<name>` 行 +
`data:<json>` 行 + 空行；SSE 规范允许冒号后跟一个空格，网关实际输出不带空格，客户端两种都能解析）：

| 顺序 | 事件 | 载荷要点 |
|------|------|----------|
| 1 | `message_start` | `message.id`（网关生成 `msg_*`）、`model`（请求侧模型名）、`content: []`、`stop_reason: null`、`usage.input_tokens`（估算）、`usage.output_tokens: 0` |
| 2 | `content_block_start` | `index: 0`、`content_block: {type:"text", text:""}` |
| 3…N | `content_block_delta` | `index: 0`、`delta: {type:"text_delta", text:"…"}` |
| N+1 | `content_block_stop` | `index: 0` |
| N+2 | `message_delta` | `delta: {stop_reason:"end_turn"\|"max_tokens", stop_sequence:null}`、`usage.output_tokens`（下游 usage 优先，否则按累计文本估算） |
| N+3 | `message_stop` | `{type:"message_stop"}` |

实际输出样例（真实序列化器产出，7 个事件；模型回包 "1" + "、2、3"）：

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

流中途失败（HTTP 200 已建立、SSE 已开始）时，网关以 `event:error` 收尾，避免客户端只看到「流被截断」：

```text
event:error
data:{"type":"error","error":{"type":"api_error","message":"…"}}
```

其中 `error.type` 依下游状态映射：`401/403 → authentication_error`、`429 → rate_limit_error`、
`400/404/422 → invalid_request_error`，其余为 `api_error`。

### Token 计数

```bash
curl -X POST "http://localhost:8080/v1/messages/count_tokens" \
  -H "Content-Type: application/json" \
  -H "x-api-key: <JAiRouter API Key>" \
  -d '{
    "model": "deepseek-chat",
    "system": "你是一个简洁的助手",
    "messages": [{"role": "user", "content": "用一句话介绍你自己"}]
  }'
```

```json
{"input_tokens": 10}
```

- 入参与 `/v1/messages` 相同，但**忽略 `max_tokens` / `stream` / `tools`**：只统计 `system` 与
  `messages` 中的**文本块**体量。
- 由网关**本地估算**（表意文字约 2 字符/token、其余非空白约 4 字符/token），不触达下游、不消耗配额，
  与流式 `message_start.usage.input_tokens` **同源**。
- 估算值用于预算/裁剪，**不等于** Anthropic 官方分词结果。

### 模型目录

```bash
curl -H "x-api-key: <JAiRouter API Key>" "http://localhost:8080/v1/models"
```

返回 OpenAI 形状 `{"object":"list","data":[{"id":"deepseek-chat","object":"model",…}]}`，
`id` 即可用于 `model` 字段。

## 当前限制

| 限制 | 说明 | 规避方式 |
|------|------|----------|
| **`tools` 不映射下游** | 请求中的 `tools` / `tool_choice` 会被**忽略**（记 debug 日志），因此 Claude Code 的**工具调用 / 文件编辑 / Bash 执行等能力不可用**，仅纯文本对话可用 | 仅把本入口用于纯文本问答；需要工具能力时直连 Anthropic |
| **`model` 必须是已注册模型名** | 无别名、无自动路由前缀；未注册的模型名会导致实例选择失败（404/503） | 用 `GET /v1/models` 获取可用 `id`，或在控制台为 `auto-model` 等池名建好映射 |
| **错误体形态两面不同** | 网关自身错误（401 鉴权失败、429 配额/限流、5xx）在 `/v1` 与 `/api` 两面上都是 **`RouterResponse`** 形状 `{"success":false,"message":"…","errorCode":"…","timestamp":"…"}`；只有 Anthropic 入口的**参数校验错误**用 Anthropic 形状 `{"type":"error","error":{…}}`；下游非 2xx 则**原样透传**下游错误体 | 客户端按 `type` 字段是否存在判定形态 |
| **`x-api-key` 不下发** | 见「认证分层」：下游密钥必须走实例 `headers` 或 `Authorization` | 方案 A/B |
| **原生面仅支持 API Key 认证** | 仅 `Jairouter_Token` 的请求在 `/v1/**` 上无法调用下游（空响应） | 始终带 `x-api-key` |
| **流式 `message_start.id` / `model` 由网关给出** | `id` 为网关生成的 `msg_*`（该事件先于首个下游块发出，下游 id 尚不可知）；`model` 为**请求侧**模型名 | 需要下游真实 id 时使用非流式（透传下游 `id`） |
| **单文本内容块** | 流式仅产出 `index: 0` 的一个文本块，不产出 `thinking` / `tool_use` 块 | — |
| **响应缓存命中（非流式）** | 开启 `jairouter.response-cache.enabled=true` 且命中时，原生面（`/v1/**`）返回**原生 JSON**（v3.1 起修复，不再返回控制台面包裹体）；控制台面（`/api/**`）仍为 `RouterResponse` 包裹体 | 流式请求默认不参与缓存（`skip-streaming: true`）；置 `false` 时缓存命中按逐块回放重放为事件序列 |
| **`count_tokens` 为估算** | 不含 `tools` JSON Schema、图片块与下游真实分词差异 | 视作上界参考 |

## 故障排查

| 现象 | 原因 | 处理 |
|------|------|------|
| `401 Unauthorized` | `x-api-key` 不是有效的 JAiRouter API Key，或误把**下游密钥**放在了 `x-api-key` | 生成/复制控制台的 API Key；下游密钥改到实例 `headers` 或 `Authorization` |
| 请求成功但下游报 401/403 | 下游密钥未配置或失效（`x-api-key` 不转发下游） | 检查实例 `headers`；用 `/api/config/instance/type/chat` 确认配置 |
| `404` / `503`（模型不可用） | `model` 未注册、实例不健康或服务被熔断 | `GET /v1/models` 核对模型名；检查实例健康状态与熔断器 |
| Claude Code 报「空响应」/无输出 | 只带了 `Jairouter_Token` 而未带 `x-api-key`（原生面只放行 API Key 认证） | 设置 `ANTHROPIC_API_KEY` |
| 工具调用不生效 | `tools` 不映射下游 | 见「当前限制」 |
| `400 invalid_request_error: model 为必填字段` | 请求体缺 `model` | 补上 `model` 或设置 `ANTHROPIC_MODEL` |
| 流式响应无 `event:` 行 | 上游/中间代理缓冲了 SSE | 使用 `curl -N`（禁用缓冲）；反向代理关闭 response buffering |
| Claude Code 提示 `[claude-code:unrecognized_model]`（如 `"deepseek-chat" isn't described by this version's model catalog`） | 该 Claude Code 版本的内置模型目录不认识自定义模型名，自动压缩会按默认 200k 窗口估算 | **不影响正常回答**（实测 exit=0 并正常产出文本）；把真实窗口显式告知，如 `CLAUDE_CODE_MAX_CONTEXT_TOKENS=65536`；必要时（模型名带 `[1m]` 后缀等场景）再考虑 `CLAUDE_CODE_DISABLE_UNKNOWN_MODEL_WINDOW_ENFORCEMENT=1` |

## 相关文档

- [快速开始](quick-start.md) — 启动网关、配置第一个实例
- [API Key 管理](../security/api-key-management.md) — 创建与轮换网关凭据
- [常用服务配置示例](../configuration/instance-examples.md) — 各下游（含 Claude 适配器）配置模板
- [统一 API](../api-reference/universal-api.md) — 控制台面 `/api/v1/**` 接口说明
- [错误码对照表](../api-reference/error-codes.md) — 网关错误码

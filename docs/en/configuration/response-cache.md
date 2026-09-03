# Response Cache

<!-- 版本信息 -->
> **Doc Version**: 1.1.0
> **Last Updated**: 2026-09-04
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

Starting with **v2.9.9**, JAiRouter ships a **response cache**: it caches the complete downstream LLM response under a cache key and, when an identical request arrives again, serves the cached result directly while **skipping the downstream call** — cutting latency and cost. **v2.9.10** extends it with three capabilities: streaming SSE caching, an invalidation API (by service / model / bulk), and a three-part cache key with optimized rate-limiting short-circuit semantics.

- **Off by default (opt-in)**: `jairouter.response-cache` is disabled unless enabled; enabling it does not affect the behavior of non-hit requests
- **Stacks with the v2.9.0 prefix cache (prefix cache / KV cache)**: the two solve different problems —
  - Prefix cache: still calls downstream but reuses its prefill computation, reducing first-token latency (TTFT) and prefill cost
  - Response cache: **skips the downstream entirely** for byte-identical requests, serving cached content directly
- A cache hit consumes no downstream token quota; miss / cacheSalt-bypass behavior is identical to running with the cache off

## Quick Start

```yaml
jairouter:
  response-cache:
    enabled: true        # enable the response cache (default false, opt-in)
    ttl: 1h              # cache TTL (default 1h, aligned with liteLLM)
    max-size: 10000      # maximum number of cached entries (default 10000)
```

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `false` | Master switch; when off, cache reads and writes short-circuit with zero overhead |
| `ttl` | `1h` | Cache entry TTL (aligned with the liteLLM default) |
| `max-size` | `10000` | Maximum number of cached entries; beyond that, Caffeine's capacity policy evicts entries automatically |
| `only-deterministic` | `true` | Cache only deterministic requests (see below) |
| `skip-streaming` | `true` | Skip streaming requests; set to `false` to enable streaming caching (v2.9.10+) |

It takes effect as soon as it is enabled — no restart or extra setup needed. The cache is a **purely additive capability**: non-hit requests behave exactly as when it is off.

## Eligible Requests

### P0 Non-Streaming (v2.9.9)

| Service | Cacheable when | Notes |
|---------|----------------|-------|
| `chat` (non-streaming) | `temperature` is `0` or `null` **and** `n` is `1` or `null` | Sampling is deterministic and reproducible; all other parameters (messages, etc.) enter the cache key |
| `embedding` | Always (deterministic by nature) | Cached directly |
| `rerank` | Always (deterministic by nature) | Cached directly |
| `image` / `TTS` / `STT` | Never | Binary / non-P0 services are excluded from the cache |

- With `only-deterministic: true` (default), chat requests with `temperature > 0` or `n > 1` are not cached (each generation differs, so caching adds no value)

### P1 Streaming Cache (v2.9.10)

| Service | Cacheable when | Notes |
|---------|----------------|-------|
| `chat` (streaming, `stream=true`) | `skip-streaming: false` **and** `temperature` is `0` or `null` **and** `n` is `1` or `null` | Requires explicitly disabling `skip-streaming`; same determinism constraints as non-streaming |

- With `skip-streaming: true` (default), streaming requests get no cache key and neither read nor write the cache
- Streaming and non-streaming cache keys are natively separated into distinct buckets — no cross-contamination

## Cache Key and Tenant Isolation

The cache key uses a **three-part** format (v2.9.10), balancing readable prefixes (for prefix-based invalidation) with content obfuscation:

```
rc:{serviceType}:{model}:{sha256}
```

- **Readable prefix**: `serviceType` (e.g. `chat`, `embedding`) and `model` (e.g. `gpt-4o`) are stored in plaintext, enabling the invalidation API to match and clear entries by prefix
- **SHA-256 digest segment**: a SHA-256 hash of the normalized request body — **holds no plaintext message content**

SHA-256 digest input:

```
cache key = SHA-256(tenantKey | [user?] | serviceType | model | canonicalJson(requestBody))
```

- **Tenant isolation**: `tenantKey` = API Key ID (`apiKeyId`), falling back to the client IP (`clientIp`) when there is no API key. Identical requests from different tenants get different keys and never share cache entries — preventing cross-tenant leakage
- **Optional `user` segment**: chat / embedding requests carrying an optional `user` join the key when it is non-empty; when empty, caching degrades to API-key (tenant) granularity
- **Normalized request body**: fields serialized in a fixed order, strings trimmed of whitespace, nested objects sorted, lists kept in order — key ordering or whitespace differences do not fragment the cache
- **cacheSalt bypass**: when chat / embedding requests pass a non-empty `options.cacheSalt`, the request **neither reads nor writes** the cache and goes straight downstream (for cases that need a forced-fresh result)
- **Conversation semantics**: the gateway has no sessionId — clients carry the full conversation history in `messages`, which fully participates in the cache key, so different contexts are distinguished naturally:
  - Repeated requests within the same session (identical messages) → cache hit, as expected
  - Different sessions hit only when requests are byte-identical, so deterministic requests cannot cross-pollute contexts
- **Generic greetings need no special handling**: single-turn high-frequency greetings (hello / what can you do) naturally hit often; staleness is bounded by TTL plus cacheSalt; semantic-level reuse (normalizing hello vs hi) is under later semantic-cache evaluation

## Cache Hit Semantics

- **Consistent response shape**: a cache hit returns a `RouterResponse` isomorphic to a normal non-streaming success response (`data` holds the cached downstream data) — transparent to callers
- **Hits do not produce call-history records**: cache-hit requests short-circuit before the adapter executes (call history is recorded on the downstream execution path), so they are **not written to call history** — only the hit counter is incremented; use `jairouter_response_cache_hits_total` when auditing hit requests
- **No downstream token quota consumed**: hits skip the downstream call entirely and generate no new downstream usage
- **Rate limiting semantics** (updated in v2.9.10): the cache read is now moved ahead of instance selection (`selectInstance`) — before the cache lookup, **service-level rate limiting is explicitly executed** (one deduction); a hit short-circuits immediately, skipping instance selection; a 429 from rate limiting **takes priority over the cache** (hard boundary); a miss proceeds through the full `selectInstance` (internal rate limiting is skipped to avoid double deduction)
- **Metrics** (tags: `service`, `model`):

| Metric | Type | Description |
|--------|------|-------------|
| `jairouter_response_cache_hits_total` | Counter | Cumulative cache hits |
| `jairouter_response_cache_misses_total` | Counter | Cumulative cache misses |
| `jairouter_response_cache_hit_ratio` | Gauge | Hit ratio 0.0~1.0 (cumulative hit/(hit+miss)) |

## Streaming Cache

v2.9.10 supports caching `stream=true` chat requests, subject to the following conditions:

- `skip-streaming: false` (explicitly enabled — **defaults to `true`, skipping streaming**)
- With `only-deterministic: true` (default), `temperature` must be `0` or `null` and `n` must be `1` or `null`

### Storage

Streaming responses are written to the cache after the full stream completes. The cached value is the list of transform-ed per-chunk SSE data strings, `model`, `usage`, and `finish_reason`, preserving the original SSE framing boundaries.

### Replay

On a cache hit, the response is replayed as a **raw SSE stream** (`Content-Type: text/event-stream`), with a `[DONE]` marker appended at the end — matching the shape of a normal streaming response, without `RouterResponse` JSON wrapping.

### Bucket Separation

Streaming (`stream=true`) and non-streaming (`stream=false`) cache keys are natively placed in distinct buckets, preventing cross-contamination — identical messages with different streaming modes are cached independently.

## Invalidation API

v2.9.10 provides an explicit cache invalidation endpoint:

```
DELETE /api/config/cache/response
```

### Query Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `serviceType` | No | Service type (e.g. `chat`, `embedding`); clears all entries for that service |
| `model` | No | Model name (e.g. `gpt-4o`); combined with `serviceType` for precise clearing |

- **No parameters**: clears all cached entries
- **`serviceType` only**: clears all entries for that service type
- **`serviceType` + `model`**: precisely clears entries for the given service + model
- **Invalid `serviceType`**: returns 400

### Three-Part Key

The invalidation API relies on the readable prefix of the three-part cache key format `rc:{serviceType}:{model}:{sha256}` to perform prefix-matched bulk deletion.

### RBAC

The invalidation endpoint requires the `config:cache:write` permission (code #44). The `ADMIN` and `OPERATOR` roles include this permission by default.

### Examples

```bash
# Clear all cached entries
curl -X DELETE http://localhost:8080/api/config/cache/response

# Clear by service type
curl -X DELETE "http://localhost:8080/api/config/cache/response?serviceType=chat"

# Clear by service + model
curl -X DELETE "http://localhost:8080/api/config/cache/response?serviceType=chat&model=gpt-4o"
```

## Limitations and Roadmap

Current version boundaries:

- **Distributed**: the cache is in-process Caffeine; each instance is independent. A shared / Redis cache is planned for a later version
- **Semantic cache**: only byte-level request matching; no semantic-level reuse (hello vs hi) — semantic caching is under later evaluation
- Binary services (image / TTS / STT) never enter the cache

## Related Documents

- [Changelog](../reference/changelog.md)
- [KV Cache](./kv-cache.md)
- [Rate Limiting](./rate-limiting.md)
- [Monitoring Metrics Reference](../monitoring/metrics.md)

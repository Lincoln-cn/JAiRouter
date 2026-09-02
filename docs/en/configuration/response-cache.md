# Response Cache

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-09-03
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

Starting with **v2.9.9**, JAiRouter ships a **response cache**: it caches the complete downstream LLM response under a cache key and, when an identical request arrives again, serves the cached result directly while **skipping the downstream call** — cutting latency and cost.

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
| `skip-streaming` | `true` | Skip streaming requests (P0 supports non-streaming caching only) |

It takes effect as soon as it is enabled — no restart or extra setup needed. The cache is a **purely additive capability**: non-hit requests behave exactly as when it is off.

## Eligible Requests (P0)

P0 caches only **non-streaming deterministic** requests:

| Service | Cacheable when | Notes |
|---------|----------------|-------|
| `chat` (non-streaming) | `temperature` is `0` or `null` **and** `n` is `1` or `null` | Sampling is deterministic and reproducible; all other parameters (messages, etc.) enter the cache key |
| `embedding` | Always (deterministic by nature) | Cached directly |
| `rerank` | Always (deterministic by nature) | Cached directly |
| `chat` (streaming) | Not cached by default | Skipped while `skip-streaming: true` (default); streaming caching is planned for a later version |
| `image` / `TTS` / `STT` | Never | Binary / non-P0 services are excluded from the cache |

- With `only-deterministic: true` (default), chat requests with `temperature > 0` or `n > 1` are not cached (each generation differs, so caching adds no value)
- With `skip-streaming: true` (default), streaming requests get no cache key and neither read nor write the cache

## Cache Key and Tenant Isolation

The cache key is a SHA-256 digest of the normalized request — **the key holds no plaintext content**:

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
- **Rate limiting semantics**: the cache read runs **after service-level rate limiting** — cache hits do not bypass rate limits (limiting already ran during instance selection)
- **Metrics** (tags: `service`, `model`):

| Metric | Type | Description |
|--------|------|-------------|
| `jairouter_response_cache_hits_total` | Counter | Cumulative cache hits |
| `jairouter_response_cache_misses_total` | Counter | Cumulative cache misses |
| `jairouter_response_cache_hit_ratio` | Gauge | Hit ratio 0.0~1.0 (cumulative hit/(hit+miss)) |

## Limitations and Roadmap

P0 boundaries:

- **Streaming**: streaming responses are not cached yet (default `skip-streaming: true`); streaming caching is planned for a later version (P1 / v2.9.10)
- **Distributed**: the cache is in-process Caffeine; each instance is independent. A shared / Redis cache is planned for a later version
- **Semantic cache**: only byte-level request matching; no semantic-level reuse (hello vs hi) — semantic caching is under later evaluation
- **Invalidation**: no explicit invalidation API yet; stale content is bounded by TTL (plus the cacheSalt bypass); an invalidation API is planned for a later version
- Binary services (image / TTS / STT) and streaming requests never enter the cache

## Related Documents

- [Changelog](../reference/changelog.md)
- [KV Cache](./kv-cache.md)
- [Rate Limiting](./rate-limiting.md)
- [Monitoring Metrics Reference](../monitoring/metrics.md)

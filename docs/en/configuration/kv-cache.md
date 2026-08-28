# KV Cache Configuration (Prefix-Cache-Friendly Gateway)

<!-- 版本信息 -->
> **Document Version**: 1.0.0  
> **Last Updated**: 2026-08-28
> **Git Commit**: 5b592476  
> **Author**: Lincoln
<!-- /版本信息 -->

Starting with **v2.9.0**, JAiRouter ships **LLM KV cache enhancement**: automatic tenant-affinity sticky routing + cache hit metrics + cache parameter passthrough, maximizing prefix-cache (KV cache) utilization of downstream LLM services. The gateway targets **long documents and multi-turn conversations** — requests from the same tenant are stably routed to the same instance so downstream prefix caches keep hitting, cutting first-token latency (TTFT) and prefill cost.

> **Note**: this is a **prefix-cache-friendly** gateway, not a response cache. It does no content caching — it makes the downstream LLM service's own cache effective through three things: routing + observability + passthrough.

## 1. Automatic Tenant-Affinity Sticky Routing (v2.9.0)

### How it works

On each request, the gateway hashes the affinity key to instances (**consistent hashing**, 150 virtual nodes), stably mapping requests of the same tenant to the same instance:

- **Affinity key** = `apiKeyId | serviceType | modelName` (falls back to `clientIp` when there is no API key)
- When the pinned instance is unhealthy / circuit-open, routing **automatically falls back** to the normal load-balancing strategy — requests are never blocked
- **On by default**: effective automatically when there are > 1 healthy instances, zero configuration

### Configuration

Sticky routing is configured per service (`model.services.<serviceKey>.sticky`):

```yaml
model:
  services:
    deepseek:
      adapter: deepseek
      sticky:
        enabled: true            # enable sticky routing (default true; effective when instances > 1)
        affinityKeyScope: tenant_model  # affinity granularity: tenant_model (default) / tenant
```

- `sticky.enabled`: enable sticky routing (default `true`; effective only when instances > 1 and healthy)
- `sticky.affinityKeyScope`:
  - `tenant_model` (default): affinity key includes `modelName` — the same tenant's different models may route to different instances
  - `tenant`: affinity key is `apiKeyId|serviceType` (falls back to clientIp) — the whole tenant pins to one instance

> Cloud APIs (DeepSeek/OpenAI official endpoints, etc.) do prefix caching automatically on the provider side — no gateway sticky routing needed; **sticky routing targets self-hosted multi-instance clusters (vLLM / GPUStack / Ollama, etc.)**.

## 2. Cache Hit Metrics (v2.9.0)

The gateway parses cache tokens from downstream response `usage` (both forms supported) and exposes Prometheus metrics:

| Form | usage field |
|------|-------------|
| DeepSeek | `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` |
| OpenAI / vLLM | `prompt_tokens_details.cached_tokens` |

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `jairouter_cache_hit_tokens_total` | Counter | `adapter`, `instance` | Cumulative cache hit tokens |
| `jairouter_cache_miss_tokens_total` | Counter | `adapter`, `instance` | Cumulative cache miss tokens |
| `jairouter_cache_hit_ratio` | Gauge | `adapter`, `instance` | Hit ratio 0.0~1.0, cumulative hit/(hit+miss) |

- Missing fields are tolerated as 0 (no error)
- `cache_hit_ratio` is **cumulative** (hitTotal/(hitTotal+missTotal)), not per-request
- Aggregate by `adapter` / `instance` in Grafana/Prometheus to observe cache gains; streaming responses are parsed too (usage in the final stream chunk)

## 3. Cache Parameter Passthrough (v2.9.0)

`ChatDTO.Options` gains two passthrough fields, forwarded via the `extra_body` whitelist to the downstream (vLLM prefix-cache params):

```json
{
  "model": "my-model",
  "options": {
    "prefixCacheHash": "abc123",
    "enablePrefixCaching": true
  }
}
```

- `prefixCacheHash` → downstream `extra_body.prefix_cache_hash`
- `enablePrefixCaching` → downstream `extra_body.enable_prefix_caching`

## 4. Verification

- **Sticky routing**: with the same `apiKeyId`, consecutive requests should hit the same `instance` in traces/logs (while the instance is healthy)
- **Metrics**: `curl http://<host>:8080/actuator/prometheus | grep jairouter_cache` (or query in Grafana)
- **Prefix hygiene**: byte-for-byte message-array passthrough regression tests (4- and 6-message sets); 5 tolerant cache-token parsing tests

## 5. Known Limitations

- **Anthropic `cache_control` structural limitation**: current `ChatDTO.Message` has no content-blocks structure, so Anthropic prefix-cache params cannot be passed through yet (requires a contentBlocks variant in a future version)
- Sticky routing only affects **outbound instance selection**; it does not guarantee shared caches across downstream replicas
- Cache metrics only count downstream services that explicitly return cache-token fields (DeepSeek / OpenAI / vLLM-compatible forms)

## Related Documents

- [Changelog](../reference/changelog.md)
- [Load Balancing](./load-balancing.md)
- [Routing Rules](./routing-rules.md)

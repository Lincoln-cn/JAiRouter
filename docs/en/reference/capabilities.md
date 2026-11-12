# Capability Reference

This page answers three questions: **what it does, what it does not do, and where the boundaries are**. Every configuration key below maps to real code in the repository — there are no "planned" entries.

> Describes the current release. Default values are in the "Default" column.

## Request endpoints

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| OpenAI-compatible Chat Completions | Available | On | `model.services.chat` (`config/router/services.yml`) | Streaming and non-streaming |
| OpenAI-compatible Embeddings | Available | On | `model.services.embedding` | — |
| Rerank | Available | On | `model.services.rerank` | — |
| Speech synthesis / recognition | Available | On | `model.services.tts` / `model.services.stt` | Served under `/v1/audio/*` |
| Anthropic Messages protocol | Available | On | Detected from the request path | Runs alongside the OpenAI surface; clients use their own protocol |
| Native passthrough (`/v1/**`) | Available | On | — | Returns native JSON, not wrapped in `RouterResponse` |
| Console surface (`/api/**`, `/admin/**`) | Available | On | — | Returns the unified `RouterResponse` envelope |

## Routing and fault tolerance

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| Multi-instance load balancing | Available | On | `model.services.<type>.instances` | Strategies: round-robin / random / least-connections / consistent-hash / IP-hash / latency-aware (sticky-session is a separate affinity switch, **off by default since 3.8.6**) |
| Same-name instances form a candidate set | Available | On | Instance `name` | The request's `model` field matches instance names; identical names mean "replicas of the same model" |
| Circuit breaking | Available | On | `model.circuit-breaker.*` | State is persisted in the database and survives restarts; half-open recovery is implemented |
| Retry | Available | On | `model.services.<type>.retry` | — |
| Failover | Available | On | `model.fallback.*` | — |
| Health checking | Available | On | `model.health-check.*` | A port-only probe can report "healthy but unusable" — see Known Limits |

## Rate limiting and quota

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| Global rate limit | Available | On | `model.rate-limit` | Outermost of the three layers |
| Service-level rate limit | Available | On | `model.services.<type>.rate-limit` | — |
| Instance-level rate limit | Available | On | `model.services.<type>.instances[i].rate-limit` | **Cannot be overridden per item via environment variables** (it replaces the whole list; see `configuration/index.md`) |
| API key quota | Available | On | `jairouter.quota.*` | Daily / weekly / monthly windows plus token dimension |
| Over-quota response | Available | — | — | Returns **429**, not 401, so clients can tell "out of quota" from "bad credentials" |

## Caching

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| Response cache (exact) | Available | Per config | `jairouter.cache.response.*` | Isolated per tenant |
| Response cache (semantic) | Available | **Off** | `jairouter.semantic-cache.*` | SimHash near-match reuse; L2 per-tenant and L3 public pool, the latter gated by seven admission rules |
| Streaming response cache | Available | Follows response cache | Same as above | Replayed as SSE frames on a hit |
| PII-bearing responses are never cached | Available | On | — | Skipped when any of three signals trips (request replacements / response restorations / an independent detector) |

## Data protection

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| Response sanitization (one-way) | Available | On in prod | `sanitization.response.*` | Rules are configurable; redaction is not reversible |
| Reversible sanitization (PII vault) | Available | **Off** | `jairouter.pii.reversible.*` | Placeholders on the request side, restored on the response side; streaming restores chunk by chunk, so a split placeholder stays split |
| Field-level sanitization scope | Available | Whole text | `jairouter.pii.reversible.json-paths` | Supports `$.a.b` and `$.a[*].b`; a misconfigured path falls back to whole-text matching (never under-redacts) |
| Sanitization access audit | Available | On with reversible sanitization | — | Metadata only, never the original values |
| Streaming guard | Available | **Off** | `jairouter.guard.*` | Regex hits can REDACT or ABORT; no cross-chunk joining, so a split sensitive string is not detected |
| SSRF protection | Available | On | — | Outbound address validation rejects internal and reserved ranges |

## Observability

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| Prometheus metrics | Available | On | `management.endpoints.web.exposure.include` | Production exposes only `health,info,prometheus` |
| Call history | Available | On | `jairouter.monitoring.call-history.*` | Cleanup runs at 02:00 |
| Token usage records | Available | On | `jairouter.monitoring.token-usage.*` | Cleanup runs at 03:00 (90-day default) |
| Audit log | Available | On | `jairouter.security.audit.*` | Event types are an enum; unknown names are rejected |
| Distributed tracing | Available | Per config | `jairouter.tracing.*` | OTLP / Zipkin / log exporters |
| Capacity metrics | Available | On | — | SSE connections, outbound in-flight requests, cache entries, event-loop delay, WebSocket sessions |
| Console | Available | On | — | Vue 3; overview / routing / cache / monitoring / configuration |

## Deployment and operations

| Capability | Status | Default | Configuration | Boundary |
|---|---|---|---|---|
| Single-jar startup | Available | — | — | `java -jar model-router.jar` |
| Docker images | Available | — | `Dockerfile` / `Dockerfile.optimized` / `Dockerfile.distroless` / `Dockerfile.jlink` | The three variants use `jarmode=tools` layering |
| Kubernetes | Available | — | `docs/*/deployment/kubernetes.md` | Deployment / Service / ConfigMap examples |
| Database | Available | H2 | `spring.datasource.*` | H2 and PostgreSQL; R2DBC was removed in v3.3.6 |
| Shared state for multiple replicas | Available | Off | `jairouter.security.*.redis.enabled` (three switches) | Startup warns when replicas are detected without them; the semantic cache pool is still per-process (see Known Limits) |
| Configuration hot update | **Partial** | — | — | Quota and monitoring change memory only (lost on restart); sanitization and circuit-breaker settings are persisted; instance config is persisted but a sync deletes instances absent from YAML |

## What this gateway does not do

| Not done | Why | Whose job |
|---|---|---|
| Model inference | The gateway forwards; it does not compute | vLLM / Ollama / cloud APIs |
| Billing and settlement | Billing involves accounts, tax, reconciliation — a separate system | Your billing platform |
| Prompt management and versioning | An application-layer concern; putting it here turns the gateway into a business system | Application / prompt platform |
| RAG and vector storage | The gateway forwards embedding requests; it stores no vectors | Vector databases |
| Training and fine-tuning | Unrelated to gateway duties | Training platforms |
| End-user application scaffolding | The console is an admin UI, not an end-user product | Business applications |

## Enterprise capabilities: are they in the open-source build?

This section lays out the current facts so a commercial split can be decided with eyes open.

| Capability | In the open-source build | Notes |
|---|---|---|
| Multi-tenancy (API key scope) | **Yes** | Quota, cache, and audit are all keyed by API key |
| Sub-tenant isolation (`user` field) | **Yes** | Used by semantic cache L2; `user` is client-reported and narrows scope only, never authenticates |
| Audit trail | **Yes** | Security audit + sanitization access audit + cross-tenant cache hit audit |
| Compliant sanitization (reversible) | **Yes** | PII vault plus field-level policy |
| Quota and rate limiting | **Yes** | Three-layer limiting plus multi-window quota |
| Observability | **Yes** | Prometheus + tracing + console |
| Shared state across replicas | **Yes** (switch required) | Three Redis switches; the semantic cache pool is still per-process |
| Fine-grained RBAC | **Yes** | 53 permission codes plus URL rules |
| SSO / OIDC | **No** | Username/password plus JWT only |
| High-availability orchestration | **No** | K8s examples only, no operational orchestration |
| SLA and paid support | **No** | An open-source project makes no SLA commitment |

The conclusion is blunt: **apart from SSO and orchestration, every enterprise capability is currently in the open-source build.** If there is to be a commercial path, the line has to be drawn here — that is a product decision, not a technical one.

## Known limits

These are deliberate trade-offs, not defects:

1. **A port-only health check does not mean "usable"** — `check_type: socket_connect` proves the port is open; an upstream returning errors may still be reported healthy.
2. **The semantic cache L2/L3 pool is per-process** — with multiple replicas the hit rate is divided by the replica count, and nothing signals why (issue #1002).
3. **The streaming guard does not join across chunks** — a sensitive string split between two chunks is not detected.
4. **Reversible sanitization restores chunk by chunk in streaming** — a split placeholder is not restored, so the client sees the placeholder text (the real value never left).
5. **The streaming path does not run the PII detector** — non-streaming responses containing PII are not cached; the streaming path does not yet apply this check.
6. **Quota hot edits are not persisted** — a console change reverts to the configuration file on restart (only `QuotaConfigController` surfaces this: restart-level fields return 400 with `RESTART_REQUIRED`).

## Related documents

- [Runtime mutability](../configuration/dynamic-config.md) — what a change actually affects
- [Data sanitization](../security/data-sanitization.md) — configuration and boundaries
- [Semantic cache](../configuration/response-cache.md) — three scopes and admission rules
- [Performance](../troubleshooting/performance.md) — measured baselines

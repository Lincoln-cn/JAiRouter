# Roadmap

<!-- 版本信息 -->
> **Doc Version**: 2.5.0
> **Last Updated**: 2026-10-30
> **Git Tag**: v3.8.5
> **Author**: Lincoln
<!-- /版本信息 -->



This document outlines the future development plans and feature roadmap for the JAiRouter project.

## Project Vision

JAiRouter aims to become the best open-source AI model service routing gateway, providing users with:

- **Unified Access**: One gateway to access all AI model services
- **Intelligent Routing**: Smart load balancing based on multiple strategies
- **High Availability**: Robust fault tolerance and recovery mechanisms
- **Ease of Use**: Simple configuration and comprehensive documentation
- **High Performance**: Support for large-scale concurrency and low-latency responses
- **Observability**: Comprehensive monitoring, logging, and distributed tracing

## Current Version Status

**Latest release: v3.8.5 (2026-11-12).** Per-release notes, upgrade notes and known limits live in the
[changelog](changelog.md); downloads and container tags are on
[Releases](https://github.com/Lincoln-cn/JAiRouter/releases).

This page keeps only directional content (feature roadmap, architecture evolution, release cycle).
**Release history is not duplicated here** — it was, and the copy fell five releases behind the
changelog; one source of truth is the only version that stays true.

## Feature Roadmap

### Adapter Extensions

#### Supported ✅
| Adapter | Type | Status |
|---------|------|--------|
| GPUStack | Chat/Embedding/Rerank | ✅ |
| Ollama | Chat/Embedding | ✅ |
| vLLM | Chat | ✅ |
| Xinference | Chat/Embedding/Rerank | ✅ |
| LocalAI | Chat/Embedding | ✅ |
| OpenAI | Chat/Embedding | ✅ |
| Azure OpenAI | Chat/Embedding | ✅ |
| Anthropic Claude | Chat | ✅ |
| Alibaba Bailian | Chat/Embedding | ✅ |
| Tencent Hunyuan | Chat | ✅ |
| Baidu Cloud | Chat | ✅ |
| Google Gemini | Chat/Embedding | ✅ |

#### Planned 📋
- 📋 Cohere API (version TBD)
- 📋 AWS Bedrock (version TBD)

> **Audit correction, 2026-10-16**: `Google Gemini` is **already implemented** — it is registered as
> `adapters.put("gemini", …)` in `AdapterRegistry` and `GeminiAdapter.supportCapability()` reports `chat` + `embedding`,
> so it moved into "Supported"; the `v2.8.x` / `v2.9.x` labels on the remaining two had long expired and are now "version TBD".

### Load Balancing Strategies

#### Implemented ✅
- ✅ Random
- ✅ Round Robin
- ✅ Weighted Round Robin
- ✅ Least Connections
- ✅ IP Hash
- ✅ Consistent Hash

#### Implemented ✅ (cont.)
- ✅ Latency-Aware (`LatencyAwareLoadBalancer`)
- ✅ Sticky (`StickyLoadBalancer`)

#### Planned 📋
- 📋 Cost-based
- 📋 Model Capability-based

> **Audit correction, 2026-10-16**: latency-based balancing is **already implemented** (`LatencyAwareLoadBalancer`) and was
> listed as planned; sticky sessions were missing from the list and are added here.

### Rate Limiting Algorithms

#### Implemented ✅
- ✅ Token Bucket
- ✅ Leaky Bucket
- ✅ Sliding Window
- ✅ Warm Up

#### Implemented ✅ (cont.)
- ✅ API-key-level rate limiting (per-key `rateLimitPerMinute` + token bucket; over-limit answers **429** with `RATE_LIMITED_API_KEY`, #404)
- ✅ End-user rate limiting (v3.5.2: per-`(API key × end user)` per-minute allowance, off by default; identity from `X-End-User-Id` or the request-body `user`; over-limit answers 429 with `Retry-After` / `X-RateLimit-*`; cross-replica counting via `jairouter.ratelimit.distributed.enabled`)
- ✅ Client-dimension rate limiting (`RedisSlidingWindowRateLimiter`, 1-second window): **wired into the request
  path since v3.5.2** (#623), controlled by `model.rate-limit.client-ip-enable` (or the service-level twin), with
  **every bundled configuration setting it to `false`**; key dimensions are `client-ip` / `api-key` / `tenant`
  (`tenant` currently equals `api-key` — there is no tenant identity yet)

#### Planned 📋
- 📋 Tenant semantics for the rate-limit key dimension (left over from #623): `tenant` currently equals `api-key`;
  counting per real tenant needs a tenant identifier first

> **Correction, 2026-10-16**: both "API-key-level" and "distributed" rate limiting were listed as planned but are
> **already implemented** (see `ApiKeyValidator.validateRateLimit` and `RateLimitManager`'s wiring of `RedisSlidingWindowRateLimiter`).
>
> **Audit correction, 2026-10-16 (second pass)**: "Adaptive Rate Limiting" was listed as implemented, but there is
> **no such implementation** anywhere in the repository (no such algorithm under `router/ratelimit/**`, and the other
> documentation pages list only token-bucket / leaky-bucket / sliding-window / warm-up) — the line was removed.
>
> **Correction, 2026-10-24**: the "distributed rate limiting is implemented" statement above needs narrowing — the
> implementation exists, but **the client-dimension path has no production caller** (#623), which is why the
> switches and docs used to read as if it were live. The end-user limiting shipped alongside is the first
> rate-limiting path that really shares counters through Redis.
>
> **Correction, 2026-10-24 (wiring landed)**: that gap is closed — the request path now calls
> `RateLimitManager.tryAcquireClientIp` after authentication and before the service-level pre-deduct. To avoid
> merging clients behind a cluster without an L7 proxy, **every bundled configuration sets `client-ip-enable`
> to `false`**; operators turn it on explicitly when the topology allows.

### Monitoring and Observability

#### Implemented ✅
- ✅ Prometheus metrics
- ✅ Grafana dashboards
- ✅ Health check endpoints
- ✅ Basic alert rules
- ✅ Distributed tracing (Zipkin/OpenTelemetry)
- ✅ Structured logging

#### Planned 📋
- 📋 Business metrics analysis
- 📋 Cost analysis
- 📋 Custom alert rules

## Technical Architecture

### Current Architecture (v3.3.5)
```
Monolithic App → Modular Design → Reactive Programming
```

### Architecture Evolution

| Phase | Version | Status |
|-------|---------|--------|
| Foundation | v0.1 - v0.3 | ✅ Complete |
| Security | v0.4 | ✅ Complete |
| Monitoring | v0.5 | ✅ Complete |
| Performance | v2.7.x | ✅ Complete |
| Configuration | v2.8.x | ✅ Complete (modular config + three config-honesty passes) |
| Maintainability | v2.9.x | ✅ Complete (refactoring records / gate system / error-code registry) |
| Microservices | v3.0 | ⏸️ Postponed (the current monolith meets the requirements) |

### Tech Stack

#### Core Technology
- **Backend**: Spring Boot 3.5.5 + WebFlux (Reactive)
- **Frontend**: Vue 3 + TypeScript + Element Plus
- **Database**: H2 (embedded) + R2DBC
- **Cache**: Redis (optional)
- **Monitoring**: Prometheus + Grafana
- **Tracing**: OpenTelemetry + Zipkin

#### Data Storage
- **Default**: H2 embedded database (single file; **cannot be shared across replicas**)
- **Now supported**: **PostgreSQL** — setting `DATABASE_URL=jdbc:postgresql://…` switches to PostgreSQL (horizontal multi-replica scaling needs a shared external database);
  both paths have **versioned schema migrations** (PostgreSQL runs Flyway with `ddl-auto` overridden to `validate`, H2 uses a startup compatibility patcher)
- **Planned**: MySQL (no driver or dialect scripts, not supported)
- **Cache**: Redis (optional)

> **Audit correction, 2026-10-16**: the text claimed "H2 only, PostgreSQL / MySQL planned" — **PostgreSQL has been supported since v3.0.0**
> (see `org.postgresql:postgresql` + `flyway-database-postgresql` in `pom.xml`, the migration mode in `application-migrate.yml`
> and the dialect override in `FlywayDialectEnvironmentPostProcessor`). MySQL is still unsupported.

## Performance Targets

> ⚠️ The table below holds **design targets, not measured values** (this repository has no benchmark suite); measure before quoting.

| Version | RPS | Latency (P95) | Availability | Connections |
|---------|-----|---------------|--------------|-------------|
| v2.6.x | 5k | < 50ms | 99.95% | 5k |
| v2.7.x | 10k | < 30ms | 99.95% | 10k |
| v2.8.x | 20k | < 20ms | 99.99% | 20k |
| Target | 100k+ | < 10ms | 99.99% | 100k+ |

## Community Development

### Current Status
- GitHub / Gitee dual platform hosting
- Complete user and API documentation
- Chinese/English documentation support
- MkDocs static website

### Development Goals
- Active developer community
- Regular version releases
- More adapter support
- Complete plugin system

## LTS Policy Retirement (2026-09)

**Decision: the LTS (Long-Term Support) release line is retired.**

**Background**: release cadence has increased sharply (roughly 20 releases between v2.9 and v3.1). Maintaining a separate LTS line means continuous regression testing, security patches and dual-track documentation for an older branch, which no longer matches the effort it costs. The project therefore no longer commits to an LTS maintenance window.

| Item | Detail |
|------|--------|
| Existing artifacts | The `v2.6.11` Git tag, Docker images and Release **remain downloadable and usable for rollback**, but carry **no dedicated maintenance commitment** (no guaranteed security patches or compatibility fixes) |
| Maintained line | Only the **current release (latest)** is maintained |
| Upgrading | Follow the latest release; breaking changes are noted in the [changelog](changelog.md) |
| Long-term stability | Pin a specific version tag (e.g. `v3.1.1`) and keep your own regression tests; pull the matching image tag instead of `latest` |

> The LTS row in the "Release Cycle" table below has been removed as part of this policy.

## Release Cycle

| Version Type | Cycle | Description |
|--------------|-------|-------------|
| Feature | 1-2 months | New feature iteration |
| Patch | As needed | Bug fixes and security updates |

---

## Contributing

### Code Contributions
- Feature development and bug fixes
- Performance optimization and refactoring
- Test case writing
- Code review

### Documentation Contributions
- User documentation improvements
- API documentation updates
- Tutorial and example writing
- Multi-language translation

### Feedback Channels

- **GitHub Issues**: [https://github.com/Lincoln-cn/JAiRouter/issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [https://github.com/Lincoln-cn/JAiRouter/discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Documentation Feedback**: Submit via GitHub PR

---

## Risks and Challenges

### Technical Risks
- **Performance bottlenecks**: Performance challenges at scale
- **Compatibility**: Multi-version API compatibility maintenance
- **Security**: Security vulnerabilities and attack protection
- **Horizontal scaling prerequisites**: The default manifest uses the H2 embedded single-file database with `replicas: 1`. After a shared PostgreSQL and a shared Redis with every shared-state switch on are wired in, and all replicas share the same `JWT_SECRET`, you can scale out (see [Multi-Replica Deployment](../deployment/multi-replica.md)). Without those switches, rate limiting, quota, JWT blacklist and API-Key caches remain per-process; service-level and instance-level rate limiting still count per replica (see the same page and issue #1114)

### Market Risks
- **Increased competition**: Competition from similar products
- **Technology changes**: Challenges from rapid AI technology development
- **User needs**: Rapidly changing user requirements

### Mitigation Strategies
- Continuous technical innovation and optimization
- Active community building and maintenance
- Flexible product strategy adjustments
- Comprehensive quality assurance system

---

## Summary

JAiRouter will continue to uphold the open-source spirit and is committed to providing users with the best AI model service routing solution. We welcome community participation and contributions to jointly promote the project's development and progress.

### Recent Focus (2026)
1. ✅ Complete v2.7.x performance optimization series + RBAC permission control
2. ✅ Advance v2.8.x configuration management + adapters + plugin system/rule engine/resource pools
3. ✅ v2.9.x intelligent routing series (failover/tag routing/response cache P0)
4. ✅ v2.9.10 response cache P1 (streaming cache + invalidation API + rate-limit short-circuit)
5. ✅ v2.9.11 README/screenshot refresh + hallmark web-console design audit + frontend fixes
6. ✅ v2.10.x Web console refactor series completed (v2.10.0 foundation ✅ 2026-09-05; v2.10.1 governance hub ✅ / v2.10.2 config & capabilities ✅ / v2.10.3 Web bilingual edition ✅ / v2.10.4 Web experience wrap-up ✅ 2026-09-06)
7. ✅ v3.0.x Web complete-flow series (v3.0.1 flow wiring ✅ 2026-09-06; v3.0.2 API cleanup & permission closure ✅ 2026-09-07; v3.0.3 acceptance & release ✅ 2026-09-13)
8. ✅ v3.1.0 quota ledger & multi-protocol entry ✅ 2026-09-14 (multi-dimensional multi-window quota + Anthropic entry + tool calling + runtime config/observability)
9. ✅ v3.2.0 PII governance & quota ops closed-loop (released 2026-09-19)
10. ✅ v3.2.1 audit remediation patch (released 2026-09-21): Reactor/SSE de-blocking, quota TOCTOU and Redis Lua CAS, spurious 401 fix, tracing chain double-execution fix, JWT blacklist degradation convergence, production fail-fast, SSRF guard, RBAC/debug-endpoint tightening, user-regex ReDoS hardening. **Known remaining (P2)**: `QuotaLedgerService` distributed path still blocks synchronously; frontend temporary IDs use `Math.random`
11. ✅ v3.2.2 patch (released 2026-09-22): fixed the user-reported API Key quota display (#84) and routing-monitor counter (#83); console business failures now return 4xx/5xx instead of HTTP 200 (#94, response-body compatible), plus quota endpoint `block` failure (#92), over-permissive e2e assertions (#95), alert-threshold inconsistency (#96) and monitoring error-code inconsistency (#98). **Known remaining (P2)**: `QuotaLedgerService` distributed path still blocks synchronously; frontend temporary IDs use `Math.random`
12. ✅ v3.3.0 minor (released 2026-10-07): PostgreSQL and versioned migration (PG on `ddl-auto: validate`, H2 gets versioned migration too, both startup patch components deleted); multi-replica cluster (cross-replica event broadcast and sliding-window counting, shared-state self-check, per-class scheduled jobs); RBAC closed out to fail-closed GETs; four batches of tracing config honesty (57 zero-consumer fields deleted, 3 wired up); structured JSON stdout and Kustomize manifests; documentation facts checked and corrected (#160–#248)
13. ✅ v3.4.0 feature release (released 2026-10-21): console overhaul (7 groups / 28 items navigation, collapsible sidebar, dark-mode hierarchy, card lists on narrow screens, 33 pages on a neutral palette, global search and status bar); permission semantics (eight read-only codes, 55 → 63); persisted sanitization configuration (Flyway V4); hourly/monthly quota limits. **Deferred**: the record-level FULL encryption strategy UI moves with the frontend track (v3.5.x)
14. ✅ v3.5.0 feature release (released 2026-10-24): response sanitization coverage closed out (non-streaming JSON under `/v1/**`, configurable `response.scope`, buffer limit, per-frame SSE), default PII rules and JSON key exemptions corrected, request-side configuration surface removed (Flyway V5), three circuit-breaker fixes, console acceptance fixes
15. ✅ v3.5.3 patch (released 2026-10-25): five fixes from the main-flow audit (external mount override #634, image-edit multipart #637, instance health keys #636, outbound path from the selected instance #635, adapter reference validation #638) plus single-sourced `model.services` #643; no API breaking changes, but #634 and #637 change behavior
16. ✅ v3.5.2 feature release (released 2026-10-24): end-user rate limiting (per-`(API key × end user)` per-minute allowance, off by default, self-reported identity so not a security boundary, bounded local counter or cross-replica Redis counting); client-dimension wiring (#623, off in every bundled configuration so a cluster without an L7 proxy does not merge clients by IP); `Retry-After` on service-level 429 and all-instances-limited 503 (#624); bounded window table in `TokenBucketRateLimiter` (#625)
17. ✅ v3.5.1 patch (released 2026-10-24): record-governance page and call-history key-source wiring (`encryption-key-source: env` went from "settable but inert" to actually effective, `GET /api/config/call-history` gains 8 read-only observation fields, new page `/security/record-governance`); live `gatewayResponseFilter` in the config snapshot; tech-debt batches A–D of #604 (end-to-end 400 case + bare-catch ceiling 835 → 786, `QuotaLedgerService` 1268 → 1094, `ServiceRequestHandler` 1027 → 785); API paths unified on `/api/v1/*`; fence-parity check wired into the docs gate
18. ✅ v3.5.4 patch (released 2026-10-27): routing and circuit accounting (4xx no longer trips breakers #647, keep trying after rate limiting #654, random balancer lost its private breakers #648, balancers cached per strategy #649, adaptive thresholds actually applied #653, failures release in-flight counts #655), health checks hardened (#650–#652), shipped credentials closed out (built-in test API key moved to dev #663, random fallbacks for secret and password #664, implicit dev default removed #666), logback fallback (#669), staging requirements documented (#670); no API breaking changes
19. 📋 Semantic cache evaluation (vector-similarity reuse, separate project); high-availability foundation (multi-node/Redis) re-assessed after v3.2.x

### Long-term Vision
1. Become the standard in AI model routing
2. Build a complete ecosystem
3. Achieve enterprise-level commercial success
4. Drive industry technology development

---

**Last Updated**: October 24, 2026

For any suggestions or ideas, feel free to communicate with us via [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions).

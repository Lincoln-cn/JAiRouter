# Roadmap

<!-- 版本信息 -->
> **Doc Version**: 2.1.0
> **Last Updated**: 2026-09-22
> **Git Tag**: v3.2.2
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

### ✅ v3.2.2 (Patch: User-Reported Fixes + Console HTTP Status Semantics, released 2026-09-22)

**Status**: Released (Git tag / GitHub Release `v3.2.2`; the Docker image is rebuilt by CI on release)
**Scope**: Fixes for the user-reported #83 / #84 plus adjacent defects (#92 / #94 / #95 / #96 / #98); bug fixes only, no new features (PRs #90 / #91 / #93 / #97 / #99)

| Area | Delivered |
|------|-----------|
| API Key quota | Create/edit now submit the complete quota payload, fixing the console display of quotas set at creation time (#84); quota update/reset endpoints no longer call `block()` on a Reactor non-blocking thread (previously failed 100% of the time, #92); the alert threshold is unified to the closed interval `[0.05,1]` with `QuotaLimits` as the single source of truth and validation added to the create path (#96) |
| Load balancing & routing monitor | Three instance selections per request reduced to one (request-scoped `SelectedInstanceHolder` plus adapter reuse of the selected instance), fixing the "instance distribution" counter jumping by 3 (#83); also fixed missing request-header propagation on the adapter side (which broke header-based rule/tag routing) and `recordCall` advancing twice per request |
| Console HTTP status | Added `ApiException` / `ApiExceptions`; all 138 sites that disguised failures as HTTP 200 now return proper 4xx/5xx statuses with a **byte-compatible response body** (#94); the frontend interceptor surfaces backend error messages; e2e assertions tightened (#95); `MonitoringController` client-validation error codes unified to 400 with new auto-mode test coverage (#98) |
| Engineering & tooling | OpenAI-compatible mock model server added; local `docker-ctx` ignored |

### ✅ v3.2.1 (Audit Remediation Patch, released 2026-09-21)

**Status**: Released (Git tag / GitHub Release `v3.2.1`; the Docker image is rebuilt by CI on release)
**Scope**: Audit remediation batch merged after v3.2.0 (Reactor / SSE / quota / auth / SSRF) + user-regex ReDoS hardening

| Area | Delivered |
|------|-----------|
| Concurrency & streaming | Health SSE and the request main chain moved off the EventLoop; streaming completion plus metrics/call-history no longer `block(5s)`; SSE/WS sinks bounded; cold-path `block` unified with timeouts (5s / 30s startup) |
| Quota & rate limiting | Single-node check-then-reserve serialized (TOCTOU); cross-instance race closed by an atomic Redis Lua CAS (HINCRBY + limit check + rollback) |
| Auth & authorization | Spurious 401 fixed (with a JWT session the console AI pages and `/v1/**` were unusable); console JWT sessions allowed; tracing filter no longer runs the chain twice; JWT blacklist degradation converged (single warning + 30s short-circuit + startup probe); `/ws/**` and health SSE require authentication (frontend sends a query token); RBAC gaps closed; debug endpoints off by default and redacted; Swagger requires credentials by default |
| Startup & config | Factory-default JWT secrets rejected; production fails fast on missing/default secret (`StartupSecurityGate`) |
| SSRF | Instance URLs guarded by `SsrfGuard`: instance create/update, config sync, outbound `WebClient`, adapter connectivity tests |
| User regex | New `SafeRegexValidator` gate (512-char limit; nested quantifiers, quantified alternation, backreferences, oversized quantifiers rejected); all three compile paths of both sanitization engines and config validation funnel through it; the detector itself is an O(n) scanner running no regex over user data |

### ✅ v3.2.0 (released 2026-09-19)

**Status**: Released (GitHub Release `v3.2.0`; PRs #60/#64/#65)
**Scope**: PII sanitization management closed-loop + quota observability UX + TDD test foundation

| Area | Delivered |
|------|-----------|
| PII record path | `sanitizeForStorage` decoupled from gateway request/response switches; call-history SUMMARY & tracing use it |
| Management API | `GET/PUT /api/config/sanitization`, rules list, dry-run test (reactive; non-JSON samples masked as text) |
| Console | Security → PII Sanitization page; permission `security:sanitization:manage` (49 codes) |
| Exclusions | Admin `/api/**` and AI realtime paths excluded from gateway response masking |
| Quota UX | Monitoring limit/progress columns + cross-links to API Key page; docs: `0`=unlimited, threshold `0.0–1.0` |
| Quality | Redis TTL flaky assertions fixed; vitest unit/API/component tests; page smoke 10/10 |

### ✅ v3.1.2 (Previous stable patch)

**Release Status**: Released (2026-09-18)
**Git Tag**: v3.1.2 (docs/changelog cover Docker startup, JWT_SECRET, image admin path)

### ✅ v3.1.1 (Previous stable)

**Release Status**: Released (2026-09-15)
**Git Tag**: v3.1.1

#### Key Features (v3.0.2 → v3.1.1 evolution)
| Version | Date | Highlights |
|---------|------|------------|
| v3.0.2 | 2026-09-07 | API cleanup & permission closure: removed 4 deprecated controllers + DTO deprecation cleanup + progressive URL-rule registration (47 total) + PermissionClosureTest 35 assertions + frontend dead-function cleanup |
| v3.0.3 | 2026-09-09 | End-to-end journey acceptance (10/10 PASS) + README screenshot refresh (32 images = 8 pages × zh/en × light/dark) + cold-start deep-link fix + i18n/icon warning cleanup |
| v3.1.0 | 2026-09-14 | Quota ledger & multi-protocol entry: multi-dimensional multi-window quota ledger (MINUTE/HOUR/DAY/MONTH + JPA persistence + Redis distributed counting with disconnect fallback, disabled by default) + quota runtime config & observability surfaces (3 console pages) + 48 permission codes total + OpenAI `/v1/models` + Anthropic `/v1/messages` (non-streaming/streaming/`count_tokens`/tool calling) + protocol-shaped gateway errors + error-body UTF-8 fix |
| v3.1.1 | 2026-09-15 | Patch fixes: 401 shape per protocol + `count_tokens` counts tools/tool_use/tool_result + `/v1/**` error-message de-noising + console SSE false-positive alert fix on route navigation |
| v3.1.2 | 2026-09-18 | Docker/startup patch: `JWT_SECRET` env unification, `--generate-key` fix, image frontend static/admin path, Redis health conditional |
| v3.2.0 | 2026-09-19 | PII sanitization management (console + `/api/config/sanitization/**` + `sanitizeForStorage`) + quota monitoring limit/progress UX + vitest/page-smoke TDD foundation + excluded-path alignment (PRs #60/#64/#65) |
| v3.2.1 | 2026-09-21 | Audit remediation patch: Reactor/SSE de-blocking + quota TOCTOU & Redis Lua CAS + spurious 401 fix + tracing chain double-execution fix + JWT blacklist degradation convergence + production fail-fast + SSRF guard + RBAC/debug-endpoint tightening + user-regex ReDoS hardening (PRs #67–#72, #78–#81) |

#### Statistics
- Test count: 3,517 (all green)
- Java source files: 750+
- Codebase: ~140k LOC

---

### ✅ v2.10.x - Web Console Refactor Series (Completed, 2026-09-06)

Features are complete but the console did not yet form a full system flow; one theme per version:
- **v2.10.0 Frontend foundation ✅ (released 2026-09-05)**: dead-code cleanup (13 .vue + common ≈13.5k lines) + PageSkeleton/StatCard/useChartTheme + Dashboard·Service·Rule pilot + ~20 pages tokenized + Element Plus dark-mode fixes + Layout/console cleanup
- **v2.10.1 Governance hub ✅ (released 2026-09-06)**: Dashboard v2 governance-chain panel + exception summary + Cross-link navigation + 7 pages onto PageSkeleton + tokenization wrap-up
- **v2.10.2 Config onboarding & capability pages ✅ (released 2026-09-06)**: response-cache management page (status/invalidation + tier-B runtime toggle + hit observability) + slow-query analysis page + CB reset / quota alerts + onboarding guide + backend cache status & deprecations + 45 permission codes
- **v2.10.3 Web bilingual edition ✅ (released 2026-09-06)**: full zh/en i18n + language switcher + Element Plus locale + date/number localization + dark-mode contrast cleanup
- **v2.10.4 Web experience wrap-up ✅ (released 2026-09-06)**: unified service-type display names + ECharts language/theme hot-switch + dead-code cleanup (`utils/errorHandler`, `validators/*`, etc.) + dark-contrast audit gate (36 pages ≥4.5) + backend-message doc note + dual-theme code highlighting

---

### 🎯 v3.0.x - Web Complete-Flow Series (In Progress)

v3.0 is the Web complete-flow milestone (public `/api/v1` stays compatible, no breaking):
- **v3.0.1 Flow wiring ✅ (released 2026-09-06)**: Onboarding preselect loop (`useRoutePreselect` + 5 containers) + Dashboard governance entries + CB/LB reverse jumps + version-page inbound + legacy redirect cleanup + PageSkeleton across 18 pages
- **v3.0.2 API cleanup & permission closure ✅ (completed 2026-09-07)**: removed 4 deprecated controllers (ServiceConfigController/ServiceInstanceController/InstanceConfigController/SecurityAuditController) and 5 dead methods in TokenUsageController + DTO deprecation cleanup + PermissionRuleRegistry deprecated binding cleanup + progressive URL-rule registration (15 new rules, 47 total) + PermissionClosureTest 35 assertions + frontend dead-function cleanup; mvn 3202 tests all green
- **v3.0.3 Acceptance & release ✅ (released 2026-09-13)**: end-to-end journey acceptance (10/10 PASS, 0 page errors / 0 HTTP 5xx) + README screenshot refresh (32 images = 8 pages × zh/en × light/dark) + docs/plan sync + dual-remote tag/Release/Docker; also fixed cold-start deep links (`/security/*`, `/system/*` role guard) and i18n/icon warnings
- **v3.1.0 Quota ledger & multi-protocol entry ✅ (released 2026-09-14)**: multi-dimensional multi-window quota ledger (MINUTE/HOUR/DAY/MONTH + JPA persistence + Redis distributed counting with disconnect fallback) + quota runtime config & observability surfaces (3 console pages) + OpenAI-native `/v1/models` + Anthropic `/v1/messages` (non-streaming/streaming/`count_tokens`/tool calling) + protocol-shaped gateway errors + error-body charset UTF-8 fix + streaming instance-level headers fix
- Semantic cache evaluation (vector-similarity reuse, separate project); high-availability foundation (multi-node/Redis distributed/config rollback) re-assessed after v3.1.x; microservice migration separate

---

### ✅ v2.6.11 (former Long-Term Support release; LTS policy retired)

**Release Status**: Released (2026-04-17)
**Maintenance Period**: Retired (was planned until 2028-05; see the LTS policy notice below)

#### Completed Features
- ✅ Multi-tenancy support
- ✅ API Key authentication
- ✅ JWT Token support
- ✅ OAuth 2.0 integration
- ✅ Role-Based Access Control (RBAC)
- ✅ Request/response data obfuscation
- ✅ Security audit logging
- ✅ H2 embedded database
- ✅ PostgreSQL/MySQL support
- ✅ Redis cache integration
- ✅ Prometheus metrics collection
- ✅ Grafana dashboard templates
- ✅ Distributed tracing (Zipkin/OpenTelemetry)
- ✅ Complete Docker deployment
- ✅ Kubernetes deployment support

#### Code Quality
- ✅ Checkstyle code standards
- ✅ SpotBugs static analysis
- ✅ JaCoCo test coverage reports
- ✅ 700+ unit tests
- ✅ E2E integration tests

---

### 🎯 v2.7.x - Performance Optimization Series (Q2-Q3 2026)

#### Completed
| Version | Date | Main Content |
|---------|------|--------------|
| v2.7.0 | 2026-04-20 | Package structure refactoring, 6 service modules |
| v2.7.1 | 2026-04-21 | auth module independence (116 files) |
| v2.7.2 | 2026-04-22 | config module independence (~50 files) |
| v2.7.3 | 2026-04-23 | router module part 1 (adapter/loadbalancer) |
| v2.7.4 | 2026-04-24 | router module part 2 (circuit breaker/rate limit) |
| v2.7.5 | 2026-04-25 | monitor module independence (98 files) |
| v2.7.6 | 2026-04-26 | persistence module independence (49 files) |
| v2.7.7 | 2026-04-27 | common module independence (96 files) |
| v2.7.8 | 2026-04-28 | controller grouping optimization |
| v2.7.9 | 2026-04-29 | package structure completion |
| v2.7.10 | 2026-07-13 | Technical debt cleanup - large file splitting + deprecated code removal |
| v2.7.11 | 2026-07-14 | RBAC permission control + UI optimization |

#### Performance Improvements
- Route flow optimization: 20-50%
- Memory usage reduction: 15%
- Startup time reduction: 30%

---

### ⏸️ v3.0 - Microservices Architecture (Indefinitely Postponed)

**Status**: ⏸️ Postponed

The v3.0 microservices architecture transformation has been indefinitely postponed as the current monolithic architecture meets all requirements.

---

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

#### Planned 📋
- 📋 Google Gemini (v2.8.x)
- 📋 Cohere API (v2.8.x)
- 📋 AWS Bedrock (v2.9.x)

### Load Balancing Strategies

#### Implemented ✅
- ✅ Random
- ✅ Round Robin
- ✅ Weighted Round Robin
- ✅ Least Connections
- ✅ IP Hash
- ✅ Consistent Hash

#### Planned 📋
- 📋 Latency-based
- 📋 Cost-based
- 📋 Model Capability-based

### Rate Limiting Algorithms

#### Implemented ✅
- ✅ Token Bucket
- ✅ Leaky Bucket
- ✅ Sliding Window
- ✅ Warm Up
- ✅ Adaptive Rate Limiting

#### Planned 📋
- 📋 Distributed Rate Limiting
- 📋 User-based Rate Limiting
- 📋 API Key-level Rate Limiting

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

### Current Architecture (v2.7.x)
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
| Configuration | v2.8.x | 📋 Planned |
| Maintainability | v2.9.x | 📋 Planned |
| Microservices | v3.0 | ⏸️ Postponed |

### Tech Stack

#### Core Technology
- **Backend**: Spring Boot 3.5.5 + WebFlux (Reactive)
- **Frontend**: Vue 3 + TypeScript + Element Plus
- **Database**: H2 (embedded) + R2DBC
- **Cache**: Redis (optional)
- **Monitoring**: Prometheus + Grafana
- **Tracing**: OpenTelemetry + Zipkin

#### Data Storage
- **Default**: H2 embedded database
- **Production**: PostgreSQL / MySQL
- **Cache**: Redis (optional)

## Performance Targets

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
12. 📋 v3.3.0 candidates: rule persistence versioning, HOUR/MONTH quota limits, FULL encryption strategy UI, dry-run regression set
13. 📋 Semantic cache evaluation (vector-similarity reuse, separate project); high-availability foundation (multi-node/Redis) re-assessed after v3.2.x

### Long-term Vision
1. Become the standard in AI model routing
2. Build a complete ecosystem
3. Achieve enterprise-level commercial success
4. Drive industry technology development

---

**Last Updated**: September 22, 2026

For any suggestions or ideas, feel free to communicate with us via [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions).

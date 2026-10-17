# Roadmap

<!-- 版本信息 -->
> **Doc Version**: 2.2.2
> **Last Updated**: 2026-10-14
> **Git Tag**: v3.3.5
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

### ✅ v3.3.5 (Patch: store contract and health semantics fixed + error-body shapes corrected in docs, **released 2026-10-17**)

**Status**: Released (GitHub Release `v3.3.5`, 2026-10-17; multi-arch Docker images rebuilt by CI on release: `v3.3.5` / `3.3.5` / `latest`)
**Scope**: everything merged after the `v3.3.4` slice (10-16 morning), i.e. 2026-10-16

| Area | Delivered |
|------|-----------|
| **Store contract: deleting the last entry is no longer a silent no-op (#446)** | `JpaStoreManager.saveConfig` no longer bails out on an empty map (only the `null` guard remains, with a warn), aligning it with `BaseStoreManager` / `MemoryStoreManager` / `FileStoreManager`; "an empty map is a legal payload" is now part of the `StoreManager.saveConfig` contract. Affected paths: deleting pools, routing rules and configurable adapters. A new `JpaStoreManagerTest` drives the real implementation and asserts *what is left in the store*; the three `*PersistenceServiceTest` deletion assertions check the payload and add a real `MemoryStoreManager` delete-to-empty round trip |
| **Health semantics: the drop rate no longer makes `/actuator/health` answer 503 forever (#447)** | An over-threshold span drop rate now reports `WARNING` (HTTP stays 200; the numbers stay in the details for alert rules); only "async processor not running / CRITICAL memory pressure / performance monitor itself DOWN" still answer DOWN. The same block's "each check overwrites the previous builder" loss of issues is fixed too. A new `TracingHealthIndicatorTest` (previously zero coverage) adds 8 cases |
| **Error-body shapes corrected (#448)** | On `/api/**`, the authentication chain's own 401/429 are protocol-shaped, an authorization-layer 403 is `text/plain`, and only controller-thrown errors are `RouterResponse` ⇒ the client rule is now "is there a top-level `error` field?". The `claude-code.md` claim that both surfaces use `RouterResponse` (wrong for `/v1` too) is corrected alongside (zh/en symmetrical) |
| **Other** | A missing request body answers 400 (#432) · the en TTS/STT prefixes corrected to `/api/v1/audio/*` (#436) · `快闸` triggers on the config tree and workflow paths · the branch-cleanup scripts landed |

**Local verification before this release**: frontend E2E **414/414**, API smoke **51/51** (cold start on a fresh H2 database → frontend build → package → run on 8080).

### 🏷️ v3.3.4 (Patch: error-code rulings landed + close-out + docs relocation, **shipped in v3.3.5** 2026-10-16)

**Status**: Shipped in the `v3.3.5` GitHub Release (2026-10-17)
**Scope**: everything merged after the `v3.3.3` slice (10-15), i.e. 2026-10-15 → 2026-10-16

| Area | Delivered |
|------|-----------|
| **Rate-limit error-code semantics (#404, decided as "429 plus a new code")** | The two real paths became two codes, both aligned to 429: the admin rate limiter keeps `RATE_LIMIT_EXCEEDED` (flat 429 body `{"code":...}`), while the API Key validation chain uses the new `RATE_LIMITED_API_KEY` (`FullValidationResult` now carries the registry status into the `AuthenticationException`); the authentication filter no longer hardcodes 401 but passes through the status the exception carries, and the `type` in the `/v1/**` and legacy shapes is always derived from the status (429 ⇒ `rate_limit_error`). Registry 62 → 63 codes; clients no longer have to treat 401 as a rate limit |
| **Error-code status alignment (issue #405, hybrid scheme)** | Endpoint status and rule-chain status now agree at both ends: `BUILTIN_READONLY` answers **403** everywhere (the endpoint used to answer 400) and `RESTART_REQUIRED` answers **400** everywhere (`ApiException.legacyResolveStatus` used to derive 403); the registry descriptions, both error-code tables and the `ErrorCodeRegistryTest` "divergence set" assertion were updated with it |
| **Dead codes deleted and docs corrected (#406 / #412 / #414)** | Seven codes with no emission site (constants plus never-called factories) deleted; four never-emitted codes in the two `management-api` tables replaced with registry names; `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE` restated as an internal constant rename (invisible to clients) |
| **Internal docs relocated (#418)** | Session memory (`mimo-memory/`, 393 files; `session-digests/`) and two internal process SOPs moved out of the published docs site into `innerdoc/`; 10 false positives from the local docs scanner fixed and two orphan `mkdocs` nav entries removed |

### 🏷️ v3.3.3 (Patch: error-code registry wired through code and docs + frontend artifact hygiene; ships the `v3.3.2` content, **shipped in v3.3.5** 2026-10-15)

**Status**: Shipped in the `v3.3.5` GitHub Release (2026-10-17)
**Scope**: everything merged after `v3.3.1` (2026-10-12 → 2026-10-15) — `v3.3.2` was tagged but never released on its own and is superseded by this version;
see the [changelog](changelog.md#333---2026-10-15)

| Area | Delivered |
|------|-----------|
| **Error-code governance (issue #370, plan A)** | The registry now holds **62 codes that are actually emitted** (code + client-visible status + **render path** + description, stage 2 #407); `resolveStatus` consults the registry **only for rule-chain codes** (every other path keeps zero behavior change); the zh/en error-code tables were rewritten against the registry (0 differences both ways); the docs gate now takes the registry as its authority and **fails in both directions** (#407); **no bare code literals remain in `src/main/java`** (~175 emission sites now read `ErrorCode.X.code()`, stage 3 #410), guarded by a new literal test. ⚠️ External contract change: `TOKEN_USAGE_FORBIDDEN` → `FORBIDDEN_TOKEN_USAGE` (that code is written into the token-usage response body, so clients do see it); separately, `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE` is only an internal constant rename inside `AuthorizationException` and has no emission site (clients never see it) (#403 / #406) |
| Error-code ledger | `RATE_LIMIT_EXCEEDED` has two statuses, 429/401 (#404 — **resolved by splitting the code after the `v3.3.3` tag**, see below); seven codes that existed only as constants plus never-called factories **were deleted** (#406); 30 failure responses used to carry no `errorCode` at all (single-argument `RouterResponse.error`), now tracked as #413 |
| Repository hygiene | Frontend build artifacts no longer dirty the working tree: normalized `package-lock.json` + `.gitattributes` `-text` for the two generated `.d.ts` files (#408 / #409) |
| Inherited from `v3.3.2` (shipped together) | `/v1` empty-stream fix (#356) · alert/audit close-out (#306/#309/#313/#394/#397/#400) · config-honesty pass 3 (#319/#322/#323/#329/#331) · dead code and shapes (#311/#316/#328/#332/#333/#369/#372/#373) · CI and documentation gates (#367/#371/#385/#388/#398/#399 plus the #250 review batches) · dependency security (Vue 3.5.44, #391) |

### 🏷️ v3.3.2 (Patch: empty `/v1` stream fixed + alert/audit close-out + config honesty pass 3 + docs and gates, **tagged, shipped in v3.3.5** 2026-10-14)

**Status**: **Tagged `v3.3.2`, never released on its own** — its content ships with `v3.3.3` inside the `v3.3.5` GitHub Release (2026-10-17)
**Scope**: the **40 changes** merged after `v3.3.1` (the 10-10 ~ 10-11 slice), i.e. 2026-10-12 → 2026-10-14 —
see the [changelog](changelog.md#332---2026-10-14)

| Area | Delivered |
|------|-----------|
| **Streaming (`/v1`)** | **Fixed: normal streams answered 200 with a zero-byte body** (#356) by removing the early body release caused by `exchangeToMono`; protocol error frames are emitted only after the first chunk, so a pre-commit failure keeps the downstream's real status (429 stays 429); the regression pin now drives **both surfaces** (the fixture used to hardcode `/gateway/stream` and never covered `/v1`) |
| Streaming baseline | Client-visible baseline probes (#298); `/v1` mid-stream error frame without `[DONE]` (#299); pre-commit status fidelity (#305) |
| Alerts & audit | Cross-replica exclusive cooldown (#306); event types always enum names, unknown names no longer fall back silently (#309); dead alert threshold/outlet config removed (#313) |
| Config honesty (pass 3) | Seven unbound `jairouter.security.audit` sub-blocks (#319), the whole `jairouter.security.performance` tree (#322) and 14 `audit.extended` keys (#323) removed; sanitization failures only terminate, error body belongs to the central handler (#329); the startup validator no longer claims a random password is generated (#331) |
| Dead code & shapes | `SecurityAuditApi` (#328), the whole `common/api` layer (16 files, #332) and `SecurityStatisticsResponse` (#311) removed; `DashboardOverview` typed to the real payload (#333); the sanitization test now drives the write path (#316) |
| Docs & gates | Facts-first README (#315) and link-plus-prose ledger (#330); docs realigned for the two deleted config trees (#325); "read the CI gates correctly" (#326) and the release-cadence rule (#362); error-code table rebuilt from code (#348); dynamic-config/quick-start examples rewritten against real endpoints and executed (#341/#349/#351/#353); fence-parity checker (#337) |
| Engineering & tooling | Local substitute gates (#336); image smoke polls container health (#338); streaming e2e fixture with the production processor in the chain (#347); docs-version wording single-sourced with the workflow now covering `scripts/docs/**` (#359/#360); the v3.3.1 entries added to master (#364) |

### ✅ v3.3.1 (Patch: Audit Remediation — Event-Loop Blocking / SSRF Connect-Time Pinning / Usage Ownership / Alert Wiring, released 2026-10-14)

**Status**: Released (Git tag / GitHub Release `v3.3.1`; the Docker image is rebuilt by CI on release)
**Scope**: the 2026-10-10 ~ 10-11 batch that landed after `v3.3.0` (**back-cut release**, cut point `ea2b7bc2`),
24 substantive changes; everything after that batch ships in `v3.3.2` — see the [changelog](changelog.md#331---2026-10-14)

| Area | Delivered |
|------|-----------|
| Event loop & blocking | API key usage accounting (#267) and error-report persistence (#268) moved off the event loop; JWT blacklist sync batched outside the lock (#273); `TRACING_APPENDER` attached via `AsyncAppender` (#279) |
| Security | Outbound resolved address pinned to the connection, closing DNS rebinding (#282); two self-built `WebClient` instances consolidated into the pool (#284); usage writes enforce ownership (#280); `SecurityAlertService` wired into the authentication-failure path (#277, it had zero callers before) |
| Security alerts & audit | Criteria aligned with audit event types with counting pushed to storage (#286); output consolidated to metrics + structured logs with an exclusive scheduled driver (#293); configuration surface unified and duplicate implementations removed (#294); the overview page now returns the real 24-hour alert count (#296) |
| Console & tracing | Trend charts use real statistics (#269); playground buffers residual lines across chunks (#270); search page shows an empty state on failure (#271); streaming no longer prints response content and supports cancellation (#274) |
| Cleanup & dependencies | Removed the unreachable `ApiKeyAuthenticationProvider` / `AuthenticationEventListener` (#275) and the unreferenced `useChatRequest` (#278); two comments realigned with the implementation (#272, #251); 4 frontend dependency bumps (#173/#225/#226/#252) |

### ✅ v3.3.0 (Minor: PostgreSQL & Versioned Migration + Multi-Replica Cluster + Config Honesty, released 2026-10-07)

**Status**: Released (Git tag / GitHub Release `v3.3.0`; the Docker image is rebuilt by CI on release)
**Scope**: the 24 features and 29 fixes merged after v3.2.2 — see the [changelog](changelog.md#330---2026-10-07)

| Area | Delivered |
|------|-----------|
| Persistence & migration | PostgreSQL support (dialect / datasource / index naming externalized; PG silently dropping indexes fixed, #160); Flyway with the current schema as baseline, PG on `ddl-auto: validate` (#190, #214); legacy patches became migration V2 and **H2 gets versioned migration too**, with both startup patch components deleted (#192); dedicated `migrate` entrypoint with meaningful exit code, non-web start and exit-after-run (#193, #195) |
| Multi-replica & cluster | Cross-replica real-time event broadcast with originating replica id (#164, #181); cross-replica sliding-window counting (#161); shared-state startup self-check and cache convergence (#162); per-class scheduled-job handling (#163) |
| Security & auth | RBAC closed out to fail-closed GETs (default `DENY_ALL`, reversible, #128); client-IP trust policy converged (#151); SSRF guard moved to `WebClientPool` (#114); JWT issuer validation plus optional fail-closed blacklist (#117, #118); JWT's dedicated Redis removed (#194, #196); incremental `RolePermissionSeeder` fixing silent permission rollback (#144) |
| Rate limiting & quota | Limiter concurrency, unbounded growth and XFF bypass fixed (#121–#124); quota reservation rollback (#119, #120); settlement / usage queries no longer block Redis (#105) |
| Streaming & frontend | Bounded stream accumulation plus upstream idle watchdog (#126, #127); storage writes moved off the EventLoop (#115, #125); throttled rendering, zombie WebSocket fix, no token logging, canvas leak fix (#129–#135) |
| Config honesty | Four batches on tracing config: 57 zero-consumer fields deleted, 3 wired up (`memory-usage` / `export-latency-p99` / `histogram-buckets`); sampling strategies wired with a console selector and a fix for saves wiping `rules` (#224, #234, #242) |
| Deployment & logging | Container stdout as structured JSON (`json-logs`, #211); Kustomize manifests plus compose port/probe/volume fixes and graceful shutdown (#165); WebClient and tracing scheduler parameters externalized (#182) |
| Docs governance | `ReviewedAt` review mechanism (#101); factual corrections across tracing / api-reference / landing pages, including an entirely fabricated section in the English `monitoring-api` and ~20 non-existent error codes (#159, #231, #235) |

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
| v3.3.2 | 2026-10-14 | Patch (**shipped in v3.3.5**): **empty `/v1` stream fixed** (#356 — no more early body release from `exchangeToMono`; error frames only after the first chunk; fixture now drives both surfaces) + cross-replica alert cooldown (#306) + audit event types always enum names (#309) + config honesty pass 3 (#319/#322/#323) + sanitization failures only terminate (#329) + startup-validator wording (#331) + dead code and shapes (#311/#328/#332/#333/#316) + docs and gates (#315/#325/#326/#330/#337/#341/#348/#349/#351/#353/#355/#362) + engineering (#336/#338/#347/#359/#360/#364) |
| v3.3.1 | 2026-10-14 | Audit remediation patch (**back-cut release**, cut point `ea2b7bc2`): API key usage accounting (#267) and error-report persistence (#268) moved off the event loop + JWT blacklist batched sync (#273) + `TRACING_APPENDER` async attachment (#279) + SSRF resolved-address pinning (#282) + outbound clients consolidated into a pool (#284) + usage-write ownership (#280) + `SecurityAlertService` wired in (#277) + alert criteria/output/config-surface close-out (#286/#293/#294/#296) + console and tracing placeholder data removed (#269–#274) + dead code removed (#275/#278) + 4 frontend dependency bumps |
| v3.3.0 | 2026-10-07 | PostgreSQL and versioned migration (PG on `ddl-auto: validate`, H2 joins in, both startup patch components deleted) + multi-replica cluster (cross-replica event broadcast and rate-limit counting, shared-state self-check, per-class scheduled jobs) + RBAC closed out to fail-closed GETs + four batches of tracing config honesty + Kustomize/json-logs deployment artifacts + documentation facts corrected (#160–#248) |
| v3.2.2 | 2026-09-22 | User-reported fixes: API key quota echo (#84) and routing-monitor count +3 (#83) + quota endpoint `block` failure (#92) + management failures moved from HTTP 200 to 4xx/5xx (#94, response body compatible) + tightened e2e assertions (#95) + unified alert thresholds `[0.05,1]` (#96) + monitoring error codes (#98) (PRs #90/#91/#93/#97/#99) |

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
- ✅ H2 embedded database (the only persistence database currently supported)
- 📋 PostgreSQL/MySQL support (planned; `pom.xml` only includes the H2 driver, and both the JDBC config and Hibernate dialect are hard-coded to H2 — no external database adapter yet)
- ✅ Redis cache integration
- ✅ Prometheus metrics collection
- ✅ Grafana dashboard templates
- ✅ Distributed tracing (Zipkin/OpenTelemetry)
- ✅ Complete Docker deployment
- 🟡 Kubernetes deployment support (**partial**: `deploy/k8s/base` plus the dev/prod overlays ship with the repository and were verified on a real cluster — the migration Job completes and three replicas become Ready; horizontal scaling is still bounded by the single-file H2 database, i.e. per-replica volumes with no shared state — see "Multi-Replica Status" in `deployment/kubernetes.md`)

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
- ✅ Distributed rate limiting (Redis sliding window `RedisSlidingWindowRateLimiter`, wired by `RateLimitManager`; **requires Redis**)

#### Planned 📋
- 📋 User-based Rate Limiting

> **Audit correction, 2026-10-16**: both "API-key-level" and "distributed" rate limiting were listed as planned but are
> **already implemented** (see `ApiKeyValidator.validateRateLimit` and `RateLimitManager`'s wiring of `RedisSlidingWindowRateLimiter`).
>
> **Audit correction, 2026-10-16 (second pass)**: "Adaptive Rate Limiting" was listed as implemented, but there is
> **no such implementation** anywhere in the repository (no such algorithm under `router/ratelimit/**`, and the other
> documentation pages list only token-bucket / leaky-bucket / sliding-window / warm-up) — the line was removed.

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
- **Horizontal scaling blocked**: The only supported store is the H2 embedded single-file database, so multiple replicas cannot share one data file and `replicas` must stay at 1; rate limiting, quota, JWT blacklist, and API-Key caches are all per-process by default. Planned order of work: issue #160 (shared database adapter), #161 (cross-instance rate limiting), #162 (shared-state wiring), #163 (scheduler locks), #164 (real-time event broadcast), #165 (K8s artifacts)

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
13. 📋 v3.4.0 candidates: rule persistence versioning, HOUR/MONTH quota limits, FULL encryption strategy UI, dry-run regression set
14. 📋 Semantic cache evaluation (vector-similarity reuse, separate project); high-availability foundation (multi-node/Redis) re-assessed after v3.2.x

### Long-term Vision
1. Become the standard in AI model routing
2. Build a complete ecosystem
3. Achieve enterprise-level commercial success
4. Drive industry technology development

---

**Last Updated**: September 22, 2026

For any suggestions or ideas, feel free to communicate with us via [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions).

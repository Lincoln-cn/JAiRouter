# Changelog

<!-- 版本信息 -->
> **Document Version**: 3.3.5
> **Last Updated**: 2026-10-16
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->



This document records the version update history and important changes of the JAiRouter project.

## Versioning Scheme

JAiRouter follows the [Semantic Versioning](https://semver.org/) specification:

- **Major Version**: Incompatible API changes
- **Minor Version**: Backward-compatible new features
- **Patch Version**: Backward-compatible bug fixes

## Version History

### [3.3.5] - 2026-10-16 - Patch Release (store contract and health semantics fixed; error-body shapes corrected in docs)

> ⚠️ **One client-visible change** (please check your client): **on the `/api/**` surface the error-body shape depends on who writes it** —
> the authentication filter's own responses (401 `AUTH_MISSING`, 429 `RATE_LIMITED_API_KEY`) are protocol-shaped
> `{"error":{"message":…,"type":…,"code":…}}`, an authorization-layer 403 is `text/plain` `Access Denied`, and only
> controller-thrown errors are `RouterResponse`. **The client rule is: is there a top-level `error` field?**
> This release **changes no runtime behavior and no status code** — that item documents the existing implementation as measured (#448).
>
> **Tagged `v3.3.5`**; per the standing release policy no GitHub Release or Docker image is produced yet.
> This entry covers everything merged after `v3.3.4`.

#### Store contract: deleting the last entry is no longer a silent no-op (#446)

- **Symptom**: deleting the only remaining entry of a collection answered `200 {"success":true}` without deleting it.
  Pools came straight back (the controller re-reads persistence via `reloadActivePools()` after deleting); a configurable
  adapter looked deleted until the **process restarted**.
- **Root cause**: `JpaStoreManager.saveConfig` returned early on an empty map (`config.isEmpty()` → `return`), while
  `PoolPersistenceService` / `AdapterDefinitionPersistenceService` / `RuleDefinitionPersistenceService` all do
  "`loadAll()` → `removeIf()` → `saveAll()`", so removing the final entry passes exactly that empty map — the write was
  skipped and the old value stayed in the database.
- **This was not a convention but a one-sided divergence**: `BaseStoreManager`, `MemoryStoreManager` and
  `FileStoreManager.doSaveConfig` all persist the map as given, empty maps included; only `JpaStoreManager` bailed out.
- **Fix**: the early return is gone (only the `null` guard remains, now with a warn), and "an empty map is a legal payload"
  is part of the `StoreManager.saveConfig` contract (use `deleteConfig` to clear a key).
- **Regression guards**: a new `JpaStoreManagerTest` drives the real implementation (only the JPA repository is replaced by
  a stateful stand-in) and asserts *what is left in the store*, not *that a method was called*; the three
  `*PersistenceServiceTest` deletion assertions now check the payload, each with an added "delete to empty → read back
  empty" round trip over a real `MemoryStoreManager`. The old tests missed all of this because they **mocked
  `StoreManager`** — the mock has no such early return, so "`saveConfig` was called" was vacuously true.
- Present since `c86fe199` (2026-04-28, v2.7.6) — **not a regression introduced after v3.3.1**.

#### Health semantics: the span drop rate no longer makes `/actuator/health` answer 503 (#447)

- **Symptom**: once the **lifetime-cumulative** span drop rate passed 10%, the aggregate `/actuator/health` answered `503`,
  and it could only return below the threshold after a restart. All three `docker-compose*.yml` files use that endpoint as
  the container healthcheck ⇒ the container was permanently unhealthy, while restarting does not stop spans from being
  dropped. Measured: `tracing=DOWN` (`issue=数据丢弃率过高: 50.44%`) with every other component `UP`.
- **Fix**: an over-threshold drop rate now reports `WARNING` (HTTP stays 200, the numbers stay in the details for alert
  rules); only "async processor not running", "CRITICAL memory pressure" and "performance monitor itself DOWN" still
  answer `DOWN`. The same block also lost issues because each check overwrote the previous builder
  (`healthBuilder = Health.down()...`), discarding the earlier status *and* its details; it now collects `downIssues` and
  `warnings` separately and decides once.
- **Regression guards**: a new `TracingHealthIndicatorTest` (that class had **zero** coverage) with 8 cases, including
  "an over-threshold drop rate must not be DOWN" and "multiple DOWN-level issues must not overwrite each other".
- The Kubernetes probe surface is unaffected (`liveness` includes only `livenessState,ping`; `readiness` only
  `readinessState`). Present since `deffede2` (2026-04-28, v2.7.5) — not a regression after v3.3.1.

#### Docs: `/api/**` error-body shapes corrected against measurement (#448)

- The authentication filter is the **single render point** for authentication-chain state
  (`SpringSecurityAuthenticationFilter:287-319`, `:339-383`) and takes a hard-coded shape branch on non-`/v1` paths, so
  `/api/**` 401/429 are protocol-shaped; an authorization-layer 403 is written as `text/plain` by Spring Security's
  default handler. A controller-thrown 403 (for example `BUILTIN_READONLY`) *is* `RouterResponse`.
- Corrected in `api-reference/{error-codes,universal-api,index}.md` and `getting-started/claude-code.md` (zh/en
  symmetrical); the latter claimed gateway errors use `RouterResponse` on **both** `/v1` and `/api`, which is wrong for
  `/v1` as well.

#### Other

- **Client-visible status fix (#432)**: a missing request body no longer becomes `500 + INTERNAL_ERROR`; three
  `@RequestBody` endpoints pre-validate and answer **400**. Constraint: the check must sit **outside** the method's own
  `try`, or the blanket `catch (Exception)` swallows it and re-wraps it as 500.
- **Documentation↔code contract (#436)**: the en TTS/STT endpoint prefixes were corrected to `/api/v1/audio/*`
  (`/v1/audio/*` has no mapping at all — the en side was wrong).
- **Gates**: `快闸` also triggers on `src/main/resources/config/**` and `.github/workflows/**`; the workflow-syntax
  validator was repaired and wired into the gate with a self-test that includes refutation cases.
- **Tooling**: the branch-cleanup scripts landed (content criteria instead of ancestry under squash merges, with a
  synthetic-repo self-test).

#### Local verification before this release

Cold start on a fresh H2 database → frontend build → package → run on 8080: **frontend E2E 414/414** (14 specs;
404/407 before the fixes); **API smoke 51/51** (covering every client-visible change since v3.3.1: #404 / #405 / #432 /
both `/v1` error shapes / the #340 `/actuator` auth surface).

### [3.3.4] - 2026-10-16 - Patch Release (error-code rulings landed, close-out and docs relocation)

> ⚠️ **Two client-visible changes** (please check your client):
> ① **`PUT`/`DELETE /api/config/adapter/{name}` on a built-in adapter changed from `400` to `403`** (`errorCode` stays `BUILTIN_READONLY`) — authorization semantics should answer 403 (#405);
> ② **API-key per-minute rate limiting changed from `401` to `429`**, and a **new code `RATE_LIMITED_API_KEY`** was added (the `/v1/**` protocol `type` therefore changes from `authentication_error` to `rate_limit_error`) — clients no longer have to treat 401 as a rate limit (#404).
> Apart from those two, this release **changes no other client-visible status code**: the registry went 62 → 63 (one addition) and both error-code tables are reconciled **both ways** by the docs gate.
>
> **Tagged `v3.3.4`**; per the standing release policy no GitHub Release or Docker image is produced yet.
> This entry covers everything merged after `v3.3.3`.

#### Error codes: rulings landed and close-out

- **Rate-limit code split (#404)**: per the "429 plus a new code" ruling the two real paths are split — the admin rate limiter
  keeps `RATE_LIMIT_EXCEEDED` (flat 429 body) while the API-key validation chain uses the new `RATE_LIMITED_API_KEY` (registered as 429);
  the authentication filter no longer hardcodes 401 but **passes through the status carried by the exception**, and all three error
  `type` fields are derived from that status via `V1ErrorBodyMapper.errorTypeOf` (same source as the mapper). `RATE_LIMIT_EXCEEDED`'s
  render path became `ERROR_BODY` (it is only written as a flat body by the admin limiter now). **The 401 shapes are byte-identical**
  (three legacy paths carry byte-level assertions that were checked against the pre-change implementation).
- **Status alignment (#405)**: `BUILTIN_READONLY` answers 403 everywhere (the endpoint used to answer 400) and `RESTART_REQUIRED`
  answers 400 everywhere (the rule chain used to derive 403); two `@ApiResponse` annotations were synced (they contradicted the implementation).
- **Dead codes deleted and docs corrected (#406 / #412 / #414)**: seven codes that existed only as constants plus never-called
  factories with no emission site were deleted; the docs now separate the internal `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE`
  constant rename from the real client-visible rename; four never-emitted codes in the two `management-api` tables were replaced
  with registry names.

#### Repository and documentation hygiene

- **Internal docs relocated (#418)**: the session memory (`mimo-memory/`, 393 files; `session-digests/`) and two internal process
  SOPs (`development/{doc-maintenance,knowledge-base-governance}.md`, one pair per language) moved out of the published docs site
  into `innerdoc/`; this also removed 10 false positives from the local docs scanner and two orphan nav entries from `mkdocs`.
- **Version and ledger (#417)**: version 3.3.3 → 3.3.4; the README / roadmap "unreleased" ledger was synced.

#### Known open items (ledger)

- #413 (30 failure responses carry no `errorCode`, single-argument `RouterResponse.error`) · #419 (the error-code docs gate only
  covers the two main tables) · #420 (two places on the authentication chain still follow the old semantics: the entry point's
  hardcoded 401 and the "rate limit logged as an authentication failure" wording).

### [3.3.3] - 2026-10-15 - Patch Release (error-code registry wired through code and docs, frontend artifact hygiene; ships the `v3.3.2` content too)

> ⚠️ **External contract change (#403)**: `TOKEN_USAGE_FORBIDDEN` → **`FORBIDDEN_TOKEN_USAGE`** — that code is written
> into the token-usage response body, so clients do see it; the category always lives in the **prefix** position
> (the only historical suffix exception is `*_NOT_FOUND`), so clients that hard-coded the old name must be updated.
> Separately, `RESOURCE_FORBIDDEN` → **`FORBIDDEN_RESOURCE`** is only an **internal constant rename** inside
> `AuthorizationException`: that code has no emission site anywhere in the repository (its factory method is called
> only by unit tests), so no client can ever receive it ⇒ it is **not** an external contract change (recorded as #406).
> Apart from that one renamed code this release **changes no status code and no error-code set**: stage 3's migration
> was re-verified — "codes resolved at emission sites = the registry's 62 codes, one by one".
>
> **Tagged `v3.3.3`**; per the standing release policy no GitHub Release or Docker image is produced yet.
> This entry covers everything merged after `v3.3.1` — `v3.3.2` was tagged but **never released on its own**, and is
> superseded by this version (its content is the next entry).

#### Error-code governance: the registry is now the single source of truth (issue #370, plan A)

- **Stage 1 (#402)**: added the `common/constants/ErrorCode` registry and made `ApiException.resolveStatus` consult it
  first, with the original rule chain extracted verbatim as `legacyResolveStatus` (**zero behavior change**, pinned by
  invariant tests).
- **Stage 2 (#407)**: the registry was completed to **62 codes that are actually emitted**, each carrying
  "code + client-visible status + **render path** + description"; `resolveStatus` now consults the registry **only for
  rule-chain (`API_EXCEPTION`) codes** (the authentication, sanitization, response-body and error-body paths keep their
  own status source, so behavior is unchanged). The zh/en error-code tables were rewritten against the registry
  (0 differences both ways: 33 missing codes added, 7 "constant + never-called factory only" codes removed,
  `DOWNSTREAM_SERVICE_ERROR` registered), and `scripts/docs/check-error-codes.py` now takes the registry as its
  authority and **fails in both directions** (a registry code missing from the docs, or a documented code that can never
  be emitted); its 11 self-tests include three counter-proofs.
- **Stage 3 (#410)**: **no bare code literals remain in `src/main/java`** — roughly 175 emission sites now read
  `ErrorCode.X.code()` (exception-class constants derive from the registry, the duplicated constant in
  `SecurityExceptionHandler` is gone, the admin rate limiter's hand-written JSON body and the authentication filter's
  variable-carried codes are migrated too). A new `ErrorCodeLiteralGuardTest` fails if a registry code ever appears as a
  bare literal again (the exemption list must match **exactly**, each entry carrying its reason).
- Three pre-existing semantic problems the registry surfaced on the spot are recorded as their own ledger entries
  (this release only registers them, it **changes no behavior**): `RATE_LIMIT_EXCEEDED` has two statuses on two real
  paths (429 from the admin limiter, 401 on the API Key validation chain — #404); `BUILTIN_READONLY` /
  `RESTART_REQUIRED` are returned with 400 by their endpoints while name resolution derives 403 (#405); seven codes
  exist only as constants and never-called factories with no emission site anywhere (#406).
- Also fixed in the error-code reference: the zh table that a blockquote had **truncated**, and the invented
  "`5xx`/`502`/`503`/`504` patterns" replaced by the real `DOWNSTREAM_SERVICE_ERROR`.

#### Repository hygiene

- **Frontend build artifacts no longer dirty the working tree (#408 / #409)**: committed the npm-11.2.0-normalized
  `frontend/package-lock.json` and added `.gitattributes -text` for `auto-imports.d.ts` / `components.d.ts`
  (disabling line-ending conversion) ⇒ after a frontend-including build (`npm install` + `vue-tsc && vite build`),
  `git status` stays empty.

#### Shipped together: the `v3.3.2` content (tagged, never released on its own)

> See the next entry — the `/v1` empty-stream fix, the alert/audit close-out, config-honesty pass 3, dead-code and
> shape clean-up, CI and documentation gates, and the dependency fix (Vue 3.5.21 → 3.5.44), 40 items in total.

### [3.3.2] - 2026-10-14 - Patch Release (Empty `/v1` stream fixed, alert/audit close-out, config honesty pass 3, docs and gates)

> This entry covers the **40 changes** merged after `v3.3.1` (the 2026-10-10 → 10-11 slice), i.e. 2026-10-12 → 2026-10-14.
> **Tagged `v3.3.2` but never released on its own**; its content ships with `v3.3.3` (see the entry above).

#### `/v1` streaming empty body fixed (the most important availability fix here)

- **Fixed: `/v1` normal streaming answered `200` + `text/event-stream` with a zero-byte body** (#356).
  The `/v1` branch used `exchangeToMono` and handed `clientResponse.bodyToFlux(...)` back out of the exchange
  scope; `Mono.just(ResponseEntity)` terminates immediately, so WebClient released and drained the body and the
  framework subscribed to nothing. It now uses `retrieve()` with a status-preserving `onStatus`, so the normal
  stream takes the same `bodyToFlux` path as the legacy surface
- **Protocol error frames are emitted only after the first chunk**: before the first chunk the status is not
  committed yet, so the exception propagates unchanged and the client keeps the downstream's real status
  (429 stays 429); after the first chunk the error frame is still emitted before terminating (never `[DONE]`)
- **Regression pin**: `StreamingErrorPathRuntimeTest` now drives **both** surfaces (legacy and `/v1`, plus the
  Anthropic surface). It previously hardcoded the client path to `/gateway/stream`, so it **never covered the
  `/v1` branch** — which is exactly why this defect escaped the suite
- Knock-on: `/v1/messages` (Anthropic surface) shares that branch and was equally empty before

#### Streaming and protocol baseline

- Client-visible baseline for streaming failures pinned by probe classes as the basis and regression pin for #257 (#298)
- `/v1` mid-stream failures emit a protocol-shaped error frame and still terminate with the exception, **never `[DONE]`** (#299)
- `/v1` ends with the downstream's real status before the response commits; legacy semantics unchanged (#305)

#### Alerts and audit semantics

- Security-alert cooldown is now **exclusive across replicas**, removing duplicate alerts and inflated counts (#306)
- Audit event types are always **enum names**; `parseEventType` no longer silently falls back to `SYSTEM_MAINTENANCE`
  (enum 17 → 26; ⚠️ rows written before this release mixed several sources and **cannot be split retroactively**) (#309)
- Dead alert-threshold and outlet config removed (#313)

#### Config honesty (third pass — same rule: wire it up or delete it)

- Seven unbound sub-blocks plus two unread keys under `jairouter.security.audit` deleted (#319)
- The whole `jairouter.security.performance` tree (no binding class) deleted (#322)
- Fourteen zero-consumer keys under `audit.extended` deleted (`extended.enabled` is live and kept) (#323)
- Sanitization failures now **only terminate** — the error body belongs to the central handler, pinned by tests (#329)
- The startup validator no longer claims a random password gets generated; it describes the real fallback and its consequences (#331)

#### Dead code and shapes

- `SecurityAuditApi` removed (no implementation, no caller) (#328)
- The whole `common/api` module-boundary layer removed (16 files, no implementation and no caller anywhere) (#332)
- The producer-less `SecurityStatisticsResponse` DTO removed (#311)
- Console `DashboardOverview` typed to the real payload; dead write-only store and variables removed (#333)
- The response-sanitization test now actually drives the response write path (its `setUp()` never stubbed
  `isEnabled()`, so the entire class was a no-op) (#316)

#### Documentation and governance

- README rewritten as a facts-first document, with the production startup secrets requirement (#315); the ledger
  became "links plus classified prose" (#330)
- Documentation realigned with code for the two deleted config trees (16 files carried never-working examples) (#325)
- Contribution guide gained "Before merging: read the CI gates correctly" (#326) and later a release-cadence rule
  plus an executable release checklist (#362)
- The error-code table was rebuilt from code: the English side aligned with Chinese, and both quick-reference tables
  re-checked against real literals (#348)
- `dynamic-config`'s instance-management and file-configuration sections rewritten against real endpoints
  (nine non-existent endpoints removed) (#349 / #351 / #353)
- The quick-start instance example made genuinely runnable (path corrected, auth added) and executed line by line (#341)
- The `"status": "HEALTHY"` example removed from the docs — that value takes the instance out of routing (#355)
- Documentation review batch: 3 pages corrected, 1 defect spun off, 2 pages recorded as `ReviewedAt` (#343 / #346)
- Six files / ten markdown fence typos fixed, plus a reusable fence-parity checker (#337)

#### Engineering and gates

- Local substitute gates for the month CI minutes ran out (scripts + protocol + dependency/ignore fixes) (#336)
- The image smoke test polls container health instead of racing it (#338)
- End-to-end fixture for the streaming error path, putting the **production processor** in the chain (#347; it found
  #354 and #356 the same day)
- The docs-version script's console wording is now a single source of truth, its self-test no longer asserts a copy of
  that wording, and the workflow's path filter now includes `scripts/docs/**` (previously changing the script did not
  trigger it at all — the second reason #352 slipped through) (#359)
- Regression pin for "repeating `--mark-reviewed` must not silently drop arguments" (#360)
- The `v3.3.1` back-cut entries were added to master's changelog/roadmap (#364)

> **Breaking changes**: none to public APIs or routes. **Behavior changes to note**:
> (1) `/v1` pre-commit failures now end with the downstream's real status (previously an empty body / forced 500);
> (2) unknown audit event names no longer fall back silently; (3) on sanitization failure the client receives the
> exception with the error body rendered by the central handler.

### [3.3.1] - 2026-10-14 - Patch Release (Audit Remediation: Event-Loop Blocking, SSRF Connect-Time Pinning, Usage Ownership, Alert Wiring)

> **This is a back-cut release**: its content is the **2026-10-10 to 10-11 batch** that landed after
> `v3.3.0` (cut point `ea2b7bc2`), i.e. **24 substantive changes** (4 frontend dependency bumps,
> 1 documentation comment fix and 19 audit-remediation items). Everything that landed after that batch
> (streaming error semantics fidelity, config honesty close-out, docs and tooling batch) ships in **`v3.3.2`**.
> It therefore does **not** represent the state of `master` at release time.

#### Event loop and blocking (availability)

- API key usage accounting no longer blocks the event loop when writing to the database (#267)
- Error reporting persistence no longer blocks the event loop (#268), so an incident no longer amplifies latency
- JWT blacklist cache synchronization now performs **batched checks outside the lock** instead of blocking Redis per entry (#273)
- The tracing `TRACING_APPENDER` is now attached through an `AsyncAppender` (#279)

#### Security

- Outbound validation now **pins the resolved address to the connection**, closing the DNS-rebinding bypass (#282)
- Two services that built their own `WebClient` now share `WebClientPool`, so the outbound guard covers them too (#284)
- `/api/token-usage/record` now **enforces ownership** on writes and rejects cross-subject reports (#280)
- `SecurityAlertService` is wired into the authentication-failure path — before this it had **zero callers**,
  so none of its four security alerts ever fired (#277)

#### Security alerts and audit semantics

- Alert criteria are aligned with audit event types and counting moved down to the storage side (#286)
- Alert output is consolidated to **metrics + structured logs**, and window aggregation is driven by an exclusive scheduled job (#293)
- The security-alert configuration surface is unified; duplicate implementations and a "configured but never read" notification block were removed (#294)
- The overview page now returns the **real 24-hour security alert count** (#296)

#### Console and tracing

- The tracing dashboard trend charts now use real statistics instead of random placeholder data (#269)
- The playground stream parser now buffers **residual lines across chunks**, so long answers no longer lose characters (#270)
- The tracing search page shows an empty state on failure instead of placeholder trace data (#271)
- The streaming path no longer prints response content to the console, and cancellation is now propagated (#274)

#### Cleanup and corrections

- Removed the unreachable `ApiKeyAuthenticationProvider` and `AuthenticationEventListener` (#275)
- Removed the unreferenced `useChatRequest` (#278)
- Corrected the `StatePersistenceManagement` comment that contradicted the implementation (#272)
- Corrected the circuit-breaker instance-identifier comment that stated the opposite of the code (#251)
- Frontend dependency bumps: axios 1.18.0 → 1.20.0 (#173), postcss-selector-parser 7.1.4 → 7.1.6 (#225),
  source-map-js 1.2.1 → 1.2.2 (#226), brace-expansion 5.0.9 → 5.0.12 (#252)

> **Breaking changes**: none to public APIs or routes. Four **intentional behavior changes**:
> (1) **security alerts start firing for real** — with zero callers before this release none of the four
> alerts ever fired, so operators will newly see the alert metric (`jairouter.security.violations`) and WARN logs;
> (2) cross-subject usage reports to `/api/token-usage/record` are now rejected;
> (3) the latency profile of the write paths above changes (from synchronous on the event loop to asynchronous/batched);
> (4) outbound SSRF validation is stricter (the resolved address is pinned to the connection).

### [3.3.0] - 2026-10-07 - Minor Release (PostgreSQL & Versioned Migration, Multi-Replica Cluster, Config Honesty)

> **24 new features and 29 fixes** have landed since v3.2.2 (2026-09-22). Three main threads:
> (1) **Persistence** — PostgreSQL support, Flyway versioned migration (PG switches to `ddl-auto: validate`,
> H2 joins in), and the removal of both startup patch components; (2) **Multi-replica** — cross-replica
> real-time event broadcast and rate-limit counting, shared-state startup self-check, per-class handling of
> scheduled jobs; (3) **Config honesty** — every tracing config field that bound but had zero consumers was
> either wired up or deleted.
>
> **Breaking changes**: none to public APIs, routes, or config keys. Three **intentional behavior changes**:
> (1) console failure responses already moved from HTTP 200 to 4xx / 5xx in v3.2.2; (2) RBAC misses for both
> writes and GETs are now **fail-closed** (default `DENY_ALL`, reversible, see #128); (3) the tracing
> `monitoring.health` thresholds now use **debounce** semantics (N consecutive failures before unhealthy).
> **Config keys**: a batch of keys that never took effect was deleted — see "Config honesty". Old deployments
> that set them lose them silently, but they never did anything.

#### Persistence & versioned migration

- PostgreSQL support, with dialect / datasource / index naming externalized; a duplicate-index bug that made
  PG **silently drop indexes** was fixed (#160)
- Flyway introduced with the current schema as baseline; the PostgreSQL path switches to `ddl-auto: validate` (#190, #214)
- The legacy-schema patch logic became a versioned migration (V2); **H2 gets versioned migration too**, and
  both startup patch components were deleted (#192)
- A dedicated migration entrypoint (`migrate` profile): exit code reflects the outcome, non-web mode starts,
  and it can exit after running (#193, #195)
- The patch components' `INFORMATION_SCHEMA` queries now scope to the current schema, fixing a silent no-op
  when several schemas share a database (#216)

#### Multi-replica & cluster

- Cross-replica **real-time event broadcast**, fixing silently missed console events; events and metrics now
  carry the originating replica id (#164, #181)
- Cross-replica **sliding-window counting** so allowed volume no longer scales with replica count; rate-limit
  key dimensions are configurable and missing dimensions are explicit (#161)
- Shared-state startup self-check and cross-replica cache convergence (#162)
- Scheduled jobs handled per class: external side effects take a cross-replica lock, file writers are
  per-replica named (#163)
- Two-instance end-to-end verification for cross-replica broadcast (#181)

#### Security & authentication

- RBAC closed out in three stages: endpoint-coverage self-check → fail-closed for writes (explicit exemption
  list) → fail-closed for GETs too (default `DENY_ALL`, reversible) (#128)
- Client-IP trust policy converged into a single component, fixing forged `X-Forwarded-For` polluting audit
  and tracing records (#151)
- SSRF guard moved down to the `WebClientPool` egress point (#114)
- JWT now validates the issuer; the blacklist can fail closed when unavailable (#117, #118)
- JWT's dedicated Redis removed, eliminating type ambiguity and duplicate bean registration under the full
  multi-replica switch set (#194, #196)
- Startup security checks: the skip switch has a single source of truth (#201); randomly generated passwords
  are no longer rejected as weak (#171) and strong random keys are no longer misjudged `VERY_WEAK` (#169)
- Authentication / authorization exceptions return 401 / 403 instead of falling into the 500 fallback (#205);
  `/actuator/health/**` is permitted so K8s probes are no longer blocked (#203)
- `RolePermissionSeeder` converges incrementally, fixing a **silent permission rollback** on already-initialized
  deployments (#144)

#### Rate limiting & quota

- Fixed concurrency safety, unbounded growth and XFF bypass in the limiter (#121–#124)
- Quota reservation rolls back on mid-flight failure; multi-window reservation no longer partially commits (#119, #120)
- Settlement and usage queries no longer block Redis synchronously on Reactor non-blocking threads (#105)

#### Streaming & frontend

- Streamed content accumulation is now bounded (with a buffer budget) plus an upstream idle watchdog (#126, #127)
- JPA / storage writes in the streaming completion callback moved off the EventLoop (#115, #125)
- Frontend: throttled rendering and persistence for streamed answers (#135); fixed the zombie WebSocket
  reconnect loop and un-aborted stream after unmount (#130–#132); request headers and tokens no longer printed
  to the console (#129); fixed the canvas leak from unreleased chart instances (#133, #134)

#### Config honesty (tracing)

- After auditing every tracing config field for "zero consumers / only echoed to `/actuator/info`", four
  batches were applied: 24 fields deleted, 23 more deleted; 3 wired up (`alerts.thresholds.memory-usage`,
  `export-latency-p99`, `metrics.traces.histogram-buckets`) with 10 deleted; and 6 `components.*.capture-*`
  fields that were not even echoed deleted (#224)
- Sampling strategies (`ratio` / `rule` / `adaptive`) are genuinely wired and configurable via `strategy`
  (#234); the console gained a strategy selector, and saving no longer wipes `sampling.rules` / `adaptive.*` (#242)
- `thread-pool.keep-alive` wired to a consumer (#220); `monitoring.health` thresholds wired up and switched
  to debounce semantics (#224)
- Dead keys in `tracing-base.yml` were each wired up or deleted, with a binding guard test (#215)
- Fixed the cross-window false red in the Redis sliding-window gating test (#227) and the wall-clock flakiness
  in `StatePersistencePerformanceTest` (#197)

#### Deployment & logging

- Container stdout switched to one structured JSON line per record (`json-logs` profile) (#211)
- Kustomize K8s manifests and probe groups added (#165); prod / dev compose port mapping, probe port and data
  volume paths fixed, graceful shutdown added (#165)
- Outbound `WebClient` connection parameters and the tracing monitor scheduler parameters externalized (#182)
- CI: `java-tests` gained a PostgreSQL service container and broader gated-test validation (#174)

#### Documentation governance

- `ReviewedAt` human-review mechanism added; staleness now judged per document type, fixing 9 false positives
  and 10 leaks (#101)
- Documentation facts checked against code and corrected: 10 tracing docs (50 key names), `config-reference`,
  the api-reference series and the tracing landing page — including an **entirely fabricated section** in the
  English `monitoring-api`, ~20 error codes that exist nowhere in the codebase, wrong Prometheus metric names
  (`model_router_*` instead of `jairouter_*`) and a `modelRouter` health component that does not exist (#159, #231, #235)

### [3.2.2] - 2026-09-22 - Patch Release (User-Reported Fixes + Console HTTP Status Semantics)

> Fixes the two user-reported issues — API Key quota not reflected in the console (#84) and the load-balancer monitor counter jumping by 3 per request (#83) — together with adjacent defects: quota endpoints unusable (#92), console business failures always returning HTTP 200 (#94), over-permissive frontend e2e assertions (#95), inconsistent quota alert threshold (#96), and inconsistent error codes in the monitoring controller (#98). Bug fixes only: no new features, no breaking changes to public APIs, routes, or config keys. Note that **console failure responses now use 4xx / 5xx instead of HTTP 200** — an intentional behavior change documented below.

#### API Key Quota

- **Fixed incomplete quota field submission when creating / editing an API Key**, which made the quota configured at creation time display incorrectly in the console (#84)
- **Fixed quota update / reset endpoints failing 100% of the time**: both called `block()` on a Reactor non-blocking thread, which WebFlux forbids, so the quota drawer's save, batch reset and reset-all operations never worked (#92)
- **Unified the alert threshold to the closed interval `[0.05, 1]`**: the create dialog used `[0,1]`, the drawer used `[0.05,1]`, and the server enforced the open interval `(0,1)`. `QuotaLimits` is now the single source of truth (range constants plus a validation method) and is called by both write paths; the create path performed no validation at all before this release (#96)
- Added a frontend e2e regression case asserting that a quota entered in the create dialog is actually persisted and can be read back

#### Load Balancing and Routing Monitor

- **Fixed the same instance being selected three times within one request**: the request main chain, `BaseAdapter.processRequest` and `BaseAdapter.getWebClient` each selected an instance, so the "instance distribution" counter advanced by 3 per request (user observed 12 → 15) (#83)
  - The adapter now reuses the already-selected instance to build its `WebClient`, removing one duplicate selection (#87)
  - Added `SelectedInstanceHolder` (a request-scoped ThreadLocal) so the gateway and the adapter share a single instance selection — exactly one selection per request (#88)
- Fixed two more severe issues in the same area: the adapter-side selection did not pass request headers through, so **header-based rule / tag routing did not take effect on the adapter side** (the instance actually used could differ from the rule); and load-balancer `recordCall` advanced twice per request, skewing round-robin and least-connections state
- Documented the counter semantics: the number counts **instance selections**, not requests; failover and other legitimate re-selections add extra counts by design

#### Console HTTP Status Semantics

- **Fixed console business failures always returning HTTP 200**: controllers commonly wrapped errors in their own `onErrorResume`, turning failures into successful responses where the only signal was `success:false` in the body, so clients (scripts / SDKs / e2e tests) could not tell success from failure by status code (#94)
  - Added `ApiException` (carrying an error code and a target status, with a rule table mapping error codes to statuses) and the `ApiExceptions` helper, plus a matching branch in the global exception handler
  - All 138 production sites that disguised failures as 200 were migrated; `onErrorResume` catch-all branches now classify by exception type instead of always mapping to 500
  - **The migration is response-body compatible**: failure bodies remain the byte-for-byte unchanged `RouterResponse.error(message, errorCode)` — only the HTTP status changes
  - The frontend response interceptor now surfaces the backend error message, so a 4xx no longer shows only the HTTP client library's English message
- Tightened frontend e2e assertions (#95): five checks asserted only the status code and still passed when the endpoint actually failed; they now assert both the status code and the body's `success` field
- Unified `MonitoringController` client-parameter error codes (#98): ten client-input validations moved from `INTERNAL_ERROR` (500) to `INVALID_REQUEST` (400), while genuine server-side failures stay 500; test coverage was added for the auto-mode endpoint, which previously had none

#### Behavior Changes (Intentional)

- **Console endpoints now return 4xx / 5xx on business failure** instead of always HTTP 200. Scripts that judged success solely by "HTTP 200 plus body `success`" must switch to the status code, or check both
- The alert threshold no longer accepts `0`: creating or updating an API Key returns 400 when the threshold is out of range (below `0.05` or above `1`)

#### Engineering and Tooling

- Added an OpenAI-compatible mock model server for local integration and end-to-end testing
- Ignore the local `docker-ctx` directory

### [3.2.1] - 2026-09-21 - Patch Release (Audit Remediation: Concurrency/Streaming/Quota/Auth/SSRF/Regex Safety)

> Audit remediation batch merged after the v3.2.0 release (PRs #67–#72, #78–#81). Bug and security fixes only: no new features, no breaking changes to public APIs, routes, or config keys.

#### Concurrency & Streaming

- Health SSE interval snapshots moved off the EventLoop (`subscribeOn(boundedElastic)`); per-instance JPA N+1 and `tryEmitNext().orThrow()` removed
- Streaming completion and adapter metrics/call-history paths no longer `block(5s)` on SUMMARY sanitization
- Request main-chain synchronous section moved off the EventLoop (`Mono.defer(...).subscribeOn(boundedElastic())`)
- SSE / WebSocket sinks bounded with `onBackpressureBuffer(256, DROP_OLDEST)`
- Cold-path `block()` unified with timeouts (`ReactorTimeouts`: 5s normal, 30s startup)

#### Quota & Rate Limiting

- Fixed single-node `evaluate` → `reserve` TOCTOU (per-API-key serialization)
- Cross-instance distributed race now closed by an atomic Redis Lua CAS (HINCRBY + limit check + rollback); 429 semantics and `X-Quota-*` / `Retry-After` unchanged

#### Authentication & Authorization

- **Fixed the spurious 401**: the auth filter's `switchIfEmpty` was attached to a Mono containing `chain.filter`, so successful authentication still returned `AUTH_MISSING` (with a JWT session, the console AI pages and `/v1/**` were unusable)
- Console JWT sessions allowed to use AI endpoints; the tracing filter no longer runs the filter chain twice
- JWT blacklist degradation when storage is unavailable: single warning + 30s short-circuit window + startup storage probe
- `/ws/**` and health SSE now require authentication (frontend WebSocket sends a query token)
- RBAC gaps closed; debug endpoints disabled by default and redacted when enabled; Swagger / OpenAPI require credentials by default
- Frontend SSE reconnect timer unified and cleaned up on unmount

#### Startup & Configuration

- Factory-default JWT secrets rejected; production fails fast on missing or default secret (`StartupSecurityGate`)

#### SSRF & Regex Safety

- Instance URLs guarded by `SsrfGuard`: instance create/update, config sync, outbound `WebClient`, adapter connectivity tests
- New `SafeRegexValidator`: all user-supplied sanitization regexes go through one gate (512-char limit; nested quantifiers, quantified alternation, backreferences, oversized quantifiers rejected); all three compile paths of both sanitization engines plus config validation are funneled through it; the detector itself is now an O(n) character scanner that runs no regex over user data
- Incidental fix: `OptimizedSanitizationRuleEngine.compileRules` returned `null` through a Reactor `map` (latent NPE)

#### Intentional Behavior Change

- User-supplied patterns with nested quantifiers / backreferences / oversized quantifiers now get: HTTP 400 from `PUT /api/config/sanitization`; a config-validation error; runtime skip with a WARN. Response sanitization is disabled by default and the shipped default patterns are regression-covered

### [3.2.0] - 2026-09-19 - Minor Release (PII Governance & Quota Ops + Console E2E/UX)

> Includes PR #60 PII closed-loop, API Key quota ops (drawer/presets/batch reset), console hang fixes, admin API read/write rate-limit buckets, modular frontend E2E, and account/dashboard discoverability improvements. Version 3.2.0.

#### Quota Ops (API Key)

- `PUT /auth/api-keys/{id}/quota`, batch-reset, reset-all; remaining counts in quota detail
- Console quota drawer with presets; list loading isolated from slow quota APIs; short axios timeouts
- Management quota reads use in-memory ledger aggregation; JWT blacklist Redis fail-fast (300ms)

#### Admin Console UX (3.2.0)

- **`AdminApiRateLimiter` read/write split buckets**: GET/HEAD default 120/min·2000/h; mutations 30/min·200/h; POST create extra 10/h — console list refresh no longer starves write quota
- Limits configurable via `jairouter.auth.admin-api-rate-limit.*`
- Account management: icon buttons get `title`/`aria-label`; username hint “3–50 characters”
- Dashboard governance jumps: View Rules / Monitor Rate Limit / Monitor Circuit Breaker / Monitor Load Balancing

#### Quality

- Frontend modular E2E: `frontend/e2e/` (`npm run test:e2e`) — full-route smoke, auth guard, dialog skeleton, core CRUD (accounts/pools/API keys/blacklist), quota & PII deep tests; `test:e2e:legacy` keeps `console-v32.mjs`
- Permission-code assertions bind `PermissionCodes.ALL_PERMISSION_CODES.size()` (49, includes `security:sanitization:manage`)
- vitest unit/API tests; TDD service tests for quota ops; `AdminApiRateLimiterReadWriteTest`

### [3.1.2] - 2026-09-18 - Patch Release (Docker Startup & Usability Fixes)

#### Startup & Secrets

- **JWT secret environment variable unified to `JWT_SECRET`**: `application-{prod,staging,dev}.yml` now use nested placeholders such as `${JWT_SECRET:${PROD_JWT_SECRET:}}` — `JWT_SECRET` first, falling back to the legacy names; when both are unset the value is still empty, **preserving prod's fail-fast "must configure explicitly" semantics** (no built-in default key is shipped). Previously prod read `PROD_JWT_SECRET` while all three compose files passed `JWT_SECRET`, so the `docker compose` path could never start
- **`--generate-key` fixed**: generation logic moved out of the Spring lifecycle (`main()` intercepts the flag before `SpringApplication.run`). It was previously a `CommandLineRunner`, and JWT binding validation aborts during context assembly — so the tool could never run in exactly the case it exists for. Also fixed the `ENTRYPOINT` in 5 Dockerfile variants so `docker run <image> --generate-key` arguments reach the application
- **`secret` validation errors are now self-serviceable**: four error messages carry the variable name and generation method (original prefix substring preserved, so existing tests need no change)

#### Image & Console

- **Frontend packaging path fixed**: the image build script passed `-Pfast` (`frontend.build.skip=true`), so `frontend/dist` was never generated, `static/admin/` was never produced, and the console `GET /admin` returned 500. Build scripts now append `-Dfrontend.build.skip=false`, and the release workflow asserts the jar contains `BOOT-INF/classes/static/admin/index.html`
- Removed 454 stale committed frontend build artifacts from the repository (they were packaged into `classes/static/` at the wrong path, silently shadowing real build output)

#### Observability

- **Health-check false alarm fixed**: without Redis deployed, `/actuator/health` was always `DOWN` and the container always `unhealthy`. Root cause: Spring Boot's auto-configured Redis probe (`spring-boot-starter-data-redis-reactive` is marked optional but present on the runtime classpath) defaults to probing `localhost:6379`. Now `management.health.redis.enabled=${REDIS_ENABLED:false}`, and the hardcoded `enabled: true` override in `persistence-base.yml` is corrected. Deployments that do use Redis should set `REDIS_ENABLED=true`

#### Docs & CI

- Quick-start / installation guides now start in dev mode (built-in development key, zero config) and state that production requires `JWT_SECRET`; the broken `--generate-key` instructions are corrected; the bare `docker run` on the docs landing pages is fixed too
- `README` production examples aligned to `JWT_SECRET`; the "build from source" snippet's directory and jar name corrected
- Branch filters in `docs-version-management.yml` / `docs-content-sync.yml` corrected (they were `[main, develop]`, so they never fired on master), plus the two latent defects this exposed (`steps is not defined` and the `--output` argument mismatch)
- **New `image-smoke-test.yml` image cold-start smoke test**: build a jar with the frontend → assert `static/admin` is present → build the image → verify `--generate-key` produces a key without starting the app, that a dev cold start yields health UP plus `GET /admin` 200 and a healthy container, and that prod without a key fails fast with a self-serviceable error

#### Related

Parent issue #44 and its sub-issues #46 / #47 / #48

### [3.1.1] - 2026-09-15 - Patch Release (Protocol Correctness + Gateway Error-Message Readability + Console SSE Alert Fix + Doc Corrections)

#### Protocol Correctness

- `/v1/messages` **auth-failure 401** now returns Anthropic protocol shape (`{"type":"error","error":{…}}`); previously all paths returned OpenAI shape, so Anthropic-only clients (e.g. Claude Code) could not parse auth errors. `/v1/chat/completions` and other native surfaces keep OpenAI shape; `/api/**` response bodies are byte-identical
- `/v1/messages/count_tokens` now counts `tools` schema text and `tool_use` / `tool_result` block content; previously all ignored, which underestimated the total sharply (Claude Code conversations are dominated by `tool_result`, impact especially severe). `image` blocks still excluded

#### Readability

- `/v1/**` gateway error messages no longer carry legacy Chinese wrapping (e.g. `请求处理失败: 429 TOO_MANY_REQUESTS "…"`) or redundant quotes, outputting a human-readable reason directly; `/api/**` keeps existing message format unchanged

#### Console

- Fixed overview-page SSE false-positive connection errors on **route navigation / page refresh** (added teardown flag and `pagehide` fallback; real failures still alert and reconnect) — previously leaving the overview page triggered `SSE连接失败` / `读取SSE流时出错` in the console

#### Docs

- Corrected `claude-code.md` (zh/en) false claim about native-surface authentication: `/v1/**` **accepts both** `X-API-Key` (OpenAI surface) / `x-api-key` (Anthropic surface) and `Jairouter_Token` (JWT); when both present JWT takes priority; previously incorrectly stated "only API Key authentication is supported"

---

### [3.1.0] - 2026-09-14 - Feature Release (Token Quota Ledger + Multi-Protocol Entry: Multi-Dimensional Multi-Window Rate Limiting + Anthropic/Tool Calling + Runtime Config & Observability)

#### Quota Ledger

- **Multi-dimensional multi-window quota ledger**: supports MINUTE / HOUR / DAY / MONTH windows, with JPA-backed persistence for quota rules and usage records
- **Main-chain `reserve` + 429 semantics**: `reserve` pre-deducts before the main chain; exceeding the quota returns HTTP 429; both streaming and non-streaming paths execute `settle`
- **Redis distributed counting with disconnect fallback**: `QuotaCounterBackend` provides Local (in-memory) and Redis dual implementations; automatically falls back to local counting when Redis is unavailable
- **Disabled by default** (`jairouter.quota.enabled=false`), no impact on existing deployments

#### Console & Observability

- **Quota runtime configuration surface** (`GET/PUT /api/config/quota`): hot-changeable fields (`enabled`, `failOpen`, `windows`); restart-required fields (`distributed.*`, `retention`, `flushIntervalSeconds`) rejected with 400 `RESTART_REQUIRED`
- **Quota observability surface**: `GET /api/monitoring/quota/status` (quota status) and `GET /api/monitoring/quota/usage` (usage details)
- **3 new console pages**: quota runtime config (`/config/quota`), quota usage monitoring (`/monitoring/quota`), client access guide (`/tools/client-access`)
- **New permission codes**: `config:quota:read`, `config:quota:write`, `monitoring:quota:read`

#### Multi-Protocol Entry

- **`GET /v1/models`**: OpenAI-native model listing endpoint
- **Anthropic `POST /v1/messages`**: non-streaming and streaming SSE responses, `count_tokens` support, Claude Code direct connectivity
- **Tool calling**: `tools` / `tool_choice` / `tool_use` / `tool_result` triple protocol translation, streaming `input_json_delta` assembly
- **Protocol-shaped gateway errors**: `/v1/**` error bodies rendered in protocol shape (Anthropic / OpenAI dual shapes); `SecurityExceptionHandler` also protocol-aware
- **Error body charset fix**: explicit UTF-8 encoding, fixing garbled Chinese on platforms where the default charset is not UTF-8

#### Fixes

- **Streaming path instance-level headers ignored**: instance-level custom headers were not injected under `stream:true`, causing downstream 401; fixed
- **`RoundRobinLoadBalancerTest` statistical flaky fix**: eliminated probabilistic test failures
- **Response cache P1 merged**

#### Upgrade Notes

- Quota is disabled by default; existing deployments are unaffected. See `configuration/quota` docs for enablement.

---

### [3.0.3] - 2026-09-13 - Feature Release (Web complete-flow acceptance & release: journey acceptance + screenshot refresh + cold-start deep-link fix)

#### End-to-end journey acceptance

- **10 end-to-end journeys verified**: onboarding (4 steps) → 5 AI playgrounds (chat/embedding/rerank/audio/image) → Dashboard governance panel → routing rules → call history / tracing lookup; 0 page errors, 0 HTTP 5xx
- **Permission smoke**: login JWT carries 44 permission codes; 44 permission codes and 47 URL rules + PermissionClosureTest stay green

#### README screenshot refresh

- **Screenshot matrix replaces legacy images**: `screenshots/` goes from 6 images (2026-09-05) to 32 (8 pages × zh/en × light/dark), adding the slow-query analysis page and a dark-theme showcase
- **README.md / README-ZH.md references synced**: English README points at `-en-US-*`, Chinese README at `-zh-CN-*`; legacy filenames removed

#### Fixes

- **Cold-start deep-link fix (important)**: on F5 / opening `/security/*` or `/system/*` in a new tab, `userInfo`/`permissions` were not hydrated from the existing token, so the role guard (`meta.roles`) treated the user as role-less and redirected to the dashboard; the store now hydrates them from the token on initialization
- **i18n warning cleanup**: added `titleKey` to 12 parent routes (dashboard/config/loadBalancers/circuitBreakers/security/system/tracing/playground/exceptions/rateLimiters/monitoring/callHistory), removing the intlify fallback warnings; fixed key shadowing for `adapter.capability` / `adapter.test` (renamed to `capabilityColumn` / `testAction`)
- **Language switcher icon fix**: Element Plus has no `Globe` icon, so the original `<Globe/>` was never registered (console warning, icon not rendered); switched to `Coordinate` with explicit icon imports

#### Docs & plan

- roadmap / changelog updated symmetrically (zh/en); `innerdoc` development plan backfilled with v2.10.x / v3.0.x iterations and status

#### Quality

- Full mvn suite **3202 tests green**; frontend `vue-tsc` 0 errors + `vite build` passed; markdownlint 0; Playwright re-verification: 0 i18n/icon warnings, 6/6 deep links reachable

---

### [3.0.2] - 2026-09-07 - Feature Release (API Cleanup & Permission Closure: Deprecated Controller Removal + Permission Rule Backfill + Frontend Dead-Function Cleanup)

#### Deprecated controllers & dead-method cleanup

- **Removed 4 deprecated controllers**: ServiceConfigController (legacy `/api/services`), ServiceInstanceController (legacy `/api/instances`), InstanceConfigController (legacy `/api/instance-configs`), SecurityAuditController (legacy `/security/audit`) — marked `@Deprecated` in v2.10.2, safely removed in this release
- **Removed 5 dead methods from TokenUsageController**: endpoints with zero callers and their corresponding tests deleted
- **DTO deprecation cleanup**: `ServiceInstanceDTO.getInstanceId` deprecated accessor removed; `ApiKey.keyValue` retained with documented contract reason (serialization compatibility)
- **Frontend dead-function cleanup**: zero-reference functions in `tokenUsage.ts` and `debugPlayground.ts` removed

#### Permission URL-rule progressive backfill

- **PermissionRuleRegistry cleanup**: removed deprecated services/instances/instance-configs bindings; retained ratelimit/circuitbreaker sub-path rules
- **15 new URL permission rules registered** (47 total): filled URL→permission-code mappings for previously uncovered management endpoints
- **PermissionClosureTest with 35 assertions**: new permission closure test validates all 47 URL rules with 401/403/200 smoke checks

#### Quality

- mvn full suite **3202 tests all green** (0 failures / 0 errors); frontend vue-tsc + vite build passed

---

### [3.0.1] - 2026-09-06 - Feature Release (Web Complete-Flow Series Kick-off: Flow Wiring - Onboarding Loop + Governance Entries + Unified Skeleton)

> Version moves from 2.x to 3.0.x: no breaking changes (public `/api/v1` stays compatible); the major bump follows the "Web complete-flow milestone" semantics. This release theme: Web flow wiring.

#### Onboarding loop

- New `useRoutePreselect` (`playgroundTargetRoute` / `useRoutePreselect` / `preselectInstanceName`); Onboarding step 4 routes to the matching playground container by serviceType
- All 5 playground containers (chat/embedding/rerank/audio/image) consume `?serviceType=` to preselect a healthy instance; audio/image also activate the tts/stt·imgGen/imgEdit inner tab

#### Governance entries (cross-page closure)

- Dashboard governance area gains 4 entry cards: response cache / slow-query analysis / token usage / tracing (named-route jumps)
- CB/LB monitoring pages get reverse "go to config" buttons: global config / history / strategy config
- Service page `#actions` gains "Version History" → version management page (version page inbound link, no longer an island)

#### Legacy routes cleanup & unified skeleton

- tracing/Dashboard navigation switched to named routes; removed 8 legacy redirects (`/admin/tracing`, playground/main, tracing/overview|performance, etc.); zero old-path references remain
- PageSkeleton unified layout applied to 18 routed leaf pages (callHistory×3, config×2, cb×3, lb×2, rl, tracing×3, security×2, exception×3) with paired pageTitle keys (zh/en)

#### Quality

- vue-tsc + vite build passed; locale packs fully paired zh/en; Playwright journey smoke passed (entry cards / version-history / CB·LB reverse jumps / playground preselect route); backend untouched (3243 baseline); embedded static refreshed and verified after restart (36/37 pages reachable, no page errors once warm)

---

### [2.10.4] - 2026-09-06 - Feature Release (Web Experience Wrap-up: Unified Service-Type Names + Chart Hot-Switch + Dead-Code Cleanup + Dark Contrast Gate + Backend-Message Note + Dual-Theme Code Highlighting)

#### Unified service-type display names

- All service-type display names consolidated onto the top-level `serviceTypes.*` namespace (per-page nested dictionaries removed from the callHistory / exception / dashboard packs — one source of truth)
- ServiceManagement type column now shows localized names (was raw codes) and the add-service dropdown gets i18n labels (was rendering key literals); exception page residual half-English labels (Chat/TTS etc.) fixed; zh wording unified (聊天 → 聊天模型, 聊天服务 → 聊天模型, …), en aligned (dropped "Service" suffixes etc.)

#### Charts & theme UX

- New `useChartAutoRefresh` (watches language and dark theme, flush: post): charts on 8 pages (Dashboard / callHistory×2 / tracing×3 / exception stats / audit logs) re-render instantly on language or theme switch (pure frontend redraw, no network)

#### Cleanup & quality

- Dead-code cleanup: removed zero-reference `utils/errorHandler.ts` and `utils/validators/{index,rules,useValidation}.ts`
- Dark-contrast audit gate: new `frontend/scripts/contrast-audit.mjs` (real-browser, logs in and audits 36 pages in dark mode, WCAG ≥ 4.5, exit-code gate); fixed the root cause and cases of dark illegibility (global bare `h2 #333 / p #666` from the index.html boot screen leaking into the SPA, tracing/dashboard title, state-persistence info button, response-cache alert text) — content-text violations across the whole console cleared
- Dual-theme code blocks: MarkdownRenderer / CodeBlock hljs token colors grouped into semantic CSS variables; `html.dark` uses a GitHub Dark-like palette; contrast ≥ 4.5 in both themes (comments ≥ 3)

#### Docs

- FAQ zh/en Q12.1: notes the console is bilingual, backend `message` is passed through verbatim, and backend error-code localization is a separate future effort

#### Quality

- Frontend vue-tsc + vite build passed; chart language/theme switch browser smoke passed; backend untouched (3243 baseline unchanged); embedded static refreshed

---

### [2.10.3] - 2026-09-06 - Feature Release (Web Bilingual Edition: Full zh/en i18n + Language Switcher + Element Plus Locale + Date/Number Localization)

#### i18n infrastructure

- **Localization packs per namespace**: `locales/<lang>/*.json` split by top-level namespace, merged via glob (new files need no registration); zh/en each 39 files / 2321 leaf keys, key & `{placeholder}` parity, no top-level collisions; zh copy preserved verbatim
- **Language switcher**: `LanguageSwitcher` pill button (rounded / bordered / theme-token hover + caret rotation) in the Layout header and Login page; selection persisted in localStorage (`ja-locale`) and mirrored to `<html lang>`; browser language picks the first-run default
- **Element Plus locale**: `el-config-provider` drives EP built-in copy (pagination/table/date etc.)
- **Title system**: `menu.ts` and route `meta.titleKey` fully converted to i18n keys; `afterEach` syncs the tab title from the route; Login validation messages update instantly on language switch

#### Full migration

- **~40 views + shared components**: Dashboard, Service/Instance/Adapter family, Rule family, Pool/Version/StatePersistence, ResponseCache/SlowQuery, API Keys, JWT×2, Blacklist, AuditLog, PermissionManagement (incl. `permission.ts`), tracing×4, exception×3, callHistory×4, cb/lb/rl×6, playground (chat/embedding/rerank/audio/image/common×4), shared dialogs CircuitBreakerConfig/RateLimitConfig
- **Shared dictionaries**: `serviceTypes` display names, playground errors, user-store fallbacks, playgroundCache, api-layer throws all converted to i18n; dead code (`utils/errorHandler`, `validators/*`) verified unreferenced and kept
- **Date/number localization**: new `utils/format.ts` (locale-aware); key table date/number columns in 11 pages delegated to it (zh output byte-identical, en month names / thousands separators)

#### Experience fixes

- **Dark-mode contrast cleanup**: callHistory stat cards' hard-coded `#303133/#909399`, JwtAccount role descriptions and ChatConfigPanel label etc. → theme tokens; bare card-header `h2` (rendered `#333` and invisible in dark) and slider marks text (EP `--el-color-info` too dark) fixed globally; DOM contrast audit (ratio ≥ 5) cleared across tracing/management, call-history/token-usage and call-history/slow-calls

#### Quality

- Frontend vue-tsc + vite build passed; backend untouched (3243 baseline unchanged); login / dark / zh↔en switch smoke passed; embedded static refreshed and live after restart

---

### [2.10.2] - 2026-09-06 - Feature Release (Dashboard v2 Governance Hub + Config Onboarding & Capability Pages)

> This release merges the v2.10.1 (governance hub) and v2.10.2 (config onboarding & capability pages) development batches.

#### Governance hub

- **Dashboard v2**: new `api/limitMetrics.ts` (rate-limiter / circuit-breaker / routing-monitor status & metrics with per-endpoint degradation); Dashboard gains a governance-chain panel (rule hits / rate limiting / circuit breaker / LB + jump links) + exception summary (latest 5) + service config table jumps (`serviceType` query)
- **Cross-link navigation**: Service (type → Instance / Adapter), Instance (query-driven tab switch + open service config), Rule (clickable action targets), Pool (type / member → Instance), CallHistory (service / instance column jumps), Exception (uppercase-enum normalization then jump)
- **Skeleton migration & tokenization wrap-up**: 7 pages (AdapterManagement / PoolManagement / CallHistoryList / JwtToken / AuditLog / Blacklist / Permission) moved onto PageSkeleton; ~102 `hex` → token across 16 files; `tokens.css` gains the `--ja-space-1..8` spacing scale (piloted in 4 files)

#### Config onboarding & capability pages

- **Response cache management page** `/config/cache`: status cards + full / per-service / per-model targeted invalidation + hit / miss / hit-rate StatCards
- **Response cache tier B (runtime toggle + hit observability)**: backend cumulative hits/misses counters and snapshot (`hitRatio` null = no data yet); `PUT /api/config/cache/response/config` partial update (enabled / skipStreaming / onlyDeterministic / ttlSeconds, ttl 1~604800, invalid → 400 INVALID_REQUEST); runtime config panel sends only changed fields and notes values reset to yaml on restart; `maxSize` stays read-only (Caffeine construction time); cache page guards missing `hitRatio` (undefined → "no data")
- **Slow query analysis page** `/monitoring/slow-queries`: stats / hotspots / alerts
- **Onboarding**: `OnboardingSteps.vue` four-step guide bar (Adapter → Service → Instance → Playground) wired into 3 config pages
- **Governance enhancements**: circuit-breaker monitoring gains clear / per-instance reset buttons (fixes a states-Map parsing bug); API Key page gains a quota-alert section
- **Backend support**: `ResponseCacheController` gains a GET status snapshot (enabled / ttl / maxSize / size / skipStreaming / onlyDeterministic) + `ResponseCacheService.size()`; `@Deprecated` added to 4 whole controllers (legacy `/api/services` `/api/instances` `/api/instance-configs` `/security/audit`) and 5 dead methods in `TokenUsageController`
- **Permissions 44→45**: 45th code `config:cache:read` added (URL rules prefer child paths over parent paths); frontend static catalogs / permission tree / role templates synced (role counts 45 / 36 / 25 / 24)

#### Quality

- Backend **3243 tests all green** (+18 since v2.10.0); frontend vue-tsc + vite build passed; embedded static resources refreshed to the latest build; menu items drop standalone icons to align with siblings

---

### [2.10.0] - 2026-09-05 - Feature Release (Web Frontend Foundation: Cleanup + Shared Components + Tokenization + Dark-Mode Fixes)

#### Frontend foundation

- **Dead-code cleanup**: removed 13 unreferenced .vue files (legacy tracing/playground group, 11,726 lines) + `components/common/` 13 components + 16 dead exports in `tracing.ts` (−13.5k lines in total)
- **Shared components**: new `PageSkeleton.vue` (slot-based page skeleton), `StatCard.vue` (token-driven stat card), `useChartTheme.ts` (light/dark chart themes); piloted on Dashboard / ServiceManagement / RuleManagement
- **Tokenization**: ~150 hard-coded `hex`/`rgba` across ~20 pages → `--ja-*` / `--el-*` semantic tokens (stat cards / tables / chart gradients / dialogs); stat cards unified to StatCard (22 cards across Dashboard/ApiKey/CallHistory/Exception/rate-limiter monitoring)
- **Dark-mode fixes**: bundled Element Plus `theme-chalk/dark/css-vars.css`; `--el-color-*-light-N` and `--ja-primary-light-N` no longer map to light values in dark (dark-tinted, e.g. light-9 #18222b) — removes near-white blocks and harsh contrast in dark mode
- **Details**: Layout tweaks (removed logo pulse / sidebar hover shadow; breadcrumb home links straight to `/dashboard/main`); ~50 console debug leftovers removed; circuit-breaker CLOSED/OPEN/HALF_OPEN status cards adapted for dark
- **Embedded UI refresh**: latest frontend build mirrored into backend static resources `src/main/resources/static`

#### Quality

- Backend **3225 tests all green** (regression); frontend vue-tsc + vite build passed; programmatic white-surface probe passed on light/dark pages

---

### [2.9.11] - 2026-09-05 - Feature Release (README Refresh + Hallmark Design Audit + Frontend Fixes)

#### README & Docs

- **README / README-ZH full refresh**: refreshed 7 UI screenshots (incl. dark theme and call-history dashboard; previously the 2026-08-26 UI); Core Features now cover response cache P1, RBAC 44 codes and dark/light theme; Roadmap permission codes 43→44; sample auth header unified to `Jairouter_Token`
- **rbac-permissions zh/en synced to 44 codes**: catalog gains `config:cache:write` (config module = 11 resources / 21 codes); role counts ADMIN 44 / OPERATOR 35; API & permission-tree descriptions updated

#### Hallmark design audit (local innerdoc, input for v2.10.x)

- Produced `innerdoc/02-架构与设计/web-management-hallmark-audit.md` (474 lines): 395 hard-coded colors, ~5,300 lines of dead code, missing governance chain on the Dashboard, 13 unused `components/common` components, 4 duplicated stat-card implementations, etc.; includes rollout notes for v2.10.0 (foundation) / v2.10.1 (config & onboarding) / v2.10.2 (governance & security)

#### Frontend fixes

- Static permission catalog synced to 44 (`permission.ts` / `menu.ts`; the permission tree now shows `config:cache:write`)
- Login i18n: added missing keys (username/passwordPlaceholder, submitting) — placeholders no longer render raw keys
- `stores/user.ts`: jwt validate changed GET→POST (matches the backend contract, fixing silent 405)
- `BlacklistManagement`: removed the non-existent `suspicious-ips` endpoint call and its mock fallback (IP list now sourced from active tokens)

#### Quality

- Backend **3225 tests all green**; frontend `vue-tsc` + `vite` build passed

---

### [2.9.10] - 2026-09-05 - Feature Release (Routing Intelligence-5: Response Cache P1)

#### Response cache (streaming SSE concatenation cache + invalidation API + service-level rate-limit early short-circuit)

- **Streaming SSE concatenation cache**: beyond non-streaming, streaming chat responses can be cached too — post-transform chunks + usage + finishReason are stored in `CachedStreamingResponse`, written when the stream completes normally; hits are replayed as bare SSE (TEXT_EVENT_STREAM + [DONE]); opt-in via `jairouter.response-cache.skip-streaming: false`
- **Invalidation API**: `CacheStore` extended with delete / deleteByPrefix / clear; new `DELETE /api/config/cache/response` (invalidate by serviceType / model / all); cache keys restructured to three parts `rc:{service}:{model}:{sha256}` so prefix invalidation works
- **Service-level rate-limit early short-circuit**: `RateLimitManager.tryAcquireService` + `ServiceRateLimitHolder` (ThreadLocal once-only, prevents double accounting) — cache hits short-circuit before instance selection; 429 hard-boundary semantics unchanged
- **Permission extension**: 44th code `config:cache:write` (PermissionCodes + PermissionRuleRegistry URL rules + role templates)

#### Tests

- Added 49 (streaming write / read replay / bucketing / invalidation / three-part key / rate-limit short-circuit integration); **3225 tests all green**

---

### [2.9.9] - 2026-09-03 - Feature Release (Routing Intelligence-4: Response Cache P0)

#### Response cache (non-streaming exact match)

- **Full response cache**: identical requests (same tenant + service/model + canonical request body) reuse the cached response and **skip the downstream entirely** — lower latency and cost; stacks with the v2.9.0 prefix cache (KV-cache affinity, still hits the downstream for prefill savings)
- **P0 scope**: non-streaming chat + embedding + rerank (deterministic: chat requires temperature==0/null and n==1/null; embedding/rerank are naturally deterministic); image/TTS/STT binary excluded; streaming caching deferred to a later version
- **Components**: `ResponseCacheProperties` (`jairouter.response-cache`, off by default, opt-in) / `CacheStore` interface + `CaffeineCacheStore` (±10% TTL jitter against thundering herd) / `ResponseCacheService` facade (enabled short-circuit + deterministic gate) / `ResponseCacheKeyBuilder` (chat/embedding/rerank canonicalization)
- **Cache key**: `SHA-256(apiKeyId|user?|serviceType|model|canonical body)` — apiKeyId in the key prevents cross-tenant leaks; `user` optionally in the key (empty → apiKey granularity, zero API breakage); `cacheSalt` explicit bypass; no plaintext content in keys
- **Conversation semantics**: no sessionId — history travels fully in `messages`, so the key naturally distinguishes contexts; single-turn greetings hit frequently by design, staleness bounded by TTL
- **Wiring**: handler read (short-circuit after instance selection, service-level rate limiting not bypassed); NonStreamingRequestProcessor write (2xx deterministic responses only)
- **Metrics**: `jairouter_response_cache_hits_total / misses_total / hit_ratio` (tags service/model)
- **Hit semantics**: cache hits short-circuit before downstream execution (no call-history record, no token quota consumed — only the hit counter increments)
- **Docs**: new `configuration/response-cache.md` (zh/en)

#### Tests

- Added 45: KeyBuilder 12 / CaffeineCacheStore 6 / ResponseCacheService 8 / processor write 4 / metrics 3 / handler integration 13 (hit short-circuit / miss / disabled / streaming & temperature>0 bypass / tenant isolation / embedding·rerank / write-read loop); **3176 tests all green**; end-to-end smoke against a real DeepSeek downstream passed (hit / tenant isolation / metrics)

---

### [2.9.8] - 2026-09-02 - Feature Release (Web Menu RBAC Management + User/Permission Extension)

#### Data-driven permission system

- **43 permission codes**: `module:resource:action` scheme (overview/config×10/lb/cb/rl/callhistory/monitoring×5/tracing×3/security×4/system×2/ai/actuator); `PermissionCodes` constants + full list
- **4 role templates**: ADMIN (all 43) / OPERATOR (34: read+write minus system/security:manage/actuator) / USER (24: dashboard+config:read+lb/cb/rl+monitoring:read+tracing dashboard/search+playground) / VIEWER (23: read-only); `RolePermissionSeeder` seeds on startup (idempotent when table empty), stored in role_permissions
- **JWT permissions claim**: permission codes embedded in login JWT (no ROLE_ prefix); roles stay in the roles claim; preserved on refresh

#### Data-driven authorization

- **PermissionRuleRegistry** (36 URL→code rules) + **PermissionAuthorizationManager** (ReactiveAuthorizationManager: hit→check code; ADMIN bypass; no rule→fallback authenticated); wired into SecurityConfiguration `/api/**`
- Sync-returning controller authorization handled purely via URL rules (RBAC 500 rule: no method-level @PreAuthorize); ModelCallStats `hasAdminPermission` stub covered by URL rule
- **Contract fix**: `/api/security/jwt/accounts/**` now maps to `system:accounts:manage`

#### Management API & UI

- `GET /api/security/permissions` / `GET /api/security/permissions/roles` / `PUT /api/security/permissions/roles/{roleName}` (invalidateCache) / `GET /api/auth/permissions`
- **Permission management UI**: System → Permission Management (role dropdown + 43-code tree + save; prompts re-login for changes)
- **Menu re-grouping**: 11 groups → 8 groups / 34 items; data-driven menu.ts + usePermission filtering + route guard meta.permissions

#### Login chain fix

- **DB account fallback**: `AccountManager` authentication now supports Web-created DB accounts (YAML static accounts first, jwt_accounts table fallback; enabled=null treated as enabled, consistent with JwtAccountService)

#### Frontend fix

- Sidebar menu "stepped" background after scroll fixed (independent scroll region over fixed full-height gradient column)

#### Tests

- Added 50+: RolePermissionService/Seeder/PermissionAuthorizationManager (incl. URL matrix)/PermissionManagementController/JWT permissions claim/AccountManager (DB fallback, 13) etc.; **3131 tests all green**; permission smoke passed (ADMIN 200 / no-token 401 / USER·VIEWER 403 matrix)

#### Docs

- New `security/rbac-permissions.md` (zh/en): permission codes/role templates/workflow/management API/permission UI/menu & routes/upgrade notes

---

### [2.9.7] - 2026-09-01 - Feature Release (Routing Intelligence-3: Tag Routing)

#### Tag routing

- **Instance tags**: `ModelInstance` gains `Map<String,String> tags` (e.g. `gpu_type: a100` / `region: cn-north` / `tier: premium`), supported across the full YAML/API/persistence chain (ConfigConverterHelper / ModelInstanceConfiguration / ServiceInstanceEntity JSON column / ServiceInstanceManager)
- **TARGET_TAGS rule action**: new "tag routing" action that selects instances by tags (AND semantics — instance tags must contain every required key-value pair); `RuleDefinition.Action` gains a `tags` field
- **Request-level header selection**: `X-JAiRouter-Tags: key=value,key2=value2` (comma-separated, tolerant of whitespace/empty items) filters candidate instances by tags; rule TARGET_TAGS takes precedence over the header
- **Priority**: TARGET_INSTANCE lock > TARGET_TAGS > resource pool; empty candidates after tag filtering → 404; pools and auto-model are naturally unaffected by tags (pool members are explicitly listed)
- **Frontend**: instance management page gains a "tag configuration" key-value editor (mirrors the headers UI); rule form gains the TARGET_TAGS action with a tag editor; rule list shows action summaries

#### Fixes

- **Legacy DB upgrade compatibility**: `CompatibilitySchemaMigrator` registers the `service_instance.tags` column (CLOB, dialect-adapted) — fixes instance query 500 on legacy H2 DBs missing the column
- **Instance limiter identifier**: `RateLimitManager.generateInstanceKey` falls back `instanceId → name → baseUrl`, fixing null identifiers for instances without instance-id; metrics parsing splits on the first `:` to tolerate colons in names
- **Rate limiter monitor metrics**: `RateLimiterTracingWrapper` now forwards `getRemainingCapacity()`/`getUsageRatio()` — fixes usage ratio stuck at -100% (the tracing wrapper previously fell back to the interface default -1)
- **Base config**: added `instance-id` to all 9 instances in `model-services-base.yml` (removes the null-identifier root cause)
- **LB strategy dropdown**: fixed select width and option layout in StrategyConfig.vue (latency strategy label no longer touches its description); fallback list now includes latency
- **Rule template copy**: `RuleTemplateService.copyAction` handles TARGET_TAGS (prevents silent tag loss when copying future templates)

#### Tests

- Added: SelectInstanceOptimizerTest (9), ModelServiceRegistryTagRoutingIntegrationTest (10), ModelInstanceConfigurationTest (6), CompatibilitySchemaMigrator migration assertions (3), RateLimitManagerInstanceKeyTest (3), RateLimiterTracingWrapperTest (2); **3069 tests all green**

---

### [2.9.6] - 2026-08-30 - Feature Release (Routing Intelligence-2: Request-Level Failover)

#### Request-level failover

- **Instance switching on retry**: `BaseAdapter.processRequestWithRetry` reworked — on retryable error (connection / 5xx / timeout) the current instance joins the per-request failed blacklist (`baseUrl:path` key); a new instance is selected through the normal chain (health/circuit-breaker filter + LB strategy), replacing the old same-instance hammering
- **Blacklist isolation**: failed instances are not reselected within a request (`tryReselectInstance`, cap `failedKeys.size()+2` prevents infinite loops)
- **Safe fallback**: single-instance service / all-instances-failed / reselect exhausted → fall back to same-instance retry (existing behavior); 4xx non-retryable errors never switch; retry count stays bounded by `RetryPolicy`
- **Enabled by default**: no config needed (reselect passes null clientIp, LB degrades to non-IP-aware; sticky routing naturally bypassed during failover)
- Per-instance WebClient fetched by baseUrl (`getWebClientForInstance`)

#### Tests

- New `BaseAdapterFailoverTest` (5 cases: A-fails→B-succeeds / all-failed fallback / single-instance no-reselect / 4xx no-retry / reselect cap); **full suite 3034 tests green** (+5)

---

### [2.9.5] - 2026-08-30 - Feature Release (Knowledge-Base Governance)

#### Governance mechanism (docs 155 + innerdoc 165)

- **Toolset** `scripts/knowledge-governance/`: kg-scanner (inventory + SHA-256 + one-off classification), kg-duplicate-detector (SHA-256 exact + filename near), kg-staleness-detector (index drift / status conflict / version literal drift), kg-archive-mover (--plan/--execute archive, hard-guard knowledge-base/ & 16-版本发布/), kg-version-tracker (innerdoc/docs-versions.json + --regen-index)
- **Workflow** `.mimocode/skills/knowledge-governance/SKILL.md`: 5 steps (scan → LLM proposal → human approval → execute → index update), run every version release
- **CI enhancement**: `validate-nav-files.py` (mkdocs nav integrity) + docs-version-management.yml (nav check step + duplicate summary in issue)
- **First-run execution**: archived 61 files (H2×23/security×16/dev-guide×11/tracing×7 etc → `innerdoc/archive/`); 3 unique contents merged into knowledge-base (JPA migration / API-Key init fix / tracing verification); indexes rebuilt (README-INNERDOC/INDEX.json/00-索引, 105 files/18 categories); 1 status conflict fixed
- **SOP docs**: `docs/{zh,en}/development/knowledge-base-governance.md` (10 sections); created missing `docs/en/development/doc-maintenance.md` (fixed mkdocs strict-build risk)
- **docs fixes**: 8 doc version headers 2.6.11 → 2.9.5; roadmap current-stable v2.7.11 → v2.9.5

#### Tests

- Governance tools verified (165 files / 3 exact duplicate groups / 22 near clusters / index drift detected); full suite **3029 tests green** (no Java changes)

---

### [2.9.4] - 2026-08-30 - Feature Release (UI Design-System Refactor + RBAC Fix)

#### UI Design System

- **Design tokens**: new `src/styles/tokens.css` (40+ semantic `--ja-*` CSS vars: primary/sidebar/main-bg/text/border/radius/shadow/font/login-gradient/dashboard gradients, with `html.dark` overrides)
- **Element Plus overrides**: `src/styles/element-override.css` maps `--el-color-primary` family, `--el-bg-color`, `--el-fill-color-*`, `--el-text-color-*`, `--el-border-color-*`, shadows to tokens (light + dark)
- **Dark mode**: `src/composables/useTheme.ts` (isDark/toggleTheme/initTheme, localStorage persistence, follows system by default, synchronous init before mount to avoid flash); sun/moon toggle in the Layout header
- **Hardcoded color cleanup**: Layout.vue sidebar/active/main, Login.vue gradient, Dashboard.vue stat-card gradients and icon colors, index.html gradient → token vars (same look preserved)

#### RBAC Fix (500 regression)

- **Root cause**: under `@EnableReactiveMethodSecurity`, method-level `@PreAuthorize` on controllers with SYNCHRONOUS return types requires a Publisher return (Reactor Context); real requests threw 500 (auth OK, authorization interceptor crashed — hence 500, not 401)
- **Fix**: removed `@PreAuthorize` from `ApiCallHistoryController` / `CallHistoryConfigController` / `TracingSecurityController` (3 sync-return controllers); RBAC moved to `SecurityConfiguration` URL rules (`/api/call-history/**`, `/api/config/call-history/**`, `/api/config/tracing/security/**` → ADMIN)
- Verified against real requests: ADMIN 200 / no token 401; local dev DB manually patched with v2.9.2 columns (H2 `DATABASE_TO_UPPER=FALSE` breaks Hibernate ddl-auto update metadata matching)

#### Tests

- Updated `ApiCallHistoryControllerRbacTest` / `RecordLevelChangeAuditTest` (RBAC assertions now match URL-rule semantics); **full suite 3023 tests green**

---

### [2.9.3] - 2026-08-30 - Feature Release (Routing Intelligence-1: EWMA Latency-Aware Routing)

#### Latency-aware load balancing (`load-balance.type: latency`)

- **LatencyAwareLoadBalancer**: EWMA-based latency-aware strategy, weighted random by `1/(1+ewma)` (lower latency = higher probability); cold-start equal weight for fair exploration; failed calls update with a 30s penalty
- **Duration chain**: `LoadBalancer` gains durationMs hook overloads (default, zero change for the 6 existing strategies); `AdapterMetricsRecorder → ModelServiceRegistry → LoadBalancer` threads call duration; `StickyLoadBalancer` 3-arg passthrough (duration preserved under sticky wrapping)
- **Config**: `ewma-alpha` (default 0.2, global/service-level); `latency` registered in ComponentFactory; validators + DTO + merger/converter full-chain support; `/api/loadbalancer/strategies` includes latency
- Default strategy remains `random`; `type: latency` is opt-in — zero default behavior change

#### Tests

- New `LatencyAwareLoadBalancerTest` (EWMA convergence / selection distribution / cold start / failure penalty / concurrency), `ModelServiceRegistryDurationFlowTest` (duration flow); extended `StickyLoadBalancerTest`, `LoadBalancerManagementControllerTest`, `ConfigValidatorHelperTest`; **full suite 3024 tests green** (+16)

---

### [2.9.2] - 2026-08-30 - Feature Release (Record Governance)

#### Three Recording Levels

- **METADATA_ONLY (default)**: metadata only, no request/reply content stored
- **SUMMARY**: content desensitized via `SanitizationService` then truncated to summary columns
- **FULL**: complete request/reply content encrypted with AES-256-GCM (new `RecordContentCipher`; key from `JAIR_CALL_HISTORY_KEY` env var or auto-generated and persisted to `~/.jairouter/call-history.key`)
- New config: `jairouter.call-history.record-level` / `max-content-length` (default 64 KB) / `encryption-key-source`

#### Content Capture

- **Non-streaming**: request body (post-transform serialization) + raw downstream response body (pre-transform) captured, truncated at `maxContentLength`; new `AdapterMetricsRecorder.recordCompleteCall` overload carrying bodies
- **Streaming**: request serialized at entry + assembled text in `doOnComplete`; streaming call-history recording added (previously streaming did not write history)
- **Dedup fix**: `BaseAdapter` success path now updates stats only, avoiding double recording with the RequestProcessor (2 rows → 1 row per call)

#### Security

- **Full RBAC on call history**: all `ApiCallHistoryController` endpoints + new config endpoints `GET/PUT /api/config/call-history` are ADMIN-only
- **Latent gap fixed**: added `@EnableReactiveMethodSecurity` (previously `@PreAuthorize` was not enforced)
- **New endpoint** `GET /api/call-history/{id}/detail`: ADMIN can decrypt and view FULL records
- **Audit events**: `RECORD_LEVEL_CHANGE` (level change), `FULL_CONTENT_ACCESS` (decrypt access to FULL records)

#### Frontend

- Call-history page gains a record-level setting (radio: metadata-only / summary (desensitized) / full (encrypted))

#### Tests

- Full suite **3008 tests green** (0 failures / 0 errors, +55 new); new `RecordContentCipherTest`, `ApiCallHistoryServiceRecordLevelTest`, `ApiCallHistoryControllerRbacTest`, `RecordLevelChangeAuditTest` and more

---

### [2.9.1] - 2026-08-30 - Quality Closing

#### Pragmatic Large-File Splits

- **15 → 5 files > 500 lines** (5 highly-cohesive files pragmatically exempted): `TracingConfiguration` 608→307 (4 nested config classes extracted), `TracingEncryptionService` 543→477, `ApiKeyBatchService` 608→459, `CircuitBreakerTracingWrapper` 570→243, `DefaultMetricsCollector` 629→379, `ConfigurationService` 653→410 (instance management extracted to `InstanceConfigService`), `JwtBlacklistServiceImpl` 558→438, `ControllerTracingInterceptor` 595→237, `ExtendedSecurityAuditServiceImpl` 629→490, `NonStreamingRequestProcessor` 574→411
- 16 new top-level classes (plain POJO config classes / DTOs + plain helpers + `@Component`/`@Service` delegates); zero behavior change, full suite regression-free

#### Frontend

- **Resource-pool member instances now use a dropdown selector**: loads available instances by service type, dedupes already-selected items, no manual input, fallback display for deleted instances

#### Tests

- Full suite **2953 tests green** (0 failures / 0 errors); jacoco INSTRUCTION coverage **31%** (≥ 30% target met, 1175 classes)

---

### [2.9.0] - 2026-08-28 - Feature Release

#### LLM KV Cache Enhancement: Prefix-Cache-Friendly Gateway

- **Automatic tenant-affinity sticky routing**: new `StickyLoadBalancer` (consistent hashing over `apiKeyId|serviceType|modelName` → instance) + `AffinityKeyResolver` (prefers `apiKeyId`, falls back to `clientIp`) + `AffinityContextHolder` (ThreadLocal affinity-key propagation); `sticky.enabled` config-aware (auto-wraps when instances > 1); `affinityKeyScope` supports `tenant` / `tenant_model` (default includes modelName) granularity, resolved dynamically
- **Cache hit metrics**: parses DeepSeek-style `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` and OpenAI/vLLM-style `prompt_tokens_details.cached_tokens`; new `jairouter_cache_hit_tokens_total` / `cache_miss_tokens_total` counters + `jairouter_cache_hit_ratio` gauge (per adapter+instance, cumulative hitTotal/(hitTotal+missTotal))
- **Cache parameter passthrough**: `ChatDTO.Options` gains `prefixCacheHash` / `enablePrefixCaching`; `OpenAiRequestTransformer` extra_body whitelist allows vLLM prefix-cache params
- **Prefix hygiene guarantee**: regression tests assert byte-for-byte message-array passthrough (4- and 6-message sets); 5 tolerant cache-token parsing tests
- **Fixes**: StickyLoadBalancer concurrency safety (volatile snapshot + fingerprint cache instead of per-request hash-ring rebuild, removing TreeMap concurrent read/write risk and O(instances×150) recompute); cache_hit_ratio cumulative semantics; affinityKeyScope actually takes effect; `ApiKeyBatchServiceTest` expiration-stats midnight flake

#### Tests

- New: `StickyLoadBalancerTest`, `AffinityKeyResolverTest`, `CacheMetricsTest`, `CacheTokenParsingTest`, `OpenAiRequestTransformerTest` (regression), `StreamingRequestProcessorTest`; **full suite 2953 tests green**

---

### [2.8.9] - 2026-08-26 - Feature Release

#### Intelligent Routing: Resource Pools + auto-model

- **Resource pools**: a named set of instances of one service type, with a configurable strategy (`weighted-random` / `round-robin` / `least-connections` / `ip-hash` / `consistent-hash`) and per-member weights; configured via YAML (`model.pools`) + StoreManager persistence + CRUD API (`/api/config/pools`) + a web management page (Configuration → Resource Pools)
- **auto-model virtual model name**: requesting `model=auto-model` (or any pool name) automatically picks a healthy pool member to serve (health/circuit-breaker filter + pool weights + pool strategy); with no pool configured, `auto-model` falls back to all healthy instances of the service type; missing members (deleted instances) are skipped
- **Rule synergy**: a rule's `TARGET_MODEL` action can point to a pool name to pin the pool
- **Response echo**: after pool routing, the downstream request and (non-streaming) response `model` field is rewritten to the actual serving instance model (streaming outbound request rewritten too; streaming response echo deferred)
- **Fix**: `ApiKeyBatchServiceTest` expiration-stats time-boundary flake (`now+2h` crossing midnight misclassified)

#### Tests

- New: `PoolSelectorTest` (POOL-001~007), `PoolPersistenceServiceTest` (POOL-PERSIST-001~008), `PoolConfigControllerTest` (POOL-API-001~013), `ModelServiceRegistryRuleIntegrationTest` pool routing (INTEG-018~021); **full suite 2909 tests green**

---

### [2.8.8] - 2026-08-26 - Feature Release

#### Rate-Limiting Capability Completion

- **Service-level rate limiting on the hot path**: the request chain now runs a service-level rate-limit check (exactly once per request, zero overhead without config); exceeding it returns `429 Too Many Requests`; instance-level keeps its "try the next instance" semantics
- **Dynamic per-service rate-limit config made real**: `PUT /api/services/{serviceType}/ratelimit` changed from an empty stub to persist (config store `model-router-config`) + hot-apply (`RateLimitManager`); GET returns the canonical format (`enabled/algorithm/capacity/rate/scope/key`); when enabled, `capacity`/`rate` are required and must be > 0; config **re-applies automatically on restart**; the service management edit dialog is wired to this endpoint
- **RATE_LIMIT rule action**: the rule engine gains a rule-level limiting action (independent limiter per rule ID, fired at the same decision point as hit counting — never double-executed); requests over the limit get 429; deleting a rule or changing its action cleans up the limiter; a new "Rate Limit Protection" preset template is added (6 → 7 templates, pre-filling the limit params when creating from template); dry-run output includes the limit params
- **Fixes**: stale assertion in the API call-history statistics test (service switched to separate range queries but the test was not updated); dev-mode `VITE_API_BASE_URL` corrected to `/api` so the dev proxy reaches the backend

#### Tests

- New/updated: ServiceRateLimitControllerTest (RL-001~008), ModelServiceRegistryRuleIntegrationTest (INTEG-014~017), RuleConfigControllerTest (RULEC-029~034), RateLimiterTest (rule-level, 3 cases), RuleTemplateServiceTest (rate-limit template), RuleTemplateControllerTest (7 templates); **full suite 2876 tests green** (including the fixed ApiCallHistoryServiceTest)

---

### [2.8.7] - 2026-08-25 - Feature Release

#### Rule Engine Trio

- **Rule hit statistics**: hits are counted at the decision-application point (no double counting; dry-run excluded); new Prometheus metric `jairouter_rule_hits_total` (tags: ruleId/actionType) + `GET /api/config/rules/stats` aggregation endpoint; the rule list page adds a "hits" column with refresh
- **Priority drag-and-drop**: rules can be reordered by drag (sortablejs), committing priorities in row order; `PUT /priority` now skips unknown ids (YAML rules no longer 404) and returns `{updated, skipped}`; rules expose a `source` field (YAML/PERSISTED), YAML rules show a badge and cannot be dragged
- **Scenario templates**: 6 built-in rule templates (canary release / tenant isolation / model rewrite / weight split / adapter switch / VIP instance pin); `GET /api/config/rules/templates` + `POST /{id}/create`; a "Create from template" wizard pre-fills the rule form as a draft before saving

#### Tests

- RuleConfigControllerTest extended (RULEC-026~028 for stats); new RuleStatsServiceTest, RuleTemplateServiceTest, RuleTemplateControllerTest; all rule-related tests pass; Checkstyle 0 violations

---

### [2.8.6] - 2026-08-25 - Enhancement Release

#### Rule Engine Usability

- **Action/condition value dropdowns**: action targets are selected from type-aware dropdowns (model name / instance / adapter, searchable with custom input; LB strategy is a fixed 5-value select); condition values support model-name / service-type / common-header dropdowns with autocomplete; options refresh when the service type changes
- **Rule simulation test (dry-run)**: `POST /api/config/rules/validate` verifies rule matching with a sample request (modelName/IP/headers), read-only; the Web form includes a built-in 「Simulate Test」 panel
- **Fix**: case-insensitive `serviceType` matching (`imgGen`/`imgEdit` camelCase enum values now resolve correctly)

#### Tests

- RuleConfigControllerTest extended (incl. RULEC-025); all rule-engine tests pass; Checkstyle 0 violations

---

### [2.8.5] - 2026-08-23 - Feature Release

#### Rule Engine (Visual Conditional Routing)

Implement visual routing rule configuration so users can configure condition-based smart routing from the Web console **without writing code**:

- **Conditional routing rules**: 5 match condition types (model name / service type / request header / client IP / weight) and 5 operators (equals / contains / starts-with / regex / CIDR match); matched requests execute a preset action
- **Action types**: TARGET_MODEL (model rewrite), TARGET_INSTANCE (pin instance), TARGET_ADAPTER (switch adapter), LB_STRATEGY (load-balancing strategy override)
- **Rule management API**: `/api/config/rules` full CRUD + enable/disable + priority batch update
- **Rule persistence**: StoreManager-backed persistence, auto-recovery on restart, YAML-first merge
- **Frontend visual configuration**: rule management page + rule form dialog (condition editing / action config)
- **Docs overhaul**: new routing-rule configuration doc, docs structure reorganization

#### Tests

- All rule-engine related test cases pass; Checkstyle 0 violations

---

### [2.8.4] - 2026-08-15 - Feature Release

#### Plugin System (Custom Adapter Framework)

Implement the "plugin system" so users can create, test, and manage custom adapters from the Web console **without writing code**:

- **Adapter Template System**: 13 built-in templates (DeepSeek/Zhipu GLM/Moonshot Kimi/Baichuan/Qwen/MiniMax/01.AI Yi/StepFun/SiliconFlow/Groq/OpenRouter/Together AI/Local Ollama)
- **Adapter Test API**: PING connectivity test + CHAT conversation test (independent 10s timeout, API Key never persisted)
- **Enhanced Frontend Wizard**: 4-step configuration wizard (template selection/basic config/advanced config/test connection) + template cards + test panel
- **Adapter Definition Persistence**: StoreManager-backed persistence, auto-recovery on restart, YAML-first merge strategy

#### New Components

- `AdapterTemplateService`: template service (13 built-in templates)
- `AdapterTestService`: adapter connectivity test service
- `AdapterDefinitionPersistenceService`: adapter definition persistence service
- `AdapterTemplateController` / `AdapterTestController`: template & test REST APIs

#### Tests

- 49 new test cases (TDD-first), all passing; Checkstyle 0 violations

---

### [2.8.3] - 2026-07-15 - Feature Release

#### Gemini Adapter + Adapter Config Enhancements

- **Gemini Adapter**: added Google Gemini adapter support
- **adapter-config phases 1-3**: OpenAI-compatible adapter config, Ollama-compatible adapter config, inheritance + override mode
- **Test coverage improvements**: ErrorResponseBuilder (15 cases), StreamingRequestProcessor (14 cases), NonStreamingRequestProcessor (11 cases), Health module (0% → 68-71%)

---

### [2.8.2] - 2026-07-17 - Code Quality

- Checkstyle cleanup: fixed 11 warnings

---

### [2.8.1] - 2026-07-16 - Code Quality

- Checkstyle cleanup: fixed 66 warnings

---

### [2.8.0] - 2026-07-15 - Feature Release

#### Anthropic Claude Adapter

Added Anthropic Claude adapter support, enabling JAiRouter to serve as a unified gateway for Claude API workloads alongside existing OpenAI-compatible backends.

- **ClaudeAdapter**: Claude adapter implementation (+295 lines)
- **ClaudeAdapterTest**: adapter tests (+404 lines, covering request transformation, response parsing, error handling)
- **Documentation cleanup & config examples**: added Claude adapter configuration examples

---

### [2.7.6] - 2026-06-30 - Feature Release

#### API Key Quota Management

Added comprehensive quota management features for API Keys:

- **Daily Token Limit**: Track and limit daily Token usage per API Key
- **Rate Limiting**: Sliding window algorithm for per-minute request limiting
- **Alert Thresholds**: Configurable usage percentage thresholds for alerts
- **Automatic Reset**: Quota counters reset daily at midnight

#### Bug Fixes

- **API Key Serialization**: Fixed `@JsonProperty(access = WRITE_ONLY)` preventing `keyHash` from being serialized to database
- **Token Usage Tracking**: Improved Token usage tracking for streaming and non-streaming requests

#### New Components

- `ApiKeyQuotaService`: Quota checking and alert service
- `TokenBucketRateLimiter`: Sliding window rate limiter
- `ApiKeyQuotaCleanupScheduler`: Scheduled cleanup of expired quota data

#### Database Changes

Added quota-related fields to `api_keys` table:
- `daily_token_limit`: Daily Token usage limit
- `rate_limit_per_minute`: Requests per minute limit
- `quota_alert_threshold`: Alert threshold percentage
- `today_token_usage`: Current day Token usage
- `today_request_count`: Current day request count
- `last_reset_time`: Last quota reset time

---

### [2.7.7] - 2026-07-10 - Feature Release

#### ExceptionEvent Collection Fixes

Fixed multiple issues in the exception event collection pipeline:

- **Bean conflict fix**: Resolved `ExceptionEventService` bean initialization conflict
- **Error degradation optimization**: Exception event collection failure no longer affects the main flow
- **Persistence chain fix**: Ensured exception events are correctly written to the database

#### API Key Service-Level Permission Checks

- **Service-level permissions**: API Keys now support fine-grained permission control by service type (Chat/Embedding/Rerank, etc.)
- **Frontend permission fixes**: Fixed permission management page display and interaction issues

---

### [2.7.8] - 2026-07-10 - Feature Release

#### Request Call History Persistence

Added a complete API call history recording feature:

- **Call record storage**: Records detailed information for each API call (model, instance, duration, status, etc.)
- **Query and statistics**: Supports querying and statistics by time range, model, service type, and other dimensions
- **Frontend dashboard**: New call history visualization page with trend charts and detail tables
- **Exception monitoring enhancement**: Exception management page adds business fields and frontend filtering

#### New Components

- `ApiCallHistoryService`: Call history recording service
- `ApiCallHistoryController`: Call history REST API
- `CallHistoryDashboard`: Frontend call history dashboard

---

### [2.5.15] - 2026-05-11 - Stable Release

#### Large Class Refactoring Complete

This release completed refactoring of 4 oversized classes, reducing 2011 lines of code (-62%).

| File | Original Lines | Final Lines | Reduction | Target |
|------|----------------|-------------|-----------|--------|
| BaseAdapter | 1386 | 416 | -70% | 600 ✅ |
| TracingService | 764 | 483 | -37% | 400 ✅ |
| DefaultStructuredLogger | 945 | 365 | -61% | 400 ✅ |
| ConfigVersionManager | 746 | 387 | -48% | 400 ✅ |

#### New Components (12)

- ConfigComparator, SpanAttributeHelper, ServiceNameResolver
- RequestLogBuilder, ResponseLogBuilder, BackendCallLogBuilder
- ErrorLogBuilder, SystemEventLogBuilder, VersionValidator
- VersionMetadataManager, VersionSyncService, ModelUtils

#### Quality Checks

- Checkstyle: ✅ Passed
- SpotBugs: ✅ Passed
- Tests: 971 passed ✅

---

### [1.7.3] - 2026-04-14

#### Bug Fixes
- **Playground Chat Streaming Response Fix**: Fixed duplicate AI response messages during streaming
  - Issue: AI responses appeared twice during streaming
  - Cause: Incorrect `displayMessages` filtering logic in `MessageList.vue`
  - Fix: Always filter the last assistant message during streaming, displayed by additional `MessageBubble` component

#### Improvements
- **Port Configuration Restoration**: Server port restored to default `8080`, consistent with documentation

#### Technical Improvements
- Updated frontend static resource build artifacts

---

### [1.7.2] - 2026-04-14

#### New Features
- **Playground Component Refactoring**: Major refactoring of Playground module with component-based architecture
  - Chat Module: `ChatContainer`, `ChatConfigPanel`, `MessageInput`, `MessageList`
  - Audio Module: `AudioContainer`, `TtsPanel`, `SttPanel`
  - Image Module: `ImageContainer`, `ImageGeneratePanel`, `ImageEditPanel`
  - Embedding Module: `EmbeddingContainer`
  - Rerank Module: `RerankContainer`
  - Common Components: `MessageBubble`, `MarkdownRenderer`, `CodeBlock`, `ModelSelector`, `ServiceLayout`, `LoadingIndicator`
- **New Composables**:
  - `useChatSession`: Chat session management (localStorage persistence)
  - `useMarkdown`: Markdown rendering handling
  - `useStreaming`: SSE streaming response processing

#### Improvements & Optimizations
- **Health Check SSE Controller Optimization**: Optimized `HealthStatusSseController` implementation
- **Instance Management Extension**: Added field support, extended `ServiceInstanceDTO` and entity classes
- **Adapter Base Class Adjustment**: Unified adapter base class handling logic
- **Frontend Routing and Layout Optimization**: Optimized routing configuration and Layout component

#### New Files
- `frontend/src/views/playground/components/` - 18 component files
- `frontend/src/views/playground/composables/` - 4 composable files
- `src/main/resources/db/migration/V3__add_adapter_headers_fields.sql`

---

### [1.7.1] - 2026-04-13

#### Bug Fixes
- **Tracing Fixes**:
  - Fixed `TracingWebFilter` duplicate `traceId` creation issue
  - Fixed `TraceQueryService` `spanCount` display error in `recentTraces` merge
  - Fixed `TracingService` `serviceName` classification, frontend route correctly identified as 'front'
- **Frontend Routing Fix**: Fixed frontend routing path error (`/admin/admin/tracing` -> `/admin/tracing`)

#### Improvements & Optimizations
- **ControllerTracingInterceptor Optimization**: Optimized child Span synchronous recording logic
- **Table Layout Optimization**: Table column width using `min-width` for adaptive filling

#### New Features
- **TraceDetail Component**: Added trace detail display component
- **Tracing Dashboard Page**: Added tracing dashboard page

#### New Files
- `frontend/src/views/tracing/Dashboard.vue`
- `frontend/src/views/tracing/components/TraceDetail.vue`
- `docs/zh/development/tracing-full-chain-design.md`

---

### [1.7.0] - 2026-04-10

#### New Features
- **Security Blacklist Management**: New security blacklist management feature, supporting IP/user/token blacklists
- **Enhanced Audit Logs**: Enhanced audit log query and display functionality with advanced search and statistics
- **JWT Account Status Toggle**: Implement account enable/disable status toggle functionality

#### Improvements & Optimizations
- **JWT Account Management**: Fixed password validation issue during editing, optimized account management interface
- **Frontend Table Optimization**: Table columns use adaptive width (min-width), added statistics cards and search functionality
- **Configuration Management Simplification**: Removed unimplemented version management features, simplified interface

#### Bug Fixes
- Fixed password validation issue when editing JWT accounts
- Fixed data display issue on account management page

#### Technical Improvements
- Added `enabled` field to `CreateJwtAccountRequest`
- Implemented `toggleAccountStatus` method in `JwtAccountService`
- Added `SecurityBlacklistController` and related entity classes
- Cleaned up unused frontend code and type definitions

---

### [1.6.2] - 2026-04-08

#### New Features
- **API Key Batch Import/Export**: Support batch import/export of API Keys
  - Added export endpoint `GET /api/auth/api-keys/export`
  - Added import endpoint `POST /api/auth/api-keys/import`
  - Support MERGE/REPLACE import modes
- **API Key Rotation**: Support automatic key rotation mechanism
  - Configure `rotationPeriodDays` to set rotation period
  - Added `ApiKeyRotationScheduler` for automatic rotation execution
- **Expired Key Auto Cleanup**: Added `ApiKeyExpirationScheduler` to automatically disable expired keys

#### Improvements & Optimizations
- **Creator Information Recording**: Record `createdBy` and `creatorIpAddress` when creating API Keys
- **Key Usage Statistics Persistence**: Usage statistics persisted via `saveApiKeysToStore()`

#### New Files
- `ApiKeyBatchExportVO.java`
- `ApiKeyBatchImportRequest.java`
- `ApiKeyBatchImportResult.java`
- `ApiKeyRotationScheduler.java`
- `ApiKeyExpirationScheduler.java`

---

### [1.6.1] - 2026-04-06

#### Security Fixes (P0)
- **API Key Hashed Storage**: API Keys stored using SHA-256 + salt hashing, replacing plaintext storage
- **Admin API Rate Limiting**: Added rate limiting (30/min, 100/hour, 10 create/hour)

#### New Features
- **IP Whitelist**: Support IP whitelist functionality (`allowedIpAddresses`)
- **Daily Request Limit**: Support daily request limit functionality (`dailyRequestLimit`)
- **Key Reset Interface**: Added key reset interface `/api/auth/api-keys/{keyId}/reset`

#### Improvements & Optimizations
- **Frontend Strong Typing**: Use strongly typed DTO/VO instead of Map data passing
- **Table Layout Optimization**: Optimized table layout and horizontal scroll support

#### New Files
- `ApiKeyHashUtil.java` - SHA-256 hash utility class
- `AdminApiRateLimiter.java` - Admin API rate limiting filter
- `ApiKeyVO/ApiKeyCreationVO/ApiKeyListVO/ApiKeyCreateRequest/ApiKeyUpdateRequest` - Strongly typed DTOs

---

### [1.6.0] - 2026-04-04

#### Breaking Changes
- **Removed Configuration Merge Feature**: Removed AutoMergeService and AutoMergeController
- **Removed Related Entity Classes**: Removed MergeResult and 5 related entity classes
- **Removed Frontend Page**: Removed ConfigMergeManagement.vue page and related API

#### Improvements & Optimizations
- **Configuration Version Management Optimization**: Simplified version management interface, retained core version switching functionality
- **Log Configuration Optimization**: Optimized logback-spring.xml configuration
- **Documentation Update**: Removed configuration merge related content

#### Retained Features
- `ConfigMergeService`: Core configuration retrieval and merge functionality
- `SecurityConfigMergeService`: Security configuration merge service

---

### [1.5.7] - 2026-04-02

#### New Features
- **JWT Account Initialization**: JWT accounts auto-initialized from YAML configuration to database
- **Account Management API Optimization**: Use standard RouterResponse response format

#### Bug Fixes
- Fixed JWT accounts not being initialized to database on system startup
- Fixed account management page unable to display data
- Fixed API path mismatch with frontend (`/api/admin/accounts` -> `/api/security/jwt/accounts`)

#### New Files
- `JwtAccountProperties.java` - Maps YAML account configuration
- `JwtConfig.accounts` field - Supports account list configuration

---

### [1.5.6] - 2026-03-30

#### New Features
- **Instance-level Rate Limiter Independent Storage**: Added `instance_rate_limit` table for instance rate limiter configuration
- **Instance-level Circuit Breaker Independent Storage**: Added `instance_circuit_breaker` table for instance circuit breaker configuration
- **Independent Configuration API**: Added independent rate limiter/circuit breaker configuration API endpoints
- **Strongly Typed DTO**: Use strongly typed DTO instead of Map data passing

#### API Changes
- `GET/PUT /api/config/instance/{type}/{id}/rate-limit`
- `GET/PUT /api/config/instance/{type}/{id}/circuit-breaker`

#### New Files
- `InstanceRateLimitEntity/InstanceCircuitBreakerEntity` - Entity classes
- `InstanceRateLimitRepository/InstanceCircuitBreakerRepository` - Repositories
- `InstanceRateLimitDTO/InstanceCircuitBreakerDTO` - DTO classes

#### Improvements & Optimizations
- `build-and-deploy.sh` script automatically cleans old compiled files

---

### [1.5.2] - 2026-03-20

#### New Features
- **JPA Migration Complete**: Completed aggressive migration from R2DBC to JPA
- **DTO Structure Optimization**: All core functions restored and optimized to DTO structure

#### Bug Fixes
- Fixed compilation errors during JPA migration
- Fixed service configuration function restoration

---

### [1.4.6] - 2026-03-10

#### Bug Fixes
- Fixed frontend independent configuration functionality
- Fixed frontend independent rate limiter and circuit breaker configuration functionality
- Fixed data return completeness issue
- Fixed `buildInstanceMap` and `convertToVO` methods

---

### [1.4.4] - 2026-03-31

#### Bug Fixes
- Fixed frontend instance management page data format issue
- Optimized data display logic

---

### [1.4.3] - 2026-03-25

#### Bug Fixes
- Fixed service type validation and exception handling logic
- Improved error messages

---

### [1.4.2] - 2026-03-25

#### New Features
- **Adapter Refactoring Plan**: Created adapter refactoring plan documentation for future architecture optimization

---

### [1.4.1] - 2026-03-24

#### New Features
- **Value Object Pattern**: Introduced InstanceId value object for improved code type safety

---

### [1.4.0] - 2026-03-24

#### Bug Fixes
- Gracefully fixed ConfigMergeService blocking call warning
- Optimized reactive programming model

---

### [1.2.5] - 2025-11-26

#### Improvements
- Merged remote branch updates
- Code synchronization and stability improvements

---

### [1.1.2] - 2025-10-30

#### Improvements
- Merged remote branch updates
- Code stability improvements

---

### [1.1.1] - 2025-10-28

#### Improvements
- **Frontend Routing Optimization**: Improved routing and authentication flow
- Code refactoring and cleanup

---

### [1.1.0] - 2025-10-28

#### Improvements
- Merged remote branch updates
- Feature stability improvements

---

### [1.0.0] - 2025-10-16

#### New Features
- **First Official Release**: JAiRouter project first official release version
- Basic gateway functionality
- Core adapter support

---

### [0.9.2] - 2025-09-30

#### New Features
- **ApiKey Model Unification**: Merged ApiKeyInfo and ApiKeyProperties into unified ApiKey model

---

### [0.9.1] - 2025-09-12

#### Bug Fixes
- Fixed merge errors
- Code stability improvements

---

### [0.9.0] - 2025-09-10

#### New Features
- **Web Console Architecture**: Added Web Console architecture design documentation
- Frontend management console planning

---

### [0.8.2] - 2025-09-05

#### Improvements
- Merged remote branch updates
- Code synchronization

---

### [0.8.1] - 2025-09-03

#### Improvements
- Updated project version number
- Version management standardization

---

### [0.7.3] - 2025-08-27

#### New Features
- **JWT Authentication**: Implemented JWT authentication and user management functionality
- Security module basic functionality

---

### [0.7.2] - 2025-08-27

#### New Features
- **Distributed Tracing Documentation**: Added distributed tracing system documentation

---

### [0.7.1] - 2025-08-27

#### New Features
- **Documentation Optimization**: Integrated Google Ads and optimized documentation styling

---

### [0.7.0] - 2025-08-22

#### New Features
- **Slow Query Alerts**: Added slow query alert functionality
- Monitoring module enhancement

---

### [0.6.1] - 2025-08-19

#### New Features
- **Internationalization Support**: Added internationalization and code compression support
- Documentation system enhancement

---

### [0.6.0] - 2025-08-18

#### New Features
- **Security Authentication**: Implemented API Key and JWT authentication functionality
- Security module core functionality

---

### [0.5.0] - 2025-08-18

#### New Features
- **Documentation Management**: Refactored documentation management workflow with unified management script
- Documentation system refactoring

---

### [0.4.0] - 2025-08-15

#### New Features
- **Prometheus Alert Rules**: Added Prometheus alert rules guide and configuration
- Added ALERT_RULES_GUIDE.md file
- Added alertmanager.yml configuration
- Created docker-compose-monitoring.yml monitoring stack configuration

---

### [Unreleased] - In Development

#### New Features
- **Security Module**: Complete enterprise-grade security features including API Key authentication, JWT token support, and bidirectional data sanitization
- **Multi-tenancy Support**: Tenant isolation, resource quotas, and tenant-based configuration management
- **Authentication and Authorization**: API Key authentication mechanism, JWT Token support, OAuth 2.0 integration, and Role-Based Access Control (RBAC)
- **Data Protection**: Request/response data obfuscation, encrypted storage of sensitive information, and security audit logs
- **H2 Database Support**: H2 embedded database as default storage with automatic data migration for configuration, security audit, API keys, and JWT accounts
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack with business and infrastructure metrics collection
- **Distributed Tracing**: End-to-end distributed tracing with Jaeger/Zipkin integration for full request lifecycle tracking
- Complete documentation system and user guides
- Multi-language documentation support (Chinese/English)
- Enhanced monitoring and alerting capabilities
- More adapter support

#### Improvements & Optimizations
- **Spring Security Integration**: Full integration with Spring Security framework for robust authentication and authorization
- **Cache Layer Optimization**: Redis cache optimization for improved performance
- **Database Support**: Added support for PostgreSQL and MySQL databases
- **H2 Storage Performance**: 5-20x performance improvement over file storage for configuration and security data operations
- **Docker Build Optimization**: China-optimized Docker builds with Alibaba Cloud Maven mirror, 5-10x faster dependency downloads
- Performance optimization and memory management improvements
- Better error handling and logging
- Configuration validation and user experience enhancements

#### Bug Fixes
- Fixed known memory leak issues
- Resolved race conditions in concurrent scenarios
- Improved stability of configuration hot updates
- Security scanning and vulnerability fixes
- Fixed configuration file path issues in container environments
- Resolved DNS resolution issues in Docker containers

---

### [0.3.2] - 2025-08-20

#### New Features
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack
- **Performance Metrics**: Added collection of business and infrastructure metrics
- **Alert Rules**: Pre-configured alert rules and notification mechanisms
- **Monitoring Dashboard**: Professional Grafana dashboard templates

#### Improvements & Optimizations
- **Metrics Optimization**: Optimized metrics collection performance to reduce impact on main business
- **Memory Management**: Improved memory usage and cleanup mechanisms for metric data
- **Configuration Simplification**: Simplified monitoring-related configuration parameters

#### Bug Fixes
- Fixed inaccurate monitoring metrics under high concurrency
- Resolved memory leak issues after long-term operation
- Fixed Prometheus metrics format compatibility issues

#### Technical Debt
- Refactored monitoring module code structure
- Improved unit test coverage
- Optimized build and deployment processes

---

### [0.3.1] - 2025-08-15

#### New Features
- **China Accelerated Build**: Docker builds optimized specifically for Chinese users
- **Alibaba Cloud Mirror**: Using Alibaba Cloud Maven mirror to accelerate dependency downloads
- **Build Script**: Added `docker-build-china.sh` build script
- **Maven Profile**: Added china profile support

#### Improvements & Optimizations
- **Build Speed**: Build speed for Chinese users improved by 5-10 times
- **Network Optimization**: Optimized network connections and timeout configurations
- **Documentation Enhancement**: Added China-specific build guides

#### Bug Fixes
- Fixed dependency download timeout issues in Chinese network environments
- Resolved unstable Maven repository connection issues
- Fixed network errors during Docker builds

---

### [0.3.0] - 2025-08-14

#### New Features
- **Docker Containerization**: Complete Docker deployment support
- **Multi-environment Deployment**: Support for development, testing, and production environment configurations
- **Docker Compose**: Provided complete container orchestration configuration
- **Health Check**: Container-level health check mechanism
- **Monitoring Integration**: Basic monitoring metrics exposure

#### Improvements & Optimizations
- **Image Optimization**: Multi-stage builds, production image approximately 200MB
- **Security Hardening**: Running as non-root user, principle of least privilege
- **Performance Tuning**: JVM parameter optimization in container environments
- **Log Management**: Log collection and rotation in containerized environments

#### Bug Fixes
- Fixed configuration file path issues in container environments
- Resolved configuration loss after container restarts
- Fixed network communication issues between containers

#### Breaking Changes
- Default configuration file path changed from `./config` to `/app/config`
- Environment variable naming convention adjusted

---

### [0.2.1] - 2025-08-12

#### New Features
- **Scheduled Cleanup Task**: Automatic cleanup of inactive rate limiters to prevent memory leaks
- **Memory Optimization**: Improved memory usage patterns to reduce GC pressure
- **Enhanced Client IP Rate Limiting**: More precise client IP identification and rate limiting
- **Automatic Configuration File Merging**: Support for intelligent merging of multi-version configuration files

#### Improvements & Optimizations
- **Performance Improvement**: Rate limiter performance optimization to reduce lock contention
- **Enhanced Monitoring**: Added monitoring metrics for memory usage and cleanup tasks
- **Log Optimization**: Improved log format and performance
- **Error Handling**: Better exception handling and error recovery mechanisms

#### Bug Fixes
- Fixed memory leak issues after long-term operation
- Resolved rate limiter race conditions in high-concurrency scenarios
- Fixed thread safety issues during configuration hot updates
- Resolved client IP acquisition issues in proxy environments

#### Technical Improvements
- Refactored rate limiter cleanup mechanism
- Improved unit test coverage to 85%
- Optimized code quality check rules

---

### [0.2.0] - 2024-08-11

#### New Features
- **Rate Limiting Mechanism**: Support for Token Bucket, Leaky Bucket, Sliding Window, and Warm Up rate limiting algorithms
- **Circuit Breaker**: Implemented circuit breaker pattern with support for failure thresholds, recovery detection, and fallback strategies
- **Fallback Strategies**: Support for default responses and cache fallback
- **Configuration Persistence**: Support for both in-memory and file storage backends
- **Dynamic Configuration Updates**: Runtime updates for service instances, weights, rate limiting, and circuit breaking configurations

#### Improvements & Optimizations
- **Performance Optimization**: Reactive programming model supporting high-concurrency processing
- **Configuration Management**: Automatic configuration file merging and version management
- **Error Handling**: Comprehensive exception handling and error recovery mechanisms
- **Monitoring Metrics**: Added rate limiting and circuit breaking related monitoring metrics

#### Bug Fixes
- Fixed thread safety issues in load balancer during instance changes
- Resolved data consistency issues during configuration updates
- Fixed memory leak issues in high-concurrency scenarios

#### API Changes
- Added dynamic configuration management API (`/api/config/instance/*`)
- Added configuration file merging API (`/api/config/merge/*`)
- Extended health check API to include more status information

---

### [0.1.0] - 2025-08-04

#### New Features
- **Basic Gateway**: Unified `/v1/*` API gateway supporting OpenAI-compatible format
- **Service Type Support**: Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing
- **Adapter Pattern**: Support for GPUStack, Ollama, VLLM, Xinference, LocalAI, and OpenAI adapters
- **Load Balancing**: Implemented Random, Round Robin, Least Connections, and IP Hash strategies
- **Health Check**: Independent status interface per service, automatic removal of unavailable instances
- **Configuration Management**: Static configuration support based on YAML

#### Technical Features
- **Spring Boot 3.5.x**: Based on the latest Spring Boot framework
- **Reactive Programming**: Using Spring WebFlux and Reactor Core
- **Code Quality**: Integrated Checkstyle, SpotBugs, JaCoCo code quality tools
- **API Documentation**: Automatically generated API documentation using SpringDoc OpenAPI
- **Unit Testing**: Unit tests for core functions including load balancing and health checks

#### Project Structure
- Established clear modular architecture
- Defined unified coding standards and best practices
- Established complete build and testing processes

---

## Upgrade Guide

### Upgrading from 0.3.1 to 0.3.2

#### Configuration Changes
```yaml
# New monitoring configuration
monitoring:
  metrics:
    enabled: true
    categories:
      - system
      - business
      - infrastructure
```

#### Deployment Changes
- Added Prometheus and Grafana containers
- Updated `docker-compose.yml` configuration
- Imported new Grafana dashboards

#### Notes
- Monitoring functionality is enabled by default, which may add slight performance overhead
- New monitoring endpoints require corresponding network configuration

### Upgrading from 0.2.1 to 0.3.0

#### Breaking Changes
- Configuration file path change: `./config` → `/app/config`
- Environment variable naming adjustment

#### Migration Steps
1. Update configuration file paths
2. Adjust environment variable names
3. Update deployment scripts and container configurations

### Upgrading from 0.1.0 to 0.2.0

#### New Dependencies
- No additional dependencies required, all features are built-in

#### Configuration Extensions
```yaml
# New rate limiting configuration
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10

# New circuit breaker configuration
      circuit-breaker:
        failure-threshold: 5
        recovery-timeout: 30s
        success-threshold: 3
```

## Known Issues

### Current Version (0.3.2)
- In extremely high concurrency scenarios (>10k RPS), monitoring metrics may experience slight delays
- Docker containers may experience slow DNS resolution in certain network environments

### Historical Issues
- ~~0.3.1: Build timeouts in Chinese network environments~~ (Fixed)
- ~~0.2.1: Memory leaks after long-term operation~~ (Fixed)
- ~~0.2.0: Race conditions during configuration updates in high-concurrency scenarios~~ (Fixed)

## Contributors

Thank you to all developers who have contributed to the JAiRouter project:

- **Core Team**: Responsible for architecture design and core feature development
- **Community Contributors**: Provided feature suggestions, bug reports, and code contributions
- **Documentation Team**: Improved project documentation and user guides
- **Testing Team**: Conducted functional testing and performance verification

## Feedback and Suggestions

If you encounter issues or have improvement suggestions during usage, please feel free to provide feedback through the following channels:

- **GitHub Issues**: [Submit issue report](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [Participate in discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Email Contact**: jairouter@example.com

We value every piece of feedback and will respond and address them promptly.

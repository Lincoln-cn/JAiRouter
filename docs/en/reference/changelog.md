# Changelog

<!-- 版本信息 -->
> **Document Version**: 3.5.10
> **Last Updated**: 2026-11-01
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
### [3.5.10] - 2026-11-01 - Measured performance baseline, E2E and coverage gates, OpenTelemetry migration (#940-#959)

The batch after v3.5.9 closes three areas that had never been systematically covered:
performance capacity now has reproducible load-test assets and measured numbers, the console
E2E suite actually runs in CI, and the coverage gate went from "configured but never executed"
to a real gate. The OpenTelemetry 1.35 to 1.67 migration is included as well.
**No API-breaking changes**; four things worth knowing are listed in "Upgrade notes" below.

#### Performance and capacity

- **Load-test assets and a measured baseline (#944)**: added `scripts/perf/mock-upstream.mjs` (a dependency-free OpenAI-compatible upstream) and `scripts/perf/loadtest.mjs` (a dependency-free load generator reporting RPS / p50 / p95 / p99 / error rate). `troubleshooting/performance.md` (zh/en) gained a "How we measure in this repository (reproducible)" section: the three rate-limit switches, a dedicated database recommendation, the three commands, the baseline table and how to read it. The "RPS > 100" claim in the docs had never been measured on a real build.
- **Connection pool parameters made explicit (#945)**: `config-service/core.yml` now carries a `hikari` block (`maximum-pool-size: 10`, `minimum-idle: 2`, `connection-timeout: 30000`, `max-lifetime: 1800000`). These are the values Hikari used implicitly, so **runtime behaviour is unchanged**; the point is that pool sizing becomes a reviewable decision. `HikariPoolSettingsTest` binds the real YAML and asserts all four values (10 to 20 turns it red).
- **The saturation was not the connection pool (#945)**: at the ~100 RPS saturation point the pool is not the bottleneck. Raising it from 10 to 32 at concurrency 100 gave 384.7 versus 368.6 RPS while p99 went from 1160 ms to 2552 ms; the outbound reactor pool never had a pending connection; three jstack samples showed all 8 `reactor-http-nio-*` threads parked in `Selector.select`. The actual cause was **dev profile logging `org.unreal.modelrouter` at DEBUG**: a 20-second run wrote 425,210 lines (about 21k lines per second). Switching to WARN raised throughput from 97.2 to 320.5 RPS and dropped p50 from 493 ms to 79.8 ms at the same concurrency. The docs now say "turn off DEBUG before load testing".
- **Capacity metrics filled in (#947)**: the outbound pool now publishes `jairouter.outbound.requests.inflight` and `outbound.request.duration`, and reactor's built-in metrics are enabled (`reactor.netty.connection.provider.*` tagged `name=jairouter-outbound`). SSE long connections publish `jairouter.sse.active.connections` labelled by endpoint, wired into `v1-chat-completions`, `chat-completions`, `anthropic-messages` and `health-status`. The response cache publishes `jairouter.cache.response.size` (estimated on demand, without triggering cleanup).

#### CI gates

- **The coverage gate runs for the first time (#949)**: the JaCoCo rules were defined inside an execution while CI only ran `mvn -B test`, so `check` never fired. Measured coverage was 54.23% lines and 32.47% branches. The rules are now plugin-level, CI runs `mvn -B jacoco:check` explicitly, and the thresholds sit just below the current values (0.52 lines, 0.31 branches, 0.35 complexity).
- **Console E2E in CI (#948)**: added `.github/workflows/e2e-smoke.yml`, which boots a real backend and runs the `smoke,overview` specs whenever frontend or backend code changes, dumping backend logs on failure. `harness.mjs` no longer hard-codes a Chromium path from the developer machine (it reads `CHROMIUM_PATH` or asks playwright); `runner.mjs` gained an `E2E_MIN_CHECKS` anti-vacuous-pass guard (fewer checks than the floor exits 1, so "one check ran" cannot look green). The four overview-page jump assertions now match both Chinese and English labels instead of depending on browser locale.

#### Dependencies

- **OpenTelemetry 1.35 to 1.67 (#940)**: after the upgrade 17 tracing tests failed. The root cause was not span semantics but that **an `SdkTracerProvider` with no SpanProcessor degrades to a noop provider** (`PropagatedSpan`, `isValid()` false). Production code always installs a `BatchSpanProcessor`, so only the 5 test fixtures were affected (they now use `TestTracerProviders.sdk()`). The `zipkin` exporter left the BOM at 1.65; this repository keeps that export path and pins the last published release `1.64.0`, with `ZipkinExporterCompatibilityTest` exercising a real `SpanData` export to guard the binary compatibility of the mix. OSV findings dropped from 4 unaccepted advisories to **0**.

#### Documentation

- **Storage examples moved to the real configuration surface (#946)**: the 6 `spring.r2dbc.*` examples in `store-config.md` (zh/en) became `spring.datasource.*` plus `spring.datasource.hikari.*`; `store.type` accepts only `jpa` / `h2` (rejected at startup by `ConfigIntegrityValidator`), so the file and memory sections are gone; the Hikari failure message quoted is now the real `Connection is not available, request timed out after 30000ms`; the H2 data file path is corrected to `./data/jairouter`.
- **A fabricated section in the configuration overview (#955)**: section 6 of `configuration/index.md` was entirely invented (`store.type: file`, `auto-backup`, `backup-interval` match nothing in the code). It now describes the real configuration surface and states that there is no automatic backup switch.

#### Upgrade notes

1. **No API-breaking changes**: error codes, protocol shapes and endpoints are untouched.
2. **Connection pool parameters are now explicit**: `spring.datasource.hikari.*` is written out in `config-service/core.yml` (10 / 2 / 30000 / 1800000). Change pool size there instead of relying on Hikari's implicit defaults.
3. **OpenTelemetry moved to 1.67**: users of `tracing.exporter.type=zipkin` need to do nothing, the exporter is pinned to a compatible version. Code that builds its own `SdkTracerProvider` should make sure it installs a SpanProcessor.
4. **Two new CI gates**: code changes must pass `jacoco:check` (52% lines, 31% branches); frontend or backend changes must pass `e2e-smoke`. Local reproduction steps are in `development/contributing.md`.


### [3.5.9] - 2026-11-01 - Dependency security baseline, client-visible error semantics, and multi-replica startup fixes (#899-#941)

A batch of follow-ups after v3.5.8: supply-chain security, client-visible error semantics, multi-replica startup and
credentials, a frontend XSS fix, and a set of guard tests. **No API breaking changes**, but five behavior changes are
listed under "Upgrade notes" below.

#### Dependencies and supply chain

- **Runtime dependencies moved to the security fix line (#931)**: Spring Boot 3.5.5 → 3.5.16, plus pins the BOM still
  lagged on: netty 4.1.139, jackson 2.21.7, postgresql 42.7.14, log4j2 2.26.1, commons-lang3 3.19.0. Querying
  `mvn dependency:list` against OSV dropped **27 artifacts / 102 advisories (4 CRITICAL) to 3 artifacts / 4 advisories**,
  with zero unaccepted risk.
- **A dependency gate now exists (#931 / #941)**: `scripts/audit/osv-scan.py` + `osv-baseline.json` +
  `.github/workflows/dependency-scan.yml`, running weekly and on any `pom.xml` / `package*.json` change. The same
  workflow adds a frontend `npm audit --omit=dev --audit-level=high` step that only fails on production dependencies;
  development-chain advisories are recorded but do not fail the build. A CycloneDX SBOM is produced as a release artifact.
- **Three frontend transitive dependencies closed out (#96 / #98)**: `follow-redirects` 1.16.1 → 1.16.2 (high, via
  axios), `minimatch` 10.2.5 → 11.0.3 and `glob` 13.0.6 → 14.0.3 (moderate). `npm audit` went from 21 findings to 0;
  lint, build, and 221 unit tests pass.

#### Client-visible error semantics

- **Daily quota exhaustion returns 429 instead of 401 (#926)**: 401 means "the credential is unusable", and OpenAI /
  Anthropic clients do not retry it — they mark the key invalid. A daily quota only resets the next day.
  `DAILY_QUOTA_EXCEEDED` and `TOKEN_QUOTA_EXCEEDED` now both map to 429.
- **Upstream timeouts map to 504 and connection failures to 502 (#924)**: both previously fell into the generic 500 and
  leaked the raw Netty text. They are now distinguished by the cause chain, `extractHttpStatus` follows suit, and
  metrics no longer record a 504 as a 500.
- **An instance baseUrl rejected by the SSRF guard returns 400 (#928)**: the write-side check threw a JDK
  `SecurityException` while the global handler only recognizes the repository's own exception type, so an administrator
  aiming at a forbidden address saw "500 system error". A new `INVALID_BASE_URL` (400) normalizes the JDK exception.
- **The `path` field of security error bodies is the real request path again (#930)**: `getCurrentPath()` always
  returned the placeholder `/api/security`, unrelated to the request. It now reads the actual path from
  `ServerWebExchange`, or `null` when there is no exchange — no invented values.

#### Multi-replica and credentials

- **Enabling cluster event broadcasting failed startup (#918)**: `RedisClusterEventBus` has two public constructors and
  neither was annotated `@Autowired`, so Spring fell back to a no-arg constructor that does not exist; with
  `jairouter.cluster.events.enabled=true` startup died with `No default constructor found`. The two-argument
  constructor is now annotated, and a context test covers both "Redis available" and "degraded without Redis".
- **Replicas that do not share `JWT_SECRET` now warn (#919)**: the default is `${JWT_SECRET:${random.uuid}}`, so
  without the environment variable each replica signs with its own key and roughly `(n-1)/n` of requests intermittently
  get 401. The multi-replica self-check emits a WARN stating that tokens cannot be verified across replicas; health
  status is unchanged.
- **The multi-replica contract is documented (#920)**: `multi-replica.md` (zh/en) gains an "all replicas must share one
  `JWT_SECRET`" precondition, plus measured numbers — a newly created key becomes visible across replicas in about
  28.5 seconds, revocation convergence is bounded by the refresh interval but one case took over 80 seconds, and a key's
  `rateLimitPerMinute` is a per-replica local token bucket regardless of `ratelimit.distributed.enabled`.

#### Security

- **Monitor WebSocket control messages are now administrator-only (#916)**: both WS endpoints push events and accept
  `pause` / `resume` / `updateConfig`, but the security rule was only `authenticated()`, while the REST side requires
  `MONITORING_ROUTING_READ` / `LB_CONFIG_WRITE`. A key holding only `ROLE_CHAT` got 403 over REST yet could pause
  monitoring over WS. Control messages are now checked for `ROLE_ADMIN` at the entry point; others are logged as a
  warning and ignored.
- **The console sanitizes model output before rendering (#899)**: Playground assistant messages were rendered by
  `marked` and injected with `v-html`, and `marked` has shipped no `sanitize` since v5 — so HTML produced by a model ran
  with administrator privileges. `useMarkdown` now routes everything through `DOMPurify`.

#### Observability and logging

- **The default profile logs at INFO, and stdout is written once (#932)**: the default inherited DEBUG from root and
  printed each line twice through two appenders; at 3 rps that produced 2.9 GB in 6.5 hours. The default is now INFO
  with a single stdout writer; enable DEBUG explicitly when needed.

#### Guards and tooling

- **One-command release script plus a seven-place version guard (#914)**: `scripts/release/release.py`
  (`check` / `bump` / `tag`, dry-run by default) and `ReleaseVersionConsistencyTest`; the release checklist is corrected
  from four places to seven.
- **The key sets of three external error surfaces are pinned key by key (#912)**: a new
  `ErrorBodyContractSnapshotTest` asserts the top-level keys for nine status codes on the OpenAI surface, that the
  Anthropic surface never carries `code`, and that the console surface is not taken over by the `/v1` shape.
- **The config-binding scan is now a hard assertion instead of an audit mode (#911)**: the unbound-key set must equal
  the registry and the orphan-key set must be empty; it also fixes the constant-resolution false positive that reported
  `redis-reachability` / `redis-timeout-ms` as orphans.
- **A parity guard for the two migration sets (#908)**: `MigrationScriptParityTest` asserts that the H2 and PostgreSQL
  version sets and descriptions match exactly and that no version repeats within a dialect, with an anti-vacuity check.
- **Known-flaky external hosts are registered (#934)**: `regexr.com` is treated as a site-side flake so document-only
  changes are not repeatedly failed by it.
- **The release script no longer leaves a stale date on `latest_release` (#942)**: `bump` used to replace only
  up to the opening parenthesis, leaving the previous date behind, and `check` only read the version so it never
  noticed; the line is now replaced whole and `check` matches the full line (version + date + end of line).

#### Documentation

- **One Kubernetes Secret convention (#939)**: the old examples used `jairouter-secret` (singular) with
  `api-key` / `jwt-secret` / `database-password` keys, incompatible with the manifests' `jairouter-secrets` +
  `envFrom.secretRef`, so copying them produced `CreateContainerConfigError`. Everything now uses `jairouter-secrets`
  with environment-variable key names (`JWT_SECRET` / `INITIAL_ADMIN_PASSWORD` / `REDIS_PASSWORD`), and the examples no
  longer mention the non-existent `PROD_*_API_KEY` or the removed `api-key.keys` shape.

#### Upgrade notes (behavior changes)

1. **Quota exhaustion changed status codes**: `DAILY_QUOTA_EXCEEDED` / `TOKEN_QUOTA_EXCEEDED` now return 429 instead of
   401. Clients that treat 401 as "key invalid" must be updated.
2. **Upstream failures changed status codes**: timeouts are now 504 and connection failures 502 (both were 500).
3. **The default log level is INFO**: the default profile no longer emits DEBUG; set `logging.level.*` explicitly when
   troubleshooting.
4. **The dependency gate can fail a PR**: the new OSV / `npm audit` steps fail on `high` and above. Items that
   cannot be fixed go into `scripts/audit/osv-baseline.json` with a reason.
5. **WebSocket control messages are administrator-only**: `pause` / `resume` / `updateConfig` from a low-privilege
   principal are ignored and logged as a warning.

### [3.5.8] - 2026-10-31 - Two P0s in the observability path, plus nineteen correctness and honesty fixes (#848-#897)

The second audit pass: the observability path first, then every remaining surface. **No API breaking changes**,
but six behavior changes are listed under "Upgrade notes" below.

#### Blocking (P0, both in the observability path)

- **The async metrics processor fed its own output back into its queue (#848)**: `AsyncMetricsProcessor` injected a `@Lazy MetricsCollector`, which resolved to the `@Primary` async collector itself, so every processed event enqueued another one. Measured: 1.67 million self-produced events in 71 seconds, the `metrics-processor` thread burning ~75% of a core, and not a single `jairouter_requests_total` in `/actuator/prometheus` — business metrics were drowned by its own noise. The processor now injects the synchronous collector and has a fuse that keeps processor-thread work synchronous.
- **Disabling tracing failed startup (#849)**: beans such as `TracingService` required a `Tracer`, which is only provided under `@ConditionalOnProperty(tracing.enabled=true)`, so the `enabled: false` shown in the docs exited with `APPLICATION FAILED TO START`. A no-op `Tracer` is now supplied when tracing is off, and `TracingHealthIndicator` reports UP instead of DOWN for a disabled optional component (the Docker HEALTHCHECK uses the aggregate endpoint, so DOWN caused restart loops).

#### Request-path correctness

- **The fallback cache recomputed its write key from a cleared ThreadLocal (#850)**: the read key was built during assembly while the write key was rebuilt when the response arrived, after `AffinityContextHolder` had been cleared, so writes were keyed `anonymous` and `strategy: cache` never hit. Writes now reuse the assembly-time key.
- **Health decisions had no hysteresis (#855)**: one bad round marked a whole service 503. An instance is now removed only after `failure-threshold` (default 2) consecutive failures.
- **Generic 500s echoed the exception message (#856)**: `"系统异常: " + ex.getMessage()` leaked details such as `jdbc:...password=…` to clients. The body is now the fixed text 「系统异常，请稍后重试」 while the original goes to the log.
- **Hourly audit statistics bypassed the transaction proxy (#857)**: `this::flushHourlyStatsToDatabase` no longer goes through the Spring proxy, so `@Transactional` did not apply, `merge` threw `TransactionRequiredException`, and the exception was swallowed — statistics never reached the database. The call now goes through an `ObjectProvider` proxy.
- **Uploads had no size limit (#862)**: audio and image uploads were read fully into the heap before being forwarded (`spring.codec.multipart.max-in-memory-size` does not cover `FilePart.content()`); an 80 MiB upload was accepted end to end. The default limit is now 25 MiB (`jairouter.multipart.max-file-size-bytes`), and oversized uploads answer 413 after releasing the buffered `DataBuffer`s.
- **The health SSE stream created one timer per connection (#866)**: N console tabs meant 2N full-table scans every 5 seconds. One shared snapshot is now used (`share()` with ref counting) and the timer stops when nobody listens.
- **The half-open circuit breaker let all concurrent requests through (#870)**: after `OPEN → HALF_OPEN` it returned true for everyone, so the backlog after a 60-second open period hit a just-recovered instance at once. A single probe is now admitted; the rest fall back to the timeout rule.
- **Management endpoints blocked the event loop (#854 / #864)**: seven monitor controllers and other synchronous controllers returned `ResponseEntity` while doing JPA on a Netty thread. They were first offloaded by an allow-list (#854) and then by an inverse rule: everything under `/api/**` is offloaded except `/api/v1/**` (streaming), `/api/health-status/**` (SSE) and `/actuator/metrics`.

#### Data and configuration honesty

- **The audit event-type constraint allowed only 16 values (#868)**: `AuditEventType` has 26 constants but H2's `enum(...)` and PostgreSQL's `check` allowed 16, so ten event types could never be written and the hot paths swallow the failure — the audit trail silently lost those rows. Migration `V7` rewrites the constraint on both databases.
- **The fallback cache never honoured `cache-ttl` (#872)**: `ComponentFactory` only passed `cache-size`. TTL now applies and expired entries count as misses.
- **Fallback strategies were never rebuilt on config change (#873)**: they were cached per `serviceType`. A configuration fingerprint now decides whether to rebuild.
- **First-start initialization was not atomic (#874)**: the condition was "both tables are empty", so a failure after writing `config_data` skipped `service_config` forever. Each store is now checked independently and a failed attempt rolls back its own writes.
- **`store.type` and `spring.r2dbc.*` had no consumer (#875)**: `store.type` only ever selected JPA, and `spring.r2dbc.*` had no dependency behind it. Accepted values are now `jpa` / `h2`, the R2DBC block is gone, and other values log a warning.
- **Config versions were an in-process counter with no rollback (#876)**: versions are now derived from persisted history and rollback appends the old snapshot as a new version.
- **Routing statistics and client-IP limiters were unbounded or non-atomic (#877 / #878)**: the client map is capped (10,000 default) and evicts in insertion order; Caffeine's `get(key, loader)` loads atomically so concurrent first accesses share one token bucket.
- **Weak-secret detection did not say what matched (#879)**: a long passphrase that happens to contain `abcdef` was judged VERY_WEAK and refused startup. A fragment now has to dominate (15% or more) and the message names it with its share.
- **An ephemeral encryption key was used silently (#880)**: without `jairouter.security.encryption.key` the service generated a throwaway key, so backups written in that process could not be decrypted after a restart. Startup now warns, logs only a key fingerprint, and decryption failures name the configuration key to set.
- **A zero-capacity fallback cache had no limit and arbitrary eviction (#890)**: `cache-size <= 0` now disables caching and eviction follows insertion order.
- **Streaming responses were cached as fallback entries (#892)**: a `Flux` body was held as an ordinary response, and replaying it subscribed a second time, producing an empty stream. The write side now rejects `Publisher` bodies and the read side treats historical entries as misses.

#### Repository and dependency hygiene

- **`SECURITY.md` added (#894)**: reporting channel (private vulnerability reporting), supported versions, scope, the two accepted CodeQL findings, and a hardening checklist; both READMEs link to it.
- **`HttpRequestProcessor` removed (#896)**: a 170-line third "send layer" whose `getHttpRequestProcessor()` had zero callers.
- **Dependency bumps (#799 / #800)**: `vue-i18n`, `@intlify/shared` and `@intlify/core-base` to 11.4.16.

#### Upgrade notes

1. Health checks need `failure-threshold` consecutive failures (default 2) before marking an instance down; set it to 1 for the previous behavior.
2. Uploads above `jairouter.multipart.max-file-size-bytes` (25 MiB default) answer 413 instead of being read into the heap.
3. Flyway migration `V7__audit_event_type_values.sql` rewrites the `security_audit.event_type` constraint on H2 and PostgreSQL; compare with any manual change you made to that constraint.
4. `store.type` accepts only `jpa` / `h2`; `redis` / `memory` now fail at startup and `spring.r2dbc.*` is gone.
5. `fallback.cache-size <= 0` now disables caching, and the cache honours `cache-ttl` (300000 ms default).
6. Generic 500 responses are the fixed text 「系统异常，请稍后重试」 and no longer contain the exception message.

### [3.5.7] - 2026-10-31 - Five blocking P0s closed, plus four capability gaps (#801-#846)

A batch of fixes that followed an audit of the core request path: the five P0 defects that could block real usage are fixed, along with 16 P1-P3 items. **No API breaking changes**, but four items affect existing deployments — health-check mode, default upstream, JWT header name and the new key format (see "Upgrade notes").

#### The five blocking defects (P0)

- **API-key authentication was off under the default profile (#801)**: `security-base.yml` was imported after `config/auth/api-key.yml` and pinned `api-key.enabled: false`, so running without a profile rejected every API key with 401. The security base template no longer declares any `api-key.*` key, and a guard test keeps it that way.
- **The admin password printed at startup did not work after a restart (#802)**: the password only matches while the account row is first created, yet every start printed the current environment/random value. It is now printed only on the start that actually creates the account; later starts say so explicitly.
- **`--spring.profiles.active=legacy` could not start (#803)**: the profile file set `spring.profiles.active` on itself and blanked `jwt.secret`. Both are gone; the legacy profile starts normally.
- **Default services pointed at an internal example address (#804)**: all nine instances used `http://172.16.30.6:9090`, which together with the health gate produced 503 on every endpoint. They now use `${MODEL_UPSTREAM_BASE_URL:http://localhost:11434}`.
- **JWT header name contained an underscore (#805 / #846)**: nginx drops such headers by default (`underscores_in_headers off`), so login succeeded and every later call returned 401. The default is now `X-Jairouter-Token`, with the old name kept as a fallback (including the Java field default).

#### Core path correctness

- **Oversized responses pass through instead of being skipped or failing (#822)**: responses above the sanitization buffer are streamed through unchanged; chunked responses no longer end in 500.
- **Cross-origin preflight is accepted when configured (#818)**: new `jairouter.security.cors.allowed-origins`; CORS stays off by default.
- **Fallback cache is keyed per request (#823)**: `FallbackStrategy` now takes a `FallbackRequestKey` (service + model + caller + request hash); 2xx responses are cached, hits replay with `X-Fallback: cache` and are counted. Responses are never shared across callers.
- **Runtime configuration is replaced as a snapshot with rollback (#819)**: instance/adapter updates copy-modify-publish; a failed refresh rolls back all three references; derived components (load balancers, rate limiters) no longer fail silently.
- **Media services no longer retry after the request was sent (#827)**: `tts` / `stt` / `imgGen` / `imgEdit` retry only when the connection was never established.
- **staging/prod validate shared-state reachability at startup (#825)**: enabling a Redis-backed switch without Redis now fails fast with an actionable message (`OFF`/`WARN`/`FAIL`, staging/prod only).
- **JWT secret length is no longer enforced by Bean Validation (#821)**: the constraint failed before the startup gate, making `JAIRouter_SKIP_AUTH_WARNING=true` useless; strength checks stay in `SecurityConfigurationValidator` and `StartupSecretKeyChecker`.
- **API-key usage statistics write one record instead of the whole table (#811)**, and **YAML-provided keys normalize legacy permissions (#813)**.
- **The fallback strategy is evaluated once (#816)**.

#### Capability and observability gaps

- **`/v1` gained native audio and image endpoints (#835)**: `POST /v1/audio/speech`, `/v1/audio/transcriptions`, `/v1/images/generations`, `/v1/images/edits`; unmapped media paths still answer 501 with a `/api/v1/...` pointer (#830).
- **Keys embed their keyId (#826)**: new keys look like `sk-<keyId>.<secret>`, turning the per-request full-cache scan into a single indexed lookup; legacy keys keep working.
- **Health checks default to HTTP (#833)**: `mode: http` with `/v1/models`; 2xx/3xx/404/405 count as healthy, 5xx/timeouts/refused as unhealthy.
- **Shared service-level rate-limit buckets are announced (#824)**, and **auth exclusions match on a path boundary (#834)** so `/health` no longer makes `/healthz` anonymous.
- **Documentation header examples were unified (#820)**: 143 `Authorization: Bearer` examples became `X-API-Key` / `X-Jairouter-Token`; the 8 that genuinely describe downstream credentials were kept.

#### Upgrade notes

1. **Health checks default to HTTP**: the gateway now sends `GET <base-url>/v1/models`; a 5xx from upstream marks the instance unhealthy. Set `jairouter.health-check.mode: tcp` to keep the old behavior.
2. **JWT header name is `X-Jairouter-Token`**: the old name still works as a fallback, but migrate clients.
3. **Default upstream is `http://localhost:11434`**: set `MODEL_UPSTREAM_BASE_URL` for real deployments.
4. **New key format `sk-<keyId>.<secret>`**: treat keys as opaque strings; legacy keys keep working.
5. **staging/prod validate shared-state Redis reachability**: set `jairouter.cluster.shared-state-check.redis-reachability: OFF` if the check does not apply.

### [3.5.6] - 2026-10-30 - Bug fixes (the 14 issues left open when the v3.5.5 audit closed: #776-#789)

A follow-up patch to `v3.5.5`: every one of the 14 issues still open when the audit closed is now fixed — accounts and permissions, configuration writes that did not reach the runtime, the entry protocol surface, a console field that was never echoed back, configuration and test honesty, and three defects that needed a behavior decision before any code could change. **No API breaking changes**, but three behavior changes and one schema migration are worth reading before upgrading (see "Upgrade notes").

#### Accounts and permissions (#789 / #777)

- **JWT accounts resolve from the database (#789, P1)**: authentication looked at the `auth/jwt.yml` accounts first and, on a hit, never consulted the database — so an account deleted or disabled in the console could still sign in through its YAML twin (and a delete was undone at the next start by the seeding step). A database row is now authoritative whenever it exists: disabled means rejected, deleted means gone. The YAML accounts are reduced to first-start seed data (written only while the table is empty), and the file says so in a comment.
- **Console account deletion is a soft delete (#789)**: a hard delete let the seeding step resurrect the YAML twin after a restart — "deleted, then back again". Deletion now sets a `deleted` flag (migration `V6__jwt_account_soft_delete.sql`, one for H2 and one for PostgreSQL); listing and login both filter on it, and re-creating a deleted username revives the row instead of inserting a duplicate.
- **The API key view echoes the IP allowlist (#777, P3)**: `ApiKeyVO` gains a read-only `allowedIpAddresses` list (empty when there is no allowlist) and the console edit dialog seeds it — saving the dialog used to wipe the allowlist.

#### Console writes that reach the runtime (#776 / #778)

- **Service configuration writes refresh the runtime registry (#776, P2)**: saving, deleting or updating a service configuration persisted it and refreshed a local cache, but the per-service instance set cached by the router was untouched — the console reported success while routing kept the old instances until a restart. All six write paths now call `ModelServiceRegistry.refreshServiceConfig(serviceType)`; validation failures do not refresh.
- **Adapter load failures are no longer silent (#778, P2)**: a definition that failed to load was caught, logged, and then simply missing. Failures are now recorded in the registry (`loadFailures`) and surfaced by a new `/actuator/health` indicator `adapterLoad` (`jairouter.router.adapter.health.enabled` defaults to on; `fail-health` defaults to false, so it reports without turning the container unhealthy and dropping the replica out of rotation because of one bad definition).

#### Entry protocol surface (#779 / #781)

- **Empty bodies on `/v1/messages` answer 400 (#779, P2)**: an empty body used to end as an NPE-driven 500. It now behaves like `/v1/chat/completions` and returns 400.
- **413 on `/v1/messages` uses the Anthropic error type (#781, P3)**: an oversized body used to answer `invalid_request_error`, which Anthropic clients read as "your parameters are wrong". 413 now maps to `request_too_large` on the Anthropic surface; the OpenAI-native surface keeps `invalid_request_error`.

#### Configuration and test honesty (#787 / #788 / #783 / #784)

- **Three unreachable management endpoint switches removed (#787, P3)**: `jwt-tokens`, `jwt-blacklist` and `jwt-persistence-health` had no `@Endpoint` implementation anywhere in the repository — enabling them exposed nothing. Removed from three yml files.
- **Dead persistence property classes removed (#788, P3)**: the two `@ConfigurationProperties` classes and the two `@Bean` methods in `JwtPersistenceConfiguration` had no readers; the file is gone. The `jairouter.security.jwt.persistence.*` key tree itself is still live (several `@ConditionalOnProperty` consumers) and was left untouched.
- **`FILTER-012` now checks the contract it names (#783, P3)**: the test called "large body skips the cache" sent 15 bytes and asserted nothing about caching. It is renamed and now asserts that a small body is cached, re-wrapped, and read back byte-for-byte identically downstream (real oversize coverage lives in `CachedBodyWebFilterPayloadLimitTest`).
- **A comment pointing at a non-existent class (#784, P3)**: it now points at `AdapterConfigRequestBodyEndToEndTest`, which really does start Netty with the real controller and the central exception handler for the 400 path.

#### Behavior decisions (#780 / #782 / #785)

- **The reset backup message tells the truth (#780, P3)**: the backup is a single overwritten slot and there is no restore endpoint, yet the response said "backed up to key xxx". It now states that there is no one-click restore, that restoring requires manual work in the configuration store, and that each reset overwrites the previous backup.
- **Adapters with no service capability are rejected before being written (#782, P2)**: a definition whose `capabilities` were all false (or absent) used to persist and register, while no service type would ever route to it. It is now validated up front and answered with 400.
- **Security notification error bodies carry the same request id (#785, P3)**: the response header and the structured log used `resolveRequestId(exchange)` while the error body's `requestId` stayed null. The same value is now written into `requestId` / `request_id`, so the three can be correlated.

#### Upgrade notes

- **JWT account precedence changed**: `accounts` in `auth/jwt.yml` is now first-start seed data only. When a row with the same username already exists, editing the yml **does not** change that account's password, roles or enabled state — use the console instead.
- **Schema migration**: `V6__jwt_account_soft_delete.sql` adds a `deleted` column (default false) to `jwt_accounts`. Flyway runs it at startup.
- **Console deletion is a soft delete**: the row stays in the table with `deleted=true`; it disappears from the list and from login checks. Delete the row in the database if you need a hard delete.
- **Adapters with no service capability are rejected**: a script or template that submits all-false `capabilities` now gets 400; enable at least one of chat / embedding / rerank / tts / stt / imgGen / imgEdit (`streaming` alone does not count).
- **The 413 error type on `/v1/messages` changed** from `invalid_request_error` to `request_too_large`; clients that branch on `error.type` should accept the new value.

### [3.5.5] - 2026-10-29 - Bug fixes (eight-stage request-lifecycle audit: entry protocol / authentication / routing / outbound / response / accounting / console writes / failure semantics)

This release lands one audit that followed a single request from the moment it arrives to the moment the response leaves, split into eight stages. Every finding that affects whether a call succeeds or whether the response is correct has been fixed; the rest stay tracked as issues.

#### Entry protocol and error semantics (#681 / #682 / #683 / #685 / #686 / #717)

- **Oversized request bodies terminate with 413 (#681, P1)**: previously the body was consumed and dropped, and the client received a "successful" empty response. Now the gateway answers `413 PAYLOAD_TOO_LARGE`.
- **Missing chat-completions body returns 400 (#683, P1)**: an empty body used to NPE into a 500; it now returns 400 with "Request body is required".
- **Authentication failures carry `Content-Type` (#686, P2)**: the 401 body was JSON without the header, so some clients failed to parse it.
- **Downstream connection failures and timeouts return 502 / 504 (#682, P1)**: previously both became 500 with the upstream address and the raw "Connection refused" text echoed to the caller. They now return fixed messages without internal details.
- **Non-streaming paths read the downstream error body (#717, P1)**: upstream 4xx/5xx bodies were discarded. The gateway now parses `error.message` / `error.code` / `Retry-After`, keeps the upstream status code and surfaces the upstream semantics.
- **Anthropic error types follow the status code (#685, P2)**: 502 responses no longer claim `invalid_request_error`; 4xx maps to `invalid_request_error`, 5xx to `api_error`, and downstream bodies are translated.

#### Authentication and authorization (#693 / #694 / #695 / #696 / #697 / #698 / #704 / #705 / #706)

- **API Key IP allowlists actually work (#693, P1)**: the authentication chain never passed the client IP, so `ipAllowlist.contains(null)` was always false - **any key with an allowlist was rejected with 401**. The real client IP is now matched against CIDR ranges, and `unknown` fails closed.
- **Refresh checks the blacklist (#694, P1)**: `/refresh` skipped the blacklist, so a revoked-but-unexpired token could be exchanged for a new one; the same change stops copying the old `jti`, which previously made a freshly issued token already blacklisted.
- **Credentials no longer land in logs or spans (#695, P2)**: query strings are redacted everywhere except the WebSocket handshake path, which genuinely needs them.
- **Self-service revocation is bound to the token owner (#696, P2)**: the check compared the `userId` in the request body instead of the token's real owner.
- **Login failures are counted per user and IP (#697, P2)**: the login endpoint had no throttling and the only producer of `BRUTE_FORCE_ATTEMPT` audit events was a test endpoint. Failures now lock the account and emit real audit events.
- **Call-history ingestion verifies ownership (#698, P2)**: `/api/call-history/record` accepted any `apiKeyId`; writes are now checked against the calling principal.
- **Deleted or disabled accounts invalidate issued tokens (#704, P1)**: tokens stayed usable until expiry; both actions now blacklist the account's active tokens.
- **Resetting an API Key keeps its settings (#705, P1)**: reset used to delete and recreate with only six fields, dropping IP allowlists, quotas and rate limits. It now reads the old settings and replaces atomically.
- **Configuration reset requires explicit confirmation (#706, P1)**: the irreversible reset needed no confirmation and reported success even when it failed. It now requires confirmation, backs up first, and reports failures truthfully.

#### Routing and load balancing (#687 / #688 / #689 / #690 / #691 / #692)

- **Consistent-hash rings are immutable after construction (#687, P1)**: the ring was a class-level `TreeMap` mutated on every request, so concurrent selections could observe a half-updated structure. Rings are now cached immutable snapshots built with `computeIfAbsent` (bounded at 128).
- **In-flight call accounting lands on the same balancer (#688, P1)**: increments went to the pool/sticky balancer while decrements went to the service-type default, so least-connections counters only grew. Both ends now use the same instance.
- **Affinity context is cleared in one place (#689, P1)**: three early returns skipped cleanup, and `boundedElastic` thread reuse leaked the previous request's `apiKeyId` into the next one. Cleanup now happens in a single `try/finally`, and `set(null)` clears stale values.
- **Sticky wrappers are reused per delegate (#690, P2)**: a new wrapper per selection meant its ring snapshot never hit. Wrappers are now cached per delegate.
- **Virtual-node keys fall back to the instance identifier (#691, P2)**: with no `instanceId`, keys degenerated to `null#i` and instances overwrote each other. Keys now fall back through `instanceId -> baseUrl -> name`.
- **ip-hash rings are cached per candidate set (#692, P3)**: rings were rebuilt per request and each node created a fresh `MessageDigest`; both are now cached or reused per thread.

#### Outbound (#699 / #700 / #701 / #702 / #703)

- **WebClient cache keys use a stable variant name (#699, P1)**: keys embedded a `Consumer` identity hash, so the tracing path missed every time and the cache grew unbounded. Keys are now `URL + variant`, with a size cap.
- **Outbound response size limit is configurable (#700, P1)**: without `ExchangeStrategies` the default 256KB applied, so long responses, inline images and TTS audio failed with 500 (and the upstream call was still billed). The limit now defaults to 10MB, is configurable, and oversize bodies map to 502.
- **Streaming uses its own transport timeout (#701, P1)**: `responseTimeout` bounds the gap between network reads, so 60 seconds of SSE silence was killed by the transport while the five-minute application watchdog never fired. Streaming variants now allow 10 minutes by default.
- **The adapter test endpoint regains connect-time SSRF protection (#702, P2)**: it built its own `WebClient` behind a resolve-once check, so DNS rebinding could bypass it. It now goes through the shared pool and the connect-time resolver.
- **URL normalization keeps the query string (#703, P3)**: upstream base URLs carrying `?api-version=` were truncated to a bare path. The query is preserved; fragments are still dropped but now logged.

#### Accounting and quotas (#710 / #711 / #712 / #713 / #714 / #715 / #716 / #719)

- **Interrupted streams are accounted (#710, P1)**: a client disconnect recorded no usage and rolled the whole reservation back, so "read a few chunks and hang up" could repeatedly bypass the daily token quota. Consumption is now settled and the failed call recorded.
- **Today's request count is no longer double-incremented (#711, P2)**: one authentication incremented it twice, doubling daily quota and overview reads.
- **Non-streaming responses without `usage` are estimated (#712, P2)**: nothing was recorded, leaving quotas at the estimate. Usage is now estimated from the request and response text, matching the streaming heuristic.
- **TokenUsage records carry the API key id (#713, P1)**: the field was never set, so per-key token statistics were always empty.
- **Success-rate denominator uses the same window (#714, P2)**: an all-time total mixed with a range numerator diluted the rate.
- **Usage statistics are concurrency-safe (#715, P2)**: a plain `HashMap` with whole-store saves lost updates and amplified writes; the store now uses concurrent maps and persists only the updated key.
- **Stream outcomes are recorded at the stream terminal (#716, P1)**: a streaming response counted as a success as soon as it was constructed, so failures looked like 200s and circuit breakers never acted.
- **Quota settlements happen once, for the final outcome (#719, P2)**: the first failed attempt consumed the settlement CAS, so a later successful retry could no longer settle; settlement is now deferred until retries are exhausted, while cancellation still settles immediately.

#### Response side and cache (#684 / #720 / #721 / #722 / #723 / #724 / #725)

- **SSE sanitization decodes on UTF-8 character boundaries (#684, P2)**: multi-byte characters split across chunks turned into U+FFFD, garbling Chinese text and emoji.
- **`stream_options.include_usage` is supported (#720, P1)**: the parameter was silently dropped, so clients never received a usage frame. A `usage` frame is now emitted before `[DONE]` when requested.
- **Truncated streams are not cached (#721, P1)**: when the upstream ended abnormally, the partial response was cached for an hour. Only streams carrying a terminator (`[DONE]`, `finish_reason`, `message_stop`) are cached now.
- **Two output-affecting parameters join the cache key (#722, P2)**: `include_stop_str_in_output` and `ignore_eos` were missing, so requests differing in them collided.
- **Absent temperature no longer means deterministic (#723, P2)**: `temperature == null` means "use the upstream default", not determinism; such requests were cached and replayed for an hour.
- **Responses carry `X-Request-Id` (#724, P3)**: `extractRequestId` had no caller, leaving clients without a traceable request identifier.
- **Body-parse failures log a digest, not the body (#725, P3)**: user content and upstream details no longer reach the error log.

#### Retry and failure semantics (#718)

- **Client errors are not retried (#718, P1)**: the non-retryable list only recognized Spring's blocking exceptions and the gateway's own `DownstreamServiceException` was explicitly retryable, so an upstream 400/422 was replayed three times. The policy now also honours `Retry-After` when the exception carries it.

#### Configuration and operations (#674 / #676 / #677 / #678 / #679)

- **Credential environment variables use single names (#674, P2)**: `DEV_JWT_SECRET` / `STAGING_JWT_SECRET` / `PROD_JWT_SECRET` / `ADMIN_PASSWORD` were read by different consumers than the ones configuration binding uses - at worst the startup gate validated a strong key while binding used a weak one. Only `JWT_SECRET` and `INITIAL_ADMIN_PASSWORD` are honoured now; the old names only warn.
- **JWT persistence configuration has one definition (#676, P2)**: the same tree existed in two YAML files and the later import won, so editing `config/auth/jwt.yml` had no effect. It now lives only in `config/security/persistence-base.yml`.
- **JWT knobs exported by the image take effect (#677, P3)**: `JWT_ALGORITHM` and `JWT_EXPIRATION_MINUTES` had no binding; three never-read variables were removed.
- **Blacklist storage checks only probe Redis when Redis is used (#678, P3)**: default deployments warned on every start that the blacklist store was unreachable and revocations would not be blocked, while the blacklist actually used local storage.
- **New environment variable reference (#679)**: no page listed the complete set before; the same batch fixed two non-existent variables and a non-existent CLI flag in the configuration pages.

#### Console and configuration writes (#707 / #708 / #709)

- **Service-type writes refresh the local cache immediately (#707, P2)**: writes persisted and broadcast, but the local `ModelServiceRegistry` cache never invalidated, so that replica needed a restart (on multiple replicas some picked up the change and some did not). The cache is now rebuilt per service key, reloading load balancers and rate limits, and removed services are dropped.
- **YAML-defined rules and pools no longer report a delete that did not happen (#708, P2)**: `remove` returned `void` and used `removeIf` silently, so entries that could not be deleted kept routing while the API answered 200. `remove` now reports whether it deleted anything, YAML-defined read-only entries and missing persisted entries return 409 with a reason, and successful deletes still return 200 with `yamlRuleActive` / `yamlPoolActive` flags warning that the YAML entry reappears once the same-id override is removed.
- **Adapter creation validates before persisting (#709, P2)**: an explicit `null` inside `capabilities` NPE'd during construction, and `type=extend` without a parent was wrapped into 500 - after the definition had already been persisted, so retries always hit 409. Value- and field-level validation now run before construction, return 400 naming the field, and the order is validate -> construct -> persist -> publish. **Behavior tightening**: omitting `auth` / `capabilities` now returns 400 instead of 201 (the console wizard always sends them; direct API clients will notice).

#### Upgrade notes

- **Gateway endpoints still use the `X-Jairouter-Token` header** (`Authorization` is reserved for downstream services) - unchanged from 3.5.4.
- **`jwt.persistence.enabled` semantics are now documented**: it controls the lifecycle service, expiration scheduler, health probe and two configuration properties, and does **not** control whether tokens are persisted.
- **Configuration reset now requires explicit confirmation**; scripted callers must pass it or receive a parameter error.
- **Streaming transport timeout and the idle watchdog remain two settings**: `jairouter.webclient.streaming-response-timeout-seconds` (default 600) and `jairouter.streaming.idle-timeout` (default 5 minutes); whichever comes first applies.


### [3.5.4] - 2026-10-27 - Bug-fix release (routing and circuit accounting / health checks / default credentials and startup profile)

#### Routing and circuit-breaker accounting (#647 / #648 / #649 / #653 / #654 / #655)

- **4xx responses no longer count as instance failures (#647, P1)**: upstream 400/401/404/429 used to be recorded exactly like 5xx, so a client sending malformed requests could trip a healthy instance's circuit breaker. Call outcomes are now three-way (`SUCCESS` / `CLIENT_ERROR` / `INSTANCE_FAILURE`): 4xx (including upstream 429) only settles the in-flight counter, while 5xx, timeouts and connection failures still count. `ModelServiceRegistry.recordCallComplete` now honors its `success` argument (it previously recorded success unconditionally).
- **Instance-level rate limiting no longer gives up after three candidates (#654, P1)**: the attempt cap in `selectWithRateLimit` moved from `min(size, 3)` to the whole candidate list, so a few rate-limited instances no longer produce a bogus 503 "All instances rate limited" while idle instances remain.
- **The random load balancer no longer carries its own circuit breakers (#648, P2)**: its private `Map<String, CircuitBreaker>` and hard-coded 5-failure / 60 s / 2-half-open thresholds are gone; availability is decided by `CircuitBreakerManager` upstream ⇒ per-instance `circuit-breaker.enabled=false` is now honored. Empty candidate lists raise `IllegalArgumentException`, and `SecureRandom.getInstanceStrong()` is no longer called per request.
- **Load balancers are cached per strategy name (#649, P2)**: under pool routing, round-robin used to build a fresh balancer per request ⇒ it always picked the first instance. Balancers are now cached with `computeIfAbsent` so counters accumulate across requests.
- **Adaptive thresholds are actually applied (#653, P2)**: `AdaptiveThresholdManager.updateCircuitBreakerThreshold` previously only logged the new threshold, and every adaptive cycle reset the instance's breaker state. Thresholds now land in `CircuitBreakerManager.updateFailureThreshold(...)` (per-instance override table), and an unchanged threshold leaves existing state alone.
- **Failed requests release their in-flight count (#655, P2)**: least-connections `recordCallFailure` only ever incremented ⇒ the counter drifted upwards and pushed the instance to the back of the queue forever; failures now release too and clamp at 0. `LoadBalancerManager` uses `ConcurrentHashMap` + `computeIfAbsent`, the round-robin index uses `Math.floorMod`, and `isInstanceHealthy` reuses `InstanceKeys.healthKey`.

#### Health checks (#650 / #651 / #652)

- **One instance no longer drags the others down (#650)**: an invalid or missing `base-url` is reported as unhealthy with a reason, and a single instance's exception no longer aborts the batch for its siblings.
- **Probe settings are configurable (#651)**: new `jairouter.health-check.*` (probe mode / interval / timeout / concurrency); the batch runs off the default single-threaded scheduler, a still-running round is skipped, and the interval is measured from the previous round's end. The never-bound `model.load-balance.health-check.*` keys in the docs are corrected.
- **State persistence matches replicas (#652)**: without `instance-id`, health state is matched by `base-url`, and unchanged state is not written back.

#### Default credentials and the startup profile (#663 / #664 / #666)

- **The built-in test API key left the base configuration (#663, P1)**: `config/auth/api-key.yml` no longer ships `sk-test-api-key-12345678`; it now lives in `application-dev.yml`. The key used to work under `prod` too (`/v1/**` only requires "authenticated"), i.e. everyone had a working call credential.
- **The JWT secret and admin password no longer have public fallbacks (#664, P1)**: the base configuration falls back to `${random.uuid}` instead of a known literal, printing the generated value once in the startup log; `prod` still refuses to boot. The admin-password warning text is corrected as well (it claimed a random password would be generated while none was).
- **No more implicit dev startup (#666, P1)**: `spring.profiles.active: dev` is gone from `config/common/server.yml` — it shipped with the artifact, so every startup that did not name a profile ran as dev with the public test credentials live. Not naming a profile now means `default`.

#### Logging (#669)

- **Logging is no longer lost on other profiles**: every `<root>` in `logback-spring.xml` lived inside a `<springProfile>` block, and only dev/default, test, prod and json-logs matched — the real `staging` profile fell outside, so every log destination was "not referenced" and neither application logs nor the startup failure stack appeared (measured: five logback warnings, then exit code 1). A fallback root keyed on `name="!(dev | default | test | prod | json-logs)"` (CONSOLE + ASYNC_FILE + ASYNC_ERROR) now covers every other profile.

#### Dependencies (#612 / #613)

- `frontend`: happy-dom 20.14.5 → 21.0.4 (GitHub advisory GHSA-834v-8h66-3h79) and vitest / @vitest/mocker → 5.0.5 (two advisories on the same package, both patched at 5.0.3); both Dependabot alerts are cleared.

#### Upgrade notes

- **Running without a profile no longer means dev**: `java -jar …` or `./mvnw spring-boot:run` starts with `default` — no built-in test key, no fixed password (a random one is printed in the startup log). Ask for dev explicitly with `--spring.profiles.active=dev` (or `-Dspring-boot.run.profiles=dev`); the docs follow suit.
- **The built-in test API key only exists under dev**: `sk-test-api-key-12345678` is no longer part of the shipped package. Scripts that rely on it must enable the dev profile or bring their own key.
- **Outside `prod`, a missing `JWT_SECRET` yields a fresh random secret per start** ⇒ tokens issued before a restart stop working. Set the variable explicitly for shared or multi-replica deployments.
- **`staging` requires `JWT_SECRET`** (legacy alias `STAGING_JWT_SECRET`): without it startup fails during binding (`@Size(min = 32)`). The docs gained a "required environment variables per profile" table.
- **Health-check configuration moved**: read `jairouter.health-check.*`; the old `model.load-balance.health-check.*` keys were never bound.
- **Only previously mis-accounted or mis-routed cases change**: #647 (4xx no longer counts), #654 (keep trying after rate limiting), #655 (failures release in-flight counts), #649 (pool round-robin actually rotates), #653 (adaptive thresholds take effect — they used to appear only in logs). No API contract changes.

### [3.5.3] - 2026-10-25 - Bug-fix release (main-flow audit: config precedence / image edits / health keys / outbound path / adapter checks)

#### Main-flow audit and five fixes (#634 / #635 / #636 / #637 / #638)

- **External mounted configuration now actually overrides (#634, P1)**: `spring.config.import` resolves later entries with higher precedence, but `application.yml` listed the three `/app/config` imports **first** ⇒ same-named keys in mounted files were silently overridden by classpath defaults, contradicting both the docs and the k8s ConfigMap comment. The three external imports now sit at the end of the list, and the new `ExternalConfigReporter` logs `外部配置已生效（按后导入者优先覆盖 classpath 同名键）: …` on startup (WARN when the mount directory exists but no known file was read).
- **Image edits now use multipart (#637, P2)**: `/api/v1/images/edits` declared `@RequestBody ImageEditDTO.Request`, but that DTO carries `List<FilePart>` — impossible to deserialize from JSON — while the console sent multipart ⇒ the endpoint worked for neither side. It now mirrors STT (`image` / `image[]` / `mask` + text parts), and outbound parts are read into in-memory resources before assembly (the old `MultipartBodyBuilder` fed `FilePart` straight in, which would also fail outbound).
- **Health keys no longer collapse (#636, P2)**: instances without `instance-id` shared the key `serviceType:null` in `ServiceStateManager` and `ServerChecker`, so one unhealthy instance dragged the others down. New `InstanceKeys` resolves `instance-id` → `baseUrl` → `name` → `unknown`.
- **Outbound path comes from the selected instance (#635, P2)**: `BaseAdapter` used to look the path up by model name; pool names, `auto-model` and aliases are not instance names ⇒ empty path, request sent to the upstream root; with same-named replicas it could also use another instance's path. It now prefers the selected instance's own `path` and only falls back to the lookup when absent.
- **Adapter names validated at startup (#638, P3)**: an unregistered adapter name used to surface only on the first request. New `AdapterReferenceValidator` (`ApplicationReadyEvent`) scans global / service-level / instance-level references, logs `ERROR` without blocking by default, and blocks only when `jairouter.config.validation.strict-mode=true`; request-time errors now include `registered=[…]`.

#### Single-source service definitions (#643)

- `model.services` was defined in both `config/router/services.yml` and `config/base/model-services-base.yml`; because base was imported later it silently won — the router copy was dead configuration. It is now single-sourced in `config/router/services.yml` (carrying the **currently effective** instance definitions), while base keeps only the global policies (`load-balance` / `adapter` / `rate-limit` / `circuit-breaker` / `fallback`).
- New `ServiceDefinitionSingleSourceTest` guards both directions: exactly one definition of `model.services` (with an anti-vacuous scan assertion), and unchanged default instance values after the move.

#### Upgrade notes

- **#634 changes behavior**: same-named keys that were written under `/app/config` but never took effect will now apply. Review your mounted files before upgrading; the startup log line above confirms the mount was read. Keys absent from the mount still come from classpath (no tree replacement).
- **#637 is a contract change**: `/api/v1/images/edits` accepts `multipart/form-data` only (e.g. `-F "image[]=@body-lotion.png" -F "mask=@mask.png" -F "prompt=…"`); JSON callers now get 415.
- **#636 resets health state once**: keys for instances with `instance-id` are unchanged; ones without move from `chat:null` to `chat:<baseUrl>`.
- **#635 only affects previously mis-routed requests** (pool routing / `auto-model` / same-named replicas); **#638 logs by default and does not block startup**; **#643 keeps effective values unchanged** (default instance remains `qwen3:4b` / `gpustack`).

### [3.5.2] - 2026-10-24 - Feature Release (end-user rate limiting + client-dimension wiring)

#### End-user rate limiting (#627 / #628 / #629)

- **New capability**: a per-minute allowance counted per `(caller API key × end user)`. This is the only way to limit several people who share one API key without issuing one key per person — a shared key could previously only be limited "per IP" or not at all.
- **Identity order**: header `X-End-User-Id` → request body `user` (`ChatDTO.Request` / `EmbeddingDTO.Request` / `ImageGenerateDTO.Request` / `ImageEditDTO.Request`) → Anthropic `metadata.user_id`. Only `[A-Za-z0-9._:@/-]` with ≤64 characters is accepted; anything else becomes the first 16 hex chars of SHA-256. **The raw identifier never enters Redis keys, logs or error responses** (logs carry an 8-char fingerprint only).
- **Counting**: fixed minute windows aligned to the clock. Key = first 16 hex chars of `sha256(apiKeyId|userId)`. By default a bounded in-process Caffeine counter (`local-cache-size`, default 50000); with `jairouter.ratelimit.distributed.enabled` it becomes a single-script atomic Redis counter (`INCR` → set TTL on first write → `DECR` rollback on overflow), failing open and reporting `degraded` when Redis is unavailable.
- **Over-limit response**: 429 with `Retry-After` / `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset`; the body goes through the central exception handler and `V1ErrorBodyMapper` (`rate_limit_error` under `/v1/**`), with no new error code and no user identifier in the message.
- **Configuration**: `jairouter.ratelimit.end-user.*` — `enabled` (default `false`), `requests-per-minute` (default 60), `header-enabled`, `missing-identity` (`skip` / `reject`), `local-cache-size`, `fingerprint-length`.
- **Not a security boundary**: the identity is self-reported and can be forged. Preventing impersonation needs authentication at the gateway; this layer only provides fair sharing within one key.

#### Client-dimension wiring and retry hints (#623 / #624 / #625)

- **Wiring (#623)**: `RateLimitManager.tryAcquireClientIp` was "fully implemented but never called from the request path", which is why the switches and docs read as if they worked. It is now invoked by the main path (after authentication, before the service-level pre-deduct and the cache read), rejecting with 429.
- **Default flipped (16 yml entries)**: every bundled `client-ip-enable` became `false` — behind a cluster without an L7 proxy, `remoteAddress` is the node IP and counting by IP merges different clients into one bucket. **The switch did nothing before, so this default change alters no actual admission behavior**; enabling IP/key-based limiting is now an explicit operator decision.
- **Retry hints (#624)**: service-level 429 and all-instances-limited 503 now carry `Retry-After: 1` (the sliding-window pane is 1 second, which is a safe upper bound for token buckets too). `X-RateLimit-*` is still not emitted — that layer's limiter does not expose remaining capacity, and a hard-coded value would mislead clients.
- **Bounded window table (#625)**: `auth/security/service/TokenBucketRateLimiter` moved from an unbounded `ConcurrentHashMap` to a bounded Caffeine cache (10,000 entries, 2× window expiry). The cost is that an evicted key restarts its window and may allow one extra burst; strict counting belongs to `jairouter.quota.*` (documented in the javadoc).

#### Documentation and gates

- Rewritten symmetrically (zh/en): `configuration/rate-limiting.md` (a new "End-User Rate Limiting" section and a rewritten implementation status for client IP limiting), `deployment/multi-replica.md` (the two Redis counting paths and the switch semantics), `deployment/kubernetes.md` (the rate-limit row) and `reference/roadmap.md` (the v3.5.2 block plus a "wiring landed" correction).
- `scripts/docs/check-capability-claims.py`: rate-limit display names and `EXTRA_ANCHORS` updated so the roadmap ↔ code cross-check keeps holding.
- Two new guard tests: `DimensionRateLimitDefaultGuardTest` (no `client-ip-enable: true` may reappear in bundled yml, with an anti-vacuity assertion) and `ServiceRequestHandlerDimensionRateLimitTest` (429 and retry header on the wiring path).

#### Upgrade notes

- **No breaking changes**: end-user rate limiting is off by default, and the `client-ip-enable` default change does not alter admission behavior (the switch was inert before).
- If you **assumed** `client-ip-enable: true` was in effect, keep the value explicitly after upgrading to get IP/key-based limiting — and make sure the topology actually lets real client IPs reach the pod; otherwise use `key-dimension: api-key` or end-user rate limiting.
- To limit per end user: set `jairouter.ratelimit.end-user.enabled=true` and have clients send a stable identifier in `X-End-User-Id` (or the OpenAI-compatible `user` field).

### [3.5.1] - 2026-10-24 - Patch Release (record-governance page + live config snapshot + tech-debt batches A–D)

#### Record governance and call-history key source (#617 / #618)

- **Fixed a setting that could be configured, echoed back, and still never apply**: `jairouter.call-history.encryption-key-source` did nothing when set to `env` — `RecordContentCipher` is a `@Component` but only exposed a no-arg constructor, which is exactly what Spring used (it hard-coded `auto` and read only the local key file). It now takes `@Autowired RecordContentCipher(CallHistoryProperties)` and resolves the key source from configuration; when the environment variable is unavailable it **falls back to `auto` and records that faithfully** (requested source / effective source / whether the environment key is present) instead of staying silent.
- New read-only observations: `keySourceRequested()`, `keySourceEffective()`, `environmentKeyPresent()`, `keyFingerprint()` (first 8 hex chars of SHA-256, never the key itself) and `algorithm()`.
- `GET /api/config/call-history` gains `encryptionKeySourceRequested`, `encryptionKeySourceEffective`, `encryptionKeySourceHonored`, `environmentKeyPresent`, `encryptionAlgorithm`, `encryptionKeyFingerprint`, `contentStoredEncrypted` and `maxRecords` (all read-only).
- New console page "Record Governance" at `/security/record-governance` (6th item of the Security group): the three record levels (`FULL` requires a second confirmation), a read-only encryption & storage card (configured source vs effective source, fallback warning, key fingerprint, content limit, retention, max records) and an entry point to the decryption audit. The menu grows 28 → 29 items (29 tabs + 12 = 41 in total); `menu.spec.ts`, `usePermission.spec.ts` and the E2E route lists are updated.

#### Config snapshot and API path wording (#605 / #619, #606 / #620)

- `gatewayResponseFilter` in `GET /api/config/sanitization` no longer returns a hard-coded legacy string; it is composed live as `enabled=…;scopeMode=…;sanitizeSse=…;failOnError=…` (#605) — previously the description disagreed with the actual behavior once the configuration had been changed.
- Docs now use the real prefix `/api/v1/*` for the gateway entry surface and state that `/v1/*` exposes only the 4 OpenAI-native endpoints (#606). The "default paths" table in `configuration/index.md` and `instances[].path` in `first-steps.md` describe **upstream service paths** and were intentionally left unchanged.

#### Tech-debt batches A–D (#604 / #611 / #614 / #615 / #616)

- **A (#611)**: added an end-to-end case that drives the real HTTP stack (controller → `ReactiveGlobalExceptionHandler` → 400) for a missing request body, with a 500 control case, plus the bare-catch baseline `src/test/resources/techdebt/bare-catch-baseline.txt` (835 occurrences / 257 files) and `BareCatchCeilingTest`, which turns red as soon as a new bare catch appears.
- **B (#614)**: `QuotaLedgerService` 1268 → 1094 lines, with `QuotaUsageMapper`, `QuotaLedgerRepositoryGateway`, `QuotaDegradationState` and `QuotaDistributedWriter` extracted; behavior unchanged.
- **C (#615)**: `ServiceRequestHandler` 1027 → 785 lines, with the cache read path extracted into `router/handler/ResponseCacheResolver` (220 lines).
- **D (#616)**: only the two hottest files — bare catches in `AsyncMetricsCollector` 29 → 0 and `MetricsManagementController` 20 → 0 (the metrics/monitoring boundary now catches checked exceptions only), baseline 835 → **786**; the **remaining 786 stay parked** for later batches.

#### Docs gates and assets (#607 / #621, #609 / #610)

- `scripts/docs/check-fence-parity.py` is wired into `docs-quality.yml` (4 self-test cases + full scan); the same change fixes a `ValueError` from `os.path.relpath` when paths cross drives (#607). The script existed before but had never run in CI.
- Re-shot the 10 console screenshots (v3.5.0 light theme, one zh-CN and one en-US set) (#609 / #610).

#### Upgrade notes

- **No breaking changes** relative to `3.5.0`: no endpoints removed, no config keys removed, no schema migration; every new `GET /api/config/call-history` field is read-only.
- `encryption-key-source: env` now **actually takes effect** (before, setting it was a no-op): before switching from `auto` to `env`, make sure `JAIR_CALL_HISTORY_KEY` is set and holds the key your stored records were encrypted with, otherwise existing records cannot be decrypted. When a fallback happens, both the API and the page show "requested ≠ effective".

### [3.5.0] - 2026-10-24 - Feature Release (response sanitization coverage + circuit-breaker governance + request-side config surface removed)

> **Version-semantics note (recorded 2026-10-24, together with 3.5.1)**: this release contains breaking changes (the request-side sanitization config surface was removed and Flyway `V5` deletes historical data), which under strict semver would be `4.0.0`. It shipped as `3.5.0` to avoid interrupting the 3.x line; the **tag and Release are left as-is and will not be rewritten**.

#### Response sanitization coverage (#560 series)

- **Non-streaming JSON** responses under `/v1/**` are now sanitized (#593): the `/v1/` and `/v1/**` entries are removed from `ExcludedPathsConfig`, while `/api/**` (including `/api/v1/**`), `/actuator/**`, `/health`, `/metrics`, Swagger and static resources stay excluded. Only the exclusion table changed; no filter branch was touched, so streaming behavior was unchanged at that point.
- The effective scope is configurable (#595, tier A): new `jairouter.security.sanitization.response.scope.{mode,patterns}`. With `mode=exclude` (the default) `patterns` lists the paths that are **not** sanitized and an empty list falls back to the built-in set (behavior identical to before); with `mode=include` only matching paths are sanitized and an empty list means **nothing** is sanitized (server logs a WARN and the console shows a red banner). Matching: entries without wildcards match by prefix (`/actuator` covers `/actuator/health`), entries with `*`/`**` use Ant matching (`/v1/**` also matches `/v1` itself). The scope applies **only to the HTTP response filter**; record-side sanitization is unaffected.
- Console and API: `GET /api/config/sanitization` exposes `scope` (with `usingBuiltInDefaults` and `warning`) under `response` plus a top-level `scopeSource`; new `POST /api/config/sanitization/scope/preview` previews path decisions; the PII masking page gains a read-only card. Scope is read-only for now — changing it requires a restart.
- Response buffer limit (#594 / #597): new `jairouter.security.sanitization.response.max-buffered-bytes`, default **1 MiB**, minimum 1 KiB (a smaller value fails startup). Over-limit behavior is twofold: with a `Content-Length` the response is passed through **without sanitization** (WARN plus an audit event); for chunked responses the bytes have already been consumed and cannot be replayed, so the request is terminated as a sanitization failure (the central error handler renders the body) and **no truncated body is emitted**. The same patch removes a double `DataBuffer` release in `finally` (Netty pooled buffers could raise `IllegalReferenceCountException`) and a double write of an already-consumed body via `switchIfEmpty`.
- SSE per-frame sanitization (#560 phase two / #598): new `SseResponseSanitizer` splits the SSE stream per line and sanitizes only `data:` payloads via `sanitizeResponse(payload, "application/json")`; `event:` / `id:` / comments / blank lines / `[DONE]` pass through untouched, partial lines are carried across chunks, an over-limit line passes through and is warned about once, and a single frame failure only rolls back that frame instead of tearing down the stream. `ResponseSanitizationFilter` now overrides `writeAndFlushWith`; the previous fallback to `application/octet-stream` when the response header carried no type (which silently let SSE through) is replaced by `null` plus an `Accept: text/event-stream` fallback. New setting `jairouter.security.sanitization.response.sanitize-sse`, default `true`.
- Default PII rules fixed (#561 / #591): the shipped `pii-patterns` become five bounded patterns — 18-digit ID cards `(?<!\d)\d{17}[\dXx](?!\d)`, 16/19-digit cards without separators, 4-group card numbers, mobile numbers `(?<!\d)1[3-9]\d{9}(?!\d)`, and email. **List order is execution order**: longer numbers must precede the mobile rule (otherwise the first 11 digits are consumed and the tail leaks), and a test pins that order. The sensitive-word list drops `key` (it masked ordinary text such as `press any key`). JSON keys `traceId` / `spanId` / `requestId` / `traceparent` / `tracestate` keep their values verbatim by default (key match is case-insensitive); every other key is still masked.
- A "tests green, production unchanged" gap was closed in the same batch (#591): the default-wired `OptimizedSanitizationRuleEngine` (`@Primary`) had no JSON key-exemption branch while the corpus test instantiated `DefaultSanitizationRuleEngine` ⇒ `traceId` was still masked in production. `JsonKeyExemption` is now shared by both engines, `SanitizationRuleEngineWiringTest` asserts that Optimized is the default wiring, and the 28-case corpus is replayed on the Optimized engine.
- Performance warning (#591): enabling sanitization in the console now asks for confirmation (disabling does not), the cards keep a persistent notice while it is on, and `security/data-sanitization.md` gains a performance and capacity section (the response side only covers `application/json`; cost grows with body size, rule count and QPS; `SafeRegexValidator` rejects dangerous patterns but does not guarantee speed).

#### Breaking: request-side sanitization configuration removed (#601 / #602)

- The request-side switch and whitelist were configurable but the inbound path never called them (`sanitizeRequest` had zero callers, see #592 / #600). This release removes the request-side configuration surface entirely and keeps only the response side and the record side (`sanitizeForStorage`) that actually run: `SanitizationConfig` loses the `request` field and its nested class, `SanitizationService` loses `sanitizeRequest` / `isUserWhitelisted`, and `SanitizationConfigStore` / `SanitizationConfigEntity` become response-only.
- `GET /api/config/sanitization` no longer returns `request`; `PUT` accepts `response` only, and a payload carrying just `request` is rejected with 400 (the error message asks for the `response` sub-configuration). Saving preserves `scope` / `maxBufferedBytes` / `sanitizeSse`, so one save cannot reset the #560 and #594 configuration to defaults.
- New Flyway migration `V5__remove_request_sanitization_scope.sql` (H2 and PostgreSQL): deletes historical `request` configuration rows and the `whitelist_users` column that only served the request-side whitelist.
- The console drops the request-side card and the `requestEnabled` indicator, renders the response side full width, and submits `response` only.

#### Circuit-breaker governance (#551 / #554 / #555 / #599)

- `POST /api/config/instance/circuit-breaker/clear-all` now performs a full reset: new `CircuitBreakerAdminService.resetAll(reason)` resets every instance in the registry (in-memory state to CLOSED, persisted state deleted, a `MANUAL_RESET` history entry written, adaptive thresholds cleared) and returns `matched=0` for an empty registry. The endpoint is `@Deprecated` in favor of `POST /api/v1/circuit-breaker-monitor/instances/reset`, which accepts `serviceType` / `states` / `instanceIds`.
- The scope of force-open / force-close is now explicit (#555): these endpoints operate on the **metrics-pipeline** singleton breaker, which is not the instance-routing breaker. Canonical paths `GET/POST /api/monitoring/metrics-pipeline/circuit-breaker/{stats,force-open,force-close}` are added (the `/actuator` side mirrors them), the old paths remain as `@Deprecated` aliases, and the response text states that only metrics processing is affected.
- `stateSyncIntervalMinutes` is removed (#554): it could be set and echoed back, but no scheduled task ever read it. The DTO, service, repository default and the console "sync" group are gone; the entity field stays and is marked `@Deprecated` (`h2/V1__baseline.sql` is published and immutable — dropping the column would trip the baseline drift check).

#### Console fixes (#584 / #585 / #586 / #589 / #590)

- The exception management and exception statistics pages drop their decorative gradients (`MetricStrip` / `ChartCard` / `useChartSkin`); the JWT token filters are inline; the load-balancer monitoring header moves pause/resume and policy configuration into a "more" dropdown (the clear-history button stays a real button because the `05-traffic-governance` E2E spec depends on it).
- The service-type tabs of the overview "service configuration" panel are plain text again (they had been treated as a filter, which broke tab switching), with `instanceTotal` / `viewInstances` copy and a tab-switching case in `03-overview`.
- Acceptance-batch UI fixes: filter spacing, list-page card surface, light-theme contrast, menu grouping, and permission semantics (`api/permission.ts` plus a new unit test).
- Cleanup (#590): the two environment variables with no consumer and several non-existent persistence / blacklist keys in `jwt-authentication.md` are replaced with the real keys; Chinese capability labels are unified; the embedded Dockerfile example in `china-optimization.md` loses its stale version label and gains a note that prod fails fast without secrets and that `JWT_SECRET` / `INITIAL_ADMIN_PASSWORD` must be injected at `docker run`.

#### Security and CI

- `.github/workflows/frontend-tests.yml` is restricted to `permissions: contents: read` (CodeQL `actions/missing-workflow-permissions` alerts #82 / #84).
- Dependency updates: `vite` 8.0.16 → 8.3.5; `follow-redirects` 1.16.0 → 1.16.1.

#### Upgrade notes

- **Breaking API change**: `GET/PUT /api/config/sanitization` handles `response` only (see above).
- **Database change**: Flyway `V5__remove_request_sanitization_scope.sql` **deletes** historical `request` configuration rows and the `whitelist_users` column, and cannot be rolled back automatically. Deployments that once saved request-side rules used them for record-side masking through `sanitizeForStorage`; those rules stop applying after the upgrade — migrate anything you want to keep into the `response` configuration (response rules are used for record-side masking as well).
- **Behavior change**: SSE streaming responses are sanitized frame by frame by default (`sanitize-sse: true`), which adds CPU cost on long streams; set it to `false` to pass them through untouched.
- **Already on in production**: `sanitization.response.enabled` is `true` in `application-prod.yml` / `application-staging.yml`, so after the upgrade non-streaming JSON under `/v1/**` and SSE frames **start being masked immediately** (visible to clients). Deployments that must return content verbatim should add the paths to `response.scope.patterns` (`mode=exclude`) or turn the switch off.
- **Behavior change**: the default PII rules and JSON key exemptions changed — identifiers such as `traceId` that used to be masked are now preserved, while ID-card tails and long card numbers that used to leak are now masked in full.
- **Removed setting**: `stateSyncIntervalMinutes` can no longer be set (it never had any effect).

### [3.4.0] - 2026-10-21 - Feature Release (console overhaul + permission semantics + persisted sanitization config + hourly/monthly quota limits)

#### Permission semantics: eight read-only codes (TA4 / #567)

- Eight read-only permission codes are added (`lb:config:read`, `tracing:config:read`, `security:apikeys:read`, `security:sanitization:read`, `security:jwttokens:read`, `security:blacklist:read`, `system:accounts:read`, `system:permissions:read`), taking the total from **55 to 63**.
- The URL rule table grows from 68 to 75 entries: the seven rules that used `any()` to bind GET requests to a write code are split into "GET → read code / write → manage code", and a narrow `GET /api/loadbalancer/config/** → lb:config:read` rule is inserted ahead of the `/api/loadbalancer/**` wildcard.
- Read-only roles no longer need a write code to list data: the incremental convergence in `RolePermissionSeeder` now enforces one invariant — **if a role template holds `:write`/`:manage` for a resource it must also hold the matching `:read`** (no built-in role loses visibility after the upgrade: admin 63 codes, operator 46, user 29, viewer 27).
- Nine console menu entries that only carried write codes now show up when *either* the read or the write code is held; the router guard changed from "all required" to "any required" and seven route declarations were widened to `[read, manage]`.

#### Persisted sanitization configuration (TA2)

- Rules, sensitive words and PII patterns edited in the console are now **persisted** (new `sanitization_config` table keyed by `config_scope` (request / response), Flyway `V4` for both dialects) and loaded from the database on startup, falling back to yml when the table is empty. Previously these edits lived only in memory, so they vanished on restart and rule IDs were reused.
- Saving is now "persist first, rebuild rules second": if persisting fails the in-memory configuration is rolled back to its pre-update snapshot and the request returns 500, so the running state can no longer diverge from the persisted state.
- `updated_at` / `updated_by` record the last writer (an absent authentication context is stored as `system`).

#### Hourly and monthly quota limits (TA3)

- API keys gain four limits: `hourlyRequestLimit`, `hourlyTokenLimit`, `monthlyRequestLimit`, `monthlyTokenLimit` (`0` means unlimited). The `QuotaWindow` enum already supported MINUTE/HOUR/DAY/MONTH; this release completes the limit model, enforcement, ledger writes and detail responses.
- Enforcement order is HOUR → DAY → MONTH → MINUTE; the first exhausted window rejects the request with a dimension-specific violation. Resetting a key's quota still clears every window.
- The console's API key "Quota" drawer and create/edit form expose the four new inputs (hourly requests/tokens, monthly requests/tokens); window percentages stay empty when the ledger has no data instead of showing 0%.

#### Console overhaul (P0-1 to P3, 33 pages + sign-in page + navigation)

#### Console navigation information architecture (P0-3)

- The sidebar menu shrinks from **9 groups / 40 entries** to **7 groups / 28 items** (Overview / Observability / Traffic Governance / Model & Instances / Security / System / Developer Tools). Twelve pages became tabs of a sibling item; **every URL is unchanged**, so deep links, bookmarks, and older paths in the docs keep working. No backend or API change and no data migration.
- The 12 folded entries: Call History Dashboard, Tracing Dashboard, Exception Statistics, Slow Query Analysis, Quota Usage Monitoring, Load Balancer Strategy, Circuit Breaker History, Circuit Breaker Global Config, and the playground's Embedding / Rerank / Audio / Image pages.
- `MenuItem.permission` in `menu.ts` now accepts `string | string[]` (**an array means OR**); every tab carries its own permission and is gated separately, and clicking a menu item lands on the first tab the user may open.
- Sidebar highlighting is driven by a `ROUTE_TO_MENU` lookup derived from the tabs (never hand-written), with unmapped routes falling back to their own path; group expansion is derived from the current route, and the hard-coded prefix table is gone.
- The breadcrumb is now "group to menu item to current tab", with the last segment not clickable; detail pages (such as `/exceptions/detail/:id`) append `#id` to the last segment.
- Documentation correction: exception management is in fact gated by `monitoring:exceptions:read` (it used to be documented as having no permission code).

#### Brand assets and first paint (P0-1)

- New brand asset folder `frontend/src/assets/brand/` (`logo-mark-64.png`, `logo-badge-256.png`, `favicon-32.png`, `favicon-180.png`); `frontend/index.html` now declares `<link rel="icon">` and `<link rel="apple-touch-icon">`; the sidebar badge replaces the placeholder icon (the image keeps an empty `alt`, since the adjacent `JAiRouter` text carries the name).
- New neutral-scale and accent tokens (`--ja-paper`, `--ja-surface`, `--ja-surface-2`, `--ja-ink`, `--ja-ink-2`, `--ja-ink-3`, `--ja-accent`, `--ja-accent-soft`). **The primary color `#409eff` is unchanged**, and `--ja-border` is unchanged too (the dark border correction is deferred).
- The boot animation drops the purple gradient for a neutral background, the badge, and a cyan progress ring, with a dark-mode branch.

#### Sign-in page (P0-2)

- The sign-in page is now a two-column layout: a brand wall on the left (badge, one-line summary, three facts, footer version) and the form on the right, over a Canvas topology animation.
- Two escape hatches: `?static` in the URL, or the system `prefers-reduced-motion: reduce` setting, renders a single static frame with no animation or parallax.
- The form contract is unchanged: still a `.login-form` container, two inputs (username then password), and a `button.login-button` with `type=submit`; a failed sign-in shows an inline message and no longer raises an extra toast.

#### Observability list pages (P1-1)

- Call History, Slow Requests, Token Usage, Tracing Search, and Exceptions now share the list-page skeleton (title and description, actions, filters, toolbar, table, pagination, drawer).
- The relative-time component `TimeText` follows the interface language (new `common.justNow`); the exception list no longer renders an empty pagination bar when there are zero rows.

#### Heavy pages and chart skin (P1-2)

- Three shared pieces: the metric strip `MetricStrip`, the chart card `ChartCard` (built-in loading and empty states, with the default slot always mounted so an ECharts instance is never lost), and the chart skin `useChartSkin` (palette, axes, grid, and tooltip all read design tokens and follow dark mode).
- API Key page: the four metric cards become a metric strip; the quota-alert card collapses into a one-line summary when nothing is alerting; a permission column with more than three entries folds into "N items" with a popover; the row actions keep "Quota" and move the rest into "More".
- Audit Log page: the four gradient cards become a metric strip; the event-type donut becomes a horizontal Top 5; localization is added for the 12 audit event types that were missing.

#### Collapsible sidebar, dark semantic colors, narrow screens (P1-3)

- The sidebar collapses between 200px and 64px, remembered in `localStorage['ja-sidebar-collapsed']`; below 768px it is forced collapsed and the user preference is not overwritten.
- The dark theme now has proper elevation (page darker than cards) and two chart-only colors (`--ja-chart-series-neutral`, `--ja-chart-axis`), fixing the case where a divider color had been reused as a data-series color and became invisible in dark mode.
- Three list pages switch to card lists below 768px (`TableCards`); between 768 and 1024px secondary columns are hidden so the table does not scroll sideways.

#### Neutral palette across the console (P2-1, three batches, 33 pages)

- KPI cards become metric strips, charts become chart cards with the shared skin, and decorative gradients and multi-color tags collapse into neutrals; semantic colors are reserved for real semantics (failure, over-quota, danger, in progress).
- The API Key page is split from a single 1696-line file into nine sub-components (table, cards, form dialog, quota drawer, import dialog, alert card, permission tags, and helpers), with behavior and wording unchanged.

#### Global search and status bar (P3)

- `⌘K` or `Ctrl+K` opens global search across menus, service types, instances, API keys, trace IDs, and exceptions; arrow keys move, Enter opens, Esc closes.
- The header status bar shows the number of open circuit breakers and the 24-hour exception count, and both link to the matching monitoring page.

#### Configuration and API fix (#575)

- `GET /api/loadbalancer/config/{serviceType}` now returns **200 with the global defaults** (`isGlobal: true`) when a service type has no dedicated load-balancer configuration, instead of 500. The strategy page no longer drops rows for service types such as Image Generation and Image Editing, and the browser console no longer logs 500s. The front end still tolerates 404 for older backends.

#### Tests and code hygiene (TA1 / TA5)

- New sanitization corpus regression suite (20 cases covering masked, missed, false-positive, and pass-through behavior) plus response-sanitization coverage tests.
- Ten backend classes with no callers are marked `@Deprecated(forRemoval = true)` with their replacement noted; two Logback converter registrations that never took effect were removed (the `conversionRule` entries pointed at a non-existent package and the patterns never used `%traceId`).

#### Upgrade notes

- **One schema change**: Flyway `V4__sanitization_config.sql` (one script per dialect; it only adds the `sanitization_config` table and touches no existing table). The normal startup migration path applies it; no manual step.
- **One authorization semantics change**: a write code no longer doubles as a read path — read endpoints are gated by their `:read` code. On upgrade `RolePermissionSeeder` adds the matching read code to built-in roles that hold a write code; custom roles that only carry write codes and need to list data must be granted the read code in role management.
- No breaking API change; every old URL still works and the menu restructure does not affect deep links.
- Automation that depends on the menu structure should switch to URL- or permission-based checks: the menu went from 9 groups / 40 entries to 7 groups / 28 items.

### [3.3.6] - 2026-10-19 - Patch Release (schema migration + two authorization restrictions + in-page write gating and contract slimming)

> ⚠️ **Behavior changes to check** (one by one, for clients and operators):
>
> | # | Change | Who is affected |
> |---|---|---|
> | 1 | **Flyway V3** rewrites the seven historic Chinese blacklist "reason" presets into stable codes (`MALICIOUS_LOGIN`, …); the console translates them per language | Deployments that already hold data; the field's value shape changes (free text is untouched) |
> | 2 | **Permission codes 54 → 55**: `monitoring:metrics:write` and `monitoring:slowquery:write` are new | Custom roles must be granted explicitly; the built-in `OPERATOR` picks both up by its "every `:read` + `:write`" template |
> | 3 | `callhistory:view` **no longer** authorizes `POST /api/call-history/record` and `DELETE /api/call-history/cleanup` | Non-ADMIN roles holding only `callhistory:view`: these two calls turn from allowed into 403 |
> | 4 | `POST /api/state-persistence/test/save-circuit-breaker/{id}` is **not registered** under `prod` | Anything in production that relied on this test endpoint (nothing should) |
> | 5 | Always-empty fields deleted: `PerformanceIssue` / `activeIssues`, and `ModelCallStats.p95ResponseTime` / `p99ResponseTime` / `peakQps` | Clients and scripts that read those fields |
> | 6 | Prometheus loses ten series (`sanitization.rule.matches` plus the nine from `SanitizationMetrics`) | Dashboards built on them — they were permanently 0 |

#### Data: blacklist reasons become stable codes with Flyway V3 (#455)

- Root cause: `security_blacklist.reason` is free text and the console submitted **the Chinese label itself** as the value, so
  historic reasons stayed Chinese after switching to English.
- Fix: the seven presets are language-neutral codes translated at render time; existing rows are rewritten by the idempotent
  `V3__blacklist_reason_codes.sql` (one copy per dialect, H2 and PostgreSQL; the `WHERE` matches the old Chinese literals, so a
  rerun does not hit twice; user-typed text is untouched). Rollback statements are in PR `#533`.

#### Authorization: two restrictions (#514 / #483 / #487)

- `callhistory:view` used to be an `any(...)` rule (empty method set = every method), so **"view" also authorized writes and
  deletes**. It is now a `GET`-only rule; writes and deletes match nothing and fall to the `DENY_ALL` default (ADMIN only).
- The state-persistence **test write endpoint** moved out of the management controller into its own `@Profile("!prod")` class,
  so it does not exist in the production contract.
- The quota-cleanup scheduler read `app.quota.cleanup.enabled` — an odd one out that no YAML ever set — so operators could not
  turn it off. It now reads `jairouter.security.quota.cleanup.enabled`, and `security-base.yml` declares the key (default
  `true`, same behavior as before).

#### Permissions and console gating (#454 / #468)

- `monitoring:metrics:write`: the ten write endpoints under `/api/monitoring/metrics/**` had **no rule at all** and were only
  caught by the default deny; they are now an explicit, grantable permission.
- `monitoring:slowquery:write`: same for `DELETE /api/monitoring/slow-queries/stats` (the "reset statistics" button).
- A new `v-permission` directive hides 33 write controls across 9 pages (including drag-to-reorder in `RuleManagement`) unless
  the user holds the matching write code. **The frontend only hides; authorization is still enforced server-side** — a limited
  role calling anyway still gets 403, asserted end-to-end.

#### Contract slimming: deleting fields that were always empty (#501)

- `PerformanceIssue` / `activeIssues`: `activeIssues` was never populated (zero `activeIssues.add` sites) so it was always `[]`;
  removed.
- `ModelCallStats.p95ResponseTime` / `p99ResponseTime` / `peakQps`: percentiles cannot be derived from aggregates and
  `currentQps` was never computed; removed.
- Five always-empty blacklist statistics fields are now computed for real; the exception event `lineNumber` is taken from the
  application frame of the stack trace.

#### Observability (#456 / #495)

- Ten security meters wired to real call sites: authentication 4 (attempts / successes / failures / duration), JWT 4
  (validations / refreshes / expired / validation duration), sanitization 2 (operations / duration).
- The never-incremented `jairouter.security.sanitization.rule.matches` counter and the zero-caller `SanitizationMetrics` class
  (nine more always-zero series) were removed, and a registration-surface guard test prevents "registered but never written"
  meters from coming back.

#### Docs and gates (#457 / #547)

- `/v3/api-docs` used to be an **empty document** (springdoc scanned a package that does not exist); the scan scope was fixed
  and pinned by a guard.
- Eighteen stale documents were reviewed one by one: the release version pinned at v3.1.2, the `docker pull` example pinned at
  v3.1.1 and a `v3.1.1x+` typo were fixed; the rest are recorded as reviewed (ReviewedAt).
- The contribution guide now documents the release cadence and how to read the CI gates correctly.

### [3.3.5] - 2026-10-16 - Patch Release (store contract and health semantics fixed; error-body shapes corrected in docs)

> ⚠️ **One client-visible change** (please check your client): **on the `/api/**` surface the error-body shape depends on who writes it** —
> the authentication filter's own responses (401 `AUTH_MISSING`, 429 `RATE_LIMITED_API_KEY`) are protocol-shaped
> `{"error":{"message":…,"type":…,"code":…}}`, an authorization-layer 403 is `text/plain` `Access Denied`, and only
> controller-thrown errors are `RouterResponse`. **The client rule is: is there a top-level `error` field?**
> This release **changes no runtime behavior and no status code** — that item documents the existing implementation as measured (#448).
>
> **Released as GitHub Release `v3.3.5` (2026-10-17)** with multi-arch images (`v3.3.5` / `3.3.5` / `latest`); this release also ships the content of `v3.3.2`–`v3.3.4`.
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
> **Tagged `v3.3.4`**; its content ships with the `v3.3.5` GitHub Release (2026-10-17).
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
> **Tagged `v3.3.3`**; its content ships with the `v3.3.5` GitHub Release (2026-10-17).
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

- Corrected `claude-code.md` (zh/en) false claim about native-surface authentication: `/v1/**` **accepts both** `X-API-Key` (OpenAI surface) / `x-api-key` (Anthropic surface) and `X-Jairouter-Token` (JWT); when both present JWT takes priority; previously incorrectly stated "only API Key authentication is supported"

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

- **README / README-ZH full refresh**: refreshed 7 UI screenshots (incl. dark theme and call-history dashboard; previously the 2026-08-26 UI); Core Features now cover response cache P1, RBAC 44 codes and dark/light theme; Roadmap permission codes 43→44; sample auth header unified to `X-Jairouter-Token`
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

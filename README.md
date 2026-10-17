# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter — AI Model Gateway" width="180">
</p>

<p align="center">
  <strong>One OpenAI-compatible endpoint in front of every model backend you run.</strong>
</p>

<p align="center">
  Ollama · vLLM · GPUStack · Xinference · LocalAI · OpenAI · Claude · Gemini<br>
  Routing · load balancing · rate limiting · circuit breaking · failover · RBAC · audit · visual console
</p>

<p align="center">
  <a href="https://github.com/Lincoln-cn/JAiRouter/releases">
    <img src="https://img.shields.io/github/v/release/Lincoln-cn/JAiRouter?style=flat-square" alt="Latest release">
  </a>
  <a href="https://hub.docker.com/r/sodlinken/jairouter">
    <img src="https://img.shields.io/docker/pulls/sodlinken/jairouter?style=flat-square&logo=docker" alt="Docker Pulls">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/Lincoln-cn/JAiRouter?style=flat-square" alt="License">
  </a>
</p>

<p align="center">
  <a href="README-ZH.md">中文</a> •
  <a href="https://jairouter.com">Docs</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/issues">Issues</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">Discussions</a>
</p>

This README is written to be read by **people and AI agents alike**: the facts block below and the
[Repository map](#repository-map) / [Commands](#commands) / [HTTP surfaces](#http-surfaces) sections
are the machine-actionable core. Where the code and this file disagree, the code wins — please open
an issue so we can fix the file.

<!-- Canonical facts. Keep in sync with pom.xml, application*.yml and the workflow files. -->
```yaml
product:         JAiRouter
repository:      Lincoln-cn/JAiRouter
checkout_dir:    model-router            # the local/git directory name differs from the product name
artifact:        model-router            # Maven artifactId
project_version: 3.3.5                   # pom.xml
latest_release:  v3.3.1 (2026-10-14)
stack:
  backend:  Java 17 + Spring Boot 3.5.5 (WebFlux / reactive)
  frontend: Vue 3 + TypeScript + Vite, bilingual zh-CN / en-US
  build:    Maven wrapper — ./mvnw (Unix) · .\mvnw.cmd (Windows)
runtime:
  default_port: 8080
  console:      /admin/
  swagger_ui:   dev profile only
  required_env_in_prod: [JWT_SECRET, INITIAL_ADMIN_PASSWORD]
surfaces:
  - /v1/**        # client surface: OpenAI-native + Anthropic-compatible
  - /api/**       # management + legacy surface (RBAC-governed)
  - /actuator/**  # health, readiness, Prometheus
tests:
  command: ./mvnw test -o
  scale:   ~4,100 test methods across ~400 test classes
license: Apache-2.0
```

## What it is

JAiRouter is a **self-hosted AI model gateway**. It puts one OpenAI-compatible endpoint in front of
the model servers you already run — Ollama, vLLM, GPUStack, Xinference, LocalAI — plus cloud
providers (OpenAI, Anthropic, Gemini, and any OpenAI-compatible vendor). Existing OpenAI SDK,
LangChain and LlamaIndex code keeps working: point `base_url` at the gateway and nothing else changes.

It then adds the layer you would otherwise build yourself: multi-instance load balancing, rate
limiting, circuit breaking with failover to a *different* healthy instance, RBAC, audit logging,
record governance and observability — all configurable at runtime from a web console, without
restarting the process.

| Problem | What JAiRouter does |
|---------|---------------------|
| Many model endpoints to manage | One unified API surface |
| Manual failover when a backend dies | Circuit breaker + request-level failover |
| Auth implemented per service | JWT + API key with data-driven RBAC |
| Logs and metrics scattered per backend | Central metrics, tracing, call history |
| Restart required for config changes | Hot reload from the console |

## Quick start

### Development profile — no secrets required

```bash
docker run -d --name jairouter -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  sodlinken/jairouter:latest

# Console:      http://localhost:8080/admin/
# Default login: admin / ChangeMeOnFirstStartup123456
```

The dev profile intentionally falls back to built-in development secrets, so it starts with no
configuration at all. **Never expose a dev-profile instance to an untrusted network.**

### Production profile — secrets are required

```bash
docker run -d --name jairouter -p 8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e INITIAL_ADMIN_PASSWORD='<your-strong-admin-password>' \
  sodlinken/jairouter:latest
```

**Both variables are mandatory.** The `prod` profile performs a startup security gate and
**refuses to boot** when either is missing or still at its factory default — see
[Production startup gate](#production-startup-gate) for the exact rules. A container started with
only `JWT_SECRET` exits during startup with `IllegalStateException`.

If you boot the JAR directly rather than through Docker, generate strong values first:

```bash
java -jar target/model-router-*.jar --generate-key        # JWT secret
java -jar target/model-router-*.jar --generate-password   # admin password
```

### Point a client at it

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",             # OpenAI-native surface (raw JSON / SSE)
    api_key="not-needed",                            # Authorization is forwarded downstream
    default_headers={"X-API-Key": "<your-api-key>"}  # gateway credential (Management API key)
)

response = client.chat.completions.create(
    model="llama3.2",                                # any model from your configured backends
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

Registered backends are managed from the console under **Instance Management**; changes apply by hot
reload, with no restart.

## Production startup gate

The gate lives in `StartupSecretKeyChecker` (a `CommandLineRunner`) and
`StartupSecurityGate`. It runs on every start and reads **environment variables directly** — setting
the equivalent YAML property is *not* enough to satisfy it:

| Check | Rule when the profile is `prod` / `production` | Rule otherwise |
|-------|-----------------------------------------------|----------------|
| JWT enabled | `jairouter.security.jwt.enabled` must be `true`, otherwise fail | not checked |
| `JWT_SECRET` | must be set, not a known factory/doc default, and not rate `VERY_WEAK` / `WEAK` | warn only |
| `INITIAL_ADMIN_PASSWORD` | must be set, except for the factory default `ChangeMeOnFirstStartup123456`, and not rate `VERY_WEAK` / `WEAK` | warn only |

Details worth knowing:

- **A missing `INITIAL_ADMIN_PASSWORD` is a hard failure in production.** `ADMIN_PASSWORD` is accepted
  as a secondary variable name, but nothing is generated for you.
- **No password is auto-generated, ever.** If neither variable is set, startup aborts; it does not
  invent a random password and it does not fall back to the documented default. (The only generators
  are the explicit CLI flags `--generate-key` / `--generate-password`.)
- A `MEDIUM`-strength value logs a warning and **allows** startup; `VERY_WEAK` / `WEAK` blocks it.
- Escape hatch for trusted isolated networks: `JAIRouter_SKIP_AUTH_WARNING=true`. It must be set
  explicitly and it disables the gate for **all** of the checks above.
- The `admin` account itself comes from `jairouter.security.jwt.accounts` in
  `src/main/resources/config/auth/jwt.yml`, whose password is
  `{noop}${INITIAL_ADMIN_PASSWORD:ChangeMeOnFirstStartup123456}`. On first start those YAML accounts
  are copied into the `jwt_accounts` table **only when that table is empty**
  (`JpaDatabaseInitializer.initializeJwtAccounts`); later starts leave the table untouched, so
  rotating `INITIAL_ADMIN_PASSWORD` does **not** change an account that already exists in the
  database — change it in the console instead.

## Repository map

| Path | What lives there |
|------|------------------|
| `src/main/java/org/unreal/modelrouter/router/` | Gateway core: adapters, request processors, load balancers, rule engine, circuit breaker, cache |
| `src/main/java/org/unreal/modelrouter/auth/` | Authentication (JWT, API key), sanitization, security audit, RBAC, console-facing auth APIs |
| `src/main/java/org/unreal/modelrouter/monitor/` | Metrics, tracing, call history, slow-query analysis, error tracking, security alerts |
| `src/main/java/org/unreal/modelrouter/persistence/` | Store abstractions plus JPA entities/repositories and schema initialization |
| `src/main/java/org/unreal/modelrouter/config/` | Configuration service: hot reload, versioning/rollback, validation, console API |
| `src/main/resources/config/**` | Modular YAML config, imported via `spring.config.import` in `application.yml` |
| `src/main/resources/db/migration/{h2,postgres}/` | Versioned schema migrations (`Flyway`), one dialect directory each |
| `src/test/java/` | JUnit 5 + Mockito + Reactor `StepVerifier`; ~4,100 test methods |
| `frontend/` | Vue 3 + TypeScript console (Vite `base: /admin/`) |
| `docs/zh/`, `docs/en/` | MkDocs documentation, kept symmetrical |
| `deploy/` | Kubernetes manifests, compose files, deployment scripts |
| `.cspell.json` | Spell-check word list — the docs workflow fails on unknown words |
| `innerdoc/` | Internal design notes, not published |

## Commands

```bash
# Build (skipping tests)
./mvnw clean package -DskipTests

# Run locally
java -jar target/model-router-*.jar --spring.profiles.active=dev

# Full Java test suite (offline works; -Dcheckstyle.skip etc. speed up iteration)
./mvnw test -o

# Focused test run — comma-separated class filter, no wildcards
./mvnw test -Dtest=FooTest,BarTest -DfailIfNoTests=false -o

# Frontend (type-check + build)
cd frontend && npm ci && npm run type-check && npm run build
```

Windows: substitute `.\mvnw.cmd` for `./mvnw`.

## HTTP surfaces

| Surface | Prefix | Protocol | Auth |
|---------|--------|----------|------|
| Client — OpenAI-native | `/v1/**` | OpenAI JSON + SSE (`/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/rerank`) | JWT or API key |
| Client — Anthropic-compatible | `/v1/messages`, `/v1/messages/count_tokens` | Anthropic Messages (streaming SSE, tool use) | JWT or API key |
| Management | `/api/**` | JSON, `RouterResponse` envelope | JWT/API key + RBAC URL matrix |
| Operations | `/actuator/**` | Health, readiness, `/actuator/prometheus` | depends on profile |

Error bodies are protocol-shaped per surface: on `/v1/messages` an Anthropic-shaped
`{"type":"error","error":{...}}`, on the other `/v1/**` paths an OpenAI-shaped
`{"error":{message,type,code}}`, and `/api/**` keeps its legacy shape. Streaming failures on the
`/v1` surface emit a protocol-shaped error frame **and** still terminate the stream with an error —
`[DONE]` is never appended to a failed stream, because clients treat `[DONE]` as success.

## Authentication and authorization

- **JWT** — header name is configurable, default `Jairouter_Token`.
- **API key** — header `X-API-Key`. Gateway credentials are separate from downstream provider keys:
  `x-api-key` is consumed by the gateway and never forwarded, so per-instance downstream credentials
  belong in the instance's own `headers.Authorization`.
- **RBAC** — data-driven: 53 permission codes, 4 role templates (`ADMIN` / `OPERATOR` / `USER` /
  `VIEWER`), a URL rule matrix plus method-level `@PreAuthorize`. Requests that match no rule follow
  `jairouter.security.rbac.unmatched-policy`, whose default is `DENY_ALL`; `ROLE_ADMIN` short-circuits
  everything. `/v1/**` is authenticated but intentionally outside the permission matrix.

## Feature inventory

Implemented and exercised by the test suite:

- **Adapters** — Ollama, vLLM, GPUStack, Xinference, LocalAI, OpenAI, Anthropic (native), Gemini
  (native), plus any OpenAI-compatible provider declared in `adapter-definitions`.
- **Load balancing** — round-robin, weighted, least-connections, IP hash, consistent hash, EWMA
  latency-aware, random.
- **Rule engine** — conditional routing on model, service type, header, client IP, weight and
  instance tags, with hit statistics; visual editing in the console.
- **Resilience** — circuit breaker per instance, request-level failover to another healthy instance,
  rate limiting (token bucket / leaky bucket / sliding window), response cache for deterministic
  requests (opt-in, tenant-isolated keys).
- **Quota** — multi-dimensional, multi-window ledger (MINUTE/HOUR/DAY/MONTH) with Redis distributed
  counting and local fallback (`jairouter.quota.enabled`, off by default).
- **Record governance** — call history at three levels: metadata only, desensitized summary, or
  AES-256-GCM-encrypted full content; ADMIN-only decryption endpoint.
- **Observability** — Prometheus metrics, OpenTelemetry tracing, slow-query analysis, error tracking,
  security alerts. Alert outlets are deliberately limited to **Prometheus metrics + structured logs** —
  the repository implements no push channels (email/webhook/IM); wire those up in Alertmanager or your
  log platform.
- **Persistence** — H2 by default; PostgreSQL (with `Flyway` migrations) when `DATABASE_URL` points at
  it; Redis for shared state in multi-replica deployments.
- **Multi-replica** — cross-replica event broadcast, shared rate-limit/quota counters, and a
  distributed lock for scheduled tasks (including the alert cooldown).
- **Console** — Vue 3 SPA at `/admin/`: services, instances, adapters, rules, versions & rollback,
  quota, call history, tracing, alerts, API keys, RBAC, dark/light theme, zh-CN / en-US.

Deliberately **not** implemented: semantic (vector-similarity) cache and any in-repo notification
delivery. See [Status and recent work](#status-and-recent-work) for the open ledger.

## Architecture

<details>
<summary><b>Request flow</b></summary>

```
┌─────────────────────────────────────────────────────────────────┐
│                         Your Application                         │
│                 (OpenAI SDK / LangChain / LlamaIndex)           │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼ OpenAI-Compatible API
┌─────────────────────────────────────────────────────────────────┐
│                         JAiRouter Gateway                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ Routing │ │ Balance │ │  Limit  │ │ Circuit │ │   Auth  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Observability & Persistence                 │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
       ┌──────────────┬───────────┼───────────┬──────────────┐
       ▼              ▼           ▼           ▼              ▼
   ┌───────┐     ┌───────┐   ┌───────┐   ┌──────────┐   ┌───────┐
   │Ollama │     │ vLLM  │   │GPUStack│  │Xinference│   │ OpenAI │
   └───────┘     └───────┘   └───────┘   └──────────┘   └───────┘
```

</details>

<details>
<summary><b>Console screenshots</b></summary>

<p align="center">
  <img src="screenshots/dashboard-en-US-light.png" alt="JAiRouter dashboard" width="720">
  <br/>
  <em>Dashboard — real-time service, instance and system metrics</em>
</p>

<p align="center">
  <img src="screenshots/service-management-en-US-light.png" alt="Service Management" width="720">
  <img src="screenshots/instance-management-en-US-light.png" alt="Instance Management" width="720">
  <br/>
  <em>Service & instance management — adapters and load-balancing strategies per service</em>
</p>

<p align="center">
  <img src="screenshots/rule-management-en-US-light.png" alt="Rule Engine" width="720">
  <br/>
  <em>Visual routing rules with hit statistics, priority drag-and-drop and templates</em>
</p>

<p align="center">
  <img src="screenshots/call-history-dashboard-en-US-light.png" alt="Call History Analytics" width="720">
  <br/>
  <em>Call history analytics — success rate, latency and token usage trends</em>
</p>

<p align="center">
  <img src="screenshots/extra-slow-query-analysis-en-US-light.png" alt="Slow Query Analysis" width="720">
  <br/>
  <em>Slow-query analysis — hotspots, alert status and operation breakdown</em>
</p>

<p align="center">
  <img src="screenshots/playground-chat-en-US-light.png" alt="AI Playground" width="720">
  <br/>
  <em>Playground — chat, embedding, rerank, audio and image test benches</em>
</p>

<p align="center">
  <img src="screenshots/quota-config-en-US-light.png" alt="Quota Runtime Configuration" width="720">
  <br/>
  <em>Quota runtime configuration — restart-required fields are read-only with a badge</em>
</p>

<p align="center">
  <img src="screenshots/quota-monitoring-en-US-light.png" alt="Quota Usage Monitoring" width="720">
  <br/>
  <em>Quota monitoring — backend type, degradation status and multi-dimensional filtering</em>
</p>

<p align="center">
  <img src="screenshots/client-access-en-US-light.png" alt="Client Access Guide" width="720">
  <br/>
  <em>Client access guide — copy-ready snippets for the OpenAI-compatible and Anthropic base URLs</em>
</p>

<p align="center">
  <img src="screenshots/dashboard-en-US-dark.png" alt="Dashboard (dark theme)" width="720">
  <br/>
  <em>Built-in dark theme — the console is themeable and bilingual</em>
</p>

</details>

## Status and recent work

**Latest release: v3.3.1 (2026-10-14)** — an audit-remediation patch: blocking writes moved off the event
loop, outbound resolved addresses pinned to the connection (DNS rebinding), token-usage writes
ownership-scoped, and `SecurityAlertService` actually wired up (it had no caller before, so none of its four
security alerts ever fired).

**Merged after v3.3.1, tagged `v3.3.2` and `v3.3.3`, not released yet** (2026-10-12 → 2026-10-15) — grouped by theme:

| Theme | What changed |
|-------|--------------|
| **Error-code governance (issue #370)** | A new `ErrorCode` registry, completed to **62 codes that are actually emitted** (**63** after the #404 rate-limit split below; each with a **render path**: status decided by the naming rule, the authentication exception, the response-body site or the error-body writer). The zh/en tables were rewritten against it and CI reconciles them **both ways** (a missing code and a documented-yet-never-emitted code both fail). **No bare code literals remain in `src/main/java`** (~175 emission sites now read `ErrorCode.X.code()`), guarded by a new literal test. ⚠️ External contract change (#403): `TOKEN_USAGE_FORBIDDEN` → `FORBIDDEN_TOKEN_USAGE` — that code is written into the token-usage response body, so clients do see it. Separately, `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE` is only an internal constant rename inside `AuthorizationException`: that code has no emission site (its factory method is called only by unit tests), so no client can ever receive it. |
| Error-code ledger | The registry surfaced two pre-existing semantic problems: `RATE_LIMIT_EXCEEDED`'s two statuses (429/401, #404) — **resolved by splitting the code after the `v3.3.3` tag** (see below) — and seven codes that existed only as constants plus never-called factories, which **were deleted** (#406) |
| **Error-code status alignment (issue #405, merged after the `v3.3.3` tag)** | Endpoint status and rule-chain status now agree at both ends under the hybrid scheme: `BUILTIN_READONLY` answers **403** everywhere (the endpoint used to answer 400) and `RESTART_REQUIRED` answers **400** everywhere (the rule chain used to derive 403). The registry descriptions, both error-code tables and the divergence assertion in `ErrorCodeRegistryTest` were updated with it |
| Repository hygiene | Frontend build artifacts no longer dirty the working tree: normalized `package-lock.json` plus `.gitattributes -text` for the two generated `.d.ts` files (#408 / #409) |
| **Streaming (`/v1`)** | **Fixed: `/v1` streaming answered `200` with a zero-byte body** — the `/v1` branch used `exchangeToMono` and handed the response body back out of the exchange scope, so WebClient released it before the framework subscribed. Error frames are now emitted only **after** the first chunk, so a pre-commit failure still ends with the downstream's real status code (429 stays 429). Regression-pinned by the end-to-end fixture, which now drives **both** faces |
| Streaming baseline | `/v1` error frames plus state fidelity on pre-commit failure; legacy `/api/**` semantics deliberately unchanged; client-visible baseline pinned by probe classes |
| Alerting & audit | Cross-replica exclusive alert cooldown; event types are always enum names and `parseEventType` no longer silently falls back to `SYSTEM_MAINTENANCE` (enum 17 → 26); dead alert-threshold/outlet config removed |
| Config honesty (3rd pass) | `jairouter.security.audit`'s seven unbound sub-blocks, the whole `jairouter.security.performance` tree and 14 `audit.extended` keys deleted; sanitization failures now only terminate (the error body belongs to the central handler); the startup validator no longer claims a random password gets generated |
| Dead code & shapes | `SecurityAuditApi`, the producer-less `SecurityStatisticsResponse` DTO and the whole `common/api` module-boundary layer removed (16 files, no implementation and no caller); console `DashboardOverview` typed to the real payload; the response-sanitization test now actually drives the write path |
| Docs & gates | README rewritten as a facts-first document; error-code table rebuilt from code; `dynamic-config` and quick-start instance examples rewritten against real endpoints and executed; markdown fence-parity checker added (6 files / 10 sites fixed); contribution guide gained "read the CI gates correctly" and a release-cadence rule |
| Tooling | Local substitute gates for the month CI minutes ran out; image smoke test polls container health instead of racing it; the docs-version self-test stopped asserting a copy of the script's wording, and its workflow now runs when `scripts/docs/**` changes |

Release tags: `v3.3.3` · `v3.3.2` · `v3.3.1` · `v3.3.0` · `v3.2.2` · `v3.2.1` · `v3.2.0` · `v3.1.2` · `v3.1.1`.
`v3.3.2` and `v3.3.3` are tagged but **not released** yet (`v3.3.2`'s content ships with `v3.3.3`).

**Tagged `v3.3.4`, not released yet** (the `v3.3.3` tag stays frozen at `e965f9a8`); what changed relative to `v3.3.3`, grouped by theme:

| Theme | What changed |
|-------|--------------|
| **Rate-limit error-code semantics (#404, decided)** | The two real paths were split per "429 plus a new code": the admin rate limiter keeps `RATE_LIMIT_EXCEEDED` (flat 429 body) while the API Key validation chain uses the new `RATE_LIMITED_API_KEY`, and the authentication filter now passes through **the 429 carried by the exception** (on `/v1/**` the `type` maps from the status to `rate_limit_error`). Registry 62 → 63 codes; clients no longer have to treat 401 as a rate limit |
| **Error-code close-out (#406 / #412 / #414)** | Seven never-emitted codes (constants plus never-called factories) deleted; the docs separate the internal `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE` constant rename from the client-visible `TOKEN_USAGE_FORBIDDEN` → `FORBIDDEN_TOKEN_USAGE` rename; four ghost codes in the management-API tables replaced with registry names |
| **Status alignment (#405)** | `BUILTIN_READONLY` answers 403 everywhere (the endpoint used to answer 400); `RESTART_REQUIRED` answers 400 everywhere (the rule chain used to derive 403) |

**Tagged `v3.3.5`, not released yet** (2026-10-16; the `v3.3.4` tag stays frozen at `92a134ef`); what changed relative to `v3.3.4`, grouped by theme:

| Theme | What changed |
|-------|--------------|
| **Client-visible status fix (#432)** | A **missing request body** no longer becomes a **500 + `INTERNAL_ERROR`**: three `@RequestBody` endpoints (`AdapterConfigController` create/update, `LoadBalancerManagementController` config update) now pre-validate and answer **400 + "request body invalid or missing"**. Note the constraint this exposed: the check has to sit **outside** the method's own `try`, otherwise the blanket `catch (Exception)` swallows the `ServerWebInputException` and re-wraps it as 500 |
| Documentation↔code contract (#436) | The en TTS/STT endpoint prefixes were corrected to `/api/v1/audio/*`. Evidence first: `/v1/audio/*` **has no mapping at all** — the `/v1` face only serves models, chat, embeddings, rerank (plus Anthropic messages and debug), so it was the **en** side that was wrong |
| Gates | `快闸` now also triggers on `src/main/resources/config/**` and `.github/workflows/**`; the previously dead workflow-syntax validator was repaired (it was misreading YAML indentation as Python and contained a tautological path check) and wired into the gate, with a self-test that includes refutation cases |
| **Store contract fix (#446)** | Deleting the **last** entry of a `StoreManager`-backed collection was a **silent no-op that still answered `200 {"success":true}`**: `JpaStoreManager.saveConfig` returned early on an empty map, while the callers are all "load all → mutate → save all", so removing the final entry passes exactly that empty map. Pools came straight back; a configurable adapter looked deleted until the **process restarted**. The early return is gone (the other three store implementations never had it) and "an empty map is a legal payload" is now part of the `StoreManager.saveConfig` contract. The old tests missed it because they **mocked `StoreManager`** — the mock has no such early return, so "`saveConfig` was called" was vacuously true |
| **Health semantics fix (#447)** | `/actuator/health` no longer turns `DOWN` on the **lifetime-cumulative** tracing span drop rate: all three compose files use that endpoint as the container healthcheck, and the ratio can only return below the threshold after a restart ⇒ the container was **permanently** unhealthy, while restarting does not stop spans from being dropped (measured: `tracing=DOWN`, `issue=数据丢弃率过高: 50.44%`, every other component `UP`). It is now a `WARNING` (HTTP stays 200, the numbers stay in the details for alert rules); only "async processor not running", "CRITICAL memory pressure" and "performance monitor itself DOWN" still answer `DOWN`. The same block also lost issues because each check overwrote the previous builder — that is fixed too |
| Docs accuracy (#448) | The `/api/**` error-body shape was corrected against measurement: the authentication chain's own 401 (`AUTH_MISSING`) and 429 (`RATE_LIMITED_API_KEY`) are protocol-shaped, an authorization-layer 403 is `text/plain` `Access Denied`, and only controller-thrown errors are `RouterResponse` ⇒ the client rule is now "**is there a top-level `error` field?**". The `claude-code.md` claim that both surfaces use `RouterResponse` (wrong for `/v1` as well) was corrected with it |
| Tooling | The branch-cleanup scripts landed: under squash merges, ancestry cannot tell whether a branch was merged (67 of 80 were missed), so they use two content criteria plus a synthetic-repo self-test |

Local verification before this release (cold start on a fresh H2 database → frontend build → package → run on 8080): **frontend E2E 414/414** (14 specs; 404/407 before the fixes — `crud:pool:delete` was the real defect, the other two were missing API-key fixtures); **API smoke 51/51** (covering every client-visible change since v3.3.1: the #404 rate-limit split, the #405 403, the #432 400, both `/v1` error shapes and the #340 `/actuator` auth surface).

See the [`[3.3.5]` changelog entry](docs/en/reference/changelog.md); tag only, no GitHub Release or image, per the standing policy.

### Open ledger

**This file deliberately does not keep a hand-maintained list of issue numbers.** An earlier version did,
and it was stale within hours of being written. The authoritative ledger is the issue tracker:
**[open issues →](https://github.com/Lincoln-cn/JAiRouter/issues?q=is%3Aissue+is%3Aopen)**

To give a sense of what tends to be tracked there, entries fall into a few kinds
(**illustrative, not a current list** — follow the link above for the live set):

- **Behavior covered only by unit tests** — paths whose real-runtime wiring has no end-to-end evidence
  (for example the alerting chain and the streaming error path).
- **Shapes that lie** — a declared type or interface that does not match what the runtime actually produces,
  so tooling cannot constrain it.
- **Operator-facing text that is wrong** — a startup message or a document that describes something the code
  does not do.
- **Dead surface kept by inertia** — config keys, DTOs or interfaces with no consumer.
- **Documentation debt** — pages due for content review, or with broken rendering.

If something is neither described above nor in the tracker, treat it as **unknown** rather than as working.

## Documentation

| Resource | Link |
|----------|------|
| Full documentation | https://jairouter.com |
| Deployment | https://jairouter.com/en/deployment/ |
| Configuration | https://jairouter.com/en/configuration/ |
| Monitoring | https://jairouter.com/en/monitoring/ |
| Interactive API reference | `http://localhost:8080/swagger-ui` (dev profile) |

## Why JAiRouter?

- **vs calling backends directly** — no shared resilience, no central credentials, no usage
  visibility. JAiRouter gives one endpoint, automatic failover and a full audit trail.
- **vs One-API / new-api** — those distribute and bill API keys for shared accounts. JAiRouter is the
  gateway in front of **your own** inference cluster: multi-instance balancing, circuit breaking, rule
  routing and RBAC for self-hosted Ollama/vLLM/GPUStack. The two complement each other.
- **vs Nginx** — Nginx is a general-purpose web server. JAiRouter is purpose-built for model
  workloads: OpenAI-compatible routing, model-aware balancing, streaming semantics.
- **vs LangChain** — LangChain is an application framework. JAiRouter is the infrastructure layer
  beneath it.

| Feature | JAiRouter | Nginx | One-API | LangChain |
|---------|:---------:|:-----:|:-------:|:---------:|
| OpenAI compatible | ✅ | ❌ | ✅ | ✅ |
| Load balancing | ✅ | ✅ | ✅ | ❌ |
| Circuit breaker | ✅ | ❌ | ❌ | ❌ |
| Rate limiting | ✅ | ✅ | ✅ | ❌ |
| Web console | ✅ | ❌ | ✅ | ❌ |
| Config hot reload | ✅ | ❌ | ✅ | ❌ |
| Version control | ✅ | ❌ | ❌ | ❌ |
| OpenTelemetry | ✅ | ❌ | ❌ | ✅ |

## Contributing

Contributions are welcome — see the [Contributing Guide](https://jairouter.com/en/development/contributing/).

Before opening a pull request:

```bash
./mvnw test -o                       # ~400 test classes, ~4,100 test methods, must stay green
cd frontend && npm run type-check    # the frontend type gate
```

House conventions worth knowing: commits use `<type>(<scope>): <summary>`; every fix/feature branch
targets `master` and is squash-merged; documentation exists in both `docs/zh/` and `docs/en/` and is
expected to stay symmetrical; docs are spell-checked against `.cspell.json`, so new technical terms
may need to be added there.

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter
./mvnw clean package -DskipTests
java -jar target/model-router-*.jar
```

## Support

- **Documentation**: https://jairouter.com

### Issues or Discussions — where should I post?

GitHub gives us two channels and we keep them separate on purpose. Pick by **what you have**:

| What you have | Where to post | How we handle it |
|---------------|---------------|------------------|
| A **reproducible defect** — crash, wrong result, regression, wrong doc | [**Issues**](https://github.com/Lincoln-cn/JAiRouter/issues) | Triaged quickly; confirmed defects are fixed or scheduled |
| A **requirement or idea** — feature request, new adapter, design discussion, usage question | [**Discussions**](https://github.com/Lincoln-cn/JAiRouter/discussions) | Collected, voted on and folded into the roadmap |

**In one line**: a defect goes to **Issues**; a requirement or a question goes to **Discussions**.

> Not sure which one fits? Post in Discussions — if it turns out to be a reproducible defect, we will
> move it to Issues for you.

Discussions is organized by category:

| Category | Use it for |
|----------|------------|
| 💡 Ideas | Feature requests and improvement suggestions |
| 🗳 Polls | Community votes on direction |
| 🙏 Q&A | How-to questions for the community |
| 🙌 Show and tell | Share what you built on JAiRouter |
| 💬 General | Anything else |
| 📣 Announcements | Release notes from maintainers |

## License

JAiRouter is released under the [Apache 2.0 License](LICENSE).

---

<p align="center">
  <strong>If JAiRouter saves you time, <a href="https://github.com/Lincoln-cn/JAiRouter/stargazers">star it on GitHub</a> ⭐</strong>
  <br/>
  <em>Found a bug? Open an issue. Have an idea or a question? Start a discussion.</em>
</p>

<p align="center">
  Maintained by Lincoln-cn · Apache-2.0
</p>

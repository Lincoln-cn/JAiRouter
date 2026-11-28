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
  <em>Java + Spring Boot — built for teams whose stack already speaks JVM.</em>
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
project_version: 3.9.1                   # pom.xml
latest_release:  v3.9.1 (2026-11-28)
stack:
  backend:  Java 17 + Spring Boot 4.1.2 (WebFlux / reactive)
  frontend: Vue 3 + TypeScript + Vite, bilingual zh-CN / en-US
  build:    Maven wrapper — ./mvnw (Unix) · .\mvnw.cmd (Windows)
runtime:
  default_port: 8080
  console:      /admin/
  swagger_ui:   dev profile only
  required_env_in_prod: [JWT_SECRET, INITIAL_ADMIN_PASSWORD]
  required_env_in_staging: [JWT_SECRET]
surfaces:
  - /v1/**        # client surface: OpenAI-native + Anthropic-compatible
  - /api/**       # management + legacy surface (RBAC-governed)
  - /actuator/**  # health, readiness, Prometheus
tests:
  command: ./mvnw test -o
  scale:   5,500+ test methods across 680+ test classes
license: Apache-2.0
```

## Why this one

Most LLM gateways are Python (LiteLLM) or an existing gateway stack you have to adopt whole
(Kong, APISIX, Envoy). If your services are Spring Boot and your ops team already runs JVM
workloads in containers, JAiRouter is the same job done in a stack you already know how to
deploy, monitor and debug: one jar or one image, PostgreSQL or H2, Flyway migrations,
Micrometer and Prometheus, and a console that reads like an admin page.

It is not a universal proxy. It is an operable gateway for a small-to-mid deployment:
authorization down to permission codes, audit that survives review, and configuration you can
change without a restart.
Fits: you already run JVM services and container ops, you host your own inference cluster, and you want permissions and audit layered in at the gateway.

Does not fit: pure multi-channel forwarding and billing (see One-API / new-api), general-purpose L7 gateway duties (see Nginx / Kong), or anything that must be handed to a SaaS proxy — this project has no SaaS form.

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

## What you can do with it

Grouped by the job you are trying to do. Everything below is implemented and exercised by the test
suite; the technical list, including what is deliberately not implemented, is in
[Implementation notes](#implementation-notes).

**Route model traffic**

- **One endpoint, many backends** — Ollama, vLLM, GPUStack, Xinference, LocalAI, OpenAI, Anthropic
  (native), Gemini (native), and any OpenAI-compatible server. Point a client's `base_url` here; its
  code stays unchanged.
- **Eight load-balancing strategies** — round-robin, weighted, least-connections, IP hash, consistent
  hash, EWMA latency-aware, random, and session stickiness.
- **Conditional routing rules** — choose an instance by model, service type, header, client IP, weight
  or instance tag; rules record hit counts and are edited visually in the console.
- **Seven service types** — chat, embedding, rerank, speech synthesis, speech recognition, image
  generation, image editing.

**Keep serving when a backend is not**

- **Per-instance circuit breaker** — open / half-open / closed with configurable thresholds; the
  console lists the instances that are open right now.
- **Request-level failover** — a failed instance is retried against a different healthy one instead of
  returning an error.
- **Four rate limiters** — token bucket, leaky bucket, sliding window, and a Redis sliding window for
  multi-replica deployments; enforced per client IP, per API key (per minute) or per service type.
- **Response cache** — deterministic requests can be served from cache with tenant-isolated keys.
- **Multi-dimensional quota** — a minute / hour / day / month ledger with distributed counting, so one
  key or tenant cannot exhaust a shared downstream.
- **State tiers** — circuit-breaker, load-balancer and rate-limiter state can live in Redis, H2 or
  files, and be switched at runtime.

**Hide what should not leave the building**

- **PII sanitization** — regex and keyword rules on outbound HTTP (including `/v1` JSON and SSE
  streams) and on stored call history; per-user whitelist and configurable mask character.
- **Record governance** — choose what is retained: metadata only, desensitized summary, or
  AES-256-GCM encrypted full content; decrypting full content is a separate ADMIN-only endpoint.
- **API keys** — per-key IP allowlist, expiry, rotation and permission subset, so one key can be
  limited to some service types.
- **Blacklist** — deny by IP, account or device identifier before the request reaches a model.
- **SLA access** — a one-time bootstrap token exchanged for a long-lived refresh credential, so an
  external SLA checker never stores an admin password.

**See what happened**

- **Call history at three levels** — metadata, desensitized summary, or encrypted full content,
  searchable in the console.
- **Statistics** — requests, tokens and latency by model, service type, day, hour and status code;
  slow-call analysis, token usage and quota usage.
- **Tracing** — span search plus per-service p95 / p99 summaries over OpenTelemetry data.
- **Exceptions and slow SQL** — grouped error statistics and the slowest statements with call sites.
- **Audit log and operations log** — every privileged action with actor, target, result and reason;
  page access heat shows who opened which console page.
- **Metrics and logs** — Prometheus exposition plus structured logs; alert push channels
  (email / webhook / IM) are deliberately not implemented.

**Give each person the right view**

- **Seven built-in roles** — ADMIN, OPERATOR, USER, VIEWER, AUDITOR, plus SLA_READONLY and
  SLA_OPERATOR for machine accounts.
- **Seventy-nine permission codes** — `resource:action` pairs; anything not explicitly granted is
  denied.
- **A landing page per role** — command center, operations board, my usage, trends, or audit log.
- **Server-side self scoping** — a token carrying only the `:self:` read codes is filtered to the
  caller's own calls and keys.
- **Login protection** — failure thresholds per account + IP and per IP, lockout window, and online
  unlock from the console.

**Operate it**

- **Storage** — H2 by default; PostgreSQL via `DATABASE_URL` with versioned Flyway migrations; Redis
  for shared state.
- **Multi-replica** — cross-replica event broadcast, shared counters and a distributed lock for
  scheduled tasks.
- **Settings that apply without a restart** — monitoring, tracing, cache, quota, cluster and
  health-probe settings take effect on save; the ones that need a restart say so on the page.
- **Production startup gate** — a weak JWT secret or the factory-default admin password refuses to
  start outside the dev profile.
- **Console** — 53 pages, one task per page, zh-CN and EN, light and dark, plus an AI playground
  (chat, embeddings, rerank, speech, image) and a client access guide you can hand to a team.

## Quick start

### 5-minute demo — no model service needed

Two containers and one script show the gateway end to end: request forwarding, model switching,
response cache, and reversible sanitization. A mock upstream stands in for a real model, so there is
no GPU to provision, no API key to obtain, and no JDK to install.

```bash
docker compose -f deploy/demo/docker-compose.yml pull        # current image
docker compose -f deploy/demo/docker-compose.yml up -d      # wait until both are healthy
node scripts/demo/run-demo.mjs
```

Full walkthrough, including how to check reversible sanitization from the upstream side:
[5-minute demo](docs/en/getting-started/demo.md).

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

- **A missing `INITIAL_ADMIN_PASSWORD` is a hard failure in production.** It is the only name the
  configuration binding reads — the legacy `ADMIN_PASSWORD` alias is gone — and nothing is generated for you.
- **No password is auto-generated in `prod`.** If neither variable is set, startup aborts; it does not
  fall back to the documented default. (The only generators are the explicit CLI flags
  `--generate-key` / `--generate-password`.) Outside `prod`, the base configuration falls back to a
  random value rather than a public one, and prints it once in the startup log.
- A `MEDIUM`-strength value logs a warning and **allows** startup; `VERY_WEAK` / `WEAK` blocks it.
- Escape hatch for trusted isolated networks: `JAIRouter_SKIP_AUTH_WARNING=true`. It must be set
  explicitly and it disables the gate for **all** of the checks above.
- The `admin` account itself comes from `jairouter.security.jwt.accounts` in
  `src/main/resources/config/auth/jwt.yml`, whose password is
  `{noop}${INITIAL_ADMIN_PASSWORD:${random.uuid}}` (random fallback outside `prod`, no public literal).
  On first start those YAML accounts
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
| `src/test/java/` | JUnit 5 + Mockito + Reactor `StepVerifier`; 5,400+ test methods |
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
- **RBAC** — data-driven: 79 permission codes, 7 role templates (`ADMIN` / `OPERATOR` / `USER` / `VIEWER` / `AUDITOR` / `SLA_READONLY` / `SLA_OPERATOR`), a URL rule matrix plus method-level
  `@PreAuthorize`. Requests that match no rule follow
  `jairouter.security.rbac.unmatched-policy`, whose default is `DENY_ALL`; `ROLE_ADMIN` short-circuits
  everything. `/v1/**` is authenticated but intentionally outside the permission matrix.

## Implementation notes

The list above is the feature surface. This section is the default values and boundaries an
operator actually hits, taken from the code:

- **Resilience** — circuit breaker per instance, request-level failover to another healthy
  instance, rate limiting (token bucket / leaky bucket / sliding window), response cache for
  deterministic requests (opt-in, tenant-isolated keys).
- **Quota** — multi-dimensional, multi-window ledger (MINUTE/HOUR/DAY/MONTH) with Redis
  distributed counting and local fallback. **Off by default** (`jairouter.quota.enabled`).
- **Record governance** — call history at three levels: metadata only, desensitized summary, or
  AES-256-GCM-encrypted full content; ADMIN-only decryption endpoint.
- **Observability** — Prometheus metrics, OpenTelemetry tracing, slow-query analysis, error
  tracking, security alerts. Alert outlets are deliberately limited to **Prometheus metrics +
  structured logs** — the repository implements no push channels (email/webhook/IM); wire those up
  in Alertmanager or your log platform.
- **Persistence** — H2 by default; PostgreSQL (with `Flyway` migrations) when `DATABASE_URL`
  points at it; Redis for shared state in multi-replica deployments.
- **Multi-replica** — cross-replica event broadcast, shared rate-limit/quota counters, and a
  distributed lock for scheduled tasks (including the alert cooldown).
- **Semantic cache** — SimHash near-match reuse of non-streaming responses. **Off by default**
  (`jairouter.semantic-cache.enabled`).
- **Console** — ships in the same jar, served at `/admin/`; zh-CN / en-US plus dark and light
  themes.

Not implemented: in-repo notification delivery.
See the [changelog](docs/en/reference/changelog.md) for per-release notes.

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

## Console tour

The console is a Vue 3 SPA served at `/admin/` from the same Spring Boot jar — no separate
frontend container, no extra port to expose. It is bilingual (zh-CN / en-US) and ships light and
dark themes.

The sidebar is **8 groups, 39 menu items, 53 pages**: pages that belong to one menu item are
folded into tabs, and no group holds more than 8 items — so fifty-odd pages never become
fifty-odd rows. Menu visibility is driven by permission codes: a missing code removes the entry
instead of failing with a 403 after it is clicked.

The four sections below are how the console is used day to day. Page-by-page reading, path lookup
and troubleshooting flows live in the [Web Console handbook](docs/en/getting-started/web-console.md).
Screenshots come from a running instance with demo data, so a panel that needs accumulated traffic
shows its empty state, not a mock-up.

### Command Center — one screen for "is anything broken?"

<p align="center">
  <img src="screenshots/dashboard-en-US-light.png" alt="Command Center — service, instance and model counters, system metrics and the governance chain">
</p>

The top row is the inventory of this deployment: services, instances, models, healthy and
unhealthy instances, accounts. Under it sit the system metrics that answer "should I restart
something" — JVM memory, threads, CPU load, uptime, authentication success and failure, audit
events — and a **governance chain** that reports the state of the four stages every request passes
through: rule hits, rate limiting, circuit breakers and load balancing. When a client reports an
error, this is the page that tells you which stage to look at next.

### Services and instances — from triple to real endpoint

<p align="center">
  <img src="screenshots/service-management-en-US-light.png" alt="Service management — service types, adapters and load-balancing strategies">
</p>

A service is the triple (service type, adapter, load-balancing strategy) and covers seven service
types — Chat, Embedding, Rerank, Speech-to-Text, Text-to-Speech, Image Generation and Image
Editing. An instance is one real endpoint: base URL, path, weight, per-instance headers and tags,
and every row carries its own rate limiter and circuit breaker, so a single misbehaving endpoint
can be isolated without touching the rest of the service.

The four-step guide at the top is the intended order for wiring a new backend: configure
adapter → create service → add instance → try it in the playground — adapter mistakes surface at
step one instead of on the first real request. Routing rules are evaluated before any balancing
decision and their hit counter updates in place, so a rule that never matches is visible instead
of quietly dead.

### Observability and records — three record levels, one dashboard

<p align="center">
  <img src="screenshots/call-history-dashboard-en-US-light.png" alt="Call history dashboard — counters, daily trend, model and service-type distribution">
</p>

Every call is recorded at one of three levels — metadata only, desensitized summary, or
AES-256-GCM encrypted full content — and this page reduces that record to what an operator needs:
total and failed requests, success rate, average response time, tokens consumed and tokens per
request, then daily trend, top-10 models, service-type mix and a 24-hour distribution.

Slow query analysis answers where the latency went: slow queries grouped by operation with count,
average, max, min and total duration, next to two figures that are easy to misread — alerts
triggered versus suppressed. Suppression is the cooldown path: a hotspot that keeps breaching the
threshold is counted once and then quietened, so a large suppressed count beside a small triggered
count is the design working.

### Playground and access — prove a model before wiring a client

<p align="center">
  <img src="screenshots/playground-chat-en-US-light.png" alt="AI Playground — chat, embedding, rerank, audio and image benches">
</p>

Five benches behind one model picker: Chat, Embedding, Rerank, Audio Service and Image Service.
Same credentials, same routing and same record governance as a real client, but the request and
the streamed response are visible on the page — the quickest way to prove a newly added instance
answers correctly. Pick a model, or nothing sends; the composer stays disabled until you do.

The client access guide is the page to hand a new team: base URL, the `X-API-Key` header, `GET
/v1/models` and `POST /v1/chat/completions` including its streaming form, then the same call as
`curl` and as the OpenAI Python SDK, each with a copy button. The base URL is read from this
instance's own configuration, so what you copy is what this gateway actually serves.

## Status

**Latest release: v3.9.1 (2026-11-28).** Per-release notes, upgrade notes and known limits live in the
[changelog](docs/en/reference/changelog.md); downloads and container tags are on
[Releases](https://github.com/Lincoln-cn/JAiRouter/releases).

This file deliberately does not repeat release history. It used to, and the copy fell five releases
behind the changelog — one source of truth is the only version that stays true.


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
| Load balancing | ✅ | ✅ | ✅ | ❌ |
| Circuit breaker | ✅ | ❌ | ❌ | ❌ |
| Web console | ✅ | ❌ | ✅ | ❌ |
| Config hot reload | ✅ | ❌ | ✅ | ❌ |
| Version control | ✅ | ❌ | ❌ | ❌ |
| OpenTelemetry | ✅ | ❌ | ❌ | ✅ |

## Contributing

Contributions are welcome — see the [Contributing Guide](https://jairouter.com/en/development/contributing/).

Security issues: please do not open a public issue — report them privately per [SECURITY.md](SECURITY.md).

Before opening a pull request:

```bash
./mvnw test -o                       # 650+ test classes, 5,400+ test methods, must stay green
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

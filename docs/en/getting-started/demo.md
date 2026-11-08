# 5-Minute Demo

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-11-08
> **Applicable Version**: v3.8.0+
> **Author**: Lincoln
<!-- /版本信息 -->

No model service and no JDK required. The repository ships a demo environment: two containers and one script walk the gateway's main path end to end.

It is meant for people who meet this project for the first time and want to see what it actually does before deploying anything. By the end of this page you will have seen how the gateway forwards a request, switches models, reuses a cached response, and removes sensitive fields before they reach the upstream.

## What the demo is made of

| Asset | Purpose |
|-------|---------|
| `deploy/demo/docker-compose.yml` | Starts two containers: the gateway (`jairouter-demo`, port 8080) and a mock upstream (`jairouter-demo-upstream`, port 18080) |
| `deploy/demo/mock-upstream.mjs` | A zero-dependency, OpenAI-compatible upstream that always answers `Hello from mock.`; it stands in for a real model service, so no GPU and no API key are needed |
| `scripts/demo/run-demo.mjs` | Runs four steps against the gateway and prints what it did, what was expected, and what actually happened |

All three live in the repository. Nothing external is required.

## Three steps to run it

Prerequisites: Docker 20.10+ with Docker Compose v2, and Node.js 18+ (the script uses only the standard library, so no `npm install`).

```bash
# 1. Pull the images (this is what gets you v3.8.0 or newer, see the FAQ)
docker compose -f deploy/demo/docker-compose.yml pull

# 2. Start the two containers
docker compose -f deploy/demo/docker-compose.yml up -d

# 3. Wait until both services are healthy (usually 20-60 seconds)
docker compose -f deploy/demo/docker-compose.yml ps

# 4. Run the demo
node scripts/demo/run-demo.mjs
```

Run the script only after both lines in `ps` say `healthy`; with one container healthy the first step fails immediately.

## What the four steps do

| Step | What it does | What to expect |
|------|--------------|----------------|
| 1 Basic call | Logs in, then sends one chat request to `qwen3:4b` | HTTP 200 with an OpenAI-compatible body; it also lists the model names the gateway exposes (7 by default) |
| 2 Same question, two models | Asks the same question through two chat models and compares the answers | The built-in config has a single chat instance, so the script prints a note and skips this step. That is expected, not a failure |
| 3 Repeated request hits the cache | Sends the same question again and compares elapsed time | A clearly faster second call means the cache worked; otherwise the script says the entry may have expired (10 minutes by default) |
| 4 Reversible sanitization | Sends text containing an ID card number and a phone number | The client still gets the original text back; the real check is on the upstream side (next section) |

On success the script prints `通过 N 项，失败 N 项` ("N passed, N failed") and the console URL `http://127.0.0.1:8080/admin/` with account `admin`.

> 💡 You can override the gateway address and credentials: `DEMO_BASE` (default `http://127.0.0.1:8080`), `DEMO_USER` (default `admin`), `DEMO_PASSWORD` (default `DemoOnlyPassw0rd`). If you move the gateway to another port, only `DEMO_BASE` needs to change.

## Reversible sanitization: what changes when you turn it on

The verdict for this step is on the upstream side, not in the client response. That is the whole point of the feature: the upstream never sees the plaintext while the client gets the original values back, so the client always sees the same thing.

Look at the last user message the mock upstream received:

```bash
docker logs jairouter-demo-upstream | grep lastUser
```

```powershell
docker logs jairouter-demo-upstream | Select-String lastUser
```

Two shapes, opposite meanings:

| Log shape | Meaning |
|-----------|---------|
| `lastUser placeholders=[PII_1],[PII_2]` | The upstream only received placeholders; sanitization is active |
| `lastUser plaintext(61B)` | The upstream received plaintext; sanitization is off (which is the default) |

The log prints only the shape and the byte count, never the plaintext. The check does not need it, and writing it into container logs would just create another leak.

The feature is off by default (`jairouter.pii.reversible.enabled: false`). To turn it on, add one line under the `environment` of the `jairouter` service in `deploy/demo/docker-compose.yml` and restart the gateway:

```yaml
    environment:
      JAIRouter_PII_REVERSIBLE_ENABLED: "true"
```

```bash
docker compose -f deploy/demo/docker-compose.yml up -d
node scripts/demo/run-demo.mjs
docker logs jairouter-demo-upstream | grep lastUser
```

Measured on 2026-11-08 with Docker Desktop and the v3.8.0 image: without the flag the line reads `lastUser plaintext(61B)`; after restarting with the variable it reads `lastUser placeholders=[PII_1],[PII_2]`, and all four steps pass.

> ⚠️ Once enabled, the gateway rewrites what it sends upstream. That changes request semantics, so confirm it against your use case before enabling it in production. Keys and rules: [Data Sanitization](../security/data-sanitization.md).

## Settings the demo environment uses

Each setting in `deploy/demo/docker-compose.yml` exists to make the demo start in one command without disturbing your machine:

| Variable | Value | Why |
|----------|-------|-----|
| `SPRING_PROFILES_ACTIVE` | `prod` | Runs the production profile (whose startup gate checks secrets) so the demo does not behave differently from production |
| `JWT_SECRET` | Fixed demo value | The demo does not need a real secret; production must generate its own |
| `INITIAL_ADMIN_PASSWORD` | `DemoOnlyPassw0rd` | Demo account password; override it with `DEMO_PASSWORD` |
| `MODEL_UPSTREAM_BASE_URL` | `mock-upstream:18080` (container name and port) | Points every instance in the built-in `services.yml` at the mock |
| `DATABASE_URL` | `jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1` | In-memory database, nothing is persisted, a restart clears it |
| `MODEL_RATELIMIT_ENABLED` | `false` | Turns rate limiting off so the demo is not interrupted by 429 |
| `MODEL_SERVICES_CHAT_RATELIMIT_ENABLED` | `false` | Same, for the per-service limiter |
| `LOGGING_LEVEL_ORG_UNREAL_MODELROUTER` | `INFO` | Keeps the logs readable |

## How this differs from production

This compose file is a **demo environment, not a production deployment template**. Four things are deliberately unsuitable for production:

1. **The database lives in memory** — a restart wipes configuration, call history, and API keys.
2. **Rate limiting is off** — in production this is one of the gateway's main jobs.
3. **Secrets are public demo values** — production must generate its own `JWT_SECRET` and admin password, otherwise the startup gate refuses to start.
4. **The upstream is a mock** — it always answers the same sentence and says nothing about a real model's quality.

For real deployments see [Production Deployment](../deployment/production.md) and [Docker Deployment](../deployment/docker.md).

## Stopping and cleaning up

```bash
# Stop the containers, keep the images (next up -d is fast)
docker compose -f deploy/demo/docker-compose.yml down

# Remove the network as well
docker compose -f deploy/demo/docker-compose.yml down -v
```

## FAQ

**Port 8080 is already in use** — change the left-hand side of the `jairouter` port mapping in the compose file (for example `8081:8080`), then run `DEMO_BASE=http://127.0.0.1:8081 node scripts/demo/run-demo.mjs`.

**A container stays unhealthy, or step 1 returns 502** — check `docker compose -f deploy/demo/docker-compose.yml ps` and `docker logs jairouter-demo` first. If the log says "下游服务连接失败" ("downstream service connection failed"), the image is most likely too old: early images failed to resolve container names such as `mock-upstream` inside the container network. Run `docker compose -f deploy/demo/docker-compose.yml pull` to get v3.8.0 or newer, then restart.

**Step 2 is always skipped** — the built-in config has a single chat instance, `qwen3:4b`. To compare two models for real, add another chat instance in the web console (pointing at the same mock is enough) or mount your own `services.yml`. Client code does not change, which is the point of a gateway.

**Step 3 shows no speed-up** — cache entries expire after 10 minutes by default, and the first request after a restart never hits. With multiple replicas the request may also land on an instance that has no cached copy. The script says so; it is not a failure.

**Login fails** — the script uses `DemoOnlyPassw0rd` by default. If you changed `INITIAL_ADMIN_PASSWORD` in the compose file, pass the new value through `DEMO_PASSWORD`.

## See also

- [Installation](installation.md) — running from a JAR or from source
- [Quick Start](quick-start.md) — connecting your own model service step by step
- [Web Console](web-console.md) — what you can see at the console URL the demo prints
- [Data Sanitization](../security/data-sanitization.md) — placeholders, TTL, and rule configuration

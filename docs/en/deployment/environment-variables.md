# Environment Variables Reference

This page lists the environment variables JAiRouter supports, when each one takes effect, what happens when it is
missing, and the **real** differences between the shipped profiles. Every entry is taken from the bundled
configuration and the code, with the location that makes it effective in parentheses. Statements marked "measured"
come from real starts of this code base (prod / dev / staging) plus endpoint readings.

## Read first: the three channels a variable can travel

Variables do not all take effect the same way. Knowing the channel is what tells you "why did setting it do nothing".

| Channel | Trigger | Typical variables | Consequence of a mistake |
|---|---|---|---|
| `yml` placeholder | The file explicitly contains `${VAR:default}` | `JWT_SECRET`, `DATABASE_URL`, `GPUSTACK_API_TOKEN`, `JAIROUTER_*` | Falls back to the default, silently |
| Spring relaxed binding | Variable name = property key in upper snake case; works whenever the target property exists | `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`, `JAiROUTER_SECURITY_*`, `SPRING_DATA_REDIS_HOST` | A misspelled name **raises nothing** — it is simply ignored |
| Direct process environment | Code calls `System.getenv("…")` | Only `JWT_SECRET` and `INITIAL_ADMIN_PASSWORD` | This channel honours neither relaxed binding nor aliases |

The difference between the second and third channel is the part most easily missed: `JWT_SECRET` must carry exactly
that name because the startup gate (`StartupSecretKeyChecker` / `StartupSecurityGate`) reads the process environment
instead of the property tree. Since `#674` the four aliases `DEV_JWT_SECRET` / `STAGING_JWT_SECRET` /
`PROD_JWT_SECRET` / `ADMIN_PASSWORD` are gone; setting them now only produces one `warn` line in the startup log.

## Variables you must set (per profile)

| Profile | Variable | If missing | Basis |
|---|---|---|---|
| `prod` / `production` | `JWT_SECRET` (at least 32 characters, not a historical default) | The startup gate throws `IllegalStateException` and the process exits | `StartupSecurityGate.enforce` |
| `prod` / `production` | `INITIAL_ADMIN_PASSWORD` (not `ChangeMeOnFirstStartup123456`) | Same, the process exits | Same |
| `staging` | `JWT_SECRET` (at least 32 characters) | Configuration binding fails: `JwtConfig.secret` violates `@Size(min = 32)` and the process exits with code 1 | `application-staging.yml:33` → `config/auth/jwt.yml:24` |
| `dev` / no profile at all | None | `JWT_SECRET` and the administrator password are generated randomly and printed in the startup log | `config/auth/jwt.yml:24`, `:48` |

Generate a strong `JWT_SECRET` with the published image:

```bash
docker run --rm sodlinken/jairouter:latest --generate-key
```

> One production pattern that never works: setting only `PROD_JWT_SECRET` without `JWT_SECRET`. The gate reads the
> latter, so such a deployment never starts.

## Variables worth setting (depending on the deployment shape)

### Data storage

| Variable | Default | When it becomes mandatory |
|---|---|---|
| `DATABASE_URL` | `jdbc:h2:file:./data/jairouter;…` (`config/config-service/core.yml:18`) | **Recommended for prod**: no profile overrides the datasource, so the default is an H2 file inside the container. `docker-compose*.yml` keeps it with a `./data:/app/data` volume; a bare `docker run` or a Kubernetes pod without a volume loses all data with the container |
| `DATABASE_USERNAME` | `sa` (`:19`) | Supply together with `DATABASE_URL` when pointing at an external database |
| `DATABASE_PASSWORD` | empty (`:20`) | Same |

### Redis (only when at least one Redis-backed switch is on)

| Variable | Default | Notes |
|---|---|---|
| `REDIS_HOST` | `localhost` (`config/security/persistence-base.yml:80`) | Equivalent: `SPRING_DATA_REDIS_HOST` |
| `REDIS_PORT` | `6379` (`:81`) | Equivalent: `SPRING_DATA_REDIS_PORT` |
| `REDIS_PASSWORD` | empty (`:82`) | Set this in production |
| `REDIS_DATABASE` | `0` (`:83`) | Use it to separate key spaces when environments share one Redis |
| `REDIS_ENABLED` | `false` (`:114`, `config/base/monitoring-base.yml:49`) | **Controls only whether the health probe checks Redis**, not whether Redis is used |

The connection timeout is not an environment variable: `spring.data.redis.timeout: 5000ms`
(`persistence-base.yml:84`). The image's `REDIS_TIMEOUT` has no binding (see
[#677](https://github.com/Lincoln-cn/JAiRouter/issues/677)).

### Upstream model services

| Variable | Default | Notes |
|---|---|---|
| `GPUSTACK_API_TOKEN` | empty | Used by the nine `Authorization: "Bearer ${GPUSTACK_API_TOKEN:}"` entries in `config/router/services.yml`. When unset the upstream receives an empty Bearer header and rejects the call itself |

### Multiple replicas

| Variable | Default | Meaning |
|---|---|---|
| `JAIROUTER_REPLICAS` | `0` (`config/common/cluster.yml:16`) | `0` = unknown, probe at runtime; `1` = single replica, skip the shared-state check; `>1` = multi-replica, enforce it |
| `JAIROUTER_SHARED_STATE_MODE` | `WARN` (`:38`) | `OFF` / `WARN` / `FAIL` — silence, warn, or refuse to start |
| `JAIROUTER_SHARED_STATE_FAIL_HEALTH` | `false` (`:47`) | `true` reports shared-state gaps through the health endpoint |
| `JAIROUTER_CLUSTER_EVENTS` | `false` (`:29`) | Real-time event broadcast across replicas; degrades to this replica only, with a warning, when Redis is unavailable |
| `JAIROUTER_RATELIMIT_DISTRIBUTED` | `false` (`config/router/ratelimit.yml:46`) | Whether end-user rate-limit counters live in Redis; Redis failures fail open, each call times out after 50 ms (`:51`) |

Which switches a multi-replica deployment must enable, and what each one changes, is covered in
[Multi-Replica Deployment](multi-replica.md).

### Framework-level switches

These are not `yml` placeholders; they work through Spring relaxed binding. The image already sets several of them
(`Dockerfile:55-64`):

| Variable | Equivalent property | Image default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | active profile | `prod` |
| `SERVER_PORT` | `server.port` | `8080` |
| `JAiROUTER_SECURITY_ENABLED` | `jairouter.security.enabled` | `true` |
| `JAiROUTER_SECURITY_JWT_ENABLED` | `jairouter.security.jwt.enabled` | `true` |
| `JAiROUTER_SECURITY_API_KEY_ENABLED` | `jairouter.security.api-key.enabled` | `true` |
| `JAiROUTER_SECURITY_SANITIZATION_REQUEST_ENABLED` | `jairouter.security.sanitization.request.enabled` | `true` |
| `JAiROUTER_SECURITY_SANITIZATION_RESPONSE_ENABLED` | `jairouter.security.sanitization.response.enabled` | `true` |
| `JAiROUTER_SECURITY_AUDIT_ENABLED` | `jairouter.security.audit.enabled` | `true` |

## Default differences between profiles

The table below shows the same keys across the three profiles. Every difference comes from
`application-{profile}.yml` overriding the base configuration; no environment variable is required to obtain them.

| Key | `default` (none) | `dev` | `staging` | `prod` |
|---|---|---|---|---|
| `jairouter.security.jwt.secret` fallback | `${random.uuid}` (new on every start) | Development literal `dev-secret-key-for-testing-only-32-characters-minimum` | Empty ⇒ `JWT_SECRET` is required | Empty ⇒ `JWT_SECRET` is required |
| `jairouter.security.jwt.issuer` | `jairouter` | `jairouter` | `jairouter-staging` | `jairouter-prod` |
| Administrator password fallback | `${random.uuid}`, printed | `ChangeMeOnFirstStartup123456` | `${random.uuid}`, printed | Empty ⇒ `INITIAL_ADMIN_PASSWORD` is required |
| Built-in test API key | none | present (`test-key-001`) | none | none |
| `jairouter.security.jwt.persistence.enabled` | `false` | `false` | `true` (`application-staging.yml:38-41`) | `false` |
| `jairouter.security.jwt.persistence.redis.enabled` | `false` | `false` | `true` | `false` |
| `jairouter.security.audit.log-level` | `INFO` | `DEBUG` (records request/response bodies) | `INFO` (30-day retention) | `WARN` (no bodies, 90-day retention) |
| `jairouter.security.sanitization.response.enabled` | `false` | `false` | `true` | `true` |
| Log level / file | console | `DEBUG` | `root INFO`, app `INFO`, Spring `WARN` → `logs/jairouter-staging.log` | `root WARN`, app `WARN`, Spring `ERROR` → `logs/jairouter-prod.log` |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus,jairouter-metrics,error-tracking` (`config/base/monitoring-base.yml:16`) | `*` (`application-dev.yml:49`) | Inherits the base value, `show-details: when-authorized` | `health,info,prometheus` (`application-prod.yml:33`) |

## What `jwt.persistence.enabled` actually switches off

This key is easily read as "turn token persistence off". It does not. What it gates is everything **around**
persistence:

| Gated component | Condition | Visible consequence when off |
|---|---|---|
| `JwtTokenLifecycleServiceImpl` | `jwt.persistence.enabled=true` | No status-history (`statusHistory`) writes |
| `JwtTokenExpirationScheduler` | Same | Expired tokens never transition from `ACTIVE` to `EXPIRED` |
| `JwtServiceHealthChecker` | Same | One JWT persistence probe missing from health |
| The two `@ConfigurationProperties` of `JwtPersistenceConfiguration` | Same | `storagePath` / `memory.*` / `cleanupThreshold` have nothing to bind to (and no consumer today) |

**Persistence itself is not controlled by it**: the component that writes and queries tokens is
`JwtServiceConfiguration.storeManagerJwtPersistenceService`, whose condition only looks at
`jairouter.security.jwt.persistence.redis.enabled=false` (`matchIfMissing=true`). Measured on prod with the switch
`false`: after two logins, `GET /api/auth/jwt/blacklist/stats` returns `persistedActiveTokens: 2`, so tokens are in
the store.

Cleanup is an independent switch, `jwt.persistence.cleanup.enabled` (default `true`); the shipped effective values are
**02:00 daily, 30-day retention** (measured: `JWT cleanup service initialized with schedule: 0 0 2 * * ?, retention
days: 30`).

> Note: the `persistence` block inside `config/auth/jwt.yml` is not effective. The values that win come from
> `config/security/persistence-base.yml`; the reason and the full comparison are in
> [#676](https://github.com/Lincoln-cn/JAiRouter/issues/676).

## Removed or ineffective names

| Name | Status |
|---|---|
| `DEV_JWT_SECRET` / `STAGING_JWT_SECRET` / `PROD_JWT_SECRET` | Removed in `#674`; setting them only produces a startup warning |
| `ADMIN_PASSWORD` | Removed in `#674`; the administrator password is `INITIAL_ADMIN_PASSWORD` only |
| `JWT_ALGORITHM` / `JWT_EXPIRATION_MINUTES` / `REDIS_TIMEOUT` | Exported by the image with no binding ([#677](https://github.com/Lincoln-cn/JAiRouter/issues/677)); the real keys are `jairouter.security.jwt.algorithm`, `jairouter.security.jwt.expiration-minutes`, `spring.data.redis.timeout` |
| `ADMIN_API_KEY` / `USER_API_KEY` | Not referenced by the shipped configuration; read only by the yml generated by `scripts/migration/migrate-to-security.ps1` ([#677](https://github.com/Lincoln-cn/JAiRouter/issues/677)) |

## How to check what is effective

- Search the startup log for `JWT cleanup service initialized with schedule:` and
  `The following 1 profile is active:` — that confirms the effective profile and cleanup parameters.
- `GET /actuator/health`: whether Redis is probed depends on `REDIS_ENABLED`.
- `GET /api/auth/jwt/cleanup/stats` and `GET /api/auth/jwt/blacklist/stats` need the `Jairouter_Token` header
  (JAiRouter's own JWTs travel in that header; `Authorization` is reserved for downstream services).
- To enumerate every placeholder and its default: `git grep -n '\${' -- src/main/resources`.

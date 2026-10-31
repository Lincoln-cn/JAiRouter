# Multi-Replica Deployment: Required Shared-State Configuration

Single-replica deployments (the repository default) do not need this page. It targets deployments
that serve traffic from **two or more JAiRouter replicas**, and covers which configuration items
**must** be enabled, what changes when they are not, and how the startup self-check surfaces gaps.

## Why this matters

Authentication, quota, and state persistence all ship with "local-first" default implementations.
With a single replica that is a feature — no external dependency required. With multiple replicas it
degrades each replica into an **isolated single-node instance**:

| Symptom | Root cause |
|---|---|
| A token revoked on replica A is still accepted on replica B | JWT blacklist is maintained per replica |
| A newly created or revoked API key reaches other replicas only after restart | API key cache has no TTL and no cross-replica invalidation |
| Effective allowed volume is roughly `configured value × replica count` | Quota counters live entirely in the JVM |
| State diverges across replicas after a rolling restart | State persistence falls back to local H2/file |

These are not performance issues but **correctness** issues: a stale revocation is a security gap, and
inflated quota is a billing and rate-limiting distortion.

## Prerequisite: one shared `JWT_SECRET`

Beyond the six switches below, multi-replica deployments have one hard prerequisite that is **not**
a switch: `JWT_SECRET` must be **the same value on every replica**.

The default of `jairouter.security.jwt.secret` in `config/auth/jwt.yml` is `${JWT_SECRET:${random.uuid}}`:
when the environment variable is unset, **every replica generates its own random UUID**. A token signed on
replica A then fails verification on replica B, which shows up as **intermittent 401s** (roughly `(n-1)/n`
of requests, with a log line that only says the token is invalid).

The production profile enforces `JWT_SECRET` at startup and is not affected; **dev / staging multi-replica
deployments have no such gate**, so the startup self-check emits a WARN when it sees multi-replica without
`JWT_SECRET`.

## Required configuration

A multi-replica deployment must enable all six items below and point them at a shared Redis:

| Configuration key | Default | Set to | Consequence if left off |
|---|---|---|---|
| `jairouter.security.jwt.blacklist.redis.enabled` | `false` | `true` | Revoked tokens are still accepted on other replicas |
| `jairouter.security.jwt.persistence.redis.enabled` | `false` | `true` | Token state is per replica; restart may lose issued state |
| `jairouter.security.cache.redis.enabled` | `false` | `true` | API key cache stays local; revocations and creates do not propagate |
| `jairouter.quota.distributed.enabled` | `false` | `true` | Quota is counted per replica; allowed volume ≈ configured value × replicas |
| `jairouter.persistence.redis.enabled` | `false` | `true` | State persistence falls back to local H2/file, never shared |
| `jairouter.ratelimit.distributed.enabled` | `false` | `true` | End-user rate-limit counting stays per replica; allowed volume ≈ configured value × replicas |

Enabling distributed quota also requires `jairouter.quota.enabled=true` (the feature's master switch).
End-user rate limiting has its own switch, `jairouter.ratelimit.end-user.enabled` (off by default); the
row above only decides whether **its** counting is shared across replicas.

Connection details come from the `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (and `REDIS_DATABASE`)
environment variables, or from Spring's `spring.data.redis.*`. The switches above only decide
*whether* Redis is used; they no longer read their own `host` / `port` — keys such as
`jairouter.security.jwt.persistence.redis.host` are **no longer read** after the JWT-specific Redis was
removed (the corresponding keys in `config/auth/jwt.yml` and `config/security/persistence-base.yml` were
deleted with them).

## Startup self-check

Once the application is ready it automatically assesses "am I multi-replica, and are the shared-state
switches complete?" No extra configuration is needed for the check to run:

```yaml
jairouter:
  cluster:
    # 0 = unknown, probe the runtime; 1 = explicitly single replica; >1 = multi-replica
    replicas: 3
    shared-state-check:
      enabled: true
      # OFF = skip; WARN = log a warning (default); FAIL = abort startup
      mode: WARN
      # In WARN mode, also report /actuator/health as DOWN
      fail-health: false
      # When a Redis-backed shared-state switch is on, probe Redis at startup (staging/prod only)
      # FAIL (default) = abort startup when unreachable; WARN = log only; OFF = skip
      redis-reachability: FAIL
      redis-timeout-ms: 2000
```

**How multi-replica is determined**: an explicit `replicas > 1` wins; when it is not declared, the
presence of `KUBERNETES_SERVICE_HOST` (injected by Kubernetes) or `POD_NAME` (downward API) marks the
deployment as multi-replica.

`HOSTNAME` is deliberately **not** used as evidence: plain Docker containers set it too, which would
misclassify a single-node deployment. This is also why single-replica deployments are never disturbed
by the check — shared-state switches being off is the normal state there.

Behavior of the three modes:

- `OFF`: no check, no health details.
- `WARN` (default): one warning at startup listing each disabled switch and its consequence. The
  `/actuator/health` status is **not** changed.
- `FAIL`: startup aborts with an `IllegalStateException`; the application never serves traffic while
  misconfigured.

### Dependency reachability (staging / prod only, v3.5.7+)

The check above only looks at *whether switches are on*, not *whether the Redis behind them exists*.
`staging` enables `jairouter.security.jwt.persistence.redis.enabled` by default; with no Redis deployed
the application still starts, and only fails on the first token-state write — by which time users are
already logging in.

So under `staging` / `prod` there is one more startup check: **if any Redis-backed shared-state switch
is enabled, probe `spring.data.redis.host:port`**, and abort startup when it is unreachable
(`jairouter.cluster.shared-state-check.redis-reachability: FAIL` by default).

- The probe is a plain TCP connect with a 2-second default timeout (`redis-timeout-ms`, clamped to
  200 ms–30 s);
- `dev` / `test` **skip** this check (locally it is normal to have switches on without Redis);
- It is skipped when no Redis-backed switch is enabled (`prod` defaults);
- Escape hatches: `WARN` (log only) and `OFF` (skip entirely).

### About the health endpoint

The `sharedState` component of `/actuator/health` continuously exposes the self-check details
(`multiReplica`, `disabledSwitches`). **It stays `UP` by default**, matching the convention set by
`RbacEndpointCoverageHealthIndicator`: a configuration gap is a visibility problem, not an
availability failure.

This matters more than usual here: every Dockerfile and compose file in the repository health-checks
`/actuator/health`, so reporting `DOWN` would mark containers unhealthy and, on Kubernetes, could
trigger a restart loop — while **restarting does not fix a configuration gap**. Set
`fail-health: true` if you do want the orchestrator to see it.

Kubernetes probes use `/actuator/health/liveness` and `/actuator/health/readiness`. Custom health
indicators are not part of those groups, so neither value of `fail-health` affects Kubernetes probes
or restart policy.

## Redis outage behavior is not uniform

Once these switches are on, the consequence of Redis being unavailable **differs per component**.
Know which one you are deploying:

| Component | When Redis is unavailable | Location |
|---|---|---|
| JWT cache / persistence / blacklist | **fail-fast**: bean initialization fails, the application does not start | `RedisJwtCacheConfiguration` |
| Distributed quota counting | **fail-open**: degrades to local counting with a warning | `QuotaProperties.degradeToLocal` (default `true`) |

In other words, **losing Redis can prevent startup entirely** (JWT side), while quota silently
degrades back to per-replica local counting. This is existing behavior, not something introduced
here. Configure Redis persistence and high availability for production, and alert on Redis
unavailability.

## Scheduled tasks under multiple replicas

`@Scheduled` tasks run on every replica, but the correct semantics differ by task category — it is
**not** universally true that they should run only once:

- **Tasks writing files to a shared directory** (security log archiving, metric dumping) name their
  artifacts per replica, removing the race outright rather than locking — so artifacts cannot
  overwrite each other even when Redis is unavailable.
- **Tasks merging this replica's delta into shared storage** (quota snapshot flush, rate limiter state
  sync) **must run on every replica**. Locking them would make the lock holder the only writer and
  silently drop every other replica's delta.
- Tasks whose side effects are externally observable (emitting alerts, daily reports, key rotation)
  take a cross-replica exclusive lock, controlled by
  `jairouter.scheduling.distributed-lock.enabled` (default `true`; degrades to single-instance
  semantics when Redis is not configured).
- **Per-replica cache refresh** (the API key cache) is likewise work every replica must run — see the
  next section.

## Cross-replica cache convergence

API key validation reads each replica's own in-memory mirror, updated only within that process. So
that revocations and creations take effect across replicas, `ApiKeyService` refreshes its cache from
shared storage every `jairouter.security.api-key.cache-refresh-interval-seconds` (default 60 seconds):

- **Convergence bound = the refresh interval.** A key revoked on replica A stops being accepted on
  replica B within one interval.
- The refresh is a **replacement**: it adds keys created on other replicas and removes deleted ones.
- It **does not write back** to storage, avoiding overwriting sibling replicas' changes with this
  replica's stale view (`loadLatestApiKeyConfig` only adds and writes the whole set back at the end,
  so it cannot be used as a refresh).
- When storage has no configuration version, no entries, or entries that all lack a `keyHash`, the
  **existing cache is preserved**: better to let a deleted key survive one more round than to drop
  every valid key and take the service down.
- Set `cache-refresh-enabled: false` to return to the "load at startup only" behavior.

**Measured behavior (2026-10-31, three replicas + shared PostgreSQL 16 + Redis 7)**: a key created on
replica A became visible on replica B after about **28.5 seconds** (inside the refresh window). Revocation
is bounded by the refresh interval in theory, but we observed cases that took **longer than one interval**
(one key was still accepted after 80 seconds; another had not converged within a 300-second window and only
started returning `INVALID_API_KEY` minutes later). When debugging, first confirm the refresh task is running
on every replica (log keyword: "刷新 API Key 缓存") before suspecting configuration.

## Rate-limit key dimension and multi-replica semantics

Rate-limit state lives **in the JVM by default**. Two Redis-backed counting paths exist:

- **end-user rate limiting (v3.5.2)** — with `jairouter.ratelimit.distributed.enabled=true`,
  per-`(API key, end user)` counters are shared across replicas.
- **client-dimension limiting (wired since v3.5.2, off by default, #623)** — `RedisSlidingWindowRateLimiter`
  is now called from the request path, but **every bundled configuration sets `client-ip-enable` to `false`**.

**The per-API-key `rateLimitPerMinute` quota is per replica** and is *not* covered by
`jairouter.ratelimit.distributed.enabled`: each replica keeps its own local token bucket. Measured with
`rateLimitPerMinute=10` on three replicas, 18 concurrent requests (6 per replica) were **all accepted
(18/18, zero 429)**, and no `jairouter:ratelimit:*` keys appeared in Redis. On a single replica the same
key returned `[200×9, 429×5]`. Budget accordingly (or front the deployment with a shared limiter) if the
per-key quota is meant to be a global cap.

Everything else (service / instance / rule level limiters) is still per replica. Under multiple replicas:

> the effective allowed volume for one client is roughly `configured value × replica count`, window
> resets are per replica, and burst shape depends on which pod a request lands on (equivalent to
> handing each pod its own full bucket).

**Current decision for the client dimension: fix the key dimension before relying on counting.** With a
meaningless key, distributed counting would be correct arithmetic over garbage: in a cluster with no
L7 ingress, `remoteAddress` is the **node IP**, not the real client IP — counting by IP means counting
by noise, either merging a whole node into one client or, behind a proxy, merging everything into the
gateway.

**Status of that decision (v3.5.2, #623)**: the call site is wired now, but the key-dimension problem is not
solved — which is why every bundled configuration keeps `client-ip-enable` at `false` and operators must opt in
deliberately once the topology provides real client IPs.

### Configuration

```yaml
model:
  rate-limit:
    client-ip-enable: false         # off by default; enable only when real client IPs reach the pod
    key-dimension: "client-ip"      # client-ip (default) / api-key / tenant
```

How each dimension resolves, and what happens when its value is missing:

| Dimension | Key value | When the value is missing |
|---|---|---|
| `client-ip` | client IP | treated as unavailable → that level is skipped, with a warning |
| `api-key` | caller API key ID | **falls back to the client IP, with a warning** (config does not match traffic) |
| `tenant` | API key ID, falling back to client IP | the fallback is by design, no warning |

**Fallback is explicit, not a silent pass**: limiting still applies after a fallback, just at IP
granularity; only when both the dimension value and the fallback are missing is that level skipped,
and then with a WARN log. Cache keys carry the dimension prefix, so switching dimensions does not
reuse the previous dimension's limiter state.

The `ip-hash` load balancer still falls back to random selection when no client IP is available (there
is no instance to pick otherwise), but that fallback now emits both a WARN log and a
`recordLoadBalancer(..., "random-fallback-no-client-ip")` metric — previously it was only a log, so
operators could not see "affinity is nominal" from metrics.

### Cross-replica counting (optional)

With `jairouter.ratelimit.distributed.enabled=true`, rate-limit counters live in Redis and every replica
shares one counter — allowed volume equals the configured value instead of `configured value × replicas`.

**Today the switch covers two paths**: end-user rate limiting (gated by
`jairouter.ratelimit.end-user.enabled`, off by default) and client-dimension limiting (wired since v3.5.2,
disabled in every bundled configuration). With the switch off, both fall back to a bounded local counter,
so each replica counts on its own.

- **Atomic Lua**: `INCR` → set the TTL on first write → on overflow `DECR` the counter back and return a
  rejection from the same script, so concurrent replicas cannot over-issue.
- **fail-open**: when Redis is unavailable (connection failure/timeout) requests are allowed with a
  warning — rejecting everything is worse than relaxing the limit, matching the quota side's
  `degradeToLocal`.
- **Bounded blocking**: the timeout defaults to 50ms and does not stall the Netty event loop.
- Counters carry a TTL and expire on their own, so no cleanup job is needed.
- With the switch off, a bounded local counter is used (Caffeine; entry cap comes from `local-cache-size`),
  so each replica counts on its own.

### Still not done

Cross-replica counting currently covers **end-user rate limiting** and **client-dimension limiting** (the
latter only when `client-ip-enable` is switched on). The service / instance / rule level limiters remain
per replica, so their allowed volume still scales with the replica count and needs the same treatment.

## Cross-replica real-time event broadcast

The routing-monitor and circuit-breaker-monitor WebSockets take their events from **in-process runtime
callbacks** (routing decisions, breaker state changes). With multiple replicas, a client only ever sees
the events of the replica it is connected to — the monitoring panel then **silently misses events**:
not an error, just incomplete data that looks normal.

With `jairouter.cluster.events.enabled` on, an event produced on one replica is broadcast to the whole
cluster over Redis pub/sub, and every replica merges what it receives into its own WebSocket stream:

- **Loopback filtering**: messages carry the source replica id, and a replica does not re-distribute its
  own events — the local sink already delivered them, and without this filter clients would see
  duplicates.
- **Degradation**: when the Redis connection is unavailable the bus warns once and degrades to
  "this replica only", without blocking startup — an observability feature must not stop the service.
- **Self-healing**: on subscription failure (Redis restart, network blip) it retries with backoff and
  re-subscribes automatically, no application restart needed.
- Each logical channel shares a single Redis subscription, so it does not multiply with client count.

**The health-status SSE is deliberately not broadcast**: its data comes from the shared database
(`ServiceInstanceRepository`) and a snapshot is generated every 5 seconds, so every replica already
serves the same data; broadcasting would only duplicate pushes. The only multi-replica difference is
that a proactive change notification may lag by up to one snapshot period (5 seconds).

Configuration:

```yaml
jairouter:
  cluster:
    events:
      enabled: true      # default false: single-replica deployments need no cross-replica broadcast
```

## Configuration change timing contract

When a management API call modifies service instances or routing rules, the configuration is written to
shared storage as a **new version** and reloaded immediately **only in the replica that handled the
request** (the version save triggers `ModelServiceRegistry.refreshFromMergedConfig`).

- **The replica that made the change**: effective immediately.
- **Every other replica**: **not** notified (there is no configuration broadcast and no periodic pull);
  it keeps the old configuration until it triggers a local reload itself (a management operation on that
  replica) or restarts.

So a configuration change in a multi-replica deployment is **not globally atomic** — there is a window
during which replicas use different rules, and its length depends on when operations restart pods or
trigger a local reload. If that window is unacceptable, pin traffic during changes at the gateway, or
wait for configuration broadcast / periodic pull to be introduced.

## Known gaps

The following are recorded but not yet implemented — multi-replica deployments should be aware:

- **Permission cache invalidation does not actively propagate across pods.** The
  `RolePermissionService` cache (`Caffeine`, 5-minute write expiry) is invalidated only in the local
  process, so role changes take up to 5 minutes to converge — a **bounded** limit, recorded here
  deliberately. The API key cache is now covered by the scheduled refresh above (same bound).
- **Configuration hot reload is not pushed between replicas.** A management API change takes effect
  only in the local process; other replicas converge on restart or on their next local reload, so
  routing and rate-limit rules briefly diverge.
- **Each replica runs DDL itself.** Versioned migrations (the prerequisite for safe concurrent DDL
  across replicas) are not in place yet.

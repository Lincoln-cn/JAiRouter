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

## Required configuration

A multi-replica deployment must enable all five items below and point them at a shared Redis:

| Configuration key | Default | Set to | Consequence if left off |
|---|---|---|---|
| `jairouter.security.jwt.blacklist.redis.enabled` | `false` | `true` | Revoked tokens are still accepted on other replicas |
| `jairouter.security.jwt.persistence.redis.enabled` | `false` | `true` | Token state is per replica; restart may lose issued state |
| `jairouter.security.cache.redis.enabled` | `false` | `true` | API key cache stays local; revocations and creates do not propagate |
| `jairouter.quota.distributed.enabled` | `false` | `true` | Quota is counted per replica; allowed volume ≈ configured value × replicas |
| `jairouter.persistence.redis.enabled` | `false` | `true` | State persistence falls back to local H2/file, never shared |

Enabling distributed quota also requires `jairouter.quota.enabled=true` (the feature's master switch).

Connection details come from each switch's own `host` / `port` / `password`, or from the
`REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` environment variables (preferred, to avoid drift
across several configuration sites).

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

## Rate-limit key dimension and multi-replica semantics

Rate-limit state lives **entirely in the JVM**, and the repository contains **no distributed rate
limiter** (no Redis-backed implementation) — unlike the quota ledger and JWT blacklist, which ship
Redis implementations and only need their switches turned on. Under multiple replicas:

> the effective allowed volume for one client is roughly `configured value × replica count`, window
> resets are per replica, and burst shape depends on which pod a request lands on (equivalent to
> handing each pod its own full bucket).

**Current decision: fix the key dimension first, evaluate distributed counting later.** With a
meaningless key, distributed counting would be correct arithmetic over garbage: in a cluster with no
L7 ingress, `remoteAddress` is the **node IP**, not the real client IP — counting by IP means counting
by noise, either merging a whole node into one client or, behind a proxy, merging everything into the
gateway.

### Configuration

```yaml
model:
  rate-limit:
    client-ip-enable: true          # enables "client-dimension" limiting
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

### Still not done

Direction A (Redis atomic counting) is **not implemented** here: it first needs decisions on the
algorithm (token bucket / sliding window on Redis), the atomicity boundary, and fail-open vs
fail-closed behavior when Redis is unavailable. Fixing the key dimension is what makes that direction
meaningful. Until then, read and load-test your limits as **per-replica quotas**.

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

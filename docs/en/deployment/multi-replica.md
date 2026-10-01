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

## Known gaps

The following are recorded but not yet implemented — multi-replica deployments should be aware:

- **Cache invalidation does not actively propagate across pods.** The `ApiKeyService` in-memory mirror
  has no TTL (a revoked key may keep being accepted on other replicas until restart), and the
  `RolePermissionService` permission cache is valid for 5 minutes (role changes take up to 5 minutes
  to converge). Enabling `jairouter.security.cache.redis.enabled` removes the API key part.
- **Configuration hot reload is not pushed between replicas.** A management API change takes effect
  only in the local process; other replicas converge on restart or on their next local reload, so
  routing and rate-limit rules briefly diverge.
- **Each replica runs DDL itself.** Versioned migrations (the prerequisite for safe concurrent DDL
  across replicas) are not in place yet.

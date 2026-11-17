# Login protection configuration

The login endpoint's failure rate limiter has three knobs: the failure threshold, the counting window, and the lock duration.
The same configuration decides both when a `429` fires and how long the lock lasts.

## Settings

`config/auth/login-guard.yml` (imported alongside `application.yml`):

```yaml
jairouter:
  auth:
    login-bruteforce:
      max-failures: 5          # failures for the same IP + username
      max-failures-per-ip: 30  # failures for the same IP across usernames
      window-minutes: 15       # counting window
      lock-minutes: 15         # lock duration once the threshold is hit
```

The dev profile (`application-dev.yml`) relaxes this to `20 / 100 / 5 / 5` so local debugging is not blocked by five typos.

## How it takes effect: seeded once, then the database wins

On first startup the four values are persisted into the single-row `security_login_guard_config` table
(`config_id = 'default'`, `updated_by = 'system'`). Every later startup reads the row — the same rule as `jwt_accounts`. Therefore:

- **Editing the YAML only affects deployments that have not been initialized yet.** To change thresholds on a running instance, use
  Security → Login Protection in the console, or `PUT /api/security/login-guard`.
- A change is **hot-applied** to the running instance and writes a `LOGIN_GUARD_CONFIG_CHANGED` audit event; no restart needed.
- Already-locked principals are **not** released by relaxing the thresholds (raising a limit is not the same as granting access).

## Operational endpoints

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/security/login-guard` | Effective thresholds plus who changed them and when |
| PUT | `/api/security/login-guard` | Change all four thresholds (all four required) |
| GET | `/api/security/login-guard/locked` | Principals still inside the lock window |
| POST | `/api/security/login-guard/unlock` | Release locks (empty body = everything) |

## Multiple replicas

Failure counters live in **instance memory**, so each replica counts on its own. A locked user shows up in `/locked` only for the
instance that locked them, and releasing them requires calling each instance (or waiting for `lock-minutes` to elapse). This is a
known boundary of the current implementation; counters are not shared across replicas.

## Troubleshooting

A `429` with `RATE_LIMITED_LOGIN` means the threshold was hit; the message includes the remaining time. The matching WARN log line
prints `scope` (`account` = IP + username, `ip` = IP only), `threshold`, `windowMinutes`, and `lockMinutes`.

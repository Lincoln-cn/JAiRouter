# Configuration Validation Rules

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-07-17
> **Git Commit**: ca98a2ee
> **Author**: Lincoln
<!-- /版本信息 -->

---

## Overview

Configuration validation in JAiRouter comes in **two kinds**, with different timing and consequences:

| Timing | Trigger | Validators | Consequence of a finding |
|---|---|---|---|
| **Startup** | `ApplicationReadyEvent` | `ConfigIntegrityValidator` · `SensitiveConfigValidator` | Former: **the application fails to start**; latter: fails **only in strict mode** |
| **Runtime** | when configuration is saved/applied | `ConfigurationValidator` (services & instances) · `SecurityConfigurationValidator` · `MonitoringConfigurationValidator` · `SamplingConfigurationValidator` (tracing) | Decided by each call site (see the inventory below) |

> ⚠️ **This page was rewritten from the code (issue #374).** The previous version listed 7 named rules
> (`rate-limit-capacity`, `circuit-breaker-threshold`, `jwt-expiration`, `thread-pool-size`, …), matching
> `export RATE_LIMIT_*` examples, and a `ConfigurationValidationRule` extension interface — **none of those
> three exist in this repository**. Every statement below can be traced to a `file:line`.

---

## 1. Startup validation

Both startup validators are `ApplicationListener<ApplicationReadyEvent>` beans that run **once the context is
ready** (so a failure shows up as the **process exiting**).

### Master switches

| Property | Default | Scope |
|---|---|---|
| `jairouter.config.validation.enabled` | `true` | Read by both validators; **read slightly differently** (see below) |
| `jairouter.config.validation.strict-mode` | `false` | **Only** affects `SensitiveConfigValidator` |

```yaml
jairouter:
  config:
    validation:
      enabled: true       # false => neither startup validator runs
      strict-mode: false  # true  => the sensitive-value findings become startup failures
```

### 1.1 `ConfigIntegrityValidator` — any error fails the startup

`config/core/validator/ConfigIntegrityValidator.java` (class at `:45`, switch read at `:79-87`).
Findings go into two buckets and then (`:107-112`):

- **any error** ⇒ log each one, then **throw `IllegalStateException("配置校验失败，存在 N 个错误")`**
  ⇒ the application **fails to start**;
- **warnings only** ⇒ logged, **startup is unaffected**.

Rules (value sets and bounds are taken **from the code**, `:129-206`):

| Property | Check | Level |
|---|---|---|
| `server.port` | outside `1`–`65535` | **error** |
| `server.port` | `< 1024` (privileged port, may need root) | warning |
| `model.load-balance.type` | not in `round-robin` · `weighted` · `least-connections` · `ip-hash` · `random` | **error** |
| `model.rate-limit.algorithm` (only when `model.rate-limit.enabled=true`) | not in `token-bucket` · `sliding-window` · `fixed-window` | **error** |
| `model.circuit-breaker.failureThreshold` (only when `model.circuit-breaker.enabled=true`) | `≤ 0` | **error** |
| same | `> 100` (the breaker may be hard to trip) | warning |
| `model.circuit-breaker.timeout` (only when `model.circuit-breaker.enabled=true`) | `≤ 0` | **error** |
| same | `> 300000` (ms; recovery may be slow) | warning |
| `store.type` | not in `jpa` · `redis` · `memory` · `h2` | **error** |
| `monitoring.enabled` | `false` (monitoring is recommended in production) | warning |
| `jairouter.tracing.enabled` | `false` (tracing is recommended in production) | warning |

How it reads the switch: `@Value("${jairouter.config.validation.enabled:true}")` (`:79`); when `false` it logs
one line and returns immediately (`:84-87`).

> 📌 The class javadoc carries its own rule list, but with **smaller value sets than the code** (4 load-balancer
> types / 2 rate-limit algorithms / 2 store types). This page follows the **code**; the javadoc has been
> aligned in the same batch as issue #374.

### 1.2 `SensitiveConfigValidator` — sensitive-value check-up (**warnings only by default**)

`config/core/validator/SensitiveConfigValidator.java`. It is
`@ConditionalOnProperty(name = "jairouter.config.validation.enabled", havingValue = "true", matchIfMissing = true)`
(`:28`) — note the difference from the validator above: **this one is enabled when the switch is absent**.

It checks three **environment variables** (read through Spring placeholders of the form `${JWT_SECRET:}`,
`:33-40`):

| Variable | Missing | Too short | Looks hard-coded |
|---|---|---|---|
| `JWT_SECRET` | strict mode: **error**; otherwise: warning | ≥32 chars recommended; shorter ⇒ **error** (strict) / warning | **error** |
| `INITIAL_ADMIN_PASSWORD` | strict mode: **error**; otherwise: warning (the message states the fallback to the factory-default password, which makes the production profile fail to start) | ≥8 chars recommended; shorter ⇒ warning | warning |
| `GPUSTACK_API_TOKEN` | warning (model services cannot authenticate) | — | warning |

"Looks hard-coded" (`:153-169`): contains `your-` / `placeholder` / `example` / `test` / `demo` / `changeme`,
or equals `secret` / `password` (case-insensitive).

**Failure semantics (easy to misread — this is what the code does)**: the `errors` bucket is printed **and**
turned into an `IllegalStateException` **only when `strict-mode=true`** (`:67-72`). Therefore:

- `strict-mode=false` (default): **only warnings are printed**; findings in the `errors` bucket (for example a
  hard-coded-looking `JWT_SECRET`) are **neither printed nor fatal**;
- `strict-mode=true`: a non-empty `errors` bucket ⇒ each entry is logged with `log.error` and the
  **startup fails**.

> 📌 **Relation to the production startup gate**: under the `prod` / `production` profile, stricter checks on
> `JWT_SECRET` and `INITIAL_ADMIN_PASSWORD` happen **earlier** — `auth/security/config/StartupSecurityGate.java`
> fails fast during the `CommandLineRunner` phase (missing value, factory default, or weak strength all abort
> the process). Production therefore usually **never reaches** this validator; it mainly helps in
> dev/test environments.

---

## 2. Runtime validation (configuration-API path)

These validators are not part of startup; they are wired into **configuration-change** call sites:

| Validator | Call sites | Notes |
|---|---|---|
| `config/core/ConfigurationValidator.java` (`:31`) | `config/controller/ServiceTypeController.java` (injected at `:54`) · `router/factory/ComponentFactory.java` (injected at `:32`) | Validates service / instance / load-balance / rate-limit / basic configuration (`:258` `validateLoadBalanceConfig`, `:281` `validateRateLimitConfig`, `:376` `validateInstanceConfig`, `:434` `validateSingleServiceConfig`, `:482` `validateBasicConfiguration`) |
| `auth/security/config/SecurityConfigurationValidator.java` (`:24`) | `SecurityConfigurationManager` (`:220`) · `ImprovedSecurityConfigurationService` (`:137`) · `SecurityConfigurationBackupService` (`:64`) | Security configuration and backup validation |
| `monitor/monitoring/config/MonitoringConfigurationValidator.java` (`:17`) | monitoring configuration save path | Monitoring configuration validation |
| `monitor/tracing/config/SamplingConfigurationValidator.java` (`:21`) | `TracingConfigManager` (`:182`) · `config/core/ConfigurationService.java` (`:122`) | Tracing sampling-rule validation |

What each call site does with a validation result (reject the save / return an error / just warn) is decided by
that call site; this page is an **inventory** only. Per-rule consequences follow each validator's javadoc and
call-site code.

---

## 3. Disabling validation / enabling strict mode

```yaml
# Turn off both startup validators (not recommended: you lose the startup check-up)
jairouter:
  config:
    validation:
      enabled: false

# Enable strict mode only (let the sensitive-value findings actually block startup)
jairouter:
  config:
    validation:
      strict-mode: true
```

Environment-variable form (Spring Boot relaxed binding — framework behavior, equivalent to the YAML above):

```bash
export JAIRouter_CONFIG_VALIDATION_ENABLED=true
export JAIRouter_CONFIG_VALIDATION_STRICT_MODE=true
```

> ⚠️ `strict-mode` affects **only** `SensitiveConfigValidator`. The errors of `ConfigIntegrityValidator` are
> always fatal for startup; there is no strict-mode switch for them.

---

## 4. Extending validation (the **real** shape)

There is **no** `ConfigurationValidationRule` interface in this repository. To add a startup check, follow the
two real validators:

```java
@Component
public class MyConfigValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(MyConfigValidator.class);

    @Value("${jairouter.config.validation.enabled:true}")
    private boolean validationEnabled;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!validationEnabled) {
            return;
        }
        // collect errors / warnings yourself; to block startup on an error, throw
        // (template: ConfigIntegrityValidator:107-112)
        log.info("✓ custom validation finished");
    }
}
```

**Key points**:

1. Register with `@Component` + `ApplicationListener<ApplicationReadyEvent>` (both real validators do this);
2. Throw `IllegalStateException` when an error must block startup (`ConfigIntegrityValidator`'s approach);
   `log.warn` when it is informational only;
3. Please **read `jairouter.config.validation.enabled`** as well, so the master switch applies to your check.

---

## 5. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Startup fails with a `配置错误` block in the log | `ConfigIntegrityValidator`'s error bucket is non-empty — fix the listed properties (`:108-111` prints the count) |
| Startup fails with `敏感配置校验失败` | `strict-mode=true` and `SensitiveConfigValidator`'s error bucket is non-empty (missing `JWT_SECRET` / too short / hard-coded-looking) |
| `⚠️` lines but no startup failure | the warnings bucket is informational only (both validators) |
| `JWT_SECRET` is set yet reported as missing | the value is read from the **environment** via `${JWT_SECRET:}`; setting an equivalent YAML property does not satisfy it (same as the production gate) |
| Env vars changed but the result did not | both validators run once, at `ApplicationReadyEvent` ⇒ restart the process |
| Want to downgrade validation to a hint | drop `strict-mode` (the sensitive-value check-up warns by default); `ConfigIntegrityValidator` errors cannot be downgraded — fix the configuration or disable everything with `enabled=false` |

---

## Related code

| File | Purpose |
|---|---|
| `config/core/validator/ConfigIntegrityValidator.java` | Startup integrity validation; an error fails startup |
| `config/core/validator/SensitiveConfigValidator.java` | Startup sensitive-value check-up; warnings by default, fatal in strict mode |
| `config/core/ConfigurationValidator.java` | Runtime configuration validation (service/instance/load-balance/rate-limit/basic) |
| `auth/security/config/SecurityConfigurationValidator.java` | Security configuration and backup validation |
| `monitor/monitoring/config/MonitoringConfigurationValidator.java` | Monitoring configuration validation |
| `monitor/tracing/config/SamplingConfigurationValidator.java` | Tracing sampling-rule validation |
| `auth/security/config/StartupSecurityGate.java` · `StartupSecretKeyChecker.java` | Production-profile startup gate (earlier and stricter than the check-up above) |

## Related documentation

- [Configuration reference](index.md)
- [Deployment · Production](../deployment/production.md)
- [Troubleshooting · Common issues](../troubleshooting/common-issues.md)

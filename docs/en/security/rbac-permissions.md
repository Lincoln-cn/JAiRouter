# RBAC Permission Management

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-09-02
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

Since **v2.9.8**, JAiRouter provides a **data-driven RBAC (role-based access control)** system built around the `module:resource:action` permission code as the single permission atom. It chains **permission code → role → JWT → URL permission matrix → menu** into one complete pipeline:

1. **Permission code**: 53 `module:resource:action` codes (`action` ∈ `read` / `write` / `manage` / `view`) drive both backend URL access decisions and frontend menu/route visibility.
2. **Role template**: On startup the system seeds 4 built-in role templates (ADMIN / OPERATOR / USER / VIEWER) into the `role_permissions` table — full seeding when the table is empty, and insert-only additive reconcile when it is not (see "Role Templates").
3. **JWT embedding**: After a successful login, the backend resolves permission codes per role from `role_permissions` and writes them into the JWT `permissions` claim (roles stay in the `roles` claim; permission codes carry no `ROLE_` prefix).
4. **URL permission matrix**: `PermissionRuleRegistry` holds 66 `{HTTP method, URL pattern} → permission code` rules, and `PermissionAuthorizationManager` evaluates every `/api/**` request at the gateway layer.
5. **Menu and routing**: the frontend renders menus from `menu.ts` in a data-driven way (re-grouped from 11 to 8 groups in v2.9.8); `usePermission` filters the menu and the route guard `meta.permissions` blocks unauthorized access.

Core design principles:

- **Data-driven, no method-level annotations**: permission checks for synchronous controllers always go through URL rules; method-level `@PreAuthorize` is forbidden (the RBAC 500 rule — synchronous method annotations do not take effect under the reactive security stack and cause 500 errors).
- **ADMIN bypass**: when a URL rule matches, `ROLE_ADMIN` passes through directly without per-code checks; when a deny decision is required, `ROLE_ADMIN` still short-circuits through.
- **Unmatched endpoints are denied by default (fail-closed)**: `/api/**` endpoints not present in the rule table are governed by the `RbacUnmatchedPolicy` posture, and **the default is `DENY_ALL` (GETs and writes alike are denied; only ADMIN passes)**. The old behaviour can be rolled back via configuration — see "Authorization Decision".
- **Explicit exemption list**: endpoints that intentionally carry no URL rule and only require a login are concentrated in `RbacExemptEndpoints`; every exemption must state a written reason, and the coverage self-check reports EXEMPT and MISSING separately.
- **`/v1/**` is independent of this system**: OpenAI-compatible inference endpoints only require authentication and are not part of the permission matrix; service-level access control is handled by **API Key service-type permissions** at the adapter layer. This RBAC system does not affect existing API Key calls.

## Permission Code System

### Format and Semantics

A permission code has the format `module:resource:action`:

| Part | Description | Values |
|------|-------------|--------|
| `module` | Module | `overview` / `config` / `lb` / `cb` / `rl` / `callhistory` / `monitoring` / `tracing` / `security` / `system` / `ai` / `actuator` |
| `resource` | Resource within the module | See the full list below |
| `action` | Operation type | `read` / `write` / `manage` / `view` |

`action` semantics:

| Value | Semantics | Description |
|-------|-----------|-------------|
| `read` | Read-only | View/query operations without any write side effects |
| `write` | Write | Create, update, delete, enable/disable operations; usually paired with the `read` code of the same resource |
| `manage` | Manage | Management operations for sensitive modules (e.g. `security:*:manage`, `system:*:manage`) |
| `view` | View | Dedicated view action; currently only `callhistory:view`, which sits outside the `:read`/`:write` filters |

### Full Permission Code List (53)

| Module | Permission code | Description |
|--------|-----------------|-------------|
| overview | `overview:dashboard:read` | Overview dashboard |
| config | `config:services:read` / `config:services:write` | Model service configuration read/write |
| config | `config:instances:read` / `config:instances:write` | Instance configuration read/write |
| config | `config:versions:read` / `config:versions:write` | Configuration version read/write |
| config | `config:persistence:read` / `config:persistence:write` | State persistence read/write |
| config | `config:adapters:read` / `config:adapters:write` | Adapter configuration read/write |
| config | `config:rules:read` / `config:rules:write` | Routing rule configuration read/write |
| config | `config:pools:read` / `config:pools:write` | Resource pool configuration read/write |
| config | `config:circuitbreaker:read` / `config:circuitbreaker:write` | Circuit breaker configuration read/write |
| config | `config:callhistory:read` / `config:callhistory:write` | Call history configuration read/write |
| config | `config:cache:read` / `config:cache:write` | Response cache status query / invalidation (read v2.10.2; write v2.9.10) |
| config | `config:quota:read` / `config:quota:write` | Quota ledger configuration read/write |
| config | `config:validation:read` / `config:validation:write` | Configuration validation read/write |
| lb | `lb:monitoring:read` | Load balancer monitoring |
| lb | `lb:config:write` | Load balancer strategy configuration (write) |
| cb | `cb:monitoring:read` | Circuit breaker monitoring |
| cb | `cb:history:read` | Circuit breaker history |
| rl | `rl:monitoring:read` | Rate limiter monitoring |
| callhistory | `callhistory:view` | Call history view |
| monitoring | `monitoring:metrics:read` | Metrics monitoring |
| monitoring | `monitoring:slowquery:read` | Slow query analysis |
| monitoring | `monitoring:tokenusage:read` | Token usage statistics |
| monitoring | `monitoring:modelstats:read` | Model statistics |
| monitoring | `monitoring:routing:read` | Routing monitor |
| monitoring | `monitoring:quota:read` | Quota monitoring |
| monitoring | `monitoring:exceptions:read` | Exception events view |
| monitoring | `monitoring:exceptions:write` | Exception events handling |
| monitoring | `monitoring:config:write` | Monitoring/operations configuration write |
| tracing | `tracing:dashboard:read` | Tracing dashboard |
| tracing | `tracing:search:read` | Tracing search |
| tracing | `tracing:config:manage` | Tracing security configuration management |
| security | `security:apikeys:manage` | API Key management |
| security | `security:jwttokens:manage` | JWT token management |
| security | `security:blacklist:manage` | Blacklist management |
| security | `security:audit:read` | Security audit log (read-only) |
| security | `security:audit:write` | Security audit log (write) |
| security | `security:sanitization:manage` | PII / data sanitization configuration management |
| system | `system:accounts:manage` | Account management |
| system | `system:permissions:manage` | Permission management |
| ai | `ai:playground:use` | AI playground usage |
| actuator | `actuator:admin:manage` | Actuator infrastructure management |

Notes:

- 53 codes in total: the config module contributes 12 resources × read/write = 24 codes; the remaining modules are shown above.
- The order above is the order used by the full list (`GET /api/security/permissions`) and the permission tree in the permission management UI.
- A few codes are naturally unpaired (e.g. `lb:config:write`, `callhistory:view`, `ai:playground:use`) and follow the semantics of their resource rather than a forced read/write pair.
- Some codes exist only for menu/route visibility (e.g. `overview:dashboard:read`, `ai:playground:use`); `ai:playground:use` is currently referenced by no URL rule, and the playground is in fact reached through the exempt `/api/v1/**` path (see "Caller Comparison Matrix").
- `/api/**` endpoints without a registered URL rule are **no longer** allowed through by default; they follow the posture described under "Authorization Decision" (deny by default).

## Role Templates

The system ships 4 built-in role templates (seeded automatically at startup by `RolePermissionSeeder`):

| Role | Permission count | Permission scope | Notes |
|------|:----------------:|------------------|-------|
| ADMIN | 53 | All permission codes | Superset; bypasses URL rules, and full codes are embedded at JWT issuance |
| OPERATOR | 43 | All `:read` + `:write` codes | Excludes `system:*`, `security:*:manage`, `actuator:*`; keeps `security:audit:read` / `security:audit:write`, no `callhistory:view` |
| USER | 28 | Dashboard + config read + full lb/cb/rl + monitoring read (incl. exceptions:read) + tracing dashboard/search + AI playground | Read-oriented; only write code is `lb:config:write`; no call history view |
| VIEWER | 27 | All `:read` codes | Pure read-only role; no `callhistory:view`, `ai:playground:use`, or other non-`:read` codes |

### Role Differences

- **OPERATOR vs ADMIN**: OPERATOR targets day-to-day operations and holds every read/write permission, but excludes system management (`system:*`), security management `manage` codes (`security:apikeys:manage` / `security:jwttokens:manage` / `security:blacklist:manage` / `security:sanitization:manage`) and infrastructure management (`actuator:admin:manage`); `security:audit:read` / `security:audit:write` are kept for OPERATOR.
- **USER vs OPERATOR**: USER targets regular viewers — config **read-only** + full lb/cb/rl (including the only write code, `lb:config:write`) + monitoring read + tracing dashboard/search + AI playground; it has no `manage` codes, no account management, and no call history view.
- **VIEWER vs USER**: VIEWER is the narrowest pure read-only role of the three and keeps only the `:read` codes; it drops USER's only write code `lb:config:write` as well as `ai:playground:use`.
- None of the three non-ADMIN roles gets `callhistory:view` by default (a dedicated view code ending in `:view`, so it falls outside the `:read`/`:write` filters); if needed, grant it to a role in the permission management UI. **In practice `/api/call-history/**` is ADMIN-only.**
- None of the three non-ADMIN roles holds any `security:*:manage` code (only ADMIN does). The 5 `security:*:manage` rules added in phase 2 therefore do not change any non-ADMIN role's access (see "Authorization Decision").

### Additive Reconcile (Insert-Only)

When the `role_permissions` table is **non-empty**, `RolePermissionSeeder` no longer skips it — it performs an **insert-only additive reconcile**:

| Table state | Behaviour |
|-------------|-----------|
| Empty | Full seeding of the 4 role templates (fresh-install path) |
| Non-empty | Per role: if the held code set is a subset of the template, insert only the missing codes that some URL rule requires (`template ∩ rule-required − held`); if the role holds a code outside its template, it is treated as hand-customized and **the whole role is skipped**; a role with no permission rows is skipped as well |

Reconcile performs `INSERT` only — it **never deletes or updates existing rows**. After writing, the `RolePermissionService` permission cache is invalidated so that already-issued JWTs cannot keep a stale permission set during the startup window.

Operator impact: on an already-initialized deployment, missing rule-required codes are backfilled at startup. Verified live on a real database: OPERATOR +9, USER +4, VIEWER +4, ADMIN +10. Hand-customized roles are untouched (skipped entirely).

## Workflow

The full flow of a permission-checked request:

1. **Login and JWT issuance**: the client calls `POST /api/auth/jwt/login`. `AccountManager` verifies credentials (v2.9.8: YAML static accounts take priority, falling back to database accounts — accounts created through the web account management API can log in directly). It then resolves permission codes per role via `RolePermissionService` from the `role_permissions` table (Caffeine cache of 5 minutes; ADMIN short-circuits to the full code list) and embeds them into the JWT `permissions` claim before returning the token.
2. **Token parsing**: the request carries `Authorization: Bearer {token}` (or a custom JWT header). `DefaultJwtTokenValidator` parses the token: the `roles` claim becomes `ROLE_`-prefixed authorities; the `permissions` claim becomes authorities **without** a prefix (the two are isolated so they never collide with API Key `ROLE_*` permissions).
3. **URL rule matching**: `SecurityConfiguration` routes every `/api/**` request to `PermissionAuthorizationManager`, which looks up the required permission code in `PermissionRuleRegistry` by "HTTP method + URL pattern" (first match wins; 66 rules in total).
4. **Permission decision**: follow the three-step decision in the next section.
5. **Result**: granted requests reach the controller; denied requests get a 403.

## Authorization Decision (Fail-Closed)

`PermissionAuthorizationManager` decides every `/api/**` request in three fixed steps:

1. **A `PermissionRuleRegistry` rule matches** → require `ROLE_ADMIN` **or** the required permission code;
2. **No rule matches, but the request hits the explicit exemption list `RbacExemptEndpoints`** → **authenticated only** (same effective behaviour as the old fallback, but now explicit and auditable, with a written reason per entry);
3. **Neither matches (MISSING)** → governed by the unmatched posture; **deny by default** (`ROLE_ADMIN` still short-circuits through).

Decision scenarios:

| Scenario | Decision |
|----------|----------|
| Rule matched + role is ADMIN | Granted (bypass) |
| Rule matched + required permission code present | Granted |
| Rule matched + required permission code missing (and not ADMIN) | Denied (403) |
| No rule matched + exempt list hit | Granted (authenticated only) |
| No rule matched + not exempt (MISSING) | Posture-dependent, denied by default; ADMIN passes |

### Unmatched Posture

Past and present postures:

| Posture | Value | Unmatched writes | Unmatched GETs |
|---------|-------|------------------|----------------|
| legacy fail-open | `AUTHENTICATED` | allow (login only) | allow (login only) |
| phase 2 | `DENY_WRITES` | deny | allow (login only) |
| **current default** | `DENY_ALL` | deny | **deny** |

Configuration:

| Key | Values | Notes |
|-----|--------|-------|
| `jairouter.security.rbac.unmatched-policy` | `AUTHENTICATED` \| `DENY_WRITES` \| `DENY_ALL` | **default `DENY_ALL`** |
| `jairouter.security.rbac.write-fail-closed.enabled` (legacy) | `true` \| `false` | **consulted only when `unmatched-policy` is absent**: `false`→`AUTHENTICATED`, `true`→`DENY_WRITES`; when both are present the new key wins, so they can never contradict |

**Rollback without a code change**: set `jairouter.security.rbac.unmatched-policy=AUTHENTICATED` (full legacy behaviour) or `=DENY_WRITES` (phase-2 posture).

### Coverage Self-Check and Observability

- At startup the RBAC endpoint coverage self-check (`RbacEndpointCoverageChecker`) classifies every mapped `/api/**` endpoint as **covered** (rule matched), **EXEMPT** (exemption matched), or **MISSING** (actionable gap). When MISSING > 0, a startup **WARN** lists the uncovered endpoints.
- `/actuator/health` exposes a `rbacEndpointCoverage` detail entry (`RbacEndpointCoverageHealthIndicator`) that reports **EXEMPT vs MISSING separately** (`exemptCount` / `missingCount` / `exemptEndpoints` / `missingEndpoints`). The health status stays **UP** — the entry is informational only and must not trigger probe restarts.

### Explicit Exemption List

Endpoints in `RbacExemptEndpoints` are **intentionally unregistered** "authenticated-only by design" endpoints. They are either self-service endpoints (method-level `@PreAuthorize` already constrains ownership) or a service proxy surface (authorization is the API-Key service-type permission + quota, not the URL permission matrix). Each entry carries a written reason in source and is reviewable:

| Method + path | Reason |
|---------------|--------|
| `write /api/v1/**` (POST/PUT/DELETE/PATCH) | AI service proxy surface (chat / embeddings / rerank / audio / images): authorization is the API-Key service-type permission + quota, **not** the URL matrix. API-Key principals carry only `ROLE_<SERVICE>`; registering a code rule here would 403 every API-Key service call |
| `POST /api/auth/jwt/refresh` | Self-service refresh of one's own token; available to any logged-in user, no ownership-escalation surface (the token is the identity) |
| `POST /api/auth/jwt/revoke` | Self-service revoke of one's own token; method-level `@PreAuthorize(hasRole('ADMIN') or authentication.name == #request.userId)` already constrains ownership |
| `GET /api/auth/jwt/tokens` | Self-service token list; method-level `@PreAuthorize` constrains ownership; registering a code rule would break ordinary users managing their own tokens |
| `GET /api/auth/permissions` | The caller's own permission codes; console menu rendering depends on it |
| `GET /api/models` | Model catalogue, public read for UI/clients, no sensitive operation surface |
| `POST /api/token-usage/record` | External token-usage ingest API: in-repo recording goes through the in-process `TokenUsageRecorder`, not this endpoint; callers may be API-Keys, and a code rule would block ingestion |
| `POST /api/token-usage/record/batch` | Batch token-usage ingest: same as above |

### URL Rules Added in Phase 2

The following 5 endpoints gained explicit URL rules in phase 2 (to close coverage gaps):

| Endpoint | Required permission code |
|----------|--------------------------|
| `GET /api/auth/jwt/blacklist/stats` | `security:blacklist:manage` |
| `GET /api/auth/jwt/cleanup/stats` | `security:jwttokens:manage` |
| `POST /api/auth/jwt/cleanup` | `security:jwttokens:manage` |
| `POST /api/auth/jwt/revoke/batch` | `security:jwttokens:manage` |
| `GET /api/auth/jwt/tokens/{tokenId}` | `security:jwttokens:manage` |

**This did not reduce anyone's access**: these 5 endpoints already carried method-level `@PreAuthorize('ADMIN')`, none of the four role templates contains a `security:*:manage` code (only ADMIN does), and ADMIN bypasses anyway.

## Caller Comparison Matrix

The table below maps five caller classes against capability groups under the current posture (`DENY_ALL`):

| Capability group | ROLE_ADMIN JWT | non-ADMIN JWT (OPERATOR/USER/VIEWER) | API-Key (`X-API-Key`) | anonymous |
|------------------|:--------------:|:------------------------------------:|:---------------------:|:---------:|
| `/v1/**` native inference surface (`GET /v1/models`, `POST /v1/chat/completions`, `POST /v1/embeddings`, `POST /v1/rerank`, `POST /v1/messages`, `POST /v1/messages/count_tokens`) | ✔ (login only) | ✔ (login only) | ✔ (API-Key service-type permission + quota) | ✘ |
| `/v1/debug/**` (exists only when `jairouter.debug.endpoints.enabled=true`, default off) | ✔ (login only) | ✔ (login only) | ✔ (login only) | ✘ |
| Exemption-list endpoints (`/api/v1/**` writes, JWT self-service, `/api/auth/permissions`, `/api/models`, token-usage ingest) | ✔ | ✔ (login only) | ✔ (login only) | ✘ |
| Rule-matched `/api/**` (config/monitoring/security, etc.) | ✔ (bypass) | ✔ per role permission codes (see role templates) | **✘ always 403** | ✘ |
| Unmatched and non-exempt `/api/**` (MISSING) | ✔ (short-circuit) | **✘ 403 (default DENY_ALL)** | **✘ 403** | ✘ |
| `/api/call-history/**` | ✔ (`callhistory:view`) | ✘ (no non-ADMIN role holds `callhistory:view`; ADMIN-only in practice) | ✘ | ✘ |
| `/api/model-stats/**` | ✔ (`hasRole('ADMIN')`) | ✘ | ✘ | ✘ |
| `/actuator/**` (except health/info/prometheus) | ✔ (`hasRole('ADMIN')`) | ✘ | ✘ | ✘ |
| `/api/health-status/**`, `/ws/**` | ✔ (login only) | ✔ (login only) | ✘ (JWT-shaped) | ✘ |
| `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | ✔ | ✔ | ✔ | ✔ (permitAll) |
| `/admin/**` (static SPA), `/favicon.ico` | ✔ | ✔ | ✔ | ✔ (permitAll) |
| `POST /api/auth/jwt/login`, `POST /api/auth/jwt/validate` | ✔ | ✔ | ✔ | ✔ (permitAll) |
| `/swagger-ui/**`, `/v3/api-docs/**`, `/webjars/**` | ✔ | ✔ | ✔ | login required by default; permitAll only when `jairouter.security.docs-public=true` |

What API-Key callers must know (external integrators):

- API-Key principals carry **ONLY `ROLE_<SERVICE>`** authorities (`ApiKeyAuthentication` uppercases the permission list and prefixes `ROLE_`); `ApiKeyService.validatePermissions` accepts only `ServiceTypeConstants` service types (`chat` / `embedding` / `rerank` / `tts` / `stt` / `imgGen` / `imgEdit`); invalid values are dropped, and legacy `READ` expands to all service types.
- Therefore an API-Key can **never** hold `ROLE_ADMIN` and never holds a permission code ⇒ **every rule-matched `/api/**` path is 403 for API-Keys**, and after phase 3 unmatched `/api/**` paths are 403 too.
- The usable API-Key surface is: **all of `/v1/**`** (`SecurityConfiguration` maps `/v1/**` to `authenticated()` — NOT the permission manager) plus the exemption set above.
- The `/v1/**` native surface (OpenAI + Anthropic, six endpoints) is **unaffected by all three changes** — existing integrations need no modification.
- `/v1/debug/**` exists only when `jairouter.debug.endpoints.enabled=true` (default off) and requires only authentication ⇒ if enabled, any authenticated principal including an API-Key can call it. **Keep it off in production.**

What non-ADMIN roles must know:

- USER has exactly one write code: `lb:config:write`; VIEWER has only `:read` codes.
- No non-ADMIN role holds any `security:*:manage` code; no non-ADMIN role holds `callhistory:view`.
- `ai:playground:use` is currently referenced by **no** URL rule (it is used by menu/permission plumbing only), so the playground is reachable via the exempt `/api/v1/**` path regardless.

## Management API

Permission management exposes 3 management endpoints plus 1 current-user endpoint (`PermissionManagementController`):

| Method | Path | Description | Required permission |
|--------|------|-------------|---------------------|
| GET | `/api/security/permissions` | All permission codes (53, in catalog order) | `system:permissions:manage` |
| GET | `/api/security/permissions/roles` | All roles with their permission codes (role name → code list) | `system:permissions:manage` |
| PUT | `/api/security/permissions/roles/{roleName}` | Replace the permission code set of a role wholesale | `system:permissions:manage` |
| GET | `/api/auth/permissions` | Permission codes of the current user (ADMIN gets the full 53) | Any authenticated user (exemption list) |

> `/api/security/permissions/**` is protected by a URL rule (`system:permissions:manage`); `GET /api/auth/permissions` sits in the exemption list and only requires a login (console menu rendering depends on it).

### 1. Login to Obtain a Token

```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "admin123"}'
```

`data.token` in the response is the JWT (it embeds the `roles` and `permissions` claims).

### 2. Get All Permission Codes

```bash
curl http://localhost:8080/api/security/permissions \
     -H "Authorization: Bearer {token}"
```

The `data` field of the response is an array of the 53 permission codes (same order as the full catalog).

### 3. Get Role Permissions

```bash
curl http://localhost:8080/api/security/permissions/roles \
     -H "Authorization: Bearer {token}"
```

The `data` field is an object: `{"ADMIN": [...], "OPERATOR": [...], ...}`.

### 4. Update Role Permissions (Wholesale Replacement)

```bash
curl -X PUT http://localhost:8080/api/security/permissions/roles/OPERATOR \
     -H "Authorization: Bearer {token}" \
     -H "Content-Type: application/json" \
     -d '["config:services:read", "config:services:write", "monitoring:metrics:read"]'
```

- `roleName` is case-insensitive (stored in uppercase); the request body is a JSON array of permission codes that replaces the role's permissions wholesale.
- An invalid role name returns 400 (`INVALID_ROLE`); an invalid permission code returns 400 (`INVALID_PERMISSION`).
- After a successful update the server-side permission cache is cleared, but **already-issued JWTs are not updated** — permission changes take effect only after the user logs in again.

### 5. Get Current User Permissions

```bash
curl http://localhost:8080/api/auth/permissions \
     -H "Authorization: Bearer {token}"
```

ADMIN receives the full 53 codes; other roles receive the codes from their JWT `permissions` claim.

## Permission Management UI

The "System Management → Permission Management" page (route `/system/permissions`, requires `system:permissions:manage`) provides graphical permission configuration:

1. **Role dropdown**: select ADMIN / OPERATOR / USER / VIEWER; the role's current permission codes are loaded and reflected in the tree.
2. **Permission code tree**: 53 codes grouped by module as a checkable tree (leaf nodes are permission codes; parent nodes toggle automatically).
3. **Save permissions**: clicking "Save" calls `PUT /api/security/permissions/roles/{roleName}` to replace the role's permissions wholesale.
4. **Effect notice**: after saving, the UI reminds you that "permission changes take effect only after re-login" — permissions are embedded in the JWT; the server-side role-permission cache expires in about 5 minutes (it only affects tokens issued by later logins).

> The permission management page lives in the System Management menu group; accounts (YAML static accounts / database accounts created via the web account management API) are maintained under "System Management → Account Management".

## Menu and Route Guarding

v2.9.8 re-grouped the web menu from 11 groups into **8 groups with 34 items**, driven by `menu.ts` plus permission filtering:

| Menu group | Items | Menu items (permission code) |
|------------|:-----:|------------------------------|
| Overview | 1 | Dashboard (`overview:dashboard:read`) |
| Model Services | 4 | Service Management / Instance Management / Version Management / Adapter Management (corresponding `config:services|instances|versions|adapters:read`) |
| Traffic Governance | 8 | Routing Rules (`config:rules:read`) / Load Balancer Monitoring (`lb:monitoring:read`) / Load Balancer Strategy (`lb:config:write`) / Circuit Breaker Monitoring (`cb:monitoring:read`) / Circuit Breaker History (`cb:history:read`) / Circuit Breaker Config (`config:circuitbreaker:read`) / Rate Limiter Monitoring (`rl:monitoring:read`) / Resource Pools (`config:pools:read`) |
| Data Records | 6 | Call History Dashboard, Call List (`callhistory:view`) / Token Usage (`monitoring:tokenusage:read`) / Slow Calls (`monitoring:slowquery:read`) / Exception Event Management, Exception Statistics (no permission code — visible to every authenticated user) |
| Distributed Tracing | 3 | Tracing Dashboard (`tracing:dashboard:read`) / Tracing Search (`tracing:search:read`) / Tracing Config (`tracing:config:manage`) |
| Security Management | 4 | API Key Management / JWT Token Management / Blacklist Management (`security:apikeys|jwttokens|blacklist:manage`) / Audit Logs (`security:audit:read`) |
| System Management | 3 | Account Management (`system:accounts:manage`) / Permission Management (`system:permissions:manage`) / State Persistence (`config:persistence:read`) |
| AI Playground | 5 | Chat Test / Embedding / Rerank / Audio / Image (all `ai:playground:use`) |

Visibility control mechanisms:

- **Menu filtering**: each item in `menu.ts` may carry a `permission` field (read-semantics codes); `usePermission` (which delegates to `hasPermission` in the user store) filters items out, and a group disappears entirely when all of its items are filtered. Items without a `permission` field stay visible to every authenticated user.
- **Route guard**: the route `meta.permissions` declares the required permission code array; `router.beforeEach` requires **all** of them (ADMIN always passes) and redirects to the dashboard otherwise.
- **Empty-permission compatibility**: when a token was issued by an older version (no `permissions` claim) or the roles are not registered in `role_permissions`, the frontend permission list is empty and imposes **no restriction** on menus/routes, so the menu does not vanish. Note this is a **frontend** compatibility behaviour (the frontend cannot evaluate URL rules); the backend now fail-closes unmatched `/api/**` paths — the two are independent.

## Upgrade and Migration Notes

### What a non-ADMIN console user loses

**Nothing on today's endpoint set.** The coverage self-check currently reports **MISSING = 0** (every mapped `/api/**` endpoint either matches a URL rule or sits in the explicit exemption list), so anything a non-ADMIN user can reach today stays reachable under `DENY_ALL`. Confirm via the startup self-check log and the `rbacEndpointCoverage` entry on `/actuator/health`.

### API-Key integrator checklist

1. Any integration hitting **unregistered `/api/**` paths** (as opposed to `/v1/**`) will now receive 403 (under the `DENY_ALL` default).
2. Remediation: move to the `/v1/**` native surface, or get the path added to the `RbacExemptEndpoints` exemption list (a written reason is required and enters code review).
3. The `/v1/**` native inference surface (OpenAI + Anthropic, six endpoints) is **unaffected** — no changes needed.

### How to verify after upgrade

1. Watch the startup WARN: when MISSING > 0, the log lists the uncovered endpoints (EXEMPT is INFO-only and needs no action).
2. Hit `/actuator/health` and inspect `rbacEndpointCoverage` — `exemptCount` / `missingCount` / `exemptEndpoints` / `missingEndpoints` (EXEMPT and MISSING reported separately; health stays UP, informational only).

### Rollback knobs (no code change)

| Target | Configuration |
|--------|---------------|
| Full legacy fail-open | `jairouter.security.rbac.unmatched-policy=AUTHENTICATED` |
| Phase-2 posture (writes fail-closed, GETs fail-open) | `jairouter.security.rbac.unmatched-policy=DENY_WRITES` |

The legacy key `jairouter.security.rbac.write-fail-closed.enabled` is consulted only when `unmatched-policy` is absent (`false`→`AUTHENTICATED`, `true`→`DENY_WRITES`); when both are present the new key wins, so they can never contradict.

### Other compatibility notes

1. **Permission changes require re-login**: permission codes are embedded in the JWT; after a role's permissions are changed via UI/API, users must **log in again** to pick up the new permissions. After the first upgrade, it is recommended that all users log in again to obtain tokens that carry the `permissions` claim.
2. **Legacy token compatibility**: old tokens have no `permissions` claim and only carry `ROLE_*` authorities. The frontend imposes no restriction when the permission list is empty (menus do not disappear); on the backend, non-ADMIN legacy tokens get a 403 on URL-rule-protected endpoints — a re-login resolves this.
3. **`role_permissions` additive reconcile (insert-only)**: the 4 role templates are seeded when the table is empty; when non-empty, only missing rule-required codes are inserted (see "Additive Reconcile"), never deleting or updating existing rows; hand-customized roles are skipped entirely.
4. **No method-level `@PreAuthorize` on synchronous controllers**: permission decisions go through URL rules; do not add method-level annotations when adding/modifying synchronous controllers, or you will hit the RBAC 500 rule.
5. **API Key and `/v1/**` are unaffected**: authentication of inference endpoints and API Key service-type permissions stay unchanged and remain independent of the web RBAC system.

## Related Docs

- [JWT Authentication](jwt-authentication.md) - issuance and validation of the `roles` / `permissions` claims
- [API Key Management](api-key-management.md) - service-type permissions (`/v1/**` endpoints)
- [Audit Log Management](audit-log-management.md) - security audit events
- [Security Blacklist](blacklist-management.md) - account and token banning

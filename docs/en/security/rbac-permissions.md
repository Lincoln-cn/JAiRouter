# RBAC Permission Management

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-09-02
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

Since **v2.9.8**, JAiRouter provides a **data-driven RBAC (role-based access control)** system built around the `module:resource:action` permission code as the single permission atom. It chains **permission code → role → JWT → URL permission matrix → menu** into one complete pipeline:

1. **Permission code**: 44 `module:resource:action` codes (`action` ∈ `read` / `write` / `manage`) drive both backend URL access decisions and frontend menu/route visibility.
2. **Role template**: On startup the system seeds 4 built-in role templates (ADMIN / OPERATOR / USER / VIEWER) into the `role_permissions` table — idempotent seeding that never overwrites existing data.
3. **JWT embedding**: After a successful login, the backend resolves permission codes per role from `role_permissions` and writes them into the JWT `permissions` claim (roles stay in the `roles` claim; permission codes carry no `ROLE_` prefix).
4. **URL permission matrix**: `PermissionRuleRegistry` holds 36 `{HTTP method, URL pattern} → permission code` rules, and `PermissionAuthorizationManager` evaluates every `/api/**` request at the gateway layer.
5. **Menu and routing**: the frontend renders menus from `menu.ts` in a data-driven way (re-grouped from 11 to 8 groups in v2.9.8); `usePermission` filters the menu and the route guard `meta.permissions` blocks unauthorized access.

Core design principles:

- **Data-driven, no method-level annotations**: permission checks for synchronous controllers always go through URL rules; method-level `@PreAuthorize` is forbidden (the RBAC 500 rule — synchronous method annotations do not take effect under the reactive security stack and cause 500 errors).
- **ADMIN bypass**: when a URL rule matches, `ROLE_ADMIN` passes through directly without per-code checks.
- **Unregistered endpoints fall back to `authenticated`**: `/api/**` endpoints not present in the rule table only require a login, preserving backward compatibility.
- **`/v1/**` is independent of this system**: OpenAI-compatible inference endpoints only require authentication and are not part of the permission matrix; service-level access control is handled by **API Key service-type permissions** at the adapter layer. This RBAC system does not affect existing API Key calls.

## Permission Code System

### Format and Semantics

A permission code has the format `module:resource:action`:

| Part | Description | Values |
|------|-------------|--------|
| `module` | Module | `overview` / `config` / `lb` / `cb` / `rl` / `callhistory` / `monitoring` / `tracing` / `security` / `system` / `ai` / `actuator` |
| `resource` | Resource within the module | See the full list below |
| `action` | Operation type | `read` / `write` / `manage` |

`action` semantics:

| Value | Semantics | Description |
|-------|-----------|-------------|
| `read` | Read-only | View/query operations without any write side effects |
| `write` | Write | Create, update, delete, enable/disable operations; usually paired with the `read` code of the same resource |
| `manage` | Manage | Management operations for sensitive modules (e.g. `security:*:manage`, `system:*:manage`) |

### Full Permission Code List (44)

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
| config | `config:cache:write` | Response cache invalidation (v2.9.10, write-only) |
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
| tracing | `tracing:dashboard:read` | Tracing dashboard |
| tracing | `tracing:search:read` | Tracing search |
| tracing | `tracing:config:manage` | Tracing security configuration management |
| security | `security:apikeys:manage` | API Key management |
| security | `security:jwttokens:manage` | JWT token management |
| security | `security:blacklist:manage` | Blacklist management |
| security | `security:audit:read` | Security audit log (read-only) |
| system | `system:accounts:manage` | Account management |
| system | `system:permissions:manage` | Permission management |
| ai | `ai:playground:use` | AI playground usage |
| actuator | `actuator:admin:manage` | Actuator infrastructure management |

Notes:

- 44 codes in total: the config module contributes 10 resources × read/write = 20 codes plus the write-only `cache` code (1) = 21; the remaining modules are shown above.
- The order above is the order used by the full list (`GET /api/security/permissions`) and the permission tree in the permission management UI.
- A few codes are naturally unpaired (e.g. `lb:config:write`, `callhistory:view`, `ai:playground:use`) and follow the semantics of their resource rather than a forced read/write pair.
- Some codes exist only for menu/route visibility (e.g. `overview:dashboard:read`); whether the corresponding endpoint is protected by a URL rule is described in the "Workflow" section — endpoints without a registered URL rule remain accessible to any authenticated user.

## Role Templates

The system ships 4 built-in role templates (seeded automatically and idempotently at startup): seeding runs only when the `role_permissions` table is empty and is skipped otherwise, so existing configurations are never overwritten.

| Role | Permission count | Permission scope | Notes |
|------|:----------------:|------------------|-------|
| ADMIN | 44 | All permission codes | Superset; bypasses URL rules, and full codes are embedded at JWT issuance |
| OPERATOR | 35 | All `:read` + `:write` codes | Excludes `system:*`, `security:*:manage`, `actuator:*`; keeps `security:audit:read`, no `callhistory:view` |
| USER | 24 | Dashboard + config read + full lb/cb/rl + monitoring read + tracing dashboard/search + AI playground | Read-oriented; only write code is `lb:config:write`; no call history view |
| VIEWER | 23 | All `:read` codes | Pure read-only role; no `callhistory:view`, `ai:playground:use`, or other non-`:read` codes |

### Role Differences

- **OPERATOR vs ADMIN**: OPERATOR targets day-to-day operations and holds every read/write permission, but excludes system management (`system:*`), security management `manage` codes (`security:apikeys:manage` / `security:jwttokens:manage` / `security:blacklist:manage`) and infrastructure management (`actuator:admin:manage`); `security:audit:read` (read-only audit log) is kept for OPERATOR.
- **USER vs OPERATOR**: USER targets regular viewers — config **read-only** + full lb/cb/rl (including the only write code, `lb:config:write`) + monitoring read + tracing dashboard/search + AI playground; it has no `manage` codes, no account management, and no call history view.
- **VIEWER vs USER**: VIEWER is the narrowest pure read-only role of the three and keeps only the `:read` codes; it drops USER's only write code `lb:config:write` as well as `ai:playground:use`.
- None of the three non-ADMIN roles gets `callhistory:view` by default (a dedicated view code independent of read/write); if needed, grant it to a role in the permission management UI.

## Workflow

The full flow of a permission-checked request:

1. **Login and JWT issuance**: the client calls `POST /api/auth/jwt/login`. `AccountManager` verifies credentials (v2.9.8: YAML static accounts take priority, falling back to database accounts — accounts created through the web account management API can log in directly). It then resolves permission codes per role via `RolePermissionService` from the `role_permissions` table (Caffeine cache of 5 minutes; ADMIN short-circuits to the full code list) and embeds them into the JWT `permissions` claim before returning the token.
2. **Token parsing**: the request carries `Authorization: Bearer {token}` (or a custom JWT header). `DefaultJwtTokenValidator` parses the token: the `roles` claim becomes `ROLE_`-prefixed authorities; the `permissions` claim becomes authorities **without** a prefix (the two are isolated so they never collide with API Key `ROLE_*` permissions).
3. **URL rule matching**: `SecurityConfiguration` routes every `/api/**` request to `PermissionAuthorizationManager`, which looks up the required permission code in `PermissionRuleRegistry` by "HTTP method + URL pattern" (first match wins; 36 rules in total).
4. **Permission decision**:
   - Rule matched: `ROLE_ADMIN` passes through; otherwise the authentication object must carry the required permission code authority;
   - No rule matched: fall back to `authenticated` (login required only).
5. **Result**: granted requests reach the controller; denied requests get a 403.

| Scenario | Decision |
|----------|----------|
| No URL rule matched | Granted if authenticated (fallback) |
| Rule matched + role is ADMIN | Granted (bypass) |
| Rule matched + required permission code present | Granted |
| Rule matched + required permission code missing (and not ADMIN) | Denied (403) |

## Management API

Permission management exposes 3 management endpoints plus 1 current-user endpoint (`PermissionManagementController`):

| Method | Path | Description | Required permission |
|--------|------|-------------|---------------------|
| GET | `/api/security/permissions` | All permission codes (43, in catalog order) | `system:permissions:manage` |
| GET | `/api/security/permissions/roles` | All roles with their permission codes (role name → code list) | `system:permissions:manage` |
| PUT | `/api/security/permissions/roles/{roleName}` | Replace the permission code set of a role wholesale | `system:permissions:manage` |
| GET | `/api/auth/permissions` | Permission codes of the current user (ADMIN gets the full 43) | Any authenticated user |

> `/api/security/permissions/**` is protected by a URL rule (`system:permissions:manage`); `GET /api/auth/permissions` has no registered URL rule and falls back to `authenticated`.

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

The `data` field of the response is an array of the 44 permission codes (same order as the full catalog).

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

ADMIN receives the full 44 codes; other roles receive the codes from their JWT `permissions` claim.

## Permission Management UI

The "System Management → Permission Management" page (route `/system/permissions`, requires `system:permissions:manage`) provides graphical permission configuration:

1. **Role dropdown**: select ADMIN / OPERATOR / USER / VIEWER; the role's current permission codes are loaded and reflected in the tree.
2. **Permission code tree**: 44 codes grouped by module as a checkable tree (leaf nodes are permission codes; parent nodes toggle automatically).
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
- **Empty-permission compatibility**: when a token was issued by an older version (no `permissions` claim) or the roles are not registered in `role_permissions`, the frontend permission list is empty and imposes **no restriction** on menus/routes, so the menu does not vanish (aligned with the backend fallback of "unregistered URL rules → authenticated").

## Upgrade Notes

Compatibility notes when upgrading from a version before v2.9.8:

1. **Unregistered endpoints fall back to `authenticated` (smooth compatibility)**: in older versions, `/api/**` only required authentication except for a few hardcoded ADMIN paths. In the new version, endpoints not registered in `PermissionRuleRegistry` behave the same (accessible once logged in), so upgrades are smooth without any configuration change.
2. **Permission changes require re-login**: permission codes are embedded in the JWT; after a role's permissions are changed via UI/API, users must **log in again** to pick up the new permissions. After the first upgrade, it is recommended that all users log in again to obtain tokens that carry the `permissions` claim.
3. **Legacy token compatibility**: old tokens have no `permissions` claim and only carry `ROLE_*` authorities. The frontend imposes no restriction when the permission list is empty (menus do not disappear); on the backend, non-ADMIN legacy tokens get a 403 on URL-rule-protected endpoints — a re-login resolves this.
4. **`role_permissions` auto-seeding (non-destructive)**: at startup, the 4 role templates are seeded only when the `role_permissions` table is empty; otherwise seeding is skipped and pre-upgrade data (including customized role permissions) is never overwritten.
5. **No method-level `@PreAuthorize` on synchronous controllers**: permission decisions go through URL rules; do not add method-level annotations when adding/modifying synchronous controllers, or you will hit the RBAC 500 rule.
6. **API Key and `/v1/**` are unaffected**: authentication of inference endpoints and API Key service-type permissions stay unchanged and remain independent of the web RBAC system.

## Related Docs

- [JWT Authentication](jwt-authentication.md) - issuance and validation of the `roles` / `permissions` claims
- [API Key Management](api-key-management.md) - service-type permissions (`/v1/**` endpoints)
- [Audit Log Management](audit-log-management.md) - security audit events
- [Security Blacklist](blacklist-management.md) - account and token banning

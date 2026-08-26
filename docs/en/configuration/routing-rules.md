# Routing Rules Configuration

<!-- 版本信息 -->
> **Doc Version**: 1.0.1
> **Last Updated**: 2026-08-25
> **Git Commit**: f8a2eebe
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

JAiRouter provides a **rule engine** (v2.8.5) that enables conditional routing rules configured via the Web console or YAML. Route requests flexibly by model name, request headers, client IP, and more — without writing code.

The rule engine evaluates on **every request**: rules are matched by priority (highest first) and the first hit takes effect. When no rule matches, the original routing logic runs unchanged — behavior is **identical to a deployment without the rule engine**.

## Use Cases

| Scenario | Example |
|----------|---------|
| Canary release | Route 10% of traffic to a new model by client IP |
| Tenant/channel isolation | Route by `x-tenant` header to different instance groups |
| Source restriction | Internal IPs (CIDR) go to dedicated instances |
| Model name rewrite | Requests for `gpt-4` actually route to `claude-3` |
| Instance pinning | Pin certain requests to a specific instance |
| Adapter switching | Switch OpenAI/Ollama adapters by request header |
| Weighted split | Same request consistently hits the same rule (IP+model hash) |

## Matching Semantics

### Condition Combination

- **AND within a rule**: all conditions must match
- **OR across rules**: matched by `priority` descending, **first match wins**

### Rule Fields

| Field | Description |
|-------|-------------|
| `id` | Unique identifier (auto-generated UUID) |
| `name` | Rule name (required) |
| `enabled` | Enabled flag, default `true`; disabled rules are skipped |
| `priority` | Priority, higher matches first (0-9999) |
| `conditions` | Condition list (all must match) |
| `action` | Action to execute when matched |

### Condition Types (type)

| Type | Description | Example value |
|------|-------------|---------------|
| `MODEL_NAME` | Request model name | `gpt-4` |
| `SERVICE_TYPE` | Service type (chat/embedding/rerank/tts/stt/imgGen/imgEdit) | `chat` |
| `HEADER` | Request header (requires `field` = header name) | `vllm` |
| `CLIENT_IP` | Client IP | `10.0.0.0/8` |
| `WEIGHT` | Weighted split (0-100 percent) | `50` |

### Operators (operator)

| Operator | Description | Applicable conditions |
|----------|-------------|----------------------|
| `EQUALS` | Equal (case-insensitive) | All |
| `CONTAINS` | Contains | MODEL_NAME/HEADER/CLIENT_IP |
| `STARTS_WITH` | Prefix match | MODEL_NAME/HEADER/CLIENT_IP |
| `REGEX` | Regex partial match (find semantics) | MODEL_NAME/HEADER |
| `CIDR_MATCH` | CIDR match, e.g. `192.168.1.0/24` | CLIENT_IP only |

> **WEIGHT condition**: stable hash of `(clientIp + "|" + modelName)`, hit when `hash % 100 < weight`. The same (IP, model) request **always yields the same result**, suitable for percentage-based splits. `weight` comes from the condition's `weight` field, falling back to `value`, then 50.

### Action Types (action.type)

| Type | Description | Target field |
|------|-------------|--------------|
| `TARGET_MODEL` | Rewrite model name and select instances by the new name | `modelName` |
| `TARGET_INSTANCE` | Pin an instance (by instanceId or name) | `instanceId` |
| `TARGET_ADAPTER` | Switch adapter by name (falls back to instance adapter if unregistered) | `adapterName` |
| `LB_STRATEGY` | Override load balancing strategy | `lbStrategy` |
| `RATE_LIMIT` | Rule-level rate limiting (per rule ID, 429 when exceeded) | `capacity` / `rate` / `algorithm` / `scope` |

> **LB_STRATEGY values**: `random` / `round-robin` / `least-connections` / `ip-hash` / `consistent-hash`. Unknown strategies fall back to the configured one.
>
> **RATE_LIMIT action (v2.8.8)**: Once a rule with this action matches, requests are rate-limited per **rule ID** (independent of service/instance-level limits). `capacity` (token bucket capacity) and `rate` (refill per second) are required and must be > 0; `algorithm` defaults to `token-bucket`, `scope` defaults to `rule`. Requests over the limit get `429 Too Many Requests`. Deleting a rule or changing its action type automatically cleans up the corresponding limiter. `GET /api/services/{serviceType}/ratelimit` exposes limiter status including the `rule` level.

## Web Console Configuration

1. Log in to the JAiRouter admin console
2. Click **Configuration → Routing Rules** in the left menu

### Creating a Rule

Click **「New Rule」** and fill in the form:

| Field | Description |
|-------|-------------|
| **Name** | Rule name |
| **Priority** | 0-9999, higher matches first |
| **Conditions** | Multiple rows: condition type → operator → value; values support **dropdown/autocomplete** (model names, service types, common headers) with manual input as fallback; HEADER adds a header-name selector; WEIGHT uses a 0-100 number |
| **Action** | Select action type; target value is chosen from a **dropdown** (model name / instance / adapter, searchable with custom input; LB strategy is a fixed 5-value select) |

> The form has a **「Simulate Test」** button — validate rule matching before saving.
>
> Alternatively click **「Create from Template」**: pick a built-in scenario template (canary release / tenant isolation / model rewrite / weight split / adapter switch / VIP pin), enter a name, and a rule draft is generated and pre-filled into the form for editing.

### Managing Rules

- **Enable/Disable**: the table switch takes effect immediately
- **Priority drag-and-drop**: drag the row handle to reorder by row position (top = highest priority) and commit in batch; YAML rules show a **YAML** badge and cannot be dragged (their priority is unaffected)
- **Hit statistics**: the "hits" column shows accumulated match counts per rule (Prometheus metric `jairouter_rule_hits_total`); the refresh button reloads list and stats
- **Edit/Delete**: via the action column

> Rule changes take effect **immediately** — no restart required.

## YAML Configuration

Edit `src/main/resources/config/router/rules.yml`:

```yaml
model:
  rules:
    - id: route-vllm-header
      name: Route to vLLM adapter by header
      enabled: true
      priority: 100
      conditions:
        - type: HEADER
          field: x-routing
          operator: EQUALS
          value: vllm
      action:
        type: TARGET_ADAPTER
        adapter-name: vllm

    - id: route-internal-ip
      name: Pin internal IPs to dedicated instance
      enabled: true
      priority: 90
      conditions:
        - type: CLIENT_IP
          operator: CIDR_MATCH
          value: 10.0.0.0/8
      action:
        type: TARGET_INSTANCE
        instance-id: internal-gpu-1

    - id: route-model-rewrite
      name: Rewrite gpt-4 to claude-3
      enabled: true
      priority: 80
      conditions:
        - type: MODEL_NAME
          operator: EQUALS
          value: gpt-4
      action:
        type: TARGET_MODEL
        model-name: claude-3
```

> Default is an empty list (`model.rules: []`), i.e. no rules enabled. YAML rules merge with Web-created rules: **for the same id, the persisted (Web) rule overrides YAML**.

## API Reference

Base path: `/api/config/rules`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/config/rules/list` | GET | List all rules (priority descending) |
| `/api/config/rules/{id}` | GET | Get a single rule |
| `/api/config/rules` | POST | Create a rule (409 if id exists) |
| `/api/config/rules/{id}` | PUT | Update a rule |
| `/api/config/rules/{id}` | DELETE | Delete a rule |
| `/api/config/rules/{id}/enable` | PUT | Enable a rule |
| `/api/config/rules/{id}/disable` | PUT | Disable a rule |
| `/api/config/rules/priority` | PUT | Batch update priorities `[{id, priority}]`; unknown ids (e.g. YAML rules) are skipped, returns `{updated, skipped}` |
| `/api/config/rules/validate` | POST | Rule simulation test (dry-run), read-only |
| `/api/config/rules/stats` | GET | Rule hit statistics (ruleId/ruleName/actionType/hits) |
| `/api/config/rules/templates` | GET | Rule scenario template list |
| `/api/config/rules/templates/{id}/create` | POST | Build a rule draft from a template (`{name, priority?}`, not persisted) |

### Create Rule Example

```bash
curl -X POST http://localhost:8080/api/config/rules \
  -H "Content-Type: application/json" \
  -H "Jairouter_Token: your-admin-token" \
  -d '{
    "name": "Route to vLLM by header",
    "priority": 100,
    "enabled": true,
    "conditions": [
      {"type": "HEADER", "field": "x-routing", "operator": "EQUALS", "value": "vllm"}
    ],
    "action": {"type": "TARGET_ADAPTER", "adapterName": "vllm"}
  }'
```

### Batch Priority Example

```bash
curl -X PUT http://localhost:8080/api/config/rules/priority \
  -H "Content-Type: application/json" \
  -H "Jairouter_Token: your-admin-token" \
  -d '[{"id": "rule-id-1", "priority": 200}, {"id": "rule-id-2", "priority": 100}]'
```

### Rule Simulation Test (dry-run)

Before saving a rule, verify it matches as expected using a sample request — **read-only, no state change**:

```bash
curl -X POST http://localhost:8080/api/config/rules/validate \
  -H "Content-Type: application/json" \
  -H "Jairouter_Token: your-admin-token" \
  -d '{
    "serviceType": "chat",
    "modelName": "gpt-4",
    "clientIp": "127.0.0.1",
    "headers": {"x-routing": "vllm"}
  }'
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `modelName` | yes | Model name of the sample request |
| `serviceType` | no | Service type, defaults to `chat` (chat/embedding/rerank/tts/stt/imgGen/imgEdit, case-insensitive) |
| `clientIp` | no | Client IP, defaults to `127.0.0.1` |
| `headers` | no | Request header key-value pairs |

On match:

```json
{
  "success": true,
  "data": {
    "matched": true,
    "ruleId": "xxx",
    "ruleName": "Route to vLLM by header",
    "priority": 100,
    "action": {"type": "TARGET_ADAPTER", "target": "vllm"},
    "message": "Matched rule: Route to vLLM by header"
  }
}
```

When nothing matches, `matched=false`. The rule form in the Web console also has a built-in **「Simulate Test」** panel.

## Verification

1. Use **AI Playground → Chat Test** to send requests and observe routing
2. Check backend logs for `Selected adapter` / routing selection info
3. Or call `/v1/*` APIs with/without the conditional header and compare routing

## Notes

1. **Hot reload**: rule changes take effect immediately, no restart needed
2. **Persistence**: Web-created rules are stored in StoreManager (key=`rule_definitions`) and restored on restart
3. **Priority**: rules with the same priority keep insertion order; semantics are "first match wins" — avoid overlapping rules
4. **Performance**: keep the rule count under ~100; per-request evaluation cost is negligible
5. **TARGET_ADAPTER**: if the specified adapter is not registered, it logs a warning and falls back to the instance-level adapter
6. **HEADER conditions**: only apply to `/v1/*` request paths (headers are used for matching only, not outbound forwarding)

---

## Resource Pools & auto-model (v2.8.9)

A resource pool bundles a named set of instances of one service type. When a request's model is a pool name (convention: uto-model), the gateway picks a healthy pool member to serve.

### Configuration

1. **YAML** (config/router/pools.yml):

``yaml
model:
  pools:
    - pool-name: auto-model
      name: Default auto-routing pool
      service-type: chat
      enabled: true
      strategy: weighted-random
      members:
        - instance-id: inst-gpt
          weight: 9
        - instance-id: inst-claude
          weight: 1
``

2. **Web console**: Configuration → Resource Pools (CRUD with hot reload; persisted to StoreManager key=pool_definitions)

### Behavior

- **Pool name = virtual model name**: requesting model=auto-model (or any configured pool name) triggers pool routing; with no pool configured, uto-model falls back to all healthy instances of the service type
- **Selection flow**: pool members (referenced by instance ID) → status/health/circuit-breaker filter → pool weights → pool strategy; deleted instances are skipped
- **Strategies**: weighted-random (default) / ound-robin / least-connections / ip-hash / consistent-hash (note: consistent hash ignores weights)
- **Rule synergy**: a rule's TARGET_MODEL target can be a pool name — the matched request then routes through the pool
- **Response echo**: after pool routing, the response and downstream request model field reflect the actual serving instance model (non-streaming; streaming outbound requests are rewritten too)
- **Rate limiting & circuit breaker**: the pool-selected instance continues through the existing service/instance rate-limit and circuit-breaker chain

# Changelog

<!-- 版本信息 -->
> **Document Version**: 2.9.11
> **Last Updated**: 2026-09-05
> **Git Commit**: -
> **Author**: Lincoln
<!-- /版本信息 -->



This document records the version update history and important changes of the JAiRouter project.

## Versioning Scheme

JAiRouter follows the [Semantic Versioning](https://semver.org/) specification:

- **Major Version**: Incompatible API changes
- **Minor Version**: Backward-compatible new features
- **Patch Version**: Backward-compatible bug fixes

## Version History

### [2.9.11] - 2026-09-05 - Feature Release (README Refresh + Hallmark Design Audit + Frontend Fixes)

#### README & Docs

- **README / README-ZH full refresh**: refreshed 7 UI screenshots (incl. dark theme and call-history dashboard; previously the 2026-08-26 UI); Core Features now cover response cache P1, RBAC 44 codes and dark/light theme; Roadmap permission codes 43→44; sample auth header unified to `Jairouter_Token`
- **rbac-permissions zh/en synced to 44 codes**: catalog gains `config:cache:write` (config module = 11 resources / 21 codes); role counts ADMIN 44 / OPERATOR 35; API & permission-tree descriptions updated

#### Hallmark design audit (local innerdoc, input for v2.10.x)

- Produced `innerdoc/02-架构与设计/web-management-hallmark-audit.md` (474 lines): 395 hard-coded colors, ~5,300 lines of dead code, missing governance chain on the Dashboard, 13 unused `components/common` components, 4 duplicated stat-card implementations, etc.; includes rollout notes for v2.10.0 (foundation) / v2.10.1 (config & onboarding) / v2.10.2 (governance & security)

#### Frontend fixes

- Static permission catalog synced to 44 (`permission.ts` / `menu.ts`; the permission tree now shows `config:cache:write`)
- Login i18n: added missing keys (username/passwordPlaceholder, submitting) — placeholders no longer render raw keys
- `stores/user.ts`: jwt validate changed GET→POST (matches the backend contract, fixing silent 405)
- `BlacklistManagement`: removed the non-existent `suspicious-ips` endpoint call and its mock fallback (IP list now sourced from active tokens)

#### Quality

- Backend **3225 tests all green**; frontend `vue-tsc` + `vite` build passed

---

### [2.9.10] - 2026-09-05 - Feature Release (Routing Intelligence-5: Response Cache P1)

#### Response cache (streaming SSE concatenation cache + invalidation API + service-level rate-limit early short-circuit)

- **Streaming SSE concatenation cache**: beyond non-streaming, streaming chat responses can be cached too — post-transform chunks + usage + finishReason are stored in `CachedStreamingResponse`, written when the stream completes normally; hits are replayed as bare SSE (TEXT_EVENT_STREAM + [DONE]); opt-in via `jairouter.response-cache.skip-streaming: false`
- **Invalidation API**: `CacheStore` extended with delete / deleteByPrefix / clear; new `DELETE /api/config/cache/response` (invalidate by serviceType / model / all); cache keys restructured to three parts `rc:{service}:{model}:{sha256}` so prefix invalidation works
- **Service-level rate-limit early short-circuit**: `RateLimitManager.tryAcquireService` + `ServiceRateLimitHolder` (ThreadLocal once-only, prevents double accounting) — cache hits short-circuit before instance selection; 429 hard-boundary semantics unchanged
- **Permission extension**: 44th code `config:cache:write` (PermissionCodes + PermissionRuleRegistry URL rules + role templates)

#### Tests

- Added 49 (streaming write / read replay / bucketing / invalidation / three-part key / rate-limit short-circuit integration); **3225 tests all green**

---

### [2.9.9] - 2026-09-03 - Feature Release (Routing Intelligence-4: Response Cache P0)

#### Response cache (non-streaming exact match)

- **Full response cache**: identical requests (same tenant + service/model + canonical request body) reuse the cached response and **skip the downstream entirely** — lower latency and cost; stacks with the v2.9.0 prefix cache (KV-cache affinity, still hits the downstream for prefill savings)
- **P0 scope**: non-streaming chat + embedding + rerank (deterministic: chat requires temperature==0/null and n==1/null; embedding/rerank are naturally deterministic); image/TTS/STT binary excluded; streaming caching deferred to a later version
- **Components**: `ResponseCacheProperties` (`jairouter.response-cache`, off by default, opt-in) / `CacheStore` interface + `CaffeineCacheStore` (±10% TTL jitter against thundering herd) / `ResponseCacheService` facade (enabled short-circuit + deterministic gate) / `ResponseCacheKeyBuilder` (chat/embedding/rerank canonicalization)
- **Cache key**: `SHA-256(apiKeyId|user?|serviceType|model|canonical body)` — apiKeyId in the key prevents cross-tenant leaks; `user` optionally in the key (empty → apiKey granularity, zero API breakage); `cacheSalt` explicit bypass; no plaintext content in keys
- **Conversation semantics**: no sessionId — history travels fully in `messages`, so the key naturally distinguishes contexts; single-turn greetings hit frequently by design, staleness bounded by TTL
- **Wiring**: handler read (short-circuit after instance selection, service-level rate limiting not bypassed); NonStreamingRequestProcessor write (2xx deterministic responses only)
- **Metrics**: `jairouter_response_cache_hits_total / misses_total / hit_ratio` (tags service/model)
- **Hit semantics**: cache hits short-circuit before downstream execution (no call-history record, no token quota consumed — only the hit counter increments)
- **Docs**: new `configuration/response-cache.md` (zh/en)

#### Tests

- Added 45: KeyBuilder 12 / CaffeineCacheStore 6 / ResponseCacheService 8 / processor write 4 / metrics 3 / handler integration 13 (hit short-circuit / miss / disabled / streaming & temperature>0 bypass / tenant isolation / embedding·rerank / write-read loop); **3176 tests all green**; end-to-end smoke against a real DeepSeek downstream passed (hit / tenant isolation / metrics)

---

### [2.9.8] - 2026-09-02 - Feature Release (Web Menu RBAC Management + User/Permission Extension)

#### Data-driven permission system

- **43 permission codes**: `module:resource:action` scheme (overview/config×10/lb/cb/rl/callhistory/monitoring×5/tracing×3/security×4/system×2/ai/actuator); `PermissionCodes` constants + full list
- **4 role templates**: ADMIN (all 43) / OPERATOR (34: read+write minus system/security:manage/actuator) / USER (24: dashboard+config:read+lb/cb/rl+monitoring:read+tracing dashboard/search+playground) / VIEWER (23: read-only); `RolePermissionSeeder` seeds on startup (idempotent when table empty), stored in role_permissions
- **JWT permissions claim**: permission codes embedded in login JWT (no ROLE_ prefix); roles stay in the roles claim; preserved on refresh

#### Data-driven authorization

- **PermissionRuleRegistry** (36 URL→code rules) + **PermissionAuthorizationManager** (ReactiveAuthorizationManager: hit→check code; ADMIN bypass; no rule→fallback authenticated); wired into SecurityConfiguration `/api/**`
- Sync-returning controller authorization handled purely via URL rules (RBAC 500 rule: no method-level @PreAuthorize); ModelCallStats `hasAdminPermission` stub covered by URL rule
- **Contract fix**: `/api/security/jwt/accounts/**` now maps to `system:accounts:manage`

#### Management API & UI

- `GET /api/security/permissions` / `GET /api/security/permissions/roles` / `PUT /api/security/permissions/roles/{roleName}` (invalidateCache) / `GET /api/auth/permissions`
- **Permission management UI**: System → Permission Management (role dropdown + 43-code tree + save; prompts re-login for changes)
- **Menu re-grouping**: 11 groups → 8 groups / 34 items; data-driven menu.ts + usePermission filtering + route guard meta.permissions

#### Login chain fix

- **DB account fallback**: `AccountManager` authentication now supports Web-created DB accounts (YAML static accounts first, jwt_accounts table fallback; enabled=null treated as enabled, consistent with JwtAccountService)

#### Frontend fix

- Sidebar menu "stepped" background after scroll fixed (independent scroll region over fixed full-height gradient column)

#### Tests

- Added 50+: RolePermissionService/Seeder/PermissionAuthorizationManager (incl. URL matrix)/PermissionManagementController/JWT permissions claim/AccountManager (DB fallback, 13) etc.; **3131 tests all green**; permission smoke passed (ADMIN 200 / no-token 401 / USER·VIEWER 403 matrix)

#### Docs

- New `security/rbac-permissions.md` (zh/en): permission codes/role templates/workflow/management API/permission UI/menu & routes/upgrade notes

---

### [2.9.7] - 2026-09-01 - Feature Release (Routing Intelligence-3: Tag Routing)

#### Tag routing

- **Instance tags**: `ModelInstance` gains `Map<String,String> tags` (e.g. `gpu_type: a100` / `region: cn-north` / `tier: premium`), supported across the full YAML/API/persistence chain (ConfigConverterHelper / ModelInstanceConfiguration / ServiceInstanceEntity JSON column / ServiceInstanceManager)
- **TARGET_TAGS rule action**: new "tag routing" action that selects instances by tags (AND semantics — instance tags must contain every required key-value pair); `RuleDefinition.Action` gains a `tags` field
- **Request-level header selection**: `X-JAiRouter-Tags: key=value,key2=value2` (comma-separated, tolerant of whitespace/empty items) filters candidate instances by tags; rule TARGET_TAGS takes precedence over the header
- **Priority**: TARGET_INSTANCE lock > TARGET_TAGS > resource pool; empty candidates after tag filtering → 404; pools and auto-model are naturally unaffected by tags (pool members are explicitly listed)
- **Frontend**: instance management page gains a "tag configuration" key-value editor (mirrors the headers UI); rule form gains the TARGET_TAGS action with a tag editor; rule list shows action summaries

#### Fixes

- **Legacy DB upgrade compatibility**: `CompatibilitySchemaMigrator` registers the `service_instance.tags` column (CLOB, dialect-adapted) — fixes instance query 500 on legacy H2 DBs missing the column
- **Instance limiter identifier**: `RateLimitManager.generateInstanceKey` falls back `instanceId → name → baseUrl`, fixing null identifiers for instances without instance-id; metrics parsing splits on the first `:` to tolerate colons in names
- **Rate limiter monitor metrics**: `RateLimiterTracingWrapper` now forwards `getRemainingCapacity()`/`getUsageRatio()` — fixes usage ratio stuck at -100% (the tracing wrapper previously fell back to the interface default -1)
- **Base config**: added `instance-id` to all 9 instances in `model-services-base.yml` (removes the null-identifier root cause)
- **LB strategy dropdown**: fixed select width and option layout in StrategyConfig.vue (latency strategy label no longer touches its description); fallback list now includes latency
- **Rule template copy**: `RuleTemplateService.copyAction` handles TARGET_TAGS (prevents silent tag loss when copying future templates)

#### Tests

- Added: SelectInstanceOptimizerTest (9), ModelServiceRegistryTagRoutingIntegrationTest (10), ModelInstanceConfigurationTest (6), CompatibilitySchemaMigrator migration assertions (3), RateLimitManagerInstanceKeyTest (3), RateLimiterTracingWrapperTest (2); **3069 tests all green**

---

### [2.9.6] - 2026-08-30 - Feature Release (Routing Intelligence-2: Request-Level Failover)

#### Request-level failover

- **Instance switching on retry**: `BaseAdapter.processRequestWithRetry` reworked — on retryable error (connection / 5xx / timeout) the current instance joins the per-request failed blacklist (`baseUrl:path` key); a new instance is selected through the normal chain (health/circuit-breaker filter + LB strategy), replacing the old same-instance hammering
- **Blacklist isolation**: failed instances are not reselected within a request (`tryReselectInstance`, cap `failedKeys.size()+2` prevents infinite loops)
- **Safe fallback**: single-instance service / all-instances-failed / reselect exhausted → fall back to same-instance retry (existing behavior); 4xx non-retryable errors never switch; retry count stays bounded by `RetryPolicy`
- **Enabled by default**: no config needed (reselect passes null clientIp, LB degrades to non-IP-aware; sticky routing naturally bypassed during failover)
- Per-instance WebClient fetched by baseUrl (`getWebClientForInstance`)

#### Tests

- New `BaseAdapterFailoverTest` (5 cases: A-fails→B-succeeds / all-failed fallback / single-instance no-reselect / 4xx no-retry / reselect cap); **full suite 3034 tests green** (+5)

---

### [2.9.5] - 2026-08-30 - Feature Release (Knowledge-Base Governance)

#### Governance mechanism (docs 155 + innerdoc 165)

- **Toolset** `scripts/knowledge-governance/`: kg-scanner (inventory + SHA-256 + one-off classification), kg-duplicate-detector (SHA-256 exact + filename near), kg-staleness-detector (index drift / status conflict / version literal drift), kg-archive-mover (--plan/--execute archive, hard-guard knowledge-base/ & 16-版本发布/), kg-version-tracker (innerdoc/docs-versions.json + --regen-index)
- **Workflow** `.mimocode/skills/knowledge-governance/SKILL.md`: 5 steps (scan → LLM proposal → human approval → execute → index update), run every version release
- **CI enhancement**: `validate-nav-files.py` (mkdocs nav integrity) + docs-version-management.yml (nav check step + duplicate summary in issue)
- **First-run execution**: archived 61 files (H2×23/security×16/dev-guide×11/tracing×7 etc → `innerdoc/archive/`); 3 unique contents merged into knowledge-base (JPA migration / API-Key init fix / tracing verification); indexes rebuilt (README-INNERDOC/INDEX.json/00-索引, 105 files/18 categories); 1 status conflict fixed
- **SOP docs**: `docs/{zh,en}/development/knowledge-base-governance.md` (10 sections); created missing `docs/en/development/doc-maintenance.md` (fixed mkdocs strict-build risk)
- **docs fixes**: 8 doc version headers 2.6.11 → 2.9.5; roadmap current-stable v2.7.11 → v2.9.5

#### Tests

- Governance tools verified (165 files / 3 exact duplicate groups / 22 near clusters / index drift detected); full suite **3029 tests green** (no Java changes)

---

### [2.9.4] - 2026-08-30 - Feature Release (UI Design-System Refactor + RBAC Fix)

#### UI Design System

- **Design tokens**: new `src/styles/tokens.css` (40+ semantic `--ja-*` CSS vars: primary/sidebar/main-bg/text/border/radius/shadow/font/login-gradient/dashboard gradients, with `html.dark` overrides)
- **Element Plus overrides**: `src/styles/element-override.css` maps `--el-color-primary` family, `--el-bg-color`, `--el-fill-color-*`, `--el-text-color-*`, `--el-border-color-*`, shadows to tokens (light + dark)
- **Dark mode**: `src/composables/useTheme.ts` (isDark/toggleTheme/initTheme, localStorage persistence, follows system by default, synchronous init before mount to avoid flash); sun/moon toggle in the Layout header
- **Hardcoded color cleanup**: Layout.vue sidebar/active/main, Login.vue gradient, Dashboard.vue stat-card gradients and icon colors, index.html gradient → token vars (same look preserved)

#### RBAC Fix (500 regression)

- **Root cause**: under `@EnableReactiveMethodSecurity`, method-level `@PreAuthorize` on controllers with SYNCHRONOUS return types requires a Publisher return (Reactor Context); real requests threw 500 (auth OK, authorization interceptor crashed — hence 500, not 401)
- **Fix**: removed `@PreAuthorize` from `ApiCallHistoryController` / `CallHistoryConfigController` / `TracingSecurityController` (3 sync-return controllers); RBAC moved to `SecurityConfiguration` URL rules (`/api/call-history/**`, `/api/config/call-history/**`, `/api/config/tracing/security/**` → ADMIN)
- Verified against real requests: ADMIN 200 / no token 401; local dev DB manually patched with v2.9.2 columns (H2 `DATABASE_TO_UPPER=FALSE` breaks Hibernate ddl-auto update metadata matching)

#### Tests

- Updated `ApiCallHistoryControllerRbacTest` / `RecordLevelChangeAuditTest` (RBAC assertions now match URL-rule semantics); **full suite 3023 tests green**

---

### [2.9.3] - 2026-08-30 - Feature Release (Routing Intelligence-1: EWMA Latency-Aware Routing)

#### Latency-aware load balancing (`load-balance.type: latency`)

- **LatencyAwareLoadBalancer**: EWMA-based latency-aware strategy, weighted random by `1/(1+ewma)` (lower latency = higher probability); cold-start equal weight for fair exploration; failed calls update with a 30s penalty
- **Duration chain**: `LoadBalancer` gains durationMs hook overloads (default, zero change for the 6 existing strategies); `AdapterMetricsRecorder → ModelServiceRegistry → LoadBalancer` threads call duration; `StickyLoadBalancer` 3-arg passthrough (duration preserved under sticky wrapping)
- **Config**: `ewma-alpha` (default 0.2, global/service-level); `latency` registered in ComponentFactory; validators + DTO + merger/converter full-chain support; `/api/loadbalancer/strategies` includes latency
- Default strategy remains `random`; `type: latency` is opt-in — zero default behavior change

#### Tests

- New `LatencyAwareLoadBalancerTest` (EWMA convergence / selection distribution / cold start / failure penalty / concurrency), `ModelServiceRegistryDurationFlowTest` (duration flow); extended `StickyLoadBalancerTest`, `LoadBalancerManagementControllerTest`, `ConfigValidatorHelperTest`; **full suite 3024 tests green** (+16)

---

### [2.9.2] - 2026-08-30 - Feature Release (Record Governance)

#### Three Recording Levels

- **METADATA_ONLY (default)**: metadata only, no request/reply content stored
- **SUMMARY**: content desensitized via `SanitizationService` then truncated to summary columns
- **FULL**: complete request/reply content encrypted with AES-256-GCM (new `RecordContentCipher`; key from `JAIR_CALL_HISTORY_KEY` env var or auto-generated and persisted to `~/.jairouter/call-history.key`)
- New config: `jairouter.call-history.record-level` / `max-content-length` (default 64 KB) / `encryption-key-source`

#### Content Capture

- **Non-streaming**: request body (post-transform serialization) + raw downstream response body (pre-transform) captured, truncated at `maxContentLength`; new `AdapterMetricsRecorder.recordCompleteCall` overload carrying bodies
- **Streaming**: request serialized at entry + assembled text in `doOnComplete`; streaming call-history recording added (previously streaming did not write history)
- **Dedup fix**: `BaseAdapter` success path now updates stats only, avoiding double recording with the RequestProcessor (2 rows → 1 row per call)

#### Security

- **Full RBAC on call history**: all `ApiCallHistoryController` endpoints + new config endpoints `GET/PUT /api/config/call-history` are ADMIN-only
- **Latent gap fixed**: added `@EnableReactiveMethodSecurity` (previously `@PreAuthorize` was not enforced)
- **New endpoint** `GET /api/call-history/{id}/detail`: ADMIN can decrypt and view FULL records
- **Audit events**: `RECORD_LEVEL_CHANGE` (level change), `FULL_CONTENT_ACCESS` (decrypt access to FULL records)

#### Frontend

- Call-history page gains a record-level setting (radio: metadata-only / summary (desensitized) / full (encrypted))

#### Tests

- Full suite **3008 tests green** (0 failures / 0 errors, +55 new); new `RecordContentCipherTest`, `ApiCallHistoryServiceRecordLevelTest`, `ApiCallHistoryControllerRbacTest`, `RecordLevelChangeAuditTest` and more

---

### [2.9.1] - 2026-08-30 - Quality Closing

#### Pragmatic Large-File Splits

- **15 → 5 files > 500 lines** (5 highly-cohesive files pragmatically exempted): `TracingConfiguration` 608→307 (4 nested config classes extracted), `TracingEncryptionService` 543→477, `ApiKeyBatchService` 608→459, `CircuitBreakerTracingWrapper` 570→243, `DefaultMetricsCollector` 629→379, `ConfigurationService` 653→410 (instance management extracted to `InstanceConfigService`), `JwtBlacklistServiceImpl` 558→438, `ControllerTracingInterceptor` 595→237, `ExtendedSecurityAuditServiceImpl` 629→490, `NonStreamingRequestProcessor` 574→411
- 16 new top-level classes (plain POJO config classes / DTOs + plain helpers + `@Component`/`@Service` delegates); zero behavior change, full suite regression-free

#### Frontend

- **Resource-pool member instances now use a dropdown selector**: loads available instances by service type, dedupes already-selected items, no manual input, fallback display for deleted instances

#### Tests

- Full suite **2953 tests green** (0 failures / 0 errors); jacoco INSTRUCTION coverage **31%** (≥ 30% target met, 1175 classes)

---

### [2.9.0] - 2026-08-28 - Feature Release

#### LLM KV Cache Enhancement: Prefix-Cache-Friendly Gateway

- **Automatic tenant-affinity sticky routing**: new `StickyLoadBalancer` (consistent hashing over `apiKeyId|serviceType|modelName` → instance) + `AffinityKeyResolver` (prefers `apiKeyId`, falls back to `clientIp`) + `AffinityContextHolder` (ThreadLocal affinity-key propagation); `sticky.enabled` config-aware (auto-wraps when instances > 1); `affinityKeyScope` supports `tenant` / `tenant_model` (default includes modelName) granularity, resolved dynamically
- **Cache hit metrics**: parses DeepSeek-style `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` and OpenAI/vLLM-style `prompt_tokens_details.cached_tokens`; new `jairouter_cache_hit_tokens_total` / `cache_miss_tokens_total` counters + `jairouter_cache_hit_ratio` gauge (per adapter+instance, cumulative hitTotal/(hitTotal+missTotal))
- **Cache parameter passthrough**: `ChatDTO.Options` gains `prefixCacheHash` / `enablePrefixCaching`; `OpenAiRequestTransformer` extra_body whitelist allows vLLM prefix-cache params
- **Prefix hygiene guarantee**: regression tests assert byte-for-byte message-array passthrough (4- and 6-message sets); 5 tolerant cache-token parsing tests
- **Fixes**: StickyLoadBalancer concurrency safety (volatile snapshot + fingerprint cache instead of per-request hash-ring rebuild, removing TreeMap concurrent read/write risk and O(instances×150) recompute); cache_hit_ratio cumulative semantics; affinityKeyScope actually takes effect; `ApiKeyBatchServiceTest` expiration-stats midnight flake

#### Tests

- New: `StickyLoadBalancerTest`, `AffinityKeyResolverTest`, `CacheMetricsTest`, `CacheTokenParsingTest`, `OpenAiRequestTransformerTest` (regression), `StreamingRequestProcessorTest`; **full suite 2953 tests green**

---

### [2.8.9] - 2026-08-26 - Feature Release

#### Intelligent Routing: Resource Pools + auto-model

- **Resource pools**: a named set of instances of one service type, with a configurable strategy (`weighted-random` / `round-robin` / `least-connections` / `ip-hash` / `consistent-hash`) and per-member weights; configured via YAML (`model.pools`) + StoreManager persistence + CRUD API (`/api/config/pools`) + a web management page (Configuration → Resource Pools)
- **auto-model virtual model name**: requesting `model=auto-model` (or any pool name) automatically picks a healthy pool member to serve (health/circuit-breaker filter + pool weights + pool strategy); with no pool configured, `auto-model` falls back to all healthy instances of the service type; missing members (deleted instances) are skipped
- **Rule synergy**: a rule's `TARGET_MODEL` action can point to a pool name to pin the pool
- **Response echo**: after pool routing, the downstream request and (non-streaming) response `model` field is rewritten to the actual serving instance model (streaming outbound request rewritten too; streaming response echo deferred)
- **Fix**: `ApiKeyBatchServiceTest` expiration-stats time-boundary flake (`now+2h` crossing midnight misclassified)

#### Tests

- New: `PoolSelectorTest` (POOL-001~007), `PoolPersistenceServiceTest` (POOL-PERSIST-001~008), `PoolConfigControllerTest` (POOL-API-001~013), `ModelServiceRegistryRuleIntegrationTest` pool routing (INTEG-018~021); **full suite 2909 tests green**

---

### [2.8.8] - 2026-08-26 - Feature Release

#### Rate-Limiting Capability Completion

- **Service-level rate limiting on the hot path**: the request chain now runs a service-level rate-limit check (exactly once per request, zero overhead without config); exceeding it returns `429 Too Many Requests`; instance-level keeps its "try the next instance" semantics
- **Dynamic per-service rate-limit config made real**: `PUT /api/services/{serviceType}/ratelimit` changed from an empty stub to persist (config store `model-router-config`) + hot-apply (`RateLimitManager`); GET returns the canonical format (`enabled/algorithm/capacity/rate/scope/key`); when enabled, `capacity`/`rate` are required and must be > 0; config **re-applies automatically on restart**; the service management edit dialog is wired to this endpoint
- **RATE_LIMIT rule action**: the rule engine gains a rule-level limiting action (independent limiter per rule ID, fired at the same decision point as hit counting — never double-executed); requests over the limit get 429; deleting a rule or changing its action cleans up the limiter; a new "Rate Limit Protection" preset template is added (6 → 7 templates, pre-filling the limit params when creating from template); dry-run output includes the limit params
- **Fixes**: stale assertion in the API call-history statistics test (service switched to separate range queries but the test was not updated); dev-mode `VITE_API_BASE_URL` corrected to `/api` so the dev proxy reaches the backend

#### Tests

- New/updated: ServiceRateLimitControllerTest (RL-001~008), ModelServiceRegistryRuleIntegrationTest (INTEG-014~017), RuleConfigControllerTest (RULEC-029~034), RateLimiterTest (rule-level, 3 cases), RuleTemplateServiceTest (rate-limit template), RuleTemplateControllerTest (7 templates); **full suite 2876 tests green** (including the fixed ApiCallHistoryServiceTest)

---

### [2.8.7] - 2026-08-25 - Feature Release

#### Rule Engine Trio

- **Rule hit statistics**: hits are counted at the decision-application point (no double counting; dry-run excluded); new Prometheus metric `jairouter_rule_hits_total` (tags: ruleId/actionType) + `GET /api/config/rules/stats` aggregation endpoint; the rule list page adds a "hits" column with refresh
- **Priority drag-and-drop**: rules can be reordered by drag (sortablejs), committing priorities in row order; `PUT /priority` now skips unknown ids (YAML rules no longer 404) and returns `{updated, skipped}`; rules expose a `source` field (YAML/PERSISTED), YAML rules show a badge and cannot be dragged
- **Scenario templates**: 6 built-in rule templates (canary release / tenant isolation / model rewrite / weight split / adapter switch / VIP instance pin); `GET /api/config/rules/templates` + `POST /{id}/create`; a "Create from template" wizard pre-fills the rule form as a draft before saving

#### Tests

- RuleConfigControllerTest extended (RULEC-026~028 for stats); new RuleStatsServiceTest, RuleTemplateServiceTest, RuleTemplateControllerTest; all rule-related tests pass; Checkstyle 0 violations

---

### [2.8.6] - 2026-08-25 - Enhancement Release

#### Rule Engine Usability

- **Action/condition value dropdowns**: action targets are selected from type-aware dropdowns (model name / instance / adapter, searchable with custom input; LB strategy is a fixed 5-value select); condition values support model-name / service-type / common-header dropdowns with autocomplete; options refresh when the service type changes
- **Rule simulation test (dry-run)**: `POST /api/config/rules/validate` verifies rule matching with a sample request (modelName/IP/headers), read-only; the Web form includes a built-in 「Simulate Test」 panel
- **Fix**: case-insensitive `serviceType` matching (`imgGen`/`imgEdit` camelCase enum values now resolve correctly)

#### Tests

- RuleConfigControllerTest extended (incl. RULEC-025); all rule-engine tests pass; Checkstyle 0 violations

---

### [2.8.5] - 2026-08-23 - Feature Release

#### Rule Engine (Visual Conditional Routing)

Implement visual routing rule configuration so users can configure condition-based smart routing from the Web console **without writing code**:

- **Conditional routing rules**: 5 match condition types (model name / service type / request header / client IP / weight) and 5 operators (equals / contains / starts-with / regex / CIDR match); matched requests execute a preset action
- **Action types**: TARGET_MODEL (model rewrite), TARGET_INSTANCE (pin instance), TARGET_ADAPTER (switch adapter), LB_STRATEGY (load-balancing strategy override)
- **Rule management API**: `/api/config/rules` full CRUD + enable/disable + priority batch update
- **Rule persistence**: StoreManager-backed persistence, auto-recovery on restart, YAML-first merge
- **Frontend visual configuration**: rule management page + rule form dialog (condition editing / action config)
- **Docs overhaul**: new routing-rule configuration doc, docs structure reorganization

#### Tests

- All rule-engine related test cases pass; Checkstyle 0 violations

---

### [2.8.4] - 2026-08-15 - Feature Release

#### Plugin System (Custom Adapter Framework)

Implement the "plugin system" so users can create, test, and manage custom adapters from the Web console **without writing code**:

- **Adapter Template System**: 13 built-in templates (DeepSeek/Zhipu GLM/Moonshot Kimi/Baichuan/Qwen/MiniMax/01.AI Yi/StepFun/SiliconFlow/Groq/OpenRouter/Together AI/Local Ollama)
- **Adapter Test API**: PING connectivity test + CHAT conversation test (independent 10s timeout, API Key never persisted)
- **Enhanced Frontend Wizard**: 4-step configuration wizard (template selection/basic config/advanced config/test connection) + template cards + test panel
- **Adapter Definition Persistence**: StoreManager-backed persistence, auto-recovery on restart, YAML-first merge strategy

#### New Components

- `AdapterTemplateService`: template service (13 built-in templates)
- `AdapterTestService`: adapter connectivity test service
- `AdapterDefinitionPersistenceService`: adapter definition persistence service
- `AdapterTemplateController` / `AdapterTestController`: template & test REST APIs

#### Tests

- 49 new test cases (TDD-first), all passing; Checkstyle 0 violations

---

### [2.8.3] - 2026-07-15 - Feature Release

#### Gemini Adapter + Adapter Config Enhancements

- **Gemini Adapter**: added Google Gemini adapter support
- **adapter-config phases 1-3**: OpenAI-compatible adapter config, Ollama-compatible adapter config, inheritance + override mode
- **Test coverage improvements**: ErrorResponseBuilder (15 cases), StreamingRequestProcessor (14 cases), NonStreamingRequestProcessor (11 cases), Health module (0% → 68-71%)

---

### [2.8.2] - 2026-07-17 - Code Quality

- Checkstyle cleanup: fixed 11 warnings

---

### [2.8.1] - 2026-07-16 - Code Quality

- Checkstyle cleanup: fixed 66 warnings

---

### [2.8.0] - 2026-07-15 - Feature Release

#### Anthropic Claude Adapter

Added Anthropic Claude adapter support, enabling JAiRouter to serve as a unified gateway for Claude API workloads alongside existing OpenAI-compatible backends.

- **ClaudeAdapter**: Claude adapter implementation (+295 lines)
- **ClaudeAdapterTest**: adapter tests (+404 lines, covering request transformation, response parsing, error handling)
- **Documentation cleanup & config examples**: added Claude adapter configuration examples

---

### [2.7.6] - 2026-06-30 - Feature Release

#### API Key Quota Management

Added comprehensive quota management features for API Keys:

- **Daily Token Limit**: Track and limit daily Token usage per API Key
- **Rate Limiting**: Sliding window algorithm for per-minute request limiting
- **Alert Thresholds**: Configurable usage percentage thresholds for alerts
- **Automatic Reset**: Quota counters reset daily at midnight

#### Bug Fixes

- **API Key Serialization**: Fixed `@JsonProperty(access = WRITE_ONLY)` preventing `keyHash` from being serialized to database
- **Token Usage Tracking**: Improved Token usage tracking for streaming and non-streaming requests

#### New Components

- `ApiKeyQuotaService`: Quota checking and alert service
- `TokenBucketRateLimiter`: Sliding window rate limiter
- `ApiKeyQuotaCleanupScheduler`: Scheduled cleanup of expired quota data

#### Database Changes

Added quota-related fields to `api_keys` table:
- `daily_token_limit`: Daily Token usage limit
- `rate_limit_per_minute`: Requests per minute limit
- `quota_alert_threshold`: Alert threshold percentage
- `today_token_usage`: Current day Token usage
- `today_request_count`: Current day request count
- `last_reset_time`: Last quota reset time

---

### [2.7.7] - 2026-07-10 - Feature Release

#### ExceptionEvent Collection Fixes

Fixed multiple issues in the exception event collection pipeline:

- **Bean conflict fix**: Resolved `ExceptionEventService` bean initialization conflict
- **Error degradation optimization**: Exception event collection failure no longer affects the main flow
- **Persistence chain fix**: Ensured exception events are correctly written to the database

#### API Key Service-Level Permission Checks

- **Service-level permissions**: API Keys now support fine-grained permission control by service type (Chat/Embedding/Rerank, etc.)
- **Frontend permission fixes**: Fixed permission management page display and interaction issues

---

### [2.7.8] - 2026-07-10 - Feature Release

#### Request Call History Persistence

Added a complete API call history recording feature:

- **Call record storage**: Records detailed information for each API call (model, instance, duration, status, etc.)
- **Query and statistics**: Supports querying and statistics by time range, model, service type, and other dimensions
- **Frontend dashboard**: New call history visualization page with trend charts and detail tables
- **Exception monitoring enhancement**: Exception management page adds business fields and frontend filtering

#### New Components

- `ApiCallHistoryService`: Call history recording service
- `ApiCallHistoryController`: Call history REST API
- `CallHistoryDashboard`: Frontend call history dashboard

---

### [2.5.15] - 2026-05-11 - Stable Release

#### Large Class Refactoring Complete

This release completed refactoring of 4 oversized classes, reducing 2011 lines of code (-62%).

| File | Original Lines | Final Lines | Reduction | Target |
|------|----------------|-------------|-----------|--------|
| BaseAdapter | 1386 | 416 | -70% | 600 ✅ |
| TracingService | 764 | 483 | -37% | 400 ✅ |
| DefaultStructuredLogger | 945 | 365 | -61% | 400 ✅ |
| ConfigVersionManager | 746 | 387 | -48% | 400 ✅ |

#### New Components (12)

- ConfigComparator, SpanAttributeHelper, ServiceNameResolver
- RequestLogBuilder, ResponseLogBuilder, BackendCallLogBuilder
- ErrorLogBuilder, SystemEventLogBuilder, VersionValidator
- VersionMetadataManager, VersionSyncService, ModelUtils

#### Quality Checks

- Checkstyle: ✅ Passed
- SpotBugs: ✅ Passed
- Tests: 971 passed ✅

---

### [1.7.3] - 2026-04-14

#### Bug Fixes
- **Playground Chat Streaming Response Fix**: Fixed duplicate AI response messages during streaming
  - Issue: AI responses appeared twice during streaming
  - Cause: Incorrect `displayMessages` filtering logic in `MessageList.vue`
  - Fix: Always filter the last assistant message during streaming, displayed by additional `MessageBubble` component

#### Improvements
- **Port Configuration Restoration**: Server port restored to default `8080`, consistent with documentation

#### Technical Improvements
- Updated frontend static resource build artifacts

---

### [1.7.2] - 2026-04-14

#### New Features
- **Playground Component Refactoring**: Major refactoring of Playground module with component-based architecture
  - Chat Module: `ChatContainer`, `ChatConfigPanel`, `MessageInput`, `MessageList`
  - Audio Module: `AudioContainer`, `TtsPanel`, `SttPanel`
  - Image Module: `ImageContainer`, `ImageGeneratePanel`, `ImageEditPanel`
  - Embedding Module: `EmbeddingContainer`
  - Rerank Module: `RerankContainer`
  - Common Components: `MessageBubble`, `MarkdownRenderer`, `CodeBlock`, `ModelSelector`, `ServiceLayout`, `LoadingIndicator`
- **New Composables**:
  - `useChatSession`: Chat session management (localStorage persistence)
  - `useMarkdown`: Markdown rendering handling
  - `useStreaming`: SSE streaming response processing

#### Improvements & Optimizations
- **Health Check SSE Controller Optimization**: Optimized `HealthStatusSseController` implementation
- **Instance Management Extension**: Added field support, extended `ServiceInstanceDTO` and entity classes
- **Adapter Base Class Adjustment**: Unified adapter base class handling logic
- **Frontend Routing and Layout Optimization**: Optimized routing configuration and Layout component

#### New Files
- `frontend/src/views/playground/components/` - 18 component files
- `frontend/src/views/playground/composables/` - 4 composable files
- `src/main/resources/db/migration/V3__add_adapter_headers_fields.sql`

---

### [1.7.1] - 2026-04-13

#### Bug Fixes
- **Tracing Fixes**:
  - Fixed `TracingWebFilter` duplicate `traceId` creation issue
  - Fixed `TraceQueryService` `spanCount` display error in `recentTraces` merge
  - Fixed `TracingService` `serviceName` classification, frontend route correctly identified as 'front'
- **Frontend Routing Fix**: Fixed frontend routing path error (`/admin/admin/tracing` -> `/admin/tracing`)

#### Improvements & Optimizations
- **ControllerTracingInterceptor Optimization**: Optimized child Span synchronous recording logic
- **Table Layout Optimization**: Table column width using `min-width` for adaptive filling

#### New Features
- **TraceDetail Component**: Added trace detail display component
- **Tracing Dashboard Page**: Added tracing dashboard page

#### New Files
- `frontend/src/views/tracing/Dashboard.vue`
- `frontend/src/views/tracing/components/TraceDetail.vue`
- `docs/zh/development/tracing-full-chain-design.md`

---

### [1.7.0] - 2026-04-10

#### New Features
- **Security Blacklist Management**: New security blacklist management feature, supporting IP/user/token blacklists
- **Enhanced Audit Logs**: Enhanced audit log query and display functionality with advanced search and statistics
- **JWT Account Status Toggle**: Implement account enable/disable status toggle functionality

#### Improvements & Optimizations
- **JWT Account Management**: Fixed password validation issue during editing, optimized account management interface
- **Frontend Table Optimization**: Table columns use adaptive width (min-width), added statistics cards and search functionality
- **Configuration Management Simplification**: Removed unimplemented version management features, simplified interface

#### Bug Fixes
- Fixed password validation issue when editing JWT accounts
- Fixed data display issue on account management page

#### Technical Improvements
- Added `enabled` field to `CreateJwtAccountRequest`
- Implemented `toggleAccountStatus` method in `JwtAccountService`
- Added `SecurityBlacklistController` and related entity classes
- Cleaned up unused frontend code and type definitions

---

### [1.6.2] - 2026-04-08

#### New Features
- **API Key Batch Import/Export**: Support batch import/export of API Keys
  - Added export endpoint `GET /api/auth/api-keys/export`
  - Added import endpoint `POST /api/auth/api-keys/import`
  - Support MERGE/REPLACE import modes
- **API Key Rotation**: Support automatic key rotation mechanism
  - Configure `rotationPeriodDays` to set rotation period
  - Added `ApiKeyRotationScheduler` for automatic rotation execution
- **Expired Key Auto Cleanup**: Added `ApiKeyExpirationScheduler` to automatically disable expired keys

#### Improvements & Optimizations
- **Creator Information Recording**: Record `createdBy` and `creatorIpAddress` when creating API Keys
- **Key Usage Statistics Persistence**: Usage statistics persisted via `saveApiKeysToStore()`

#### New Files
- `ApiKeyBatchExportVO.java`
- `ApiKeyBatchImportRequest.java`
- `ApiKeyBatchImportResult.java`
- `ApiKeyRotationScheduler.java`
- `ApiKeyExpirationScheduler.java`

---

### [1.6.1] - 2026-04-06

#### Security Fixes (P0)
- **API Key Hashed Storage**: API Keys stored using SHA-256 + salt hashing, replacing plaintext storage
- **Admin API Rate Limiting**: Added rate limiting (30/min, 100/hour, 10 create/hour)

#### New Features
- **IP Whitelist**: Support IP whitelist functionality (`allowedIpAddresses`)
- **Daily Request Limit**: Support daily request limit functionality (`dailyRequestLimit`)
- **Key Reset Interface**: Added key reset interface `/api/auth/api-keys/{keyId}/reset`

#### Improvements & Optimizations
- **Frontend Strong Typing**: Use strongly typed DTO/VO instead of Map data passing
- **Table Layout Optimization**: Optimized table layout and horizontal scroll support

#### New Files
- `ApiKeyHashUtil.java` - SHA-256 hash utility class
- `AdminApiRateLimiter.java` - Admin API rate limiting filter
- `ApiKeyVO/ApiKeyCreationVO/ApiKeyListVO/ApiKeyCreateRequest/ApiKeyUpdateRequest` - Strongly typed DTOs

---

### [1.6.0] - 2026-04-04

#### Breaking Changes
- **Removed Configuration Merge Feature**: Removed AutoMergeService and AutoMergeController
- **Removed Related Entity Classes**: Removed MergeResult and 5 related entity classes
- **Removed Frontend Page**: Removed ConfigMergeManagement.vue page and related API

#### Improvements & Optimizations
- **Configuration Version Management Optimization**: Simplified version management interface, retained core version switching functionality
- **Log Configuration Optimization**: Optimized logback-spring.xml configuration
- **Documentation Update**: Removed configuration merge related content

#### Retained Features
- `ConfigMergeService`: Core configuration retrieval and merge functionality
- `SecurityConfigMergeService`: Security configuration merge service

---

### [1.5.7] - 2026-04-02

#### New Features
- **JWT Account Initialization**: JWT accounts auto-initialized from YAML configuration to database
- **Account Management API Optimization**: Use standard RouterResponse response format

#### Bug Fixes
- Fixed JWT accounts not being initialized to database on system startup
- Fixed account management page unable to display data
- Fixed API path mismatch with frontend (`/api/admin/accounts` -> `/api/security/jwt/accounts`)

#### New Files
- `JwtAccountProperties.java` - Maps YAML account configuration
- `JwtConfig.accounts` field - Supports account list configuration

---

### [1.5.6] - 2026-03-30

#### New Features
- **Instance-level Rate Limiter Independent Storage**: Added `instance_rate_limit` table for instance rate limiter configuration
- **Instance-level Circuit Breaker Independent Storage**: Added `instance_circuit_breaker` table for instance circuit breaker configuration
- **Independent Configuration API**: Added independent rate limiter/circuit breaker configuration API endpoints
- **Strongly Typed DTO**: Use strongly typed DTO instead of Map data passing

#### API Changes
- `GET/PUT /api/config/instance/{type}/{id}/rate-limit`
- `GET/PUT /api/config/instance/{type}/{id}/circuit-breaker`

#### New Files
- `InstanceRateLimitEntity/InstanceCircuitBreakerEntity` - Entity classes
- `InstanceRateLimitRepository/InstanceCircuitBreakerRepository` - Repositories
- `InstanceRateLimitDTO/InstanceCircuitBreakerDTO` - DTO classes

#### Improvements & Optimizations
- `build-and-deploy.sh` script automatically cleans old compiled files

---

### [1.5.2] - 2026-03-20

#### New Features
- **JPA Migration Complete**: Completed aggressive migration from R2DBC to JPA
- **DTO Structure Optimization**: All core functions restored and optimized to DTO structure

#### Bug Fixes
- Fixed compilation errors during JPA migration
- Fixed service configuration function restoration

---

### [1.4.6] - 2026-03-10

#### Bug Fixes
- Fixed frontend independent configuration functionality
- Fixed frontend independent rate limiter and circuit breaker configuration functionality
- Fixed data return completeness issue
- Fixed `buildInstanceMap` and `convertToVO` methods

---

### [1.4.4] - 2026-03-31

#### Bug Fixes
- Fixed frontend instance management page data format issue
- Optimized data display logic

---

### [1.4.3] - 2026-03-25

#### Bug Fixes
- Fixed service type validation and exception handling logic
- Improved error messages

---

### [1.4.2] - 2026-03-25

#### New Features
- **Adapter Refactoring Plan**: Created adapter refactoring plan documentation for future architecture optimization

---

### [1.4.1] - 2026-03-24

#### New Features
- **Value Object Pattern**: Introduced InstanceId value object for improved code type safety

---

### [1.4.0] - 2026-03-24

#### Bug Fixes
- Gracefully fixed ConfigMergeService blocking call warning
- Optimized reactive programming model

---

### [1.2.5] - 2025-11-26

#### Improvements
- Merged remote branch updates
- Code synchronization and stability improvements

---

### [1.1.2] - 2025-10-30

#### Improvements
- Merged remote branch updates
- Code stability improvements

---

### [1.1.1] - 2025-10-28

#### Improvements
- **Frontend Routing Optimization**: Improved routing and authentication flow
- Code refactoring and cleanup

---

### [1.1.0] - 2025-10-28

#### Improvements
- Merged remote branch updates
- Feature stability improvements

---

### [1.0.0] - 2025-10-16

#### New Features
- **First Official Release**: JAiRouter project first official release version
- Basic gateway functionality
- Core adapter support

---

### [0.9.2] - 2025-09-30

#### New Features
- **ApiKey Model Unification**: Merged ApiKeyInfo and ApiKeyProperties into unified ApiKey model

---

### [0.9.1] - 2025-09-12

#### Bug Fixes
- Fixed merge errors
- Code stability improvements

---

### [0.9.0] - 2025-09-10

#### New Features
- **Web Console Architecture**: Added Web Console architecture design documentation
- Frontend management console planning

---

### [0.8.2] - 2025-09-05

#### Improvements
- Merged remote branch updates
- Code synchronization

---

### [0.8.1] - 2025-09-03

#### Improvements
- Updated project version number
- Version management standardization

---

### [0.7.3] - 2025-08-27

#### New Features
- **JWT Authentication**: Implemented JWT authentication and user management functionality
- Security module basic functionality

---

### [0.7.2] - 2025-08-27

#### New Features
- **Distributed Tracing Documentation**: Added distributed tracing system documentation

---

### [0.7.1] - 2025-08-27

#### New Features
- **Documentation Optimization**: Integrated Google Ads and optimized documentation styling

---

### [0.7.0] - 2025-08-22

#### New Features
- **Slow Query Alerts**: Added slow query alert functionality
- Monitoring module enhancement

---

### [0.6.1] - 2025-08-19

#### New Features
- **Internationalization Support**: Added internationalization and code compression support
- Documentation system enhancement

---

### [0.6.0] - 2025-08-18

#### New Features
- **Security Authentication**: Implemented API Key and JWT authentication functionality
- Security module core functionality

---

### [0.5.0] - 2025-08-18

#### New Features
- **Documentation Management**: Refactored documentation management workflow with unified management script
- Documentation system refactoring

---

### [0.4.0] - 2025-08-15

#### New Features
- **Prometheus Alert Rules**: Added Prometheus alert rules guide and configuration
- Added ALERT_RULES_GUIDE.md file
- Added alertmanager.yml configuration
- Created docker-compose-monitoring.yml monitoring stack configuration

---

### [Unreleased] - In Development

#### New Features
- **Security Module**: Complete enterprise-grade security features including API Key authentication, JWT token support, and bidirectional data sanitization
- **Multi-tenancy Support**: Tenant isolation, resource quotas, and tenant-based configuration management
- **Authentication and Authorization**: API Key authentication mechanism, JWT Token support, OAuth 2.0 integration, and Role-Based Access Control (RBAC)
- **Data Protection**: Request/response data obfuscation, encrypted storage of sensitive information, and security audit logs
- **H2 Database Support**: H2 embedded database as default storage with automatic data migration for configuration, security audit, API keys, and JWT accounts
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack with business and infrastructure metrics collection
- **Distributed Tracing**: End-to-end distributed tracing with Jaeger/Zipkin integration for full request lifecycle tracking
- Complete documentation system and user guides
- Multi-language documentation support (Chinese/English)
- Enhanced monitoring and alerting capabilities
- More adapter support

#### Improvements & Optimizations
- **Spring Security Integration**: Full integration with Spring Security framework for robust authentication and authorization
- **Cache Layer Optimization**: Redis cache optimization for improved performance
- **Database Support**: Added support for PostgreSQL and MySQL databases
- **H2 Storage Performance**: 5-20x performance improvement over file storage for configuration and security data operations
- **Docker Build Optimization**: China-optimized Docker builds with Alibaba Cloud Maven mirror, 5-10x faster dependency downloads
- Performance optimization and memory management improvements
- Better error handling and logging
- Configuration validation and user experience enhancements

#### Bug Fixes
- Fixed known memory leak issues
- Resolved race conditions in concurrent scenarios
- Improved stability of configuration hot updates
- Security scanning and vulnerability fixes
- Fixed configuration file path issues in container environments
- Resolved DNS resolution issues in Docker containers

---

### [0.3.2] - 2025-08-20

#### New Features
- **Monitoring Integration**: Complete Prometheus + Grafana monitoring stack
- **Performance Metrics**: Added collection of business and infrastructure metrics
- **Alert Rules**: Pre-configured alert rules and notification mechanisms
- **Monitoring Dashboard**: Professional Grafana dashboard templates

#### Improvements & Optimizations
- **Metrics Optimization**: Optimized metrics collection performance to reduce impact on main business
- **Memory Management**: Improved memory usage and cleanup mechanisms for metric data
- **Configuration Simplification**: Simplified monitoring-related configuration parameters

#### Bug Fixes
- Fixed inaccurate monitoring metrics under high concurrency
- Resolved memory leak issues after long-term operation
- Fixed Prometheus metrics format compatibility issues

#### Technical Debt
- Refactored monitoring module code structure
- Improved unit test coverage
- Optimized build and deployment processes

---

### [0.3.1] - 2025-08-15

#### New Features
- **China Accelerated Build**: Docker builds optimized specifically for Chinese users
- **Alibaba Cloud Mirror**: Using Alibaba Cloud Maven mirror to accelerate dependency downloads
- **Build Script**: Added `docker-build-china.sh` build script
- **Maven Profile**: Added china profile support

#### Improvements & Optimizations
- **Build Speed**: Build speed for Chinese users improved by 5-10 times
- **Network Optimization**: Optimized network connections and timeout configurations
- **Documentation Enhancement**: Added China-specific build guides

#### Bug Fixes
- Fixed dependency download timeout issues in Chinese network environments
- Resolved unstable Maven repository connection issues
- Fixed network errors during Docker builds

---

### [0.3.0] - 2025-08-14

#### New Features
- **Docker Containerization**: Complete Docker deployment support
- **Multi-environment Deployment**: Support for development, testing, and production environment configurations
- **Docker Compose**: Provided complete container orchestration configuration
- **Health Check**: Container-level health check mechanism
- **Monitoring Integration**: Basic monitoring metrics exposure

#### Improvements & Optimizations
- **Image Optimization**: Multi-stage builds, production image approximately 200MB
- **Security Hardening**: Running as non-root user, principle of least privilege
- **Performance Tuning**: JVM parameter optimization in container environments
- **Log Management**: Log collection and rotation in containerized environments

#### Bug Fixes
- Fixed configuration file path issues in container environments
- Resolved configuration loss after container restarts
- Fixed network communication issues between containers

#### Breaking Changes
- Default configuration file path changed from `./config` to `/app/config`
- Environment variable naming convention adjusted

---

### [0.2.1] - 2025-08-12

#### New Features
- **Scheduled Cleanup Task**: Automatic cleanup of inactive rate limiters to prevent memory leaks
- **Memory Optimization**: Improved memory usage patterns to reduce GC pressure
- **Enhanced Client IP Rate Limiting**: More precise client IP identification and rate limiting
- **Automatic Configuration File Merging**: Support for intelligent merging of multi-version configuration files

#### Improvements & Optimizations
- **Performance Improvement**: Rate limiter performance optimization to reduce lock contention
- **Enhanced Monitoring**: Added monitoring metrics for memory usage and cleanup tasks
- **Log Optimization**: Improved log format and performance
- **Error Handling**: Better exception handling and error recovery mechanisms

#### Bug Fixes
- Fixed memory leak issues after long-term operation
- Resolved rate limiter race conditions in high-concurrency scenarios
- Fixed thread safety issues during configuration hot updates
- Resolved client IP acquisition issues in proxy environments

#### Technical Improvements
- Refactored rate limiter cleanup mechanism
- Improved unit test coverage to 85%
- Optimized code quality check rules

---

### [0.2.0] - 2024-08-11

#### New Features
- **Rate Limiting Mechanism**: Support for Token Bucket, Leaky Bucket, Sliding Window, and Warm Up rate limiting algorithms
- **Circuit Breaker**: Implemented circuit breaker pattern with support for failure thresholds, recovery detection, and fallback strategies
- **Fallback Strategies**: Support for default responses and cache fallback
- **Configuration Persistence**: Support for both in-memory and file storage backends
- **Dynamic Configuration Updates**: Runtime updates for service instances, weights, rate limiting, and circuit breaking configurations

#### Improvements & Optimizations
- **Performance Optimization**: Reactive programming model supporting high-concurrency processing
- **Configuration Management**: Automatic configuration file merging and version management
- **Error Handling**: Comprehensive exception handling and error recovery mechanisms
- **Monitoring Metrics**: Added rate limiting and circuit breaking related monitoring metrics

#### Bug Fixes
- Fixed thread safety issues in load balancer during instance changes
- Resolved data consistency issues during configuration updates
- Fixed memory leak issues in high-concurrency scenarios

#### API Changes
- Added dynamic configuration management API (`/api/config/instance/*`)
- Added configuration file merging API (`/api/config/merge/*`)
- Extended health check API to include more status information

---

### [0.1.0] - 2025-08-04

#### New Features
- **Basic Gateway**: Unified `/v1/*` API gateway supporting OpenAI-compatible format
- **Service Type Support**: Chat, Embedding, Rerank, TTS, STT, Image Generation, Image Editing
- **Adapter Pattern**: Support for GPUStack, Ollama, VLLM, Xinference, LocalAI, and OpenAI adapters
- **Load Balancing**: Implemented Random, Round Robin, Least Connections, and IP Hash strategies
- **Health Check**: Independent status interface per service, automatic removal of unavailable instances
- **Configuration Management**: Static configuration support based on YAML

#### Technical Features
- **Spring Boot 3.5.x**: Based on the latest Spring Boot framework
- **Reactive Programming**: Using Spring WebFlux and Reactor Core
- **Code Quality**: Integrated Checkstyle, SpotBugs, JaCoCo code quality tools
- **API Documentation**: Automatically generated API documentation using SpringDoc OpenAPI
- **Unit Testing**: Unit tests for core functions including load balancing and health checks

#### Project Structure
- Established clear modular architecture
- Defined unified coding standards and best practices
- Established complete build and testing processes

---

## Upgrade Guide

### Upgrading from 0.3.1 to 0.3.2

#### Configuration Changes
```yaml
# New monitoring configuration
monitoring:
  metrics:
    enabled: true
    categories:
      - system
      - business
      - infrastructure
```

#### Deployment Changes
- Added Prometheus and Grafana containers
- Updated `docker-compose.yml` configuration
- Imported new Grafana dashboards

#### Notes
- Monitoring functionality is enabled by default, which may add slight performance overhead
- New monitoring endpoints require corresponding network configuration

### Upgrading from 0.2.1 to 0.3.0

#### Breaking Changes
- Configuration file path change: `./config` → `/app/config`
- Environment variable naming adjustment

#### Migration Steps
1. Update configuration file paths
2. Adjust environment variable names
3. Update deployment scripts and container configurations

### Upgrading from 0.1.0 to 0.2.0

#### New Dependencies
- No additional dependencies required, all features are built-in

#### Configuration Extensions
```yaml
# New rate limiting configuration
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10

# New circuit breaker configuration
      circuit-breaker:
        failure-threshold: 5
        recovery-timeout: 30s
        success-threshold: 3
```

## Known Issues

### Current Version (0.3.2)
- In extremely high concurrency scenarios (>10k RPS), monitoring metrics may experience slight delays
- Docker containers may experience slow DNS resolution in certain network environments

### Historical Issues
- ~~0.3.1: Build timeouts in Chinese network environments~~ (Fixed)
- ~~0.2.1: Memory leaks after long-term operation~~ (Fixed)
- ~~0.2.0: Race conditions during configuration updates in high-concurrency scenarios~~ (Fixed)

## Contributors

Thank you to all developers who have contributed to the JAiRouter project:

- **Core Team**: Responsible for architecture design and core feature development
- **Community Contributors**: Provided feature suggestions, bug reports, and code contributions
- **Documentation Team**: Improved project documentation and user guides
- **Testing Team**: Conducted functional testing and performance verification

## Feedback and Suggestions

If you encounter issues or have improvement suggestions during usage, please feel free to provide feedback through the following channels:

- **GitHub Issues**: [Submit issue report](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [Participate in discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Email Contact**: jairouter@example.com

We value every piece of feedback and will respond and address them promptly.

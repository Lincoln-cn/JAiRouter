# RBAC 权限管理

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-09-02
> **Git 提交**: -
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 自 **v2.9.8** 起提供**数据驱动的 RBAC（基于角色的访问控制）**体系：以 `module:resource:action` 权限码为唯一权限原子，将「权限码 → 角色 → JWT → URL 权限矩阵 → 菜单」串成一条完整链路：

1. **权限码**：53 个 `module:resource:action` 权限码（`action` ∈ `read` / `write` / `manage` / `view`），同时驱动后端 URL 访问判定与前端菜单/路由可见性。
2. **角色模板**：启动时自动在 `role_permissions` 表种入 4 个内置角色模板（ADMIN / OPERATOR / USER / VIEWER），每个角色对应一组权限码（表为空则全量播种；表非空则执行只增不删的增量收敛，见「角色模板」一节）。
3. **JWT 内嵌**：登录成功后，服务端按角色从 `role_permissions` 查询权限码并写入 JWT 的 `permissions` claim（角色仍保留在 `roles` claim；权限码无 `ROLE_` 前缀）。
4. **URL 权限矩阵**：`PermissionRuleRegistry` 登记 66 条「{HTTP 方法, URL 模式} → 权限码」规则，`PermissionAuthorizationManager` 在网关层对每个 `/api/**` 请求完成权限判定。
5. **菜单与路由**：前端 `menu.ts` 数据驱动渲染（v2.9.8 由 11 组重分类为 8 组），`usePermission` 过滤菜单、路由守卫 `meta.permissions` 拦截无权限访问。

核心设计原则：

- **数据驱动、禁方法级注解**：同步返回的 Controller 权限判定一律走 URL 规则，禁止方法级 `@PreAuthorize`（RBAC 500 铁律——响应式安全框架下同步方法注解不生效会导致 500）。
- **ADMIN 直通**：命中 URL 规则时，`ROLE_ADMIN` 直接放行，无需逐一校验权限码；未命中且要求拒绝时，`ROLE_ADMIN` 同样短路放行。
- **未命中端点默认拒绝（fail-closed）**：不在规则表中的 `/api/**` 端点按 `RbacUnmatchedPolicy` 姿态判定，**默认 `DENY_ALL`（GET 与写方法一律拒绝，仅 ADMIN 直通）**。旧行为可通过配置回退，详见「授权决策」一节。
- **显式豁免清单**：有意不登记 URL 规则、仅要求登录的端点集中在 `RbacExemptEndpoints`，每条豁免必须写明理由，覆盖自检分别报告 EXEMPT 与 MISSING。
- **`/v1/**` 独立于本体系**：OpenAI 兼容推理端点仅要求认证、不纳入权限矩阵；服务级访问控制由 **API Key 的服务类型权限**在适配器层完成，本 RBAC 不影响既有 API Key 调用。

## 权限码体系

### 格式与语义

权限码格式为 `module:resource:action`：

| 段 | 说明 | 取值 |
|----|------|------|
| `module` | 模块 | `overview` / `config` / `lb` / `cb` / `rl` / `callhistory` / `monitoring` / `tracing` / `security` / `system` / `ai` / `actuator` |
| `resource` | 模块内资源 | 见下方全量清单 |
| `action` | 操作类型 | `read` / `write` / `manage` / `view` |

`action` 语义：

| 取值 | 语义 | 说明 |
|------|------|------|
| `read` | 只读 | 查看、查询类操作，不含任何写副作用 |
| `write` | 写入 | 创建、更新、删除、启停等写操作，通常与同资源的 `read` 成对出现 |
| `manage` | 管理 | 管理类操作，用于安全/系统等敏感模块（如 `security:*:manage`、`system:*:manage`） |
| `view` | 查看 | 专用查看动作，目前仅 `callhistory:view`；独立于 read/write 过滤器 |

### 全量权限码清单（53 个）

| 模块 | 权限码 | 说明 |
|------|--------|------|
| overview | `overview:dashboard:read` | 概览仪表盘 |
| config | `config:services:read` / `config:services:write` | 模型服务配置读/写 |
| config | `config:instances:read` / `config:instances:write` | 实例配置读/写 |
| config | `config:versions:read` / `config:versions:write` | 配置版本读/写 |
| config | `config:persistence:read` / `config:persistence:write` | 状态持久化读/写 |
| config | `config:adapters:read` / `config:adapters:write` | Adapter 配置读/写 |
| config | `config:rules:read` / `config:rules:write` | 路由规则配置读/写 |
| config | `config:pools:read` / `config:pools:write` | 资源池配置读/写 |
| config | `config:circuitbreaker:read` / `config:circuitbreaker:write` | 熔断器配置读/写 |
| config | `config:callhistory:read` / `config:callhistory:write` | 调用历史配置读/写 |
| config | `config:cache:read` / `config:cache:write` | 响应缓存状态查询 / 失效管理（read v2.10.2；write v2.9.10） |
| config | `config:quota:read` / `config:quota:write` | 配额账本配置读/写 |
| config | `config:validation:read` / `config:validation:write` | 配置校验读/写 |
| lb | `lb:monitoring:read` | 负载均衡监控 |
| lb | `lb:config:write` | 负载均衡策略配置（写） |
| cb | `cb:monitoring:read` | 熔断器监控 |
| cb | `cb:history:read` | 熔断器历史 |
| rl | `rl:monitoring:read` | 限流监控 |
| callhistory | `callhistory:view` | 调用历史查看 |
| monitoring | `monitoring:metrics:read` | 指标监控 |
| monitoring | `monitoring:slowquery:read` | 慢查询分析 |
| monitoring | `monitoring:tokenusage:read` | Token 用量统计 |
| monitoring | `monitoring:modelstats:read` | 模型统计 |
| monitoring | `monitoring:routing:read` | 路由监控 |
| monitoring | `monitoring:quota:read` | 配额监控 |
| monitoring | `monitoring:exceptions:read` | 异常事件查看 |
| monitoring | `monitoring:exceptions:write` | 异常事件处理 |
| monitoring | `monitoring:config:write` | 监控/运维配置写 |
| tracing | `tracing:dashboard:read` | 追踪仪表盘 |
| tracing | `tracing:search:read` | 追踪搜索 |
| tracing | `tracing:config:manage` | 追踪安全配置管理 |
| security | `security:apikeys:manage` | API Key 管理 |
| security | `security:jwttokens:manage` | JWT 令牌管理 |
| security | `security:blacklist:manage` | 黑名单管理 |
| security | `security:audit:read` | 安全审计日志（只读） |
| security | `security:audit:write` | 安全审计日志（写） |
| security | `security:sanitization:manage` | PII / 脱敏配置管理 |
| system | `system:accounts:manage` | 账户管理 |
| system | `system:permissions:manage` | 权限管理 |
| ai | `ai:playground:use` | AI 试验场使用 |
| actuator | `actuator:admin:manage` | Actuator 基础设施管理 |

说明：

- 合计 53 个：config 模块 12 个资源全部 read/write 成对 = 24 码，其余模块见上表。
- 上表顺序即全量列表（`GET /api/security/permissions`）与权限管理 UI 权限树的展示顺序。
- 少数权限码天然不成对（如 `lb:config:write`、`callhistory:view`、`ai:playground:use`），按其资源语义定义，不强行补 read/write。
- 部分权限码仅供菜单/路由可见性使用（如 `overview:dashboard:read`、`ai:playground:use`）；`ai:playground:use` 当前不被任何 URL 规则引用，试验场实际通过豁免路径 `/api/v1/**` 访问（见「调用方对照矩阵」）。
- 未登记 URL 规则的 `/api/**` 端点不再默认放行，按「授权决策」一节的姿态判定（默认拒绝）。

## 角色模板

系统内置 4 个角色模板（`RolePermissionSeeder` 启动时自动播种）：

| 角色 | 权限码数 | 权限范围 | 说明 |
|------|:-------:|----------|------|
| ADMIN | 53 | 全量权限码 | 超集；URL 规则直通，JWT 签发时内嵌全量码 |
| OPERATOR | 43 | 全部 `:read` + `:write` 码 | 排除 `system:*`、`security:*:manage`、`actuator:*`；保留 `security:audit:read` / `security:audit:write`，不含 `callhistory:view` |
| USER | 28 | 仪表盘 + config 只读 + lb/cb/rl + monitoring 只读（含 exceptions:read）+ tracing dashboard/search + AI 试验场 | 只读为主，唯一写码 `lb:config:write`；不含调用历史查看 |
| VIEWER | 27 | 全部 `:read` 权限码 | 纯只读角色；不含 `callhistory:view`、`ai:playground:use` 等非 `:read` 码 |

### 角色差异

- **OPERATOR vs ADMIN**：OPERATOR 面向日常运维，拥有所有读/写权限，但不含系统管理（`system:*`）、安全管理类 `manage`（`security:apikeys:manage` / `security:jwttokens:manage` / `security:blacklist:manage` / `security:sanitization:manage`）与基础设施管理（`actuator:admin:manage`）；`security:audit:read` / `security:audit:write` 保留给 OPERATOR。
- **USER vs OPERATOR**：USER 面向普通查看用户，仅 config **只读** + lb/cb/rl（含唯一写码 `lb:config:write`）+ monitoring 只读 + tracing 仪表盘/搜索 + AI 试验场；不含任何 `manage` 码与账户管理，也不含调用历史查看。
- **VIEWER vs USER**：VIEWER 是三者中最窄的纯只读角色，仅保留全部 `:read` 码；不含 USER 唯一的写码 `lb:config:write`，也不含 `ai:playground:use`。
- 三个非 ADMIN 角色默认**均不含** `callhistory:view`（专用查看码，以 `:view` 结尾，落在 `:read`/`:write` 过滤器之外）；如业务需要，可在权限管理 UI 中为角色额外勾选该码。**实践上 `/api/call-history/**` 仅 ADMIN 可用。**
- 三个非 ADMIN 角色**均不含**任何 `security:*:manage` 码（仅 ADMIN 持有）；因此阶段 2 补登的 5 条 `security:*:manage` 规则实际不影响任何非 ADMIN 角色的访问（详见「授权决策」）。

### 增量收敛（只增不删）

`RolePermissionSeeder` 在 `role_permissions` 表**非空**时不再跳过，而是执行**只增不删**的增量收敛：

| 表状态 | 行为 |
|--------|------|
| 表为空 | 全量种入 4 个角色模板（全新安装路径） |
| 表非空 | 逐角色判定：当前权限码集合是模板的子集 → 仅补种「模板 ∩ URL 规则所需 − 已持有」的缺失码；持有模板外权限码 → 视为手工定制，**整个角色跳过**；无任何权限行的角色同样跳过 |

收敛只执行 `INSERT`，**绝不删除或修改既有行**。写入后会清空 `RolePermissionService` 权限缓存，避免启动窗口内已签发 JWT 的旧权限集合继续生效。

运维影响：已初始化部署在升级后启动时，缺失的「规则所需」权限码会被自动回填。真实数据库上实测：OPERATOR +9、USER +4、VIEWER +4、ADMIN +10。手工定制过的角色不受影响（整个跳过）。

## 工作流程

一次带权限判定的完整请求流程如下：

1. **登录签发 JWT**：客户端 `POST /api/auth/jwt/login`。`AccountManager` 校验凭据（v2.9.8：YAML 静态账户优先，未命中时回退数据库账户——Web 账户管理 API 创建的账户可直接登录），随后按用户角色经 `RolePermissionService` 查询 `role_permissions` 表（Caffeine 缓存 5 分钟；ADMIN 短路返回全量码），将权限码写入 JWT `permissions` claim 并返回令牌。
2. **令牌解析**：请求携带 `Authorization: Bearer {token}`（或自定义 JWT 头）。`DefaultJwtTokenValidator` 解析令牌：`roles` claim → `ROLE_` 前缀 authority；`permissions` claim → 无前缀 authority（两者语义隔离，避免与 API Key 的 `ROLE_*` 权限冲突）。
3. **URL 规则匹配**：`SecurityConfiguration` 将 `/api/**` 全部交给 `PermissionAuthorizationManager`；其从 `PermissionRuleRegistry` 按「HTTP 方法 + URL 模式」首条命中查找所需权限码（共 66 条规则）。
4. **权限判定**：按下节「授权决策」的三步判定执行。
5. **结果**：放行进入 Controller；拒绝则返回 403。

## 授权决策（fail-closed）

`PermissionAuthorizationManager` 对 `/api/**` 的判定为固定三步：

1. **命中 `PermissionRuleRegistry` 规则** → 要求 `ROLE_ADMIN` **或** 携带该规则所需权限码；
2. **未命中规则，但命中显式豁免清单 `RbacExemptEndpoints`** → **仅要求登录（authenticated）**（与旧回退行为等效，但现在显式可审计，每条豁免写明理由）；
3. **两者都未命中（MISSING）** → 由未命中姿态管控；**默认拒绝**（`ROLE_ADMIN` 仍短路放行）。

判定场景：

| 场景 | 判定结果 |
|------|----------|
| 命中 URL 规则 + 角色为 ADMIN | 放行（直通） |
| 命中 URL 规则 + 携带所需权限码 | 放行 |
| 命中 URL 规则 + 无所需权限码（且非 ADMIN） | 拒绝（403） |
| 未命中规则 + 命中豁免清单 | 放行（仅要求登录） |
| 未命中规则 + 未豁免（MISSING） | 按姿态判定，默认拒绝；ADMIN 直通 |

### 未命中姿态

历史与当前姿态对照：

| 姿态 | 配置值 | 未命中写方法 | 未命中 GET |
|------|--------|--------------|------------|
| 遗留 fail-open | `AUTHENTICATED` | 放行（仅要求登录） | 放行（仅要求登录） |
| 阶段 2 | `DENY_WRITES` | 拒绝 | 放行（仅要求登录） |
| **当前默认** | `DENY_ALL` | 拒绝 | **拒绝** |

配置：

| 配置键 | 取值 | 说明 |
|--------|------|------|
| `jairouter.security.rbac.unmatched-policy` | `AUTHENTICATED` \| `DENY_WRITES` \| `DENY_ALL` | **默认 `DENY_ALL`** |
| `jairouter.security.rbac.write-fail-closed.enabled`（遗留） | `true` \| `false` | **仅当 `unmatched-policy` 未配置时生效**：`false`→`AUTHENTICATED`，`true`→`DENY_WRITES`；两者同配时新键优先，永不矛盾 |

**无改码回退**：设置 `jairouter.security.rbac.unmatched-policy=AUTHENTICATED`（完全回退遗留行为）或 `=DENY_WRITES`（回退阶段 2 姿态）。

### 覆盖自检与可观测

- 启动时执行 RBAC 端点覆盖自检（`RbacEndpointCoverageChecker`），把已映射 `/api/**` 端点归类为 **covered**（规则命中）/ **EXEMPT**（豁免命中）/ **MISSING**（可行动缺口）。存在 MISSING 时输出 **WARN** 并列出未覆盖端点。
- `/actuator/health` 增加 `rbacEndpointCoverage` 详情项（`RbacEndpointCoverageHealthIndicator`），**EXEMPT 与 MISSING 分开报告**（`exemptCount` / `missingCount` / `exemptEndpoints` / `missingEndpoints`）。健康状态保持 **UP**——该项仅作信息参考，不应触发探针重启。

### 显式豁免清单

`RbacExemptEndpoints` 中的端点是**有意不登记 URL 权限规则**的「仅要求登录」端点。它们要么是自助端点（方法级 `@PreAuthorize` 已约束属主），要么是服务代理面（授权由 API-Key 服务类型权限 + 配额控制，而非 URL 权限矩阵）。每条豁免在源码中写明理由，审查可追溯：

| 方法 + 路径 | 豁免理由 |
|-------------|----------|
| `write /api/v1/**`（POST/PUT/DELETE/PATCH） | AI 服务代理面（chat / embeddings / rerank / audio / images）：授权由 API-Key 服务类型权限 + 配额控制，**不走 URL 权限矩阵**。API-Key 主体仅携带 `ROLE_<SERVICE>`，若登记权限码规则会 403 全部 API-Key 服务调用 |
| `POST /api/auth/jwt/refresh` | 自助刷新本人令牌；任意登录用户可用，无属主越权面（令牌即身份） |
| `POST /api/auth/jwt/revoke` | 自助撤销本人令牌；方法级 `@PreAuthorize(hasRole('ADMIN') or authentication.name == #request.userId)` 已约束属主 |
| `GET /api/auth/jwt/tokens` | 自助令牌列表；方法级 `@PreAuthorize` 约束属主；登记权限码规则会破坏普通用户管理本人令牌 |
| `GET /api/auth/permissions` | 当前登录用户自身权限码；控制台菜单渲染依赖 |
| `GET /api/models` | 模型目录，UI 与客户端公共读取，无敏感操作面 |
| `POST /api/token-usage/record` | Token 用量上报摄取面：内部代理经进程内 `TokenUsageRecorder` 落库，不经此端点；对外公开摄取 API，调用方可能为 API-Key，登记权限码会阻断上报 |
| `POST /api/token-usage/record/batch` | 批量 Token 用量上报摄取面：同上 |

### 阶段 2 补登的 URL 规则

以下 5 个端点在阶段 2 获得了显式 URL 规则（消除覆盖缺口）：

| 端点 | 所需权限码 |
|------|-----------|
| `GET /api/auth/jwt/blacklist/stats` | `security:blacklist:manage` |
| `GET /api/auth/jwt/cleanup/stats` | `security:jwttokens:manage` |
| `POST /api/auth/jwt/cleanup` | `security:jwttokens:manage` |
| `POST /api/auth/jwt/revoke/batch` | `security:jwttokens:manage` |
| `GET /api/auth/jwt/tokens/{tokenId}` | `security:jwttokens:manage` |

**这没有收窄任何人的访问**：这 5 个端点的方法级 `@PreAuthorize` 本来就是 `hasRole('ADMIN')`，且四个角色模板中只有 ADMIN 持有 `security:*:manage` 码（ADMIN 还会直通）。

## 调用方对照矩阵

下表按能力组对照五类调用方在当前姿态（`DENY_ALL`）下的可用面：

| 能力组 | ROLE_ADMIN JWT | 非 ADMIN JWT（OPERATOR/USER/VIEWER） | API-Key（`X-API-Key`） | 匿名 |
|--------|:--------------:|:-----------------------------------:|:----------------------:|:----:|
| `/v1/**` 原生推理面（`GET /v1/models`、`POST /v1/chat/completions`、`POST /v1/embeddings`、`POST /v1/rerank`、`POST /v1/messages`、`POST /v1/messages/count_tokens`） | ✔（仅要求登录） | ✔（仅要求登录） | ✔（API-Key 服务类型权限 + 配额） | ✘ |
| `/v1/debug/**`（仅 `jairouter.debug.endpoints.enabled=true` 时存在，默认关闭） | ✔（仅要求登录） | ✔（仅要求登录） | ✔（仅要求登录） | ✘ |
| 豁免清单端点（`/api/v1/**` 写、JWT 自助、`/api/auth/permissions`、`/api/models`、token-usage 上报） | ✔ | ✔（仅要求登录） | ✔（仅要求登录） | ✘ |
| 命中 URL 规则的 `/api/**`（配置/监控/安全等） | ✔（直通） | ✔ 按角色权限码（见角色模板） | **✘ 一律 403** | ✘ |
| 未命中规则且未豁免的 `/api/**`（MISSING） | ✔（短路直通） | **✘ 403（默认 DENY_ALL）** | **✘ 403** | ✘ |
| `/api/call-history/**` | ✔（`callhistory:view`） | ✘（三角色均无 `callhistory:view`，实际 ADMIN-only） | ✘ | ✘ |
| `/api/model-stats/**` | ✔（`hasRole('ADMIN')`） | ✘ | ✘ | ✘ |
| `/actuator/**`（除 health/info/prometheus） | ✔（`hasRole('ADMIN')`） | ✘ | ✘ | ✘ |
| `/api/health-status/**`、`/ws/**` | ✔（仅要求登录） | ✔（仅要求登录） | ✘（JWT 形态） | ✘ |
| `/actuator/health`、`/actuator/info`、`/actuator/prometheus` | ✔ | ✔ | ✔ | ✔（permitAll） |
| `/admin/**`（静态 SPA）、`/favicon.ico` | ✔ | ✔ | ✔ | ✔（permitAll） |
| `POST /api/auth/jwt/login`、`POST /api/auth/jwt/validate` | ✔ | ✔ | ✔ | ✔（permitAll） |
| `/swagger-ui/**`、`/v3/api-docs/**`、`/webjars/**` | ✔ | ✔ | ✔ | 默认需登录；仅 `jairouter.security.docs-public=true` 时 permitAll |

API-Key 调用方要点（外部集成方必读）：

- API-Key 主体**只携带 `ROLE_<SERVICE>`** authority（`ApiKeyAuthentication` 将权限列表大写并加 `ROLE_` 前缀）；`ApiKeyService.validatePermissions` 只接受 `ServiceTypeConstants` 服务类型（`chat` / `embedding` / `rerank` / `tts` / `stt` / `imgGen` / `imgEdit`），非法值被丢弃；遗留 `READ` 扩展为全部服务类型。
- 因此 API-Key **永远不可能持有 `ROLE_ADMIN`，也不持有任何权限码** ⇒ 所有命中规则的 `/api/**` 路径对 API-Key 一律 403；阶段 3 之后未命中规则的 `/api/**` 路径同样 403。
- API-Key 的可用面：**全部 `/v1/**`**（`SecurityConfiguration` 将 `/v1/**` 映射为 `authenticated()`，不走权限管理器）+ 上表豁免集。
- `/v1/**` 原生面（OpenAI + Anthropic 六个端点）**不受三次变更影响**，现有集成无需改动。
- `/v1/debug/**` 仅在 `jairouter.debug.endpoints.enabled=true` 时存在（默认关闭），且只要求认证 ⇒ 若开启，任何已认证主体（含 API-Key）均可调用。**生产环境建议保持关闭。**

非 ADMIN 角色要点：

- USER 恰好一个写码：`lb:config:write`；VIEWER 只有 `:read` 码。
- 无任何非 ADMIN 角色持有 `security:*:manage`；无任何非 ADMIN 角色持有 `callhistory:view`。
- `ai:playground:use` 当前不被任何 URL 规则引用（仅菜单/权限链路使用），试验场实际经豁免路径 `/api/v1/**` 访问。

## 管理 API

权限管理提供 3 个管理端点 + 1 个当前用户端点（`PermissionManagementController`）：

| 方法 | 路径 | 说明 | 所需权限 |
|------|------|------|----------|
| GET | `/api/security/permissions` | 全部权限码（53 个，按清单顺序） | `system:permissions:manage` |
| GET | `/api/security/permissions/roles` | 全部角色及其权限码（角色名 → 权限码列表） | `system:permissions:manage` |
| PUT | `/api/security/permissions/roles/{roleName}` | 整体替换指定角色的权限码集合 | `system:permissions:manage` |
| GET | `/api/auth/permissions` | 当前登录用户权限码（ADMIN 返回全量 53 码） | 任意已登录用户（豁免清单） |

> `/api/security/permissions/**` 由 URL 规则保护（`system:permissions:manage`）；`GET /api/auth/permissions` 在豁免清单中，仅要求登录（控制台菜单渲染依赖）。

### 1. 登录获取令牌

```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "admin123"}'
```

响应 `data.token` 即 JWT（内嵌 `roles` 与 `permissions` claim）。

### 2. 查询全部权限码

```bash
curl http://localhost:8080/api/security/permissions \
     -H "Authorization: Bearer {token}"
```

响应 `data` 为 53 个权限码数组（顺序与全量清单一致）。

### 3. 查询角色权限

```bash
curl http://localhost:8080/api/security/permissions/roles \
     -H "Authorization: Bearer {token}"
```

响应 `data` 为对象：`{"ADMIN": [...], "OPERATOR": [...], ...}`。

### 4. 更新角色权限（整体替换）

```bash
curl -X PUT http://localhost:8080/api/security/permissions/roles/OPERATOR \
     -H "Authorization: Bearer {token}" \
     -H "Content-Type: application/json" \
     -d '["config:services:read", "config:services:write", "monitoring:metrics:read"]'
```

- `roleName` 大小写不敏感（存储统一大写）；请求体为权限码 JSON 数组，将整体替换该角色权限。
- 非法角色名返回 400（`INVALID_ROLE`）；非法权限码返回 400（`INVALID_PERMISSION`）。
- 更新成功后清空服务端权限缓存，但**已签发的 JWT 不会更新**——权限变更需重新登录生效。

### 5. 查询当前用户权限

```bash
curl http://localhost:8080/api/auth/permissions \
     -H "Authorization: Bearer {token}"
```

ADMIN 直接返回全量 53 码；其他角色返回其 JWT `permissions` claim 中的权限码。

## 权限管理 UI

「系统管理 → 权限管理」页面（路由 `/system/permissions`，需 `system:permissions:manage`）提供图形化权限配置：

1. **角色下拉**：选择 ADMIN / OPERATOR / USER / VIEWER，自动加载该角色当前权限码并回显勾选。
2. **权限码树**：53 个权限码按模块分组的树形勾选（叶子为权限码，父节点自动联动）。
3. **保存权限**：点击「保存权限」调用 `PUT /api/security/permissions/roles/{roleName}`，整体替换该角色权限。
4. **生效说明**：保存成功后提示「权限变更后需重新登录方可生效」——权限内嵌于 JWT；服务端角色权限缓存约 5 分钟过期（仅影响后续登录的令牌签发）。

> 权限管理页入口位于系统管理菜单组；账户（YAML 静态账户 / Web 账户管理创建的数据库账户）由「系统管理 → 账户管理」维护。

## 菜单与路由

v2.9.8 将 Web 菜单由 11 组重分类为 **8 组 34 项**，前端 `menu.ts` 数据驱动 + 权限过滤：

| 菜单组 | 子项数 | 子项（权限码） |
|--------|:-----:|----------------|
| 概览 | 1 | 仪表板（`overview:dashboard:read`） |
| 模型服务 | 4 | 服务管理 / 实例管理 / 版本管理 / Adapter 管理（对应 `config:services|instances|versions|adapters:read`） |
| 流量治理 | 8 | 路由规则（`config:rules:read`）/ 负载均衡监控（`lb:monitoring:read`）/ 负载均衡策略（`lb:config:write`）/ 熔断器监控（`cb:monitoring:read`）/ 熔断器历史（`cb:history:read`）/ 熔断器配置（`config:circuitbreaker:read`）/ 限流监控（`rl:monitoring:read`）/ 资源池（`config:pools:read`） |
| 数据记录 | 6 | 调用历史仪表盘、调用列表（`callhistory:view`）/ Token 统计（`monitoring:tokenusage:read`）/ 慢调用（`monitoring:slowquery:read`）/ 异常事件管理、异常统计分析（无权限码，所有已登录用户可见） |
| 链路追踪 | 3 | 追踪仪表盘（`tracing:dashboard:read`）/ 追踪搜索（`tracing:search:read`）/ 追踪配置（`tracing:config:manage`） |
| 安全管理 | 4 | API 密钥管理 / JWT 令牌管理 / 黑名单管理（`security:apikeys|jwttokens|blacklist:manage`）/ 审计日志（`security:audit:read`） |
| 系统管理 | 3 | 账户管理（`system:accounts:manage`）/ 权限管理（`system:permissions:manage`）/ 状态持久化（`config:persistence:read`） |
| AI 试验场 | 5 | 对话测试 / 向量生成 / 重排序 / 语音服务 / 图像服务（均为 `ai:playground:use`） |

可见性控制机制：

- **菜单过滤**：`menu.ts` 中每项可带 `permission` 字段（采用「读」语义码）；`usePermission`（内部调用 user store 的 `hasPermission`）过滤子项，组内子项全部被过滤时整组隐藏。无 `permission` 字段的项对所有已登录用户可见。
- **路由守卫**：路由 `meta.permissions` 声明所需权限码数组，`router.beforeEach` 要求**全部满足**（ADMIN 恒通过），不满足时跳转仪表板。
- **空权限数据兼容**：旧版本签发的令牌无 `permissions` claim（或角色未在 `role_permissions` 登记）时，前端权限列表为空且**不限制**菜单/路由，避免菜单整体消失。注意这是**前端**的兼容行为（前端无法判定 URL 规则）；后端对未命中规则的 `/api/**` 已改为 fail-closed，两者独立。

## 升级与迁移

### 非 ADMIN 控制台用户会失去什么

**在今天的端点集合上什么也不会失去。** 覆盖自检在当前代码中报告 **MISSING = 0**（所有已映射 `/api/**` 端点要么命中 URL 规则，要么在显式豁免清单中），因此非 ADMIN 用户今天能访问的端点在 `DENY_ALL` 下仍然可访问。请以启动日志的自检结果与 `/actuator/health` 的 `rbacEndpointCoverage` 为准确认。

### API-Key 集成方检查清单

1. 任何调用**未登记 `/api/**` 路径**（而非 `/v1/**`）的集成，升级后将收到 403（`DENY_ALL` 默认）。
2. 处置方式：迁移到 `/v1/**` 原生面，或将该路径加入 `RbacExemptEndpoints` 豁免清单（需写明书面理由，随代码审查入库）。
3. `/v1/**` 原生推理面（OpenAI + Anthropic 六个端点）**不受影响**，无需改动。

### 升级后如何验证

1. 查看启动 WARN：MISSING > 0 时日志列出未覆盖端点（EXEMPT 只打 INFO，无需处理）。
2. 访问 `/actuator/health`，检查 `rbacEndpointCoverage` 详情项的 `exemptCount` / `missingCount` / `exemptEndpoints` / `missingEndpoints`（EXEMPT 与 MISSING 分开报告；健康状态保持 UP，仅信息参考）。

### 回退开关（无需改码）

| 目标 | 配置 |
|------|------|
| 完全回退遗留 fail-open | `jairouter.security.rbac.unmatched-policy=AUTHENTICATED` |
| 回退阶段 2 姿态（写 fail-closed、GET fail-open） | `jairouter.security.rbac.unmatched-policy=DENY_WRITES` |

遗留键 `jairouter.security.rbac.write-fail-closed.enabled` 仅在 `unmatched-policy` 未配置时生效（`false`→`AUTHENTICATED`，`true`→`DENY_WRITES`）；两者同配时新键优先，永不矛盾。

### 其他兼容说明

1. **权限变更需重新登录生效**：权限码内嵌于 JWT；通过 UI/API 修改角色权限后，已登录用户需**重新登录**才能获得新权限。首次升级后建议让全部用户重新登录一次，以获取含 `permissions` claim 的新令牌。
2. **旧令牌兼容**：旧令牌无 `permissions` claim、仅含 `ROLE_*` authority。前端对空权限数据不限制（菜单不消失）；后端受 URL 规则保护的非管理端点，非 ADMIN 旧令牌会返回 403，重新登录即可解决。
3. **role_permissions 增量收敛（只增不删）**：表为空则种入 4 个角色模板；表非空则仅补种规则所需缺失码（见「增量收敛」一节），绝不删除或修改既有行；手工定制角色整个跳过。
4. **同步 Controller 禁方法级 @PreAuthorize**：权限判定统一走 URL 规则；新增/修改同步返回的 Controller 时不得加方法级注解，否则触发 RBAC 500 铁律。
5. **API Key 与 `/v1/**` 不受影响**：推理端点的认证与 API Key 服务类型权限体系保持不变，与 Web RBAC 相互独立。

## 相关文档

- [JWT 认证](jwt-authentication.md) - `roles` / `permissions` claim 的签发与校验
- [API Key 管理](api-key-management.md) - 服务类型权限（`/v1/**` 端点）
- [审计日志管理](audit-log-management.md) - 安全审计事件
- [安全黑名单](blacklist-management.md) - 账户与令牌封禁

# RBAC 权限管理

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-09-02
> **Git 提交**: -
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 自 **v2.9.8** 起提供**数据驱动的 RBAC（基于角色的访问控制）**体系：以 `module:resource:action` 权限码为唯一权限原子，将「权限码 → 角色 → JWT → URL 权限矩阵 → 菜单」串成一条完整链路：

1. **权限码**：44 个 `module:resource:action` 权限码（`action` ∈ `read` / `write` / `manage`），同时驱动后端 URL 访问判定与前端菜单/路由可见性。
2. **角色模板**：启动时自动在 `role_permissions` 表种入 4 个内置角色模板（ADMIN / OPERATOR / USER / VIEWER），每个角色对应一组权限码（幂等播种，不覆盖已有数据）。
3. **JWT 内嵌**：登录成功后，服务端按角色从 `role_permissions` 查询权限码并写入 JWT 的 `permissions` claim（角色仍保留在 `roles` claim；权限码无 `ROLE_` 前缀）。
4. **URL 权限矩阵**：`PermissionRuleRegistry` 登记 36 条「{HTTP 方法, URL 模式} → 权限码」规则，`PermissionAuthorizationManager` 在网关层对每个 `/api/**` 请求完成权限判定。
5. **菜单与路由**：前端 `menu.ts` 数据驱动渲染（v2.9.8 由 11 组重分类为 8 组），`usePermission` 过滤菜单、路由守卫 `meta.permissions` 拦截无权限访问。

核心设计原则：

- **数据驱动、禁方法级注解**：同步返回的 Controller 权限判定一律走 URL 规则，禁止方法级 `@PreAuthorize`（RBAC 500 铁律——响应式安全框架下同步方法注解不生效会导致 500）。
- **ADMIN 直通**：命中 URL 规则时，`ROLE_ADMIN` 直接放行，无需逐一校验权限码。
- **未登记端点回退 authenticated**：不在规则表中的 `/api/**` 端点仅要求登录，保证向后兼容。
- **`/v1/**` 独立于本体系**：OpenAI 兼容推理端点仅要求认证、不纳入权限矩阵；服务级访问控制由 **API Key 的服务类型权限**在适配器层完成，本 RBAC 不影响既有 API Key 调用。

## 权限码体系

### 格式与语义

权限码格式为 `module:resource:action`：

| 段 | 说明 | 取值 |
|----|------|------|
| `module` | 模块 | `overview` / `config` / `lb` / `cb` / `rl` / `callhistory` / `monitoring` / `tracing` / `security` / `system` / `ai` / `actuator` |
| `resource` | 模块内资源 | 见下方全量清单 |
| `action` | 操作类型 | `read` / `write` / `manage` |

`action` 语义：

| 取值 | 语义 | 说明 |
|------|------|------|
| `read` | 只读 | 查看、查询类操作，不含任何写副作用 |
| `write` | 写入 | 创建、更新、删除、启停等写操作，通常与同资源的 `read` 成对出现 |
| `manage` | 管理 | 管理类操作，用于安全/系统等敏感模块（如 `security:*:manage`、`system:*:manage`） |

### 全量权限码清单（44 个）

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
| config | `config:cache:write` | 响应缓存失效管理（v2.9.10，仅 write） |
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
| tracing | `tracing:dashboard:read` | 追踪仪表盘 |
| tracing | `tracing:search:read` | 追踪搜索 |
| tracing | `tracing:config:manage` | 追踪安全配置管理 |
| security | `security:apikeys:manage` | API Key 管理 |
| security | `security:jwttokens:manage` | JWT 令牌管理 |
| security | `security:blacklist:manage` | 黑名单管理 |
| security | `security:audit:read` | 安全审计日志（只读） |
| system | `system:accounts:manage` | 账户管理 |
| system | `system:permissions:manage` | 权限管理 |
| ai | `ai:playground:use` | AI 试验场使用 |
| actuator | `actuator:admin:manage` | Actuator 基础设施管理 |

说明：

- 合计 44 个：config 模块为 11 个资源（10 个 read/write 对 = 20 码 + `cache` 仅 write 1 码）= 21 码，其余模块见上表。
- 上表顺序即全量列表（`GET /api/security/permissions`）与权限管理 UI 权限树的展示顺序。
- 少数权限码天然不成对（如 `lb:config:write`、`callhistory:view`、`ai:playground:use`），按其资源语义定义，不强行补 read/write。
- 部分权限码仅供菜单/路由可见性使用（如 `overview:dashboard:read`），对应端点是否受 URL 规则保护见「工作流程」一节；未登记 URL 规则的端点不影响已登录用户访问。

## 角色模板

系统内置 4 个角色模板（启动时自动播种、幂等）：`role_permissions` 表为空时种入，表非空则跳过，绝不覆盖已有配置。

| 角色 | 权限码数 | 权限范围 | 说明 |
|------|:-------:|----------|------|
| ADMIN | 44 | 全量权限码 | 超集；URL 规则直通，JWT 签发时内嵌全量码 |
| OPERATOR | 35 | 全部 `:read` + `:write` 码 | 排除 `system:*`、`security:*:manage`、`actuator:*`；保留 `security:audit:read`，不含 `callhistory:view` |
| USER | 24 | 仪表盘 + config 只读 + lb/cb/rl 全量 + monitoring 只读 + tracing dashboard/search + AI 试验场 | 只读为主，唯一写码 `lb:config:write`；不含调用历史查看 |
| VIEWER | 23 | 全部 `:read` 权限码 | 纯只读角色；不含 `callhistory:view`、`ai:playground:use` 等非 `:read` 码 |

### 角色差异

- **OPERATOR vs ADMIN**：OPERATOR 面向日常运维，拥有所有读/写权限，但不含系统管理（`system:*`）、安全管理类 `manage`（`security:apikeys:manage` / `security:jwttokens:manage` / `security:blacklist:manage`）与基础设施管理（`actuator:admin:manage`）；`security:audit:read`（审计日志只读）保留给 OPERATOR。
- **USER vs OPERATOR**：USER 面向普通查看用户，仅 config **只读** + lb/cb/rl 全量（含唯一写码 `lb:config:write`）+ monitoring 只读 + tracing 仪表盘/搜索 + AI 试验场；不含任何 `manage` 码与账户管理，也不含调用历史查看。
- **VIEWER vs USER**：VIEWER 是三者中最窄的纯只读角色，仅保留全部 `:read` 码；不含 USER 唯一的写码 `lb:config:write`，也不含 `ai:playground:use`。
- 三个非 ADMIN 角色默认**均不含** `callhistory:view`（专用查看码，独立于 read/write）；如业务需要，可在权限管理 UI 中为角色额外勾选该码。

## 工作流程

一次带权限判定的完整请求流程如下：

1. **登录签发 JWT**：客户端 `POST /api/auth/jwt/login`。`AccountManager` 校验凭据（v2.9.8：YAML 静态账户优先，未命中时回退数据库账户——Web 账户管理 API 创建的账户可直接登录），随后按用户角色经 `RolePermissionService` 查询 `role_permissions` 表（Caffeine 缓存 5 分钟；ADMIN 短路返回全量码），将权限码写入 JWT `permissions` claim 并返回令牌。
2. **令牌解析**：请求携带 `Authorization: Bearer {token}`（或自定义 JWT 头）。`DefaultJwtTokenValidator` 解析令牌：`roles` claim → `ROLE_` 前缀 authority；`permissions` claim → 无前缀 authority（两者语义隔离，避免与 API Key 的 `ROLE_*` 权限冲突）。
3. **URL 规则匹配**：`SecurityConfiguration` 将 `/api/**` 全部交给 `PermissionAuthorizationManager`；其从 `PermissionRuleRegistry` 按「HTTP 方法 + URL 模式」首条命中查找所需权限码（共 36 条规则）。
4. **权限判定**：
   - 命中规则：`ROLE_ADMIN` 直通放行；否则校验认证对象是否携带该权限码 authority；
   - 未命中规则：回退 `authenticated`（仅要求登录）。
5. **结果**：放行进入 Controller；拒绝则返回 403。

| 场景 | 判定结果 |
|------|----------|
| 未命中 URL 规则 | 已登录即放行（authenticated 回退） |
| 命中规则 + 角色为 ADMIN | 放行（直通） |
| 命中规则 + 携带所需权限码 | 放行 |
| 命中规则 + 无所需权限码（且非 ADMIN） | 拒绝（403） |

## 管理 API

权限管理提供 3 个管理端点 + 1 个当前用户端点（`PermissionManagementController`）：

| 方法 | 路径 | 说明 | 所需权限 |
|------|------|------|----------|
| GET | `/api/security/permissions` | 全部权限码（44 个，按清单顺序） | `system:permissions:manage` |
| GET | `/api/security/permissions/roles` | 全部角色及其权限码（角色名 → 权限码列表） | `system:permissions:manage` |
| PUT | `/api/security/permissions/roles/{roleName}` | 整体替换指定角色的权限码集合 | `system:permissions:manage` |
| GET | `/api/auth/permissions` | 当前登录用户权限码（ADMIN 返回全量 44 码） | 任意已登录用户 |

> `/api/security/permissions/**` 由 URL 规则保护（`system:permissions:manage`）；`GET /api/auth/permissions` 未登记 URL 规则，回退 `authenticated`。

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

响应 `data` 为 44 个权限码数组（顺序与全量清单一致）。

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

ADMIN 直接返回全量 44 码；其他角色返回其 JWT `permissions` claim 中的权限码。

## 权限管理 UI

「系统管理 → 权限管理」页面（路由 `/system/permissions`，需 `system:permissions:manage`）提供图形化权限配置：

1. **角色下拉**：选择 ADMIN / OPERATOR / USER / VIEWER，自动加载该角色当前权限码并回显勾选。
2. **权限码树**：44 个权限码按模块分组的树形勾选（叶子为权限码，父节点自动联动）。
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
- **空权限数据兼容**：旧版本签发的令牌无 `permissions` claim（或角色未在 `role_permissions` 登记）时，前端权限列表为空且**不限制**菜单/路由，避免菜单整体消失（与后端「未登记 URL 规则回退 authenticated」对齐）。

## 升级注意

从 v2.9.8 之前的版本升级时的兼容性说明：

1. **未登记端点回退 authenticated（平滑兼容）**：旧版 `/api/**` 除少量硬编码 ADMIN 路径外仅要求认证。新版未登记到 `PermissionRuleRegistry` 的端点行为不变（已登录即可访问），升级无需改动配置即可平滑过渡。
2. **权限变更需重新登录生效**：权限码内嵌于 JWT；通过 UI/API 修改角色权限后，已登录用户需**重新登录**才能获得新权限。首次升级后建议让全部用户重新登录一次，以获取含 `permissions` claim 的新令牌。
3. **旧令牌兼容**：旧令牌无 `permissions` claim、仅含 `ROLE_*` authority。前端对空权限数据不限制（菜单不消失）；后端受 URL 规则保护的非管理端点，非 ADMIN 旧令牌会返回 403，重新登录即可解决。
4. **role_permissions 表自动种子（非破坏）**：启动时若 `role_permissions` 表为空则自动种入 4 个角色模板；表非空则跳过，不会覆盖升级前已有数据（含自定义角色权限）。
5. **同步 Controller 禁方法级 @PreAuthorize**：权限判定统一走 URL 规则；新增/修改同步返回的 Controller 时不得加方法级注解，否则触发 RBAC 500 铁律。
6. **API Key 与 `/v1/**` 不受影响**：推理端点的认证与 API Key 服务类型权限体系保持不变，与 Web RBAC 相互独立。

## 相关文档

- [JWT 认证](jwt-authentication.md) - `roles` / `permissions` claim 的签发与校验
- [API Key 管理](api-key-management.md) - 服务类型权限（`/v1/**` 端点）
- [审计日志管理](audit-log-management.md) - 安全审计事件
- [安全黑名单](blacklist-management.md) - 账户与令牌封禁

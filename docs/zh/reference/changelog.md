# 更新日志

<!-- 版本信息 -->

> **文档版本**: 2.10.0
> **最后更新**: 2026-09-05
> **作者**: JAiRouter Team

<!-- /版本信息 -->

本文档记录了 JAiRouter 项目的版本更新历史和重要变更。

## 版本规范

JAiRouter 遵循 [语义化版本](https://semver.org/lang/zh-CN/) 规范：

* **主版本号 (MAJOR)**: 不兼容的 API 变更

* **次版本号 (MINOR)**: 向后兼容的功能新增

* **修订号 (PATCH)**: 向后兼容的问题修正

## 版本历史

### [2.10.0] - 2026-09-05 - 功能发布（Web 前端地基：清理 + 统一组件 + 令牌化 + 暗色修复）

#### 前端地基

- **死代码清理**：删除 13 个零引用 .vue（旧 tracing/playground 群，11,726 行）+ `components/common/` 13 组件 + `tracing.ts` 16 个死导出（合计 -13.5k 行）
- **统一组件**：新建 `PageSkeleton.vue`（slot 化页面骨架）、`StatCard.vue`（统计卡，tone 令牌化）、`useChartTheme.ts`（亮/暗图表主题）；Dashboard / ServiceManagement / RuleManagement 试点接入
- **令牌化**：约 20 个页面 ~150 处硬编码 hex/rgba → `--ja-*` / `--el-*` 语义令牌（统计卡/表格/图表渐变/对话框）；统计卡统一为 StatCard（Dashboard/ApiKey/CallHistory/Exception/限流监控 22 卡）
- **暗色模式修复**：引入 Element Plus 官方 `theme-chalk/dark/css-vars.css`；暗色下 `--el-color-*-light-N` 与 `--ja-primary-light-N` 不再映射浅色值（改暗调，如 light-9 #18222b）——消除暗色近白块与刺眼高对比
- **细节**：Layout 微调（移除 logo 呼吸灯/侧边栏 hover 阴影、面包屑直链 `/dashboard/main`）；清除约 50 处 console 调试残留；熔断器监控 CLOSED/OPEN/HALF_OPEN 状态卡暗色适配
- **内嵌 UI 刷新**：最新前端构建同步至后端静态资源 `src/main/resources/static`

#### 质量

- 后端全量 **3225 用例全绿**（回归）；前端 vue-tsc + vite build 通过；亮/暗页面程序化白面探测通过

---

### [2.9.11] - 2026-09-05 - 功能发布（README 更新 + hallmark 设计审计 + 前端修复）

#### README 与文档

- **README / README-ZH 全面更新**：重拍并替换 7 张界面截图（含暗色主题与调用历史仪表盘，此前为 2026-08-26 旧 UI）；Core Features 补充响应缓存 P1、RBAC 44 权限码、暗色/亮色主题；Roadmap 权限码 43→44；示例认证头统一为 `Jairouter_Token`
- **rbac-permissions zh/en 同步 44 码**：全量清单新增 `config:cache:write`（config 模块 11 资源 = 21 码）；角色计数 ADMIN 44 / OPERATOR 35；接口与权限树描述同步

#### hallmark 设计审计（本地 innerdoc，v2.10.x 重构输入）

- 产出 `innerdoc/02-架构与设计/web-management-hallmark-audit.md`（474 行）：硬编码颜色 395 处、~5,300 行死代码、Dashboard 治理链路缺失、`components/common` 13 组件零引用、4 套 stat-card 重复等；含 v2.10.0（地基）/ v2.10.1（配置接入）/ v2.10.2（治理安全）落地要点

#### 前端修复

- 权限码静态清单同步 44（`permission.ts` / `menu.ts`，权限树可显示 `config:cache:write`）
- 登录页 i18n 补齐缺失 key（username/passwordPlaceholder、submitting），修复占位符直接渲染原始 key
- `stores/user.ts` jwt validate GET→POST（对齐后端契约，修复 405 静默失败）
- `BlacklistManagement` 移除不存在的 `suspicious-ips` 端点调用与 mock 回退（IP 列表改由活跃令牌提供）

#### 质量

- 后端全量 **3225 用例全绿**；前端 `vue-tsc` + `vite` build 通过

---

### [2.9.10] - 2026-09-05 - 功能发布（路由智能深化-5：响应缓存 P1）

#### 响应缓存（流式 SSE 拼接缓存 + 失效 API + 服务级限流提前短路）

- **流式 SSE 拼接缓存**：非流式之外，流式 chat 响应同样可缓存——transform 后逐块 + usage + finishReason 存入 `CachedStreamingResponse`，流正常结束写缓存；命中按裸 SSE（TEXT_EVENT_STREAM + [DONE]）回放；需显式配置 `jairouter.response-cache.skip-streaming: false` 开启
- **失效 API**：`CacheStore` 扩展 delete / deleteByPrefix / clear；新增 `DELETE /api/config/cache/response`（支持按 serviceType / model / 全部失效）；缓存键改三段式 `rc:{service}:{model}:{sha256}` 支持按前缀失效
- **服务级限流提前短路**：`RateLimitManager.tryAcquireService` + `ServiceRateLimitHolder`（ThreadLocal 恰一次防双扣）——缓存命中在实例选择前短路，429 硬边界语义不变
- **权限扩展**：第 44 码 `config:cache:write`（PermissionCodes + PermissionRuleRegistry URL 规则 + 角色模板同步）

#### 测试

- 新增 49（流式写/读回放/分桶/invalidate/键三段式/限流短路集成）；**全量 3225 用例全绿**

---

### [2.9.9] - 2026-09-03 - 功能发布（路由智能深化-4：响应缓存 P0）

#### 响应缓存（非流式精确匹配）

- **完整响应缓存**：相同请求（同租户 + 同服务/模型 + 规范化同请求体）直接复用缓存响应，**完全跳过下游**——降延迟、降成本；与 v2.9.0 前缀缓存（KV Cache 亲和，仍走下游省 prefill）可叠加
- **P0 范围**：非流式 chat + embedding + rerank（确定性请求：chat 需 temperature==0/null 且 n==1/null；embedding/rerank 天然确定性）；image/TTS/STT 二进制排除；流式缓存放后续版本
- **组件**：`ResponseCacheProperties`（`jairouter.response-cache`，默认关 opt-in）/ `CacheStore` 接口 + `CaffeineCacheStore`（±10% TTL 抖动防雪崩）/ `ResponseCacheService` 门面（enabled 短路 + 确定性门控）/ `ResponseCacheKeyBuilder`（chat/embedding/rerank 规范化）
- **缓存键**：`SHA-256(apiKeyId|user?|serviceType|model|规范化请求体)`——apiKeyId 入键防跨租户泄漏；user 可选入键（空则 apiKey 粒度，零接口破坏）；`cacheSalt` 显式绕过位；键无明文内容
- **会话语义**：无 sessionId、会话历史全量在 messages——键含完整 messages 天然区分上下文；通用问候单轮高频天然高命中，陈旧由 TTL 控制
- **挂载**：handler 读（selectInstance 后短路，服务级限流不绕过）；NonStreamingRequestProcessor 写（仅 2xx 确定性响应）
- **指标**：`jairouter_response_cache_hits_total / misses_total / hit_ratio`（tag service/model）
- **命中语义**：命中短路在下游执行前（不写调用历史、不耗 token 配额，仅累加命中指标）
- **配置文档**：新增 `configuration/response-cache.md`（zh/en）

#### 测试

- 新增 45：KeyBuilder 12 / CaffeineCacheStore 6 / ResponseCacheService 8 / processor 写 4 / 指标 3 / handler 集成 13（命中短路/未命中/禁用/流式与 temperature>0 绕过/租户隔离/embedding·rerank/写读闭环）；**全量 3176 用例全绿**；DeepSeek 真实下游端到端冒烟通过（命中/租户隔离/指标）

---

### [2.9.8] - 2026-09-02 - 功能发布（Web 菜单 RBAC 管理 + 用户/权限扩展）

#### 数据驱动权限体系

- **43 权限码**：`module:resource:action` 体系（overview/config×10/lb/cb/rl/callhistory/monitoring×5/tracing×3/security×4/system×2/ai/actuator）；`PermissionCodes` 常量 + 全量清单
- **4 角色模板**：ADMIN（43 全量）/ OPERATOR（34：read+write 排除 system/security:manage/actuator）/ USER（24：dashboard+config:read+lb/cb/rl+monitoring:read+tracing dashboard/search+playground）/ VIEWER（23：仅 :read）；`RolePermissionSeeder` 启动播种（表空幂等），role_permissions 表存储
- **JWT permissions claim**：登录 JWT 内嵌权限码（无 ROLE_ 前缀）；角色仍为 roles claim；refresh 自动保留

#### 数据驱动授权

- **PermissionRuleRegistry**（36 条 URL→权限码规则）+ **PermissionAuthorizationManager**（ReactiveAuthorizationManager：命中→查码；ADMIN 直通；无规则回退 authenticated）；SecurityConfiguration `/api/**` 接入
- 同步返回 controller 权限全走 URL 规则（遵守 RBAC 500 铁律：禁方法级 @PreAuthorize）；ModelCallStats `hasAdminPermission` stub 补 URL 规则
- **契约修复**：`/api/security/jwt/accounts/**` 映射统一为 `system:accounts:manage`

#### 管理 API 与 UI

- `GET /api/security/permissions`（全量码）/ `GET /api/security/permissions/roles`（角色→码）/ `PUT /api/security/permissions/roles/{roleName}`（改角色权限，invalidateCache）/ `GET /api/auth/permissions`（当前用户码）
- **权限管理 UI**：系统管理→权限管理（角色下拉 + 43 码权限树 + 保存；提示变更需重新登录）
- **菜单重分类**：11 组 → 8 组 34 项（概览/模型服务/流量治理/数据记录/链路追踪/安全管理/系统管理/AI 试验场）；menu.ts 数据驱动 + usePermission 过滤 + 路由守卫 meta.permissions

#### 登录链路修复

- **DB 账户 fallback**：`AccountManager` 登录认证支持 Web 账户管理创建的 DB 账户（YAML 静态账户优先，jwt_accounts 表兜底；enabled=null 按启用，与 JwtAccountService 约定一致）

#### 前端修复

- 侧边菜单滚动阶梯感修复（菜单区独立滚动容器 + 固定全高渐变列，滚动背景连续）

#### 测试

- 新增 50+：RolePermissionService/Seeder/PermissionAuthorizationManager（含 URL 规则矩阵）/PermissionManagementController/JWT permissions claim/AccountManager（DB fallback 13 例）等；**全量 3131 用例全绿**；权限冒烟（ADMIN 200 / 无 token 401 / USER/VIEWER 403 矩阵）通过

#### 文档

- 新增 `security/rbac-permissions.md`（zh/en）：权限码/角色模板/工作流/管理 API/权限 UI/菜单路由/升级注意

---

### [2.9.7] - 2026-09-01 - 功能发布（路由智能深化-3：标签路由）

#### 标签路由（tags）

- **实例标签**：`ModelInstance` 新增 `Map<String,String> tags`（如 `gpu_type: a100` / `region: cn-north` / `tier: premium`），YAML/API/持久化全链路支持（ConfigConverterHelper/ModelInstanceConfiguration/ServiceInstanceEntity JSON 列/ServiceInstanceManager）
- **规则动作 TARGET_TAGS**：路由规则新增「标签路由」动作，命中后按标签圈选实例（AND 语义——实例 tags 必须包含全部键值对才入选）；`RuleDefinition.Action` 新增 `tags` 字段
- **请求级 header 圈选**：`X-JAiRouter-Tags: key=value,key2=value2`（逗号分隔、容忍空格与空项）直接按标签过滤候选实例；规则 TARGET_TAGS 优先于 header
- **优先级**：TARGET_INSTANCE 锁定 > TARGET_TAGS > 资源池；标签过滤空候选 404；池路由与 auto-model 天然不受 tags 影响（池成员显式指定）
- **前端**：实例管理页「标签配置」key-value 编辑区（仿请求头）；规则表单新增 TARGET_TAGS 动作 + 标签编辑区；规则列表动作摘要展示

#### 修复

- **旧库升级兼容**：`CompatibilitySchemaMigrator` 登记 `service_instance.tags` 列（CLOB，方言适配）——修复旧 H2 库缺列导致实例查询 500
- **限流器实例标识**：`RateLimitManager.generateInstanceKey` 回退 `instanceId → name → baseUrl`，修复实例未配置 instance-id 时监控显示 null；指标解析按首个 `:` 切分兼容含冒号实例名
- **限流器监控指标**：`RateLimiterTracingWrapper` 补 `getRemainingCapacity()`/`getUsageRatio()` 委托转发——修复监控页使用率恒显示 -100%（此前 tracing 包装层未转发接口默认值 -1）
- **base 配置**：`model-services-base.yml` 9 个实例补 `instance-id`（消除 null 标识根因）
- **LB 策略下拉**：StrategyConfig.vue 下拉宽度 + 选项布局修复（延迟感知策略与说明不再贴字）+ fallback 补 latency 策略
- **规则模板复制**：`RuleTemplateService.copyAction` 补 TARGET_TAGS case（防未来模板复制丢 tags）

#### 测试

- 新增：SelectInstanceOptimizerTest（9 例）、ModelServiceRegistryTagRoutingIntegrationTest（10 例）、ModelInstanceConfigurationTest（6 例）、CompatibilitySchemaMigrator 迁移断言（3 例）、RateLimitManagerInstanceKeyTest（3 例）、RateLimiterTracingWrapperTest（2 例）；**全量 3069 用例全绿**

---

### [2.9.6] - 2026-08-30 - 功能发布（路由智能深化-2：请求级故障转移）

#### 请求级故障转移（failover）

- **换实例重试**：`BaseAdapter.processRequestWithRetry` 改造——可重试错误（连接错误/5xx/超时）时，当前实例加入本次请求失败黑名单（`baseUrl:path` 键），经正常选择链（健康/熔断过滤 + 负载均衡策略）**重新选择未失败实例**重试，替代此前"同一实例反复重试"
- **黑名单隔离**：请求内已失败实例不会被重复选择（`tryReselectInstance` 循环，上限 `failedKeys.size()+2` 防死循环）
- **安全回退**：单实例服务 / 全实例失败 / 重选耗尽 → 回退原实例重试（既有行为不变）；4xx 不可重试错误不换实例；重试次数仍由 `RetryPolicy` 控制
- **默认启用**：无需额外配置（重选用 null clientIp，LB 自动降级非 IP 感知；粘性路由在重选阶段自然旁路）
- 新 WebClient 按实例 baseUrl 获取（`getWebClientForInstance`），实例切换无需重建连接池语义

#### 测试

- 新增 `BaseAdapterFailoverTest`（5 用例：A 失败→B 成功 / 全失败回退 / 单实例无重选 / 4xx 不重试 / 重选 cap 防循环）；**全量 3034 用例全绿**（+5）

---

### [2.9.5] - 2026-08-30 - 功能发布（文档知识库治理）

#### 知识库治理机制（docs 155 + innerdoc 165）

- **工具集** `scripts/knowledge-governance/`：kg-scanner（清点+SHA-256+一次性分类）/ kg-duplicate-detector（SHA-256 精确 + 文件名近似）/ kg-staleness-detector（索引漂移/状态冲突/版本字面量）/ kg-archive-mover（--plan/--execute 归档，knowledge-base 与 16-版本发布 硬保护）/ kg-version-tracker（innerdoc/docs-versions.json + --regen-index）
- **治理工作流** `.mimocode/skills/knowledge-governance/SKILL.md`：5 步（扫描→LLM 提案→人工审批→执行→索引更新），每版本发布执行（开发计划版本条目含 checklist）
- **CI 增强**：`validate-nav-files.py`（mkdocs nav 完整性校验）+ docs-version-management.yml 增强（nav 校验步骤 + issue 附 docs 重复检测）
- **首次治理执行**：归档 61 文件（H2×23/security×16/开发指南×11/tracing×7 等 → `innerdoc/archive/`）；3 处唯一内容合并入 knowledge-base（JPA 迁移/API-Key 初始化修复/追踪验证清单）；索引三件套重建（README-INNERDOC/INDEX.json/00-索引，105 文件/18 分类）；修复状态冲突 1 处
- **SOP 文档**：`docs/{zh,en}/development/knowledge-base-governance.md`（10 节）；补齐缺失的 `docs/en/development/doc-maintenance.md`（修复 mkdocs strict 构建风险）
- **docs 修复**：8 个文档版本头 2.6.11 → 2.9.5；roadmap 当前稳定版 v2.7.11 → v2.9.5

#### 测试

- 治理工具实测通过（165 文件/3 精确重复组/22 近似集群/索引漂移检出）；全量 **3029 用例全绿**（无 Java 代码改动）

---

### [2.9.4] - 2026-08-30 - 功能发布（UI 设计系统重构 + RBAC 修复）

#### UI 设计系统

- **设计 Token**：新增 `src/styles/tokens.css`（40+ 语义化 `--ja-*` CSS 变量：主色/侧栏/主区/文本/边框/圆角/阴影/字体/登录渐变/仪表盘渐变色，含 `html.dark` 覆盖）
- **Element Plus 变量覆盖**：`src/styles/element-override.css` 将 `--el-color-primary` 家族、`--el-bg-color`、`--el-fill-color-*`、`--el-text-color-*`、`--el-border-color-*`、阴影映射到 token（亮/暗双套）
- **暗色模式**：`src/composables/useTheme.ts`（isDark/toggleTheme/initTheme，localStorage 持久化，默认跟随系统，启动前同步初始化防闪烁）；Layout 顶栏日/月切换按钮
- **硬编码色清理**：Layout.vue 侧栏/激活/主区、Login.vue 渐变、Dashboard.vue 统计卡渐变与图标色、index.html 渐变 → token 变量（保持原视觉）

#### RBAC 修复（500 回归）

- **根因**：`@EnableReactiveMethodSecurity` 下，同步返回类型 controller 的方法级 `@PreAuthorize` 要求 Publisher 返回（Reactor Context），真实请求抛 500（认证正常、授权拦截崩溃，非 401）
- **修复**：`ApiCallHistoryController` / `CallHistoryConfigController` / `TracingSecurityController`（3 个同步返回 controller）移除 `@PreAuthorize`，改由 `SecurityConfiguration` URL 规则保护（`/api/call-history/**`、`/api/config/call-history/**`、`/api/config/tracing/security/**` → ADMIN）
- 真实请求验证：ADMIN 200 / 无 token 401；本地 dev 库手动补齐 v2.9.2 新列（H2 `DATABASE_TO_UPPER=FALSE` 下 Hibernate ddl-auto update 大小写元数据不匹配导致无法自动加列）

#### 旧库升级兼容（自动迁移）

- **`CompatibilitySchemaMigrator`**（启动时幂等迁移）：旧版本（≤v2.9.1）升级的数据库若缺 `record_level` / `request_body_encrypted` / `response_body_encrypted` 列，应用启动时自动检测并 `ALTER TABLE ADD COLUMN` 补齐，无需人工干预；列类型按方言适配（H2=CLOB / MySQL=LONGTEXT / PostgreSQL=TEXT）；新库由 JPA 建表自动跳过；重复启动安全；新增实体列时在迁移清单登记即可扩展

#### 测试

- 更新 ApiCallHistoryControllerRbacTest / RecordLevelChangeAuditTest（RBAC 断言改为 URL 规则语义）；**全量 3023 用例全绿**

---

### [2.9.3] - 2026-08-30 - 功能发布（路由智能深化-1：EWMA 延迟感知路由）

#### 延迟感知负载均衡（`load-balance.type: latency`）

- **LatencyAwareLoadBalancer**：基于 EWMA（指数加权移动平均）的延迟感知策略，按实例历史调用延迟加权随机（`1/(1+ewma)`，延迟越低概率越高）；冷启动（无样本）等权重公平探索；失败调用按 30s 惩罚值更新
- **duration 链路接入**：`LoadBalancer` 接口新增带 durationMs 的 hook 重载（default 兼容，现有 6 策略零改动）；`AdapterMetricsRecorder → ModelServiceRegistry → LoadBalancer` 全链路传递调用耗时；`StickyLoadBalancer` 3 参透传（粘性包裹下 duration 不丢失）
- **配置**：`ewma-alpha`（默认 0.2，全局/服务级均可配）；ComponentFactory 注册 `latency` 类型；两个校验器 + DTO + 合并器/转换器全链路支持；`/api/loadbalancer/strategies` 含 latency
- 默认策略仍为 random，`type: latency` 配置选择开启，零默认行为变化

#### 测试

- 新增 LatencyAwareLoadBalancerTest（EWMA 收敛/选择分布/冷启动/失败惩罚/并发安全）、ModelServiceRegistryDurationFlowTest（duration 传递）；扩展 StickyLoadBalancerTest、LoadBalancerManagementControllerTest、ConfigValidatorHelperTest；**全量 3024 用例全绿**（+16）

---

### [2.9.2] - 2026-08-30 - 功能发布（记录治理）

#### 三档记录级别

- **METADATA_ONLY（默认）**：仅保存调用元数据，不保存请求/回复内容
- **SUMMARY**：经 SanitizationService 脱敏后保存前 N 字摘要（复用现有摘要列）
- **FULL**：AES-256-GCM 加密保存完整请求/回复内容（新 `RecordContentCipher`，密钥来自 `JAIR_CALL_HISTORY_KEY` 环境变量或自动生成持久化到 `~/.jairouter/call-history.key`）
- 新配置项：`jairouter.call-history.record-level` / `max-content-length`（默认 64KB）/ `encryption-key-source`

#### 内容捕获

- **非流式**：请求体（转换后序列化）与下游原始响应体（转换前）捕获，超限截断；`AdapterMetricsRecorder.recordCompleteCall` 新增含 body 重载
- **流式**：入口序列化请求 + `doOnComplete` 组装文本；补齐流式调用历史记录（此前流式不落历史）
- **去重修复**：BaseAdapter 成功路径改为仅更新统计，避免与 RequestProcessor 重复记录（每调用 2 行 → 1 行）

#### 安全

- **调用历史 API 全面 RBAC**：`ApiCallHistoryController` 全部端点 + 新配置端点 `GET/PUT /api/config/call-history` 均为 ADMIN-only
- **修复潜在安全缺口**：新增 `@EnableReactiveMethodSecurity`（此前 `@PreAuthorize` 实际未生效）
- **新端点** `GET /api/call-history/{id}/detail`：ADMIN 解密查看 FULL 记录内容
- **审计事件**：`RECORD_LEVEL_CHANGE`（记录级别变更）、`FULL_CONTENT_ACCESS`（解密访问 FULL 记录）

#### 前端

- 调用历史页新增记录级别设置（radio：仅元数据 / 摘要（脱敏） / 完整（加密））

#### 测试

- 全量 **3008 用例全绿**（0 失败/0 错误，新增 55）；新增 RecordContentCipherTest、ApiCallHistoryServiceRecordLevelTest、ApiCallHistoryControllerRbacTest、RecordLevelChangeAuditTest 等

---

### [2.9.1] - 2026-08-30 - 质量收口

#### 大文件务实拆分

- **15 → 5 个 >500 行文件**（务实豁免 5 个高内聚文件）：`TracingConfiguration` 608→307（提取 4 个嵌套配置类）、`TracingEncryptionService` 543→477、`ApiKeyBatchService` 608→459、`CircuitBreakerTracingWrapper` 570→243、`DefaultMetricsCollector` 629→379、`ConfigurationService` 653→410（实例管理提取为 `InstanceConfigService`）、`JwtBlacklistServiceImpl` 558→438、`ControllerTracingInterceptor` 595→237、`ExtendedSecurityAuditServiceImpl` 629→490、`NonStreamingRequestProcessor` 574→411
- 新增 16 个顶层类（纯 POJO 配置类/DTO + 普通 helper + `@Component`/`@Service` 委托类），行为零变更，全量测试无回归

#### 前端

- **资源池成员实例改为下拉选择**：按服务类型加载可用实例、已选去重、禁手输、已删实例兜底显示

#### 测试

- 全量 **2953 用例全绿**（0 失败/0 错误）；jacoco INSTRUCTION 覆盖率 **31%**（≥ 30% 目标达标，1175 类）

---

### [2.9.0] - 2026-08-28 - 功能发布

#### LLM KV 缓存增强:前缀缓存友好网关

- **自动租户粘性路由**：新增 `StickyLoadBalancer`（一致性哈希按 `apiKeyId|serviceType|modelName` 映射实例）+ `AffinityKeyResolver`（优先 apiKeyId，回退 clientIp）+ `AffinityContextHolder`（ThreadLocal 传递亲和性键）；`sticky.enabled` 配置感知，实例 >1 时自动包装粘性负载均衡；`affinityKeyScope` 支持 `tenant` / `tenant_model`（默认含 modelName）粒度，动态解析生效
- **缓存命中指标**：解析 DeepSeek 形态 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` 与 OpenAI/vLLM 形态 `prompt_tokens_details.cached_tokens`；新增 `jairouter_cache_hit_tokens_total` / `cache_miss_tokens_total` 计数器 + `jairouter_cache_hit_ratio` Gauge（按 adapter+instance 聚合，基于累积计数 hitTotal/(hitTotal+missTotal)）
- **缓存参数透传**：`ChatDTO.Options` 新增 `prefixCacheHash` / `enablePrefixCaching` 字段；OpenAiRequestTransformer 扩展 extra_body 白名单放行 vLLM 前缀缓存参数
- **前缀卫生保障**：回归测试断言消息数组逐字透传（4 消息 + 6 消息两组），5 组缓存 token 解析容错测试
- **修复**：StickyLoadBalancer 并发安全（volatile 快照 + 指纹缓存替代每请求重建 hash ring，消除 TreeMap 并发读写风险并避免 O(instances×150) 重复计算）；cache_hit_ratio 累积语义修正；affinityKeyScope 真正生效；ApiKeyBatchServiceTest 到期统计时间边界 flaky 修复

#### 测试

- 新增：StickyLoadBalancerTest、AffinityKeyResolverTest、CacheMetricsTest、CacheTokenParsingTest、OpenAiRequestTransformerTest（回归）、StreamingRequestProcessorTest；**全量 2953 用例全绿**

---

### [2.8.9] - 2026-08-26 - 功能发布

#### 智能分流:资源池 + auto-model

- **资源池**:一组同服务类型实例的命名集合,可配置策略(weighted-random/round-robin/least-connections/ip-hash/consistent-hash)与成员权重;YAML(`model.pools`)+ StoreManager 持久化 + CRUD API(`/api/config/pools`)+ Web 管理页(配置管理 → 资源池)
- **auto-model 虚拟模型名**:请求 `model=auto-model`(或任意池名)时自动从池内健康实例中选择执行(健康/熔断过滤 + 池级权重 + 池策略);未配置池时 auto-model 回退为该服务全部健康实例;池成员缺失(实例被删)自动跳过
- **规则联动**:规则 `TARGET_MODEL` 可直接指向池名锁定资源池
- **响应回显**:池路由后,下游请求与响应(非流式)的 `model` 字段改写为实际实例模型名(流式出站请求同步改写,流式响应回显留待后续)
- **修复**:ApiKeyBatchServiceTest 到期统计时间边界 flaky(now+2h 深夜跨零点误判)

#### 测试

- 新增:PoolSelectorTest(POOL-001~007)、PoolPersistenceServiceTest(POOL-PERSIST-001~008)、PoolConfigControllerTest(POOL-API-001~013)、ModelServiceRegistryRuleIntegrationTest(POOL 路由 INTEG-018~021);**全量 2909 用例全绿**

---

### [2.8.8] - 2026-08-26 - 功能发布

#### 限流能力补齐

- **服务级限流热路径接入**：请求链新增服务级限流检查（每请求恰一次，无配置零开销），超限返回 `429 Too Many Requests`；实例级仍为"跳过换下一个实例"语义
- **按服务动态限流配置做实**：`PUT /api/services/{serviceType}/ratelimit` 由空实现改为 持久化（配置存储 `model-router-config`）+ 热生效（RateLimitManager）；GET 统一返回 canonical 格式（`enabled/algorithm/capacity/rate/scope/key`）；启用时 `capacity/rate` 必填且 > 0；配置**重启后自动生效**；服务管理页编辑对话框接入该端点
- **规则 RATE_LIMIT 动作**：规则引擎新增规则级限流动作（按规则 ID 独立限流，与命中统计同一决策生效点，不重复执行），命中后超限返回 429；删除规则/变更动作自动清理限流器；新增「限流保护」预置模板（模板总数 6 → 7，从模板创建自动预填限流参数）；dry-run 输出包含限流参数
- **修复**：API 调用历史统计测试过时断言（服务改用独立范围查询后测试未同步更新）；dev 模式 `VITE_API_BASE_URL` 修正为 `/api`（开发代理直连后端）

#### 测试

- 新增/更新：ServiceRateLimitControllerTest（RL-001~008）、ModelServiceRegistryRuleIntegrationTest（INTEG-014~017）、RuleConfigControllerTest（RULEC-029~034）、RateLimiterTest（规则级限流 3 例）、RuleTemplateServiceTest（限流模板）、RuleTemplateControllerTest（7 模板）；**全量 2876 用例全绿**（含顺带修复的 ApiCallHistoryServiceTest）

---

### [2.8.7] - 2026-08-25 - 功能发布

#### 规则引擎三件套

- **规则命中统计**：命中计数挂在决策生效点（不重复计数，dry-run 不计）；新增 Prometheus 指标 `jairouter_rule_hits_total`（tag: ruleId/actionType）+ `GET /api/config/rules/stats` 聚合端点；前端规则列表新增"命中"列与刷新
- **优先级拖拽**：规则列表支持拖拽排序（sortablejs），按行序批量提交优先级；`PUT /priority` 改为跳过未知 id（YAML 规则不再 404），返回 `{updated, skipped}`；规则新增 `source` 字段（YAML/PERSISTED），YAML 规则显示徽章且不可拖拽
- **场景模板**：新增 6 个预置规则模板（灰度发布/租户隔离/模型名重写/权重分流/适配器切换/VIP 实例锁定）；`GET /api/config/rules/templates` + `POST /{id}/create`；前端"从模板创建"向导，生成草稿预填表单后保存

#### 测试

- RuleConfigControllerTest 新增 RULEC-026~028（统计）；新增 RuleStatsServiceTest、RuleTemplateServiceTest、RuleTemplateControllerTest；规则相关测试全部通过；Checkstyle 0 violations

---

### [2.8.6] - 2026-08-25 - 功能增强

#### 规则引擎易用性增强

- **动作/条件值下拉化**：动作目标按类型下拉选择（模型名/实例/适配器，可搜索+自定义；LB 策略固定 5 选 1），条件值支持模型名/服务类型/常用请求头下拉与自动补全；服务类型变化时联动刷新模型与实例选项
- **规则模拟测试（dry-run）**：`POST /api/config/rules/validate` 用示例请求（modelName/IP/headers）验证规则命中，只读不改状态；Web 表单内置「模拟测试」面板
- **修复**：`serviceType` 大小写不敏感匹配（`imgGen/imgEdit` 等 camelCase 枚举可正常命中）

#### 测试

- RuleConfigControllerTest 新增 RULEC-025 等用例，规则引擎相关测试全部通过；Checkstyle 0 violations

---

### [2.8.5] - 2026-08-23 - 功能发布

#### 规则引擎（可视化条件路由）

实现可视化路由规则配置，让用户通过 Web 控制台**无需编写代码**即可配置基于条件的智能路由：

- **条件路由规则**：支持 5 种匹配条件（模型名/服务类型/请求头/来源 IP/权重）与 5 种运算符（等于/包含/前缀/正则/CIDR 匹配），命中后执行预设动作
- **动作类型**：TARGET_MODEL（模型重写）、TARGET_INSTANCE（锁定实例）、TARGET_ADAPTER（切换适配器）、LB_STRATEGY（负载均衡策略覆盖）
- **规则管理 API**：`/api/config/rules` 完整 CRUD + 启停 + 优先级批量调整
- **规则持久化**：StoreManager 持久化，重启自动恢复，YAML 定义优先合并
- **前端可视化配置**：规则管理页面 + 规则表单对话框（条件编辑/动作配置）
- **文档全面整理**：新增路由规则配置文档，docs 结构整理

#### 测试

- 规则引擎相关测试用例全部通过；Checkstyle 0 violations

---

### \[2.8.4] - 2026-08-15 - 功能发布

#### 插件系统（自定义适配器框架）

实现"插件系统"，让用户通过 Web 控制台**无需编写代码**即可创建、测试和管理自定义适配器：

- **适配器模板系统**：13 个预置模板（DeepSeek/智谱GLM/月之暗面Kimi/百川智能/通义千问/MiniMax/零一万物/阶跃星辰/硅基流动/Groq/OpenRouter/Together AI/本地 Ollama）
- **适配器测试 API**：PING 连通性测试 + CHAT 对话测试（独立 10 秒超时，API Key 不持久化）
- **增强前端向导**：4 步配置向导（模板选择/基本配置/高级配置/测试连接）+ 模板卡片 + 测试面板
- **适配器定义持久化**：StoreManager 持久化，重启自动恢复，YAML 定义优先合并策略

#### 新增组件

- `AdapterTemplateService`：模板服务（13 个预置模板）
- `AdapterTestService`：适配器连通性测试服务
- `AdapterDefinitionPersistenceService`：适配器定义持久化服务
- `AdapterTemplateController` / `AdapterTestController`：模板与测试 REST API

#### 测试

- 新增 49 个测试用例（TDD 先行），全部通过；Checkstyle 0 violations

---

### \[2.8.3] - 2026-07-15 - 功能发布

#### Gemini 适配器 + 适配器配置增强

- **Gemini 适配器**：新增 Google Gemini 适配器支持
- **adapter-config 阶段1-3**：支持 OpenAI 兼容 Adapter 配置、Ollama 兼容 Adapter 配置、继承+覆盖模式
- **测试覆盖提升**：ErrorResponseBuilder（15 用例）、StreamingRequestProcessor（14 用例）、NonStreamingRequestProcessor（11 用例）、Health 模块（覆盖率 0% → 68-71%）

---

### \[2.8.2] - 2026-07-17 - 代码质量

- Checkstyle 治理：修复 11 个警告

---

### \[2.8.1] - 2026-07-16 - 代码质量

- Checkstyle 治理：修复 66 个警告

---

### \[2.8.0] - 2026-07-15 - 功能发布

#### Anthropic Claude 适配器

新增 Anthropic Claude 适配器支持，使 JAiRouter 可以统一网关承载 Claude API 负载，与现有 OpenAI 兼容后端并存。

- **ClaudeAdapter**：Claude 适配器实现（+295 行）
- **ClaudeAdapterTest**：适配器测试（+404 行，覆盖请求转换、响应解析、错误处理）
- **文档清理与配置示例**：补充 Claude 适配器配置示例

---

### \[2.7.6] - 2026-06-30 - 功能发布

#### API Key 配额管理

新增全面的 API Key 配额管理功能：

- **每日 Token 限额**：跟踪和限制每个 API Key 的每日 Token 使用量
- **请求速率限制**：使用滑动窗口算法限制每分钟请求次数
- **告警阈值**：可配置的使用量百分比告警阈值
- **自动重置**：配额计数器每天午夜自动重置

#### 问题修复

- **API Key 序列化**：修复 `@JsonProperty(access = WRITE_ONLY)` 导致 `keyHash` 无法序列化到数据库的问题
- **Token 使用量跟踪**：改进流式和非流式请求的 Token 使用量跟踪

#### 新增组件

- `ApiKeyQuotaService`：配额检查与告警服务
- `TokenBucketRateLimiter`：滑动窗口限流器
- `ApiKeyQuotaCleanupScheduler`：定时清理过期配额数据

#### 数据库变更

在 `api_keys` 表中新增配额相关字段：
- `daily_token_limit`：每日 Token 使用限额
- `rate_limit_per_minute`：每分钟请求次数限制
- `quota_alert_threshold`：告警阈值百分比
- `today_token_usage`：当日 Token 使用量
- `today_request_count`：当日请求次数
- `last_reset_time`：最后重置时间

---

### \[2.7.7] - 2026-07-10 - 功能发布

#### ExceptionEvent 事件收集修复

修复异常事件收集链路中的多个问题：

- **Bean 冲突修复**：解决 `ExceptionEventService` Bean 初始化冲突
- **错误降级优化**：异常事件收集失败时不影响主流程
- **持久化链路修复**：确保异常事件正确写入数据库

#### API Key 服务级权限检查

- **服务级权限**：API Key 现在支持按服务类型（Chat/Embedding/Rerank 等）进行细粒度权限控制
- **前端权限修复**：修复权限管理页面的显示和交互问题

---

### \[2.7.8] - 2026-07-10 - 功能发布

#### 请求调用历史持久化

新增完整的 API 调用历史记录功能：

- **调用记录存储**：记录每次 API 调用的详细信息（模型、实例、耗时、状态等）
- **查询与统计**：支持按时间范围、模型、服务类型等维度查询和统计
- **前端仪表盘**：新增调用历史可视化页面，支持趋势图和明细表格
- **异常监控增强**：异常管理页面添加业务字段与前端筛选功能

#### 新增组件

- `ApiCallHistoryService`：调用历史记录服务
- `ApiCallHistoryController`：调用历史 REST API
- `CallHistoryDashboard`：前端调用历史仪表盘

---

### \[2.5.15] - 2026-05-11 - 封板版本

#### 超大类重构完成

本次版本完成了 4 个超大类的重构工作，总计减少 2011 行代码（-62%）。

| 文件 | 原行数 | 最终行数 | 减少 | 目标 |
|------|--------|----------|------|------|
| BaseAdapter | 1386 | 416 | -70% | 600 ✅ |
| TracingService | 764 | 483 | -37% | 400 ✅ |
| DefaultStructuredLogger | 945 | 365 | -61% | 400 ✅ |
| ConfigVersionManager | 746 | 387 | -48% | 400 ✅ |

#### 新增组件（12个）

- ConfigComparator、SpanAttributeHelper、ServiceNameResolver
- RequestLogBuilder、ResponseLogBuilder、BackendCallLogBuilder
- ErrorLogBuilder、SystemEventLogBuilder、VersionValidator
- VersionMetadataManager、VersionSyncService、ModelUtils

#### 质量检查

- Checkstyle: ✅ 通过
- SpotBugs: ✅ 通过
- 测试: 971 passed ✅

---

### \[1.7.3] - 2026-04-14

#### 问题修复

* **Playground Chat 流式响应修复**：修复流式响应时 AI 回复消息重复显示问题

  * 问题：流式响应时，AI 回复出现两个一模一样的对话

  * 原因：`MessageList.vue` 中 `displayMessages` 过滤逻辑不正确

  * 修复：流式响应期间，始终过滤最后一条助手消息，由额外的 `MessageBubble` 组件显示流式内容

#### 改进优化

* **端口配置恢复**：服务器端口恢复为默认 `8080`，与文档保持一致

#### 技术改进

* 更新前端静态资源构建产物

---

### \[1.7.2] - 2026-04-14

#### 新增功能

* **Playground 组件化重构**：大幅重构 Playground 模块，实现组件化拆分

  * Chat 模块：`ChatContainer`、`ChatConfigPanel`、`MessageInput`、`MessageList`

  * Audio 模块：`AudioContainer`、`TtsPanel`、`SttPanel`

  * Image 模块：`ImageContainer`、`ImageGeneratePanel`、`ImageEditPanel`

  * Embedding 模块：`EmbeddingContainer`

  * Rerank 模块：`RerankContainer`

  * Common 组件：`MessageBubble`、`MarkdownRenderer`、`CodeBlock`、`ModelSelector`、`ServiceLayout`、`LoadingIndicator`

* **新增 Composables**：

  * `useChatSession`：聊天会话管理（localStorage 持久化）

  * `useMarkdown`：Markdown 渲染处理

  * `useStreaming`：SSE 流式响应处理

#### 改进优化

* **健康检查 SSE 控制器优化**：优化 `HealthStatusSseController` 实现逻辑

* **实例管理功能扩展**：新增字段支持，扩展 `ServiceInstanceDTO` 和实体类

* **Adapter 基类调整**：统一各适配器基类处理逻辑

* **前端路由和布局优化**：优化路由配置和 Layout 组件

#### 新增文件

* `frontend/src/views/playground/components/` - 18 个组件文件

* `frontend/src/views/playground/composables/` - 4 个 composable 文件

* `src/main/resources/db/migration/V3__add_adapter_headers_fields.sql`

---

### \[1.7.1] - 2026-04-13

#### 问题修复

* **链路追踪修复**：

  * 修复 `TracingWebFilter` 重复创建 `traceId` 问题

  * 修复 `TraceQueryService` 的 `recentTraces` 合并时 `spanCount` 显示错误

  * 修复 `TracingService` 的 `serviceName` 分类，前端页面路由正确识别为 'front'

* **前端路由修复**：修复前端路由路径错误 (`/admin/admin/tracing` -> `/admin/tracing`)

#### 改进优化

* **ControllerTracingInterceptor 优化**：优化子 Span 同步记录逻辑

* **表格布局优化**：表格列宽度使用 `min-width` 实现自适应填充

#### 新增功能

* **TraceDetail 组件**：新增链路追踪详情展示组件

* **Tracing Dashboard 页面**：新增链路追踪仪表板页面

#### 新增文件

* `frontend/src/views/tracing/Dashboard.vue`

* `frontend/src/views/tracing/components/TraceDetail.vue`

* `docs/zh/development/tracing-full-chain-design.md`

---

### \[1.7.0] - 2026-04-10

#### 新增功能

* **安全黑名单管理**：新增安全黑名单管理功能，支持 IP/用户/令牌黑名单

* **审计日志增强**：增强审计日志查询和展示功能，支持高级搜索和统计分析

* **JWT账户状态切换**：实现账户启用/禁用状态切换功能

#### 改进优化

* **JWT账户管理**：修复编辑密码验证问题，优化账户管理界面

* **前端表格优化**：表格列使用自适应宽度（min-width），添加统计卡片和搜索功能

* **配置管理简化**：移除未实现的版本管理功能，简化界面

#### 问题修复

* 修复 JWT 账户编辑时的密码验证问题

* 修复账户管理页面数据展示问题

#### 技术改进

* `CreateJwtAccountRequest` 添加 `enabled` 字段

* `JwtAccountService` 实现 `toggleAccountStatus` 方法

* 新增 `SecurityBlacklistController` 和相关实体类

* 清理未使用的前端代码和类型定义

---

### \[1.6.2] - 2026-04-08

#### 新增功能

* **API Key 批量导入/导出**：支持批量导入导出 API Key

  * 新增导出端点 `GET /api/auth/api-keys/export`

  * 新增导入端点 `POST /api/auth/api-keys/import`

  * 支持 MERGE/REPLACE 两种导入模式

* **API Key 密钥轮换**：支持密钥自动轮换机制

  * 配置 `rotationPeriodDays` 设置轮换周期

  * 新增 `ApiKeyRotationScheduler` 自动执行轮换

* **过期密钥自动清理**：新增 `ApiKeyExpirationScheduler` 自动禁用过期密钥

#### 改进优化

* **创建者信息记录**：API Key 创建时记录 `createdBy` 和 `creatorIpAddress`

* **密钥使用统计持久化**：使用统计数据通过 `saveApiKeysToStore()` 持久化

#### 新增文件

* `ApiKeyBatchExportVO.java`

* `ApiKeyBatchImportRequest.java`

* `ApiKeyBatchImportResult.java`

* `ApiKeyRotationScheduler.java`

* `ApiKeyExpirationScheduler.java`

---

### \[1.6.1] - 2026-04-06

#### 安全修复 (P0)

* **API Key 哈希存储**：API Key 使用 SHA-256 + 盐值哈希存储，替代明文存储

* **管理接口速率限制**：添加速率限制 (30/min, 100/hour, 10 create/hour)

#### 新增功能

* **IP 白名单**：支持 IP 白名单功能 (`allowedIpAddresses`)

* **每日请求限制**：支持每日请求限制功能 (`dailyRequestLimit`)

* **密钥重置接口**：新增密钥重置接口 `/api/auth/api-keys/{keyId}/reset`

#### 改进优化

* **前端强类型**：使用强类型 DTO/VO 替代 Map 数据传递

* **表格布局优化**：优化表格布局和横向滚动支持

#### 新增文件

* `ApiKeyHashUtil.java` - SHA-256 哈希工具类

* `AdminApiRateLimiter.java` - 管理接口速率限制过滤器

* `ApiKeyVO/ApiKeyCreationVO/ApiKeyListVO/ApiKeyCreateRequest/ApiKeyUpdateRequest` - 强类型 DTO

---

### \[1.6.0] - 2026-04-04

#### 破坏性变更

* **移除配置合并功能**：移除 AutoMergeService 和 AutoMergeController

* **移除相关实体类**：移除 MergeResult 等 5 个相关实体类

* **移除前端页面**：移除 ConfigMergeManagement.vue 页面和相关 API

#### 改进优化

* **配置版本管理优化**：简化版本管理界面，保留核心版本切换功能

* **日志配置优化**：优化 logback-spring.xml 配置

* **文档更新**：移除配置合并相关内容

#### 保留功能

* `ConfigMergeService`：配置获取和合并核心功能

* `SecurityConfigMergeService`：安全配置合并服务

---

### \[1.5.7] - 2026-04-02

#### 新增功能

* **JWT 账户初始化**：JWT 账户从 YAML 配置自动初始化到数据库

* **账户管理 API 优化**：使用标准 RouterResponse 响应格式

#### 问题修复

* 修复系统启动时 JWT 账户未初始化到数据库的问题

* 修复账户管理页面无法显示数据的问题

* 修复 API 路径与前端不匹配的问题（`/api/admin/accounts` -> `/api/security/jwt/accounts`）

#### 新增文件

* `JwtAccountProperties.java` - 映射 YAML 账户配置

* `JwtConfig.accounts` 字段 - 支持账户列表配置

---

### \[1.5.6] - 2026-03-30

#### 新增功能

* **实例级别限流器独立存储**：新增 `instance_rate_limit` 表存储实例限流器配置

* **实例级别熔断器独立存储**：新增 `instance_circuit_breaker` 表存储实例熔断器配置

* **独立配置 API**：新增独立的限流器/熔断器配置 API 接口

* **强类型 DTO**：使用强类型 DTO 替代 Map 数据传递

#### API 变更

* `GET/PUT /api/config/instance/{type}/{id}/rate-limit`

* `GET/PUT /api/config/instance/{type}/{id}/circuit-breaker`

#### 新增文件

* `InstanceRateLimitEntity/InstanceCircuitBreakerEntity` - 实体类

* `InstanceRateLimitRepository/InstanceCircuitBreakerRepository` - 仓库

* `InstanceRateLimitDTO/InstanceCircuitBreakerDTO` - DTO 类

#### 改进优化

* `build-and-deploy.sh` 脚本自动清理旧编译文件

---

### \[1.5.2] - 2026-03-20

#### 新增功能

* **JPA 迁移完成**：完成 R2DBC 到 JPA 的激进迁移

* **DTO 结构优化**：所有核心功能恢复并优化为 DTO 结构

#### 问题修复

* 修复 JPA 迁移过程中的编译错误

* 修复服务配置功能恢复

---

### \[1.4.6] - 2026-03-10

#### 问题修复

* 修复前端独立配置功能

* 修复前端独立限流器和熔断器配置功能

* 修复数据返回完整性问题

* 修复 `buildInstanceMap` 和 `convertToVO` 方法

---

### \[1.4.4] - 2026-03-31

#### 问题修复

* 修复前端实例管理页面数据格式问题

* 优化数据展示逻辑

---

### \[1.4.3] - 2026-03-25

#### 问题修复

* 修复服务类型验证和异常处理逻辑

* 改进错误提示信息

---

### \[1.4.2] - 2026-03-25

#### 新增功能

* **Adapter 重构计划**：创建适配器重构计划文档，为后续架构优化做准备

---

### \[1.4.1] - 2026-03-24

#### 新增功能

* **值对象模式**：引入 InstanceId 值对象，提升代码类型安全性

---

### \[1.4.0] - 2026-03-24

#### 问题修复

* 优雅修复 ConfigMergeService 阻塞调用警告

* 优化响应式编程模型

---

### \[1.2.5] - 2025-11-26

#### 改进优化

* 合并远程分支更新

* 代码同步和稳定性改进

---

### \[1.1.2] - 2025-10-30

#### 改进优化

* 合并远程分支更新

* 代码稳定性改进

---

### \[1.1.1] - 2025-10-28

#### 改进优化

* **前端路由优化**：改进路由和认证流程

* 代码重构和清理

---

### \[1.1.0] - 2025-10-28

#### 改进优化

* 合并远程分支更新

* 功能稳定性改进

---

### \[1.0.0] - 2025-10-16

#### 新增功能

* **首个正式版本**：JAiRouter 项目首个正式发布版本

* 基础网关功能实现

* 核心适配器支持

---

### \[0.9.2] - 2025-09-30

#### 新增功能

* **ApiKey 模型统一**：合并 ApiKeyInfo 与 ApiKeyProperties 为统一 ApiKey 模型

---

### \[0.9.1] - 2025-09-12

#### 问题修复

* 修复合并错误

* 代码稳定性改进

---

### \[0.9.0] - 2025-09-10

#### 新增功能

* **Web Console 架构**：添加 Web Console 架构设计文档

* 前端管理控制台规划

---

### \[0.8.2] - 2025-09-05

#### 改进优化

* 合并远程分支更新

* 代码同步

---

### \[0.8.1] - 2025-09-03

#### 改进优化

* 更新项目版本号

* 版本管理规范化

---

### \[0.7.3] - 2025-08-27

#### 新增功能

* **JWT 认证**：实现 JWT 认证和用户管理功能

* 安全模块基础功能

---

### \[0.7.2] - 2025-08-27

#### 新增功能

* **分布式追踪文档**：添加分布式追踪系统文档

---

### \[0.7.1] - 2025-08-27

#### 新增功能

* **文档优化**：集成谷歌广告并优化文档样式

---

### \[0.7.0] - 2025-08-22

#### 新增功能

* **慢查询告警**：添加慢查询告警功能

* 监控模块增强

---

### \[0.6.1] - 2025-08-19

#### 新增功能

* **国际化支持**：添加国际化和代码压缩支持

* 文档系统增强

---

### \[0.6.0] - 2025-08-18

#### 新增功能

* **安全认证**：实现 API Key 和 JWT 认证功能

* 安全模块核心功能

---

### \[0.5.0] - 2025-08-18

#### 新增功能

* **文档管理**：重构文档管理流程并提供统一管理脚本

* 文档系统重构

---

### \[0.4.0] - 2025-08-15

#### 新增功能

* **Prometheus 告警规则**：添加 Prometheus 告警规则指南和配置

* 新增 ALERT\_RULES\_GUIDE.md 文件

* 添加 alertmanager.yml 配置

* 创建 docker-compose-monitoring.yml 监控栈配置

- **安全模块**：完整的企业级安全功能，包括API Key认证、JWT令牌支持和双向数据脱敏

- **多租户支持**：租户隔离、资源配额和基于租户的配置管理

- **认证鉴权**：API Key认证机制、JWT Token支持、OAuth 2.0集成和基于角色的访问控制(RBAC)

- **数据保护**：请求/响应数据脱敏、敏感信息加密存储和安全审计日志

- **H2数据库支持**：H2嵌入式数据库作为默认存储，支持配置数据、安全审计、API密钥和JWT账户的自动迁移

- **监控集成**：完整的Prometheus + Grafana监控栈，支持业务指标和基础设施指标收集

- **分布式追踪**：端到端分布式追踪，集成Jaeger/Zipkin实现完整的请求生命周期追踪

- 完善的文档体系和用户指南

- 多语言文档支持（中文/英文）

- 增强的监控和告警功能

- 更多的适配器支持

#### 改进优化

* **Spring Security集成**：与Spring Security框架完全集成，实现强大的认证和授权功能

* **缓存层优化**：Redis缓存优化以提升性能

* **数据库支持**：新增对PostgreSQL和MySQL数据库的支持

* **H2存储性能**：相比文件存储，配置和安全数据操作性能提升5-20倍

* **Docker构建优化**：为中国用户优化的Docker构建，使用阿里云Maven镜像，依赖下载速度提升5-10倍

* 性能优化和内存管理改进

* 更好的错误处理和日志记录

* 配置验证和用户体验提升

#### 问题修复

* 修复已知的内存泄漏问题

* 解决并发场景下的竞态条件

* 改进配置热更新的稳定性

* 安全扫描和漏洞修复

* 修复容器环境下的配置文件路径问题

* 解决Docker容器中的DNS解析问题

---

### \[0.3.2] - 2025-08-20

#### 新增功能

* **监控集成**: 完整的 Prometheus + Grafana 监控栈

* **性能指标**: 新增业务指标和基础设施指标收集

* **告警规则**: 预配置的告警规则和通知机制

* **监控仪表板**: 专业的 Grafana 仪表板模板

#### 改进优化

* **指标优化**: 优化指标收集性能，减少对主业务的影响

* **内存管理**: 改进指标数据的内存使用和清理机制

* **配置简化**: 简化监控相关的配置参数

#### 问题修复

* 修复监控指标在高并发下的数据不准确问题

* 解决长时间运行后的内存泄漏问题

* 修复 Prometheus 指标格式兼容性问题

#### 技术债务

* 重构监控模块的代码结构

* 改进单元测试覆盖率

* 优化构建和部署流程

---

### \[0.3.1] - 2025-08-15

#### 新增功能

* **中国加速构建**: 专门为中国用户优化的 Docker 构建

* **阿里云镜像**: 使用阿里云 Maven 镜像加速依赖下载

* **构建脚本**: 新增 `docker-build-china.sh` 构建脚本

* **Maven Profile**: 新增 `china` profile 支持

#### 改进优化

* **构建速度**: 中国用户构建速度提升 5-10 倍

* **网络优化**: 优化网络连接和超时配置

* **文档完善**: 添加中国用户专用的构建指南

#### 问题修复

* 修复在中国网络环境下的依赖下载超时问题

* 解决 Maven 仓库连接不稳定的问题

* 修复 Docker 构建过程中的网络错误

---

### \[0.3.0] - 2025-08-14

#### 新增功能

* **Docker 容器化**: 完整的 Docker 部署支持

* **多环境部署**: 支持开发、测试、生产环境配置

* **Docker Compose**: 提供完整的容器编排配置

* **健康检查**: 容器级别的健康检查机制

* **监控集成**: 基础的监控指标暴露

#### 改进优化

* **镜像优化**: 多阶段构建，生产镜像约 200MB

* **安全加固**: 非 root 用户运行，最小权限原则

* **性能调优**: 容器环境下的 JVM 参数优化

* **日志管理**: 容器化环境的日志收集和轮转

#### 问题修复

* 修复容器环境下的配置文件路径问题

* 解决容器重启后配置丢失的问题

* 修复网络连接在容器间的通信问题

#### 破坏性变更

* 默认配置文件路径从 `./config` 改为 `/app/config`

* 环境变量命名规范调整

---

### \[0.2.1] - 2025-08--12

#### 新增功能

* **定时清理任务**: 自动清理不活跃的限流器，防止内存泄漏

* **内存优化**: 改进内存使用模式，减少 GC 压力

* **客户端 IP 限流增强**: 更精确的客户端 IP 识别和限流

* **配置文件自动合并**: 支持多版本配置文件的智能合并

#### 改进优化

* **性能提升**: 限流器性能优化，减少锁竞争

* **监控增强**: 新增内存使用和清理任务的监控指标

* **日志优化**: 改进日志格式和性能

* **错误处理**: 更好的异常处理和错误恢复机制

#### 问题修复

* 修复长时间运行后的内存泄漏问题

* 解决高并发场景下的限流器竞态条件

* 修复配置热更新时的线程安全问题

* 解决客户端 IP 获取在代理环境下的问题

#### 技术改进

* 重构限流器清理机制

* 改进单元测试覆盖率到 85%

* 优化代码质量检查规则

---

### \[0.2.0] - 2025-08-11

#### 新增功能

* **限流机制**: 支持 Token Bucket、Leaky Bucket、Sliding Window、Warm Up 四种限流算法

* **熔断器**: 实现熔断器模式，支持失败阈值、恢复检测、降级策略

* **降级策略**: 支持默认响应和缓存降级

* **配置持久化**: 支持内存存储和文件存储两种后端

* **动态配置更新**: 运行时更新服务实例、权重、限流、熔断等配置

#### 改进优化

* **性能优化**: 响应式编程模型，支持高并发处理

* **配置管理**: 配置文件自动合并和版本管理

* **错误处理**: 完善的异常处理和错误恢复机制

* **监控指标**: 新增限流、熔断相关的监控指标

#### 问题修复

* 修复负载均衡器在实例变更时的线程安全问题

* 解决配置更新时的数据一致性问题

* 修复高并发场景下的内存泄漏问题

#### API 变更

* 新增动态配置管理 API (`/api/config/instance/*`)

* 新增配置版本管理 API (`/api/config/versions/*`)

* 扩展健康检查 API，包含更多状态信息

---

### \[0.1.0] - 2025-08-04

#### 新增功能

* **基础网关**: 统一 `/v1/*` API 网关，支持 OpenAI 兼容格式

* **服务类型支持**: Chat、Embedding、Rerank、TTS、STT、Image Generation、Image Editing

* **适配器模式**: 支持 GPUStack、Ollama、VLLM、Xinference、LocalAI、OpenAI 适配器

* **负载均衡**: 实现 Random、Round Robin、Least Connections、IP Hash 四种策略

* **健康检查**: 每服务独立状态接口，自动剔除不可用实例

* **配置管理**: 基于 YAML 的静态配置支持

#### 技术特性

* **Spring Boot 3.5.x**: 基于最新的 Spring Boot 框架

* **响应式编程**: 使用 Spring WebFlux 和 Reactor Core

* **代码质量**: 集成 Checkstyle、SpotBugs、JaCoCo 代码质量工具

* **API 文档**: 使用 SpringDoc OpenAPI 自动生成 API 文档

* **单元测试**: 包含负载均衡、健康检查等核心功能的单元测试

#### 项目结构

* 建立清晰的模块化架构

* 定义统一的编码规范和最佳实践

* 建立完整的构建和测试流程

---

## 升级指南

### 从 0.3.1 升级到 0.3.2

#### 配置变更

```yaml
# 新增监控配置
monitoring:
  metrics:
    enabled: true
    categories:
      - system
      - business
      - infrastructure
```

#### 部署变更

* 新增 Prometheus 和 Grafana 容器

* 更新 `docker-compose.yml` 配置

* 导入新的 Grafana 仪表板

#### 注意事项

* 监控功能默认启用，可能会增加少量性能开销

* 新增的监控端点需要相应的网络配置

### 从 0.2.1 升级到 0.3.0

#### 破坏性变更

* 配置文件路径变更：`./config` → `/app/config`

* 环境变量命名调整

#### 迁移步骤

1. 更新配置文件路径
2. 调整环境变量名称
3. 更新部署脚本和容器配置

### 从 0.1.0 升级到 0.2.0

#### 新增依赖

* 无需额外依赖，所有功能已内置

#### 配置扩展

```yaml
# 新增限流配置
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10

# 新增熔断配置
      circuit-breaker:
        failure-threshold: 5
        recovery-timeout: 30s
        success-threshold: 3
```

## 已知问题

### 当前版本 (0.3.2)

* 在极高并发场景下（>10k RPS），监控指标可能出现轻微延迟

* Docker 容器在某些网络环境下可能出现 DNS 解析缓慢

### 历史问题

* ~~0.3.1: 中国网络环境下构建超时~~ (已修复)

* ~~0.2.1: 长时间运行后内存泄漏~~ (已修复)

* ~~0.2.0: 高并发下配置更新竞态条件~~ (已修复)

## 贡献者

感谢所有为 JAiRouter 项目做出贡献的开发者：

* **核心团队**: 负责架构设计和核心功能开发

* **社区贡献者**: 提供功能建议、问题报告和代码贡献

* **文档团队**: 完善项目文档和用户指南

* **测试团队**: 进行功能测试和性能验证

## 反馈和建议

如果您在使用过程中遇到问题或有改进建议，欢迎通过以下方式反馈：

* **GitHub Issues**: [提交问题报告](https://github.com/Lincoln-cn/JAiRouter/issues)

* **GitHub Discussions**: [参与讨论](https://github.com/Lincoln-cn/JAiRouter/discussions)

* **邮件联系**: <jairouter@example.com>

我们重视每一个反馈，并会及时响应和处理。

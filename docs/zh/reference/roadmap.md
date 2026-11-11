# 发展路线图

<!-- 版本信息 -->
> **文档版本**: 2.5.0
> **最后更新**: 2026-10-30
> **Git 标签**: v3.8.4
> **作者**: Lincoln
<!-- /版本信息 -->



本文档描述了 JAiRouter 项目的未来发展规划和功能路线图。

## 项目愿景

JAiRouter 致力于成为最优秀的开源 AI 模型服务路由网关，为用户提供：

- **统一接入**: 一个网关接入所有 AI 模型服务
- **智能路由**: 基于多种策略的智能负载均衡
- **高可用性**: 完善的容错和故障恢复机制
- **易于使用**: 简单的配置和丰富的文档
- **高性能**: 支持大规模并发和低延迟响应
- **可观测性**: 全面的监控、日志和链路追踪

## 当前版本状态

**最新版本：v3.8.4（2026-11-12）。** 逐版本说明、升级提示与已知边界见[更新日志](changelog.md)，
下载与容器 tag 见 [Releases](https://github.com/Lincoln-cn/JAiRouter/releases)。

本页只保留方向性内容（功能规划、架构演进、发布周期）。**版本历史不在本页重复** ——
此前这里抄了一份，结果落后 changelog 五个版本；只有一个事实来源才不会失真。

## 功能特性规划

### 适配器扩展

#### 已支持 ✅
| 适配器 | 类型 | 状态 |
|--------|------|------|
| GPUStack | Chat/Embedding/Rerank | ✅ |
| Ollama | Chat/Embedding | ✅ |
| vLLM | Chat | ✅ |
| Xinference | Chat/Embedding/Rerank | ✅ |
| LocalAI | Chat/Embedding | ✅ |
| OpenAI | Chat/Embedding | ✅ |
| Azure OpenAI | Chat/Embedding | ✅ |
| Anthropic Claude | Chat | ✅ |
| 阿里云百炼 | Chat/Embedding | ✅ |
| 腾讯云混元 | Chat | ✅ |
| 百度智能云 | Chat | ✅ |
| Google Gemini | Chat/Embedding | ✅ |

#### 计划支持 📋
- 📋 Cohere API（版本待定）
- 📋 AWS Bedrock（版本待定）

> **2026-10-16 审计更正**：`Google Gemini` 已实现 —— `AdapterRegistry` 里注册为
> `adapters.put("gemini", …)`，`GeminiAdapter.supportCapability()` = `chat` + `embedding`
> ⇒ 已移入「已支持」；另两项原先写的 `v2.8.x` / `v2.9.x` 版本号早已过期，改为「版本待定」。

### 负载均衡策略

#### 已实现 ✅
- ✅ Random (随机)
- ✅ Round Robin (轮询)
- ✅ Weighted Round Robin (加权轮询)
- ✅ Least Connections (最少连接)
- ✅ IP Hash (IP哈希)
- ✅ Consistent Hash (一致性哈希)

#### 已实现 ✅（续）
- ✅ Latency-Aware（延迟感知，`LatencyAwareLoadBalancer`）
- ✅ Sticky（会话粘滞，`StickyLoadBalancer`）

#### 计划实现 📋
- 📋 Cost-based (成本优先)
- 📋 Model Capability-based (能力匹配)

> **2026-10-16 审计更正**：延迟优先其实**已实现**（`LatencyAwareLoadBalancer`），原先被列在「计划实现」里；
> 会话粘滞此前未列出，一并补上。

### 限流算法

#### 已实现 ✅
- ✅ Token Bucket (令牌桶)
- ✅ Leaky Bucket (漏桶)
- ✅ Sliding Window (滑动窗口)
- ✅ Warm Up (预热限流)

#### 已实现 ✅（续）
- ✅ API Key 级限流（每键 `rateLimitPerMinute` + 令牌桶；超限返回 **429** 与 `RATE_LIMITED_API_KEY`，#404）
- ✅ 终端用户级限流（v3.5.2：按 `(API Key × 终端用户)` 计每分钟额度，默认关闭；身份取 `X-End-User-Id` 或请求体 `user`；超限 429 带 `Retry-After` / `X-RateLimit-*`；Redis 跨副本计数由 `jairouter.ratelimit.distributed.enabled` 控制）
- ✅ 客户端维度限流（`RedisSlidingWindowRateLimiter`，1 秒窗口）：**v3.5.2 起已接进主链路**（#623），由
  `model.rate-limit.client-ip-enable` 或服务级同名开关控制，**仓库自带配置一律为 `false`**；键维度
  `client-ip` / `api-key` / `tenant`（`tenant` 目前等同 `api-key`，系统还没有租户身份）

#### 计划实现 📋
- 📋 限流键维度的租户语义（#623 遗留）：`tenant` 维度当前等同 `api-key`；要按真实租户计数，得先引入租户标识

> **2026-10-16 审计更正**：「API Key 级别的限流」与「分布式限流」原先都列在计划里，实际**都已实现**
> （前者见 `ApiKeyValidator.validateRateLimit`，后者见 `RateLimitManager` 对 `RedisSlidingWindowRateLimiter` 的装配）。
>
> **2026-10-16 审计更正（第二轮）**：「Adaptive Rate Limiting（自适应限流）」原先列在「已实现」里，
> 但全仓**没有该实现**（`router/ratelimit/**` 下没有这个算法，其余文档也只列了
> token-bucket / leaky-bucket / sliding-window / warm-up 四种）⇒ 该行已删除。
>
> **2026-10-24 更正**：上面第二轮所说的「分布式限流已实现」需要收窄 —— 实现确实在仓库里，但**客户端维度那条
> 路径没有生产调用点**（#623），开关与文档口径此前给出了「已生效」的错觉；同期落地的终端用户级限流才是
> 第一处真正使用 Redis 共享计数的限流路径。
>
> **2026-10-24 更正（接线完成）**：上面那条缺口已补 —— `RateLimitManager.tryAcquireClientIp` 现由主链路在
> 认证之后、服务级预扣之前调用；为避免集群/无反代部署下按 IP 合并客户端，**仓库自带配置把
> `client-ip-enable` 一律置为 `false`**，需要时由运维显式开启。

### 监控和可观测性

#### 已实现 ✅
- ✅ Prometheus 指标
- ✅ Grafana 仪表板
- ✅ 健康检查端点
- ✅ 基础告警规则
- ✅ 分布式链路追踪 (Zipkin/OpenTelemetry)
- ✅ 结构化日志

#### 计划实现 📋
- 📋 业务指标分析
- 📋 成本分析
- 📋 自定义告警规则

## 技术架构

### 当前架构 (v3.3.5)
```
单体应用 → 模块化设计 → 响应式编程
```

### 架构演进

| 阶段 | 版本 | 状态 |
|------|------|------|
| 基础架构 | v0.1 - v0.3 | ✅ 已完成 |
| 安全认证 | v0.4 | ✅ 已完成 |
| 监控追踪 | v0.5 | ✅ 已完成 |
| 性能优化 | v2.7.x | ✅ 已完成 |
| 配置管理 | v2.8.x | ✅ 已完成（模块化配置 + 三批配置诚实性收口） |
| 可维护性提升 | v2.9.x | ✅ 已完成（重构记录 / 门闩体系 / 错误码注册表） |
| 微服务探索 | v3.0 | ⏸️ 推迟（当前单体架构已满足需求） |

### 技术栈

#### 核心技术
- **后端**: Spring Boot 3.5.5 + WebFlux (响应式)
- **前端**: Vue 3 + TypeScript + Element Plus
- **数据库**: H2 (嵌入式) + R2DBC
- **缓存**: Redis (可选)
- **监控**: Prometheus + Grafana
- **追踪**: OpenTelemetry + Zipkin

#### 数据存储
- **默认**: H2 嵌入式数据库（单文件；**不可跨副本共享**）
- **现已支持**: **PostgreSQL** —— 显式设置 `DATABASE_URL=jdbc:postgresql://…` 即走 PostgreSQL（多副本横向扩展需要外部共享库）；
  两条路径都有**版本化 schema 迁移**（PostgreSQL 走 Flyway 并把 `ddl-auto` 覆盖为 `validate`，H2 走启动期兼容补丁）
- **规划中**: MySQL（无驱动与方言脚本，未支持）
- **缓存**: Redis (可选)

> **2026-10-16 审计更正**：原文写「当前唯一支持 H2，PostgreSQL / MySQL 规划中」——**PostgreSQL 自 v3.0.0 起已支持**
> （`pom.xml` 的 `org.postgresql:postgresql` + `flyway-database-postgresql`、`application-migrate.yml` 的迁移模式、
> `FlywayDialectEnvironmentPostProcessor` 的方言覆盖）。MySQL 仍未支持。

## 性能目标

> ⚠️ 下表是**设计目标**，不是实测值（本仓未做基准压测）；引用前请自行实测。

| 版本 | RPS | 延迟 (P95) | 可用性 | 并发连接 |
|------|-----|-----------|--------|----------|
| v2.6.x | 5k | < 50ms | 99.95% | 5k |
| v2.7.x | 10k | < 30ms | 99.95% | 10k |
| v2.8.x | 20k | < 20ms | 99.99% | 20k |
| 目标 | 100k+ | < 10ms | 99.99% | 100k+ |

## 社区发展

### 当前状态
- GitHub / Gitee 双平台托管
- 完整的用户文档和 API 文档
- 中英文文档支持
- MkDocs 静态网站

### 发展目标
- 活跃的开发者社区
- 定期的版本发布
- 更多适配器支持
- 完善的插件系统

## LTS 政策调整说明（2026-09）

**决定：终止 LTS（长期支持）版本策略。**

**背景**：项目迭代节奏明显加快（v2.9 → v3.1 期间发布约 20 个版本）。维护一条独立的 LTS 线意味着对旧分支持续做回归测试、安全补丁与文档双轨维护，投入产出已不匹配，因此不再对外承诺 LTS 维护周期。

| 事项 | 说明 |
|------|------|
| 既有发布物 | `v2.6.11` 的 Git 标签、Docker 镜像与 Release **仍可下载、可用于回滚**，但**不再有专门的维护承诺**（不保证提供安全补丁或兼容性修复） |
| 后续维护线 | 只维护**当前版本（latest）一条线** |
| 升级建议 | 跟随最新版本；破坏性变更会在[更新日志](changelog.md) 中标注 |
| 需要长期稳定 | 请固定到具体版本标签（如 `v3.1.1`）并自建回归测试，拉取镜像时使用对应版本 tag 而非 `latest` |

> 本页「版本发布周期」表中的 LTS 行已随本政策一并移除。

## 版本发布周期

| 版本类型 | 周期 | 说明 |
|----------|------|------|
| 功能版 | 1-2个月 | 新功能迭代 |
| 补丁版 | 按需 | Bug 修复和安全更新 |

---

## 参与方式

### 开发贡献

#### 代码贡献
- 功能开发和 Bug 修复
- 性能优化和重构
- 测试用例编写
- 代码审查

#### 文档贡献
- 用户文档完善
- API 文档更新
- 教程和示例编写
- 多语言翻译

### 反馈渠道

- **GitHub Issues**: [https://github.com/Lincoln-cn/JAiRouter/issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [https://github.com/Lincoln-cn/JAiRouter/discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **文档反馈**: 通过 GitHub PR 提交

---

## 风险和挑战

### 技术风险
- **性能瓶颈**: 大规模部署下的性能挑战
- **兼容性**: 多版本 API 兼容性维护
- **安全性**: 安全漏洞和攻击防护
- **横向扩展受限**: 当前唯一支持 H2 嵌入式单文件数据库，多副本无法共享同一数据文件，`replicas` 必须为 1；限流、配额、JWT 黑名单、API-Key 缓存等状态默认均为进程内语义。落地顺序见 issue #160（共享数据库适配）、#161（限流跨实例化）、#162（共享态接线）、#163（调度锁）、#164（实时事件广播）、#165（K8s 制品）

### 市场风险
- **竞争加剧**: 类似产品的竞争
- **技术变化**: AI 技术快速发展带来的挑战
- **用户需求**: 用户需求的快速变化

### 应对策略
- 持续的技术创新和优化
- 活跃的社区建设和维护
- 灵活的产品策略调整
- 完善的质量保证体系

---

## 总结

JAiRouter 项目将继续秉承开源精神，致力于为用户提供最优秀的 AI 模型服务路由解决方案。我们欢迎社区的参与和贡献，共同推动项目的发展和进步。

### 近期重点 (2026年)
1. ✅ 完成 v2.7.x 性能优化系列 + RBAC权限控制
2. ✅ 推进 v2.8.x 配置管理优化 + 新适配器 + 插件系统/规则引擎/资源池
3. ✅ v2.9.x 智能路由深化系列（故障转移/标签路由/响应缓存 P0）
4. ✅ v2.9.10 响应缓存 P1（流式缓存 + 失效 API + 限流短路）
5. ✅ v2.9.11 README/截图更新 + hallmark 管理台设计审计 + 前端修复
6. ✅ v2.10.x Web 管理台重构系列全部完成（v2.10.0 前端地基 ✅ 2026-09-05；v2.10.1 治理指挥台 ✅ / v2.10.2 配置接入与能力补页 ✅ / v2.10.3 Web 双语版 ✅ / v2.10.4 Web 体验收尾 ✅ 2026-09-06）
7. ✅ v3.0.x Web 完整流程系列（v3.0.1 流程串联 ✅ 2026-09-06；v3.0.2 API 清理与权限收口 ✅ 2026-09-07；v3.0.3 验收与发布 ✅ 2026-09-13）
8. ✅ v3.1.0 配额账本与多协议入口 ✅ 2026-09-14（多维多窗配额 + Anthropic 入口 + 工具调用 + 运行时配置面/观测面）
9. ✅ v3.2.0 PII 治理与配额操作闭环（2026-09-19 发布）
10. ✅ v3.2.1 审计整改补丁（2026-09-21 发布）：Reactor/SSE 去阻塞、配额 TOCTOU 与 Redis Lua CAS、伪 401 修复、追踪链重复执行修复、JWT 黑名单降级收敛、生产 fail-fast、SSRF 防护、RBAC/调试端点收紧、用户正则 ReDoS 收口。**已知剩余（P2）**：`QuotaLedgerService` 分布式路径同步 `block`、前端临时 ID 使用 `Math.random`
11. ✅ v3.2.2 补丁（2026-09-22 发布）：用户上报的 API Key 配额回显（#84）与路由监控计数 +3（#83）修复；管理面业务失败由 HTTP 200 改为 4xx/5xx（#94，响应体兼容），并收口配额接口 `block` 失败（#92）、e2e 断言假通过（#95）、告警阈值口径（#96）、监控接口错误码（#98）。**已知剩余（P2）**：`QuotaLedgerService` 分布式路径同步 `block`、前端临时 ID 使用 `Math.random`
12. ✅ v3.3.0 次版本（2026-10-07 发布）：PostgreSQL 与版本化迁移（PG 切 `ddl-auto: validate`、H2 也接上、删两个启动期补丁组件）；多副本集群（跨副本事件广播与滑动窗口计数、共享态自检、定时任务按类处置）；RBAC 收口到 GET 也 fail-closed；tracing 配置诚实性四批收口（删 57 个零消费方字段、接 3 个）；容器 stdout 结构化 JSON 与 Kustomize 制品；文档事实逐条核对修正（#160–#248）
13. ✅ v3.4.0 功能版（2026-10-21 发布）：控制台重构（7 组 28 项导航、可折叠侧栏、暗色层级、窄屏卡片化、33 页去彩虹化、全局搜索与状态条）、权限语义化（8 个只读码，55 → 63）、脱敏配置持久化（Flyway V4）、配额小时/月限额。**顺延**：记录级 FULL 加密策略 UI 随前端轨道（v3.5.0 系列）落地
14. ✅ v3.5.0 功能版（2026-10-24 发布）：响应脱敏覆盖面收口（`/v1/**` 非流式 JSON、`response.scope` 可配置、缓冲上限、SSE 逐帧）、默认 PII 规则与 JSON 键豁免修正、请求侧配置面移除（Flyway V5）、熔断器三项治理、控制台验收修正
15. ✅ v3.5.3 补丁（2026-10-25 发布）：主流程审计五条修复（外部挂载配置真正覆盖 #634、图像编辑改 multipart #637、实例健康键 #636、出站路径取自选中实例 #635、adapter 引用启动期校验 #638）+ `model.services` 单源化 #643；无 API 破坏性变更，但 #634 / #637 属行为变更
16. ✅ v3.5.2 功能版（2026-10-24 发布）：终端用户级限流（按 `(API Key × 终端用户)` 计每分钟额度，默认关闭、身份自报不构成安全边界，本机有界计数或 Redis 跨副本计数）；客户端维度限流接线（#623，仓库默认关闭，避免无 L7 反代时按 IP 合并客户端）；服务级 429 与实例全限 503 补 `Retry-After`（#624）；`TokenBucketRateLimiter` 窗口表改有界（#625）
17. ✅ v3.5.1 补丁（2026-10-24 发布）：记录治理页与 call-history 密钥来源接线（`encryption-key-source: env` 由「能设不生效」改为真生效，`GET /api/config/call-history` 增 8 个只读观测字段，新页 `/security/record-governance`）；配置快照 `gatewayResponseFilter` 实时化；技术债 #604 的 A～D 批次（端到端 400 用例 + 裸捕获上限 835 → 786、`QuotaLedgerService` 1268 → 1094、`ServiceRequestHandler` 1027 → 785）；文档 API 路径统一 `/api/v1/*`、围栏配平检查接入文档门闩
18. ✅ v3.5.4 补丁（2026-10-27 发布）：路由与熔断记账（4xx 不计熔断 #647、限流后继续尝试 #654、随机负载均衡器去自有熔断器 #648、按策略名缓存 #649、自适应阈值真正生效 #653、失败释放在途计数 #655）、健康检查加固（#650–#652）、随包凭据收口（内置测试 API Key 移入 dev #663、密钥与口令改随机兜底 #664、删隐式 dev 默认 #666）、logback 兜底（#669）、staging 必填项文档（#670）；无 API 破坏性变更
19. 📋 语义缓存评估（向量相似度复用，独立项目）；高可用地基（多节点/Redis）在 v3.2.x 后再评估

### 长期愿景
1. 成为 AI 模型路由领域的标准
2. 建立完整的生态系统
3. 实现企业级商业化成功
4. 推动行业技术发展

---

**更新时间**: 2026年10月24日

如有任何建议或想法，欢迎通过 [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions) 与我们交流。

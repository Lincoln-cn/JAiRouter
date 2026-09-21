# 发展路线图

<!-- 版本信息 -->
> **文档版本**: 2.1.0
> **最后更新**: 2026-09-21
> **Git 标签**: v3.2.1
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

### ✅ v3.2.1（审计整改补丁，已发布 2026-09-21）

**状态**: 已发布（Git tag / GitHub Release `v3.2.1`；Docker 镜像随 Release 由 CI 重建）
**范围**: v3.2.0 发布后合入的审计整改批次（Reactor / SSE / 配额 / 鉴权 / SSRF）+ 用户正则 ReDoS 收口

| 领域 | 交付 |
|------|------|
| 并发与流式 | 健康 SSE 与请求主链同步段移出 EventLoop；流式完成回调与指标/调用历史去掉 `block(5s)`；SSE/WS Sink 加背压上限；冷路径 `block` 统一超时（5s / 启动 30s） |
| 配额与限流 | 单机「判定 + 预留」串行化（消除 TOCTOU）；分布式跨实例竞态改为 Redis 原子 Lua CAS（HINCRBY + 上限检查 + 超限回滚） |
| 认证与授权 | 修复认证过滤器伪 401（JWT 登录态下控制台 AI 面与 `/v1/**` 曾全不可用）；控制台 JWT 登录态放行；追踪过滤器不再重复执行过滤器链；JWT 黑名单存储不可用降级收敛（首次告警 + 30s 短路 + 启动探测）；`/ws/**` 与健康 SSE 需认证（前端改带 query token）；RBAC 缺口补齐；调试端点默认关闭并脱敏；Swagger 默认需认证 |
| 启动与配置 | 拒绝出厂默认 JWT 密钥；生产环境缺密钥或使用默认值启动即失败（`StartupSecurityGate`） |
| SSRF | 实例 URL `SsrfGuard`：覆盖实例创建/更新、配置同步、出站 `WebClient`、适配器连通性测试 |
| 用户正则 | 新增 `SafeRegexValidator` 统一闸门（长度上限 512、嵌套量词、被量词包裹的交替分支、反向引用、量词上限），两个脱敏引擎三条编译路径与配置面校验全部收口；检测器自身改为 O(n) 字符扫描（不对用户输入跑正则） |

### ✅ v3.2.0（已发布 2026-09-19）

**状态**: 已发布（GitHub Release `v3.2.0`；PR #60/#64/#65）
**范围**: PII 脱敏管理闭环 + 配额观测操作闭环 + TDD 测试地基

| 领域 | 交付 |
|------|------|
| PII 记录链路 | `sanitizeForStorage` 与网关 request/response 开关解耦；调用历史 SUMMARY / 追踪使用该路径 |
| 管理 API | `GET/PUT /api/config/sanitization`、规则列表、试脱敏（全链路响应式；非 JSON 样例按文本掩码） |
| 控制台 | 安全管理 → PII 脱敏管理；权限 `security:sanitization:manage`（权限码 49） |
| 排除路径 | 管理台 `/api/**` 与 AI 实时路径排除网关响应脱敏 |
| 配额 UX | 监控页限额/进度对照 + 与 API Key 页交叉链接；文档：`0`=不限制，阈值 `0.0–1.0` |
| 质量 | Redis TTL flaky 断言修复；vitest 单测/API/组件；页面冒烟 10/10 |

### ✅ v3.1.2（上一稳定补丁）

**发布状态**: 已发布（2026-09-18）
**说明**: Docker 启动 / JWT_SECRET / 镜像前端 static/admin 等补丁

### ✅ v3.1.1（上一稳定版）

**发布状态**: 已发布（2026-09-15）
**Git 标签**: v3.1.1

#### 主要功能（v3.0.2 → v3.1.1 演进）
| 版本 | 日期 | 核心内容 |
|------|------|---------|
| v3.0.2 | 2026-09-07 | API 清理与权限收口：删除 4 废弃控制器 + DTO 弃用项清理 + 权限 URL 规则渐进登记（累计 47 条）+ PermissionClosureTest 35 断言 + 前端死函数清理 |
| v3.0.3 | 2026-09-09 | 端到端旅程验收（10/10 PASS）+ README 素材重拍（32 张 = 8 页 × 中/英 × 亮/暗）+ 冷启动深链修复 + i18n/图标告警清理 |
| v3.1.0 | 2026-09-14 | 配额账本与多协议入口：多维多窗配额账本（MINUTE/HOUR/DAY/MONTH + JPA 持久化 + Redis 分布式计数与断连降级，默认关闭）+ 配额运行时配置面与观测面（控制台 3 页）+ 权限码增至 48 + OpenAI `/v1/models` + Anthropic `/v1/messages`（非流式/流式/`count_tokens`/工具调用）+ 网关错误体协议化 + 错误体 UTF-8 修复 |
| v3.1.1 | 2026-09-15 | 补丁修复：401 形状按协议输出 + `count_tokens` 计入 tools/tool_use/tool_result + `/v1/**` 错误消息去噪 + 控制台 SSE 路由切换误报告警修复 |
| v3.1.2 | 2026-09-18 | Docker/启动补丁：`JWT_SECRET` 统一、`--generate-key` 修复、镜像前端 static/admin 路径、Redis 健康探针条件化 |
| v3.2.0 | 2026-09-19 | PII 脱敏管理（控制台 + `/api/config/sanitization/**` + `sanitizeForStorage`）+ 配额监控限额/进度 UX + vitest/页面冒烟 TDD 地基 + 排除路径对齐（PR #60/#64/#65） |
| v3.2.1 | 2026-09-21 | 审计整改补丁：Reactor/SSE 去阻塞 + 配额 TOCTOU 与 Redis Lua CAS + 伪 401 修复 + 追踪链重复执行修复 + JWT 黑名单降级收敛 + 生产 fail-fast + SSRF 防护 + RBAC/调试端点收紧 + 用户正则 ReDoS 收口（PR #67–#72、#78–#81） |

#### 统计数据
- 测试数量: 3,517（全绿）
- Java 源文件: 750+
- 代码规模: ~140k LOC

---

### ✅ v2.10.x - Web 管理台重构系列（已完成，2026-09-06）

前端功能已齐但未形成完整系统流程，按每版一主题拆分重构：
- **v2.10.0 前端地基 ✅（2026-09-05 已发布）**：死代码清理（13 .vue + common ≈13.5k 行）+ PageSkeleton/StatCard/useChartTheme + Dashboard·Service·Rule 试点 + ~20 页令牌化 + Element Plus 暗色修复 + Layout/console 清理
- **v2.10.1 治理指挥台 ✅（2026-09-06 已发布）**：Dashboard v2 治理链路面板 + 异常摘要 + Cross-link 跨页导航 + 7 页 PageSkeleton 迁移 + 令牌化收尾
- **v2.10.2 配置接入与能力补页 ✅（2026-09-06 已发布）**：响应缓存管理页（状态/失效 + B 档运行时开关 + 命中观测）+ 慢查询分析页 + CB 重置/配额告警 + Onboarding 接入引导 + 后端缓存状态与废弃标注 + 权限码 45
- **v2.10.3 Web 双语版 ✅（2026-09-06 已发布）**：全量 zh/en i18n + 语言切换器 + Element Plus locale + 日期/数字本地化 + 暗色对比收口
- **v2.10.4 Web 体验收尾 ✅（2026-09-06 已发布）**：服务类型显示名统一 + ECharts 图表语言/主题热切换 + 死代码清理（utils/errorHandler、validators/* 等）+ 暗色对比审计门禁（36 页 ≥4.5）+ 后端 message 文档注记 + 代码块暗色语法高亮

---

### 🎯 v3.0.x - Web 完整流程系列 (进行中)

v3.0 为 Web 完整流程里程碑（对外 /api/v1 保持兼容，无 breaking）：
- **v3.0.1 流程串联 ✅（2026-09-06 已发布）**：Onboarding 预选闭环（useRoutePreselect + 5 容器 query 预选）+ Dashboard 治理入口补全 + CB/LB 反向跳转 + 版本页入站 + 旧 redirect 清理 + PageSkeleton 覆盖 18 页
- **v3.0.2 API 清理与权限收口 ✅（2026-09-07 已完成）**：删除 4 废弃控制器（ServiceConfigController/ServiceInstanceController/InstanceConfigController/SecurityAuditController）与 TokenUsageController 5 个死方法 + DTO 弃用项清理 + PermissionRuleRegistry 清理废弃绑定 + 权限 URL 规则渐进登记 15 条（共 47 条）+ PermissionClosureTest 35 断言 + 前端死函数清理；mvn 3202 用例全绿
- **v3.0.3 验收与发布 ✅（2026-09-13 已发布）**：端到端旅程验收（10/10 PASS，0 pageerror/0 个 5xx）+ README 素材重拍（32 张 = 8 页 × 中/英 × 亮/暗）+ 文档/计划同步 + 双远端 tag/Release/Docker；另修复冷启动深链（`/security/*`、`/system/*` 角色守卫）与 i18n/图标告警
- **v3.1.0 配额账本与多协议入口 ✅（2026-09-14 已发布）**：多维多窗配额账本（MINUTE/HOUR/DAY/MONTH + JPA 持久化 + Redis 分布式计数与断连降级）+ 配额运行时配置面与观测面（控制台 3 页）+ OpenAI 原生面 `/v1/models` + Anthropic `/v1/messages`（非流式/流式/`count_tokens`/工具调用）+ 网关错误体协议化 + 错误体字符集 UTF-8 修复 + 流式路径实例级 headers 修复
- 语义缓存评估（向量相似度复用，另立项）；高可用地基（多节点/Redis 分布式/配置回滚）于 v3.1.x 后评估；微服务化另行

---

### ✅ v2.6.11（已发布的长期支持版，LTS 政策已终止）

**发布状态**: 已发布 (2026-04-17)
**维护周期**: 已终止（原计划至 2028-05，见下方"LTS 政策调整说明"）

#### 已完成功能
- ✅ 多租户支持
- ✅ API Key 认证机制
- ✅ JWT Token 支持
- ✅ OAuth 2.0 集成
- ✅ 基于角色的访问控制 (RBAC)
- ✅ 请求/响应数据脱敏
- ✅ 安全审计日志
- ✅ H2 嵌入式数据库
- ✅ PostgreSQL/MySQL 支持
- ✅ Redis 缓存集成
- ✅ Prometheus 指标收集
- ✅ Grafana 仪表板模板
- ✅ 分布式链路追踪 (Zipkin/OpenTelemetry)
- ✅ 完整的 Docker 部署方案
- ✅ Kubernetes 部署支持

#### 代码质量
- ✅ Checkstyle 代码规范检查
- ✅ SpotBugs 静态分析
- ✅ JaCoCo 测试覆盖率报告
- ✅ 700+ 单元测试
- ✅ E2E 集成测试

---

### 🎯 v2.7.x - 性能优化系列 (2026年Q2-Q3)

#### 已完成
| 版本 | 日期 | 主要内容 |
|------|------|----------|
| v2.7.0 | 2026-04-20 | Package 结构重组，6个服务模块 |
| v2.7.1 | 2026-04-21 | auth 模块独立 (116 文件) |
| v2.7.2 | 2026-04-22 | config 模块独立 (~50 文件) |
| v2.7.3 | 2026-04-23 | router 模块上 (adapter/loadbalancer) |
| v2.7.4 | 2026-04-24 | router 模块下 (熔断/限流) |
| v2.7.5 | 2026-04-25 | monitor 模块独立 (98 文件) |
| v2.7.6 | 2026-04-26 | persistence 模块独立 (49 文件) |
| v2.7.7 | 2026-04-27 | common 模块独立 (96 文件) |
| v2.7.8 | 2026-04-28 | controller 分组优化 |
| v2.7.9 | 2026-04-29 | 包结构优化完成 |
| v2.7.10 | 2026-07-13 | 技术债务清理 - 大文件拆分+废弃代码清理 |
| v2.7.11 | 2026-07-14 | RBAC权限控制 + UI优化 |

#### 性能提升
- 路由流程优化 20-50%
- 内存占用降低 15%
- 启动时间减少 30%

---

### ⏸️ v3.0 - 微服务架构 (无限期推迟)

**状态**: ⏸️ 推迟

由于当前单体架构已满足需求，v3.0 微服务架构转型已无限期推迟。

---

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

#### 计划支持 📋
- 📋 Google Gemini (v2.8.x)
- 📋 Cohere API (v2.8.x)
- 📋 AWS Bedrock (v2.9.x)

### 负载均衡策略

#### 已实现 ✅
- ✅ Random (随机)
- ✅ Round Robin (轮询)
- ✅ Weighted Round Robin (加权轮询)
- ✅ Least Connections (最少连接)
- ✅ IP Hash (IP哈希)
- ✅ Consistent Hash (一致性哈希)

#### 计划实现 📋
- 📋 Latency-based (延迟优先)
- 📋 Cost-based (成本优先)
- 📋 Model Capability-based (能力匹配)

### 限流算法

#### 已实现 ✅
- ✅ Token Bucket (令牌桶)
- ✅ Leaky Bucket (漏桶)
- ✅ Sliding Window (滑动窗口)
- ✅ Warm Up (预热限流)
- ✅ Adaptive Rate Limiting (自适应限流)

#### 计划实现 📋
- 📋 Distributed Rate Limiting (分布式限流)
- 📋 User-based Rate Limiting (用户级限流)
- 📋 API Key 级别的限流

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

### 当前架构 (v2.6.x)
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
| 配置管理 | v2.8.x | 📋 规划中 |
| 可维护性提升 | v2.9.x | 📋 规划中 |
| 微服务探索 | v3.0 | ⏸️ 推迟 |

### 技术栈

#### 核心技术
- **后端**: Spring Boot 3.5.5 + WebFlux (响应式)
- **前端**: Vue 3 + TypeScript + Element Plus
- **数据库**: H2 (嵌入式) + R2DBC
- **缓存**: Redis (可选)
- **监控**: Prometheus + Grafana
- **追踪**: OpenTelemetry + Zipkin

#### 数据存储
- **默认**: H2 嵌入式数据库
- **生产**: PostgreSQL / MySQL
- **缓存**: Redis (可选)

## 性能目标

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
11. 📋 v3.2.2+ 候选：脱敏规则持久化版本化、配额 HOUR/MONTH 限额、记录级 FULL 加密策略 UI、试脱敏回归集
12. 📋 语义缓存评估（向量相似度复用，独立项目）；高可用地基（多节点/Redis）在 v3.2.x 后再评估

### 长期愿景
1. 成为 AI 模型路由领域的标准
2. 建立完整的生态系统
3. 实现企业级商业化成功
4. 推动行业技术发展

---

**更新时间**: 2026年9月19日

如有任何建议或想法，欢迎通过 [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions) 与我们交流。

# 能力清单

这张表回答三个问题：**能做什么、不做什​么、边界在哪**。每一行的配置键都能在仓库里找到对应实现，没有「规划中」的条目。

> 本页描述的是当前版本的实现状态。能力开关默认值见各行的「默认」列。

## 请求入口

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| OpenAI 兼容 Chat Completions | 可用 | 开 | `model.services.chat`（`config/router/services.yml`） | 支持流式与非流式 |
| OpenAI 兼容 Embeddings | 可用 | 开 | `model.services.embedding` | — |
| Rerank | 可用 | 开 | `model.services.rerank` | — |
| 语音合成 / 语音识别 | 可用 | 开 | `model.services.tts` / `model.services.stt` | 走 `/v1/audio/*` |
| Anthropic Messages 协议 | 可用 | 开 | 由入口路径自动识别 | 与 OpenAI 面并行，客户端各用各的协议 |
| 原生面透传（`/v1/**`） | 可用 | 开 | — | 返回原生 JSON，不包 `RouterResponse` |
| 控制台面（`/api/**`、`/admin/**`） | 可用 | 开 | — | 返回统一 `RouterResponse` 包裹体 |

## 路由与容错

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| 多实例负载均衡 | 可用 | 开 | `model.services.<type>.instances` | 8 种策略：轮询 / 随机 / 最少连接 / 一致性哈希 / IP 哈希 / 延迟感知 / 会话粘滞 |
| 同名实例聚合成候选集 | 可用 | 开 | 实例的 `name` | 请求里的 `model` 字段按实例名匹配；同名即「同一模型的多个副本」 |
| 熔断 | 可用 | 开 | `model.circuit-breaker.*` | 状态持久化在库里，重启不清；有半开恢复 |
| 失败重试 | 可用 | 开 | `model.services.<type>.retry` | — |
| 故障转移 | 可用 | 开 | `model.fallback.*` | — |
| 健康检查 | 可用 | 开 | `model.health-check.*` | 只探端口时可能「健康但不可用」，见下方「已知边界」 |

## 限流与配额

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| 全局限流 | 可用 | 开 | `model.rate-limit` | 三层限流里最外层 |
| 服务级限流 | 可用 | 开 | `model.services.<type>.rate-limit` | — |
| 实例级限流 | 可用 | 开 | `model.services.<type>.instances[i].rate-limit` | **不能用环境变量逐项覆盖**（会让整个列表被替换，见 `configuration/index.md`） |
| API Key 配额 | 可用 | 开 | `jairouter.quota.*` | 支持日 / 周 / 月窗口与 token 维度 |
| 配额超限响应 | 可用 | — | — | 返回 **429** 不是 401（客户端可据此区分「额度用完」与「认证失败」） |

## 缓存

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| 响应缓存（精确） | 可用 | 按配置 | `jairouter.cache.response.*` | 按租户维度隔离 |
| 响应缓存（语义近似） | 可用 | **关** | `jairouter.semantic-cache.*` | SimHash 近似复用；分 L2 租户内 / L3 公共池两层，L3 需过七条准入 |
| 流式响应缓存 | 可用 | 跟随响应缓存 | 同上 | 命中时按 SSE 帧回放 |
| 含 PII 的响应不缓存 | 可用 | 开 | — | 三个来源任一命中即跳过（请求替换数 / 响应还原数 / 独立探测器） |

## 数据保护

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| 响应脱敏（单向） | 可用 | prod 开 | `sanitization.response.*` | 规则可配，脱敏后不回填 |
| 可逆脱敏（PII Vault） | 可用 | **关** | `jairouter.pii.reversible.*` | 请求侧换占位符、响应侧还原；流式逐块还原，被切开的占位符那一段还原不到 |
| 字段级脱敏范围 | 可用 | 全文本 | `jairouter.pii.reversible.json-paths` | 支持 `$.a.b` 与 `$.a[*].b`；配错路径会回退全文本（不会漏脱敏） |
| 脱敏访问审计 | 可用 | 开（随可逆脱敏） | — | 只记元数据，不记原值 |
| 流式响应护栏 | 可用 | **关** | `jairouter.guard.*` | 正则命中可 REDACT 或 ABORT；不跨 chunk 拼接，敏感串被切开时检测不到 |
| SSRF 防护 | 可用 | 开 | — | 出站地址校验，拒绝内网/保留地址 |

## 可观测性

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| Prometheus 指标 | 可用 | 开 | `management.endpoints.web.exposure.include` | prod 只暴露 `health,info,prometheus` |
| 调用历史 | 可用 | 开 | `jairouter.monitoring.call-history.*` | 有清理任务（02:00） |
| 用量记录 | 可用 | 开 | `jairouter.monitoring.token-usage.*` | 有清理任务（默认 90 天，03:00） |
| 审计日志 | 可用 | 开 | `jairouter.security.audit.*` | 事件类型走枚举，未知类型会被拒绝 |
| 链路追踪 | 可用 | 按配置 | `jairouter.tracing.*` | 支持 OTLP / Zipkin / 日志导出 |
| 容量类指标 | 可用 | 开 | — | SSE 连接数、出站连接池在途、响应缓存条目数、事件循环延迟、WebSocket 会话数 |
| 控制台 | 可用 | 开 | — | Vue 3；概览 / 路由 / 缓存 / 监控 / 配置页 |

## 部署与运维

| 能力 | 状态 | 默认 | 配置位置 | 边界 |
|---|---|---|---|---|
| 单 jar 启动 | 可用 | — | — | `java -jar model-router.jar` |
| Docker 镜像 | 可用 | — | `Dockerfile` / `Dockerfile.optimized` / `Dockerfile.distroless` / `Dockerfile.jlink` | 三个变体用 `jarmode=tools` 分层 |
| Kubernetes | 可用 | — | `docs/*/deployment/kubernetes.md` | 提供 Deployment / Service / ConfigMap 示例 |
| 数据库 | 可用 | H2 | `spring.datasource.*` | 支持 H2 与 PostgreSQL；R2DBC 已在 v3.3.6 移除 |
| 多副本共享状态 | 可用 | 关 | `jairouter.security.*.redis.enabled`（三个开关） | 未开启且检测到多副本时启动期告警；语义缓存池仍为进程内（见已知边界） |
| 配置热更新 | **部分** | — | — | 配额 / 监控只改内存（重启回退）；脱敏与熔断落库；实例配置落库但同步会删 YAML 中不存在的实例 |

## 本网关不做什么

| 不做 | 为什么 | 交给谁 |
|---|---|---|
| 模型推理 | 网关是转发层，不做算力 | vLLM / Ollama / 云厂商 API |
| 计费与结算 | 计费涉及账单主体、税率、对账，是另一套系统 | 自有计费平台 |
| Prompt 管理与版本控制 | 属于应用层职责，放进网关会让网关变成业务系统 | 应用侧 / Prompt 平台 |
| RAG 与向量库 | 网关只转发 embedding 请求，不存向量 | 向量数据库 |
| 训练与微调 | 与网关职责无关 | 训练平台 |
| 前端应用脚手架 | 控制台是管理界面，不是终端用户产品 | 业务应用 |

## 企业级能力：当前是否在开源版

这一节的作用是**把现状摆清楚**，供商业化划线时参考。

| 能力 | 当前在开源版 | 说明 |
|---|---|---|
| 多租户隔离（API Key 维度） | **是** | 配额、缓存、审计都按 API Key 隔离 |
| 租户内二级隔离（`user` 字段） | **是** | 语义缓存 L2 用；`user` 由客户端自报，只用于收窄不用于鉴权 |
| 审计留痕 | **是** | 安全审计 + 脱敏访问审计 + 跨租户缓存命中审计 |
| 合规脱敏（可逆） | **是** | PII Vault + 字段级策略 |
| 配额与限流 | **是** | 三层限流 + 多窗口配额 |
| 可观测性 | **是** | Prometheus + 追踪 + 控制台 |
| 多副本共享状态 | **是**（需开开关） | 三个 Redis 开关；语义缓存池仍进程内 |
| 细粒度 RBAC | **是** | 53 个权限码 + URL 规则 |
| SSO / OIDC | **否** | 当前只有用户名密码 + JWT |
| 高可用部署编排 | **否** | 只提供 K8s 示例，不含运维编排 |
| SLA 保障与技术支持 | **否** | 开源项目不承诺 SLA |

结论很直接：**除 SSO 与运维编排外，企业级能力目前都在开源版里**。要让「商业化路径」有落点，需要在这里划一条线 —— 这是产品决策，不是技术决策。

## 已知边界

这些是实现上刻意接受的取舍，不是缺陷：

1. **健康检查只探端口时不代表可用** —— `check_type: socket_connect` 只能证明端口开着；上游返回错误仍可能被判定为健康。
2. **语义缓存 L2/L3 池是进程内的** —— 多副本时命中率按副本数摊薄，且没有信号说明原因（issue #1002）。
3. **流式护栏不跨 chunk 拼接** —— 敏感串被切开在两个 chunk 之间时检测不到。
4. **可逆脱敏在流式下逐块还原** —— 占位符被切开时那一段还原不到，客户端会看到占位符原文（真实值没有出境）。
5. **流式路径没做 PII 探测** —— 非流式会跳过含 PII 的响应不写缓存，流式这条路径尚未覆盖。
6. **配额热改不持久化** —— 控制台改了重启回到配置文件的值（只有 `QuotaConfigController` 把这一边界写进了接口：重启级字段返回 400 + `RESTART_REQUIRED`）。

## 相关文档

- [运行时可变性](../configuration/dynamic-config.md) —— 改了什么、什么时候生效
- [可逆脱敏](../security/data-sanitization.md) —— 配置与边界
- [语义缓存](../configuration/response-cache.md) —— 三层 scope 与准入
- [性能与容量](../troubleshooting/performance.md) —— 实测基线

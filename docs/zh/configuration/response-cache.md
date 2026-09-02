# 响应缓存

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-09-03
> **Git 提交**: -
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 从 **v2.9.9** 起提供**响应缓存（response cache）**：将下游 LLM 服务的完整响应按缓存键（cache key）缓存，相同请求再次到达时直接复用缓存结果并**跳过下游调用**，显著降低延迟与成本。

- **默认全关（opt-in）**：`jairouter.response-cache` 默认不启用；开启后不影响任何未命中请求的行为
- **与 v2.9.0 前缀缓存（prefix cache / KV cache）可叠加**：两者定位不同——
  - 前缀缓存：仍调用下游，但复用其 prefill 计算，降低首 token 延迟（TTFT）与 prefill 开销
  - 响应缓存：字节级相同的请求**完全跳过下游**，直接返回缓存内容
- 命中不消耗下游 token 配额；未命中 / cacheSalt 绕过时行为与缓存关闭时完全一致

## 快速启用

```yaml
jairouter:
  response-cache:
    enabled: true        # 是否启用响应缓存（默认 false，opt-in）
    ttl: 1h              # 缓存有效期（默认 1h，对齐 liteLLM）
    max-size: 10000      # 最大缓存条目数（默认 10000）
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enabled` | `false` | 总开关；关闭时缓存读写全部短路，零额外开销 |
| `ttl` | `1h` | 缓存条目有效期（对齐 liteLLM 默认） |
| `max-size` | `10000` | 最大缓存条目数，超出后由 Caffeine 按容量淘汰策略自动淘汰 |
| `only-deterministic` | `true` | 仅缓存确定性请求（见下节） |
| `skip-streaming` | `true` | 跳过流式请求（P0 仅支持非流式缓存） |

开启即生效，无需重启或额外配置；缓存为**纯增量能力**，未命中请求的转发行为与关闭时一致。

## 适用请求（P0）

P0 仅缓存**非流式**确定性请求：

| 服务 | 可缓存条件 | 说明 |
|------|-----------|------|
| `chat`（非流式） | `temperature` 为 `0` 或 `null` **且** `n` 为 `1` 或 `null` | 采样确定性、结果可复现；其余参数（messages 等）全部进入缓存键 |
| `embedding` | 天然确定性 | 直接可缓存 |
| `rerank` | 天然确定性 | 直接可缓存 |
| `chat`（流式） | 默认不缓存 | `skip-streaming: true`（默认）时跳过；流式缓存规划在后续版本 |
| `image` / `TTS` / `STT` | 不缓存 | 二进制 / 非 P0 服务，不参与缓存 |

- `only-deterministic: true`（默认）时，`temperature > 0` 或 `n > 1` 的 chat 请求不缓存（每次生成结果不同，缓存无意义）
- `skip-streaming: true`（默认）时，流式请求不生成缓存键、不读写缓存

## 缓存键与租户隔离

缓存键是规范化请求的 SHA-256 摘要，**键内不含任何明文内容**：

```
cache key = SHA-256(tenantKey | [user?] | serviceType | model | canonicalJson(requestBody))
```

- **租户隔离（tenant isolation）**：`tenantKey` = API Key ID（`apiKeyId`），缺省（无 API Key）时回退客户端 IP（`clientIp`）。不同租户的相同请求键不同、互不共享缓存，防止跨租户数据泄漏
- **user 可选入键**：chat / embedding 请求携带可选 `user` 时，非空则作为键段参与计算；为空时缓存退化为 API Key（租户）粒度
- **规范化请求体**：字段固定顺序序列化、字符串去空白、嵌套对象字典序、列表保序——键序或空白差异不会产生缓存碎片
- **cacheSalt 绕过位**：chat / embedding 请求在 `options.cacheSalt` 传入非空值时，本次请求**既不读也不写**缓存，直接走下游（用于需要强制新鲜结果的场景）
- **会话语义**：网关无 sessionId 概念，会话历史由客户端全量携带在 `messages` 中——`messages` 完整参与键计算，天然区分不同上下文：
  - 同一会话重复请求（消息完全一致）→ 命中，符合预期
  - 不同会话仅在请求字节级完全相同时命中，确定性请求下不会产生上下文污染
- **通用问候无需特殊处理**：单轮高频问候（你好 / 你能干什么）天然高命中；陈旧由 TTL 与 cacheSalt 控制；语义级复用（你好 vs 嗨 的归一）属后续语义缓存评估范围

## 命中语义

- **响应结构一致**：命中返回与正常非流式成功响应同构的 `RouterResponse`（`data` 为缓存的下游原始数据），调用方无感
- **命中不产生调用历史**：缓存命中的请求在适配器执行前短路返回（调用历史在下游执行路径记录），因此**不写入调用历史**，仅累加命中指标；需审计命中请求时以 `jairouter_response_cache_hits_total` 为准
- **不消耗下游 token 配额**：命中请求完全跳过下游调用，不产生新的下游用量
- **限流语义**：缓存读在**服务级限流之后**执行——命中请求不绕过限流（限流在实例选择阶段已执行）
- **指标**（标签：`service`、`model`）：

| 指标 | 类型 | 说明 |
|------|------|------|
| `jairouter_response_cache_hits_total` | Counter | 缓存命中（cache hit）累计次数 |
| `jairouter_response_cache_misses_total` | Counter | 缓存未命中累计次数 |
| `jairouter_response_cache_hit_ratio` | Gauge | 命中率 0.0~1.0（累积 hit/(hit+miss)） |

## 限制与后续

P0 范围边界：

- **流式**：暂不缓存流式响应（默认 `skip-streaming: true`）；流式缓存规划在后续版本（P1 / v2.9.10）
- **分布式**：当前为进程内 Caffeine 缓存，多实例各自独立；共享 / Redis 缓存后续版本支持
- **语义缓存**：仅做请求级字节级匹配，不做语义级复用（你好 vs 嗨）；语义缓存属后续评估
- **失效机制**：暂无显式失效 API，陈旧内容由 TTL（+ cacheSalt 绕过）控制；失效 API 规划在后续版本
- 二进制服务（image / TTS / STT）与流式请求不参与缓存

## 相关文档

- [更新日志](../reference/changelog.md)
- [KV缓存](./kv-cache.md)
- [限流配置](./rate-limiting.md)
- [监控指标参考](../monitoring/metrics.md)

# KV 缓存配置（前缀缓存友好网关）

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2026-08-28
> **Git 提交**: 5b592476  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter 从 **v2.9.0** 起提供 **LLM KV 缓存增强**：自动租户粘性路由 + 缓存命中指标 + 缓存参数透传，最大化 LLM 前缀缓存（prefix cache / KV cache）利用率。本网关面向**长文档、多轮对话**场景——同一租户的请求被稳定路由到同一实例，使下游服务的前缀缓存持续命中，显著降低首 token 延迟（TTFT）与 prefill 开销。

> **说明**：这是**前缀缓存友好**网关，不是响应缓存。它不做内容缓存，而是通过"路由 + 观测 + 透传"三件事让下游 LLM 服务自身的缓存生效。

## 一、自动租户粘性路由（v2.9.0）

### 工作原理

请求进入后，网关按亲和键对实例做**一致性哈希**（150 虚拟节点），将同一租户的请求稳定映射到同一实例：

- **亲和键** = `apiKeyId | serviceType | modelName`（无 API Key 时回退 `clientIp`）
- 目标实例健康/熔断不可用时**自动回退**到普通负载均衡策略，不阻塞请求
- **默认开启**：实例数 > 1 且健康时自动生效，零配置

### 配置

粘性路由按服务配置（`model.services.<serviceKey>.sticky`）：

```yaml
model:
  services:
    deepseek:
      adapter: deepseek
      sticky:
        enabled: true            # 是否启用粘性路由（默认 true；实例数 > 1 时生效）
        affinityKeyScope: tenant_model  # 粘性粒度：tenant_model（默认）/ tenant
```

- `sticky.enabled`：是否启用粘性路由（默认 `true`；仅实例数 > 1 且健康时生效）
- `sticky.affinityKeyScope`：
  - `tenant_model`（默认）：亲和键含 `modelName` —— 同一租户的不同模型可路由到不同实例
  - `tenant`：亲和键为 `apiKeyId|serviceType`（无 apiKey 回退 clientIp）—— 同一租户全部钉在同一实例

> 云 API（DeepSeek/OpenAI 等官方接口）由服务商侧自动做前缀缓存，无需网关粘性路由；**粘性路由主要面向自建 vLLM/GPUStack/Ollama 等多实例集群**。

## 二、缓存命中指标（v2.9.0）

网关解析下游响应 usage 中的缓存 token（兼容两种形态），并暴露 Prometheus 指标：

| 形态 | usage 字段 |
|------|------------|
| DeepSeek | `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` |
| OpenAI / vLLM | `prompt_tokens_details.cached_tokens` |

| 指标 | 类型 | 标签 | 说明 |
|------|------|------|------|
| `jairouter_cache_hit_tokens_total` | Counter | `adapter`, `instance` | 缓存命中 token 累计数 |
| `jairouter_cache_miss_tokens_total` | Counter | `adapter`, `instance` | 缓存未命中 token 累计数 |
| `jairouter_cache_hit_ratio` | Gauge | `adapter`, `instance` | 命中率 0.0~1.0，基于累计计数 hit/(hit+miss) |

- 字段缺失按 0 容忍，不报错
- `cache_hit_ratio` 为**累积语义**（hitTotal/(hitTotal+missTotal)），非单次请求比率
- Grafana/Prometheus 可直接按 `adapter` / `instance` 聚合观察缓存收益；流式响应同样解析（usage 出现在流结束块时）

## 三、缓存参数透传（v2.9.0）

`ChatDTO.Options` 新增两个透传字段，经 `extra_body` 白名单放行给下游（vLLM 前缀缓存参数）：

```json
{
  "model": "my-model",
  "options": {
    "prefixCacheHash": "abc123",
    "enablePrefixCaching": true
  }
}
```

- `prefixCacheHash` → 下游 `extra_body.prefix_cache_hash`
- `enablePrefixCaching` → 下游 `extra_body.enable_prefix_caching`

## 四、验证

- **粘性路由**：同一 `apiKeyId` 连续请求，追踪/日志中目标 `instance` 应保持一致（实例健康时）
- **指标**：`curl http://<host>:8080/actuator/prometheus | grep jairouter_cache`（或 Grafana 查询）
- **前缀卫生**：消息数组逐字透传（4 消息 + 6 消息两组回归测试），缓存 token 解析 5 组容错测试

## 五、已知限制

- **Anthropic `cache_control` 结构性限制**：当前 `ChatDTO.Message` 无 content blocks 结构，Anthropic 前缀缓存参数暂无法透传（如需支持请后续版本增加 contentBlocks 变体）
- 粘性路由仅影响**出站实例选择**，不保证下游多副本共享缓存
- 缓存指标仅统计显式返回缓存 token 字段的下游（DeepSeek/OpenAI/vLLM 兼容形态）

## 相关文档

- [更新日志](../reference/changelog.md)
- [负载均衡](./load-balancing.md)
- [路由规则](./routing-rules.md)

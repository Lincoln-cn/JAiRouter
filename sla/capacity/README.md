# 压测资产（#944）

两个零依赖的 Node 脚本，用于测量**网关自身**的吞吐与延迟，而不是把上游模型的延迟混进结果。

| 脚本 | 用途 |
| --- | --- |
| `mock-upstream.mjs` | OpenAI 兼容的最小上游：`/v1/models` 探活、非流式 JSON、流式 SSE |
| `loadtest.mjs` | 固定并发 + 固定时长的压测器：RPS、p50/p95/p99、失败样例 |

## 三步跑法

```bash
# 1) 上游 mock（127.0.0.1:18080，20ms 延迟；MOCK_DELAY_MS / MOCK_CHUNK_DELAY_MS 可调）
node scripts/perf/mock-upstream.mjs

# 2) 网关（dev profile + 独立库 + 三层限流关闭）
DATABASE_URL='jdbc:h2:file:./data/perf;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE' \
MODEL_UPSTREAM_BASE_URL=http://127.0.0.1:18080 \
MODEL_RATELIMIT_ENABLED=false \
MODEL_SERVICES_CHAT_RATELIMIT_ENABLED=false \
MODEL_SERVICES_CHAT_INSTANCES_0_RATELIMIT_ENABLED=false \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3) 压测
node scripts/perf/loadtest.mjs --url http://127.0.0.1:8080/v1/chat/completions \
  --api-key sk-test-api-key-12345678 --model "qwen3:4b" --concurrency 50 --duration 20
```

`--model` 必须是 `config/router/services.yml` 里存在的实例名（dev 下是 `qwen3:4b`）；否则网关返回
`404 No instances found for model ...`。

## 结果怎么读

- 吞吐不随并发上升、延迟随并发线性上涨 ⇒ 存在串行瓶颈，去看 `/actuator/prometheus` 的
  `hikaricp_connections_pending` 与 `jairouter_outbound_requests_inflight`。
- 基线数字、环境说明与已知限制写在 `docs/{zh,en}/troubleshooting/performance.md` 的
  「本仓的测量方法」一节，改脚本后请同步更新那一节。

## 已知限制

- 压测器与网关同机运行会互相抢 CPU，绝对值偏保守；
- 流式（SSE）场景尚未跑通，原因见文档的「已知限制」。

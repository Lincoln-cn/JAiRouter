# 版本漂移：工具与网关如何不脱节

## 问题

`sla/` 不在仓库里，所以：

- 没有任何 CI job 会跑它；
- 网关改了 API，工具不会自动知道；
- 时间一长，工具报的「异常」可能只是它自己过时了。

这类失败最坏的地方是**它看起来像产品故障**。运维看到「检查失败」，第一反应是去查服务，
而真正该做的是升级工具 —— 一次事故的处理时间就这么浪费掉了。

## 三道防线

### 一、版本锚（最轻）

`sla/contract.json`：

```json
{
  "toolingVersion": "0.1.0",
  "targetRange": ">=3.7.0 <4.0.0"
}
```

`preflight.mjs` 拿实际版本与 `targetRange` 比对，超范围直接判失败并明确输出：

```
[FAIL] 版本不兼容           当前 3.9.0，本工具适用 >=3.7.0 <4.0.0
       └ 这是工具与网关版本不匹配，不是服务故障；请升级 sla/ 工具链。
```

版本号从哪里来：优先 `--version` 显式传入（部署脚本注入），
其次尝试从响应头读取。**不要从 `/actuator/info` 读** —— 那里面的
`config.serviceVersion` 是 tracing 配置的版本（当前恒为 `1.0.0`），不是应用版本。

### 二、契约测试（权威，唯一自动的）

仓库侧 `src/test/java/org/unreal/modelrouter/AdminApiSurfaceContractTest.java`，
对 SLA 依赖的接口面做**快照断言**：

- `/api/auth/jwt/login` 返回 `data.token`
- `/api/models/stats` 返回 `data.totalInstances`
- `/api/config/instance/{type}` 返回 `data`
- `/api/auth/api-keys` 返回 `data`
- `/actuator/prometheus` 以 `text/plain` 返回

任何一个字段被改名、被删掉、层级被调整，测试就红，PR 合不进来。

**命名是刻意的**：不叫 `SlaContractTest`，而叫 `AdminApiSurfaceContractTest`。
它守的是「管理端 API 是控制台与外部自动化工具的公共契约」—— 这个理由本身成立，
不需要在开源仓库里暴露商业版的存在。

### 三、运行时自检（兜底）

`preflight.mjs` 逐个调用并确认字段还在。它在**客户现场**跑，所以能发现
仓库测试覆盖不到的情况（例如客户的网关版本落后、中间层改写了响应）。

**每个工具启动时都该先跑它**。失败的语义必须区分清楚：

| 现象 | 结论 |
| --- | --- |
| `preflight` 失败 | 工具与网关不兼容，升级工具 |
| `preflight` 通过、`doctor` 失败 | 服务真有问题，进入事故处理 |

## 清单同步的 SOP

网关侧发生变更时：

1. `AdminApiSurfaceContractTest` 红 —— 这是设计意图，让人**必须**面对这件事；
2. 确认变更是有意的，更新快照，并在 PR 描述里写「管理端 API 形状变更」；
3. 同步更新 `sla/contract.json`（字段、`targetRange`）与 `toolingVersion`；
4. 用新网关跑一次 `preflight.mjs`，两侧对齐；
5. 对外发布说明里写清「控制台之外的自动化集成受影响」。

## 发版检查

`scripts/release/release.py check` 在检测到 `sla/contract.json` 存在时，会比较
`targetRange` 与新版本号：覆盖不到就提醒「本次发版超出 SLA 工具链的适用范围」。
这是一条**软检查**（不阻断发布），因为它只对维护者有意义，与开源用户无关。

## 为什么不做「自动同步」

可以写脚本让 CI 在 API 变更时自动改 `contract.json`，但**不应该**：
自动改会让「工具依赖被打破」变成静默发生的事。契约测试红一下、让人停下来确认，
是有意保留的摩擦。

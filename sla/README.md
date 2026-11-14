# SLA 工具链

面向 JAiRouter 托管服务（SLA 交付）的专用工具与脚本。**不进开源仓库** ——
`sla/` 在 `.gitignore` 里，随商业版分发，或提供给授权客户。

开源仓库里保留的是网关本体；这里放的是「怎么把网关运营成一项有承诺的服务」所需的东西：
巡检、诊断、压测、调优、报表。两者分开的理由不是保密，而是**生命周期不同** ——
网关按发布节奏走，工具按客户环境的实际情况走。

## 目录

```
sla/
├── contract.json        工具链依赖的网关契约面 + 适用版本范围
├── preflight.mjs        契约自检 CLI（任何工具跑之前先跑它）
├── lib/
│   ├── client.mjs       登录 / API 调用 / 指标抓取（所有工具共用）
│   └── preflight.mjs    契约自检的实现
├── probes/              一、测得到 —— 可用性观测
│   └── synthetic.mjs    四条合成探针（登录 / 实例配置 / chat / chat 流式）
├── diagnose/            二、查得清 —— 现场诊断
│   ├── doctor.mjs       单点诊断器（分层给出在哪一层失败）
│   └── bundle.mjs       现场快照包（一次抓齐可分析的证据 + summary.md）
├── capacity/            三、扛得住 —— 容量与性能
│   └── loadtest.mjs     压测器（API Key / JWT / 自定义头三种认证）
├── verify/              升级后回归 —— 修过的行为有没有回来
│   └── post-upgrade.mjs 7 项检查，写操作需 --allow-writes 且自动恢复
├── mockupstream/        桩上游 —— 复现异常 / 负载均衡 / 熔断
│   └── server.mjs       7 类端点 + 故障注入 + /__mock/* 控制面
├── scenario/            场景编排（备份 → 改造 → 测试 → 恢复）
│   ├── load-balancing.mjs
│   └── strategy-matrix.py
├── evidence/            四、说得明 —— SLA 证据与报表
├── runbooks/            值班手册
└── docs/                设计文档
```

## 四段闭环

SLA 工具链不是「一堆脚本」，而是一条闭环。缺任何一段，承诺都落不了地：

| 段 | 回答的问题 | 工具 | 状态 |
| --- | --- | --- | --- |
| **测得到** | 此刻这个客户的环境能不能用？ | 合成事务、定时巡检、依赖面分项探测 | 待建 |
| **查得清** | 不能用的时候，坏在哪一层？ | doctor、现场快照包、requestId 追踪 | doctor 已有 |
| **扛得住** | 还能撑多少？什么时候要扩容？ | 压测器、容量水位计算器、长稳测试 | 压测器已有 |
| **说得明** | 上个月我们真的做到了吗？ | 可用性报表、事件时间线、审计导出 | 待建 |

顺序不是随意的：**没有「测得到」，后面三段都没有输入**。

## 工具与网关的版本漂移（重要）

`sla/` 不在仓库里，所以**没有任何 CI job 能跑它**。网关的 API 一变，工具就静默失效 ——
最坏的结果是工具报「服务异常」，而实际是工具自己过时了。

这条缝隙用两道守卫补上：

### 守卫一：仓库侧契约测试（权威）

`src/test/java/org/unreal/modelrouter/SlaSurfaceContractTest.java` 对 SLA 依赖的接口面做
**快照断言**。任何一个字段被改名、被删掉、层级被调整，这个测试就红，PR 合不进来。

这是唯一能自动守住的部分，所以它必须放在仓库里 —— **不依赖 sla/ 的存在**。
它守的是「公开 API 的形状」，而不是「SLA 的私有实现」，所以放进开源仓库是合理的。

### 守卫二：工具侧运行时自检（兜底）

`sla/preflight.mjs` + `contract.json`。每个工具启动时先跑一遍：

```bash
node sla/preflight.mjs --base http://gw:8080 --user admin --pass *** --version 3.7.3
```

它做三件事：

1. **版本范围**：`contract.json` 的 `targetRange`（如 `>=3.7.0 <4.0.0`）与实际版本比对；
2. **端点形状**：逐个调用并确认依赖的字段还在（`data.token`、`data.totalInstances` …）；
3. **指标存在**：确认 `jairouter_*` 指标还在被导出。

失败时的输出**必须**是「工具与网关版本不兼容，请升级工具链」，
而**不能**是「服务检查失败」—— 把工具过时报成产品故障，会白白消耗一次事故的处理时间。

### 清单怎么保持同步

`contract.json` 与仓库侧测试断言**同一份清单**。改动流程：

1. 网关侧改 API，`SlaSurfaceContractTest` 红；
2. 开发者确认这是有意变更后更新快照，并在 PR 描述里写「SLA 面临变更」；
3. 同步更新 `sla/contract.json`（字段、`targetRange`）与 `toolingVersion`；
4. 用新网关跑一次 `preflight.mjs` 验证两侧对齐。

发版时 `scripts/release/release.py check` 会检查 `sla/` 是否存在、`targetRange` 是否还覆盖
新版本号 —— 覆盖不到就提醒「本次发版超出 SLA 工具链的适用范围」。

## 在客户环境里怎么跑（三种方式）

工具本身零第三方依赖，但需要一个 JavaScript 运行时。客户环境什么样都有，所以给三层，
**按可用性从高到低选，结果等价**：

### 方式一：宿主机有 Node ≥ 18

最直接，什么都不用准备：

```bash
node sla/preflight.mjs --base http://gw:8080 --user admin --pass *** --version 3.8.1
```

### 方式二：没有 Node，但有 Docker

**在线**（能拉公共镜像）—— 不安装任何东西，挂载目录跑一次性容器：

```bash
docker run --rm --network host -v "$PWD/sla:/sla" -w /sla node:22-alpine \
  node preflight.mjs --base http://127.0.0.1:8080 --user admin --pass *** --version 3.8.1
```

`--network host` 是为了让容器里的 `127.0.0.1` 指向宿主机 —— 否则 `localhost` 是容器自己，
会得到「连不上」的假结论。

**离线**（客户网络出不去）—— 在我方构建一个专用镜像，导出成 tar 带过去：

```bash
./sla/build-image.sh --save          # 产出 jairouter-sla-<version>.tar.gz
# 客户机上：
docker load -i jairouter-sla-0.1.0.tar.gz
docker run --rm --network host jairouter-sla:0.1.0 preflight.mjs --base http://127.0.0.1:8080 --user admin --pass *** --version 3.8.1
```

镜像基于 `node:22-alpine`，`docker images` 显示约 239 MB，但 **`docker save` 出来的包只有
约 59 MB**（gzip 后），因为 node 二进制本身已压缩过。工具全程只读、以 `node` 用户运行
（不需要 root，也不需要 `npm install`）。

## 日志从哪里读（现场快照包）

`diagnose/bundle.mjs` 需要读日志来做 ERROR/WARN 摘要，但客户现场的部署形态差异很大，
所以按「显式指定 → 自动探测」的顺序尝试，**读不到不会让整份快照失败**，只在 `summary.md`
里列出每个来源的失败原因：

| 顺序 | 参数 | 适用场景 |
| --- | --- | --- |
| 1 | `--logs-file /var/log/jairouter/jairouter-all.log` | 非容器部署，或日志已落到文件 |
| 2 | `--journal-unit jairouter` | systemd 管理的裸跑 jar |
| 3 | `--container <名字>`（默认 `jairouter`） | 容器部署 |
| 4 | 自动兜底 `journalctl -u jairouter.service` | 上面都没配时试一次 |

```bash
# 容器内没有 docker CLI 时，把宿主机的日志目录挂进来再指定文件
docker run --rm --network host \
  -v /opt/sla:/sla:ro -v /opt/bundle:/out -v /opt/jairouter/logs:/logs:ro \
  -w /sla node:22-alpine \
  node diagnose/bundle.mjs --base http://127.0.0.1:8080 --user admin --pass *** \
    --out /out/run1 --logs-file /logs/jairouter-all.log
```

实测输出形如：

```
## 日志（文件 /logs/jairouter-all.log，最近 5225 行）
- ERROR 16 / WARN 4653 / 其它异常行 0
```

`WARN` 数量本身就是一个信号：上游不可达时每轮健康检查都会为每个实例写一条 WARN，
一次 20 分钟的故障即可累积数千行 —— 这类「日志风暴」会在真正的错误被滚动掉之前先把磁盘吃掉。

### 方式三：既没有 Node，也没有 Docker

`preflight-curl.sh` 是同一份契约检查的 **POSIX sh 实现**，只依赖 `curl` 与
`sed`/`grep`/`sort`（**不依赖 jq**）。Linux 发行版默认就有这些：

```bash
sh sla/preflight-curl.sh --base http://gw:8080 --user admin --pass *** --version 3.8.1
```

退出码同 `preflight.mjs`：`0` 全通过、`2` 有失败。它比 `.mjs` 版多一条 `version.range`
（把 `contract.json` 的 `targetRange` 也一起判了），但**不覆盖** `doctor` / `loadtest`
的能力 —— 那两个仍需要 Node 或 Docker。

### 选哪一种

| 场景 | 选择 | 说明 |
| --- | --- | --- |
| 我方运维机 | 方式一 | 快，且能跑全部工具 |
| 客户给了一台能联网的机器 | 方式二（在线） | 不污染环境，不留残留 |
| 客户内网离线 | 方式二（离线镜像） | 只需带一个 59 MB 的 tar |
| 只有一台最小化系统 / 受限 Shell | 方式三 | 只做契约自检，够回答「接口形状还对吗」 |

三种方式的通过判据相同：**6 项契约端点全绿 + 版本落在 `targetRange` 内**。
任一失败时，输出会明确写在「哪一项、哪个字段、什么状态码」上 —— 这正是下面「失败要说清是哪一层」的要求。

## 升级后回归：verify（会写配置，需显式授权）

`preflight` 回答「契约还在不在」——只读、几秒；`verify` 回答「这次升级修过的那几个行为有没有真的回来」——它会写服务配置和临时 API Key，所以默认只跑只读项，写项必须显式加 `--allow-writes`。

```bash
node sla/verify/post-upgrade.mjs --base http://gw:8080 --user admin --pass '***' --expect 3.8.2 --allow-writes
```

| 检查项 | 判据 | 修复前的表现 |
|---|---|---|
| `version.build` | `/actuator/info` 有 `build.version` 且等于 `--expect` | 只有 `tracing.config.serviceVersion`（那是 tracing 的版本号），工具链无法判版本 |
| `heap.container-ratio` | `jvm_memory_max_bytes{area="heap"}` 最大值 ≥ `--min-heap-mb`（默认 520） | 镜像里 `-Xmx512m` 覆盖了 `-XX:MaxRAMPercentage`，容器加内存堆不涨 |
| `static.missing-asset-traceable` | 缺失的 `/admin/assets/*.js` 返回 404 **且** `text/plain` **且** 响应体含路径 | 404 空响应体、无 content-type、无日志，Micrometer 把 URI 归一成 `/admin/**`，查不到是哪个 chunk |
| `actuator.threaddump` | `/actuator/threaddump` 返回 200 并带线程列表 | prod 只暴露 health/info/prometheus，进程卡死时没有现场 |
| `config.incremental-put` | PUT 只传 `loadBalance` 后 `adapter` 仍然在 | 未提交字段被静默清空（`adapter` 变 null、`rateLimit` 重置） |
| `quota.enforced` | 新建 `dailyRequestLimit=2` 的 Key 连打 3 次出现 429 | 账本默认关闭，按 Key 的额度从不检查 |
| `lb.spread` | 30 次请求命中 ≥2 个实例 | 最少连接并列时恒选同一实例 |

判读要点：

- 写项（`config.incremental-put` / `quota.enforced`）结束后按登记的逆操作**逆序恢复**；恢复失败会单独列出并以退出码 2 结束，不会静默。
- `lb.spread` 在实例数 < 2 时是 SKIP。**即使实例数 ≥ 2 也可能恒不分散**——先看是不是会话粘性（issue #1095）：`sticky.enabled` 默认为 true，亲和键是 `apiKeyId|serviceType|modelName`，同一主体 + 同一 model 的请求会被固定到同一实例，与负载均衡策略无关。要观察真正的分流，需在 yml 里把 `model.services.<type>.sticky.enabled` 置 false 并重启。
- 退出码：0 全通过（SKIP 不算失败）、1 有 FAIL、2 恢复失败或未预期错误。

Python 版同参数：`python3 sla.py verify --base ... --user admin --pass '***' --allow-writes`。

## 压测与桩服务

### 桩上游（`mockupstream/`）

```bash
node sla/mockupstream/server.mjs --port 9100 --tag mock-a
```

- 覆盖 7 类端点：`/v1/chat/completions`（支持 `stream`）、`/v1/embeddings`、`/v1/rerank`、`/v1/audio/speech`、`/v1/audio/transcriptions`、`/v1/images/generations`、`/v1/images/edits`，外加 `/v1/models` 与 `/health`。
- `/health` **恒返回 200，不受故障注入影响**——否则分不清「桩挂了」和「我注入的故障」。
- 故障注入：`status`、`errorRate`、`latencyMs` / `jitterMs`、`hangMs`、`malformed`、`closeConnection`、`streamChunkDelayMs`、`onlyModels`、`instanceTag`。
- 控制面：`GET /__mock/state`、`POST /__mock/behavior`、`POST /__mock/reset`、`GET /__mock/stats`。响应头 `X-Mock-Instance`、响应体 `mock_instance` 标记来源实例，便于统计命中分布。

### 压测（`capacity/loadtest.mjs`）

```bash
node sla/capacity/loadtest.mjs --url http://gw:8080/v1/chat/completions --api-key <KEY> --concurrency 20 --duration 30
```

- 认证支持三种：`--api-key`（`X-API-Key`）、`--token`（`X-Jairouter-Token`，JWT）、可重复的 `--header 'X-Foo: bar'`。注意网关的 API Key 头是 **`X-API-Key`**，不是 `Authorization: Bearer`。
- 失败请求的耗时单独记在 `failedLatencyMs`；**全部失败时 `latencyMs` 的样本数是 0、分位为 null**，并明确输出「无成功样本」——不要把这个读成「延迟很好」。

### 场景矩阵（`scenario/`）

- `load-balancing.mjs`：5 种策略 × 3 实例的命中分布。
- `strategy-matrix.py`：负载均衡 / 限流（3 种算法）/ 熔断的编排，自带备份与 `finally` 恢复。
- 两者的前置都是「按实例名路由」——**必须有同名的多个实例**，请求里的 `model` 也要用这个名字。

## 配置体检：audit

前四段（preflight / doctor / loadtest / verify）回答的是「现在能不能用」。
`audit` 回答的是另一个问题：**当前这套配置跑起来是什么效果，要不要调**。

它只读三个配置接口（`/api/config/type`、`/api/config/quota`、`/api/config/ratelimit`），
不发起业务请求、不写任何东西，因此可以随时在客户现场跑。

```bash
run.sh audit --base http://127.0.0.1:8080 --user admin --pass '<密码>'
run.sh audit --base http://127.0.0.1:8080 --token '<JWT>'
```

每一项都以「当前值 → 怎么改」的形式给出，例如：

```
[WARN] 配置     chat：sticky.enabled=true、2 个实例、策略 least-connections →
               同一客户端固定同一实例，负载均衡不会分流
               └ 不需要会话亲和就关掉：PUT /api/config/type/services/chat {"sticky":{"enabled":false}}
```

| 检查面 | 项 | 触发条件 |
| --- | --- | --- |
| 服务配置 | 实例数 | 0 个实例 → FAIL（该服务请求一律 503） |
| 服务配置 | 粘性 | `sticky.enabled=true` 且实例数 > 1 → WARN（不会分流） |
| 服务配置 | 可用性 | 全部实例非 active → FAIL；active 但无一 HEALTHY → WARN（附首个错误原因） |
| 服务配置 | 假多实例 | 多个实例的 `baseUrl + path` 完全相同 → WARN |
| 服务配置 | 权重 | 存在 `weight <= 0` → WARN |
| 服务配置 | 负载均衡策略 | 多实例但未显式设 `loadBalance.type` → WARN |
| 服务配置 | 服务级限流 | `enabled=true` 但 `rate <= 0`；或 `capacity < rate` → WARN |
| 服务配置 | 分桶维度 | `keyDimension=client-ip` 但 `clientIpEnable!=true` → WARN（退化为 remoteAddress） |
| 配额 | 窗口集合 | `windows` 含 `MINUTE` → WARN（3.8.6 起账本不再判每分钟速率） |
| 配额 | 开关 | `enabled=false` → WARN（小时/天/月限额都不判） |
| 限流 | API Key 级 | `apiKey.enabled=false` → WARN；否则输出后端实现（redis / local） |
| 限流 | 分布式 | `distributed.enabled!=true` → WARN（多副本下放行量约为配置值 × 副本数） |
| 限流 | 终端用户级 | 输出开关状态与阈值 |

退出码与其它子命令一致：`0` 全通过、`1` 有 WARN、`2` 有 FAIL。

> `audit` 与 `doctor` 的分工：`doctor` 会连真实上游、看连通性与真实调用；
> `audit` 一行网络请求都不发上游，只看「配置组合本身是否自相矛盾」。

## 设计约束

- **只读**：所有工具不修改系统状态（不发写请求、不改配置、不触发复位）。工具自己不能成为故障源。
- **失败要说清是哪一层**：本轮三个真实缺陷分别落在配置层、解析层、状态层，
  单一的「服务异常」没有任何诊断价值。
- **宁可说「测不到」，不猜**：doctor 的 DNS 检查降级为 WARN 就是这个原则 ——
  在宿主机上测不到容器的网络，就如实说明，而不是把宿主机的结果当成网关的结论。
- **共享一个客户端**：登录、鉴权头、响应解包只在 `lib/client.mjs` 里写一次。
  网关改鉴权方式时，只改这一个文件。

## 相关

- 开源仓库：`https://github.com/Lincoln-cn/JAiRouter`
- 能力边界：`docs/{zh,en}/reference/capabilities.md`
- 多副本注意事项：`docs/{zh,en}/deployment/multi-replica.md`

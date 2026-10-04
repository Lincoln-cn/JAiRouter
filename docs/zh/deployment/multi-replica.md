# 多副本部署：跨副本共享态必开配置

单副本部署（仓库默认）无需阅读本文。本文面向 **JAiRouter 以 2 个及以上副本对外提供服务** 的部署，
说明哪些配置项在多副本下**必须开启**、开与不开的差别，以及启动自检如何帮你提前发现配置缺口。

## 为什么需要

认证、配额、状态持久化三条链路都保留了「本机优先」的默认实现。单副本下这是优点——零外部依赖即可运行；
多副本下则会让各副本退化成**互不知情的单机实例**：

| 现象 | 根因 |
|---|---|
| 在副本 A 吊销的令牌，请求落到副本 B 仍被放行 | JWT 黑名单各副本各自维护 |
| 新建或吊销的 API Key 要等副本重启才在其它副本生效 | API Key 缓存无 TTL，且不跨副本失效 |
| 实际放行量约为 `配置值 × 副本数` | 配额计数全部在 JVM 内 |
| 滚动重启后各副本状态不一致 | 状态持久化退坡到本机 H2/文件 |

这些都不是「性能问题」，而是**正确性问题**：令牌吊销失效属于安全缺口，配额放大属于计费与限流失真。

## 必开配置项

多副本部署需同时开启以下 5 项，并配好 Redis 连接：

| 配置项 | 默认 | 多副本应设为 | 不开的后果 |
|---|---|---|---|
| `jairouter.security.jwt.blacklist.redis.enabled` | `false` | `true` | 吊销的令牌在其它副本仍被放行 |
| `jairouter.security.jwt.persistence.redis.enabled` | `false` | `true` | 令牌状态各副本独立，重启可能丢失已签发状态 |
| `jairouter.security.cache.redis.enabled` | `false` | `true` | API Key 缓存各副本独立，吊销/新建不跨副本生效 |
| `jairouter.quota.distributed.enabled` | `false` | `true` | 配额计数每副本一份，放行量约为配置值 × 副本数 |
| `jairouter.persistence.redis.enabled` | `false` | `true` | 状态持久化退坡到本机 H2/文件，不做跨节点共享 |

开启配额分布式计数时还需 `jairouter.quota.enabled=true`（配额功能本身的总开关）。

Redis 连接信息统一由 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`（以及 `REDIS_DATABASE`）环境变量，
或 Spring 的 `spring.data.redis.*` 提供 —— 上面的开关只决定**用不用** Redis，不再各自读一份
`host` / `port`：`jairouter.security.jwt.persistence.redis.host` 这类键在取消 JWT 独立 Redis
之后已**不再被读取**（原先写在 `config/auth/jwt.yml`、`config/security/persistence-base.yml` 里的
那些键已随之删除）。

## 启动自检

应用启动就绪时会自动判定「是否多副本 + 共享态开关是否齐全」，无需额外配置即可生效：

```yaml
jairouter:
  cluster:
    # 0 = 未知，按运行环境探测；1 = 显式单副本；>1 = 多副本
    replicas: 3
    shared-state-check:
      enabled: true
      # OFF = 不检查；WARN = 告警（默认）；FAIL = 启动失败
      mode: WARN
      # WARN 模式下是否同时把 /actuator/health 置为 DOWN
      fail-health: false
```

**多副本的判定依据**：显式 `replicas > 1` 优先；未显式声明时，存在 `KUBERNETES_SERVICE_HOST`
（K8s 自动注入）或 `POD_NAME`（downward API）即判为多副本。

刻意**不**以 `HOSTNAME` 为据：普通 Docker 容器也会设置它，会把单机部署误判为多副本。
这也是单机部署**绝不会**被自检打扰的原因——共享态开关在单机下关闭本就是正常状态。

三种模式的行为：

- `OFF`：不检查，也不暴露健康详情。
- `WARN`（默认）：启动时给出一条告警，逐项列出未开启的开关及其后果。**不改变** `/actuator/health` 状态。
- `FAIL`：启动即失败（`IllegalStateException`），应用不会带病对外提供服务。

### 关于健康端点

`/actuator/health` 的 `sharedState` 组件会持续暴露自检详情（`multiReplica`、
`disabledSwitches`）。**默认该组件恒为 `UP`**，与 `RbacEndpointCoverageHealthIndicator`
的约定一致：配置缺口是「信息可见性」问题，不是「可用性故障」。

这一点在多副本下尤其重要：仓库全部 Dockerfile 与 compose 的健康检查都用 `/actuator/health`，
直接判为 `DOWN` 会让容器变 unhealthy，K8s 上还可能触发重启循环——而**重启并不能修复配置缺口**。
确实需要让编排层感知时，显式设 `fail-health: true`。

K8s 探针用的是 `/actuator/health/liveness` 与 `/actuator/health/readiness`，
自定义健康指标不在这两个分组内，因此无论 `fail-health` 取何值都**不会**影响 K8s 探针与重启策略。

## Redis 不可用时的行为并不统一

开启上述开关后，Redis 不可用时的表现**因组件而异**，部署前需明确预期：

| 组件 | Redis 不可用时 | 定位 |
|---|---|---|
| JWT 缓存/持久化/黑名单 | **fail-fast**：Bean 初始化失败，应用启动失败 | `RedisJwtCacheConfiguration` |
| 配额分布式计数 | **fail-open**：降级回本地计数并打告警 | `QuotaProperties.degradeToLocal`（默认 `true`） |

即：**Redis 挂掉时应用可能直接起不来**（JWT 侧），而配额侧会静默降级成本地计数
（此时限流又变回每副本一份）。这是既有实现的选择，不是本文引入的行为。生产环境请为 Redis
配置持久化与高可用，并把「Redis 不可用」纳入告警。

## 定时任务的多副本语义

多副本下 `@Scheduled` 任务会在每个副本各执行一遍，但语义按任务类别区分，**并非一律只应执行一次**：

- **写共享目录文件的任务**（安全日志归档、指标落盘）按副本命名产物，从根上消除竞争，
  而非加锁——这样即使 Redis 不可用，产物也不会互相覆盖。
- **把本副本增量合入共享存储的任务**（配额快照落库、限流器状态同步）**必须每个副本都执行**。
  给它们加锁反而会让「抢到锁的副本」成为唯一写入者，直接丢掉其它副本的增量。
- 副作用可被外部观测的任务（发告警、出日报、轮换密钥）加跨副本排他锁，
  由 `jairouter.scheduling.distributed-lock.enabled` 控制（默认 `true`，未配 Redis 时自动降级为单实例语义）。
- **本副本缓存刷新**（API Key 缓存）同样属于「每个副本都必须执行」，理由见下一节。

## 缓存跨副本收敛

API Key 校验走的是各副本自己的内存镜像，只在本进程内更新。为让吊销与新建跨副本生效，
`ApiKeyService` 按 `jairouter.security.api-key.cache-refresh-interval-seconds`（默认 60 秒）
从共享存储刷新本副本缓存：

- **收敛上限 = 刷新间隔**：在副本 A 吊销的 Key，最迟一个刷新间隔后在副本 B 失效。
- 刷新是**替换式**的：既补入其它副本新建的 Key，也移除已删除的 Key。
- 刷新**不回写**存储，避免用本副本的旧视图覆盖兄弟副本的变更（`loadLatestApiKeyConfig`
  只增不减且末尾会全量回写，不能用作刷新）。
- 存储读不到配置、内容为空、或条目全部缺少 `keyHash` 时**保留现有缓存**：宁可让已删除的 Key
  多存活一轮，也不要把全部有效 Key 剔除导致服务不可用。
- 置 `cache-refresh-enabled: false` 可回到「仅启动时装载」的旧行为。

## 限流键维度与多副本语义

限流状态**全部在 JVM 内**，且仓库**不存在分布式限流实现**（无 Redis 限流器）——这一点与配额账本、
JWT 黑名单不同，后两者都有现成的 Redis 实现、只差开关。因此多副本下：

> 同一客户端的实际放行量 ≈ 配置值 × 副本数，且窗口重置各副本独立，突发形态还会随请求落到哪个
> Pod 而变化（等价于每 Pod 各发一个满额桶）。

**当前决策：先修正键维度，分布式计数待评估。** 理由是键维度不成立时做了分布式计数也是错的：
集群 + 无 L7 Ingress 的部署下 `remoteAddress` 是**节点 IP** 而非真实客户端 IP，按 IP 计数等于
按垃圾数据计数——要么把整个节点的流量算作一个客户端，要么在反代后把所有流量算作网关。

### 配置

```yaml
model:
  rate-limit:
    client-ip-enable: true          # 启用「客户端维度」限流
    key-dimension: "client-ip"      # client-ip（默认）/ api-key / tenant
```

三种维度的取值与回退行为：

| 维度 | 键值 | 键值缺失时 |
|---|---|---|
| `client-ip` | 客户端 IP | 判为不可用 → 跳过该级限流 + 告警 |
| `api-key` | 调用方 API Key ID | **回退客户端 IP 并告警**（说明配置与实际流量不匹配） |
| `tenant` | API Key ID，缺失时回退客户端 IP | 回退属于设计行为，不告警 |

**回退是显式的，不是静默放行**：回退后限流**仍然生效**，只是把被限流对象换成 IP 粒度；只有维度值
与回退值都缺失时才跳过该级限流，并输出 WARN 日志。缓存键带维度前缀，切换维度后不会串用旧维度的
限流器状态。

`ip-hash` 负载均衡在取不到客户端 IP 时仍回退随机选择（不回退就无法选实例），但该回退现在同时输出
WARN 日志与 `recordLoadBalancer(..., "random-fallback-no-client-ip")` 指标——此前只有日志，
运维无法从指标发现「粘性名存实亡」。

### 跨副本计数（可选）

开启 `jairouter.ratelimit.distributed.enabled` 后，客户端维度的滑动窗口状态放到 Redis，各副本共享同一份计数 —— 放行量等于配置值，而不是配置值 × 副本数。

- **与本地实现严格对齐**：窗口 1 秒、上限 `rate` 次/秒；切换实现只改变计数范围，不改变限流行为。
- **Lua 原子**：判定（`ZCARD`）与记录（`ZADD`）在同一段脚本内完成，多副本并发不会超发。
- **fail-open**：Redis 不可用（连接失败/超时）时放行并告警 —— 全站拒绝比放宽限流更糟，与配额侧 `degradeToLocal` 的取向一致。
- **有界阻塞**：该调用发生在 `Schedulers.boundedElastic` 上（见 `ServiceRequestHandler` 的 `selectInstance` 调用），不会卡住 Netty EventLoop；超时默认 50ms。
- 桶带 TTL 自动过期，无需清理任务。

**前提仍然是键维度成立**：无 L7 反代时按 IP 计数，分布式化只是更精确地统计垃圾数据。

### 仍未做

跨副本计数只覆盖了**客户端维度**；服务级 / 实例级 / 规则级限流器仍是每副本一份，多副本下这几级的放行量仍会随副本数放大，需要按同样思路改造。

## 实时事件跨副本广播

路由监控与熔断监控 WebSocket 的事件源是**本进程运行时回调**（路由决策、熔断状态变更）。
多副本下客户端连在哪个副本就只看得到哪个副本的事件——监控面板会**静默漏事件**：不是报错，
而是看起来正常但数据不全。

开启 `jairouter.cluster.events.enabled` 后，本副本产生的事件会经 Redis pub/sub 广播到全集群，
各副本把收到的事件并入自己的 WebSocket 输出流：

- **回环过滤**：消息带来源副本标识，本副本发出的事件不再向下分发——本地 Sink 已经分发过一次，
  不滤掉会让客户端收到重复事件。
- **降级**：Redis 连接不可用时只告警一次并退化为「仅本副本可见」，不阻断启动——
  可观测性能力不应让服务起不来。
- **自愈**：订阅中断（Redis 重启、网络抖动）按退避重试，Redis 恢复后自动续订，无需重启应用。
- 每个逻辑通道共享一条 Redis 订阅，不会随客户端数量增长而放大订阅数。

**健康状态 SSE 刻意不做广播**：它的数据来自共享数据库（`ServiceInstanceRepository`）且每 5 秒
生成一次快照，各副本看到的本就是同一份数据，广播只会带来重复推送。多副本下的差异仅是主动变更
通知最多延迟一个快照周期（5 秒）。

配置：

```yaml
jairouter:
  cluster:
    events:
      enabled: true      # 默认 false：单副本部署无需跨副本广播
```

## 配置变更的生效时间契约

管理 API 修改服务实例或路由规则后，配置以「新版本」写入共享存储，并**只在处理该请求的副本内**
立即重载（保存版本后调用 `ModelServiceRegistry.refreshFromMergedConfig`）。

- **发起变更的副本**：立即生效。
- **其它副本**：**不会**被主动通知（仓库既无配置广播，也无定时拉取），保持旧配置，直到该副本
  自身触发一次本地重载（本副本上的一次管理操作）或重启。

即多副本下配置变更**不是全局原子**的，存在「各副本短暂使用不同规则」的窗口，窗口长度取决于运维
何时滚动重启或在本副本触发 reload。若该窗口不可接受，需在网关层做变更期流量固定，或等待后续
引入配置广播 / 定时拉取。

## 已知未覆盖

以下项已记录但尚未实现，多副本部署需知悉：

- **权限缓存失效不跨 Pod 主动传播**：`RolePermissionService` 的缓存（`Caffeine`，写后 5 分钟过期）
  只在本进程失效，改角色后各副本最长 5 分钟内不一致——这是**已知且有界**的失效上限。
  API Key 缓存已由上一节的定时刷新覆盖（上限同样是刷新间隔）。
- **配置热更新不互推**：管理 API 改配置后只有本进程生效，其它副本靠重启或再次触发本地 reload，
  期间路由与限流规则在各副本间短暂分裂。
- **DDL 由各副本自行执行**：多副本同时启动时并发 DDL 的前置治理（版本化迁移）尚未落地。

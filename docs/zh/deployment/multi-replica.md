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

## 前提：所有副本共用同一个 `JWT_SECRET`

除下表六个开关外，多副本还有一条**不属于开关**的硬前提：`JWT_SECRET` 必须在所有副本上是**同一个值**。

`config/auth/jwt.yml` 里 `jairouter.security.jwt.secret` 的默认值是 `${JWT_SECRET:${random.uuid}}`：不设环境变量时，
**每个副本各自生成一个随机 UUID**。于是副本 A 签发的令牌拿到副本 B 上验签失败，表现为**间歇性 401**
（约 `(n-1)/n` 的请求失败，日志只说「令牌无效」）。

生产 profile 有启动门闩强制要求 `JWT_SECRET`，不会踩到；**dev / staging 的多副本没有这层保护**，
所以启动自检会对「多副本 + 未设置 `JWT_SECRET`」给出一条 WARN，写明令牌无法跨副本验证。

## 必开配置项

多副本部署需同时开启以下 7 项，并配好 Redis 连接：

| 配置项 | 默认 | 多副本应设为 | 不开的后果 |
|---|---|---|---|
| `jairouter.security.jwt.blacklist.redis.enabled` | `false` | `true` | 吊销的令牌在其它副本仍被放行 |
| `jairouter.security.jwt.persistence.redis.enabled` | `false` | `true` | 令牌状态各副本独立，重启可能丢失已签发状态 |
| `jairouter.security.cache.redis.enabled` | `false` | `true` | API Key 缓存各副本独立，吊销/新建不跨副本生效 |
| `jairouter.quota.distributed.enabled` | `false` | `true` | 配额计数每副本一份，放行量约为配置值 × 副本数 |
| `jairouter.persistence.redis.enabled` | `false` | `true` | 状态持久化退坡到本机 H2/文件，不做跨节点共享 |
| `jairouter.ratelimit.distributed.enabled` | `false` | `true` | 终端用户限流计数每副本一份，放行量约为配置值 × 副本数 |
| `jairouter.semantic-cache.redis.enabled` | `false` | `true` | 语义缓存条目各副本独立，命中率约摊薄为单副本的 1/N |

开启配额分布式计数时还需 `jairouter.quota.enabled=true`（配额功能本身的总开关）；终端用户限流另由 `jairouter.ratelimit.end-user.enabled` 控制（默认关），上表那条只决定它的计数是否跨副本共享。API Key 每分钟限流由 `jairouter.ratelimit.api-key.enabled` 控制（**默认开**，v3.8.6 起接替已删除的认证链本机限流器），它的计数是否跨副本共享同样取决于上表的 `jairouter.ratelimit.distributed.enabled`。

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
      # 已开启的共享态开关依赖 Redis 时，启动期探测 Redis 是否连得上（仅 staging/prod 生效）
      # FAIL（默认）= 连不上就启动失败；WARN = 只告警；OFF = 不检查
      redis-reachability: FAIL
      redis-timeout-ms: 2000
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

### 依赖可达性（仅 staging / prod，v3.5.7+）

上面的判定只看「开关开没开」，不看「开关背后的 Redis 在不在」。`staging` 默认就把
`jairouter.security.jwt.persistence.redis.enabled` 设成 `true`，如果那里没有部署 Redis，
应用仍会正常启动，直到第一次写令牌状态才报错——那时用户已经在登录了。

所以 `staging` / `prod` 下还有一道启动检查：**只要有一个依赖 Redis 的共享态开关是开启的，
就探测 `spring.data.redis.host:port` 是否可连**，连不上直接启动失败（默认
`jairouter.cluster.shared-state-check.redis-reachability: FAIL`）。

- 探测只做 TCP 建连，超时默认 2 秒（`redis-timeout-ms`，钳在 200ms–30s）；
- `dev` / `test` **不做**这项检查（本地常见「开关开、Redis 没起」）；
- 没有开启任何 Redis 开关时跳过（`prod` 默认即如此）；
- 逃生口：`WARN` 只告警不阻断，`OFF` 完全跳过。

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

**实测口径（2026-10-31，三副本 + 共享 PostgreSQL 16 + Redis 7）**：在副本 A 新建的 Key，副本 B 约
**28.5 秒**后认到（落在刷新窗口内）；吊销的收敛以刷新间隔为上界，但实测出现过**超过一个刷新间隔仍未收敛**
（一次 >80 秒仍在放行、另一次 300 秒窗口内未收敛，数分钟后复查才全部 `INVALID_API_KEY`）。排障时先确认
各副本的刷新任务是否在跑（日志关键字「刷新 API Key 缓存」），再怀疑配置。

## 限流键维度与多副本语义

限流状态**默认在 JVM 内**。仓库里所有 Redis 计数路径都受 `jairouter.ratelimit.distributed.enabled` 控制：
终端用户限流（v3.5.2）、API Key 每分钟限流（v3.8.6，前缀 `jairouter:ratelimit:api-key:`）、客户端维度限流
（`RedisSlidingWindowRateLimiter`，窗口 1 秒，v3.5.2 起已接进主链路、但**仓库默认关闭**，见 #623），
以及 v3.8.6 起接上的全局 / 服务级 / 实例级 / 规则级限流器（前缀 `jairouter:ratelimit:scoped:`，见 #1114）。

**API Key 的每分钟限流（`rateLimitPerMinute`）自 v3.8.6 起由主链路的 `ApiKeyMinuteRateLimitService` 判定**，
开关是 `jairouter.ratelimit.api-key.enabled`（默认 `true`）。打开 `jairouter.ratelimit.distributed.enabled`
后所有副本共用同一个 Redis 固定分钟窗口，`rateLimitPerMinute=10` 就是**全局**上限；关闭时退回每副本一份本机
计数，此时实际放行量 ≈ 配置值 × 副本数。

> 这条路径在 v3.8.6 之前由认证链的进程内滑动窗口与配额账本的 MINUTE 窗口各判一次，两套都是每副本独立。
> 实测口径：`rateLimitPerMinute=10` 的 Key 在三副本上并发 18 次（每副本 6 次）**全部放行（18/18，0 个 429）**，
> Redis 中也没有任何 `jairouter:ratelimit:*` 键；单副本连打 14 次则是 `[200×9, 429×5]`。现在认证链与账本
> 都不再判定每分钟速率，配额账本只负责小时/天/月（#1113）。

**v3.8.6 起，全局 / 服务级 / 实例级 / 规则级四级限流器也跟随同一开关**（#1114）：打开后它们同样写 Redis，
放行量等于配置值而不是配置值 × 副本数；关闭时退回每 Pod 各一份。仍未接线的是对**本地限流器状态**的持久化
（`RateLimiterStatePersistenceAdapter` 只认本地实现），那是重启恢复能力，与计数口径无关。

因此，未开该开关的多副本部署下：

> 同一客户端的实际放行量 ≈ 配置值 × 副本数，且窗口重置各副本独立，突发形态还会随请求落到哪个
> Pod 而变化（等价于每 Pod 各发一个满额桶）。

**当前决策：先修正键维度，分布式计数待评估。** 理由是键维度不成立时做了分布式计数也是错的：
集群 + 无 L7 Ingress 的部署下 `remoteAddress` 是**节点 IP** 而非真实客户端 IP，按 IP 计数等于
按垃圾数据计数——要么把整个节点的流量算作一个客户端，要么在反代后把所有流量算作网关。

> **实现状态（v3.5.2 起已接线，#623）**：主链路在认证之后、服务级预扣之前调用
> `RateLimitManager.tryAcquireClientIp`，键维度由 `key-dimension` 决定（回退语义见下表）。
> **仓库自带配置把 `client-ip-enable` 一律置为 `false`** —— 集群 + 无 L7 反代时 `remoteAddress` 是节点 IP，
> 按 IP 计数会把不同客户端合并；这种部署请改用 `key-dimension: api-key`。真正按「调用方 × 下游用户」
> 计数的是 v3.5.2 的终端用户限流（见[限流配置](../configuration/rate-limiting.md)）。

### 配置

```yaml
model:
  rate-limit:
    client-ip-enable: false         # 默认关闭；仅在真实客户端 IP 能到达 Pod 时开启
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

`jairouter.ratelimit.distributed.enabled=true` 时，限流计数写到 Redis，各副本共享同一份 —— 放行量等于配置值，而不是配置值 × 副本数。

**该开关作用于全部限流路径**：终端用户限流（`jairouter.ratelimit.end-user.enabled`，默认关闭）、
API Key 每分钟限流（`jairouter.ratelimit.api-key.enabled`，v3.8.6 起默认开启）、客户端维度限流
（v3.5.2 起已接线、仓库自带配置默认关闭），以及全局 / 服务级 / 实例级 / 规则级限流器（v3.8.6 起，见 #1114）。
未开启该开关时它们都用本机有界计数器，各副本各算各的。

- **Lua 原子**：`INCR` → 首次写入时设置 TTL → 超过上限则 `DECR` 回滚并在同一段脚本里返回拒绝，多副本并发不会超发。
- **fail-open**：Redis 不可用（连接失败/超时）时放行并告警 —— 全站拒绝比放宽限流更糟，与配额侧 `degradeToLocal` 的取向一致。
- **有界阻塞**：超时默认 50ms，不阻塞 Netty EventLoop。
- 键带 TTL 自动过期，无需清理任务。
- 未开启该开关时用本机有界计数器（Caffeine，条目上限由 `local-cache-size` 决定），多副本下各算各的。

### 开启分布式后的两点语义差异

- **容量（`capacity`）不再生效**：本地实现是 token-bucket，允许 `capacity` 的初始突发；Redis 路径是每秒滑动窗口，
  只认 `rate`。开分布式后突发被抹平、上限变准 —— 这是为了让多副本下有可预期的全局上限，属有意取舍。
- **本地限流器状态持久化不覆盖 Redis 路径**：`RateLimiterStatePersistenceAdapter` 只读写本地实现的桶状态，
  重启恢复能力限于「未开分布式」的部署；Redis 侧靠 key 的 TTL 自然过期，不需要清理任务。

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

## 集群成员发现与状态可见（v3.8.6+）

多副本部署下每个副本会在共享存储里维护一条「成员记录」，让运维能在一个页面上看到「一共有几个副本、
谁在线、谁的配置版本落后」：

```yaml
jairouter:
  cluster:
    mode: auto                 # auto / standalone / cluster
    advertise-url: ""          # 可选：本副本对外可达地址，缺省 http://<hostname>:<server.port>
    heartbeat-interval-ms: 5000
    ttl-seconds: 15            # 超过该时长未续期即视为失联
    member-store: redis        # redis / db
```

- **单机模式零开销**：`mode=auto` 时按 `replicas>1` 或 `KUBERNETES_SERVICE_HOST`/`POD_NAME` 判定；
  判为单机则心跳线程与成员存储根本不启动（Bean 存在但不工作），不会因为多了一个部署形态而多一份后台开销。
- **成员表落点**：默认 Redis（`SET ... EX ttl` 每 `heartbeat-interval-ms` 续期，租约语义，无需清理任务）；
  `member-store: db` 时改用 PostgreSQL 表 + 定时清理，适用于「客户只提供 PostgreSQL」的部署。
- **`advertise-url` 建议显式配置**：容器 bridge 网络下 `hostname:port` 对外不可达，K8s Pod IP 会随重启变化；
  工具链（`sla`）与其它副本靠它访问本副本。
- **状态端点**：`GET /api/cluster/status`（权限 `config:cluster:read`）返回 `mode`、`modeSource`、`self`、
  `clusterConfigVersion`、`summary{total,online,suspect,staleReplicas}` 与 `members[]`
  （每个成员的 `displayName` / `advertiseUrl` / `startedAt` / `configVersion` / `instanceCount` /
  `healthyInstances` / `buildVersion` / `lastSeen`）。单机模式返回 `mode: "standalone"` 与空成员表。
- **控制台**：「系统 → 集群设置」页展示成员表与配置同步参数，读写走 `/api/config/cluster`
  （权限 `config:cluster:read` / `config:cluster:write`）。

成员记录的默认 TTL 是 15 秒（3 个心跳周期）。副本被 `kill -9` 时，最长 15 秒后从成员表消失；
页面把「超过 2 个心跳周期未续期」的成员标为疑似离线（黄色），不会立即从表里移除。

## 上游健康状态跨副本共享（v3.8.6+）

默认每个副本对上游实例的健康判定**各算各的**：副本 A 认为某实例挂了，副本 B 可能仍认为它健康，
于是同一时刻不同副本对同一实例做出相反的路由决策。开启健康共享后，各副本把自己对每个实例的判定
写进共享存储，选路时按配置的语义合并：

```yaml
jairouter:
  cluster:
    health-sharing:
      enabled: false           # 默认关：单副本无需共享
      mode: quorum             # local / shared / quorum
      tolerance-count: 1       # quorum 下至少多少个副本认为健康才算健康
      grace-seconds: 30        # 共享视图的新鲜度窗口
```

| 模式 | 语义 |
|---|---|
| `local` | 只看本地判定（等价于不开启） |
| `shared` | 有新鲜上报时以共享视图为准；无上报退回本地 |
| `quorum` | 本地健康即健康；本地不健康时要求「共享视图里健康副本数 ≥ `tolerance-count`」 |

**失败语义**：共享视图读取异常时回退本地判定（选路不受影响），只记 debug 日志。

## 配置变更的生效时间契约（v3.8.6+）

管理 API 修改服务实例或路由规则后，配置以「新版本」写入共享存储。v3.8.6 起新增**跨副本同步**：

```yaml
jairouter:
  cluster:
    config-sync:
      enabled: true            # 默认开
      poll-interval-ms: 5000   # 轮询共享存储版本号的间隔
      event-bus-enabled: true  # 额外用 Redis pub/sub 即时通知（需 cluster.events.enabled=true）
```

- **发起变更的副本**：立即生效（保存后本地重载）。
- **其它副本**：最迟一个 `poll-interval-ms` 内感知版本号前进并重载；`event-bus-enabled` 且
  `cluster.events.enabled=true` 时通常亚秒级。
- **版本号来源**：共享存储里配置的 `version` 列（每轮只读一次 `max`，代价很小）。
- **版本号分配**：由 `config_sequences` 表 + CAS 保证跨副本不撞号（v3.8.6，#1135）。
- 单副本部署不受影响：开关虽默认开，但轮询发现版本不变时不动作。

## 已知未覆盖

以下项已记录但尚未实现，多副本部署需知悉：

- **权限缓存失效不跨 Pod 主动传播**：`RolePermissionService` 的缓存（`Caffeine`，写后 5 分钟过期）
  只在本进程失效，改角色后各副本最长 5 分钟内不一致——这是**已知且有界**的失效上限。
  API Key 缓存已由上一节的定时刷新覆盖（上限同样是刷新间隔）。
- **DDL 由各副本自行执行**：Flyway 迁移在各副本启动时各自执行，并发由 Flyway 自己加锁；
  跨版本升级时仍建议先滚动到一个副本完成迁移，再滚动其余副本。

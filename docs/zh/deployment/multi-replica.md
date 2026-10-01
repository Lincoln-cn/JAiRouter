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

Redis 连接信息由各开关自己的 `host` / `port` / `password` 配置，或统一用 `REDIS_HOST`、
`REDIS_PORT`、`REDIS_PASSWORD` 环境变量注入（推荐后者，避免多处配置漂移）。

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

## 已知未覆盖

以下项已记录但尚未实现，多副本部署需知悉：

- **权限缓存失效不跨 Pod 主动传播**：`RolePermissionService` 的缓存（`Caffeine`，写后 5 分钟过期）
  只在本进程失效，改角色后各副本最长 5 分钟内不一致——这是**已知且有界**的失效上限。
  API Key 缓存已由上一节的定时刷新覆盖（上限同样是刷新间隔）。
- **配置热更新不互推**：管理 API 改配置后只有本进程生效，其它副本靠重启或再次触发本地 reload，
  期间路由与限流规则在各副本间短暂分裂。
- **DDL 由各副本自行执行**：多副本同时启动时并发 DDL 的前置治理（版本化迁移）尚未落地。

# 环境变量参考

本文说明 JAiRouter 支持哪些环境变量、每个变量在什么条件下生效、不设置会怎样，以及各 profile 之间**真实存在的差异**。
所有条目都取自随包配置与代码，括号内给出生效位置；文中「实测」结论来自本机真实启动（prod / dev / staging）与端点读数。

## 先读：环境变量生效的三条通道

同一个应用里，环境变量的生效方式并不相同。搞清通道，才能判断「设了为什么没用」。

| 通道 | 触发条件 | 典型变量 | 写错的后果 |
|---|---|---|---|
| `yml` 占位符 | 配置文件里显式写了 `${VAR:默认值}` | `JWT_SECRET`、`DATABASE_URL`、`GPUSTACK_API_TOKEN`、`JAIROUTER_*` | 走默认值，静默 |
| Spring 宽松绑定 | 变量名 = 配置键转下划线大写；目标属性存在即生效 | `SERVER_PORT`、`SPRING_PROFILES_ACTIVE`、`JAiROUTER_SECURITY_*`、`SPRING_DATA_REDIS_HOST` | 名字拼错**不会报错**，只是无效 |
| 直接读取进程环境 | 代码里 `System.getenv("…")` | 仅 `JWT_SECRET`、`INITIAL_ADMIN_PASSWORD` | 这条通道不吃宽松绑定，也不吃别名 |

第二、三条的差别是本节最容易被忽略的一点：`JWT_SECRET` 之所以必须叫这个名字，是因为启动门闩（`StartupSecretKeyChecker` / `StartupSecurityGate`）直接读进程环境，而不是读配置树。`#674` 起 `DEV_JWT_SECRET` / `STAGING_JWT_SECRET` / `PROD_JWT_SECRET` / `ADMIN_PASSWORD` 四个别名已移除，仍设置它们只会在启动日志里得到一条 warn。

## 必须设置的变量（按 profile）

| profile | 变量 | 不设的后果 | 依据 |
|---|---|---|---|
| `prod` / `production` | `JWT_SECRET`（≥ 32 字符，且不得是历史默认值） | 启动门闩抛 `IllegalStateException`，进程退出 | `StartupSecurityGate.enforce`（`auth/security/config/StartupSecurityGate.java`） |
| `prod` / `production` | `INITIAL_ADMIN_PASSWORD`（不得等于 `ChangeMeOnFirstStartup123456`） | 同上，进程退出 | 同上 |
| `staging` | `JWT_SECRET`（≥ 32 字符） | 配置绑定失败：`JwtConfig.secret` 的 `@Size(min = 32)` 校验不通过，退出码 1 | `application-staging.yml:33` → `config/auth/jwt.yml:24` |
| `dev` / 未指定 profile | 无必填 | `JWT_SECRET` 与管理员口令会随机生成，并打印在启动日志里 | `config/auth/jwt.yml:24`、`:48` |

`JWT_SECRET` 用镜像生成强值：

```bash
docker run --rm sodlinken/jairouter:latest --generate-key
```

> 生产环境还有一类「设置了也白设」的写法：只设 `PROD_JWT_SECRET` 而没设 `JWT_SECRET` 的部署从来起不来，因为门闩只读后者。

## 建议设置的变量（按部署形态）

### 数据存储

| 变量 | 默认值 | 何时必须设置 |
|---|---|---|
| `DATABASE_URL` | `jdbc:h2:file:./data/jairouter;…`（`config/config-service/core.yml:18`） | **prod 建议必设**：三种 profile 都没有覆盖数据源，缺省即落容器内 H2 文件；`docker-compose*.yml` 用 `./data:/app/data` 卷保住了数据，裸 `docker run` 与未挂卷的 k8s 会随容器一起丢 |
| `DATABASE_USERNAME` | `sa`（`:19`） | 接外部数据库时随 `DATABASE_URL` 一起给 |
| `DATABASE_PASSWORD` | 空（`:20`） | 同上 |

### Redis（仅当启用任一 Redis 相关开关时）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `REDIS_HOST` | `localhost`（`config/security/persistence-base.yml:80`） | 等价写法 `SPRING_DATA_REDIS_HOST` |
| `REDIS_PORT` | `6379`（`:81`） | 等价写法 `SPRING_DATA_REDIS_PORT` |
| `REDIS_PASSWORD` | 空（`:82`） | 生产建议设置 |
| `REDIS_DATABASE` | `0`（`:83`） | 多套环境共用一个 Redis 时用来隔离键空间 |
| `REDIS_ENABLED` | `false`（`:114`、`config/base/monitoring-base.yml:49`） | **只控制健康探针是否检查 Redis**，不控制是否使用 Redis |

连接超时不是环境变量：`spring.data.redis.timeout: 5000ms`（`persistence-base.yml:84`）。镜像里的 `REDIS_TIMEOUT` 没有绑定（见 [#677](https://github.com/Lincoln-cn/JAiRouter/issues/677)）。

### 上游模型服务

| 变量 | 默认值 | 说明 |
|---|---|---|
| `GPUSTACK_API_TOKEN` | 空 | 用于 `config/router/services.yml` 的 9 处 `Authorization: "Bearer ${GPUSTACK_API_TOKEN:}"`；不设时下游会收到空的 Bearer 头，由下游自行拒绝 |

### 多副本

| 变量 | 默认值 | 取值含义 |
|---|---|---|
| `JAIROUTER_REPLICAS` | `0`（`config/common/cluster.yml:16`） | `0` = 未知，运行时探测；`1` = 单副本，跳过共享态检查；`>1` = 多副本，按共享态门禁检查 |
| `JAIROUTER_SHARED_STATE_MODE` | `WARN`（`:38`） | `OFF` / `WARN` / `FAIL`，决定共享态缺口是静默、告警还是拒绝启动 |
| `JAIROUTER_SHARED_STATE_FAIL_HEALTH` | `false`（`:47`） | `true` 时把共享态缺口反映到健康检查 |
| `JAIROUTER_CLUSTER_EVENTS` | `false`（`:29`） | 跨副本的实时事件广播；Redis 不可用时降级为仅本副本并告警 |
| `JAIROUTER_RATELIMIT_DISTRIBUTED` | `false`（`config/router/ratelimit.yml:46`） | 终端用户级限流的计数是否放 Redis；Redis 故障时按 fail-open 放行，单次调用超时 50 ms（`:51`） |

多副本必开哪些开关、不开分别会怎样，见[多副本部署](multi-replica.md)。

### 框架级开关

这类变量不在 `yml` 占位符里，靠 Spring 宽松绑定生效；镜像里已经替你设好了几个（`Dockerfile:55-64`）：

| 变量 | 等价配置键 | 镜像默认 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 激活的 profile | `prod` |
| `SERVER_PORT` | `server.port` | `8080` |
| `JAiROUTER_SECURITY_ENABLED` | `jairouter.security.enabled` | `true` |
| `JAiROUTER_SECURITY_JWT_ENABLED` | `jairouter.security.jwt.enabled` | `true` |
| `JAiROUTER_SECURITY_API_KEY_ENABLED` | `jairouter.security.api-key.enabled` | `true` |
| `JAiROUTER_SECURITY_SANITIZATION_REQUEST_ENABLED` | `jairouter.security.sanitization.request.enabled` | `true` |
| `JAiROUTER_SECURITY_SANITIZATION_RESPONSE_ENABLED` | `jairouter.security.sanitization.response.enabled` | `true` |
| `JAiROUTER_SECURITY_AUDIT_ENABLED` | `jairouter.security.audit.enabled` | `true` |

## 各 profile 的默认差异

下表是「同一配置键在三个 profile 下的默认值」。差异都来自 `application-{profile}.yml` 对基础配置的覆盖，不需要设置任何环境变量。

| 配置键 | `default`（不指定） | `dev` | `staging` | `prod` |
|---|---|---|---|---|
| `jairouter.security.jwt.secret` 兜底 | `${random.uuid}`（每次启动随机） | 开发字面量 `dev-secret-key-for-testing-only-32-characters-minimum` | 空 ⇒ 必须设 `JWT_SECRET` | 空 ⇒ 必须设 `JWT_SECRET` |
| `jairouter.security.jwt.issuer` | `jairouter` | `jairouter` | `jairouter-staging` | `jairouter-prod` |
| 管理员口令兜底 | `${random.uuid}` 并打印 | `ChangeMeOnFirstStartup123456` | `${random.uuid}` 并打印 | 空 ⇒ 必须设 `INITIAL_ADMIN_PASSWORD` |
| 内置测试 API Key | 无 | 有（`test-key-001`） | 无 | 无 |
| `jairouter.security.jwt.persistence.enabled` | `false` | `false` | `true`（`application-staging.yml:38-41`） | `false` |
| `jairouter.security.jwt.persistence.redis.enabled` | `false` | `false` | `true` | `false` |
| `jairouter.security.audit.log-level` | `INFO` | `DEBUG`（含请求/响应体） | `INFO`（保留 30 天） | `WARN`（不含请求/响应体，保留 90 天） |
| `jairouter.security.sanitization.response.enabled` | `false` | `false` | `true` | `true` |
| 日志级别 / 文件 | 控制台 | `DEBUG` | `root INFO`、应用 `INFO`、Spring `WARN` → `logs/jairouter-staging.log` | `root WARN`、应用 `WARN`、Spring `ERROR` → `logs/jairouter-prod.log` |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus,jairouter-metrics,error-tracking`（`config/base/monitoring-base.yml:16`） | `*`（`application-dev.yml:49`） | 继承基础值，`show-details: when-authorized` | `health,info,prometheus`（`application-prod.yml:33`） |

## `jwt.persistence.enabled` 到底关掉了什么

这一项最容易被读成「关闭令牌持久化」，实际不是。**关掉的不是落库**，而是围绕它的四类东西：

| 受控对象 | 条件 | 关掉后的可见后果 |
|---|---|---|
| `JwtTokenLifecycleServiceImpl` | `jwt.persistence.enabled=true` | 不再写状态流转历史（`statusHistory`） |
| `JwtTokenExpirationScheduler` | 同上 | 过期令牌不会自动从 `ACTIVE` 流转到 `EXPIRED` |
| `JwtServiceHealthChecker` | 同上 | 健康检查里少一个 JWT 持久化探针 |
| `JwtPersistenceConfiguration` 的两个 `@ConfigurationProperties` | 同上 | `storagePath` / `memory.*` / `cleanupThreshold` 等属性无从绑定（当前也无消费者） |

**落库本身不受它控制**：真正负责写入与查询的是 `JwtServiceConfiguration.storeManagerJwtPersistenceService`，条件只看 `jairouter.security.jwt.persistence.redis.enabled=false`（`matchIfMissing=true`）。实测 prod（该开关为 `false`）：登录两次后 `GET /api/auth/jwt/blacklist/stats` 返回 `persistedActiveTokens: 2`，令牌确实在库里。

清理任务也是独立开关：`jwt.persistence.cleanup.enabled`（默认 `true`），随包生效值为**每天 02:00、保留 30 天**（实测日志 `JWT cleanup service initialized with schedule: 0 0 2 * * ?, retention days: 30`）。

> 顺带提醒：`config/auth/jwt.yml` 里那份 `persistence` 段并不生效，实际生效值来自 `config/security/persistence-base.yml`，原因与取值差异见 [#676](https://github.com/Lincoln-cn/JAiRouter/issues/676)。

## 已移除或无效的名字

| 名字 | 状态 |
|---|---|
| `DEV_JWT_SECRET` / `STAGING_JWT_SECRET` / `PROD_JWT_SECRET` | 已移除（`#674`），仍设置只会得到启动 warn |
| `ADMIN_PASSWORD` | 已移除（`#674`），管理员口令只认 `INITIAL_ADMIN_PASSWORD` |
| `JWT_ALGORITHM` / `JWT_EXPIRATION_MINUTES` / `REDIS_TIMEOUT` | 镜像导出了但没有绑定（[#677](https://github.com/Lincoln-cn/JAiRouter/issues/677)）；真实键分别是 `jairouter.security.jwt.algorithm`、`jairouter.security.jwt.expiration-minutes`、`spring.data.redis.timeout` |
| `ADMIN_API_KEY` / `USER_API_KEY` | 当前随包配置零引用，只在 `scripts/migration/migrate-to-security.ps1` 生成的 yml 里被读取（[#677](https://github.com/Lincoln-cn/JAiRouter/issues/677)） |

## 核对方法与参考

- 启动日志里搜 `JWT cleanup service initialized with schedule:`、`The following 1 profile is active:`，可确认当前生效的 profile 与清理参数。
- `GET /actuator/health`：Redis 探针是否开启取决于 `REDIS_ENABLED`。
- `GET /api/auth/jwt/cleanup/stats`、`GET /api/auth/jwt/blacklist/stats`：需要 `Jairouter_Token` 头（网关自己的 JWT 走这个头，`Authorization` 留给下游）。
- 变量清单一览可用 `scripts` 之外的方式复核：`git grep -n '\${' -- src/main/resources` 会列出全部占位符及其默认值。

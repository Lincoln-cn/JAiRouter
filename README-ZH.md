# JAiRouter

<p align="center">
  <img src="logo/JAiRouterLogo.png" alt="JAiRouter — AI Model Gateway" width="180">
</p>

<p align="center">
  <strong>把你在跑的所有模型后端收进一个 OpenAI 兼容端点。</strong>
</p>

<p align="center">
  Ollama · vLLM · GPUStack · Xinference · LocalAI · OpenAI · Claude · Gemini<br>
  路由 · 负载均衡 · 限流 · 熔断 · 失败转移 · RBAC · 审计 · 可视化控制台
</p>

<p align="center">
  <a href="https://github.com/Lincoln-cn/JAiRouter/releases">
    <img src="https://img.shields.io/github/v/release/Lincoln-cn/JAiRouter?style=flat-square" alt="Latest release">
  </a>
  <a href="https://hub.docker.com/r/sodlinken/jairouter">
    <img src="https://img.shields.io/docker/pulls/sodlinken/jairouter?style=flat-square&logo=docker" alt="Docker Pulls">
  </a>
  <a href="https://github.com/Lincoln-cn/JAiRouter/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/Lincoln-cn/JAiRouter?style=flat-square" alt="License">
  </a>
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="https://jairouter.com">文档站</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/issues">Issues</a> •
  <a href="https://github.com/Lincoln-cn/JAiRouter/discussions">Discussions</a>
</p>

本 README 同时面向**人和 AI 助手**编写：下方的事实块，以及[仓库地图](#仓库地图)、
[常用命令](#常用命令)、[HTTP 面](#http-面)三节，是可直接机器读取的核心。**代码与本文件冲突时以代码为准**，
并请开 issue 提醒我们修文档。

<!-- 事实块：请与 pom.xml、application*.yml 及各 workflow 保持同步 -->
```yaml
product:         JAiRouter
repository:      Lincoln-cn/JAiRouter
checkout_dir:    model-router            # 本地/仓库目录名与产品名不同
artifact:        model-router            # Maven artifactId
project_version: 3.4.0                   # pom.xml
latest_release:  v3.4.0 (2026-10-21)
stack:
  backend:  Java 17 + Spring Boot 3.5.5（WebFlux / 响应式）
  frontend: Vue 3 + TypeScript + Vite，中英双语（zh-CN / en-US）
  build:    Maven wrapper —— ./mvnw（Unix）· .\mvnw.cmd（Windows）
runtime:
  default_port: 8080
  console:      /admin/
  swagger_ui:   仅 dev profile
  required_env_in_prod: [JWT_SECRET, INITIAL_ADMIN_PASSWORD]
surfaces:
  - /v1/**        # 客户端面：OpenAI 原生 + Anthropic 兼容
  - /api/**       # 管理面 + 历史面（受 RBAC 治理）
  - /actuator/**  # 健康、就绪、Prometheus
tests:
  command: ./mvnw test -o
  scale:   约 400 个测试类、约 4,100 个测试方法
license: Apache-2.0
```

## 它是什么

JAiRouter 是一个**可自托管的 AI 模型网关**。它把你已有的模型服务——Ollama、vLLM、GPUStack、
Xinference、LocalAI——以及云端供应商（OpenAI、Anthropic、Gemini，以及任何 OpenAI 兼容厂商）
统一到一个 OpenAI 兼容端点之后。原有的 OpenAI SDK、LangChain、LlamaIndex 代码无需改动：把
`base_url` 指过来即可。

它补齐的是你本来要自己写的那一层：多实例负载均衡、限流、实例熔断与**换一个健康实例重试**的
失败转移、RBAC、审计日志、记录治理与可观测性——全部可以从 Web 控制台在运行时修改，无需重启进程。

| 问题 | JAiRouter 的做法 |
|------|------------------|
| 模型端点太多、分散管理 | 统一到一个 API 面 |
| 后端挂了要人工切换 | 熔断 + 请求级失败转移 |
| 每个服务各自实现鉴权 | 内置 JWT + API Key，配数据驱动 RBAC |
| 日志和指标散落在各后端 | 统一指标、链路追踪、调用历史 |
| 改配置要重启 | 控制台热重载 |

## 快速开始

### 开发 profile —— 无需任何密钥

```bash
docker run -d --name jairouter -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  sodlinken/jairouter:latest

# 控制台：  http://localhost:8080/admin/
# 默认账号: admin / ChangeMeOnFirstStartup123456
```

dev profile 有意回退到内置的开发用密钥，因此零配置即可启动。**切勿把 dev profile 实例暴露在不可信网络。**

### 生产 profile —— 密钥必填

```bash
docker run -d --name jairouter -p 8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e INITIAL_ADMIN_PASSWORD='<你的强管理密码>' \
  sodlinken/jairouter:latest
```

**两个变量都是必填。** `prod` profile 带启动安全门闩：任一缺失、或仍是出厂默认值时
**拒绝启动**——具体规则见[生产环境启动门闩](#生产环境启动门闩)。只给 `JWT_SECRET` 的容器会在启动阶段
以 `IllegalStateException` 退出。

直接跑 JAR（不走 Docker）时，先生成强值：

```bash
java -jar target/model-router-*.jar --generate-key        # JWT 密钥
java -jar target/model-router-*.jar --generate-password   # 管理员密码
```

### 让客户端接进来

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",             # OpenAI 原生面（原始 JSON / SSE）
    api_key="not-needed",                            # Authorization 会被转发给下游
    default_headers={"X-API-Key": "<your-api-key>"}  # 网关凭据（管理面创建的 API Key）
)

response = client.chat.completions.create(
    model="llama3.2",                                # 你配置过的任意模型
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

已注册的后端在控制台的**实例管理**里维护；改动经热重载生效，无需重启。

## 生产环境启动门闩

门闩实现位于 `StartupSecretKeyChecker`（一个 `CommandLineRunner`）与 `StartupSecurityGate`。
每次启动都会执行，且**直接读环境变量**——只设置等价的 YAML 属性**不足以**通过它：

| 检查项 | `prod` / `production` profile 下的规则 | 其它 profile |
|--------|----------------------------------------|--------------|
| JWT 是否启用 | `jairouter.security.jwt.enabled` 必须为 `true`，否则失败 | 不检查 |
| `JWT_SECRET` | 必须已设置、不是已知出厂/文档默认值、且强度不低于 `MEDIUM` | 仅告警 |
| `INITIAL_ADMIN_PASSWORD` | 必须已设置、不能是出厂默认值 `ChangeMeOnFirstStartup123456`、且强度不低于 `MEDIUM` | 仅告警 |

几点值得知道：

- **生产环境缺 `INITIAL_ADMIN_PASSWORD` 是硬失败。** `ADMIN_PASSWORD` 作为备用变量名被接受，
  但**系统不会替你生成任何密码**。
- **不存在「自动生成随机密码」这回事。** 两个变量都没设时启动直接中止——既不会随机生成，
  也不会回落到文档里的默认值。（唯一的生成器是显式 CLI 参数 `--generate-key` / `--generate-password`。）
- 强度为 `MEDIUM` 的值会打警告并**允许**启动；`VERY_WEAK` / `WEAK` 会阻断启动。
- 可信隔离网络的逃生舱：`JAIRouter_SKIP_AUTH_WARNING=true`。必须显式设置，且它会一次性关掉
  上表**全部**检查。
- `admin` 账号本身来自 `src/main/resources/config/auth/jwt.yml` 的
  `jairouter.security.jwt.accounts`，其口令是
  `{noop}${INITIAL_ADMIN_PASSWORD:ChangeMeOnFirstStartup123456}`。首次启动时这些 YAML 账户会在
  `jwt_accounts` 表**为空**的前提下被写入该表（`JpaDatabaseInitializer.initializeJwtAccounts`）；
  之后的启动不再改动该表，所以**改 `INITIAL_ADMIN_PASSWORD` 不会改变已存在于数据库里的账号**——
  请到控制台里改。

## 仓库地图

| 路径 | 内容 |
|------|------|
| `src/main/java/org/unreal/modelrouter/router/` | 网关核心：适配器、请求处理器、负载均衡、规则引擎、熔断、缓存 |
| `src/main/java/org/unreal/modelrouter/auth/` | 认证（JWT、API Key）、脱敏、安全审计、RBAC、控制台侧认证接口 |
| `src/main/java/org/unreal/modelrouter/monitor/` | 指标、链路追踪、调用历史、慢查询分析、错误追踪、安全告警 |
| `src/main/java/org/unreal/modelrouter/persistence/` | 存储抽象 + JPA 实体与仓储 + schema 初始化 |
| `src/main/java/org/unreal/modelrouter/config/` | 配置服务：热重载、版本与回滚、校验、控制台接口 |
| `src/main/resources/config/**` | 模块化 YAML 配置，由 `application.yml` 的 `spring.config.import` 引入 |
| `src/main/resources/db/migration/{h2,postgres}/` | 版本化 schema 迁移（`Flyway`），两种方言各一个目录 |
| `src/test/java/` | JUnit 5 + Mockito + Reactor `StepVerifier`；约 4,100 个测试方法 |
| `frontend/` | Vue 3 + TypeScript 控制台（Vite `base: /admin/`） |
| `docs/zh/`、`docs/en/` | MkDocs 文档，中英保持对称 |
| `deploy/` | Kubernetes 清单、compose 文件、部署脚本 |
| `.cspell.json` | 拼写检查词表——文档门闩对未知词报错 |
| `innerdoc/` | 内部设计笔记，不对外发布 |

## 常用命令

```bash
# 构建（跳过测试）
./mvnw clean package -DskipTests

# 本地运行
java -jar target/model-router-*.jar --spring.profiles.active=dev

# 全量 Java 测试（离线可跑；加 skip 参数可加快迭代）
./mvnw test -o

# 聚焦测试 —— 类名用逗号分隔，不支持通配符
./mvnw test -Dtest=FooTest,BarTest -DfailIfNoTests=false -o

# 前端（类型检查 + 构建）
cd frontend && npm ci && npm run type-check && npm run build
```

Windows 下把 `./mvnw` 换成 `.\mvnw.cmd`。

## HTTP 面

| 面 | 前缀 | 协议 | 鉴权 |
|----|------|------|------|
| 客户端 —— OpenAI 原生 | `/v1/**` | OpenAI JSON + SSE（`/v1/chat/completions`、`/v1/models`、`/v1/embeddings`、`/v1/rerank`） | JWT 或 API Key |
| 客户端 —— Anthropic 兼容 | `/v1/messages`、`/v1/messages/count_tokens` | Anthropic Messages（流式 SSE、工具调用） | JWT 或 API Key |
| 管理面 | `/api/**` | JSON，`RouterResponse` 信封 | JWT/API Key + RBAC URL 矩阵 |
| 运维面 | `/actuator/**` | 健康、就绪、`/actuator/prometheus` | 视 profile |

错误体按面区分协议形状：`/v1/messages` 上为 Anthropic 形状的
`{"type":"error","error":{...}}`，其余 `/v1/**` 路径为 OpenAI 形状的
`{"error":{message,type,code}}`，`/api/**` 保持历史形状。`/v1` 面的流式失败会发出协议形状的
error 帧，**并且仍以异常终止流**——失败流绝不补 `[DONE]`，因为客户端把 `[DONE]` 视为成功。

## 认证与授权

- **JWT** —— 头名可配置，默认 `Jairouter_Token`。
- **API Key** —— 头 `X-API-Key`。网关凭据与下游供应商密钥彻底分离：`x-api-key` 只被网关消费、
  绝不透传，实例级下游凭据请放在该实例自己的 `headers.Authorization`。
- **RBAC** —— 数据驱动：63 个权限码、4 个角色模板（`ADMIN` / `OPERATOR` / `USER` / `VIEWER`）、
  URL 规则矩阵 + 方法级 `@PreAuthorize`。未命中任何规则的请求按
  `jairouter.security.rbac.unmatched-policy` 处理，默认 `DENY_ALL`；`ROLE_ADMIN` 恒直通。
  `/v1/**` 需要认证，但有意不进权限矩阵。

## 能力清单

已实现、且有测试套件覆盖：

- **适配器** —— Ollama、vLLM、GPUStack、Xinference、LocalAI、OpenAI、Anthropic（原生）、
  Gemini（原生），以及在 `adapter-definitions` 里声明的任意 OpenAI 兼容厂商。
- **负载均衡** —— 轮询、加权、最少连接、IP 哈希、一致性哈希、EWMA 延迟感知、随机。
- **规则引擎** —— 按模型、服务类型、请求头、客户端 IP、权重、实例标签做条件路由，带命中统计；
  控制台可视化编辑。
- **韧性** —— 实例级熔断、请求级失败转移到另一健康实例、限流（令牌桶 / 漏桶 / 滑动窗口）、
  确定性请求的响应缓存（可选开启，按租户隔离键）。
- **配额** —— 多维度、多窗口账本（`MINUTE`/`HOUR`/`DAY`/`MONTH`），Redis 分布式计数并本地降级
  （`jairouter.quota.enabled`，默认关闭）。
- **记录治理** —— 调用历史三档：仅元数据 / 脱敏摘要 / AES-256-GCM 加密全文；解密端点仅 ADMIN。
- **可观测性** —— Prometheus 指标、OpenTelemetry 链路追踪、慢查询分析、错误追踪、安全告警。
  告警出口被有意限定为 **Prometheus 指标 + 结构化日志**——仓内不实现任何推送通道
  （邮件/Webhook/IM），请在 Alertmanager 或日志平台里接。
- **持久化** —— 默认 H2；当 `DATABASE_URL` 指向 PostgreSQL 时用 PostgreSQL（配 `Flyway` 迁移）；
  多副本部署用 Redis 承载共享状态。
- **多副本** —— 跨副本事件广播、共享限流/配额计数、定时任务的分布式锁（含告警冷却）。
- **控制台** —— `/admin/` 的 Vue 3 界面：服务、实例、适配器、规则、版本与回滚、配额、调用历史、
  链路追踪、告警、API Key、RBAC，深/浅主题，中英双语。

有意**未**实现：语义（向量相似度）缓存，以及任何仓内通知推送。开放中的问题见
[状态与近期工作](#状态与近期工作)。

## 架构

<details>
<summary><b>请求流</b></summary>

```
┌─────────────────────────────────────────────────────────────────┐
│                         Your Application                         │
│                 (OpenAI SDK / LangChain / LlamaIndex)           │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼ OpenAI-Compatible API
┌─────────────────────────────────────────────────────────────────┐
│                         JAiRouter Gateway                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ Routing │ │ Balance │ │  Limit  │ │ Circuit │ │   Auth  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Observability & Persistence                 │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
       ┌──────────────┬───────────┼───────────┬──────────────┐
       ▼              ▼           ▼           ▼              ▼
   ┌───────┐     ┌───────┐   ┌───────┐   ┌──────────┐   ┌───────┐
   │Ollama │     │ vLLM  │   │GPUStack│  │Xinference│   │ OpenAI │
   └───────┘     └───────┘   └───────┘   └──────────┘   └───────┘
```

</details>

<details>
<summary><b>控制台截图</b></summary>

<p align="center">
  <img src="screenshots/dashboard-zh-CN-light.png" alt="JAiRouter 仪表盘" width="720">
  <br/>
  <em>仪表盘 —— 实时的服务、实例与系统指标</em>
</p>

<p align="center">
  <img src="screenshots/service-management-zh-CN-light.png" alt="服务管理" width="720">
  <img src="screenshots/instance-management-zh-CN-light.png" alt="实例管理" width="720">
  <br/>
  <em>服务与实例管理 —— 每个服务的适配器与负载均衡策略</em>
</p>

<p align="center">
  <img src="screenshots/rule-management-zh-CN-light.png" alt="规则引擎" width="720">
  <br/>
  <em>可视化路由规则 —— 命中统计、优先级拖拽与模板</em>
</p>

<p align="center">
  <img src="screenshots/call-history-dashboard-zh-CN-light.png" alt="调用历史分析" width="720">
  <br/>
  <em>调用历史分析 —— 成功率、延迟与 Token 用量趋势</em>
</p>

<p align="center">
  <img src="screenshots/extra-slow-query-analysis-zh-CN-light.png" alt="慢查询分析" width="720">
  <br/>
  <em>慢查询分析 —— 热点、告警状态与操作分布</em>
</p>

<p align="center">
  <img src="screenshots/playground-chat-zh-CN-light.png" alt="AI 演练场" width="720">
  <br/>
  <em>演练场 —— 对话、向量化、重排、语音与图像测试台</em>
</p>

<p align="center">
  <img src="screenshots/quota-config-zh-CN-light.png" alt="配额运行时配置" width="720">
  <br/>
  <em>配额运行时配置 —— 仅重启生效的字段为只读并带角标</em>
</p>

<p align="center">
  <img src="screenshots/quota-monitoring-zh-CN-light.png" alt="配额用量监控" width="720">
  <br/>
  <em>配额监控 —— 后端类型、降级状态与多维度筛选</em>
</p>

<p align="center">
  <img src="screenshots/client-access-zh-CN-light.png" alt="客户端接入指引" width="720">
  <br/>
  <em>客户端接入指引 —— OpenAI 兼容与 Anthropic base URL 的可复制片段</em>
</p>

<p align="center">
  <img src="screenshots/dashboard-zh-CN-dark.png" alt="仪表盘（暗色主题）" width="720">
  <br/>
  <em>内置暗色主题 —— 界面可换肤、双语</em>
</p>

</details>

## 状态与近期工作

**最新发布：v3.4.0（2026-10-21）** —— 跟 `v3.3.6` 的功能版：完成控制台重构（P0-1 → P3）并补齐后端三项能力，带**一次库结构迁移**与**一处授权语义变更**。

| 主题 | 变更内容 |
|------|----------|
| **控制台重构（33 页）** | 侧栏 9 组 40 项 → 7 组 28 项（12 页降级为 tab，**所有 URL 不变**）；侧栏可折叠（200px ↔ 64px，767px 以下强制折叠）；暗色层级修正（页面底色深于卡片）并新增图表专用色；767px 以下三个列表页改卡片列表；33 个页面从装饰性渐变改为中性色板。另新增共享的指标条 / 图表卡 / 图表皮肤、`⌘K` 全局搜索与顶部状态条 |
| **权限语义化（55 → 63）** | 新增 8 个只读权限码，URL 规则表 68 → 75 条：GET 请求不再借用写码。`RolePermissionSeeder` 增加不变式「持有 `:write`/`:manage` 即必须持有对应 `:read`」，升级后内置角色的可见面不会变小 |
| **脱敏配置持久化** | 控制台改的规则 / 敏感词 / PII 模式落库到新表 `sanitization_config`（**Flyway V4**，双方言），启动时数据库优先、无行回落 yml；保存改为「先落库、后重建」，落库失败回滚内存并返回 500 |
| **配额小时 / 月限额** | API Key 新增 4 个限额（`hourlyRequestLimit`、`hourlyTokenLimit`、`monthlyRequestLimit`、`monthlyTokenLimit`；`0` = 不限制），判定顺序 HOUR → DAY → MONTH → MINUTE；账本、详情回显与控制台配额抽屉同步扩展，无账本数据时窗口百分比留空而不是 0% |

`[3.4.0]` 的 changelog 条目见 [更新日志](docs/zh/reference/changelog.md)；以 GitHub Release `v3.4.0` 发布，并触发多架构镜像（`v3.4.0` / `3.4.0` / `latest`）。

**`v3.3.6`（2026-10-19）** 是跟 `v3.3.5` 的补丁版：带一次**库结构迁移**（黑名单原因改稳定码）、
两处授权收紧（`callhistory:view` 不再放行调用历史的写/删；状态持久化的测试写端点在 `prod` 下不存在）、
两个新权限码 + 由它们驱动的页内写门控，以及一轮把「恒空字段」删掉的契约瘦身。

| 主题 | 变更内容 |
|------|----------|
| **数据迁移（#455）** | 黑名单「原因」的预设值改为与语言无关的稳定码（`MALICIOUS_LOGIN`、`ABNORMAL_ACCESS` …），展示时按语言翻译；**Flyway V3** 在 H2 与 PostgreSQL 两条链上把 7 个历史中文值改写为稳定码（幂等；用户自填的自由文本不动） |
| **授权收紧（#514 / #483 / #487）** | `callhistory:view` 不再放行 `POST /api/call-history/record` 与 `DELETE /api/call-history/cleanup`（无规则命中 ⇒ `DENY_ALL`，仅 ADMIN）；`POST /api/state-persistence/test/save-circuit-breaker/{id}` 本版先隔离到 `@Profile("!prod")`、随后**已移除**（测试夹具不该留在业务包里，该链路改由测试包单测覆盖）；配额清理调度器改读 `jairouter.security.quota.cleanup.enabled`（原先读的是没人设置过的 `app.quota.*` 键，运维改不动） |
| **权限码 54 → 55（#454 / #468）** | `monitoring:metrics:write` 让 10 个指标写端点成为「显式授予」而不是靠默认拒绝兜住；`monitoring:slowquery:write` 对 `DELETE /api/monitoring/slow-queries/stats` 做同样的事。`OPERATOR` 按模板（所有 `:read`+`:write`）自动获得这两个能力 |
| **控制台门控（#468）** | 新增 `v-permission` 指令，9 个页面的 33 处页内写控件（含拖拽排序）按写权限显隐；只读角色不再看到「点了必 403」的按钮 |
| **契约瘦身（#501）** | 删除 `PerformanceIssue` / `activeIssues`（恒空）与 `ModelCallStats` 的 `p95ResponseTime` / `p99ResponseTime` / `peakQps`（无生产者）；黑名单统计的 5 个恒空字段改为后端真算；异常事件 `lineNumber` 改为取应用帧行号 |
| **可观测性（#456 / #495）** | 10 个安全指标接到真实埋点（认证 4 + JWT 4 + 脱敏 2）；删掉从不递增的 `sanitization.rule.matches` 计数器与零调用方的 `SanitizationMetrics` 类（另 9 条序列） |
| **文档与门闩（#457 / #547）** | `/v3/api-docs` 曾是空文档（springdoc 扫了不存在的包）；18 篇过期文档逐篇评审并修掉三类过期事实；发布节奏与「把 CI 门闩读对」写进贡献指南 |

`[3.3.6]` 的 changelog 条目见 [更新日志](docs/zh/reference/changelog.md)；以 GitHub Release `v3.3.6` 发布，并触发多架构镜像（`v3.3.6` / `3.3.6` / `latest`）。

**`v3.3.5`（2026-10-17）** 本身是四版合并发布的补丁（`v3.3.2` → `v3.3.5`，149 个提交）：修复 `/v1` 流式 0 字节、
「删除最后一个条目」不落库、`/actuator/health` 因累计 span 丢弃率长期 503；错误码注册表落地，含 #404 限流拆码
（`401` → `429` + `RATE_LIMITED_API_KEY`）与 #405 内置 adapter 的 `403`。

**同批交付：`v3.3.2` / `v3.3.3`**（2026-10-12 → 2026-10-15）。按主题归类：

| 主题 | 变更内容 |
|------|----------|
| **错误码治理（#370 方案 A）** | 新增 `ErrorCode` 注册表并补全到 **62 个「会真正发出的码」**（**#404 拆码后为 63 个**，见下表；每项带**渲染路径**：状态由命名规则 / 认证异常 / 响应体直写 / 错误体直写决定）；中英错误码表按注册表重写且由 CI **双向对账**（文档漏列、文档列了发不出的码，都判红）；`src/main/java` 里**不再有裸码字面量**（约 175 处发出点统一写 `ErrorCode.X.code()`），并加「码字面量守卫」测试防复发。⚠️ 对外契约变更（#403）：`TOKEN_USAGE_FORBIDDEN` → `FORBIDDEN_TOKEN_USAGE` —— 该码写进令牌用量响应体，客户端可见。另有 `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE`，那只是 `AuthorizationException` 的内部常量改名、该码全仓没有发出点（工厂方法只被单元测试调用），客户端不可能收到 |
| 错误码台账 | 注册表当场暴露的两处既有语义问题：`RATE_LIMIT_EXCEEDED` 同码两状态 429/401（#404，**已在 `v3.3.3` 之后拆码解决**，见下）；7 个「只有常量与从未被调用的工厂」的码**已删除**（#406） |
| **错误码状态对齐（#405，`v3.3.3` 打 tag 之后合入）** | 端点状态与规则链推导值按混合方案对齐：`BUILTIN_READONLY` 两端均为 **403**（端点原为 400）、`RESTART_REQUIRED` 两端均为 **400**（规则链原推导 403）；注册表说明、中英错误码表与 `ErrorCodeRegistryTest` 的分歧断言随之更新 |
| 仓库卫生 | 前端生成物不再改脏工作树：`package-lock.json` 按本仓 npm 版本归一化 + 两个生成 `.d.ts` 关掉行尾转换（#408 / #409） |
| **流式（`/v1`）** | **修复：`/v1` 流式返回 `200` 但响应体 0 字节** —— `/v1` 分支用 `exchangeToMono` 把响应体交回了 exchange 作用域之外，WebClient 在框架订阅之前就释放了它。现在协议 error 帧只在**首块之后**补发，因此首块前失败仍按下游真实状态结束（429 仍是 429）。由端到端夹具做回归钉，且夹具现在**同时驱动两个面** |
| 流式基线 | `/v1` 面：首块前失败补状态保真；legacy `/api/**` 语义有意不动；客户端可见基线由探针类钉住 |
| 告警与审计 | 告警冷却改为跨副本排他；审计事件类型一律走枚举名，`parseEventType` 不再静默回退成 `SYSTEM_MAINTENANCE`（枚举 17 → 26）；清掉残余的死告警阈值/出口配置 |
| 配置诚实性（第三批） | 删除 `jairouter.security.audit` 下 7 个无绑定子块、整棵 `jairouter.security.performance`、以及 `audit.extended` 下 14 个无消费方键；脱敏失败改为只终止（错误体归中央处理器）；启动校验不再谎称会生成随机密码 |
| 死代码与形状 | 删除 `SecurityAuditApi`、无生产方的 `SecurityStatisticsResponse`、以及**整层** `common/api` 模块边界层（16 个文件，全仓无实现方与调用方）；控制台 `DashboardOverview` 类型改为接口真实载荷；响应脱敏测试改为真正驱动响应写入路径 |
| 文档与门闩 | README 重写为「事实优先」结构；错误码表按代码重写；`dynamic-config` 与 quick-start 的实例管理示例改为真实接口且逐条实跑；新增 markdown 围栏配平检查器（修 6 个文件 / 10 处）；贡献文档新增「怎么读 CI 门闩」与「发布节奏」判据 |
| 工程与工具 | CI 配额耗尽期间的本地替代门闩；镜像冒烟改轮询容器健康状态；文档版本自测不再抄脚本措辞，且该 workflow 现在会在 `scripts/docs/**` 变更时触发 |

发布标签：`v3.3.6` · `v3.3.5` · `v3.3.4` · `v3.3.3` · `v3.3.2` · `v3.3.1` · `v3.3.0` · `v3.2.2` · `v3.2.1` · `v3.2.0` · `v3.1.2` · `v3.1.1`。
`v3.3.2`–`v3.3.5` 均已随 **v3.3.5** 的 GitHub Release 一并发布（`v3.3.2` 的内容随 `v3.3.3` 一并交付）。

**`v3.3.4`**（随 `v3.3.5` 发布；`v3.3.3` tag 冻结在 `e965f9a8`）；本版相对 `v3.3.3` 的改动按主题归类：

| 主题 | 变更内容 |
|------|----------|
| **限流错误码语义（#404，已裁决）** | 按「429 + 拆新码」拆开两条真实路径：管理面限流器保留 `RATE_LIMIT_EXCEEDED`（429 扁平体），API Key 校验链改用新增的 `RATE_LIMITED_API_KEY`，并由认证过滤器把**异常携带的 429** 透出（`/v1/**` 的 `type` 按状态映射为 `rate_limit_error`）。注册表 62 → 63 个码；客户端不再需要把 401 当限流处理 |
| **错误码收口（#406 / #412 / #414）** | 删除 7 个「只有常量与从未被调用的工厂、全仓无发出点」的码；文档把 `RESOURCE_FORBIDDEN` → `FORBIDDEN_RESOURCE` 的**内部常量改名**与客户端可见的 `TOKEN_USAGE_FORBIDDEN` → `FORBIDDEN_TOKEN_USAGE` 分开写；`management-api` 两表里 4 个幽灵码换成注册表真名 |
| **状态对齐（#405）** | `BUILTIN_READONLY` 两端都是 403（端点原为 400）、`RESTART_REQUIRED` 两端都是 400（规则链原推导 403） |

**`v3.3.5`**（2026-10-16；`v3.3.4` tag 冻结在 `92a134ef`）；本版相对 `v3.3.4` 的改动按主题归类：

| 主题 | 变更内容 |
|------|----------|
| **客户端可见状态修复（#432）** | **请求体缺失**不再变成 **500 + `INTERNAL_ERROR`**：三个 `@RequestBody` 端点（`AdapterConfigController` 的创建/更新、`LoadBalancerManagementController` 的配置更新）改为前置校验，返回 **400 +「请求体无效或缺失」**。这条顺带暴露了一个约束：校验必须放在方法自己的 `try` **之外**，否则会被兜底 `catch (Exception)` 吞掉并重新包成 500 |
| 文档↔代码契约（#436） | 英文侧 TTS/STT 端点前缀纠正为 `/api/v1/audio/*`。先取证：**`/v1/audio/*` 根本没有映射** —— `/v1` 面只提供 models / chat / embeddings / rerank（外加 Anthropic messages 与 debug），所以**错的是英文侧** |
| 门闩 | `快闸` 现也会在 `src/main/resources/config/**` 与 `.github/workflows/**` 变更时触发；此前失效的工作流语法校验器修好（它把 YAML 缩进当成 Python 解析，且含一处恒真的路径自比较）并接进门闩，配了含反证的用例自测 |
| **存储契约修复（#446）** | **「删除最后一个条目」此前静默不生效**，却仍返回 `200 {"success":true}`：`JpaStoreManager.saveConfig` 对空表早退（`config.isEmpty()` → `return`），而上层普遍是「读全量 → 改 → 写全量」，删掉最后一项时正好传空表。资源池侧表现为**立即复活**，可配置 adapter 侧表现为**重启后复活**。已去掉早退、与其余三个 store 实现对齐，并把「空表是合法输入」写进 `StoreManager.saveConfig` 契约。原测试漏掉它是因为把 `StoreManager` mock 了 —— mock 上没有那段早退，「saveConfig 被调用过」恒真 |
| **健康判定修复（#447）** | `/actuator/health` 不再因**生命周期累计**的 span 丢弃率判 `DOWN`：三份 compose 的容器健康检查都用该端点，而该比值越过阈值后只能靠重启归零 ⇒ 容器会被**永久**判 unhealthy，而重启并不解决丢 span（实测现场：`tracing=DOWN`、`issue=数据丢弃率过高: 50.44%`、其余组件全 UP）。现改为 `WARNING`（HTTP 仍 200，数值仍留在 details 供告警规则使用），只有「异步处理器未运行」「内存压力 CRITICAL」「性能监控自身 DOWN」才判 DOWN；顺带修掉同段「后写覆盖前写」导致的问题项丢失 |
| 文档准确性（#448） | `/api/**` 面的错误体形状按实测更正：认证链自己写的 401（`AUTH_MISSING`）与 429（`RATE_LIMITED_API_KEY`）用协议形状，授权层拦下的 403 是 `text/plain` 的 `Access Denied`，只有 controller 抛出的错误才是 `RouterResponse` ⇒ 客户端判据改为「**顶层有没有 `error` 字段**」；同时更正 `claude-code.md` 里「两面都是 `RouterResponse`」这处对 `/v1` 也错的表述 |
| 工程与工具 | 分支清理脚本入库：squash 合并下不再用 ancestry 判「分支是否已合并」（实测 67/80 会被漏掉），改用两条内容判据 + 合成仓库自检 |

本轮发布前的本地验证（冷启动全新 H2 库 → 前端构建 → 打包 → 起 8080）：**前端全量 E2E 414/414 通过**（14 个 spec；修复前 404/407，其中 `crud:pool:delete` 是真缺陷、另 2 例是用例缺少 API Key 前置）；**接口冒烟 51/51 通过**（覆盖 v3.3.1 以来全部对外语义变更：#404 限流拆码、#405 的 403、#432 的 400、`/v1` 双协议错误体、#340 的 `/actuator` 认证面）。

`[3.3.5]` 的 changelog 条目见 [更新日志](docs/zh/reference/changelog.md)；已作为 [GitHub Release v3.3.5](https://github.com/Lincoln-cn/JAiRouter/releases/tag/v3.3.5) 发布，并触发多架构镜像（`v3.3.5` / `3.3.5` / `latest`）。

### 开放台账

**本文件刻意不再维护一份手写的 issue 编号清单。** 早先版本这么做，结果是**写出来几小时内就过期**。
权威台账在 issue 跟踪器：**[开放 issue →](https://github.com/Lincoln-cn/JAiRouter/issues?q=is%3Aissue+is%3Aopen)**

下面用来说明台账里**通常有哪些类**的条目（**仅示意、不是当前清单**，当前集合请点上面的链接）：

- **只有单测覆盖的行为** —— 真实运行态接线没有端到端证据的路径（例如告警链路与流式错误路径）。
- **会撒谎的形状** —— 声明的类型/接口与运行态实际产出不一致，工具因此无法约束它。
- **面向运维的错误文案** —— 启动提示或文档描述了代码并不会做的事。
- **靠惯性留着的死面** —— 没有消费方的配置键、DTO 或接口。
- **文档欠账** —— 到期待复审，或渲染已经坏掉的页面。

若某件事既不在上面这些类里、也不在跟踪器里，请按**未知**对待，而不要假定它可用。

## 文档

| 资源 | 链接 |
|------|------|
| 完整文档 | https://jairouter.com |
| 部署 | https://jairouter.com/deployment/ |
| 配置 | https://jairouter.com/configuration/ |
| 监控 | https://jairouter.com/monitoring/ |
| 交互式 API 文档 | `http://localhost:8080/swagger-ui`（dev profile） |

## 为什么选 JAiRouter？

- **对比直连后端** —— 没有共享的韧性、没有中心化凭据、没有用量可见性。JAiRouter 给你一个端点、
  自动失败转移与完整审计轨迹。
- **对比 One-API / new-api** —— 它们面向共享账号的分发与计费。JAiRouter 是**你自己**推理集群前面的
  那层网关：多实例均衡、熔断、规则路由、自托管 Ollama/vLLM/GPUStack 的 RBAC 与可观测性。两者互补。
- **对比 Nginx** —— Nginx 是通用 Web 服务器；JAiRouter 为模型负载而建：OpenAI 兼容路由、
  模型感知均衡、流式语义。
- **对比 LangChain** —— LangChain 是应用框架；JAiRouter 是它下面的基础设施层。

| 能力 | JAiRouter | Nginx | One-API | LangChain |
|------|:---------:|:-----:|:-------:|:---------:|
| OpenAI 兼容 | ✅ | ❌ | ✅ | ✅ |
| 负载均衡 | ✅ | ✅ | ✅ | ❌ |
| 熔断 | ✅ | ❌ | ❌ | ❌ |
| 限流 | ✅ | ✅ | ✅ | ❌ |
| Web 控制台 | ✅ | ❌ | ✅ | ❌ |
| 配置热重载 | ✅ | ❌ | ✅ | ❌ |
| 版本管理 | ✅ | ❌ | ❌ | ❌ |
| OpenTelemetry | ✅ | ❌ | ❌ | ✅ |

## 参与贡献

欢迎贡献——见[贡献指南](https://jairouter.com/development/contributing/)。

提 PR 前请先跑：

```bash
./mvnw test -o                       # 约 400 个测试类、约 4,100 个测试方法，必须保持全绿
cd frontend && npm run type-check    # 前端类型门闩
```

几条仓内约定：提交信息用 `<type>(<scope>): <summary>`；每个修复/功能分支都指向 `master` 并以
squash 方式合并；文档同时存在于 `docs/zh/` 与 `docs/en/` 且要求保持对称；文档会按 `.cspell.json`
做拼写检查，新技术名词可能需要先加进词表。

```bash
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd JAiRouter
./mvnw clean package -DskipTests
java -jar target/model-router-*.jar
```

## 支持

- **文档**：https://jairouter.com

### 该发 Issue 还是 Discussion？

GitHub 给了我们两个渠道，我们有意分开，避免信息丢失。按**你手上有什么**来选：

| 你有什么 | 发到哪里 | 我们怎么处理 |
|----------|----------|--------------|
| **可复现的缺陷** —— 崩溃、结果错误、回归、文档写错 | [**Issues**](https://github.com/Lincoln-cn/JAiRouter/issues) | 快速分诊；确认的缺陷会被修复或排期 |
| **需求或想法** —— 功能请求、新适配器、设计讨论、使用提问 | [**Discussions**](https://github.com/Lincoln-cn/JAiRouter/discussions) | 定期梳理，收集与投票后并入路线图 |

**一句话**：缺陷发 **Issues**；需求或提问发 **Discussions**。

> 拿不准？先发 Discussions——如果最后确认是可复现的缺陷，我们会帮你转到 Issues。

Discussions 按分类组织：

| 分类 | 用途 |
|------|------|
| 💡 Ideas | 功能请求与改进建议 |
| 🗳 Polls | 社区对方向的投票 |
| 🙏 Q&A | 使用问题互助 |
| 🙌 Show and tell | 分享你基于 JAiRouter 做的东西 |
| 💬 General | 其它 |
| 📣 Announcements | 维护者的发布说明 |

## 许可

JAiRouter 基于 [Apache 2.0 许可](LICENSE) 发布。

---

<p align="center">
  <strong>如果 JAiRouter 帮你省了时间，<a href="https://github.com/Lincoln-cn/JAiRouter/stargazers">在 GitHub 上点个 Star</a> ⭐</strong>
  <br/>
  <em>发现 bug？开 issue。有想法或疑问？开 discussion。</em>
</p>

<p align="center">
  Maintained by Lincoln-cn · Apache-2.0
</p>

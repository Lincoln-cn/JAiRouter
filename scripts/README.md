# JAiRouter 脚本目录

本目录包含项目所有的脚本文件，按功能分类管理。

## 目录结构

```
scripts/
├── audit/                # 静态面审计探测器与其输出
├── build/                # 构建部署脚本
├── demo/                 # 5 分钟演示（对本地起的网关跑一遍主链路）
├── dev/                  # 开发辅助脚本
├── docs/                 # 文档检查器与它们的自测（CI 文档门闩跑的就是这些）
├── knowledge-governance/ # innerdoc/ 私有知识库治理工具
├── migration/            # 数据迁移脚本
├── monitoring/           # 监控部署脚本
├── release/              # 发布助手（版本号 / tag / 发布说明）
├── test/                 # 测试、本地门闩与分支清理
└── tools/                # 杂项工具
```

根目录下的 `monitoring-stack.sh` 是 v1.9.5 时期的入口，尚未并入 `monitoring/`；新用法看 `monitoring/`。

## 脚本分类说明

### build/ - 构建部署

| 脚本 | 说明 |
|------|------|
| `build-and-deploy.sh` | 标准构建部署脚本，完整的构建部署流程 |
| `deploy-security.sh/ps1` | 安全功能部署脚本 |
| `docker-build.sh/ps1` | Docker 镜像构建 |
| `docker-build-china.sh/ps1` | Docker 镜像构建（国内镜像加速） |
| `docker-run.sh/ps1` | Docker 容器运行 |

### dev/ - 开发辅助

| 脚本 | 说明 |
|------|------|
| `dev-start.sh` | 快速启动开发环境 |
| `run-dev.sh/ps1` | 快速构建并运行应用 |
| `quick-fix-assets.sh` | 快速修复静态资源版本问题 |
| `quick-test-fixes.sh` | 快速测试 JWT 安全修复 |

### docs/ - 文档检查器

CI 的文档门闩跑的就是这里的检查器：改文档、或改这些脚本本身，都会触发它们。
**改检查器必须先跑同名自测** `test_<脚本名>.py` —— 自测里带反向用例，确保探测器真的能抓出问题。

| 脚本 | 作用 | 跑在哪 |
|------|------|--------|
| `check-links.py` | 死链检查（含外链） | `docs-quality.yml` |
| `check-fence-parity.py` | Markdown 围栏配平（分隔符为奇数 ⇒ 自错位点起整篇被当代码渲染） | `docs-quality.yml` |
| `check-error-codes.py` | 文档里出现的错误码必须在代码里有定义 | `docs-quality.yml` |
| `check-api-endpoints.py` | 文档里写的端点必须真的存在 | `docs-quality.yml` |
| `check-config-imports.py` | 文档里的配置键必须能绑定到真实字段 | `docs-quality.yml` |
| `check-capability-claims.py` | 文档里的能力声明与实现面对账 | `gates-fast.yml` |
| `check-docs-sync.py` | 文档与代码的同步面 | `docs-code-sync.yml` / `docs-content-sync.yml` |
| `docs-version-manager.py` | 版本信息块扫描 / 写入 / 过期提醒（含 `--mark-reviewed`） | `docs-version-management.yml` |
| `validate-nav-files.py` | `mkdocs.yml` 的 nav 与真实文件一致 | `docs-version-management.yml` |
| `test_*.py` | 上面各检查器的自测 | 与各自 workflow 同批 |

`docs-manager.*` / `validate-docs-config.py` / `validate-docs-sync.py` / `validate-structure.ps1` /
`check-links.ps1` / `fix-links.py` / `test-workflow-paths.py` 是早期版本或本地辅助，**未接 CI**，需要手工跑。

### migration/ - 数据迁移

| 脚本 | 说明 |
|------|------|
| `migrate_database.sh` | 数据库迁移脚本 |
| `migrate-to-security.sh/ps1` | 安全功能迁移脚本 |

### monitoring/ - 监控部署

| 脚本 | 说明 |
|------|------|
| `setup-monitoring.sh/ps1` | 监控栈一键部署（Prometheus、Grafana 等） |
| `run-monitoring-tests.sh/ps1` | 监控系统集成测试 |

### test/ - 测试脚本

各类功能测试和验证脚本，包括：
- E2E 测试
- API 集成测试
- 版本管理测试
- 安全功能测试
- 熔断器/限流器测试

| 脚本 | 说明 |
|------|------|
| `mock-model-server.mjs` | OpenAI 兼容的 mock 模型服务，供本地联调与路由/负载均衡测试使用（见下） |
| `local-gates.ps1/.cmd` | 本地复刻 CI 门闩（GitHub Actions 不可用期间使用） |
| `api-smoke.mjs` | 接口层冒烟（可复跑资产，见下）：直接断言对外状态码与错误体形状 |
| `branch-cleanup.ps1/.sh/.cmd` | 分支清理与「合并不干净」梳理（见下） |
| `test-branch-cleanup.ps1` | `branch-cleanup.ps1` 的自检：用合成 git 仓库复现各条判据 |
| `e2e_test.sh` / `e2e_test_with_login.sh` | 端到端冒烟，需真实运行实例 |
| `generate-tracing-data.py/.ps1/.sh` | 生成链路追踪样本数据（用法见 `README-tracing-data-generator.md`） |
| `test-audit-functionality.*` / `test-integrated-audit.*` | 审计功能验证，需真实运行实例 |
| `test-response-cache-smoke.ps1` | 响应缓存冒烟 |
| `test-deployment.*` / `test-security-deployment.*` | 部署与安全部署验证 |

### 接口冒烟（api-smoke）

单元测试与前端 E2E 都不直接断言「线上返回什么状态码、错误体是什么形状」，而这恰恰是客户端最先感知的面。本仓的对外语义却在持续变化（`#404` API Key 限流 401→429 并拆码、`#405` `BUILTIN_READONLY` 400→403、`#432` 缺请求体 500→400、`#340` `/actuator` 不再整段豁免、`#448` `/api/**` 的错误体形状），所以单独留一份接口层冒烟。

```bash
# 先起服务（dev profile 即可），然后：
node scripts/test/api-smoke.mjs
node scripts/test/api-smoke.mjs http://127.0.0.1:8080        # 或 E2E_BASE_URL
E2E_USER=admin E2E_PASS=*** node scripts/test/api-smoke.mjs  # 非出厂口令时
```

覆盖哪些面（每项都是「应当成立」的契约）：

| 面 | 断言 |
|---|---|
| 认证入口 | 无凭据 / 坏凭据 → **401**（不是 403、不是重定向） |
| `/actuator` 认证面 | `health` / `info` / `prometheus` 匿名可达；`env` / `beans` / `configprops` 无凭据 **401**、带 ADMIN **200** |
| `/v1` 协议错误体 | OpenAI 形状 `{"error":{message,type,code}}` 且 `type=authentication_error`；`/v1/messages` 为 Anthropic 形状 |
| `/api/**` 形状 | 认证链写出的 401 是协议形状；授权层 403 是 `text/plain`；controller 抛的 403 是 `RouterResponse` |
| 内置 adapter 只读 | `PUT`/`DELETE` 命中内置 adapter → **403 + `BUILTIN_READONLY`** |
| 缺失请求体 | 三个 `@RequestBody` 端点 → **400**（不得是 500）；非法 JSON → 400 |
| API Key 全链 | 建 Key → `/v1/models` 200 → 限流后 **429 + `RATE_LIMITED_API_KEY`**，且 `/v1` 的 `type=rate_limit_error` → 清理 |
| **RBAC 读/写矩阵** | 临时低权限账号（`roles:["USER"]`）：`/config/quota` 与 `/monitoring/slow-queries` 的**读 200 / 写 403**；且**未绑定规则的写端点**必须 fail-closed（低权限 403、ADMIN 200）→ 清理 |

约束与边界：

* 只依赖 Node 内置 `fetch`，**无 npm 依赖**，因此不进 Maven / vitest 门闩也能跑；
* 会创建并删除一个临时 API Key（`rateLimitPerMinute: 5`）用于验证限流，跑完自行清理；
* **只断言「应当成立」的契约**：已登记未修的问题（`#454` 的 405、`#456` 的 custom-tags 谎报成功、`#457` 的空 OpenAPI）刻意不断言，避免把缺陷固化成期望值；
* 它需要**真实运行**的实例，而 CI 目前没有这一步（`#454` 建议把接口对账固化进守卫）。

### 分支清理（branch-cleanup）

清理已在主线落地的本地/远端分支，并把「合并得不干净、有遗留」的分支单独梳理成清单。

**为什么不能直接用 `git branch --merged`**：本仓 PR 用 **squash** 合并，分支提交**从未成为主线祖先**。
对 80 个本地分支实测，ancestry 判据只认出 6 个，而内容早已进主线的有 67 个 ⇒ 据此删除会漏掉九成。

脚本改用两条**内容判据**：

| 判据 | 做法 | 含义 |
|------|------|------|
| A（最强） | `git merge-tree --write-tree --merge-base=<mb> <base> <branch>` 的结果树是否**等于**基线树 | 等价 ⇒ 分支净增量已被完全吸收 |
| B（次强） | 分支相对 merge-base 的改动在基线里的**存活率**（双向：新增行要在、删除行要不在） | 整份文件零存活 / 新文件缺失 ⇒ 内容没落地 |

分类结果：`contained`（判据 A，可删）· `patch`（判据 B，可删）· `churn`（改动已进基线、后续被改写，可删）
· `review` / `orphan`（**禁止直接删**，报告列出哪几份文件没进基线）· `has-open-pr` · `protected-*`。

**安全默认：dry-run**，只打印命令；必须显式 `-Apply`（或 `--apply`）才真正删除。删除用 `git branch -D`
（必需：squash 合并的分支用 `-d` 会被 git 拒绝），且当前分支 / worktree 占用 / 基线 / `-Protect` 匹配的分支一律不动。

```bash
# 1) 先看（默认不改动任何分支）
powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1
scripts/test/branch-cleanup.cmd                    # 或绕过执行策略
scripts/test/branch-cleanup.sh                     # Linux/macOS

# 2) 出报告给后续开发用（markdown 可直接贴 issue；同时落盘 md + json）
powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -IncludeRemote -Format Markdown -OutDir .tmp-branch-cleanup

# 3) 确认无误后执行（远端服务端删除需再显式加 -DeleteRemote）
powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -Apply
powershell -ExecutionPolicy Bypass -File scripts/test/branch-cleanup.ps1 -IncludeRemote -Apply -DeleteRemote
```

退出码：`0`=无待删；`2`=有可删候选；`3`=存在「需人工确认」项；`1`=前置条件不满足。

**自检**（改了判据务必先跑；用合成仓库复现 6 种情形，全部断言必须绿）：

```bash
powershell -ExecutionPolicy Bypass -File scripts/test/test-branch-cleanup.ps1
```

> ⚠️ 已知环境缺陷（偶发，非脚本错误）：Windows PowerShell 5.1 + 本机 AMSI 组合下，长脚本可能抛
> `AccessViolationException ... AmsiScanBuffer` 并直接结束进程；**同一命令重跑即可通过**
> （与 `local-gates.ps1` 同款问题）。自检脚本已内置重试。

### audit/ - 静态面审计

每个审计轴一个**自校准**探测器 + 一份它的输出（`out/`）。结论落在 issue 台账，方法与覆盖边界见
`scripts/audit/README.md`；这些探测器只读，不改运行时行为。

| 脚本 | 说明 |
|------|------|
| `detect-backend-dead-code.py` | 后端零引用类型、未使用的私有方法、注入但从不读取的字段 |
| `detect-frontend-dead-code.py` | 前端零引用导出、无调用方的 api 函数、不可达路由视图 |
| `analyze-config-keys.py` | 配置键对账：无绑定类 / 无读取方 / 代码读了但 yml 没有 |
| `reconstruct-consumer-paths.py` | 把 `@ConditionalOnProperty(prefix+name)` / `@Value` / `getProperty` 还原成完整键路径，防漏判 |
| `reconcile-frontend-api.py` | 前端调用与后端端点的对账 |
| `reconcile-contract-shapes.py` | 契约形状对账（前端声明了后端从不发送的字段） |
| `detect-hardcoded-i18n.py` | 未走 vue-i18n 的用户可见中文文案 |
| `detect-ui-states.py` | 界面态：缺加载态、缺二次确认、页内权限门控等 |
| `osv-scan.py` | 依赖漏洞扫描（`dependency-scan.yml`，基线 `osv-baseline.json`） |

### demo/ - 演示

| 脚本 | 说明 |
|------|------|
| `run-demo.mjs` | 5 分钟演示：先 `docker compose -f deploy/demo/docker-compose.yml up -d`，再跑一遍主链路，每节打印「做了什么 / 预期 / 实际」，失败会指出是哪一步 |

### knowledge-governance/ - 知识库治理

治理 `innerdoc/` 私有知识库的健康状态（扫描 / 重复 / 过时 / 归档 / 版本），详见该目录 `README.md`。

### release/ - 发布助手

| 子命令 | 说明 |
|------|------|
| `check` | 只读校验：七处版本号一致 + changelog 有当前版本条目 + tag 未占用 |
| `bump` | 一次改齐 pom / `frontend/package.json` / README×2 / changelog×2 / roadmap×2 |
| `tag` | 打 tag + 推 tag + 创建 GitHub Release（校验发布说明首行是 `# `） |
| `notes` | 从 changelog 生成发布说明骨架 |

默认 dry-run，加 `--write` 才真正写盘；发布流程本身见 `docs/zh/development/contributing.md`。

### tools/ - 工具脚本

| 脚本 | 说明 |
|------|------|
| `generate_sitemap.py` | 生成站点地图（`update-sitemap.yml` 用） |
| `validate-jwt-persistence-config.sh` | JWT 持久化配置验证 |
| `dismiss-csrf-alerts.ps1` | 批量关闭 CSRF 相关告警（GitHub 侧，本地一次性工具） |

## 常用脚本使用

### 标准构建部署

```bash
# 完整构建部署（推荐用于生产环境）
./scripts/build/build-and-deploy.sh

# 仅构建和部署前端
./scripts/build/build-and-deploy.sh -f

# 验证部署状态
./scripts/build/build-and-deploy.sh -v
```

### Docker 操作

```bash
# 构建镜像
./scripts/build/docker-build.sh

# 国内镜像加速构建
./scripts/build/docker-build-china.sh

# 运行容器
./scripts/build/docker-run.sh [环境] [版本]
```

### 开发调试

```bash
# 快速启动开发环境
./scripts/dev/dev-start.sh

# 快速修复静态资源问题
./scripts/dev/quick-fix-assets.sh
```

### 本地 mock 模型服务

JAiRouter 的实例健康检查是「socket 探测上游 host:port」，且路由、负载均衡、
熔断、配额等链路都需要一个**真实可达**的上游。当手边没有可用的推理服务
（或只想验证网关自身行为）时，用本 mock 起一个 OpenAI 兼容端点即可跑通完整链路。

零依赖（仅需 Node），支持流式 SSE，并对每条请求打日志（带 `--tag`），
便于观察请求实际落到了哪个实例。

```bash
# 默认监听 127.0.0.1:9099
node scripts/test/mock-model-server.mjs

# 多实例场景用 tag 区分
node scripts/test/mock-model-server.mjs --port 9099 --tag A
node scripts/test/mock-model-server.mjs --port 9100 --tag B

# 模拟慢上游 / 模拟故障（用于验证熔断与降级）
node scripts/test/mock-model-server.mjs --latency 800
node scripts/test/mock-model-server.mjs --fail-rate 0.5
```

支持端点：`GET /v1/models`、`POST /v1/chat/completions`（含 `stream=true` 的 SSE）、
`/v1/embeddings`、`/v1/rerank`、`/v1/audio/speech`、`/v1/audio/transcriptions`、
`/v1/images/generations`、`/v1/images/edits`。其余路径一律返回 200，保证健康检查通过。

在 JAiRouter 里把它挂成实例（`X-Jairouter-Token` 为控制台登录后的 JWT）：

```bash
curl -X POST http://127.0.0.1:8080/api/config/instance/chat \
  -H "X-Jairouter-Token: <admin-jwt>" -H 'Content-Type: application/json' \
  -d '{"name":"qwen3.8-flash","baseUrl":"http://127.0.0.1:9099",
       "path":"/v1/chat/completions","weight":1,"status":"active","adapter":"gpustack"}'
```

实例健康状态转为 `HEALTHY`（约 20~30s，取决于健康检查周期）后即可发起请求：

```bash
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "X-Jairouter-Token: <admin-jwt>" -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.8-flash","messages":[{"role":"user","content":"hi"}],"stream":false}'
```

用完后删除该实例：

```bash
curl -X DELETE http://127.0.0.1:8080/api/config/instance/chat/<instanceDbId> \
  -H "X-Jairouter-Token: <admin-jwt>"
```

### 监控部署

```bash
# 部署监控栈
./scripts/monitoring/setup-monitoring.sh

# 运行监控测试
./scripts/monitoring/run-monitoring-tests.sh
```

## 注意事项

1. **脚本执行权限**: 确保脚本有执行权限 `chmod +x scripts/**/*.sh`
2. **跨平台**: `.sh` 文件用于 Linux/macOS，`.ps1` 文件用于 Windows PowerShell
3. **强制刷新浏览器**: 部署后务必使用 `Ctrl+Shift+R` 强制刷新
4. **一次性脚本不入库**: 只在某一次迁移/发版期间用到的脚本用完即删（v1.8.1 发布、git tag 合并、包迁移三类已按此清理），需要留痕就写进 PR 或 issue，不要留在 `scripts/` 里 —— 留在仓库里的脚本读者会默认它现在还能用

## 静态资源版本问题

如果页面显示旧版本，这是因为 Maven 复制前端资源时不会自动清理旧文件。

**解决方案**:
```bash
./scripts/dev/quick-fix-assets.sh
```

或手动清理:
```bash
rm -rf target/classes/static/admin/assets/*
./scripts/build/build-and-deploy.sh -f
```

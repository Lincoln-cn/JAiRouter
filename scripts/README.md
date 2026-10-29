# JAiRouter 脚本目录

本目录包含项目所有的脚本文件，按功能分类管理。

## 目录结构

```
scripts/
├── build/          # 构建部署脚本
├── dev/            # 开发辅助脚本
├── docs/           # 文档管理脚本
├── migration/      # 数据迁移脚本
├── monitoring/     # 监控部署脚本
├── test/           # 测试脚本
└── tools/          # 工具脚本
```

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

### docs/ - 文档管理

文档相关的管理和验证脚本。

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

### tools/ - 工具脚本

| 脚本 | 说明 |
|------|------|
| `fix_frontend_complete.py` | 前端配置完整修复 |
| `fix_frontend_independent_config.sh` | 前端独立配置修复 |
| `quick_fix_frontend.sh` | 前端快速修复 |
| `generate_sitemap.py` | 生成站点地图 |
| `validate-jwt-persistence-config.sh` | JWT 持久化配置验证 |

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

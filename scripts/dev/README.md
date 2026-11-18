# 开发环境脚本

Windows / Linux / macOS 通用，全部基于仓库自带的 Maven Wrapper（`mvnw.cmd` / `mvnw`），不要求全局安装 Maven。

## 启动

```powershell
# Windows PowerShell
.\scripts\dev\run-dev.ps1                                   # 构建（含前端）+ 启动，8080
.\scripts\dev\run-dev.ps1 -SkipBuild                        # 已构建过，直接启动
.\scripts\dev\run-dev.ps1 -Port 8081 -AdminPassword 'xxx'
.\scripts\dev\run-dev.ps1 -SkipFrontend                     # 跳过前端构建：/admin/ 无页面，只有接口
```

```bash
# Linux / macOS / Git Bash
./scripts/dev/run-dev.sh                                    # 构建（含前端）+ 启动，8080
./scripts/dev/run-dev.sh --skip-build
./scripts/dev/run-dev.sh --port 8081 --admin-password 'xxx'
./scripts/dev/dev-start.sh                                  # 后端 + Vite HMR 一起起
./scripts/dev/dev-start.sh --no-frontend --backend-port 8081
```

- 控制台：`http://localhost:8080/admin/`（后端集成静态资源，跑的是构建产物）
- 需要热更新时用 `dev-start.sh`：Vite 在 3000（被占用时自动 +1），代理到后端
- Swagger：`http://localhost:8080/swagger-ui.html`
- 空库首次启动的 admin 口令来自 `INITIAL_ADMIN_PASSWORD`，dev 默认 `ChangeMeOnFirstStartup123456`；库已存在账号时该变量不再生效，改口令要去控制台或直连 H2

## 三个曾经踩过的坑（脚本已修）

| 坑 | 表现 | 原因 |
|---|---|---|
| 用 `-Pfast` 构建 | 服务起来了但 `/admin/` 404 | `fast` profile 里 `frontend.build.skip=true`，jar 里没有控制台静态资源 |
| 端口写 31080 | 浏览器打不开，以为没启动 | 早期脚本默认端口与前端代理/文档不一致 |
| `java -jar -Dserver.port=…` | 端口设置不生效 | JVM 参数写在 `-jar` 之后会被当成应用参数；应放前面或改用 `--server.port=` |

## 历史脚本

`quick-fix-assets.sh`、`quick-test-fixes.sh` 是当时一次性排障脚本，已加标注，**不建议日常使用**：

- `quick-fix-assets.sh`：清空 `target/classes/static/admin` 后重编译，用于"页面版本错乱"，会被 `run-dev.sh` 的新构建取代
- `quick-test-fixes.sh`：验证某次 JWT 修复后的错误体形状，断言针对当时的响应格式

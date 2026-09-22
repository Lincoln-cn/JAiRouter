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

在 JAiRouter 里把它挂成实例（`Jairouter_Token` 为控制台登录后的 JWT）：

```bash
curl -X POST http://127.0.0.1:8080/api/config/instance/chat \
  -H "Jairouter_Token: <admin-jwt>" -H 'Content-Type: application/json' \
  -d '{"name":"qwen3.8-flash","baseUrl":"http://127.0.0.1:9099",
       "path":"/v1/chat/completions","weight":1,"status":"active","adapter":"gpustack"}'
```

实例健康状态转为 `HEALTHY`（约 20~30s，取决于健康检查周期）后即可发起请求：

```bash
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Jairouter_Token: <admin-jwt>" -H 'Content-Type: application/json' \
  -d '{"model":"qwen3.8-flash","messages":[{"role":"user","content":"hi"}],"stream":false}'
```

用完后删除该实例：

```bash
curl -X DELETE http://127.0.0.1:8080/api/config/instance/chat/<instanceDbId> \
  -H "Jairouter_Token: <admin-jwt>"
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

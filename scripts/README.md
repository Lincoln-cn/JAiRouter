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
# JAiRouter 构建部署脚本使用说明

## 脚本列表

### 1. build-and-deploy.sh - 标准构建部署脚本

**位置**: `scripts/build-and-deploy.sh`

**功能**: 完整的构建部署流程，避免静态资源版本混乱

**用法**:
```bash
# 完整构建部署（推荐用于生产环境）
./scripts/build-and-deploy.sh

# 仅构建和部署前端（开发调试，不重启服务）
./scripts/build-and-deploy.sh -f

# 跳过构建，仅清理和复制资源
./scripts/build-and-deploy.sh -s

# 仅验证当前部署状态
./scripts/build-and-deploy.sh -v
```

**执行流程**:
1. 停止正在运行的 Spring Boot 服务
2. 清理旧的静态资源文件（防止版本混乱）
3. 构建前端（npm run build）
4. 复制前端资源到 target/classes/static/admin
5. 编译后端（mvn clean compile）
6. 启动 Spring Boot 服务
7. 验证部署状态

### 2. quick-fix-assets.sh - 快速修复脚本

**位置**: `scripts/quick-fix-assets.sh`

**功能**: 快速解决静态资源版本混乱问题

**用法**:
```bash
# 一键修复静态资源问题
./scripts/quick-fix-assets.sh
```

**适用场景**:
- 页面显示旧版本（按钮数量不对）
- 静态资源文件版本混乱
- 需要快速重置到最新版本

## 常见问题

### Q: 为什么页面会显示旧版本？

**原因**: Maven 复制前端资源时不会自动清理旧文件，导致多个版本的 JS/CSS 文件共存，浏览器可能加载到旧版本。

**解决方案**:
1. 使用标准脚本：`./scripts/build-and-deploy.sh`
2. 或使用快速修复：`./scripts/quick-fix-assets.sh`

### Q: 如何验证部署是否成功？

```bash
# 方法1: 使用脚本验证
./scripts/build-and-deploy.sh -v

# 方法2: 手动检查
ls -la target/classes/static/admin/assets/InstanceManagement-*.js
# 应该只显示1个文件

# 方法3: 检查按钮数量
curl -s http://localhost:8080/admin/assets/InstanceManagement-*.js | grep -o "限流器配置\|熔断器配置" | wc -l
# 应该显示 >= 4
```

### Q: 开发时如何快速更新前端？

```bash
# 仅更新前端，不重启服务
./scripts/build-and-deploy.sh -f

# 然后强制刷新浏览器 (Ctrl+Shift+R)
```

## 最佳实践

### 开发环境
```bash
# 修改前端代码后
./scripts/build-and-deploy.sh -f
```

### 生产环境
```bash
# 完整部署流程
./scripts/build-and-deploy.sh
```

### 出现问题时
```bash
# 快速修复
./scripts/quick-fix-assets.sh
```

## 注意事项

1. **强制刷新浏览器**: 部署后务必使用 `Ctrl+Shift+R` (Windows/Linux) 或 `Cmd+Shift+R` (Mac) 强制刷新
2. **禁用缓存**: 开发时建议在浏览器开发者工具中勾选 "Disable cache"
3. **无痕模式**: 如果问题持续，尝试使用无痕模式访问

## 技术细节

### 静态资源版本混乱的根本原因

Vue 项目构建后会生成带哈希值的文件名（如 `InstanceManagement-CnP0C_dz.js`），每次构建哈希值都会变化。Maven 的 `copy-resources` 插件会复制新文件，但不会删除旧文件，导致：

```
assets/
├── InstanceManagement-BkKBX1kb.js  (旧版本)
├── InstanceManagement-CnP0C_dz.js  (新版本)
└── ...
```

浏览器可能加载到旧版本，导致页面显示不一致。

### 脚本的解决方案

1. **清理阶段**: 删除 `target/classes/static/admin/assets/*` 所有文件
2. **复制阶段**: 只复制最新构建的文件
3. **验证阶段**: 检查 InstanceManagement 文件数量，如果多于1个则删除旧的

这样可以确保只有一个版本的静态资源文件，避免版本混乱。

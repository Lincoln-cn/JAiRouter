# Scripts 目录说明

本目录包含 JAiRouter 项目的各种脚本工具。

## 文档管理脚本

### 统一管理脚本（推荐使用）

- **`docs-manager.ps1`** - Windows PowerShell 版本的统一文档管理脚本
- **`docs-manager.sh`** - Linux/macOS 版本的统一文档管理脚本  
- **`docs-manager.cmd`** - Windows 批处理版本的基础文档管理脚本

这些脚本整合了以下功能：
- 启动文档服务器
- 检查链接有效性
- 修复无效链接
- 管理文档版本
- 验证文档结构
- 检查文档同步性

**使用方法：**
```bash
# Windows PowerShell
.\scripts\docs-manager.ps1 <命令> [选项]

# Linux/macOS
./scripts/docs-manager.sh <命令> [选项]

# Windows 批处理
.\scripts\docs-manager.cmd <命令> [选项]
```

### 核心功能脚本

以下脚本提供具体的功能实现，通常由统一管理脚本调用：

#### 链接检查和修复
- **`check-links.ps1`** - PowerShell 版本的链接检查脚本
- **`check-links.py`** - Python 版本的链接检查脚本（功能最完整）
- **`fix-links.py`** - Python 版本的链接修复脚本

#### 文档同步检查
- **`check-docs-sync.ps1`** - PowerShell 版本的文档同步检查脚本
- **`check-docs-sync.py`** - Python 版本的文档同步检查脚本

#### 版本管理
- **`docs-version-manager.ps1`** - PowerShell 版本的文档版本管理脚本
- **`docs-version-manager.py`** - Python 版本的文档版本管理脚本
- **`docs-version-manager.sh`** - Shell 版本的文档版本管理脚本

#### 验证工具
- **`validate-docs-config.py`** - MkDocs 配置文件验证脚本
- **`validate-structure.ps1`** - 文档结构验证脚本
- **`validate-docs-sync.py`** - 文档同步验证脚本

#### 配置文件
- **`docs-sync-config.yml`** - 文档同步检查配置文件

## 其他脚本

### Docker 相关
- **`docker-build.ps1`** / **`docker-build.sh`** - Docker 镜像构建脚本
- **`docker-build-china.ps1`** / **`docker-build-china.sh`** - 中国优化的 Docker 构建脚本
- **`docker-run.ps1`** / **`docker-run.sh`** - Docker 容器运行脚本

### 监控相关
- **`setup-monitoring.ps1`** / **`setup-monitoring.sh`** - 监控环境设置脚本
- **`run-monitoring-tests.ps1`** / **`run-monitoring-tests.sh`** - 监控测试脚本

### 部署测试
- **`test-deployment.ps1`** / **`test-deployment.sh`** / **`test-deployment.cmd`** - 部署测试脚本

## 使用建议

1. **日常开发**: 使用统一管理脚本 `docs-manager.*`
2. **CI/CD 集成**: 可以直接调用具体的功能脚本
3. **自定义需求**: 参考现有脚本创建自定义脚本

## 脚本维护

- 优先维护 Python 版本的脚本（功能最完整）
- PowerShell 版本适用于 Windows 环境
- Shell 版本适用于 Linux/macOS 环境
- 批处理版本提供基础功能支持

## 依赖要求

### 文档管理脚本
- Python 3.x
- pip / pip3
- PowerShell (Windows)
- Bash (Linux/macOS)

### Python 依赖包
- PyYAML (用于 YAML 配置解析)
- 其他依赖见各脚本文件头部注释

## 故障排查

如果脚本执行失败，请检查：

1. **权限问题**: 确保脚本有执行权限
   ```bash
   chmod +x scripts/*.sh
   ```

2. **依赖缺失**: 安装必要的依赖包
   ```bash
   pip install pyyaml
   ```

3. **路径问题**: 在项目根目录执行脚本
   ```bash
   cd /path/to/jairouter
   ./scripts/docs-manager.sh serve
   ```

4. **Python 版本**: 确保使用 Python 3.x
   ```bash
   python3 --version
   ```

## 贡献指南

添加新脚本时请：

1. 遵循现有的命名规范
2. 添加适当的错误处理
3. 提供帮助信息
4. 更新本 README 文件
5. 考虑跨平台兼容性
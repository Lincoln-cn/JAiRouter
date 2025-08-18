# 文档开发指南

<!-- 版本信息 -->
> **文档版本**: 1.1.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: a676365d  
> **作者**: Lincoln
<!-- /版本信息 -->

本目录包含 JAiRouter 项目的完整文档。

## 文档管理工具

我们提供了统一的文档管理脚本，整合了文档服务、链接检查、版本管理等功能。

### 环境要求

- Python 3.x
- pip (Python 包管理器)
- PowerShell (Windows) 或 Bash (Linux/macOS)

### 统一管理脚本

根据你的操作系统选择对应的脚本：

- **Windows PowerShell**: `scripts\docs\docs-manager.ps1`
- **Windows 批处理**: `scripts\docs\docs-manager.cmd`
- **Linux/macOS**: `scripts/docs/docs-manager.sh`

### 快速开始

#### 启动文档服务器

```bash
# Windows PowerShell
.\scripts\docs\docs-manager.ps1 serve

# Windows 批处理
.\scripts\docs\docs-manager.cmd serve

# Linux/macOS
./scripts/docs/docs-manager.sh serve

# 自定义端口和地址
.\scripts\docs\docs-manager.ps1 serve -HostAddress 0.0.0.0 -Port 3000
./scripts/docs/docs-manager.sh serve --host 0.0.0.0 --port 3000
```

#### 检查文档链接

```bash
# 检查所有链接
.\scripts\docs\docs-manager.ps1 check-links

# 输出报告到文件
.\scripts\docs\docs-manager.ps1 check-links -Output report.json

# 发现问题时退出码为1
.\scripts\docs\docs-manager.ps1 check-links -FailOnError
```

#### 修复无效链接

```bash
# 分析并显示修复建议
.\scripts\docs\docs-manager.ps1 fix-links

# 应用修复建议（交互式确认）
.\scripts\docs\docs-manager.ps1 fix-links -Apply

# 自动修复（不询问确认）
.\scripts\docs\docs-manager.ps1 fix-links -Apply -AutoFix
```

#### 版本管理

```bash
# 扫描并更新文档版本
.\scripts\docs\docs-manager.ps1 version -Scan

# 添加版本头信息
.\scripts\docs\docs-manager.ps1 version -AddHeaders

# 导出版本数据
.\scripts\docs\docs-manager.ps1 version -Export data.json

# 清理90天前的变更记录
.\scripts\docs\docs-manager.ps1 version -Cleanup 90
```

#### 验证文档结构

```bash
# 验证文档结构和配置
.\scripts\docs\docs-manager.ps1 validate
```

#### 检查文档同步性

```bash
# 检查文档与代码的同步性
.\scripts\docs\docs-manager.ps1 check-sync

# 输出报告到文件
.\scripts\docs\docs-manager.ps1 check-sync -Output sync-report.md
```

### 传统方式（手动执行）

如果你更喜欢手动执行各个步骤：

```bash
# 安装依赖
pip install -r requirements.txt

# 启动开发服务器
mkdocs serve

# 构建静态文件
mkdocs build
```

#### Linux/macOS 用户

```bash
# 使用 Shell 脚本启动文档服务
./scripts/docs/docs-manager.sh serve

# 或者手动执行
pip3 install -r requirements.txt
mkdocs serve
```

## 可用命令

### serve - 启动文档服务器

启动本地开发服务器，支持热重载。

**选项:**
- `--host <地址>`: 监听地址 (默认: localhost)
- `--port <端口>`: 监听端口 (默认: 8000)

**示例:**
```bash
.\scripts\docs\docs-manager.ps1 serve -HostAddress 0.0.0.0 -Port 3000
```

### check-links - 检查链接有效性

检查文档中的所有链接，包括内部链接和外部链接。

**选项:**
- `--output <文件>`: 输出报告文件路径
- `--fail-on-error`: 发现无效链接时退出码为1

**示例:**
```bash
.\scripts\docs\docs-manager.ps1 check-links -Output link-report.json -FailOnError
```

### fix-links - 修复无效链接

基于链接检查报告，提供修复建议和自动修复功能。

**选项:**
- `--apply`: 应用修复建议
- `--auto-fix`: 自动修复不询问确认

**示例:**
```bash
.\scripts\docs\docs-manager.ps1 fix-links -Apply -AutoFix
```

### version - 版本管理

管理文档版本信息，追踪文档变更。

**选项:**
- `--scan`: 扫描并更新版本信息
- `--add-headers`: 添加版本头信息到文档
- `--cleanup <天数>`: 清理指定天数前的变更记录
- `--export <文件>`: 导出版本数据到文件
- `--check-outdated <天数>`: 检查过期文档的天数阈值

**示例:**
```bash
.\scripts\docs\docs-manager.ps1 version -Scan -AddHeaders -Export version-data.json
```

### check-sync - 检查同步性

检查文档内容与代码的同步性，验证配置示例和API文档的准确性。

**选项:**
- `--output <文件>`: 输出报告文件路径
- `--fail-on-error`: 发现严重问题时退出码为1

**示例:**
```bash
.\scripts\docs\docs-manager.ps1 check-sync -Output sync-report.md -FailOnError
```

### validate - 验证文档

验证文档结构和MkDocs配置文件的正确性。

**示例:**
```bash
.\scripts\docs\docs-manager.ps1 validate
```

## 文档结构

```
docs/
├── zh/                     # 中文文档
│   ├── index.md           # 首页
│   ├── getting-started/   # 快速开始
│   ├── configuration/     # 配置指南
│   ├── api-reference/     # API 参考
│   ├── deployment/        # 部署指南
│   ├── monitoring/        # 监控指南
│   ├── development/       # 开发指南
│   ├── troubleshooting/   # 故障排查
│   └── reference/         # 参考资料
├── en/                     # 英文文档
│   └── (同中文结构)
├── assets/                 # 静态资源
├── CNAME                   # GitHub Pages 域名配置
└── README.md              # 本文件
```

## 开发工作流

1. **启动开发服务器**
   ```bash
   .\scripts\docs\docs-manager.ps1 serve
   ```

2. **编辑文档内容**
   - 在对应语言目录下编辑 Markdown 文件
   - 保存后自动重新加载

3. **检查链接有效性**
   ```bash
   .\scripts\docs\docs-manager.ps1 check-links
   ```

4. **修复发现的问题**
   ```bash
   .\scripts\docs\docs-manager.ps1 fix-links -Apply
   ```

5. **更新版本信息**
   ```bash
   .\scripts\docs\docs-manager.ps1 version -Scan -AddHeaders
   ```

6. **验证文档结构**
   ```bash
   .\scripts\docs\docs-manager.ps1 validate
   ```

## 部署

文档通过 GitHub Actions 自动部署到 GitHub Pages。每次推送到 `main` 分支时会自动触发构建和部署。

### 手动部署

如需手动部署：

```bash
# 构建文档
mkdocs build

# 部署到 GitHub Pages
mkdocs gh-deploy
```

## 贡献指南

1. 创建功能分支
2. 编辑文档内容
3. 运行文档检查工具
4. 提交 Pull Request

### 文档编写规范

- 使用 Markdown 格式
- 中文文档使用中文标点符号
- 英文文档使用英文标点符号
- 代码块指定语言类型
- 链接使用相对路径
- 图片放在 `assets/` 目录下

### 质量检查

在提交前请运行以下检查：

```bash
# 检查链接有效性
.\scripts\docs\docs-manager.ps1 check-links -FailOnError

# 检查文档同步性
.\scripts\docs\docs-manager.ps1 check-sync -FailOnError

# 验证文档结构
.\scripts\docs\docs-manager.ps1 validate
```

## 故障排查

### 常见问题

1. **Python 依赖安装失败**
   - 确保已安装 Python 3.x
   - 尝试使用 `pip3` 而不是 `pip`
   - 检查网络连接

2. **MkDocs 服务启动失败**
   - 检查 `mkdocs.yml` 配置文件语法
   - 确保所有导航文件都存在
   - 运行 `.\scripts\docs-manager.ps1 validate` 检查配置

3. **链接检查失败**
   - 检查网络连接
   - 某些外部链接可能有防爬虫保护
   - 内部链接检查文件路径是否正确

4. **脚本执行权限问题 (Linux/macOS)**
   ```bash
   chmod +x scripts/docs/docs-manager.sh
   ```

### 获取帮助

如果遇到问题，可以：

1. 查看脚本帮助信息：
   ```bash
   .\scripts\docs\docs-manager.ps1 help
   ```

2. 检查项目 Issues
3. 联系项目维护者

## 更新日志

- **v1.1.0** (2025-08-18): 整合文档管理脚本，简化使用流程
- **v1.0.0** (2025-08-18): 初始版本，基础文档结构
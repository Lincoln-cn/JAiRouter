# 文档开发指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: a676365d  
> **作者**: Lincoln
<!-- /版本信息 -->


本目录包含 JAiRouter 项目的完整文档。

## 本地开发

### 环境要求

- Python 3.x
- pip (Python 包管理器)

### 快速开始

#### Windows 用户

```powershell
# 使用 PowerShell 脚本
.\scripts\serve-docs.ps1

# 或者手动执行
pip install -r requirements.txt
mkdocs serve
```

#### Linux/macOS 用户

```bash
# 使用 Shell 脚本
chmod +x scripts/serve-docs.sh
./scripts/serve-docs.sh

# 或者手动执行
pip3 install -r requirements.txt
mkdocs serve
```

### 访问文档

启动服务器后，在浏览器中访问 http://localhost:8000

## 文档结构

```
docs/
├── index.md                    # 主页
├── getting-started/            # 快速开始
├── configuration/              # 配置指南
├── api-reference/              # API参考
├── deployment/                 # 部署指南
├── monitoring/                 # 监控指南
├── development/                # 开发指南
├── troubleshooting/            # 故障排查
└── reference/                  # 参考资料
```

## 编写规范

- 使用 Markdown 格式
- 遵循 [Markdown 语法规范](https://www.markdownguide.org/)
- 代码块使用语法高亮
- 图片放在对应章节的 `images/` 子目录中
- 链接使用相对路径

## 构建和部署

文档通过 GitHub Actions 自动构建和部署到 GitHub Pages。

### 本地构建

```bash
# 构建静态文件
mkdocs build

# 构建并检查链接
mkdocs build --strict
```

### 质量检查

```bash
# 检查 Markdown 语法
markdownlint-cli2 "docs/**/*.md"

# 检查链接有效性
markdown-link-check docs/**/*.md
```

## 贡献指南

1. 创建新分支进行文档修改
2. 本地测试文档构建和显示效果
3. 提交 Pull Request
4. 等待自动化检查通过
5. 合并到主分支后自动部署
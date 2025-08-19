# 部署测试指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: f47f2607  
> **作者**: Lincoln
<!-- /版本信息 -->



## 概述

本文档介绍如何测试 JAiRouter 文档的自动化部署流程，确保文档能够正确构建和发布到 GitHub Pages。

## 测试脚本

项目提供了多个平台的测试脚本：

- **Windows PowerShell**: `scripts/test-deployment.ps1`
- **Windows CMD**: `scripts/test-deployment.cmd`
- **Linux/macOS**: `scripts/test-deployment.sh`

## 本地测试

### Windows 环境

使用 PowerShell：
```powershell
# 完整测试
.\scripts\test-deployment.ps1

# 跳过构建测试
.\scripts\test-deployment.ps1 -SkipBuild

# 包含链接检查
.\scripts\test-deployment.ps1 -CheckLinks

# 指定语言测试
.\scripts\test-deployment.ps1 -Language "zh"
```

使用 CMD：
```cmd
# 完整测试
scripts\test-deployment.cmd
```

### Linux/macOS 环境

```bash
# 完整测试
./scripts/test-deployment.sh

# 跳过构建测试
./scripts/test-deployment.sh --skip-build

# 包含链接检查
./scripts/test-deployment.sh --check-links

# 指定语言测试
./scripts/test-deployment.sh --language zh
```

## 自动化测试

### GitHub Actions 工作流

项目配置了以下自动化测试工作流：

1. **部署测试验证** (`.github/workflows/deployment-test.yml`)
   - 验证配置文件
   - 检查文档结构
   - 测试构建过程
   - 验证多语言支持
   - 跨平台兼容性测试

2. **文档构建和部署** (`.github/workflows/docs.yml`)
   - 自动构建文档
   - 部署到 GitHub Pages

### 触发条件

自动化测试在以下情况下触发：

- 推送到 main/master 分支
- 创建 Pull Request
- 修改文档相关文件
- 手动触发工作流

## 测试检查项

### 前置条件检查

- Python 环境
- pip 包管理器
- 必要的依赖包

### 配置验证

- `mkdocs.yml` 语法正确性
- 插件配置有效性
- 导航结构完整性

### 文档结构检查

- 必要目录存在性
- 关键文件完整性
- 多语言目录结构

### 构建测试

- 文档构建成功
- 输出文件生成
- 多语言版本正确

### 内容验证

- 页面内容正确性
- 导航功能正常
- 搜索功能可用
- 语言切换功能

## 测试结果解读

### 成功标识

- ✓ 绿色勾号：测试通过
- 🎉 庆祝图标：所有测试通过

### 失败标识

- ✗ 红色叉号：测试失败
- ⚠ 黄色警告：需要注意的问题

### 测试报告

测试完成后会生成详细报告，包括：

- 各项测试结果
- 构建产物信息
- 错误详情（如有）
- 修复建议

## 常见问题排查

### 构建失败

1. **依赖问题**
   ```bash
   # 重新安装依赖
   pip install --upgrade mkdocs-material
   pip install --upgrade mkdocs-git-revision-date-localized-plugin
   ```

2. **配置错误**
   ```bash
   # 验证配置
   mkdocs config
   ```

3. **文件缺失**
   - 检查必要的文档文件是否存在
   - 验证路径配置是否正确

### 多语言问题

1. **语言版本缺失**
   - 确认 `docs/zh/` 和 `docs/en/` 目录存在
   - 检查对应的 `index.md` 文件

2. **导航翻译问题**
   - 验证 `mkdocs.yml` 中的 `nav_translations` 配置
   - 确认所有导航项都有对应翻译

### 部署问题

1. **GitHub Pages 未启用**
   - 在仓库设置中启用 GitHub Pages
   - 选择 "GitHub Actions" 作为源

2. **权限问题**
   - 确认工作流有足够的权限
   - 检查 `GITHUB_TOKEN` 权限设置

## 性能优化建议

### 构建优化

- 使用缓存加速依赖安装
- 并行处理多语言版本
- 优化图片和资源文件

### 部署优化

- 启用 CDN 加速
- 配置适当的缓存策略
- 压缩静态资源

## 持续改进

### 监控指标

- 构建时间
- 部署成功率
- 页面加载速度
- 用户访问统计

### 自动化扩展

- 添加更多质量检查
- 集成性能测试
- 实现自动化回滚

## 相关文档

- [GitHub Pages 部署配置](github-pages.md)
- [文档质量保证](../development/quality-assurance.md)
- [多语言支持](../configuration/i18n.md)
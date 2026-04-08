# 文档内容同步检查工具

本目录包含用于检查文档与代码同步性的工具集，确保项目文档与实际代码配置保持一致。

## 工具概述

### 核心检查脚本

1. **check-docs-sync.py** - Python 版本的主检查脚本
2. **check-docs-sync.ps1** - PowerShell 版本的检查脚本
3. **check-docs-sync.cmd** - Windows 批处理包装脚本

### 配置和验证

4. **docs-sync-config.yml** - 检查规则配置文件
5. **validate-docs-sync.py** - 验证脚本功能的测试工具

### 自动化集成

6. **.github/workflows/docs-content-sync.yml** - GitHub Actions 工作流

## 功能特性

### 检查内容

- ✅ **配置文档同步性** - 验证文档中的配置示例与实际配置文件一致
- ✅ **API 文档完整性** - 检查 API 文档是否包含所有端点说明
- ✅ **依赖文档准确性** - 验证依赖文档与 POM 文件中的依赖一致
- ✅ **Docker 文档同步** - 检查 Docker 相关文档与实际文件的一致性
- ✅ **监控配置文档** - 验证监控相关文档的准确性
- ✅ **版本信息同步** - 检查文档中的版本信息是否最新

### 检查规则

- **严重问题 (FAIL)**: 缺少必要文档、关键配置错误
- **警告问题 (WARN)**: 配置不匹配、文档不完整
- **通过检查 (PASS)**: 所有检查项都符合要求

## 使用方法

### 1. Python 版本 (推荐)

```bash
# 基本检查
python scripts/check-docs-sync.py

# 指定项目根目录和输出文件
python scripts/check-docs-sync.py --project-root . --output report.md

# 发现严重问题时失败退出
python scripts/check-docs-sync.py --fail-on-error
```

### 2. PowerShell 版本 (Windows)

```powershell
# 基本检查
.\scripts\check-docs-sync.ps1

# 指定参数
.\scripts\check-docs-sync.ps1 -ProjectRoot . -OutputFile "report.md" -FailOnError
```

### 3. Windows 批处理版本

```cmd
# 基本检查
scripts\check-docs-sync.cmd

# 指定参数
scripts\check-docs-sync.cmd --project-root . --output report.md --fail-on-error
```

## 配置文件

### docs-sync-config.yml

配置文件定义了检查规则和严重程度：

```yaml
# 检查规则配置
check_rules:
  configuration:
    enabled: true
    key_configs:
      - path: "server.port"
        description: "服务器端口配置"
        required: true

# 严重程度配置
severity_levels:
  missing_doc: "FAIL"
  config_mismatch: "WARN"
  
# 忽略规则
ignore_rules:
  file_patterns:
    - "*.tmp"
    - "*.bak"
```

## 检查项目详情

### 配置文档检查

检查以下配置文档与实际配置的一致性：

- `docs/zh/configuration/application-config.md`
- `docs/zh/configuration/load-balancing.md`
- `docs/zh/configuration/rate-limiting.md`
- `docs/zh/configuration/circuit-breaker.md`

**检查内容**:
- 服务器端口配置
- 模型适配器配置
- 负载均衡策略和算法
- 限流算法和参数
- 熔断器配置参数

### API 文档检查

检查 API 文档的完整性：

- `docs/zh/api-reference/universal-api.md`
- `docs/zh/api-reference/management-api.md`

**检查内容**:
- 所有 `/v1/*` API 端点是否有文档说明
- 管理端点 (Actuator) 是否有说明
- 服务配置与文档的一致性

### 依赖文档检查

检查依赖相关文档：

- `docs/zh/getting-started/installation.md`
- `docs/zh/development/index.md`

**检查内容**:
- 关键依赖是否在文档中说明
- Spring Boot 版本是否与 POM 一致
- Maven 构建说明是否完整

### Docker 文档检查

检查 Docker 相关文档：

- `docs/zh/deployment/docker.md`

**检查内容**:
- Docker 文件是否在文档中说明
- 端口配置是否一致
- Docker Compose 文件是否有说明

### 监控文档检查

检查监控相关文档：

- `docs/zh/monitoring/setup.md`

**检查内容**:
- 监控配置参数是否准确
- Prometheus 集成说明是否完整
- 指标配置是否与实际一致

## 报告格式

检查完成后会生成 Markdown 格式的报告：

```markdown
# 文档内容同步检查报告

## 检查统计
- 总问题数: 5
- 严重问题: 1
- 警告问题: 4
- 通过检查: 0

## 严重问题

### MISSING_DOC
**文件**: docs/zh/configuration/application-config.md
**描述**: 应用配置文档不存在
**建议**: 创建应用配置文档并添加相关说明

## 警告问题

### CONFIG_MISMATCH
**文件**: docs/zh/configuration/application-config.md
**描述**: 文档中的端口配置与实际配置不符，实际端口: 8080
**建议**: 更新文档中的端口配置
```

## 自动化集成

### GitHub Actions

项目包含 GitHub Actions 工作流 (`.github/workflows/docs-content-sync.yml`)，会在以下情况自动运行检查：

- 推送到 main/develop 分支
- 创建 Pull Request
- 每天凌晨 2 点定时检查
- 手动触发

工作流会：
1. 运行 Python 版本的检查
2. 运行 Windows PowerShell 版本的检查
3. 生成检查报告
4. 在 PR 中添加评论（如果发现问题）
5. 上传报告作为构建产物

### 本地 Git Hook

可以将检查脚本集成到 Git Hook 中：

```bash
# 在 .git/hooks/pre-commit 中添加
#!/bin/bash
python scripts/check-docs-sync.py --fail-on-error
```

## 故障排查

### 常见问题

1. **Python 不可用**
   - 使用 PowerShell 版本: `.\scripts\check-docs-sync.ps1`
   - 或使用批处理版本: `scripts\check-docs-sync.cmd`

2. **YAML 解析错误**
   - 检查 `application.yml` 文件格式是否正确
   - 确保文件编码为 UTF-8

3. **权限问题 (PowerShell)**
   - 运行: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

4. **路径问题**
   - 确保在项目根目录运行脚本
   - 使用 `--project-root` 参数指定正确路径

### 调试模式

添加详细输出进行调试：

```bash
# Python 版本
python scripts/check-docs-sync.py --project-root . --output debug-report.md

# PowerShell 版本
.\scripts\check-docs-sync.ps1 -ProjectRoot . -OutputFile "debug-report.md" -Verbose
```

## 扩展开发

### 添加新的检查规则

1. 在 `check-docs-sync.py` 中添加新的检查方法
2. 在 `docs-sync-config.yml` 中配置检查规则
3. 在 `validate-docs-sync.py` 中添加对应的测试用例
4. 更新文档说明

### 自定义检查逻辑

```python
def check_custom_documentation(self):
    """自定义文档检查"""
    doc_path = self.project_root / "docs/zh/custom/custom.md"
    
    if not doc_path.exists():
        self.add_issue(
            str(doc_path),
            "MISSING_CUSTOM_DOC",
            "自定义文档不存在",
            CheckResult.FAIL
        )
        return
    
    # 添加具体检查逻辑
    # ...
```

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 添加测试用例
4. 提交 Pull Request
5. 确保所有检查通过

## 许可证

本工具遵循项目的开源许可证。
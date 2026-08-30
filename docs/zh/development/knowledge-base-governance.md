# 知识库治理标准操作规程

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2026-05-21  
> **Git 提交**: -  
> **作者**: Lincoln
<!-- /版本信息 -->


## 1. 概述

### 什么是知识库治理

知识库治理是对 JAiRouter 项目文档资产（`docs/` 公开文档约 155 个文件、`innerdoc/` 内部文档约 165 个文件）进行系统化清点、去重、归档和版本追踪的周期性运维流程。

### 为什么需要治理

随着项目快速迭代，文档库不可避免地出现以下问题：

- **过时文档（Stale）**：内容与当前代码/配置脱节，误导开发者
- **重复文档（Duplicate）**：同一内容散落在多个路径，更新时容易遗漏
- **一次性产物（One-off）**：调试记录、临时分析等不应长期保留的文件堆积

### 治理节奏

知识库治理**每次版本发布时**执行一次，作为版本发布检查清单的固定步骤。

---

## 2. 工具集

所有治理脚本位于 `scripts/knowledge-governance/` 目录下。

### kg-scanner.py — 清点与分类

扫描 `docs/` 和 `innerdoc/` 目录树，输出文件清单及分类统计。

```bash
# 扫描全部目录并输出 JSON 报告
python scripts/knowledge-governance/kg-scanner.py --root . --output innerdoc/governance/scan-report.json

# 仅扫描 innerdoc
python scripts/knowledge-governance/kg-scanner.py --root . --scope innerdoc --output innerdoc/governance/scan-report.json
```

### kg-duplicate-detector.py — 重复检测

支持两种检测模式：SHA-256 精确匹配和文件名近似匹配。

```bash
# 精确 + 近似检测，输出重复集群报告
python scripts/knowledge-governance/kg-duplicate-detector.py --root . --threshold 0.8 --output innerdoc/governance/duplicates.json
```

### kg-staleness-detector.py — 过时检测

检测三类过时信号：索引漂移（索引指向不存在的文件）、状态冲突（文档声明与实际不符）、版本字面量过期。

```bash
# 检测过时文档
python scripts/knowledge-governance/kg-staleness-detector.py --root . --output innerdoc/governance/staleness.json
```

### kg-archive-mover.py — 归档执行

将标记为归档的文件移动到 `innerdoc/archive/` 对应子目录（仅移动，不删除）。

```bash
# 仅生成归档计划（dry-run）
python scripts/knowledge-governance/kg-archive-mover.py --plan innerdoc/governance/governance-proposal.md --dry-run

# 执行归档
python scripts/knowledge-governance/kg-archive-mover.py --plan innerdoc/governance/governance-proposal.md --execute
```

### kg-version-tracker.py — 版本追踪

管理 `innerdoc/docs-versions.json` 版本索引文件，支持重新生成索引和检查过期文档。

```bash
# 重新生成 innerdoc 索引
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .

# 检查过期文档（knowledge-base 类目 30 天阈值）
python scripts/knowledge-governance/kg-version-tracker.py --check-outdated --category knowledge-base --threshold 30

# 检查所有类目的过期文档
python scripts/knowledge-governance/kg-version-tracker.py --check-outdated --all
```

---

## 3. 治理工作流

知识库治理遵循五步工作流：

### 步骤 1：扫描

运行三个扫描器，生成原始报告：

```bash
python scripts/knowledge-governance/kg-scanner.py --root . --output innerdoc/governance/scan-report.json
python scripts/knowledge-governance/kg-duplicate-detector.py --root . --threshold 0.8 --output innerdoc/governance/duplicates.json
python scripts/knowledge-governance/kg-staleness-detector.py --root . --output innerdoc/governance/staleness.json
```

报告输出至 `innerdoc/governance/` 目录。

### 步骤 2：分析

由 mimo 子代理读取三份报告，结合重复集群信息，生成结构化治理提案 `innerdoc/governance/governance-proposal.md`，包含以下四个部分：

- **Merge Map（合并映射）**：哪些重复文件应合并为一个权威版本
- **Archive List（归档清单）**：应归档的一次性产物和过时文件
- **Content Updates（内容更新）**：需要更新版本字面量或修正状态声明的文件
- **Index Plan（索引计划）**：索引文件需要做的增删改操作

### 步骤 3：人工审批

开发者审查 `governance-proposal.md`，确认或修改提案内容后批准执行。

> **重要**：未经人工审批，不得执行归档和合并操作。

### 步骤 4：执行

按批准的提案执行：

```bash
# 1. 归档文件
python scripts/knowledge-governance/kg-archive-mover.py --plan innerdoc/governance/governance-proposal.md --execute

# 2. 手动完成 knowledge-base 合并（按 Merge Map 操作）

# 3. 重新生成索引
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .
```

### 步骤 5：更新

- 在 `innerdoc/开发计划2026/` 中记录本次治理的版本条目
- 更新任务跟踪表，标记治理任务完成

---

## 4. 归档策略

### 归档条件

满足以下任一条件的文件可被归档：

| 条件 | 说明 |
|------|------|
| 字节级重复 | SHA-256 哈希完全相同的文件（保留一份权威副本） |
| 一次性调试产物 | 文件名包含 `debug`、`temp`、`tmp`、`test-output`、`scratch` 等启发式关键词 |
| 内容已被 knowledge-base 吸收 | 文件的核心内容已整合到 `knowledge-base/` 对应文档中 |

### 绝不归档的文件

- `knowledge-base/*` — 知识库核心文档
- `16-版本发布/*` — 版本发布相关文档
- `开发计划/`、`任务跟踪表` — 活跃使用的项目管理文档

### 归档目录结构

归档文件移动到 `innerdoc/archive/<原分类>/` 目录下，保持原始文件名不变。归档是**移动操作**，不是删除操作，确保可追溯和可恢复。

---

## 5. 重复检测

### 精确重复检测（SHA-256）

对所有文件计算 SHA-256 哈希值，哈希相同的文件归为一个重复集群。处理方式：

- 保留路径最短或最新修改的副本作为权威版本
- 其余副本归档

### 近似重复检测（文件名相似度）

使用归一化文件名计算相似度，阈值为 **0.8**（80%）：

1. 文件名去除扩展名、日期前缀、版本后缀
2. 转为小写并归一化空白字符
3. 计算编辑距离相似度
4. 相似度 > 0.8 的文件对标记为近似重复

近似重复需人工确认是否真正重复，不自动归档。

---

## 6. 版本追踪

### innerdoc/docs-versions.json

`innerdoc/docs-versions.json` 的结构与 `docs/docs-versions.json` 一致：

```json
{
  "versions": {
    "innerdoc/knowledge-base/xxx.md": {
      "FilePath": "innerdoc/knowledge-base/xxx.md",
      "Version": "1.0.0",
      "LastModified": "2026-05-21T00:00:00",
      "ContentHash": "abc123...",
      "GitCommit": "xxxxxxx",
      "Author": "Lincoln",
      "ChangeSummary": "",
      "Dependencies": []
    }
  }
}
```

### 过期检查策略

通过 `docs/docs-version-config.yml` 配置过期阈值，使用 `kg-version-tracker.py --check-outdated` 按类目检查：

| 类目 | 过期阈值 | 说明 |
|------|----------|------|
| knowledge-base | 30 天 | 知识库文档应保持最新 |
| development | 90 天 | 开发文档允许较长更新周期 |
| releases | 永不过期 | 版本发布文档为历史记录 |

---

## 7. 索引维护

治理执行后需重新生成以下三个索引文件：

| 索引文件 | 说明 |
|----------|------|
| `innerdoc/README-INNERDOC.md` | innerdoc 目录的顶层 README，包含分类导航 |
| `innerdoc/INDEX.json` | innerdoc 的 JSON 格式索引，供工具消费 |
| `innerdoc/00-索引/README.md` | 分类索引页 |

重新生成命令：

```bash
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .
```

此命令会扫描 innerdoc 目录结构，自动更新上述三个索引文件。

---

## 8. CI 集成

### CI 自动化（docs/ 目录）

以下检查由 GitHub Actions 自动执行：

| 检查项 | 工作流 | 说明 |
|--------|--------|------|
| 导航文件验证 | `scripts/docs/validate-nav-files.py` | 确保 mkdocs.yml 中引用的所有文件存在 |
| 文档过期检测 | `docs-version-management.yml` | 每日检查 docs/ 中超过 30 天未更新的文档，自动创建 GitHub Issue |

### 本地治理（innerdoc/ 目录）

innerdoc 的治理是**本地操作**，不纳入 CI：

1. 手动运行扫描器生成报告
2. 子代理生成治理提案
3. 人工审批后本地执行归档和索引更新

> **注意**：innerdoc 不在 CI 中检测过期，因为内部文档的更新节奏与公开文档不同。

---

## 9. 版本发布检查清单

每次版本发布时，在发布检查清单中包含以下条目：

```markdown
- [ ] 知识库治理：运行 knowledge-governance skill（扫描→治理提案→审批→执行→索引更新）
```

完整版本发布检查清单的执行顺序建议：

1. 代码冻结与功能验证
2. 文档更新与同步检查
3. **知识库治理**（扫描→提案→审批→执行→索引更新）
4. 版本号更新与标签
5. 发布构建与部署

---

## 10. 故障排查

### 索引在手动编辑后不一致

**症状**：手动添加或删除 innerdoc 文件后，索引文件未同步更新。

**解决方案**：

```bash
# 重新生成全部索引
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .
```

### 归档误分类（启发式假阳性）

**症状**：正常文件被文件名启发式（如包含 `test`）错误标记为一次性产物并归档。

**解决方案**：

1. 从 `innerdoc/archive/<原分类>/` 将文件移回原位置
2. 在治理提案的 Archive List 中手动排除该文件
3. 考虑将该文件名模式加入启发式排除列表

### 扫描器遇到编码问题

**症状**：扫描器因文件编码（非 UTF-8）报错退出。

**解决方案**：

```bash
# 指定编码重试
python scripts/knowledge-governance/kg-scanner.py --root . --encoding gbk --output innerdoc/governance/scan-report.json

# 或排除有问题的文件
python scripts/knowledge-governance/kg-scanner.py --root . --exclude-pattern "*.bak" --output innerdoc/governance/scan-report.json
```

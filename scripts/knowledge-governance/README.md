# 知识库治理工具集 (Knowledge Governance Toolset)

v2.9.5 文档知识库治理工具集，用于管理 `innerdoc/` 私有知识库的健康状态。

## 工具列表

### 1. kg-scanner.py — 知识库扫描器

扫描 innerdoc/ 下所有 .md 文件，记录元信息并按目录分类。

```bash
# 执行扫描并输出 JSON 报告
python scripts/knowledge-governance/kg-scanner.py --project-root . --scan --output report.json

# 启用文件分类（一次性/归档候选）并输出 Markdown
python scripts/knowledge-governance/kg-scanner.py --project-root . --scan --classify --output report.json --output-md report.md
```

### 2. kg-duplicate-detector.py — 重复文件检测器

检测完全重复（SHA-256 相同）和近似重复（文件名相似度 > 0.8）文件。

```bash
python scripts/knowledge-governance/kg-duplicate-detector.py --project-root . --scan --output duplicates.json
```

### 3. kg-staleness-detector.py — 过时性检测器

检查索引漂移、状态冲突和版本漂移。

```bash
python scripts/knowledge-governance/kg-staleness-detector.py --project-root . --scan --output staleness.json
```

### 4. kg-archive-mover.py — 归档移动器

将标记为归档的文件移动到 innerdoc/archive/ 目录下。

```bash
# 查看归档计划（dry-run）
python scripts/knowledge-governance/kg-archive-mover.py --project-root . --dry-run

# 保存归档计划
python scripts/knowledge-governance/kg-archive-mover.py --project-root . --plan archive-plan.json

# 执行归档
python scripts/knowledge-governance/kg-archive-mover.py --project-root . --execute

# 指定文件归档
python scripts/knowledge-governance/kg-archive-mover.py --project-root . --paths "10-开发指南/temp.md,10-开发指南/fix.md" --execute
```

**受保护目录**: `knowledge-base/` 和 `16-版本发布/` 下的文件不会被归档。

### 5. kg-version-tracker.py — 版本追踪器

管理版本追踪文件和索引。

```bash
# 生成 innerdoc/docs-versions.json
python scripts/knowledge-governance/kg-version-tracker.py --project-root . --scan

# 重新生成 INDEX.json 并更新 README-INNERDOC.md
python scripts/knowledge-governance/kg-version-tracker.py --project-root . --regen-index

# 检查超过 30 天未修改的文件
python scripts/knowledge-governance/kg-version-tracker.py --project-root . --check-outdated 30
```

## 通用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--project-root` | 项目根目录路径 | `.` |

## 依赖

仅使用 Python 标准库，无需安装额外依赖。

## 约定

- 所有脚本使用 UTF-8 编码读写文件
- 中文注释和文档字符串
- argparse CLI 接口
- 排除 `innerdoc/archive/**` 目录

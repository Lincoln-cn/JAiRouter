# v2.5.x 版本发布说明

## v2.5.15 (2026-05-11) - 封板版本

### 超大类重构完成

本次版本完成了4个超大类的重构工作，总计减少2011行代码（-62%）。

| 文件 | 原行数 | 最终行数 | 减少 | 目标 |
|------|--------|----------|------|------|
| BaseAdapter | 1386 | 416 | -70% | 600 ✅ |
| TracingService | 764 | 483 | -37% | 400 ✅ |
| DefaultStructuredLogger | 945 | 365 | -61% | 400 ✅ |
| ConfigVersionManager | 746 | 387 | -48% | 400 ✅ |

### 新增组件（12个）

- ConfigComparator、SpanAttributeHelper、ServiceNameResolver
- RequestLogBuilder、ResponseLogBuilder、BackendCallLogBuilder
- ErrorLogBuilder、SystemEventLogBuilder、VersionValidator
- VersionMetadataManager、VersionSyncService、ModelUtils

### 质量检查

- Checkstyle: ✅ 通过
- SpotBugs: ✅ 通过
- 测试: 971 passed ✅

---

## v2.5.11-v2.5.14 中间版本

详见 `innerdoc/16-版本发布/v2.5.11-v2.5.15-超大类重构总结.md`

---

## 历史版本

### v2.5.10 (2026-05-10)
- ConfigIntegrityValidator修复

### v2.5.9 及之前
- 配置文件整合、废弃代码清理等
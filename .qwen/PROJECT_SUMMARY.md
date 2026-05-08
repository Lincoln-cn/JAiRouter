# Project Summary

## Overall Goal
v2.7-v2.9 微服务化准备系列已完成。v2.10-v2.13 代码质量提升和超大类重构进行中。
**当前重点**：超大类拆分，降低代码复杂度，提升可维护性。

## Key Knowledge
- **Build Command**: `mvn compile -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true`
- **Test Command**: `mvn test -Dcheckstyle.skip=true -Dspotbugs.skip=true`
- **Coverage Command**: `mvn clean test jacoco:report -Dcheckstyle.skip=true -Dspotbugs.skip=true`
- **Current Tests**: 951 (all passing)
- **Coverage**: INSTRUCTION 13.1%
- **Package Structure**: 6 service modules (auth/config/router/monitor/persistence/common)
- **Configuration Files**: 25 modular files under src/main/resources/config/
- **超大类进度**: ~40% (已提取1797行，待拆分4500行)

## Recent Actions
- **v2.13.x ConfigurationHelper拆分完成**:
  - 创建3个helper类: ServiceTypeResolver(124), ConfigValidatorHelper(590), ConfigConverterHelper(445)
  - 总计提取: 1159行代码
  - Git 标签: v2.13.1-v2.13.3
  - **待完成**: ConfigurationHelper委托调用 (v2.13.4)

## Completed Versions
| Series | Status | Summary |
|--------|--------|---------|
| v2.7.x | ✅ 完成 | Package 结构重组，481 文件迁移，6 服务模块 |
| v2.8.x | ✅ 完成 | 配置整合，25 配置文件，校验机制 |
| v2.9.x | ✅ 完成 | 废弃清理 -628 行，+57 测试，Checkstyle -46% |
| v2.10.x | ✅ 完成 | Checkstyle修复，警告-65% |
| v2.11.x | ✅ 完成 | TraceQueryService拆分，+145测试，覆盖率+2.1% |
| v2.12.x | ✅ 完成 | 事件驱动重构，4事件类+3监听器，代码复杂度-82% |
| v2.13.x | ⚠️ 进行中 | ConfigurationHelper拆分，3helper类(1159行)，待委托调用 |

## Next Version (下次推进)
- **v2.13.4**: ConfigurationHelper委托调用 (工作量: 1天)
  - 添加helper类注入
  - 方法改为委托调用
  - 标记@Deprecated
  
- **v2.14.x**: ApiKeyService拆分 (工作量: 4天)
  - 提取ApiKeyValidator (~200行)
  - 提取ApiKeyBatchService (~300行)
  - 提取ApiKeyPersistenceService (~200行)

## 关键文档 (下次启动请先读取)
| 文档 | 路径 |
|------|------|
| 下次推进待办任务 | `innerdoc/03-重构记录/下次推进待办任务.md` |
| v2.13.x开发总结 | `innerdoc/16-版本发布/v2.13.x-开发总结.md` |
| 超大类重构分析报告 | `innerdoc/03-重构记录/超大类重构分析报告.md` |
| ConfigurationService架构文档 | `innerdoc/03-重构记录/ConfigurationService-架构文档.md` |

## 超大类重构进度
| 文件 | 原行数 | 当前行数 | 目标行数 | 进度 |
|------|--------|----------|----------|------|
| ConfigurationService | 2269 | 2269 | ~1200 | 50% (事件驱动已设计) |
| BaseAdapter | 1350 | 1350 | ~800 | 30% (v2.3.x已拆分) |
| ApiKeyService | 1198 | 1198 | ~400 | 0% (待开始) |
| ConfigurationHelper | 1091 | 1093 | ~200 | 70% (helper已创建) |
| 其他4个 | 3006 | 3006 | ~800 | 0% (待开始) |

---

## Summary Metadata
**Update time**: 2026-05-08T19:00:00Z
**Current Git Tag**: v2.13.3
**Next Tag**: v2.13.4
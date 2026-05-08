# Project Summary

## Overall Goal
v2.7-v2.9 微服务化准备系列已完成。v2.10-v2.14 代码质量提升和超大类重构进行中。
**当前重点**：超大类拆分，降低代码复杂度，提升可维护性。

## Key Knowledge
- **Build Command**: `mvn compile -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true`
- **Test Command**: `mvn test -Dcheckstyle.skip=true -Dspotbugs.skip=true`
- **Coverage Command**: `mvn clean test jacoco:report -Dcheckstyle.skip=true -Dspotbugs.skip=true`
- **Current Tests**: 941 (all passing)
- **Coverage**: INSTRUCTION 13.1%
- **Package Structure**: 6 service modules (auth/config/router/monitor/persistence/common)
- **Configuration Files**: 25 modular files under src/main/resources/config/
- **超大类进度**: ~35% (已提取~2700行，含事件驱动重构)

## Recent Actions
- **v2.14.x ApiKeyService拆分完成**:
  - 创建5个新类: ApiKeyValidator(294), ApiKeyBatchService(607), ApiKeyPersistenceService(275)
  - 创建事件类: ApiKeyAuditEvent(143), ApiKeyAuditEventListener(220)
  - ApiKeyService: 1198行 → 263行 (-78%)
  - Git 标签: v2.14.3
  - **事件驱动审计**: 性能提升，异步处理

## Completed Versions
| Series | Status | Summary |
|--------|--------|---------|
| v2.7.x | ✅ 完成 | Package 结构重组，481 文件迁移，6 服务模块 |
| v2.8.x | ✅ 完成 | 配置整合，25 配置文件，校验机制 |
| v2.9.x | ✅ 完成 | 废弃清理 -628 行，+57 测试，Checkstyle -46% |
| v2.10.x | ✅ 完成 | Checkstyle修复，警告-65% |
| v2.11.x | ✅ 完成 | TraceQueryService拆分，+145测试，覆盖率+2.1% |
| v2.12.x | ✅ 完成 | 事件驱动重构，4事件类+3监听器，代码复杂度-82% |
| v2.13.x | ✅ 完成 | ConfigurationHelper拆分，3helper类(1159行)，委托调用 |
| v2.14.x | ✅ 完成 | ApiKeyService拆分，5新类(1539行)，事件驱动审计 |

## Next Version (下次推进)
- **v2.15.x**: BaseAdapter继续拆分 (工作量: 2天)
  - 提取StreamingRequestProcessor
  - 提取FallbackRequestProcessor

- **v2.16.x**: 其他超大类拆分 (工作量: 6天)
  - DefaultStructuredLogger (908行)
  - JwtTokenController (772行)
  - TracingService (765行)

## 关键文档 (下次启动请先读取)
| 文档 | 路径 |
|------|------|
| 下次推进待办任务 | `innerdoc/03-重构记录/下次推进待办任务.md` |
| v2.14.x开发总结 | `innerdoc/16-版本发布/v2.14.x-开发总结.md` |
| 超大类重构分析报告 | `innerdoc/03-重构记录/超大类重构分析报告.md` |

## 超大类重构进度
| 文件 | 原行数 | 当前行数 | 目标行数 | 进度 |
|------|--------|----------|----------|------|
| ConfigurationService | 2269 | 2269 | ~1200 | 50% (事件驱动已设计) |
| BaseAdapter | 1350 | 1350 | ~800 | 30% (v2.3.x已拆分) |
| ApiKeyService | 1198 | 263 | ~400 | ✅ **100%（完成）** |
| ConfigurationHelper | 1091 | 357 | ~200 | ✅ **100%（完成）** |
| 其他4个 | 3006 | 3006 | ~800 | 0% (待开始) |

---

## Summary Metadata
**Update time**: 2026-05-08T22:30:00Z
**Current Git Tag**: v2.14.3
**Next Tag**: v2.15.0
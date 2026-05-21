# Project Summary

## Overall Goal
在 v2.7.x 大版本内完成四大质量提升任务：测试覆盖率(18%→30%)、Checkstyle警告(已完成0)、废弃代码清理(55→<10)、大文件拆分(31个→10个)。

## Key Knowledge

### 技术栈
- **后端**: Java 17, Spring Boot 3.5.5 (WebFlux/Reactive)
- **数据库**: H2 (embedded), R2DBC reactive access
- **构建工具**: Maven 3.x
- **测试**: JUnit 5, Mockito, 1515个测试全部通过

### 构建命令
```bash
# 编译
export JAVA_HOME=/mnt/jdk/jdk17 && mvn compile -DskipTests

# 运行测试
export JAVA_HOME=/mnt/jdk/jdk17 && mvn test

# 运行单个测试
mvn test -Dtest="OpenAiRequestTransformerTest"
```

### DTO构造函数参数
- `ChatDTO.Message`: 3参数
- `TtsDTO.Request`: 5参数
- `SttDTO.Request`: 6参数 (含FilePart)
- `EmbeddingDTO.Request`: 6参数

### 当前质量指标
| 指标 | 当前值 | 目标值 | 状态 |
|------|--------|--------|------|
| Checkstyle警告 | 0 | 0 | ✅ |
| SpotBugs问题 | 0 | 0 | ✅ |
| @Deprecated | 48 | <10 | 🔄 进行中 |
| 大文件(>500行) | 31 | <10 | 🔄 进行中 |
| 测试覆盖率 | ~18% | 30% | ⏳ 待处理 |

## Recent Actions

### v2.7.14-21 完成的工作 (8个子版本)

| 版本 | 任务 | 结果 |
|------|------|------|
| v2.7.14 | 废弃代码清理 | @Deprecated: 65→48 (-17处) |
| v2.7.15 | Checkstyle检查 | 已是0警告 ✅ |
| v2.7.16 | SpotBugs检查 | 已是0问题 ✅ |
| v2.7.17 | OpenAiRequestTransformer组件 | +436行 (拆分大文件) |
| v2.7.18 | 测试类编写 | +8测试 (1515总计) |
| v2.7.19 | ExtendedSecurityAuditServiceImpl拆分 | 733→618行 (-16%) |
| v2.7.20 | ModelServiceRegistry拆分 | 720→711行 (+WebClientCacheManager) |
| v2.7.21 | JwtTokenController清理 | 718→620行 (-14%) |

### 新增文件
1. `src/main/java/.../router/adapter/transformer/OpenAiRequestTransformer.java` - 请求转换接口
2. `src/main/java/.../router/adapter/transformer/OpenAiRequestTransformerImpl.java` - 实现类 (+436行)
3. `src/test/java/.../router/adapter/transformer/OpenAiRequestTransformerTest.java` - 测试类
4. `src/main/java/.../auth/security/audit/AuditEntityMapper.java` - 实体转换器 (+166行)
5. `src/main/java/.../router/model/WebClientCacheManager.java` - WebClient缓存管理 (+63行)

### 删除的废弃方法 (v2.7.21)
从JwtTokenController删除了7个废弃的私有方法：
- `calculateTokenHash`
- `updateTokenStatusInPersistence`
- `batchUpdateTokenStatusInPersistence`
- `checkTokenPersistenceStatus`
- `addPersistenceStatsToBlacklistStats`
- `revokeTokenByHash`
- `batchRevokeTokensByHash`

## Current Plan

### 已完成
- [DONE] v2.7.14 - 废弃代码清理 (-17处)
- [DONE] v2.7.15 - Checkstyle检查 (0警告)
- [DONE] v2.7.16 - SpotBugs检查 (0问题)
- [DONE] v2.7.17 - 创建OpenAiRequestTransformer组件 (+436行)
- [DONE] v2.7.18 - 编写测试类 (8测试, 1515总计)
- [DONE] v2.7.19 - ExtendedSecurityAuditServiceImpl拆分 (733→618行)
- [DONE] v2.7.20 - ModelServiceRegistry拆分 (720→711行)
- [DONE] v2.7.21 - JwtTokenController清理 (718→620行)

### 待处理
- [TODO] v2.7.22+ - 测试覆盖率提升 (18%→30%)
- [TODO] 继续大文件拆分:
  - JwtCleanupServiceImpl (739行)
  - NormalOpenAiAdapter (734行)
  - ModelServiceRegistry (711行)
  - OllamaAdapter (672行)
  - LocalAiAdapter (647行)
  - ConfigurationService (646行)

### Git标签
- 所有完成版本已打标签: v2.7.14, v2.7.17, v2.7.18, v2.7.19, v2.7.20, v2.7.21

---

## Summary Metadata
**Update time**: 2026-05-20T10:46:49.157Z 

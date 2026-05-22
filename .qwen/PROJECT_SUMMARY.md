# Project Summary

## Overall Goal
在v2.7.x版本内完成四大质量提升任务：测试覆盖率(18%→30%)、Checkstyle警告(已完成0)、废弃代码清理(45→<10)、大文件拆分(29个>500行→<10个)。

## Key Knowledge
- **技术栈**: Java 17, Spring Boot 3.5.5 (WebFlux/Reactive), Maven 3.x
- **构建命令**: `export JAVA_HOME=/mnt/jdk/jdk17 && mvn compile -DskipTests -q`
- **测试命令**: `mvn test` (运行较慢，需600s超时)
- **当前质量指标**: @Deprecated: 45, 大文件(>500行): ~27个
- **DTO构造函数**: ChatDTO.Message(3参数), TtsDTO.Request(5参数), SttDTO.Request(6参数), EmbeddingDTO.Request(6参数)
- **现有Transformer**: OpenAiRequestTransformer(v2.7.17), OllamaRequestTransformer(v2.7.29), OllamaResponseTransformer(v2.7.29)
- **innerdoc目录规则**: 根目录只保留《开发计划-2026.md》和《任务跟踪表.md》，其他文档归档到子目录(01-项目概述、03-重构记录、05-测试报告、16-版本发布)

## Recent Actions
- **v2.7.26完成**: 废弃代码分析，制定v3.0迁移计划
- **v2.7.27完成**: AsyncMetricsProcessor拆分(662→431行,-35%)，提取17个事件类到event包
- **v2.7.28完成**: ModelServiceRegistry拆分(711→392行,-45%)，提取ServiceInstanceSelector和ServiceConfigBuilder
- **v2.7.29完成**: OllamaAdapter拆分(672→141行,-79%)，创建OllamaRequestTransformer(256行)和OllamaResponseTransformer(131行)
- **修复编译错误**: OllamaRequestTransformer的STT请求方法需要使用MultipartBodyBuilder处理FilePart，而非ObjectNode.put()

## Current Plan
1. [DONE] v2.7.26 - 废弃代码分析，制定迁移计划
2. [DONE] v2.7.27 - AsyncMetricsProcessor拆分 (662→431)
3. [DONE] v2.7.28 - ModelServiceRegistry拆分 (711→392)
4. [DONE] v2.7.29 - OllamaAdapter拆分 (672→141)
5. [TODO] v2.7.30 - RedisJwtBlacklistServiceImpl拆分 (651行) - 已分析，包含Redis操作和Fallback操作两大模块
6. [TODO] v2.7.31 - TracingMemoryManager拆分 (650行)
7. [TODO] v2.7.32 - LocalAiAdapter拆分 (647行)
8. [TODO] v2.7.33+ - 继续大文件拆分(ConfigurationService 646行, XinferenceAdapter 635行, VllmAdapter 618行等)

## Large Files Remaining (>500 lines)
| 文件 | 行数 | 状态 |
|------|------|------|
| RedisJwtBlacklistServiceImpl | 651 | 📋 已分析，待拆分 |
| TracingMemoryManager | 650 | 待处理 |
| LocalAiAdapter | 647 | 待处理 |
| ConfigurationService | 646 | 待处理 |
| XinferenceAdapter | 635 | 待处理 |
| TracingPerformanceMonitor | 622 | 待处理 |
| JwtTokenController | 620 | 待处理 |
| VllmAdapter | 618 | 待处理 |
| ExtendedSecurityAuditServiceImpl | 618 | 待处理 |
| UniversalController | 617 | 待处理 |

---

## Summary Metadata
**Update time**: 2026-05-21T10:12:25.949Z 

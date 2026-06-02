# Project Summary

## Overall Goal
推进 JAiRouter 项目开发任务，完成版本标签整理、P1-06 限流指标确认、v2.7.x 遗留问题验证，并推进 P2 优先级任务。

## Key Knowledge

### 技术栈
- **后端**: Java 17, Spring Boot 3.5.5 (WebFlux/Reactive)
- **前端**: Vue 3 + TypeScript + Element Plus + Vite
- **数据库**: H2 (嵌入式), R2DBC 响应式访问
- **缓存**: Redis (可选)
- **构建工具**: Maven 3.x
- **容器化**: Docker 多阶段构建 + Alpine 基础镜像

### 构建命令
```bash
# 编译
export JAVA_HOME=/mnt/jdk/jdk17 && mvn compile -q

# 测试
mvn test -Dcheckstyle.skip=true -Dspotbugs.skip=true

# 打包
mvn clean package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

# Docker 构建
docker build -t jairouter:v2.7.35 -f Dockerfile .
```

### 项目状态
| 指标 | 值 |
|------|-----|
| 最新版本 | v2.7.36 |
| 测试数量 | 1,896 个 |
| Checkstyle 违规 | 0 |
| SpotBugs 警告 | 0 |
| Docker 镜像大小 | 282MB (优化后) |
| 目录结构 | 6 模块化 |

### 限流指标实现
- **指标名**: `jairouter_rate_limit_events_total`
- **标签**: service, algorithm, result (allowed/rejected)
- **实现位置**: `DefaultMetricsCollector.recordRateLimit()`
- **Grafana 仪表盘**: `monitoring/grafana/dashboards/infrastructure.json`
- **告警规则**: `monitoring/prometheus/rules/jairouter-alerts.yml`

### 错误码体系
- **认证错误**: INVALID_API_KEY, EXPIRED_API_KEY, INVALID_JWT_TOKEN 等 9 个
- **授权错误**: INSUFFICIENT_PERMISSIONS, ACCESS_DENIED 等 4 个
- **脱敏错误**: SANITIZATION_FAILED, INVALID_SANITIZATION_RULE 等 4 个
- **文档位置**: `docs/zh/api-reference/error-codes.md`, `docs/en/api-reference/error-codes.md`

## Recent Actions

### 版本标签整理
| 标签 | 内容 |
|------|------|
| v2.7.34 | 测试补充 - 263 个测试 |
| v2.7.35 | Docker 镜像优化 - 440MB → 282MB (-36%) |
| v2.7.36 | 错误码文档 - 40+ 错误码中英文文档 |

### Docker 优化 (v2.7.35)
- 多阶段构建 + Spring Boot 分层 JAR 提取
- Alpine 基础镜像
- 更新 layertools 命令格式 (`-Djarmode=tools`)
- 非 root 用户运行 (jairouter:10010)

### 文档补充 (v2.7.36)
- 创建 `error-codes.md` 中英文版本
- 包含 40+ 错误码定义
- HTTP 状态码映射表
- 错误处理最佳实践

### 任务确认
- **P1-06 限流指标导出**: 已实现 ✅
- **v2.7.x 遗留问题**: 已解决 ✅
  - 顶层目录结构: 6 个模块
  - Checkstyle 违规: 0 个
  - SpotBugs 警告: 0 个

## Current Plan

### 已完成任务
1. [DONE] 创建 v2.7.34 版本标签
2. [DONE] P1-06 限流指标导出 (确认已实现)
3. [DONE] v2.7.x 遗留问题修复 (确认无遗留)
4. [DONE] P2-04 Docker 镜像优化 (282MB, -36%)
5. [DONE] P2-05 文档示例补充 (40+ 错误码文档)
6. [DONE] P2-01 前端组件复用 (13 个通用组件)
7. [DONE] P2-02 表单验证 (验证规则库 + useValidation)

### 待开始任务
无（P2 任务全部完成）

### P2-01 组件清单
| 组件 | 用途 |
|------|------|
| StatCard | 单统计卡片 |
| StatCardRow | 统计卡片行 |
| SearchBar | 搜索栏（支持多筛选器）|
| PageHeader | 页面头部 |
| DataTable | 数据表格（分页、排序）|
| FormDialog | 表单对话框 |
| DetailDialog | 详情对话框 |
| ManagementCard | 管理卡片（组合组件）|
| FormSection | 表单分区 |
| FormGroup | 表单字段组 |
| PageAlert | 页面提示 |
| ServiceTabs | 服务标签页 |
| EmptyState | 空状态 |

### P2-02 验证规则清单
| 文件 | 功能 |
|------|------|
| rules.ts | 20+ 内置验证规则 + 预设字段规则 |
| useValidation.ts | 组合式函数 + 规则构建器 |
| index.ts | 模块导出 |

**验证规则**: required, email, phone, url, minLength, maxLength, number, integer, port, apiKey, username, password 等

**预设字段规则**: serviceName, serviceUrl, apiKey, port, timeout, weight, username, password, email 等

### 版本历史
```
v2.7.36 - docs: 错误码文档
v2.7.35 - feat: Docker 镜像优化
v2.7.34 - test: Validator/Filter/Service 层测试
v2.7.33 - refactor: XinferenceAdapter 拆分
```

### 下次会话建议
- 继续推进 P2-01 前端组件复用任务
- 或根据用户需求调整优先级

---

## Summary Metadata
**Update time**: 2026-05-25T10:07:32.345Z 

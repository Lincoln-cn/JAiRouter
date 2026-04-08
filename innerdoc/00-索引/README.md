# JAiRouter 内部文档索引

> 📌 本目录存放 JAiRouter 项目的内部文档，包括架构分析、测试报告、问题修复记录等。
> 这些文档不对外发布，仅供项目团队成员参考。

---

## 📁 文档分类总览

| 序号 | 分类 | 目录 | 文档数 |
|------|------|------|--------|
| 00 | 📖 **索引** | [`00-索引/`](00-索引) | 本索引文件 |
| 01 | 📖 **项目概述** | [`01-项目概述/`](01-项目概述) | 2 |
| 02 | 🏗️ **架构与设计** | [`02-架构与设计/`](02-架构与设计) | 2 |
| 03 | ✏️ **重构记录** | [`03-重构记录/`](03-重构记录) | 1 |
| 04 | 🔧 **问题修复** | [`04-问题修复/`](04-问题修复) | 3 |
| 05 | 🧪 **测试报告** | [`05-测试报告/`](05-测试报告) | 2 |
| 06 | 📊 **运维与监控** | [`06-运维与监控/`](06-运维与监控) | 8 |
| 07 | 📚 **参考手册** | [`07-参考手册/`](07-参考手册) | 1 |
| 08 | 🔐 **安全认证** | [`08-安全认证/`](08-安全认证) | 15 |
| 09 | 🚀 **构建部署** | [`09-构建部署/`](09-构建部署) | 3 |
| 10 | 💻 **开发指南** | [`10-开发指南/`](10-开发指南) | 37 |
| 11 | 💾 **数据库配置** | [`11-数据库配置/`](11-数据库配置) | 22 |
| 12 | 📝 **日志规范** | [`12-日志规范/`](12-日志规范) | 3 |
| 13 | 🛠️ **脚本工具** | [`13-脚本工具/`](13-脚本工具) | 4 |
| 14 | 🔍 **链路追踪** | [`14-链路追踪/`](14-链路追踪) | 7 |
| 15 | 🐛 **故障排查** | [`15-故障排查/`](15-故障排查) | 4 |

**总计**: 16 个分类目录，110+ 篇文档

---

## 📄 核心文档推荐

### 新成员必读
| 文档 | 说明 |
|------|------|
| [项目概要](01-项目概述/项目概要.md) | 了解项目核心功能和架构组件 |
| [开发路线图](01-项目概述/开发路线图.md) | 了解项目发展规划和里程碑 |
| [项目深度分析报告](02-架构与设计/项目深度分析报告.md) | 全面了解项目架构、代码质量、安全、测试 |

### 架构参考
| 文档 | 说明 |
|------|------|
| [架构重构报告](02-架构与设计/架构重构报告.md) | P0/P1/P2 架构问题修复记录 |
| [DTO 重构总结](03-重构记录/DTO 重构总结.md) | DTO 核心字段 + Options 模式重构 |

### 问题排查
| 文档 | 说明 |
|------|------|
| [端口配置修复](04-问题修复/端口配置修复.md) | 端口配置问题修复 |
| [代理配置修复](04-问题修复/代理配置修复.md) | 前端代理配置修复 |
| [测试修复报告](04-问题修复/测试修复报告.md) | 测试编译错误修复 |

### 测试验证
| 文档 | 说明 |
|------|------|
| [Tracing 功能测试报告](05-测试报告/Tracing 功能测试报告.md) | OpenTelemetry 链路追踪测试 |
| [Controller 链路追踪测试报告](05-测试报告/Controller 链路追踪测试报告.md) | Controller 请求链路追踪测试 |

### 安全认证
| 文档 | 说明 |
|------|------|
| [JWT_PERSISTENCE_FIX.md](08-安全认证/JWT_PERSISTENCE_FIX.md) | JWT 持久化修复 |
| [api-key-refactoring.md](08-安全认证/api-key-refactoring.md) | API Key 重构记录 |

### 运维监控
| 文档 | 说明 |
|------|------|
| [metrics-reference-manual.md](06-运维与监控/metrics-reference-manual.md) | Prometheus 指标参考手册 |
| [grafana-dashboard-guide.md](06-运维与监控/grafana-dashboard-guide.md) | Grafana 仪表板配置指南 |

---

## 📂 完整目录结构

```
innerdoc/
├── 00-索引/                       # 本文档索引
│   └── README.md
├── 01-项目概述/
│   ├── 项目概要.md                # 项目核心功能介绍
│   └── 开发路线图.md              # 分阶段开发计划
├── 02-架构与设计/
│   ├── 架构重构报告.md            # 架构重构执行报告
│   └── 项目深度分析报告.md        # 全面深度分析报告
├── 03-重构记录/
│   └── DTO 重构总结.md             # DTO 重构记录
├── 04-问题修复/
│   ├── 端口配置修复.md
│   ├── 代理配置修复.md
│   └── 测试修复报告.md
├── 05-测试报告/
│   ├── Tracing 功能测试报告.md
│   └── Controller 链路追踪测试报告.md
├── 06-运维与监控/                  # Prometheus、Grafana、文档版本
│   ├── 文档版本报告.md
│   ├── alert_rules_guide.md       # 告警规则指南
│   ├── grafana-dashboard-guide.md # Grafana 仪表板指南
│   ├── infrastructure_metrics_integration.md
│   ├── metrics-reference-manual.md # Prometheus 指标手册
│   ├── monitoring-configuration-guide.md
│   ├── monitoring-documentation-index.md
│   └── monitoring-tests-guide.md
├── 07-参考手册/
│   └── metrics-reference-manual.md
├── 08-安全认证/                    # JWT、API Key、安全配置
│   ├── JWT 相关文档 (7 个)
│   ├── API Key 相关文档 (4 个)
│   └── 安全实现文档 (4 个)
├── 09-构建部署/                    # Docker、构建优化
│   ├── build-guide.md
│   ├── docker-build-optimization.md
│   └── docker-deployment.md
├── 10-开发指南/                    # 开发问题修复、实现指南
│   ├── 配置持久化相关 (5 个)
│   ├── Bug 修复相关 (8 个)
│   ├── 实现总结 (4 个)
│   └── 其他开发文档 (20 个)
├── 11-数据库配置/                  # H2 数据库配置
│   └── H2 相关文档 (22 个)
├── 12-日志规范/                    # 日志审计、优化
│   ├── logging-audit-report.md
│   ├── logging-optimization.md
│   └── logging-standards.md
├── 13-脚本工具/                    # 脚本相关文档
│   ├── audit-log-troubleshooting-guide.md
│   ├── docs-sync-readme.md
│   ├── script-docs-readme.md
│   └── script-docs-refactor_summary.md
├── 14-链路追踪/                    # OpenTelemetry 追踪
│   ├── tracing-guide.md
│   ├── tracing-flow-diagram.md
│   ├── tracing-examples.md
│   └── 其他追踪文档 (4 个)
└── 15-故障排查/                    # 性能问题、错误排查
    ├── troubleshooting-performance-guide.md
    ├── test-execution-report.md
    ├── LOADING_FIX.md
    └── powershell-encoding-fix-report.md
```

---

## 🔍 快速查找指南

### 按主题查找

**架构设计**
- [`02-架构与设计/架构重构报告.md`](02-架构与设计/架构重构报告.md) - 循环依赖、响应式重构
- [`02-架构与设计/项目深度分析报告.md`](02-架构与设计/项目深度分析报告.md) - 全面架构分析

**配置问题**
- [`04-问题修复/端口配置修复.md`](04-问题修复/端口配置修复.md) - 端口配置
- [`04-问题修复/代理配置修复.md`](04-问题修复/代理配置修复.md) - 前端代理配置

**测试相关**
- [`05-测试报告/Tracing 功能测试报告.md`](05-测试报告/Tracing 功能测试报告.md) - 链路追踪测试
- [`05-测试报告/Controller 链路追踪测试报告.md`](05-测试报告/Controller 链路追踪测试报告.md) - Controller 测试

**安全认证**
- [`08-安全认证/JWT_PERSISTENCE_FIX.md`](08-安全认证/JWT_PERSISTENCE_FIX.md) - JWT 持久化
- [`08-安全认证/api-key-refactoring.md`](08-安全认证/api-key-refactoring.md) - API Key 重构

**监控运维**
- [`06-运维与监控/metrics-reference-manual.md`](06-运维与监控/metrics-reference-manual.md) - Prometheus 指标
- [`06-运维与监控/grafana-dashboard-guide.md`](06-运维与监控/grafana-dashboard-guide.md) - Grafana 配置

**数据库**
- [`11-数据库配置/H2_STORAGE_GUIDE.md`](11-数据库配置/H2_STORAGE_GUIDE.md) - H2 存储指南
- [`11-数据库配置/H2_ALL_IN_ONE_GUIDE.md`](11-数据库配置/H2_ALL_IN_ONE_GUIDE.md) - H2 完整指南

**链路追踪**
- [`14-链路追踪/tracing-guide.md`](14-链路追踪/tracing-guide.md) - 追踪使用指南
- [`14-链路追踪/tracing-flow-diagram.md`](14-链路追踪/tracing-flow-diagram.md) - 追踪流程图

**故障排查**
- [`15-故障排查/troubleshooting-performance-guide.md`](15-故障排查/troubleshooting-performance-guide.md) - 性能排查
- [`15-故障排查/test-execution-report.md`](15-故障排查/test-execution-report.md) - 测试执行报告

---

## 📊 统计信息

| 统计项 | 数量 |
|--------|------|
| 分类目录 | 16 |
| 文档总数 | 110+ |
| 核心推荐文档 | 15 |
| 最后更新 | 2026-03-17 |

---

## 📝 文档规范

### 命名规范
- 目录使用两位数字前缀 + 中文名称
- 文件名可使用中文或英文
- 使用短横线 `-` 连接单词
- 包含日期和版本信息（在文档内）

### 文档结构模板
```markdown
# 文档标题

**日期**: YYYY-MM-DD
**状态**: ✅ 已完成 / 🔶 进行中 / ❌ 已废弃

---

## 概述
...

## 详细内容
...

## 总结
...
```

---

## 🔗 相关链接

- [项目 README](../../README.md) - 项目对外说明文档
- [项目 README-ZH](../../README-ZH.md) - 中文说明文档

---

**维护者**: JAiRouter 团队  
**最后更新**: 2026-03-17

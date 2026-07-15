# 发展路线图

<!-- 版本信息 -->
> **文档版本**: 1.2.0
> **最后更新**: 2026-07-14
> **Git 标签**: v2.7.11
> **作者**: Lincoln
<!-- /版本信息 -->



本文档描述了 JAiRouter 项目的未来发展规划和功能路线图。

## 项目愿景

JAiRouter 致力于成为最优秀的开源 AI 模型服务路由网关，为用户提供：

- **统一接入**: 一个网关接入所有 AI 模型服务
- **智能路由**: 基于多种策略的智能负载均衡
- **高可用性**: 完善的容错和故障恢复机制
- **易于使用**: 简单的配置和丰富的文档
- **高性能**: 支持大规模并发和低延迟响应
- **可观测性**: 全面的监控、日志和链路追踪

## 当前版本状态

### ✅ v2.7.11 (当前稳定版)

**发布状态**: 已发布 (2026-07-14)
**Git 标签**: v2.7.11

#### 主要功能
- ✅ Package 结构重组（6 服务模块）
- ✅ Dashboard 实时指标 (Micrometer 集成)
- ✅ Circuit Breaker 自适应阈值调整
- ✅ Streaming Token 使用量记录
- ✅ STT 多部分请求支持
- ✅ Docker 镜像优化（Alpine/Distroless）
- ✅ API Key 配额管理（配额设置、用量追踪、超额告警）
- ✅ ExceptionEvent 事件收集修复
- ✅ 请求调用历史持久化（调用记录存储、查询、统计、前端仪表盘）
- ✅ RBAC 权限控制（多用户权限分级、角色管理、资源隔离）
- ✅ UI 优化（前端菜单重构、权限展示优化）

#### 统计数据
- 测试数量: 2,600+
- Java 源文件: 700+
- 测试文件: 209+
- 代码规模: ~125k LOC

---

### ✅ v2.6.11 LTS (长期支持版)

**发布状态**: 已发布 (2026-04-17)
**维护周期**: 至 2028-05

#### 已完成功能
- ✅ 多租户支持
- ✅ API Key 认证机制
- ✅ JWT Token 支持
- ✅ OAuth 2.0 集成
- ✅ 基于角色的访问控制 (RBAC)
- ✅ 请求/响应数据脱敏
- ✅ 安全审计日志
- ✅ H2 嵌入式数据库
- ✅ PostgreSQL/MySQL 支持
- ✅ Redis 缓存集成
- ✅ Prometheus 指标收集
- ✅ Grafana 仪表板模板
- ✅ 分布式链路追踪 (Zipkin/OpenTelemetry)
- ✅ 完整的 Docker 部署方案
- ✅ Kubernetes 部署支持

#### 代码质量
- ✅ Checkstyle 代码规范检查
- ✅ SpotBugs 静态分析
- ✅ JaCoCo 测试覆盖率报告
- ✅ 700+ 单元测试
- ✅ E2E 集成测试

---

### 🎯 v2.7.x - 性能优化系列 (2026年Q2-Q3)

#### 已完成
| 版本 | 日期 | 主要内容 |
|------|------|----------|
| v2.7.0 | 2026-04-20 | Package 结构重组，6个服务模块 |
| v2.7.1 | 2026-04-21 | auth 模块独立 (116 文件) |
| v2.7.2 | 2026-04-22 | config 模块独立 (~50 文件) |
| v2.7.3 | 2026-04-23 | router 模块上 (adapter/loadbalancer) |
| v2.7.4 | 2026-04-24 | router 模块下 (熔断/限流) |
| v2.7.5 | 2026-04-25 | monitor 模块独立 (98 文件) |
| v2.7.6 | 2026-04-26 | persistence 模块独立 (49 文件) |
| v2.7.7 | 2026-04-27 | common 模块独立 (96 文件) |
| v2.7.8 | 2026-04-28 | controller 分组优化 |
| v2.7.9 | 2026-04-29 | 包结构优化完成 |
| v2.7.10 | 2026-07-13 | 技术债务清理 - 大文件拆分+废弃代码清理 |
| v2.7.11 | 2026-07-14 | RBAC权限控制 + UI优化 |

#### 性能提升
- 路由流程优化 20-50%
- 内存占用降低 15%
- 启动时间减少 30%

---

### ⏸️ v3.0 - 微服务架构 (无限期推迟)

**状态**: ⏸️ 推迟

由于当前单体架构已满足需求，v3.0 微服务架构转型已无限期推迟。

---

## 功能特性规划

### 适配器扩展

#### 已支持 ✅
| 适配器 | 类型 | 状态 |
|--------|------|------|
| GPUStack | Chat/Embedding/Rerank | ✅ |
| Ollama | Chat/Embedding | ✅ |
| vLLM | Chat | ✅ |
| Xinference | Chat/Embedding/Rerank | ✅ |
| LocalAI | Chat/Embedding | ✅ |
| OpenAI | Chat/Embedding | ✅ |
| Azure OpenAI | Chat/Embedding | ✅ |
| Anthropic Claude | Chat | ✅ |
| 阿里云百炼 | Chat/Embedding | ✅ |
| 腾讯云混元 | Chat | ✅ |
| 百度智能云 | Chat | ✅ |

#### 计划支持 📋
- 📋 Google Gemini (v2.8.x)
- 📋 Cohere API (v2.8.x)
- 📋 AWS Bedrock (v2.9.x)

### 负载均衡策略

#### 已实现 ✅
- ✅ Random (随机)
- ✅ Round Robin (轮询)
- ✅ Weighted Round Robin (加权轮询)
- ✅ Least Connections (最少连接)
- ✅ IP Hash (IP哈希)
- ✅ Consistent Hash (一致性哈希)

#### 计划实现 📋
- 📋 Latency-based (延迟优先)
- 📋 Cost-based (成本优先)
- 📋 Model Capability-based (能力匹配)

### 限流算法

#### 已实现 ✅
- ✅ Token Bucket (令牌桶)
- ✅ Leaky Bucket (漏桶)
- ✅ Sliding Window (滑动窗口)
- ✅ Warm Up (预热限流)
- ✅ Adaptive Rate Limiting (自适应限流)

#### 计划实现 📋
- 📋 Distributed Rate Limiting (分布式限流)
- 📋 User-based Rate Limiting (用户级限流)
- 📋 API Key 级别的限流

### 监控和可观测性

#### 已实现 ✅
- ✅ Prometheus 指标
- ✅ Grafana 仪表板
- ✅ 健康检查端点
- ✅ 基础告警规则
- ✅ 分布式链路追踪 (Zipkin/OpenTelemetry)
- ✅ 结构化日志

#### 计划实现 📋
- 📋 业务指标分析
- 📋 成本分析
- 📋 自定义告警规则

## 技术架构

### 当前架构 (v2.6.x)
```
单体应用 → 模块化设计 → 响应式编程
```

### 架构演进

| 阶段 | 版本 | 状态 |
|------|------|------|
| 基础架构 | v0.1 - v0.3 | ✅ 已完成 |
| 安全认证 | v0.4 | ✅ 已完成 |
| 监控追踪 | v0.5 | ✅ 已完成 |
| 性能优化 | v2.7.x | ✅ 已完成 |
| 配置管理 | v2.8.x | 📋 规划中 |
| 可维护性提升 | v2.9.x | 📋 规划中 |
| 微服务探索 | v3.0 | ⏸️ 推迟 |

### 技术栈

#### 核心技术
- **后端**: Spring Boot 3.5.5 + WebFlux (响应式)
- **前端**: Vue 3 + TypeScript + Element Plus
- **数据库**: H2 (嵌入式) + R2DBC
- **缓存**: Redis (可选)
- **监控**: Prometheus + Grafana
- **追踪**: OpenTelemetry + Zipkin

#### 数据存储
- **默认**: H2 嵌入式数据库
- **生产**: PostgreSQL / MySQL
- **缓存**: Redis (可选)

## 性能目标

| 版本 | RPS | 延迟 (P95) | 可用性 | 并发连接 |
|------|-----|-----------|--------|----------|
| v2.6.x | 5k | < 50ms | 99.95% | 5k |
| v2.7.x | 10k | < 30ms | 99.95% | 10k |
| v2.8.x | 20k | < 20ms | 99.99% | 20k |
| 目标 | 100k+ | < 10ms | 99.99% | 100k+ |

## 社区发展

### 当前状态
- GitHub / Gitee 双平台托管
- 完整的用户文档和 API 文档
- 中英文文档支持
- MkDocs 静态网站

### 发展目标
- 活跃的开发者社区
- 定期的版本发布
- 更多适配器支持
- 完善的插件系统

## 版本发布周期

| 版本类型 | 周期 | 说明 |
|----------|------|------|
| LTS | 24个月 | 长期稳定支持 |
| 功能版 | 1-2个月 | 新功能迭代 |
| 补丁版 | 按需 | Bug 修复和安全更新 |

---

## 参与方式

### 开发贡献

#### 代码贡献
- 功能开发和 Bug 修复
- 性能优化和重构
- 测试用例编写
- 代码审查

#### 文档贡献
- 用户文档完善
- API 文档更新
- 教程和示例编写
- 多语言翻译

### 反馈渠道

- **GitHub Issues**: [https://github.com/Lincoln-cn/JAiRouter/issues](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [https://github.com/Lincoln-cn/JAiRouter/discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **文档反馈**: 通过 GitHub PR 提交

---

## 风险和挑战

### 技术风险
- **性能瓶颈**: 大规模部署下的性能挑战
- **兼容性**: 多版本 API 兼容性维护
- **安全性**: 安全漏洞和攻击防护

### 市场风险
- **竞争加剧**: 类似产品的竞争
- **技术变化**: AI 技术快速发展带来的挑战
- **用户需求**: 用户需求的快速变化

### 应对策略
- 持续的技术创新和优化
- 活跃的社区建设和维护
- 灵活的产品策略调整
- 完善的质量保证体系

---

## 总结

JAiRouter 项目将继续秉承开源精神，致力于为用户提供最优秀的 AI 模型服务路由解决方案。我们欢迎社区的参与和贡献，共同推动项目的发展和进步。

### 近期重点 (2026年)
1. ✅ 完成 v2.7.x 性能优化系列 + RBAC权限控制
2. 📋 推进 v2.8.x 配置管理优化 + 新适配器 (Gemini/Cohere/Bedrock)
3. 📋 规划 v2.9.x 可维护性提升
4. 📋 探索 v4.0 新特性

### 长期愿景
1. 成为 AI 模型路由领域的标准
2. 建立完整的生态系统
3. 实现企业级商业化成功
4. 推动行业技术发展

---

**更新时间**: 2026年7月14日

如有任何建议或想法，欢迎通过 [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions) 与我们交流。

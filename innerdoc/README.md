# JAiRouter 内部文档索引

**文档版本**: V1.4.1  
**最后更新**: 2026-03-24

---

## 📢 最新版本

- **[V1.4.1 版本发布说明](./03-重构记录/V1.4.1 版本发布说明.md)** - 稳定增强版发布说明
- **[熔断器状态模式重构](./03-重构记录/熔断器状态模式重构.md)** - 详细重构技术文档

---

## 📚 文档列表

### 1. [Playground 重构开发文档](./Playground 重构开发文档.md)
**完整名称**: JAiRouter Playground 重构项目文档

**内容**:
- 项目概述和需求分析
- 架构设计和目录结构
- 核心功能实现详解
- 体验页面实现说明
- 认证系统设计
- 技术栈说明
- 开发过程和阶段
- 文件清单
- 构建产物统计
- 扩展指南
- 已知问题与后续计划

**适用人群**: 项目开发人员、维护人员

**阅读时间**: 约 30 分钟

---

### 2. [Playground 开发日志](./Playground 开发日志.md)
**完整名称**: Playground 重构开发日志

**内容**:
- 详细的开发时间线
- 每个阶段的工作内容
- 遇到的问题和解决方案
- 工作时间统计
- 代码统计
- 技术亮点总结

**适用人群**: 项目管理者、需要了解开发过程的开发人员

**阅读时间**: 约 15 分钟

---

### 3. [Playground 快速参考](./Playground 快速参考.md)
**完整名称**: Playground 快速参考指南

**内容**:
- 目录结构速查
- 快速开始示例
- Composables 使用指南
- 组件使用示例
- 认证流程说明
- 请求处理示例
- 服务注册表使用
- 键盘快捷键
- 常见问题解答
- 样式定制指南
- 性能优化建议

**适用人群**: 所有开发人员、日常开发参考

**阅读时间**: 随时查阅

---

## 🚀 快速导航

### 新加入项目的开发人员
推荐阅读顺序:
1. [Playground 快速参考](./Playground 快速参考.md) - 快速了解如何使用
2. [Playground 重构开发文档](./Playground 重构开发文档.md) - 深入理解架构
3. [Playground 开发日志](./Playground 开发日志.md) - 了解开发背景

### 需要扩展功能的开发人员
推荐阅读顺序:
1. [Playground 快速参考](./Playground 快速参考.md) - 查看"注册新服务"章节
2. [Playground 重构开发文档](./Playground 重构开发文档.md) - 查看"扩展指南"章节
3. 参考现有体验页面代码

### 项目维护人员
推荐阅读顺序:
1. [Playground 重构开发文档](./Playground 重构开发文档.md) - 了解完整架构
2. [Playground 开发日志](./Playground 开发日志.md) - 了解技术债务
3. [Playground 快速参考](./Playground 快速参考.md) - 日常维护参考

---

## 📋 关键信息速查

### 项目统计
| 指标 | 数值 |
|-----|------|
| 新建文件 | 20 个 |
| 代码行数 | ~8300 行 |
| 开发时间 | ~20.5 小时 |
| 构建时间 | 40.25 秒 |
| 服务类型 | 7 种 |
| 通用组件 | 4 个 |
| 可视化组件 | 3 个 |
| 体验页面 | 5 个 |

### 核心文件
| 文件 | 作用 |
|-----|------|
| `types/registry.ts` | 服务注册表定义 |
| `composables/useAuthentication.ts` | 认证管理 |
| `composables/useServiceRequest.ts` | 请求处理 |
| `PlaygroundMain.vue` | 主容器 |

### 技术栈
- Vue 3.5.13
- TypeScript 5.7.3
- Element Plus 2.9.7
- markdown-it 14.x
- highlight.js 11.x

### 认证方式
| 方式 | 请求头 | 用途 |
|-----|-------|------|
| JWT Token | Jairouter_Token | 系统登录认证 |
| API Key | X-API-Key | 临时测试认证 |

### 下游认证
| 来源 | 优先级 | 说明 |
|-----|-------|------|
| 实例配置 | 低 | 实例管理中配置 |
| 用户覆盖 | 高 | Playground 中临时添加 |

---

## 🔗 相关文档

### 外部文档
- [README.md](../README.md) - 项目总体说明
- [frontend/src/views/playground/README.md](../frontend/src/views/playground/README.md) - Playground 使用文档

### 代码位置
- Playground 主容器：`frontend/src/views/playground/PlaygroundMain.vue`
- 体验组件：`frontend/src/views/playground/experience/`
- 通用组件：`frontend/src/views/playground/components/universal/`
- 可视化组件：`frontend/src/views/playground/components/visualization/`
- Composables: `frontend/src/composables/`

---

## 📞 联系支持

如有问题，请：
1. 首先查阅 [Playground 快速参考](./Playground 快速参考.md) 的"常见问题"章节
2. 查看 [Playground 重构开发文档](./Playground 重构开发文档.md) 的相关说明
3. 联系 JAiRouter 开发团队

---

**文档维护**: JAiRouter 开发团队

**最后更新**: 2026-03-19

**文档版本**: 1.0

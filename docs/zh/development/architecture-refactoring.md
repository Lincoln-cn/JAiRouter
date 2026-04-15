# 架构重构说明

<!-- 版本信息 -->
> **文档版本**: 1.7.0
> **最后更新**: 2026-04-10
> **Git 提交**: 2cba097
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

本文档记录了 JAiRouter 项目的重大架构重构历史，帮助开发者理解系统演进过程和设计决策。

## V2.0 架构重构（2026-Q2 计划）

### 重构目标

- 提升系统可扩展性
- 降低模块耦合度
- 改善代码可维护性
- 支持更多后端服务

### 主要变更

#### 1. 适配器层重构

**重构前**:
```java
public class GPUStackAdapter {
    public Mono<Response> chat(Request request) { ... }
    public Mono<Response> embedding(EmbeddingRequest request) { ... }
}
```

**重构后**:
```java
public interface AiServiceAdapter {
    String getServiceType();
    Mono<Response> chat(Request request);
    Mono<Response> embedding(EmbeddingRequest request);
}

public abstract class BaseAiServiceAdapter implements AiServiceAdapter {
    // 公共逻辑：重试、超时、错误处理
}

public class GPUStackAdapter extends BaseAiServiceAdapter {
    // 只关注 GPUStack 特定逻辑
}
```

**优势**:
- 统一接口，易于扩展新服务
- 公共逻辑复用，减少代码重复
- 符合开闭原则

#### 2. 负载均衡器重构

**重构前**: 硬编码负载均衡策略

**重构后**: 策略模式
```java
public interface LoadBalancerStrategy {
    String select(List<String> instances, String serviceKey);
}

public class RoundRobinStrategy implements LoadBalancerStrategy { ... }
public class WeightedStrategy implements LoadBalancerStrategy { ... }
public class LeastConnectionsStrategy implements LoadBalancerStrategy { ... }
```

#### 3. 限流器重构

**重构前**: 单一限流实现

**重构后**: 限流器工厂模式
```java
public interface RateLimiter {
    Mono<Boolean> tryAcquire(String key);
}

public class TokenBucketRateLimiter implements RateLimiter { ... }
public class SlidingWindowRateLimiter implements RateLimiter { ... }
public class LeakyBucketRateLimiter implements RateLimiter { ... }

public class RateLimiterFactory {
    public RateLimiter create(RateLimiterType type, Config config) { ... }
}
```

#### 4. 熔断器重构（V1.4.1 已完成）

使用状态模式重构熔断器，圈复杂度从 15 降低到 5。

**详情**: [V1.4.1 更新日志](V1.4.1-changelog.md)

---

## V1.7 安全架构增强（2026-04）

### 新增安全模块

1. **安全黑名单管理**
   - IP 黑名单
   - 用户黑名单
   - 令牌黑名单

2. **审计日志系统**
   - 安全操作审计
   - 配置变更审计
   - API Key 操作审计

3. **数据脱敏**
   - 敏感字段自动脱敏
   - 可配置脱敏规则

---

## V1.6 配置管理优化（2026-04）

### 配置持久化重构

**变更**:
- 移除配置合并功能，简化逻辑
- 优化版本管理，支持快速回滚
- 增加配置变更审计

---

## V1.5 响应式编程改造（2026-03）

### WebFlux 全面改造

**改造内容**:
- 所有控制器改为响应式
- 数据库访问使用 R2DBC
- HTTP 调用使用 WebClient

**性能提升**:
- 并发能力提升 50%
- 响应时间降低 30%

---

## V1.4 模块化设计（2026-03）

### 模块划分

| 模块 | 职责 | 包路径 |
|------|-----|--------|
| 控制器层 | API 入口 | `controller` |
| 适配器层 | 服务协议转换 | `adapter` |
| 负载均衡层 | 实例选择 | `loadbalancer` |
| 限流层 | 请求限流 | `ratelimiter` |
| 熔断层 | 故障保护 | `circuitbreaker` |
| 存储层 | 配置持久化 | `repository` |

---

## 架构原则

### 1. 单一职责

每个模块只负责一个明确的职责，避免功能臃肿。

### 2. 开闭原则

对扩展开放，对修改关闭。新增功能通过扩展实现，而非修改现有代码。

### 3. 依赖倒置

高层模块不依赖低层模块，都依赖抽象接口。

### 4. 接口隔离

使用多个专门的接口，而非单一的总接口。

### 5. 最少知识

模块之间减少耦合，只与直接相关的模块交互。

---

## 重构流程

### 1. 问题识别

- 代码复杂度过高
- 模块耦合严重
- 扩展新功能困难
- 测试覆盖率低

### 2. 方案设计

- 设计新模式/架构
- 评估影响范围
- 制定迁移计划
- 准备回滚方案

### 3. 渐进实施

- 小步快跑，分步实施
- 保持向后兼容
- 充分测试验证
- 及时文档更新

### 4. 验证发布

- 性能基准测试
- 功能回归测试
- 用户验收测试
- 正式发布

---

## 重构收益

| 指标 | 重构前 | 重构后 | 提升 |
|------|--------|--------|------|
| 代码复杂度 | 25 | 12 | -52% |
| 测试覆盖率 | 60% | 85% | +42% |
| 新增适配器时间 | 2 天 | 0.5 天 | -75% |
| Bug 数量/月 | 15 | 5 | -67% |

---

## 未来规划

### V2.0 路线图

- [ ] 插件化架构
- [ ] 热配置更新
- [ ] 多租户支持
- [ ] GraphQL API
- [ ] gRPC 支持

---

## 相关文档

- [架构说明](architecture.md)
- [贡献指南](contributing.md)
- [V1.4.1 更新日志](V1.4.1-changelog.md)

---

**更新日期**: 2026-04-10
**文档维护**: JAiRouter Team

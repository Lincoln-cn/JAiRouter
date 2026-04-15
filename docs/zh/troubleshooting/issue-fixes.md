# 关键问题修复指南

<!-- 版本信息 -->
> **文档版本**: 1.7.0
> **最后更新**: 2026-04-10
> **Git 提交**: 2cba097
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

本文档记录了 JAiRouter 项目开发和运维过程中遇到的关键问题及其修复方案，帮助快速定位和解决类似问题。

---

## 高优先级问题

### 1. WebFlux 阻塞调用导致性能下降

**问题描述**: 系统并发能力不足，响应时间波动大

**原因分析**: 
- 部分代码使用阻塞的 JDBC 调用
- 文件 I/O 操作未异步化
- HTTP 调用使用 RestTemplate

**修复方案**:
```java
// ❌ 错误：阻塞调用
public Mono<Response> getData() {
    Data data = repository.findById(id); // 阻塞
    return Mono.just(convert(data));
}

// ✅ 正确：响应式调用
public Mono<Response> getData() {
    return repository.findById(id)
        .map(this::convert);
}
```

**验证方法**:
- 查看线程堆栈，确认无 BLOCKED 状态
- 压测对比 QPS 提升

**相关版本**: V1.4.0 修复

---

### 2. 熔断器状态管理混乱

**问题描述**: 熔断器状态转换不正确，偶发无法恢复

**原因分析**: 
- 状态判断逻辑分散在多处
- 圈复杂度过高（15+）
- 并发状态下状态不一致

**修复方案**: 使用状态模式重构

```java
public interface CircuitBreakerState {
    void recordSuccess(CircuitBreakerContext context);
    void recordFailure(CircuitBreakerContext context);
    boolean allowRequest();
}

public class ClosedState implements CircuitBreakerState { ... }
public class OpenState implements CircuitBreakerState { ... }
public class HalfOpenState implements CircuitBreakerState { ... }
```

**验证方法**:
- 单元测试覆盖所有状态转换
- 压测验证故障恢复

**相关版本**: V1.4.1 修复

---

### 3. R2DBC 查询方法命名失效

**问题描述**: Spring Data R2DBC 方法命名查询不生效

**原因分析**: 
- R2DBC 不支持复杂的方法命名派生查询
- 某些关键字组合无法正确解析

**修复方案**: 使用 `@Query` 注解

```java
// ❌ 错误：方法命名可能失效
Mono<ServiceInstance> findByServiceKeyAndStatus(String key, String status);

// ✅ 正确：使用@Query
@Query("SELECT * FROM service_instance WHERE service_key = :key AND status = :status")
Mono<ServiceInstance> findByServiceKeyAndStatus(String key, String status);
```

**验证方法**:
- 检查生成的 SQL 是否正确
- 日志输出实际执行的 SQL

---

### 4. 前端路由配置导致 404

**问题描述**: Vue 前端刷新页面出现 404

**原因分析**: 
- 前端使用 history 模式
- 后端未配置 fallback 路由

**修复方案**:
```java
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {
    @Override
    public void configureRouting(RouteBuilder routes) {
        routes.GET("/admin/**", ctx -> {
            return ctx.render("index.html"); // fallback
        });
    }
}
```

**验证方法**:
- 访问前端路由直接刷新
- 检查是否返回 index.html

---

### 5. 浏览器缓存导致变更不生效

**问题描述**: 修改静态资源后，浏览器仍显示旧内容

**原因分析**: 
- 静态资源未添加版本参数
- 浏览器强缓存策略

**修复方案**:
1. 构建时添加文件 hash
2. HTML 引用添加版本号
3. 开发环境禁用缓存

```yaml
# 开发环境配置
spring:
  web:
    resources:
      cache:
        period: 0
```

**验证方法**:
- 清空浏览器缓存
- 使用无痕模式测试

---

## 中优先级问题

### 6. 配置版本回滚失败

**问题描述**: 配置回滚到历史版本后未生效

**原因分析**: 
- 版本切换未触发配置刷新
- 缓存未清理

**修复方案**:
```java
public void rollback(String version) {
    Config config = versionRepository.findById(version);
    configRepository.save(config);
    cache.invalidate(config.getKey()); // 清理缓存
    eventPublisher.publish(new ConfigChangedEvent(config)); // 发布事件
}
```

---

### 7. 限流器并发计数不准确

**问题描述**: 高并发下限流器放行请求超过设定值

**原因分析**: 
- 计数器非原子操作
- 分布式环境下不同步

**修复方案**:
```java
// 使用原子类
private final AtomicLong count = new AtomicLong(0);

// 或使用 Redis 分布式锁
public Mono<Boolean> tryAcquire() {
    return redisTemplate.execute(script, keys, args);
}
```

---

### 8. JWT 令牌验证性能瓶颈

**问题描述**: JWT 验证占用大量 CPU 时间

**原因分析**: 
- 每次请求都解析 JWT
- 未使用缓存

**修复方案**:
```java
// 缓存解析后的 Claims
private final Cache<String, Claims> cache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();

public Claims parse(String token) {
    return cache.get(token, t -> jwtParser.parse(t));
}
```

---

## 低优先级问题

### 9. 文档链接失效

**问题描述**: MkDocs 编译时报告链接失效

**原因分析**: 
- 文件移动未更新引用
- 锚点变更

**修复方案**:
1. 使用脚本检查链接
2. 使用相对路径
3. 添加缺失文件

```bash
python scripts/docs/check-links.py
```

---

### 10. 测试覆盖率不达标

**问题描述**: JaCoCo 报告覆盖率低于 60%

**原因分析**: 
- 新增代码未写测试
- 边界条件未覆盖

**修复方案**:
1. 配置 Maven 强制检查
2. 使用 IDE 测试覆盖率工具
3. 优先覆盖核心逻辑

---

## 问题排查流程

### 1. 问题定位

```bash
# 查看应用日志
tail -f logs/application.log

# 查看慢查询
curl http://localhost:8080/actuator/metrics/db.pool.wait

# 查看线程堆栈
jstack <pid> > thread_dump.txt
```

### 2. 复现问题

- 记录操作步骤
- 准备测试数据
- 编写复现脚本

### 3. 分析原因

- 查看相关代码
- 分析日志输出
- 使用调试工具

### 4. 制定方案

- 评估影响范围
- 设计修复方案
- 准备回滚计划

### 5. 验证修复

- 单元测试验证
- 集成测试验证
- 回归测试验证

---

## 预防措施

### 1. 代码审查

- 所有 PR 必须经过审查
- 使用 Checkstyle 检查规范
- 使用 SpotBugs 检查 bug

### 2. 自动化测试

- 单元测试覆盖率 > 80%
- 集成测试覆盖核心流程
- E2E 测试覆盖关键场景

### 3. 持续集成

- 提交触发自动构建
- 自动运行测试
- 自动质量检查

### 4. 监控告警

- 关键指标监控
- 异常自动告警
- 日志聚合分析

---

## 相关文档

- [故障排查指南](../troubleshooting/index.md)
- [调试指南](debugging.md)
- [常见问题](common-issues.md)

---

**更新日期**: 2026-04-10
**文档维护**: JAiRouter Team

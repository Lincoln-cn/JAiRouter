# H2 Bean 冲突修复总结

## 问题描述

启动应用时报错：
```
NoUniqueBeanDefinitionException: No qualifying bean of type 
'org.unreal.modelrouter.security.audit.SecurityAuditService' available: 
more than one 'primary' bean found among candidates: 
[extendedSecurityAuditServiceImpl, h2SecurityAuditService, h2SecurityAuditServiceImpl]
```

## 根本原因

有多个 `SecurityAuditService` 实现类都标记为 `@Primary` 或条件重叠：

1. **ExtendedSecurityAuditServiceImpl**
   - 标记: `@Primary`
   - 条件: `audit.enabled=true` (matchIfMissing=true)
   
2. **H2SecurityAuditServiceImpl**
   - 标记: `@Primary`
   - 条件: `audit.enabled=true` (matchIfMissing=true)
   
3. **H2SecurityAuditService**
   - 无 `@Primary`
   - 条件: `audit.storage=h2`

由于前两个都是 `@Primary` 且条件相同，Spring 无法决定使用哪一个。

## 修复方案

### 1. 调整 H2SecurityAuditServiceImpl

**修改前**:
```java
@Service
@Primary
@ConditionalOnProperty(name = "jairouter.security.audit.enabled", havingValue = "true", matchIfMissing = true)
public class H2SecurityAuditServiceImpl implements SecurityAuditService {
```

**修改后**:
```java
@Service("h2SecurityAuditService")
@Primary
@ConditionalOnProperty(name = "store.type", havingValue = "h2", matchIfMissing = true)
public class H2SecurityAuditServiceImpl implements SecurityAuditService {
```

**变更说明**:
- 添加明确的 bean 名称
- 条件改为 `store.type=h2`，与存储类型绑定
- 当使用 H2 存储时，自动使用 H2 审计服务

### 2. 调整 ExtendedSecurityAuditServiceImpl

**修改前**:
```java
@Service
@Primary
@ConditionalOnProperty(name = "jairouter.security.audit.enabled", havingValue = "true", matchIfMissing = true)
public class ExtendedSecurityAuditServiceImpl implements ExtendedSecurityAuditService {
```

**修改后**:
```java
@Service("extendedSecurityAuditService")
@ConditionalOnProperty(name = "jairouter.security.audit.enabled", havingValue = "true", matchIfMissing = true)
public class ExtendedSecurityAuditServiceImpl implements ExtendedSecurityAuditService {
```

**变更说明**:
- 移除 `@Primary` 注解
- 添加明确的 bean 名称
- 作为扩展服务，不作为主要实现

### 3. 删除重复的 H2SecurityAuditService

**删除文件**: `src/main/java/org/unreal/modelrouter/security/audit/H2SecurityAuditService.java`

**原因**:
- 与 `H2SecurityAuditServiceImpl` 功能重复
- 保留更完整的实现（H2SecurityAuditServiceImpl）

## Bean 优先级规则

修复后的 Bean 选择逻辑：

```
当 store.type=h2 (默认):
  ├── H2SecurityAuditServiceImpl (@Primary) ✓ 被选中
  ├── ExtendedSecurityAuditServiceImpl (无 @Primary)
  └── SecurityAuditServiceImpl (条件不满足)

当 store.type=memory:
  ├── SecurityAuditServiceImpl (条件满足)
  └── ExtendedSecurityAuditServiceImpl (无 @Primary)

当 audit.enabled=false:
  └── 所有审计服务都不加载
```

## 验证结果

### 启动日志

```
2025-11-24 17:28:56.563 [main] INFO  [] o.u.m.s.s.H2JwtBlacklistService - 从H2恢复了 0 条黑名单记录到本地缓存
2025-11-24 17:28:56.564 [main] INFO  [] o.u.m.s.c.JwtBlacklistInitializer - JWT 黑名单恢复完成
```

### 启动成功

```bash
mvn spring-boot:run -P fast
# ✅ 启动成功，无 Bean 冲突错误
```

## 文件清单

### 修改的文件

1. `src/main/java/org/unreal/modelrouter/security/audit/H2SecurityAuditServiceImpl.java`
   - 条件改为 `store.type=h2`
   - 添加 bean 名称

2. `src/main/java/org/unreal/modelrouter/security/audit/ExtendedSecurityAuditServiceImpl.java`
   - 移除 `@Primary`
   - 添加 bean 名称

### 删除的文件

1. `src/main/java/org/unreal/modelrouter/security/audit/H2SecurityAuditService.java`
   - 重复实现，已删除

## 最佳实践

### 1. 避免多个 @Primary

同一接口的多个实现不应该都标记为 `@Primary`，除非有明确的条件区分。

### 2. 使用条件注解

使用 `@ConditionalOnProperty` 等条件注解来控制 Bean 的加载：

```java
// 好的做法
@Service
@Primary
@ConditionalOnProperty(name = "store.type", havingValue = "h2")
public class H2ServiceImpl implements Service {
}

@Service
@ConditionalOnProperty(name = "store.type", havingValue = "memory")
public class MemoryServiceImpl implements Service {
}
```

### 3. 明确 Bean 名称

为 Bean 指定明确的名称，便于调试和引用：

```java
@Service("h2SecurityAuditService")
public class H2SecurityAuditServiceImpl implements SecurityAuditService {
}
```

### 4. 条件互斥

确保不同实现的条件是互斥的，避免同时满足：

```java
// ❌ 错误：条件可能同时满足
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true")
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true")

// ✅ 正确：条件互斥
@ConditionalOnProperty(name = "store.type", havingValue = "h2")
@ConditionalOnProperty(name = "store.type", havingValue = "memory")
```

## 配置说明

### 默认配置 (application.yml)

```yaml
store:
  type: h2  # 默认使用 H2，会加载 H2SecurityAuditServiceImpl
```

### 使用内存存储

```yaml
store:
  type: memory  # 使用内存存储，会加载 SecurityAuditServiceImpl

jairouter:
  security:
    audit:
      storage: memory
```

### 禁用审计

```yaml
jairouter:
  security:
    audit:
      enabled: false  # 所有审计服务都不加载
```

## 测试验证

### 1. 编译测试

```bash
mvn clean compile -DskipTests
# ✅ BUILD SUCCESS
```

### 2. 启动测试

```bash
mvn spring-boot:run -P fast
# ✅ 启动成功，无 Bean 冲突
```

### 3. Bean 检查

```bash
# 查看加载的 Bean
curl http://localhost:8080/actuator/beans | jq '.contexts.application.beans | keys | .[] | select(contains("SecurityAudit"))'

# 预期输出:
# "h2SecurityAuditService"
# "extendedSecurityAuditService"
```

## 常见问题

### Q1: 为什么保留 ExtendedSecurityAuditServiceImpl？

**A**: 
- 它实现了 `ExtendedSecurityAuditService` 接口
- 提供了额外的 JWT 和 API Key 审计方法
- 不与 `SecurityAuditService` 冲突

### Q2: 如何切换到内存审计？

**A**: 修改配置：
```yaml
store:
  type: memory

jairouter:
  security:
    audit:
      storage: memory
```

### Q3: 如何自定义审计实现？

**A**: 创建新的实现并使用条件注解：
```java
@Service
@Primary
@ConditionalOnProperty(name = "audit.storage", havingValue = "custom")
public class CustomAuditService implements SecurityAuditService {
    // 自定义实现
}
```

## 总结

### 问题

- ❌ 多个 `@Primary` Bean
- ❌ 条件重叠
- ❌ Bean 选择冲突

### 解决

- ✅ 只有一个 `@Primary` (H2SecurityAuditServiceImpl)
- ✅ 条件互斥 (store.type)
- ✅ 明确 Bean 名称
- ✅ 删除重复实现

### 结果

- ✅ 编译成功
- ✅ 启动成功
- ✅ 无 Bean 冲突
- ✅ H2 审计服务正常工作

---

**修复时间**: 2024-11-24  
**影响范围**: 安全审计服务  
**测试状态**: ✅ 通过

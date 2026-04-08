# H2 安全持久化快速参考

## 修复概览

✅ **高优先级**: 安全审计服务 H2 持久化 - 已完成  
✅ **中优先级**: JWT 黑名单 H2 持久化 - 已完成

## 核心变更

### 1. 安全审计服务

**新实现**: `H2SecurityAuditServiceImpl` (422行)
- 使用 `SecurityAuditRepository` 持久化到 H2
- 标记为 `@Primary`，优先使用
- 保留内存缓存用于告警

**旧实现**: `SecurityAuditServiceImpl` → `memorySecurityAuditService`
- 仅在 `audit.storage=memory` 时启用
- 向后兼容

### 2. JWT 黑名单服务

**新实现**: `H2JwtBlacklistService` (292行)
- 使用 `JwtBlacklistRepository` 持久化到 H2
- 标记为 `@Primary`，优先使用
- 三层保障: H2 + 本地缓存 + Redis(可选)

**旧实现**: `EnhancedJwtBlacklistService` → `redisJwtBlacklistService`
- 仅在 `blacklist.redis.enabled=true` 时启用
- 向后兼容

## 数据库表

### jwt_blacklist
```sql
CREATE TABLE "jwt_blacklist" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "token_hash" VARCHAR(255) NOT NULL UNIQUE,
    "user_id" VARCHAR(255),
    "revoked_at" TIMESTAMP NOT NULL,
    "expires_at" TIMESTAMP NOT NULL,
    "reason" VARCHAR(1000),
    "revoked_by" VARCHAR(255),
    "created_at" TIMESTAMP NOT NULL
);
```

## 配置

### application-h2.yml
```yaml
jairouter:
  security:
    audit:
      enabled: true
      storage: h2  # 使用 H2
      retention-days: 30
    
    jwt:
      blacklist-enabled: true
    
    cleanup:
      enabled: true
      audit:
        schedule: "0 0 2 * * ?"  # 每天2点
      blacklist:
        schedule: "0 0 * * * ?"  # 每小时
```

## API 使用

### 审计服务
```java
@Autowired
private H2SecurityAuditServiceImpl auditService;

// 记录事件
auditService.recordAuthenticationEvent(
    "user123", "192.168.1.1", "Mozilla", true, null
).subscribe();

// 查询事件
auditService.queryEvents(startTime, endTime, null, null, 100)
    .subscribe(event -> log.info("Event: {}", event));

// 获取统计
auditService.getSecurityStatistics(startTime, endTime)
    .subscribe(stats -> log.info("Stats: {}", stats));
```

### 黑名单服务
```java
@Autowired
private H2JwtBlacklistService blacklistService;

// 添加到黑名单
blacklistService.addToBlacklist(
    tokenHash, userId, 3600, "登出", "admin"
).subscribe();

// 检查黑名单
blacklistService.isBlacklisted(tokenHash)
    .subscribe(isBlacklisted -> {
        if (isBlacklisted) {
            // 拒绝访问
        }
    });

// 获取统计
blacklistService.getStats()
    .subscribe(stats -> log.info("Stats: {}", stats));
```

## 自动任务

### 审计日志清理
- **时间**: 每天凌晨2点
- **保留**: 30天（可配置）
- **类**: `SecurityDataCleanupScheduler.cleanupExpiredAuditLogs()`

### 黑名单清理
- **时间**: 每小时
- **清理**: 过期记录
- **类**: `SecurityDataCleanupScheduler.cleanupExpiredBlacklistTokens()`

## 启动流程

1. **H2 数据库初始化**
   - `H2DatabaseConfiguration` 创建连接
   - 执行 `schema.sql` 创建表

2. **黑名单恢复**
   - `JwtBlacklistInitializer` 从 H2 恢复到缓存
   - 仅恢复未过期的记录

3. **服务启动**
   - `H2SecurityAuditServiceImpl` 标记为 @Primary
   - `H2JwtBlacklistService` 标记为 @Primary

## 监控指标

### 审计服务
- 总事件数
- 按类型统计
- 成功/失败率
- 认证/JWT/API Key 统计

### 黑名单服务
- 本地缓存大小 (最大10000)
- H2 记录数
- H2 可用性

## 故障处理

### H2 不可用
- **审计**: 记录错误日志，返回失败
- **黑名单**: 使用本地缓存，记录警告

### 启动失败
- **黑名单恢复失败**: 记录错误，不影响启动
- **表创建失败**: 应用启动失败

## 测试

```bash
# 运行测试
mvn test -Dtest=H2SecurityPersistenceTest

# 启动应用
mvn spring-boot:run -Dspring-boot.run.profiles=h2

# 检查数据库
ls -lh ./data/config.mv.db
```

## 文件清单

### 新增 (7个)
1. `H2SecurityAuditServiceImpl.java` - 审计服务
2. `JwtBlacklistEntity.java` - 黑名单实体
3. `JwtBlacklistRepository.java` - 黑名单仓库
4. `H2JwtBlacklistService.java` - 黑名单服务
5. `JwtBlacklistInitializer.java` - 启动初始化
6. `SecurityDataCleanupScheduler.java` - 清理任务
7. `H2SecurityPersistenceTest.java` - 测试

### 修改 (5个)
1. `schema.sql` - 添加表
2. `SecurityAuditRepository.java` - 添加方法
3. `SecurityAuditServiceImpl.java` - 重命名
4. `EnhancedJwtBlacklistService.java` - 重命名
5. `application-h2.yml` - 添加配置

## 性能特性

- ✅ 本地缓存加速查询
- ✅ 异步数据库写入
- ✅ 批量查询优化
- ✅ 自动过期清理
- ✅ 启动预加载

## 总代码量

**新增代码**: 900 行  
**修改代码**: ~50 行  
**总计**: ~950 行

---

**完成时间**: 2024-11-24  
**状态**: ✅ 已完成  
**测试**: 待验证

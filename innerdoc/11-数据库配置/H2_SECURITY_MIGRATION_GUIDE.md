# H2 安全持久化迁移指南

## 概述

本指南帮助你从旧的内存/Redis存储迁移到新的H2持久化存储。

## 迁移前检查

### 1. 确认当前配置

检查 `application.yml` 或 `application-h2.yml`:

```yaml
jairouter:
  security:
    audit:
      storage: ?  # 当前是什么？
    jwt:
      blacklist:
        redis:
          enabled: ?  # 是否启用？
```

### 2. 备份现有数据

如果使用 Redis:
```bash
# 备份 Redis 数据
redis-cli SAVE
cp /var/lib/redis/dump.rdb /backup/redis-backup-$(date +%Y%m%d).rdb
```

如果使用文件存储:
```bash
# 备份配置文件
cp -r ./config ./config-backup-$(date +%Y%m%d)
```

## 迁移步骤

### 步骤 1: 更新代码

```bash
# 拉取最新代码
git pull origin main

# 或者应用补丁
git apply h2-security-persistence.patch
```

### 步骤 2: 更新配置

编辑 `application-h2.yml`:

```yaml
jairouter:
  security:
    # 启用安全功能
    enabled: true
    
    # 审计配置 - 使用 H2
    audit:
      enabled: true
      storage: h2  # 从 memory 改为 h2
      retention-days: 30
    
    # JWT 配置
    jwt:
      enabled: true
      blacklist-enabled: true
      blacklist:
        persistence:
          enabled: true
          primary-storage: file  # 使用 StoreManager (H2)
        redis:
          enabled: false  # 关闭 Redis
    
    # 清理任务
    cleanup:
      enabled: true
      audit:
        schedule: "0 0 2 * * ?"
      blacklist:
        schedule: "0 0 * * * ?"
```

### 步骤 3: 编译项目

```bash
mvn clean compile
```

检查编译错误：
```bash
mvn compile 2>&1 | grep ERROR
```

### 步骤 4: 运行测试

```bash
# 运行所有测试
mvn test

# 或只运行安全持久化测试
mvn test -Dtest=H2SecurityPersistenceTest
```

### 步骤 5: 启动应用

```bash
# 使用 H2 配置启动
mvn spring-boot:run -Dspring-boot.run.profiles=h2

# 或使用 jar 启动
java -jar target/modelrouter.jar --spring.profiles.active=h2
```

### 步骤 6: 验证功能

#### 6.1 检查日志

查找以下日志确认服务已启动:

```
✓ H2SecurityAuditServiceImpl initialized
✓ H2JwtBlacklistService initialized
✓ 开始从 H2 数据库恢复 JWT 黑名单...
✓ JWT 黑名单恢复完成
```

#### 6.2 检查数据库

```bash
# 检查数据库文件
ls -lh ./data/config.mv.db

# 查看表结构（可选）
# 启用 H2 控制台: spring.h2.console.enabled=true
# 访问: http://localhost:8080/h2-console
```

#### 6.3 测试审计功能

```bash
# 触发一个认证事件
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

# 查询审计日志（如果有API）
curl http://localhost:8080/api/audit/events?limit=10
```

#### 6.4 测试黑名单功能

```bash
# 撤销一个令牌
curl -X POST http://localhost:8080/api/jwt/revoke \
  -H "Authorization: Bearer YOUR_TOKEN"

# 检查黑名单统计
curl http://localhost:8080/api/jwt/blacklist/stats
```

## 数据迁移

### 从 Redis 迁移黑名单

如果之前使用 Redis 存储黑名单，需要手动迁移：

```java
// 创建迁移脚本
@Component
public class RedisToH2BlacklistMigration {
    
    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;
    
    @Autowired
    private H2JwtBlacklistService h2BlacklistService;
    
    public void migrate() {
        redisTemplate.keys("jwt:blacklist:*")
            .flatMap(key -> {
                String tokenHash = key.replace("jwt:blacklist:", "");
                return redisTemplate.getExpire(key)
                    .flatMap(ttl -> {
                        if (ttl > 0) {
                            return h2BlacklistService.addToBlacklist(
                                tokenHash, null, ttl, "从Redis迁移", "system"
                            );
                        }
                        return Mono.empty();
                    });
            })
            .subscribe();
    }
}
```

### 从内存迁移审计日志

内存中的审计日志无法迁移，但新的事件会自动保存到 H2。

## 回滚方案

如果迁移出现问题，可以回滚：

### 方案 1: 恢复旧配置

```yaml
jairouter:
  security:
    audit:
      storage: memory  # 恢复为内存
    jwt:
      blacklist:
        redis:
          enabled: true  # 恢复 Redis
```

### 方案 2: 恢复旧代码

```bash
# 回滚到之前的提交
git revert HEAD

# 或切换到之前的分支
git checkout previous-branch
```

### 方案 3: 使用备份

```bash
# 恢复 Redis 备份
redis-cli FLUSHALL
redis-cli --rdb /backup/redis-backup-YYYYMMDD.rdb

# 恢复配置文件
cp -r ./config-backup-YYYYMMDD/* ./config/
```

## 常见问题

### Q1: 启动时报错 "Table not found: JWT_BLACKLIST"

**原因**: 数据库表未创建

**解决**:
```bash
# 检查 schema.sql 是否包含 jwt_blacklist 表定义
grep "jwt_blacklist" src/main/resources/schema.sql

# 手动执行 SQL（如果需要）
# 连接到 H2 控制台执行 schema.sql
```

### Q2: 审计日志没有保存到数据库

**原因**: 可能使用了旧的内存实现

**解决**:
```bash
# 检查配置
grep "audit.storage" src/main/resources/application-h2.yml

# 应该是: storage: h2

# 检查日志
grep "H2SecurityAuditServiceImpl" logs/application.log
```

### Q3: 黑名单检查总是返回 false

**原因**: 可能使用了旧的 Redis 实现

**解决**:
```bash
# 检查配置
grep "blacklist.redis.enabled" src/main/resources/application-h2.yml

# 应该是: enabled: false

# 检查日志
grep "H2JwtBlacklistService" logs/application.log
```

### Q4: 性能下降

**原因**: 数据库查询比内存慢

**解决**:
1. 确保索引已创建
2. 调整本地缓存大小
3. 考虑使用 Redis 作为缓存层

```yaml
# 启用 Redis 缓存（可选）
jairouter:
  security:
    jwt:
      blacklist:
        redis:
          enabled: true  # 作为缓存层
```

### Q5: H2 数据库文件过大

**原因**: 审计日志或黑名单记录过多

**解决**:
```yaml
# 调整保留天数
jairouter:
  security:
    audit:
      retention-days: 7  # 从 30 改为 7

# 手动清理
curl -X POST http://localhost:8080/api/audit/cleanup
```

## 性能优化

### 1. 调整缓存大小

```java
// 在 H2JwtBlacklistService 中
private static final int MAX_LOCAL_CACHE_SIZE = 20000; // 从 10000 增加
```

### 2. 调整清理频率

```yaml
jairouter:
  security:
    cleanup:
      blacklist:
        schedule: "0 */30 * * * ?"  # 每30分钟（从每小时）
```

### 3. 添加数据库索引

```sql
-- 如果查询慢，添加更多索引
CREATE INDEX idx_audit_timestamp_type ON security_audit(timestamp, event_type);
CREATE INDEX idx_blacklist_expires ON jwt_blacklist(expires_at);
```

## 监控建议

### 1. 数据库大小

```bash
# 定期检查数据库文件大小
watch -n 60 'ls -lh ./data/config.mv.db'
```

### 2. 清理任务

```bash
# 检查清理任务日志
grep "清理" logs/application.log | tail -20
```

### 3. 性能指标

```bash
# 监控审计事件写入速度
grep "安全审计事件已记录" logs/application.log | wc -l

# 监控黑名单查询
grep "令牌在.*黑名单中" logs/application.log | wc -l
```

## 生产环境建议

### 1. 数据库配置

```yaml
# 生产环境使用独立的数据库文件
store:
  h2:
    url: file:/var/lib/jairouter/data/security
```

### 2. 备份策略

```bash
# 每天备份数据库
0 3 * * * cp /var/lib/jairouter/data/security.mv.db /backup/security-$(date +\%Y\%m\%d).mv.db
```

### 3. 监控告警

```yaml
# 配置告警
jairouter:
  security:
    monitoring:
      alerts:
        enabled: true
        thresholds:
          database-size-mb: 1000  # 数据库超过1GB告警
          cleanup-failure-count: 3  # 清理失败3次告警
```

## 总结

### 迁移检查清单

- [ ] 备份现有数据
- [ ] 更新代码
- [ ] 更新配置
- [ ] 编译通过
- [ ] 测试通过
- [ ] 启动成功
- [ ] 审计功能正常
- [ ] 黑名单功能正常
- [ ] 性能可接受
- [ ] 监控已配置

### 预期收益

- ✅ 数据持久化，重启不丢失
- ✅ 配置与实现一致
- ✅ 支持历史查询和分析
- ✅ 自动清理过期数据
- ✅ 更好的可观测性

### 注意事项

- ⚠️ 首次启动会创建数据库表
- ⚠️ 数据库文件会随时间增长
- ⚠️ 需要定期备份数据库
- ⚠️ 性能可能略低于纯内存

---

**文档版本**: 1.0  
**更新时间**: 2024-11-24  
**适用版本**: ModelRouter 1.0+

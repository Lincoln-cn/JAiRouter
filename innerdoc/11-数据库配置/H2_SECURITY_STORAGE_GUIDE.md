# H2 安全数据存储指南

## 概述

除了配置数据，项目现在支持将以下安全相关数据存储到 H2 数据库：

1. **安全审计日志** - 所有安全事件的审计记录
2. **API Keys** - API 密钥管理
3. **JWT 账户** - JWT 用户账户信息

## 数据库表结构

### 1. 安全审计表 (security_audit)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| event_id | VARCHAR(255) | 事件唯一标识 |
| event_type | VARCHAR(100) | 事件类型 |
| user_id | VARCHAR(255) | 用户ID |
| client_ip | VARCHAR(50) | 客户端IP |
| user_agent | VARCHAR(500) | 用户代理 |
| timestamp | TIMESTAMP | 事件时间 |
| resource | VARCHAR(500) | 访问资源 |
| action | VARCHAR(100) | 执行操作 |
| success | BOOLEAN | 是否成功 |
| failure_reason | VARCHAR(1000) | 失败原因 |
| additional_data | TEXT | 附加数据（JSON） |
| request_id | VARCHAR(255) | 请求ID |
| session_id | VARCHAR(255) | 会话ID |

### 2. API Key 表 (api_keys)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| key_id | VARCHAR(255) | Key ID |
| key_value | VARCHAR(500) | Key 值 |
| description | VARCHAR(1000) | 描述 |
| permissions | TEXT | 权限列表（JSON） |
| expires_at | TIMESTAMP | 过期时间 |
| created_at | TIMESTAMP | 创建时间 |
| enabled | BOOLEAN | 是否启用 |
| metadata | TEXT | 元数据（JSON） |
| usage_statistics | TEXT | 使用统计（JSON） |

### 3. JWT 账户表 (jwt_accounts)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(255) | 用户名 |
| password | VARCHAR(500) | 密码（加密） |
| roles | TEXT | 角色列表（JSON） |
| enabled | BOOLEAN | 是否启用 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

## 配置方式

### 1. 启用 H2 安全审计存储

```yaml
jairouter:
  security:
    audit:
      enabled: true
      storage: h2  # 使用 H2 数据库存储审计日志
      retentionDays: 30  # 日志保留天数
```

### 2. 启用安全数据迁移

```yaml
store:
  type: h2
  security-migration:
    enabled: true  # 启用 API Key 和 JWT 账户迁移
```

### 3. 完整配置示例

```yaml
store:
  type: h2
  h2:
    url: file:./data/config
  migration:
    enabled: true  # 配置数据迁移
  security-migration:
    enabled: true  # 安全数据迁移

jairouter:
  security:
    enabled: true
    audit:
      enabled: true
      storage: h2
      retentionDays: 30

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/config
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

## 数据迁移

### 自动迁移

启用自动迁移后，应用启动时会自动执行：

1. **配置数据迁移** - 从 `./config` 目录迁移配置文件
2. **API Key 迁移** - 从内存/文件迁移 API Keys
3. **JWT 账户迁移** - 从配置文件迁移 JWT 账户

### 手动迁移

通过代码手动触发迁移：

```java
@Autowired
private SecurityDataMigrationService migrationService;

public void migrate() {
    // 迁移所有安全数据
    migrationService.migrateAll();
    
    // 或单独迁移
    migrationService.migrateApiKeys();
    migrationService.migrateJwtAccounts();
}
```

## 使用示例

### 1. 安全审计查询

```sql
-- 查询最近的审计日志
SELECT * FROM security_audit 
ORDER BY timestamp DESC 
LIMIT 100;

-- 查询失败的认证尝试
SELECT * FROM security_audit 
WHERE event_type = 'AUTHENTICATION_FAILURE' 
AND timestamp > DATEADD('DAY', -7, CURRENT_TIMESTAMP);

-- 按用户统计事件
SELECT user_id, event_type, COUNT(*) as count
FROM security_audit
WHERE timestamp > DATEADD('DAY', -30, CURRENT_TIMESTAMP)
GROUP BY user_id, event_type
ORDER BY count DESC;

-- 查询可疑活动
SELECT client_ip, COUNT(*) as failed_attempts
FROM security_audit
WHERE event_type = 'AUTHENTICATION_FAILURE'
AND timestamp > DATEADD('HOUR', -1, CURRENT_TIMESTAMP)
GROUP BY client_ip
HAVING COUNT(*) > 5
ORDER BY failed_attempts DESC;
```

### 2. API Key 查询

```sql
-- 查询所有有效的 API Keys
SELECT key_id, description, expires_at, enabled
FROM api_keys
WHERE enabled = true
AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP);

-- 查询即将过期的 API Keys
SELECT key_id, description, expires_at
FROM api_keys
WHERE expires_at BETWEEN CURRENT_TIMESTAMP AND DATEADD('DAY', 7, CURRENT_TIMESTAMP);

-- 查询使用统计
SELECT key_id, description, usage_statistics
FROM api_keys
ORDER BY created_at DESC;
```

### 3. JWT 账户查询

```sql
-- 查询所有启用的账户
SELECT username, roles, enabled, created_at
FROM jwt_accounts
WHERE enabled = true;

-- 查询账户数量统计
SELECT 
    COUNT(*) as total_accounts,
    SUM(CASE WHEN enabled = true THEN 1 ELSE 0 END) as enabled_accounts,
    SUM(CASE WHEN enabled = false THEN 1 ELSE 0 END) as disabled_accounts
FROM jwt_accounts;
```

## 性能优化

### 1. 审计日志清理

定期清理过期的审计日志：

```java
@Autowired
private H2SecurityAuditService auditService;

@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
public void cleanupAuditLogs() {
    int retentionDays = 30;
    auditService.cleanupExpiredLogs(retentionDays)
        .subscribe(count -> log.info("Cleaned up {} audit logs", count));
}
```

### 2. 索引优化

系统已自动创建以下索引：

**安全审计索引：**
- `idx_audit_event_id` - 事件ID索引
- `idx_audit_timestamp` - 时间戳索引
- `idx_audit_user_id` - 用户ID索引
- `idx_audit_event_type` - 事件类型索引
- `idx_audit_client_ip` - 客户端IP索引

**API Key 索引：**
- `idx_apikey_key_id` - Key ID索引
- `idx_apikey_key_value` - Key值索引
- `idx_apikey_enabled` - 启用状态索引
- `idx_apikey_expires_at` - 过期时间索引

**JWT 账户索引：**
- `idx_jwt_username` - 用户名索引
- `idx_jwt_enabled` - 启用状态索引

## 安全最佳实践

### 1. 审计日志

- **保留期限**：建议保留 30-90 天
- **定期备份**：定期备份审计数据
- **监控告警**：设置异常事件告警
- **访问控制**：限制审计日志的访问权限

### 2. API Key 管理

- **定期轮换**：定期更新 API Keys
- **最小权限**：只授予必要的权限
- **过期策略**：设置合理的过期时间
- **使用监控**：监控 API Key 使用情况

### 3. JWT 账户

- **密码加密**：使用强加密算法
- **定期审计**：定期审查账户权限
- **禁用策略**：及时禁用不活跃账户
- **角色管理**：使用基于角色的访问控制

## 监控和告警

### 1. 审计事件监控

```java
@Autowired
private H2SecurityAuditService auditService;

// 检查是否需要触发告警
public void checkSecurityAlerts() {
    // 检查认证失败次数
    auditService.shouldTriggerAlert("AUTHENTICATION_FAILURE", 5, 10)
        .subscribe(shouldAlert -> {
            if (shouldAlert) {
                // 发送告警通知
                sendAlert("High authentication failure rate detected");
            }
        });
}
```

### 2. 统计报表

```java
// 获取安全统计
public void generateSecurityReport() {
    LocalDateTime startTime = LocalDateTime.now().minusDays(7);
    LocalDateTime endTime = LocalDateTime.now();
    
    auditService.getSecurityStatistics(startTime, endTime)
        .subscribe(stats -> {
            log.info("Security Statistics: {}", stats);
            // 生成报表
        });
}
```

## 备份与恢复

### 备份

```bash
# 备份整个数据库
cp ./data/config.mv.db ./backup/config-$(date +%Y%m%d).mv.db

# 导出特定表
java -cp h2*.jar org.h2.tools.Script \
  -url jdbc:h2:./data/config \
  -user sa \
  -script backup.sql \
  -table security_audit
```

### 恢复

```bash
# 恢复数据库
cp ./backup/config-20241120.mv.db ./data/config.mv.db

# 导入SQL脚本
java -cp h2*.jar org.h2.tools.RunScript \
  -url jdbc:h2:./data/config \
  -user sa \
  -script backup.sql
```

## 故障排查

### 问题 1: 审计日志未记录

**检查项：**
1. 确认 `jairouter.security.audit.storage=h2`
2. 检查数据库连接是否正常
3. 查看应用日志中的错误信息

### 问题 2: 迁移失败

**解决方案：**
1. 检查源数据格式是否正确
2. 确保数据库表已创建
3. 查看迁移日志了解具体错误

### 问题 3: 查询性能慢

**优化方案：**
1. 检查索引是否正确创建
2. 定期清理过期数据
3. 考虑分区或归档历史数据

## 与内存存储的对比

| 特性 | 内存存储 | H2 数据库 |
|------|---------|----------|
| 持久化 | ✗ | ✓ |
| 性能 | 极高 | 高 |
| 查询能力 | 有限 | 强大 |
| 数据量 | 受内存限制 | 受磁盘限制 |
| 重启后保留 | ✗ | ✓ |
| 复杂查询 | 不支持 | 支持 |
| 适用场景 | 开发测试 | 生产环境 |

## 总结

使用 H2 数据库存储安全数据的优势：

1. **持久化** - 数据不会因重启而丢失
2. **查询能力** - 支持复杂的 SQL 查询和统计
3. **性能** - 比文件存储更快
4. **可靠性** - 事务支持保证数据一致性
5. **易于管理** - 通过 H2 控制台可视化管理
6. **审计合规** - 满足安全审计要求

推荐在生产环境中使用 H2 数据库存储所有安全相关数据。

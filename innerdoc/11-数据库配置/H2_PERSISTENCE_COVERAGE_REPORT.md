# H2 数据库持久化业务覆盖检查报告

## 检查时间
2024年11月24日

## 一、H2 数据库配置概览

### 1.1 数据库配置
- **存储类型**: H2 文件数据库
- **数据库路径**: `./data/config` (开发环境) / `/var/lib/jairouter/data/config` (生产环境)
- **连接方式**: R2DBC (响应式数据库连接)
- **数据库模式**: MySQL 兼容模式
- **表名大小写**: 保持小写 (DATABASE_TO_UPPER=FALSE)

### 1.2 数据库表结构
根据 `schema.sql`，系统定义了以下表：

1. **config_data** - 配置数据表
2. **security_audit** - 安全审计表
3. **api_keys** - API Key 表
4. **jwt_accounts** - JWT 账户表

## 二、持久化业务覆盖情况

### ✅ 2.1 已覆盖的持久化业务

#### 2.1.1 配置管理 (ConfigEntity)
- **实体类**: `ConfigEntity.java`
- **仓库**: `ConfigRepository.java`
- **表名**: `config_data`
- **功能**: 
  - 配置数据的版本化存储
  - 支持配置历史记录
  - 支持配置回滚
- **使用服务**: 
  - `H2StoreManager` - 主要配置存储管理器
  - `ConfigMigrationService` - 配置迁移服务
  - `AutoMergeService` - 配置合并服务
- **状态**: ✅ 完全实现

#### 2.1.2 安全审计 (SecurityAuditEntity)
- **实体类**: `SecurityAuditEntity.java`
- **仓库**: `SecurityAuditRepository.java`
- **表名**: `security_audit`
- **功能**:
  - 记录所有安全相关事件
  - 支持按时间、用户、事件类型查询
  - 支持审计日志清理
- **字段覆盖**:
  - 事件ID、事件类型、用户ID
  - 客户端IP、User Agent
  - 时间戳、资源、操作
  - 成功/失败状态、失败原因
  - 附加数据、请求ID、会话ID
- **配置**: `jairouter.security.audit.storage: h2`
- **状态**: ✅ 完全实现

#### 2.1.3 API Key 管理 (ApiKeyEntity)
- **实体类**: `ApiKeyEntity.java`
- **仓库**: `ApiKeyRepository.java`
- **表名**: `api_keys`
- **功能**:
  - API Key 的创建、更新、删除
  - API Key 验证和权限检查
  - 使用统计和过期管理
- **字段覆盖**:
  - Key ID、Key Value
  - 描述、权限 (JSON)
  - 过期时间、创建时间
  - 启用状态
  - 元数据 (JSON)
  - 使用统计 (JSON)
- **使用服务**: `ApiKeyService`
- **存储方式**: 
  - H2 数据库持久化
  - 内存缓存加速访问
- **状态**: ✅ 完全实现

#### 2.1.4 JWT 账户管理 (JwtAccountEntity)
- **实体类**: `JwtAccountEntity.java`
- **仓库**: `JwtAccountRepository.java`
- **表名**: `jwt_accounts`
- **功能**:
  - JWT 用户账户的存储
  - 用户认证信息管理
  - 角色和权限管理
- **字段覆盖**:
  - 用户名、密码 (加密)
  - 角色列表 (JSON)
  - 启用状态
  - 创建时间、更新时间
- **使用服务**: `AccountManager`
- **状态**: ✅ 完全实现

#### 2.1.5 JWT 令牌持久化
- **实现类**: `JwtTokenPersistenceServiceImpl`
- **存储方式**: 通过 `StoreManager` 使用 H2 数据库
- **功能**:
  - JWT 令牌的保存和查询
  - 令牌状态管理 (ACTIVE, REVOKED, EXPIRED)
  - 用户令牌索引
  - 状态索引
  - 过期令牌清理
- **配置**: `jairouter.security.jwt.persistence.enabled: true`
- **存储键前缀**:
  - `jwt_token_*` - 令牌数据
  - `jwt_user_index_*` - 用户索引
  - `jwt_status_index_*` - 状态索引
  - `jwt_token_counter` - 令牌计数器
- **状态**: ✅ 完全实现

## 三、未使用 H2 持久化的业务

### ⚠️ 3.1 仅使用内存存储的业务

#### 3.1.1 JWT 黑名单 (EnhancedJwtBlacklistService)
- **当前实现**: Redis + 本地内存缓存
- **存储方式**:
  - 主存储: Redis
  - 备份存储: 本地 ConcurrentHashMap
- **问题**: 
  - 未使用 H2 数据库持久化
  - 重启后本地缓存丢失
  - 依赖 Redis 可用性
- **建议**: 
  - 添加 H2 作为持久化层
  - 启动时从 H2 恢复黑名单
  - 定期同步到 H2
- **状态**: ⚠️ 需要改进

#### 3.1.2 安全审计服务 (SecurityAuditServiceImpl)
- **当前实现**: 仅内存存储
- **存储方式**: ConcurrentLinkedQueue
- **问题**:
  - 虽然配置了 `audit.storage: h2`
  - 但实际实现仍使用内存队列
  - 重启后审计日志丢失
- **建议**:
  - 修改实现以使用 `SecurityAuditRepository`
  - 将审计事件持久化到 H2
  - 保留内存缓存用于快速查询
- **状态**: ⚠️ 配置与实现不一致

## 四、配置文件分析

### 4.1 application-h2.yml (开发环境)
```yaml
store:
  type: h2
  h2:
    url: file:./data/config
  migration:
    enabled: true
  security-migration:
    enabled: true

jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: file  # 使用 StoreManager (H2)
      blacklist:
        persistence:
          enabled: true
          primary-storage: file  # 使用 StoreManager (H2)
    audit:
      enabled: true
      storage: h2  # 配置为 H2，但实现未使用
```

### 4.2 application-prod.yml (生产环境)
```yaml
store:
  type: h2
  h2:
    url: file:/var/lib/jairouter/data/config
  migration:
    enabled: false
  security-migration:
    enabled: false

jairouter:
  security:
    jwt:
      enabled: false  # 生产环境默认关闭
      persistence:
        enabled: false
        primary-storage: redis  # 生产环境优先使用 Redis
      blacklist:
        persistence:
          enabled: false
          primary-storage: redis
    audit:
      enabled: true
      storage: h2
      retention-days: 90
```

## 五、问题总结

### 5.1 配置与实现不一致

#### 问题 1: 安全审计服务
- **配置**: `jairouter.security.audit.storage: h2`
- **实现**: `SecurityAuditServiceImpl` 使用内存队列
- **影响**: 审计日志无法持久化，重启后丢失
- **优先级**: 🔴 高

#### 问题 2: JWT 黑名单
- **配置**: `jairouter.security.jwt.blacklist.persistence.primary-storage: file`
- **实现**: `EnhancedJwtBlacklistService` 使用 Redis + 内存
- **影响**: 未使用 H2 持久化，依赖 Redis
- **优先级**: 🟡 中

### 5.2 缺失的持久化功能

#### 缺失 1: JWT 黑名单 H2 持久化
- 当前只有 Redis 和内存存储
- 需要添加 H2 作为持久化层
- 建议创建 `jwt_blacklist` 表

#### 缺失 2: 安全审计 H2 实现
- 虽然有 `SecurityAuditEntity` 和 `SecurityAuditRepository`
- 但 `SecurityAuditServiceImpl` 未使用它们
- 需要重构服务实现

## 六、改进建议

### 6.1 立即修复 (高优先级)

#### 1. 修复安全审计服务
```java
// 修改 SecurityAuditServiceImpl 使用 SecurityAuditRepository
@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {
    private final SecurityAuditRepository auditRepository;
    
    @Override
    public Mono<Void> recordEvent(SecurityAuditEvent event) {
        SecurityAuditEntity entity = convertToEntity(event);
        return auditRepository.save(entity).then();
    }
}
```

#### 2. 添加 JWT 黑名单 H2 持久化
```sql
-- 添加黑名单表
CREATE TABLE IF NOT EXISTS "jwt_blacklist" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "token_hash" VARCHAR(255) NOT NULL UNIQUE,
    "user_id" VARCHAR(255),
    "revoked_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expires_at" TIMESTAMP NOT NULL,
    "reason" VARCHAR(1000),
    "revoked_by" VARCHAR(255)
);
```

### 6.2 中期优化 (中优先级)

#### 1. 统一持久化策略
- 所有安全相关数据使用 H2 作为主存储
- Redis 作为缓存层（可选）
- 内存缓存用于热数据

#### 2. 添加数据同步机制
- H2 ↔ Redis 双向同步
- 启动时从 H2 恢复数据
- 定期将内存数据刷新到 H2

### 6.3 长期改进 (低优先级)

#### 1. 性能优化
- 添加更多索引
- 实现分区表
- 优化查询性能

#### 2. 监控和告警
- 添加持久化失败告警
- 监控数据库大小
- 自动清理过期数据

## 七、结论

### 7.1 覆盖情况总结

| 业务模块 | H2 持久化 | 状态 | 优先级 |
|---------|----------|------|--------|
| 配置管理 | ✅ 完全实现 | 正常 | - |
| API Key 管理 | ✅ 完全实现 | 正常 | - |
| JWT 账户管理 | ✅ 完全实现 | 正常 | - |
| JWT 令牌持久化 | ✅ 完全实现 | 正常 | - |
| 安全审计 | ⚠️ 配置不一致 | 需修复 | 🔴 高 |
| JWT 黑名单 | ⚠️ 未使用 H2 | 需改进 | 🟡 中 |

### 7.2 总体评估

**覆盖率**: 约 70%

**核心业务**: ✅ 已覆盖
- 配置管理
- API Key 管理
- JWT 账户管理
- JWT 令牌持久化

**需要改进**: ⚠️ 2 项
- 安全审计服务实现
- JWT 黑名单持久化

### 7.3 建议行动

1. **立即修复**: 安全审计服务使用 H2 持久化
2. **短期改进**: 添加 JWT 黑名单 H2 持久化
3. **持续优化**: 完善监控和性能优化

---

**报告生成时间**: 2024-11-24
**检查范围**: 所有安全和配置相关的持久化业务
**检查方法**: 代码审查 + 配置文件分析

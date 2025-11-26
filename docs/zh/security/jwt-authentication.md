# JWT 认证配置说明

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->

## 概述

JAiRouter 支持 JWT（JSON Web Token）认证，可以与现有的身份认证系统集成。JWT 认证提供了无状态的认证机制，支持令牌刷新和黑名单功能。

## 功能特性

- **标准 JWT 支持**：完全兼容 RFC 7519 标准
- **多种签名算法**：支持 HS256、HS384、HS512、RS256、RS384、RS512
- **令牌刷新**：支持访问令牌和刷新令牌机制
- **黑名单功能**：支持令牌撤销和登出
- **与 API Key 共存**：可以与 API Key 认证同时使用
- **用户名密码登录**：支持通过用户名密码获取JWT令牌
- **持久化存储**：支持将 JWT 账户和令牌信息存储在 H2 数据库中
- **H2 数据库默认存储**：H2 数据库现在是 JWT 数据的默认持久化存储方式，提供更好的性能和可靠性

## 快速开始

### 1. 启用 JWT 认证

```yaml
jairouter:
  security:
    enabled: true
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"
      algorithm: "HS256"
      expiration-minutes: 60
```

**配置内容的说明**  
若需 **更换用于携带 JWT 的 HTTP 头**，可在启用 JWT 的同时追加：
 ```yaml
 jwt:
   enabled: true
   jwt-header: "Jairouter_Token"
 ```
 此时 JAiRouter 不再读取默认的 `Authorization` 头，而是读取自定义的 `Jairouter_Token` 头获取令牌，适用于与既有系统共用 `Authorization` 头的场景。

### 2. 设置 JWT 密钥

#### 对称密钥（HS256/HS384/HS512）
```bash
# 生产环境 JWT 密钥配置
export PROD_JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"
```

#### 对称密钥（HS256/HS384/HS512）

```bash
# 生产环境 JWT 密钥配置
export PROD_JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"

# 可选的过期时间配置
export PROD_JWT_EXPIRATION_MINUTES=15
export PROD_JWT_REFRESH_EXPIRATION_DAYS=30
```

#### 非对称密钥（RS256/RS384/RS512）

```bash
# 生产环境非对称密钥配置
export JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\nyour-public-key-here\n-----END PUBLIC KEY-----"
export JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nyour-private-key-here\n-----END PRIVATE KEY-----"
```

### 3. 配置用户账户

```yaml
jairouter:
  security:
    jwt:
      enabled: true
      secret: "dev-jwt-secret-key-for-development-only-not-for-production"
      algorithm: "HS256"
      expiration-minutes: 60
      refresh-expiration-days: 7
      issuer: "jairouter"
      blacklist-enabled: true
      # 用户账户配置
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"  # BCrypt加密的密码
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"  # 开发环境明文密码，生产环境应使用加密
          roles: [ "USER" ]
          enabled: true
```

### 4. 客户端使用

在 HTTP 请求头中添加 JWT 令牌：

```bash
curl -H "Authorization: Bearer your-jwt-token-here" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

## 登录获取JWT令牌

### 登录端点

```
POST /api/auth/jwt/login
```

### 请求示例

```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{
           "username": "admin",
           "password": "admin123"
         }'
```

### 响应示例

```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "message": "登录成功",
    "timestamp": "2023-01-01T12:00:00"
  },
  "errorCode": null
}
```

### 错误响应

```json
{
  "success": false,
  "message": "登录失败: 用户名或密码错误",
  "data": null,
  "errorCode": "LOGIN_FAILED"
}
```

## 详细配置

### JWT 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| [enabled](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\config\TraceConfig.java#L12-L12) | boolean | false | 是否启用 JWT 认证 |
| [secret](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityProperties.java#L126-L127) | string | - | JWT 签名密钥（对称算法） |
| `public-key` | string | - | JWT 公钥（非对称算法） |
| `private-key` | string | - | JWT 私钥（非对称算法） |
| [algorithm](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityProperties.java#L132-L134) | string | "HS256" | JWT 签名算法 |
| `expiration-minutes` | int | 60 | 访问令牌过期时间（分钟） |
| `refresh-expiration-days` | int | 7 | 刷新令牌过期时间（天） |
| [issuer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityProperties.java#L153-L155) | string | "jairouter" | JWT 发行者标识 |
| `blacklist-enabled` | boolean | true | 是否启用黑名单功能 |
| [accounts](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\JwtUserProperties.java#L14-L14) | array | [] | 用户账户列表 |

### 支持的签名算法

#### 对称算法（HMAC）

- **HS256**：HMAC using SHA-256
- **HS384**：HMAC using SHA-384
- **HS512**：HMAC using SHA-512

```yaml
jairouter:
  security:
    jwt:
      algorithm: "HS256"
      secret: "${JWT_SECRET}"
```

#### 非对称算法（RSA）

- **RS256**：RSASSA-PKCS1-v1_5 using SHA-256
- **RS384**：RSASSA-PKCS1-v1_5 using SHA-384
- **RS512**：RSASSA-PKCS1-v1_5 using SHA-512

```yaml
jairouter:
  security:
    jwt:
      algorithm: "RS256"
      public-key: |
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
        -----END PUBLIC KEY-----
      private-key: |
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
        -----END PRIVATE KEY-----
```

## JWT 令牌格式

### 标准声明（Claims）

JAiRouter 支持以下标准 JWT 声明：

```json
{
  "iss": "jairouter",           // 发行者
  "sub": "user123",             // 主题（用户ID）
  "aud": "jairouter-api",       // 受众
  "exp": 1640995200,            // 过期时间
  "iat": 1640991600,            // 签发时间
  "nbf": 1640991600,            // 生效时间
  "jti": "unique-token-id"      // JWT ID
}
```

### 自定义声明

您可以在 JWT 中包含自定义声明：

```json
{
  "sub": "user123",
  "permissions": ["read", "write"],
  "department": "IT",
  "role": "admin",
  "custom_data": {
    "user_level": "premium",
    "features": ["feature1", "feature2"]
  }
}
```

## 密码加密配置

JAiRouter 支持多种密码加密方式，以提高安全性：

### BCrypt 加密配置

BCrypt 是一种安全的密码哈希函数，推荐在生产环境中使用：

```java
// 示例代码：生成BCrypt加密的密码
String rawPassword = "admin123";
org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = 
    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
String encodedPassword = encoder.encode(rawPassword);
System.out.println("Encoded password: " + encodedPassword);
```

在配置文件中使用BCrypt加密的密码：

```yaml
jairouter:
  security:
    jwt:
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"
          roles: [ "ADMIN", "USER" ]
          enabled: true
```

### 明文密码配置（仅限开发环境）

为了方便开发测试，支持明文密码配置：

```yaml
jairouter:
  security:
    jwt:
      accounts:
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### 密码编码器配置

在 [SecurityConfiguration.java](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityConfiguration.java) 中配置密码编码器：

```java
/**
 * 配置密码编码器 - BCrypt作为默认编码器
 */
@Bean
public PasswordEncoder passwordEncoder() {
    java.util.Map<String, org.springframework.security.crypto.password.PasswordEncoder> encoders = 
        java.util.Map.of(
            "bcrypt", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10),
            "noop", org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance()
        );
    
    org.springframework.security.crypto.password.DelegatingPasswordEncoder delegatingEncoder = 
        new org.springframework.security.crypto.password.DelegatingPasswordEncoder("bcrypt", encoders);
    
    // 设置默认的密码编码器
    delegatingEncoder.setDefaultPasswordEncoderForMatches(encoders.get("bcrypt"));
    
    return delegatingEncoder;
}
```

## 令牌刷新机制

### 配置刷新令牌

```yaml
jairouter:
  security:
    jwt:
      expiration-minutes: 15        # 访问令牌15分钟过期
      refresh-expiration-days: 30   # 刷新令牌30天过期
      refresh-endpoint: "/auth/refresh"
```

### 刷新令牌流程

1. **获取访问令牌和刷新令牌**
```bash
curl -X POST http://localhost:8080/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "user", "password": "pass"}'
```

响应：
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 900,
  "token_type": "Bearer"
}
```

2. **使用访问令牌访问 API**
```bash
curl -H "Authorization: Bearer access_token_here" \
     http://localhost:8080/v1/chat/completions
```

3. **刷新访问令牌**
```bash
curl -X POST http://localhost:8080/auth/refresh \
     -H "Content-Type: application/json" \
     -d '{"refresh_token": "refresh_token_here"}'
```

## JWT 令牌持久化存储

### 启用令牌持久化

JAiRouter 支持将 JWT 令牌持久化存储以增强管理和监控：

```yaml
jairouter:
  security:
    jwt:
      # 令牌持久化配置
      persistence:
        enabled: true
        primary-storage: h2    # h2, redis, memory
        fallback-storage: memory  # memory
        
        # 清理配置
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # 每天凌晨2点
          retention-days: 30
          batch-size: 1000
        
        # 内存存储配置
        memory:
          max-tokens: 50000
          cleanup-threshold: 0.8  # 80%触发清理
          lru-enabled: true
          
        # H2 数据库存储配置
        h2:
          table-name: "jwt_tokens"  # 表名
          max-batch-size: 1000      # 批量操作最大大小
```

### 令牌管理功能

启用持久化后，您可以：

1. **跟踪活动令牌**：监控所有已颁发令牌及其状态
2. **令牌生命周期管理**：自动状态更新和清理
3. **增强安全性**：支持 H2 数据库的持久化黑名单
4. **审计跟踪**：完整的令牌操作审计日志
5. **性能监控**：令牌操作的指标和健康检查

### H2 数据库存储优势

使用 H2 数据库存储 JWT 令牌具有以下优势：

1. **默认存储方式**：H2 数据库现在是 JWT 令牌的默认存储方式
2. **持久化**：数据不会因应用重启而丢失
3. **高性能**：嵌入式数据库，无网络开销
4. **易于维护**：单一数据库文件，便于备份
5. **强大查询**：支持复杂的 SQL 查询
6. **事务支持**：保证数据一致性
7. **可视化管理**：H2 控制台便于调试
8. **生产就绪**：满足生产环境要求

### H2 数据库表结构

JWT 令牌在 H2 数据库中存储在以下表中：

#### jwt_tokens 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(255) | 令牌唯一标识符 |
| user_id | VARCHAR(255) | 用户ID |
| token_hash | VARCHAR(255) | 令牌哈希值 |
| issued_at | TIMESTAMP | 签发时间 |
| expires_at | TIMESTAMP | 过期时间 |
| status | VARCHAR(50) | 令牌状态 (ACTIVE, REVOKED, EXPIRED) |
| device_info | VARCHAR(500) | 设备信息 |
| ip_address | VARCHAR(50) | IP地址 |
| user_agent | VARCHAR(500) | 用户代理 |
| revoke_reason | VARCHAR(1000) | 撤销原因 |
| revoked_at | TIMESTAMP | 撤销时间 |
| revoked_by | VARCHAR(255) | 撤销者 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### 索引优化

系统自动创建以下索引以提高查询性能：

- `idx_jwt_token_id`: 令牌ID索引
- `idx_jwt_user_id`: 用户ID索引
- `idx_jwt_token_hash`: 令牌哈希索引
- `idx_jwt_expires_at`: 过期时间索引
- `idx_jwt_status`: 状态索引
- `idx_jwt_issued_at`: 签发时间索引

### 令牌存储结构

令牌存储包含以下元数据：

```json
{
  "id": "token-uuid-123",
  "userId": "user123",
  "tokenHash": "sha256-hash-of-token",
  "issuedAt": "2025-01-15T10:30:00Z",
  "expiresAt": "2025-01-15T11:30:00Z",
  "status": "ACTIVE",
  "deviceInfo": "Mozilla/5.0...",
  "ipAddress": "192.168.1.100",
  "revokeReason": null,
  "revokedAt": null,
  "revokedBy": null,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

## 黑名单功能

### 启用黑名单

```yaml
jairouter:
  security:
    jwt:
      blacklist-enabled: true
      # 增强的黑名单持久化
      blacklist:
        persistence:
          enabled: true
          primary-storage: h2  # h2, redis, memory
          fallback-storage: memory
          max-memory-size: 10000
          cleanup-interval: 3600  # 1小时
```

### 令牌撤销

撤销令牌（登出）：

```bash
curl -X POST http://localhost:8080/auth/logout \
     -H "Authorization: Bearer token_to_revoke"
```

### 增强的令牌管理

启用持久化后，您可以使用管理 API：

#### 获取令牌列表
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20&status=ACTIVE" \
     -H "Authorization: Bearer admin_token"
```

#### 撤销特定令牌
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/token-uuid-123/revoke" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{"reason": "安全策略违规"}'
```

#### 批量令牌撤销
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/revoke-batch" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "tokenIds": ["token-uuid-123", "token-uuid-456"],
       "reason": "批量安全清理"
     }'
```

#### 手动清理
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/cleanup" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{"cleanupType": "ALL"}'
```

#### 获取黑名单统计
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/blacklist/stats" \
     -H "Authorization: Bearer admin_token"
```

### H2 黑名单存储

JWT 黑名单现在默认使用 H2 数据库存储，提供以下优势：

1. **持久化存储**：黑名单数据在应用重启后不会丢失
2. **高性能查询**：通过索引优化快速检查令牌是否在黑名单中
3. **自动清理**：定期清理过期的黑名单记录
4. **易于备份**：单一数据库文件便于备份和恢复

#### jwt_blacklist 表结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| token_hash | VARCHAR(255) | 令牌哈希值（唯一） |
| user_id | VARCHAR(255) | 用户ID |
| revoked_at | TIMESTAMP | 撤销时间 |
| expires_at | TIMESTAMP | 过期时间 |
| reason | VARCHAR(1000) | 撤销原因 |
| revoked_by | VARCHAR(255) | 撤销者 |
| created_at | TIMESTAMP | 创建时间 |

#### 索引优化

系统自动创建以下索引以提高黑名单查询性能：

- `idx_blacklist_token_hash`: 令牌哈希索引（唯一）
- `idx_blacklist_user_id`: 用户ID索引
- `idx_blacklist_expires_at`: 过期时间索引
- `idx_blacklist_revoked_at`: 撤销时间索引

## 与 API Key 集成

JWT 认证可以与 API Key 认证同时使用：

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
    jwt:
      enabled: true
```

认证优先级：
1. 如果请求包含 JWT 令牌，优先使用 JWT 认证
2. 如果 JWT 认证失败或不存在 JWT 令牌，尝试 API Key 认证
3. 如果两种认证都失败，返回 401 错误

## 性能优化

### 缓存配置

```yaml
jairouter:
  security:
    performance:
      cache:
        redis:
          enabled: true
        local:
          jwt-blacklist:
            max-size: 10000
            expire-after-write: 86400
```

### 异步验证

```yaml
jairouter:
  security:
    performance:
      authentication:
        async-enabled: true
        thread-pool-size: 10
        timeout-ms: 5000
```

## 安全审计和监控

### 增强的审计配置

```yaml
jairouter:
  security:
    # 增强的审计配置
    audit:
      enabled: true
      log-level: "INFO"
      include-request-body: false
      include-response-body: false
      retention-days: 90
      
      # JWT 操作审计
      jwt-operations:
        enabled: true
        log-token-details: false  # 不记录完整令牌
        log-user-agent: true
        log-ip-address: true
      
      # API Key 操作审计
      api-key-operations:
        enabled: true
        log-key-details: false   # 不记录完整密钥
        log-usage-patterns: true
        log-ip-address: true
      
      # 安全事件审计
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 10
          token-revoke-per-minute: 5
          api-key-usage-per-minute: 100
      
      # 审计存储配置
      storage:
        type: "h2"              # 选项: h2, file, database
        h2:
          table-name: "security_audit_events"  # H2表名
        file-path: "logs/security-audit.log"
        rotation:
          max-file-size: "100MB"
          max-files: 30
        # 可选: 数据库存储
        database:
          enabled: false
          table-name: "security_audit_events"
```

### 审计事件类型

系统记录以下 JWT 和 API Key 事件：

#### JWT 令牌事件
- **令牌颁发**：创建新 JWT 令牌时
- **令牌刷新**：刷新访问令牌时
- **令牌撤销**：手动撤销令牌时
- **令牌验证**：验证令牌时（成功/失败）
- **令牌过期**：令牌自然过期时

#### API Key 事件
- **密钥创建**：生成新 API 密钥时
- **密钥使用**：使用 API 密钥进行认证时
- **密钥撤销**：撤销 API 密钥时
- **密钥过期**：API 密钥过期时

#### 安全事件
- **可疑活动**：异常认证模式
- **认证失败**：失败的登录尝试
- **批量操作**：大量令牌/密钥操作

### H2 审计存储

安全审计事件现在默认使用 H2 数据库存储，提供以下优势：

1. **持久化存储**：审计数据不会因应用重启而丢失
2. **高性能查询**：支持复杂的审计事件查询和分析
3. **易于分析**：通过 SQL 查询进行审计数据分析
4. **备份恢复**：单一数据库文件便于备份和恢复

#### security_audit 表结构

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

#### 索引优化

系统自动创建以下索引以提高审计查询性能：

- `idx_audit_event_id`: 事件ID索引
- `idx_audit_timestamp`: 时间戳索引
- `idx_audit_user_id`: 用户ID索引
- `idx_audit_event_type`: 事件类型索引
- `idx_audit_client_ip`: 客户端IP索引
- `idx_audit_success`: 成功状态索引

### 审计事件结构

```json
{
  "id": "audit-event-uuid",
  "type": "JWT_TOKEN_ISSUED",
  "userId": "user123",
  "resourceId": "token-uuid-123",
  "action": "ISSUE_TOKEN",
  "details": "为用户登录颁发 JWT 令牌",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "success": true,
  "timestamp": "2025-01-15T10:30:00Z",
  "metadata": {
    "tokenExpiresAt": "2025-01-15T11:30:00Z",
    "deviceInfo": "桌面浏览器",
    "authMethod": "username_password"
  }
}
```

### 安全报告生成

生成综合安全报告：

```bash
# 获取最近30天的安全报告
curl -X GET "http://localhost:8080/api/security/audit/report?from=2025-01-01&to=2025-01-31" \
     -H "Authorization: Bearer admin_token"
```

响应包括：
- JWT 和 API Key 操作总数
- 失败认证统计
- 可疑活动警报
- 顶级 IP 地址和用户
- 安全事件趋势

### 监控指标

#### JWT 令牌指标
- `jairouter_security_jwt_tokens_issued_total`：颁发的令牌总数
- `jairouter_security_jwt_tokens_refreshed_total`：刷新的令牌总数
- `jairouter_security_jwt_tokens_revoked_total`：撤销的令牌总数
- `jairouter_security_jwt_validation_duration_seconds`：令牌验证耗时
- `jairouter_security_jwt_blacklist_size`：当前黑名单大小
- `jairouter_security_jwt_active_tokens`：活动令牌数

#### API Key 指标
- `jairouter_security_api_keys_created_total`：创建的 API 密钥总数
- `jairouter_security_api_keys_used_total`：API 密钥使用总数
- `jairouter_security_api_keys_revoked_total`：撤销的 API 密钥总数
- `jairouter_security_api_key_validation_duration_seconds`：API 密钥验证耗时

#### 存储指标
- `jairouter_security_storage_operations_total`：存储操作总数
- `jairouter_security_storage_errors_total`：存储操作错误
- `jairouter_security_h2_connection_status`：H2 连接健康状态
- `jairouter_security_memory_usage_bytes`：令牌存储内存使用量

## 安全最佳实践

### 1. 密钥管理

- **使用强密钥**：对称密钥至少 256 位（32 字节）
- **定期轮换**：定期更换 JWT 签名密钥
- **安全存储**：使用环境变量或密钥管理系统存储密钥
- **分离密钥**：不同环境使用不同的密钥

### 2. 令牌生命周期

- **短期访问令牌**：访问令牌应该有较短的过期时间（15-60分钟）
- **长期刷新令牌**：刷新令牌可以有较长的过期时间（7-30天）
- **及时撤销**：在用户登出或检测到异常时及时撤销令牌

### 3. 算法选择

- **生产环境推荐**：使用 RS256 等非对称算法
- **开发环境**：可以使用 HS256 等对称算法
- **避免弱算法**：不要使用 none 算法

### 4. 声明验证

- **验证标准声明**：始终验证 exp、iat、nbf 等标准声明
- **验证自定义声明**：根据业务需求验证自定义声明
- **最小权限原则**：只在令牌中包含必要的信息

## 故障排除

### 常见问题

#### 1. 令牌验证失败

**错误信息**：`Invalid JWT token`

**可能原因**：
- 令牌格式不正确
- 签名验证失败
- 令牌已过期
- 密钥配置错误

**解决方案**：
1. 检查令牌格式是否正确
2. 验证签名密钥配置
3. 检查令牌是否过期
4. 查看详细错误日志

#### 2. 令牌过期

**错误信息**：`JWT token has expired`

**解决方案**：
1. 使用刷新令牌获取新的访问令牌
2. 重新登录获取新令牌
3. 调整令牌过期时间配置

#### 3. 黑名单问题

**错误信息**：`JWT token has been revoked`

**解决方案**：
1. 检查令牌是否被撤销
2. 重新登录获取新令牌
3. 检查黑名单缓存配置

### 调试技巧

#### 1. 启用详细日志

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
```

#### 2. 令牌解析工具

使用在线工具解析 JWT 令牌：
- https://jwt.io/
- https://jwt-decoder.com/

#### 3. 验证令牌内容

```bash
# 解码 JWT 令牌（不验证签名）
echo "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | base64 -d
```

## 示例配置

### 开发环境

```yaml
jairouter:
  security:
    enabled: true
    jwt:
      enabled: true
      secret: "dev-jwt-secret-key-for-development-only"
      algorithm: "HS256"
      expiration-minutes: 60
      refresh-expiration-days: 1
      issuer: "jairouter-dev"
      blacklist-enabled: true
      accounts:
        - username: "admin"
          password: "{noop}admin123"
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### 生产环境

```yaml
jairouter:
  security:
    enabled: true
    jwt:
      enabled: true
      algorithm: "RS256"
      public-key: "${JWT_PUBLIC_KEY}"
      private-key: "${JWT_PRIVATE_KEY}"
      expiration-minutes: 15
      refresh-expiration-days: 30
      issuer: "jairouter-prod"
      blacklist-enabled: true
      blacklist-cache:
        expiration-seconds: 86400
        max-size: 50000
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"
          roles: [ "ADMIN", "USER" ]
          enabled: true
```

### 高可用环境

```yaml
jairouter:
  security:
    jwt:
      enabled: true
      algorithm: "RS256"
      public-key: "${JWT_PUBLIC_KEY}"
      expiration-minutes: 15
    performance:
      cache:
        redis:
          enabled: true
          host: "${REDIS_HOST}"
          port: "${REDIS_PORT}"
          password: "${REDIS_PASSWORD}"
          cluster:
            enabled: true
            nodes:
              - "${REDIS_NODE1}"
              - "${REDIS_NODE2}"
              - "${REDIS_NODE3}"
```

## 相关文档

- [API Key 管理指南](api-key-management.md)
- [数据脱敏规则配置](data-sanitization.md)
- [安全功能故障排除指南](troubleshooting.md)
- [安全监控和告警](../monitoring/index.md)
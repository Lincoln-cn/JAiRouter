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

### 2. 设置 JWT 密钥

#### 对称密钥（HS256/HS384/HS512）

```bash
# 生成强密钥
export JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long"
```

#### 非对称密钥（RS256/RS384/RS512）

```yaml
jairouter:
  security:
    jwt:
      algorithm: "RS256"
      public-key: "${JWT_PUBLIC_KEY}"
      private-key: "${JWT_PRIVATE_KEY}"
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
| `enabled` | boolean | false | 是否启用 JWT 认证 |
| `secret` | string | - | JWT 签名密钥（对称算法） |
| `public-key` | string | - | JWT 公钥（非对称算法） |
| `private-key` | string | - | JWT 私钥（非对称算法） |
| `algorithm` | string | "HS256" | JWT 签名算法 |
| `expiration-minutes` | int | 60 | 访问令牌过期时间（分钟） |
| `refresh-expiration-days` | int | 7 | 刷新令牌过期时间（天） |
| `issuer` | string | "jairouter" | JWT 发行者标识 |
| `blacklist-enabled` | boolean | true | 是否启用黑名单功能 |
| `accounts` | array | [] | 用户账户列表 |

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

在 `SecurityConfiguration.java` 中配置密码编码器：

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
      expiration-minutes: 15        // 访问令牌15分钟过期
      refresh-expiration-days: 30   // 刷新令牌30天过期
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

## 黑名单功能

### 启用黑名单

```yaml
jairouter:
  security:
    jwt:
      blacklist-enabled: true
      blacklist-cache:
        expiration-seconds: 86400   // 24小时
        max-size: 10000            // 最大缓存条目数
```

### 令牌撤销

撤销令牌（登出）：

```bash
curl -X POST http://localhost:8080/auth/logout \
     -H "Authorization: Bearer token_to_revoke"
```

### 批量撤销

撤销用户的所有令牌：

```bash
curl -X POST http://localhost:8080/auth/revoke-all \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{"user_id": "user123"}'
```

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

## 监控和审计

### 审计事件

```yaml
jairouter:
  security:
    audit:
      enabled: true
      event-types:
        jwt-token-issued: true
        jwt-token-refreshed: true
        jwt-token-revoked: true
        jwt-token-expired: true
        jwt-validation-failed: true
```

### 监控指标

- `jairouter_security_jwt_tokens_issued_total`：签发的令牌总数
- `jairouter_security_jwt_tokens_refreshed_total`：刷新的令牌总数
- `jairouter_security_jwt_tokens_revoked_total`：撤销的令牌总数
- `jairouter_security_jwt_validation_duration_seconds`：令牌验证耗时

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
// 解码 JWT 令牌（不验证签名）
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
- [安全监控和告警](monitoring.md)
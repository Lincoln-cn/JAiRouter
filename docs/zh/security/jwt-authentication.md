# JWT 认证配置说明

<!-- 版本信息 -->
> **文档版本**: 2.0.0
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a
> **作者**: Lincoln
<!-- /版本信息 -->

## 概述

JAiRouter 支持 JWT（JSON Web Token）认证，可以与现有的身份认证系统集成。JWT 认证提供了无状态的认证机制，支持令牌刷新和黑名单功能。

## 功能特性

### 核心功能

- **标准 JWT 支持**：完全兼容 RFC 7519 标准
- **多种签名算法**：支持 HS256、HS384、HS512、RS256、RS384、RS512
- **令牌刷新**：支持访问令牌和刷新令牌机制
- **黑名单功能**：支持令牌撤销和登出
- **与 API Key 共存**：可以与 API Key 认证同时使用
- **用户名密码登录**：支持通过用户名密码获取JWT令牌
- **持久化存储**：支持将 JWT 账户和令牌信息存储在 H2 数据库中
- **H2 数据库默认存储**：H2 数据库是 JWT 数据的默认持久化存储方式

### 账户管理增强 (v1.5.7+)

- **账户自动初始化**：JWT 账户从 YAML 配置自动初始化到数据库
- **账户状态管理**：支持账户启用/禁用状态切换

### 安全黑名单 (v1.7.0+)

- **安全黑名单管理**：支持 IP/用户/令牌级别的黑名单
- **黑名单自动清理**：定期清理过期的黑名单记录

## 快速开始

### 1. 启用 JWT 认证

配置文件：`config/auth/jwt.yml`

```yaml
jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"
      algorithm: "HS256"
      expiration-minutes: 60
      jwt-header: "Jairouter_Token"  # 自定义 JWT 头
```

### 2. 设置 JWT 密钥

#### 对称密钥（HS256/HS384/HS512）

```bash
# 生产环境 JWT 密钥配置
export JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"

# 可选的过期时间配置
export JWT_EXPIRATION_MINUTES=15
export JWT_REFRESH_EXPIRATION_DAYS=30
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
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### 4. 客户端使用

在 HTTP 请求头中添加 JWT 令牌：

```bash
curl -H "Jairouter_Token: your-jwt-token-here" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

---

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

---

## JWT 配置参数

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
| `jwt-header` | string | "Jairouter_Token" | 自定义 JWT 头名称 |
| `accounts` | array | [] | 用户账户列表 |

---

## 支持的签名算法

### 对称算法（HMAC）

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

### 非对称算法（RSA）

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

---

## 令牌刷新机制

### 配置刷新令牌

```yaml
jairouter:
  security:
    jwt:
      expiration-minutes: 15        # 访问令牌15分钟过期
      refresh-expiration-days: 30   # 刷新令牌30天过期
```

### 刷新令牌流程

1. **获取访问令牌和刷新令牌**
```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{"username": "user", "password": "pass"}'
```

2. **使用访问令牌访问 API**
```bash
curl -H "Jairouter_Token: access_token_here" \
     http://localhost:8080/v1/chat/completions
```

3. **刷新访问令牌**
```bash
curl -X POST http://localhost:8080/api/auth/jwt/refresh \
     -H "Content-Type: application/json" \
     -d '{"refresh_token": "refresh_token_here"}'
```

---

## JWT 令牌持久化存储

### 启用令牌持久化

```yaml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: h2    # h2, redis, memory
        fallback-storage: memory
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # 每天凌晨2点
          retention-days: 30
          batch-size: 1000
```

### H2 数据库存储优势

1. **默认存储方式**：H2 数据库是 JWT 令牌的默认存储方式
2. **持久化**：数据不会因应用重启而丢失
3. **高性能**：嵌入式数据库，无网络开销
4. **易于维护**：单一数据库文件，便于备份
5. **强大查询**：支持复杂的 SQL 查询

---

## 黑名单功能

### 启用黑名单

```yaml
jairouter:
  security:
    jwt:
      blacklist-enabled: true
      blacklist:
        persistence:
          enabled: true
          primary-storage: h2
          fallback-storage: memory
          max-memory-size: 10000
          cleanup-interval: 3600
```

### 令牌撤销

```bash
curl -X POST http://localhost:8080/api/auth/jwt/logout \
     -H "Jairouter_Token: token_to_revoke"
```

---

## 令牌管理 API

### 获取令牌列表
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20&status=ACTIVE" \
     -H "Jairouter_Token: admin_token"
```

### 撤销特定令牌
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/token-uuid-123/revoke" \
     -H "Jairouter_Token: admin_token" \
     -H "Content-Type: application/json" \
     -d '{"reason": "安全策略违规"}'
```

### 批量令牌撤销
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/revoke-batch" \
     -H "Jairouter_Token: admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "tokenIds": ["token-uuid-123", "token-uuid-456"],
       "reason": "批量安全清理"
     }'
```

### 获取黑名单统计
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/blacklist/stats" \
     -H "Jairouter_Token: admin_token"
```

---

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

---

## 安全最佳实践

### 1. 密钥管理

- **使用强密钥**：对称密钥至少 256 位（32 字节）
- **定期轮换**：定期更换 JWT 签名密钥
- **安全存储**：使用环境变量或密钥管理系统存储密钥

### 2. 令牌生命周期

- **短期访问令牌**：访问令牌应该有较短的过期时间（15-60分钟）
- **长期刷新令牌**：刷新令牌可以有较长的过期时间（7-30天）
- **及时撤销**：在用户登出时及时撤销令牌

### 3. 算法选择

- **生产环境推荐**：使用 RS256 等非对称算法
- **开发环境**：可以使用 HS256 等对称算法
- **避免弱算法**：不要使用 none 算法

---

## 故障排除

### 常见问题

#### 令牌验证失败

**错误**：`Invalid JWT token`

**解决方案**：
1. 检查令牌格式
2. 验证签名密钥配置
3. 检查令牌是否过期
4. 查看详细错误日志

#### 令牌过期

**错误**：`JWT token has expired`

**解决方案**：
1. 使用刷新令牌获取新的访问令牌
2. 重新登录获取新令牌
3. 调整令牌过期时间配置

### 调试技巧

#### 启用详细日志

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
```

#### 令牌解析工具

- https://jwt.io/
- https://jwt-decoder.com/

---

*最后更新：2026-05-21*

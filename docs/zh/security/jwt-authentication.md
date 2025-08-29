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

```
# 生产环境 JWT 密钥配置
export PROD_JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"

# 可选的过期时间配置
export PROD_JWT_EXPIRATION_MINUTES=15
export PROD_JWT_REFRESH_EXPIRATION_DAYS=30
```

#### 非对称密钥（RS256/RS384/RS512）

```
# 生产环境非对称密钥配置
export JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\nyour-public-key-here\n-----END PUBLIC KEY-----"
export JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nyour-private-key-here\n-----END PRIVATE KEY-----"
```

```

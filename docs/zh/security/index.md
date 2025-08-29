# 安全功能

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->



JAiRouter 提供了企业级的安全功能，包括身份认证、数据脱敏、安全审计和监控等。这些功能确保您的 AI 模型服务在提供高性能路由的同时，也能满足严格的安全和合规要求。

## 功能概览

### 🔐 身份认证
- **API Key 认证**：支持多级权限控制和过期时间管理
- **JWT 认证**：支持标准 JWT 令牌和刷新机制
- **双重认证**：API Key 和 JWT 可以同时使用
- **缓存优化**：支持 Redis 和本地缓存提升性能

### 🛡️ 数据脱敏
- **双向脱敏**：支持请求和响应数据脱敏
- **智能识别**：自动识别 PII 数据和敏感词汇
- **多种策略**：支持掩码、替换、删除、哈希等脱敏策略
- **白名单机制**：支持用户和 IP 白名单

### 📊 安全审计
- **全面记录**：记录所有安全相关事件
- **实时告警**：支持异常情况实时告警
- **长期存储**：支持审计日志的长期存储和归档
- **合规支持**：满足数据保护法规要求

### 📈 安全监控
- **性能指标**：提供详细的安全功能性能指标
- **健康检查**：实时监控安全功能状态
- **告警通知**：支持邮件、Webhook 等多种告警方式

## 快速开始

### 1. 启用安全功能

在 `application.yml` 中启用安全功能：

```yaml
jairouter:
  security:
    enabled: true
```

### 2. 配置 API Key 认证

```yaml
jairouter:
  security:
    api-key:
      enabled: true
      keys:
        - key-id: "admin-key"
          key-value: "${ADMIN_API_KEY}"
          permissions: ["admin", "read", "write"]
          expires-at: "2025-12-31T23:59:59"
```

### 3. 配置数据脱敏

```yaml
jairouter:
  security:
    sanitization:
      request:
        enabled: true
        sensitive-words: ["password", "secret"]
        pii-patterns: ["\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"]
```

### 4. 测试安全功能

```bash
curl -H "X-API-Key: your-api-key" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

## 文档导航

- [API Key 管理](api-key-management.md) - API Key 的配置和管理
- [JWT 认证](jwt-authentication.md) - JWT 认证的配置和使用
- [数据脱敏](data-sanitization.md) - 数据脱敏功能的配置
- [故障排除](troubleshooting.md) - 常见问题的解决方案

## 架构概览

``mermaid
graph TB
    Client[客户端] --> Gateway[API网关层]
    Gateway --> Auth[认证过滤器]
    Auth --> Sanitize[数据脱敏过滤器]
    Sanitize --> Controller[控制器层]
    Controller --> Service[服务层]
    Service --> Backend[后端AI服务]
    Backend --> Service
    Service --> ResponseSanitize[响应脱敏过滤器]
    ResponseSanitize --> Client
    
    Auth --> SecurityConfig[安全配置]
    Auth --> TokenValidator[令牌验证器]
    Sanitize --> SanitizeConfig[脱敏配置]
    
    SecurityConfig --> ConfigStore[配置存储]
    SanitizeConfig --> ConfigStore
    
    Auth --> AuditLogger[审计日志]
    Sanitize --> AuditLogger
    AuditLogger --> Monitoring[监控系统]
```

## 配置示例

### 开发环境

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      keys:
        - key-id: "dev-admin"
          key-value: "dev-admin-key-12345"
          permissions: ["admin", "read", "write"]
    sanitization:
      request:
        enabled: true
        whitelist-users: ["dev-admin"]
    audit:
      enabled: true
      log-level: "DEBUG"
```

### 生产环境

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      cache-enabled: true
      keys:
        - key-id: "prod-admin"
          key-value: "${PROD_ADMIN_API_KEY}"
          permissions: ["admin", "read", "write"]
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"
    sanitization:
      request:
        enabled: true
        sensitive-words: ["password", "secret", "token"]
      response:
        enabled: true
    audit:
      enabled: true
      retention-days: 365
    performance:
      cache:
        redis:
          enabled: true
```

## 最佳实践

### 认证安全
- 使用强 API Key（至少 32 个字符）
- 定期轮换 API Key 和 JWT 密钥
- 设置合理的过期时间
- 启用缓存提升性能

### 数据保护
- 根据业务需求配置脱敏规则
- 定期审查和更新脱敏模式
- 合理设置白名单
- 确保合规性

### 运维安全
- 启用详细的审计日志
- 配置实时告警
- 定期备份配置
- 监控系统性能

## 环境变量

```bash
# API Key 配置
export ADMIN_API_KEY="your-admin-api-key-here"
export USER_API_KEY="your-user-api-key-here"

# 生产环境 API Key 配置
export PROD_ADMIN_API_KEY="your-production-admin-api-key-here"
export PROD_SERVICE_API_KEY="your-production-service-api-key-here"
export PROD_READONLY_API_KEY="your-production-readonly-api-key-here"

# JWT 配置
export JWT_SECRET="your-jwt-secret-key-here"

# 生产环境 JWT 配置
export PROD_JWT_SECRET="your-production-jwt-secret-key-here"

# Redis 配置（如果启用）
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD="your-redis-password"

# 安全告警配置
export SECURITY_ALERT_EMAIL="security-alerts@your-company.com"
export SECURITY_ALERT_WEBHOOK="https://your-webhook-url.com/security-alerts"
```

## 监控指标

### 认证指标
- `jairouter_security_authentication_attempts_total`: 认证尝试总数
- `jairouter_security_authentication_successes_total`: 认证成功总数
- `jairouter_security_authentication_failures_total`: 认证失败总数

### 脱敏指标
- `jairouter_security_sanitization_operations_total`: 脱敏操作总数
- `jairouter_security_sanitization_duration_seconds`: 脱敏操作耗时
- `jairouter_security_sanitization_patterns_matched_total`: 匹配的模式总数

## 下一步

1. 阅读 [API Key 管理指南](api-key-management.md) 了解详细的认证配置
2. 查看 [数据脱敏配置](data-sanitization.md) 学习如何保护敏感数据
3. 参考 [故障排除指南](troubleshooting.md) 解决常见问题
4. 配置监控和告警确保系统安全运行
# API Key 管理指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->



## 概述

JAiRouter 的 API Key 认证功能为系统提供了安全的访问控制机制。通过 API Key，您可以控制谁可以访问您的 AI 模型服务，并为不同的用户分配不同的权限级别。

## 功能特性

- **多级权限控制**：支持 admin、read、write、delete 等不同权限级别
- **过期时间管理**：支持设置 API Key 的过期时间
- **使用统计**：记录每个 API Key 的使用情况
- **缓存优化**：支持 Redis 和本地缓存，提升认证性能
- **动态管理**：支持运行时添加、删除和更新 API Key

## 快速开始

### 1. 启用 API Key 认证

在 `application.yml` 中启用安全功能：

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      header-name: "X-API-Key"
```

### 2. 配置 API Key

```yaml
jairouter:
  security:
    api-key:
      keys:
        - key-id: "admin-key-001"
          key-value: "${ADMIN_API_KEY}"
          description: "管理员API密钥"
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "2025-12-31T23:59:59"
          enabled: true
```

### 3. 客户端使用

在 HTTP 请求头中添加 API Key：

```bash
curl -H "X-API-Key: your-api-key-here" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

## 详细配置

### API Key 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | true | 是否启用 API Key 认证 |
| `header-name` | string | "X-API-Key" | API Key 请求头名称 |
| `default-expiration-days` | int | 365 | 默认过期天数 |
| `cache-enabled` | boolean | true | 是否启用缓存 |
| `cache-expiration-seconds` | int | 3600 | 缓存过期时间（秒） |

### API Key 属性

每个 API Key 包含以下属性：

```yaml
- key-id: "unique-key-identifier"      # 唯一标识符
  key-value: "actual-api-key-string"   # 实际的 API Key 值
  description: "Key description"        # 描述信息
  permissions: ["read", "write"]        # 权限列表
  expires-at: "2025-12-31T23:59:59"    # 过期时间
  enabled: true                         # 是否启用
  metadata:                            # 元数据
    created-by: "admin"
    department: "IT"
```

### 权限级别说明

| 权限 | 说明 | 适用场景 |
|------|------|----------|
| `read` | 只读权限，可以查询模型和发送推理请求 | 普通用户、客户端应用 |
| `write` | 写权限，可以修改配置（不包括安全配置） | 服务管理员 |
| `delete` | 删除权限，可以删除配置和数据 | 高级管理员 |
| `admin` | 管理员权限，可以管理所有功能包括安全配置 | 系统管理员 |

## 环境变量配置

为了安全起见，建议通过环境变量设置 API Key：

### Linux/macOS

```bash
export ADMIN_API_KEY="your-admin-api-key-here"
export USER_API_KEY="your-user-api-key-here"
```

### Windows

```cmd
set ADMIN_API_KEY=your-admin-api-key-here
set USER_API_KEY=your-user-api-key-here
```

### Docker

```bash
docker run -e ADMIN_API_KEY="your-admin-api-key-here" \
           -e USER_API_KEY="your-user-api-key-here" \
           jairouter:latest
```

## 缓存配置

### Redis 缓存

```yaml
jairouter:
  security:
    performance:
      cache:
        redis:
          enabled: true
          host: "localhost"
          port: 6379
          password: "your-redis-password"
          database: 0
```

### 本地缓存

```yaml
jairouter:
  security:
    performance:
      cache:
        local:
          enabled: true
          api-key:
            max-size: 1000
            expire-after-write: 3600
```

## 监控和审计

### 使用统计

系统会自动记录每个 API Key 的使用统计：

- 总请求数
- 成功请求数
- 失败请求数
- 最后使用时间
- 每日使用量

### 审计日志

启用审计功能后，系统会记录所有认证相关的事件：

```yaml
jairouter:
  security:
    audit:
      enabled: true
      event-types:
        authentication-success: true
        authentication-failure: true
```

### 监控指标

系统提供以下 Prometheus 指标：

- `jairouter_security_authentication_attempts_total`：认证尝试总数
- `jairouter_security_authentication_successes_total`：认证成功总数
- `jairouter_security_authentication_failures_total`：认证失败总数
- `jairouter_security_authentication_duration_seconds`：认证耗时

## 最佳实践

### 1. API Key 安全

- **使用强密钥**：API Key 应该足够长且随机，建议至少 32 个字符
- **定期轮换**：定期更换 API Key，特别是在人员变动时
- **环境变量**：不要在配置文件中硬编码 API Key，使用环境变量
- **最小权限原则**：只授予必要的权限

### 2. 过期时间管理

- **合理设置过期时间**：根据使用场景设置合适的过期时间
- **提前续期**：在 API Key 过期前及时续期
- **监控过期**：设置告警监控即将过期的 API Key

### 3. 权限管理

- **分级授权**：根据用户角色分配不同的权限级别
- **定期审查**：定期审查 API Key 的权限分配
- **及时回收**：及时禁用或删除不再使用的 API Key

### 4. 性能优化

- **启用缓存**：在生产环境中启用 Redis 缓存
- **合理设置缓存时间**：根据安全要求和性能需求设置缓存过期时间
- **监控缓存命中率**：监控缓存性能，及时调整配置

## 故障排除

### 常见问题

#### 1. 认证失败

**问题**：客户端收到 401 Unauthorized 错误

**可能原因**：
- API Key 不正确
- API Key 已过期
- API Key 已被禁用
- 请求头名称不正确

**解决方案**：
1. 检查 API Key 是否正确
2. 检查 API Key 是否过期
3. 检查配置中的 `header-name` 设置
4. 查看审计日志获取详细错误信息

#### 2. 权限不足

**问题**：客户端收到 403 Forbidden 错误

**可能原因**：
- API Key 权限不足
- 访问了需要更高权限的接口

**解决方案**：
1. 检查 API Key 的权限配置
2. 为 API Key 添加必要的权限
3. 使用具有足够权限的 API Key

#### 3. 性能问题

**问题**：认证响应时间过长

**可能原因**：
- 缓存未启用或配置不当
- Redis 连接问题
- API Key 数量过多

**解决方案**：
1. 启用并正确配置缓存
2. 检查 Redis 连接状态
3. 优化 API Key 配置
4. 监控认证性能指标

### 调试技巧

#### 1. 启用详细日志

```yaml
logging:
  level:
    org.unreal.modelrouter.security: DEBUG
```

#### 2. 检查审计日志

```bash
tail -f logs/security-audit.log | grep authentication
```

#### 3. 监控指标

访问 Prometheus 指标端点：
```
http://localhost:8080/actuator/prometheus
```

#### 4. 健康检查

检查系统健康状态：
```
http://localhost:8080/actuator/health
```

## 示例配置

### 开发环境

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      default-expiration-days: 30
      keys:
        - key-id: "dev-admin"
          key-value: "dev-admin-key-12345"
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "2025-12-31T23:59:59"
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
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "${PROD_ADMIN_KEY_EXPIRES}"
        - key-id: "prod-service"
          key-value: "${PROD_SERVICE_API_KEY}"
          permissions: ["read", "write"]
          expires-at: "${PROD_SERVICE_KEY_EXPIRES}"
    performance:
      cache:
        redis:
          enabled: true
          host: "${REDIS_HOST}"
          port: "${REDIS_PORT}"
          password: "${REDIS_PASSWORD}"
```

## 相关文档

- [JWT 认证配置说明](jwt-authentication.md)
- [数据脱敏规则配置](data-sanitization.md)
- [安全功能故障排除指南](troubleshooting.md)
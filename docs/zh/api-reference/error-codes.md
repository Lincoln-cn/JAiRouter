# 错误码对照表

<!-- 版本信息 -->
> **文档版本**: 1.0.2
> **最后更新**: 2026-06-30
> **适用版本**: v2.7.6+
> **作者**: AI Assistant

JAiRouter 使用标准化的错误码系统来标识各种错误情况。所有错误响应都包含错误码字段，便于客户端进行错误处理。

## 错误响应格式

```json
{
  "success": false,
  "message": "错误描述",
  "errorCode": "ERROR_CODE",
  "data": null
}
```

## HTTP 状态码映射

| HTTP 状态码 | 说明 | 错误类别 |
|-------------|------|----------|
| 400 | 请求参数错误 | 验证错误 |
| 401 | 未认证 | 认证错误 |
| 403 | 权限不足 | 授权错误 |
| 404 | 资源不存在 | 资源错误 |
| 409 | 资源冲突 | 业务错误 |
| 429 | 请求过多 | 限流错误 |
| 500 | 服务器内部错误 | 系统错误 |
| 502 | 网关错误 | 下游服务错误 |
| 503 | 服务不可用 | 下游服务错误 |

---

## 认证错误码 (Authentication)

**HTTP 状态码**: 401 Unauthorized

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INVALID_API_KEY` | 无效的 API Key | API Key 格式错误或不存在 | 检查 API Key 是否正确 |
| `EXPIRED_API_KEY` | API Key 已过期 | API Key 超过有效期 | 重新生成 API Key |
| `MISSING_API_KEY` | 缺少 API Key | 请求头未包含 API Key | 添加 `X-API-Key` 请求头 |
| `INVALID_JWT_TOKEN` | 无效的 JWT 令牌 | JWT 格式错误或签名无效 | 检查 JWT 格式和签名 |
| `EXPIRED_JWT_TOKEN` | JWT 令牌已过期 | JWT 超过有效期 | 刷新或重新获取 JWT |
| `BLACKLISTED_TOKEN` | 令牌已被列入黑名单 | JWT 已被注销 | 重新登录获取新令牌 |
| `AUTH_FAILED` | 认证失败 | 通用认证失败 | 检查认证凭据 |
| `TOKEN_EXPIRED` | 令牌过期 | JWT 令牌过期 | 刷新令牌 |
| `AUTH_ERROR` | 认证错误 | 认证过程异常 | 检查认证服务状态 |

**示例响应**:
```json
{
  "success": false,
  "message": "无效的API Key",
  "errorCode": "INVALID_API_KEY",
  "data": null
}
```

---

## 授权错误码 (Authorization)

**HTTP 状态码**: 403 Forbidden

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INSUFFICIENT_PERMISSIONS` | 权限不足 | 用户没有所需权限 | 联系管理员分配权限 |
| `ACCESS_DENIED` | 访问被拒绝 | 尝试访问无权资源 | 检查资源访问权限 |
| `RESOURCE_FORBIDDEN` | 资源禁止访问 | 资源设置为禁止访问 | 联系资源所有者 |
| `FORBIDDEN` | 禁止访问 | 通用授权失败 | 检查用户角色和权限 |

**示例响应**:
```json
{
  "success": false,
  "message": "权限不足，需要权限: admin",
  "errorCode": "INSUFFICIENT_PERMISSIONS",
  "data": null
}
```

---

## 数据脱敏错误码 (Sanitization)

**HTTP 状态码**: 500 Internal Server Error

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `SANITIZATION_FAILED` | 数据脱敏失败 | 脱敏过程异常 | 检查日志获取详情 |
| `INVALID_SANITIZATION_RULE` | 无效的脱敏规则 | 规则配置错误 | 检查规则配置 |
| `RULE_COMPILATION_FAILED` | 规则编译失败 | 正则表达式语法错误 | 修正正则表达式 |
| `CONTENT_PROCESSING_FAILED` | 内容处理失败 | 内容格式不支持 | 检查内容类型 |

**示例响应**:
```json
{
  "success": false,
  "message": "数据脱敏失败: 内容格式不支持",
  "errorCode": "SANITIZATION_FAILED",
  "data": null
}
```

---

## 资源错误码 (Resource)

**HTTP 状态码**: 404 Not Found

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `NOT_FOUND` | 资源未找到 | 请求的资源不存在 | 检查资源 ID 或路径 |
| `SERVICE_NOT_FOUND` | 服务未找到 | 服务类型不存在 | 检查服务类型名称 |
| `INSTANCE_NOT_FOUND` | 实例未找到 | 实例 ID 不存在 | 检查实例 ID |
| `CONFIG_NOT_FOUND` | 配置未找到 | 配置项不存在 | 检查配置键 |

---

## 验证错误码 (Validation)

**HTTP 状态码**: 400 Bad Request

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `VALIDATION_ERROR` | 请求数据无效 | 参数格式或值错误 | 检查请求参数 |
| `INVALID_PARAMETER` | 参数无效 | 参数值超出范围 | 检查参数约束 |
| `MISSING_PARAMETER` | 缺少参数 | 必填参数未提供 | 添加必填参数 |
| `INVALID_FORMAT` | 格式无效 | 数据格式错误 | 检查数据格式 |

---

## 业务错误码 (Business)

| 错误码 | 描述 | HTTP 状态码 | 解决方案 |
|--------|------|-------------|----------|
| `CONFLICT` | 资源冲突 | 409 | 检查资源状态 |
| `DUPLICATE_RESOURCE` | 资源重复 | 409 | 使用不同标识 |
| `OPERATION_FAILED` | 操作失败 | 500 | 检查日志获取详情 |
| `RATE_LIMIT_EXCEEDED` | 限流触发 | 429 | 降低请求频率 |

---

## 下游服务错误码 (Downstream)

**HTTP 状态码**: 502/503

| 错误码模式 | 描述 | 解决方案 |
|------------|------|----------|
| `5xx` | 下游服务错误 | 检查下游服务状态 |
| `502` | 网关错误 | 检查网络连接 |
| `503` | 服务不可用 | 等待服务恢复 |
| `504` | 网关超时 | 增加超时时间或检查下游响应 |

---

## 限流错误码 (Rate Limit)

**HTTP 状态码**: 429 Too Many Requests

| 错误码 | 描述 | 响应头 | 解决方案 |
|--------|------|--------|----------|
| `RATE_LIMIT_EXCEEDED` | 超过限流阈值 | `X-RateLimit-Reset` | 等待令牌恢复 |
| `GLOBAL_RATE_LIMIT` | 全局限流触发 | `Retry-After` | 降低全局请求频率 |
| `SERVICE_RATE_LIMIT` | 服务级限流触发 | `X-RateLimit-Remaining` | 降低服务请求频率 |
| `INSTANCE_RATE_LIMIT` | 实例级限流触发 | `X-RateLimit-Remaining` | 切换实例或等待 |

---

## 熔断器错误码 (Circuit Breaker)

| 错误码 | 描述 | 状态 | 解决方案 |
|--------|------|------|----------|
| `CIRCUIT_BREAKER_OPEN` | 熔断器开启 | OPEN | 等待熔断器进入半开状态 |
| `CIRCUIT_BREAKER_HALF_OPEN` | 熔断器半开 | HALF_OPEN | 少量请求尝试 |
| `SERVICE_DEGRADED` | 服务降级 | - | 使用降级策略 |

---

## 系统错误码 (System)

**HTTP 状态码**: 500 Internal Server Error

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INTERNAL_ERROR` | 服务器内部错误 | 未预期的异常 | 检查服务日志 |
| `CONFIGURATION_ERROR` | 配置错误 | 配置文件格式错误 | 检查配置文件 |
| `DATABASE_ERROR` | 数据库错误 | 数据库连接失败 | 检查数据库状态 |
| `CACHE_ERROR` | 缓存错误 | Redis 连接失败 | 检查 Redis 状态 |

---

## 错误处理最佳实践

### 1. 客户端错误处理

```javascript
// JavaScript 示例
async function handleApiResponse(response) {
  const data = await response.json();
  
  if (!data.success) {
    switch (data.errorCode) {
      case 'INVALID_API_KEY':
      case 'EXPIRED_API_KEY':
      case 'INVALID_JWT_TOKEN':
      case 'EXPIRED_JWT_TOKEN':
        // 跳转到登录页面
        window.location.href = '/login';
        break;
      case 'RATE_LIMIT_EXCEEDED':
        // 等待后重试
        const resetTime = response.headers.get('X-RateLimit-Reset');
        await sleep(resetTime * 1000);
        return retryRequest();
        break;
      default:
        // 显示错误消息
        showError(data.message);
    }
    return;
  }
  
  return data.data;
}
```

### 2. 服务端错误处理

```java
// Java 示例
@RestControllerAdvice
public class ErrorHandler {
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(ex.getMessage(), ex.getErrorCode()));
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("X-RateLimit-Reset", ex.getResetTime())
            .body(new ErrorResponse("请求过多", "RATE_LIMIT_EXCEEDED"));
    }
}
```

### 3. 错误日志记录

```java
// 记录错误日志
log.error("认证失败: errorCode={}, message={}", 
    ex.getErrorCode(), ex.getMessage());
```

---

## 错误码快速查询

### 按状态码分类

| 状态码 | 错误码列表 |
|--------|-----------|
| 400 | `VALIDATION_ERROR`, `INVALID_PARAMETER`, `MISSING_PARAMETER`, `INVALID_FORMAT` |
| 401 | `INVALID_API_KEY`, `EXPIRED_API_KEY`, `MISSING_API_KEY`, `INVALID_JWT_TOKEN`, `EXPIRED_JWT_TOKEN`, `BLACKLISTED_TOKEN` |
| 403 | `INSUFFICIENT_PERMISSIONS`, `ACCESS_DENIED`, `RESOURCE_FORBIDDEN`, `FORBIDDEN` |
| 404 | `NOT_FOUND`, `SERVICE_NOT_FOUND`, `INSTANCE_NOT_FOUND`, `CONFIG_NOT_FOUND` |
| 409 | `CONFLICT`, `DUPLICATE_RESOURCE` |
| 429 | `RATE_LIMIT_EXCEEDED`, `GLOBAL_RATE_LIMIT`, `SERVICE_RATE_LIMIT`, `INSTANCE_RATE_LIMIT` |
| 500 | `INTERNAL_ERROR`, `SANITIZATION_FAILED`, `CONFIGURATION_ERROR`, `DATABASE_ERROR`, `CACHE_ERROR` |
| 502/503 | 下游服务错误码 |

---

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0.1 | 2026-06-29 | 更新版本引用为 v2.7.5+，修正版本号错误 |
| 1.0.0 | 2026-05-25 | 初始版本，包含所有错误码定义 |

---

*最后更新: 2026-06-29*

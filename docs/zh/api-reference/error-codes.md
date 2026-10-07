# 错误码对照表

<!-- 版本信息 -->
> **文档版本**: 1.0.3
> **最后更新**: 2026-09-15
> **适用版本**: v3.1.1
> **作者**: AI Assistant

JAiRouter 使用标准化的错误码系统来标识各种错误情况，便于客户端进行错误处理。

> ⚠️ **响应形状按入口面不同**：`/api/**`（控制台面）用下面表格里的 `RouterResponse` 形状（`errorCode` 字段）；`/v1/**`（OpenAI 面）用嵌套的 `{"error":{"message","type","code"}}`（见 [统一 API 接口](universal-api.md)）。另有两个已知例外不走本表：管理面限流 429 返回`{"code":"RATE_LIMIT_EXCEEDED","message":...}`（`AdminApiRateLimiter`，**扁平体、没有 `error` 包裹**）；异常处理器兜底返回 `{"code":"500"}`（`ReactiveGlobalExceptionHandler`）。

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
| `JWT_INVALID` | 无效的 JWT 令牌 | JWT 格式错误或签名无效 | 检查 JWT 格式和签名 |
| `JWT_EXPIRED` | JWT 令牌已过期 | JWT 超过有效期 | 刷新或重新获取 JWT |
| `JWT_BLACKLISTED` | 令牌已被列入黑名单 | JWT 已被注销 | 重新登录获取新令牌 |
| `EMPTY_JWT_TOKEN` | 未携带 JWT | 请求头缺少 `Jairouter_Token` | 添加 `Jairouter_Token` 请求头 |
| `EMPTY_API_KEY` | 未携带 API Key | 请求头缺少 `X-API-Key` | 添加 `X-API-Key` 请求头 |
| `API_KEY_DISABLED` | API Key 已被禁用 | 该 Key 被停用 | 启用或更换 Key |
| `API_KEY_AUTH_FAILED` | API Key 认证失败 | Key 与账号不匹配 | 检查 Key 归属 |

> 原表里的 `INVALID_JWT_TOKEN` / `EXPIRED_JWT_TOKEN` / `BLACKLISTED_TOKEN` 在
> `AuthenticationException` 中**只有常量声明**；认证链路实际发出的 401 码是上面这几个新增项
> （`DefaultJwtTokenValidator.java:76,85,120`、`CustomReactiveAuthenticationManager.java:57,67,92,115`）。
| `AUTH_FAILED` | 认证失败 | 通用认证失败 | 检查认证凭据 |
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

---

## 验证错误码 (Validation)

**HTTP 状态码**: 400 Bad Request

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INVALID_REQUEST` | 请求不合法 | 参数格式或值错误 | 检查请求参数 |
| `INVALID_PARAM` | 参数无效 | 参数值超出范围 | 检查参数约束 |

> 原表里的 `VALIDATION_ERROR`（只是 `ValidationFailureType` 的枚举值，从不作为响应码发出，见 `ApiKeyValidator.java:344`）、`INVALID_PARAMETER` / `MISSING_PARAMETER` / `INVALID_FORMAT`（全库无匹配）已删除。

---

## 业务错误码 (Business)

| 错误码 | 描述 | HTTP 状态码 | 解决方案 |
|--------|------|-------------|----------|
| `CONFLICT` | 资源冲突 | 409 | 检查资源状态 |
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
| `RATE_LIMIT_EXCEEDED` | 超过限流阈值 | `Retry-After` / `X-Quota-*` | 等待令牌恢复 |

> 响应头**没有** `X-RateLimit-Reset` / `X-RateLimit-Remaining`（全仓无此头）；配额超限实际写
> `Retry-After` 与 `X-Quota-*`（limit / remaining / window），见 `ServiceRequestHandler.java:692-695`。
> 原表里的 `GLOBAL_RATE_LIMIT` / `SERVICE_RATE_LIMIT` / `INSTANCE_RATE_LIMIT` 全库无匹配（最后一项只是 JPA 表名 `instance_rate_limit`，见 `InstanceRateLimitEntity.java:26`），已删除。

---

## 熔断器错误码 (Circuit Breaker)

**熔断器没有专属错误码。** 熔断状态通过追踪 span 属性（小写的 `circuit_breaker_open`，见
`CircuitBreakerTracingDelegate.java:132`）与监控接口 `GET /api/monitoring/circuit-breaker/stats`
暴露；请求在熔断打开时按下游不可用处理。

> 原表里的 `CIRCUIT_BREAKER_OPEN` / `CIRCUIT_BREAKER_HALF_OPEN` / `SERVICE_DEGRADED`作为**错误码**全库无匹配，已删除。

---

## 系统错误码 (System)

**HTTP 状态码**: 500 Internal Server Error

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INTERNAL_ERROR` | 服务器内部错误 | 未预期的异常 | 检查服务日志 |

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

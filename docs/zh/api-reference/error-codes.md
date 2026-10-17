# 错误码对照表

<!-- 版本信息 -->
> **文档版本**: 1.2.0
> **最后更新**: 2026-10-15
> **适用版本**: v3.3.3+
> **作者**: AI Assistant

JAiRouter 使用标准化的错误码系统来标识各种错误情况，便于客户端进行错误处理。

> ⚠️ **响应形状按入口面不同；而在 `/api/**` 上，还取决于「谁写的这个响应」**：`/api/**`（控制台面）通常用下面表格里的 `RouterResponse` 形状（`errorCode` 字段）；`/v1/**`（OpenAI 面）用嵌套的 `{"error":{"message","type","code"}}`（见 [统一 API 接口](universal-api.md)）；`/v1/messages`（Anthropic 面）用 Anthropic 的 `{"type":"error","error":{...}}` 形状。**不走本表的已知例外**：**（1）认证过滤器在每一个面上都自己写响应体，`/api/**` 也不例外** —— 缺少/非法凭据（401）与凭据在校验环节被限流（429 `RATE_LIMITED_API_KEY`）返回的是 `{"error":{"message":…,"type":"authentication_error"|"rate_limit_error","code":…}}`，因为该过滤器是认证链状态的**唯一渲染点**，这条链上两个 `@RestControllerAdvice` 都到不了；**（2）由授权层拦下的 403**（URL 权限矩阵 / `@PreAuthorize`）是 Spring Security 默认的 `text/plain` 纯文本 `Access Denied`，而 controller 自己抛的 403（例如 `BUILTIN_READONLY`）**是** `RouterResponse` —— 也就是说在 `/api/**` 上形状取决于写入方，而不只取决于面；**（3）**管理面限流 429 返回 `{"code":"RATE_LIMIT_EXCEEDED","message":...}`（扁平体、没有 `error` 包裹）；**（4）**异常处理器兜底返回数字码（如 `{"code":"500"}`）。
>
> 📌 **本表的权威来源是代码**：本表与 `common/constants/ErrorCode.java`（错误码注册表，**63 个会被真正发出的码**）逐条对账，
> 由 `scripts/docs/check-error-codes.py` 在 CI 里**双向校验** —— 文档漏列一个注册表里的码、或文档列了一个发不出来的码，都会判红。
> 每个码的「状态由哪条链决定」也登记在注册表里（渲染路径），因此**改码名/改状态前先改注册表**。

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

> 这一节的码由认证异常承载（注册表里的渲染路径是认证链）：客户端可见状态来自异常携带的 401，
> **不是**由错误码命名规则推导的 —— 例如 `INVALID_API_KEY` 的规则链推导值是 400，但客户端拿到的是 401。
> 认证链上唯一不返回 401 的码是 `RATE_LIMITED_API_KEY`（每分钟限速超限，**429**），它按状态归入下面的「限流错误码」一节。

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INVALID_API_KEY` | 无效的 API Key | Key 不存在、格式错误、来源 IP 不在白名单、或已被禁用 | 核对 Key 与调用来源 |
| `EXPIRED_API_KEY` | API Key 已过期 | Key 超过有效期 | 重新生成 API Key |
| `EMPTY_API_KEY` | 未携带 API Key | 请求头缺少 `X-API-Key` | 添加 `X-API-Key` 请求头 |
| `API_KEY_DISABLED` | API Key 认证功能未启用 | 服务端关闭了 API Key 认证 | 打开服务端开关 |
| `API_KEY_AUTH_FAILED` | API Key 认证失败 | 校验链抛出未预期异常 | 查服务端日志定位根因 |
| `DAILY_QUOTA_EXCEEDED` | 超过每日请求配额 | Key 的每日请求数已用尽 | 等次日重置或提升配额 |
| `TOKEN_QUOTA_EXCEEDED` | 超过每日 Token 配额 | Key 的每日 Token 数已用尽 | 等次日重置或提升配额 |
| `JWT_INVALID` | JWT 令牌校验失败 | 签名不匹配或格式错误 | 重新登录获取新令牌 |
| `JWT_EXPIRED` | JWT 令牌已过期 | 令牌超过有效期 | 刷新或重新登录 |
| `EXPIRED_JWT_TOKEN` | JWT 令牌已过期（按 expired 归类） | 校验链报 expired 时由认证管理器归类 | 重新登录 |
| `JWT_BLACKLISTED` | 令牌已被列入黑名单 | 令牌已注销或被吊销 | 重新登录获取新令牌 |
| `JWT_VALIDATION_ERROR` | JWT 校验过程中发生错误 | 校验时抛出异常 | 查服务端日志 |
| `JWT_REFRESH_EXPIRED` | JWT 令牌超出刷新窗口 | 刷新窗口已过 | 重新登录 |
| `JWT_REFRESH_ERROR` | JWT 刷新过程中发生错误 | 刷新时抛出异常 | 查服务端日志 |
| `JWT_REFRESH_FAILED` | JWT 令牌刷新失败 | 刷新链抛出异常 | 重新登录 |
| `JWT_USER_ID_EXTRACTION_FAILED` | 无法从 JWT 令牌中提取用户 ID | 令牌缺少可解析的用户标识 | 重新登录 |
| `JWT_AUTH_FAILED` | JWT 认证失败 | 认证管理器抛出未预期异常 | 查服务端日志 |
| `JWT_DISABLED` | JWT 认证功能未启用 | 服务端关闭了 JWT 认证 | 打开服务端开关 |
| `TOKEN_REQUIRED` | 刷新时未携带当前令牌 | 刷新请求缺少当前令牌 | 携带当前令牌再刷新 |
| `EMPTY_JWT_TOKEN` | 未携带 JWT 令牌 | 请求头缺少 `Jairouter_Token` | 添加 `Jairouter_Token` 请求头 |
| `AUTH_MISSING` | 请求缺少认证信息 | 既未带 API Key 也未带 JWT | 补齐认证凭据 |
| `AUTH_FAILED` | 认证失败（默认码） | 认证过滤器兜底 | 查服务端日志确认具体原因 |
| `AUTH_ERROR` | 认证过程中发生未知错误 | 认证链抛出未识别异常 | 查服务端日志 |
| `UNSUPPORTED_AUTH_TYPE` | 不支持的认证类型 | 客户端用了本网关不识别的认证方式 | 改用 API Key 或 JWT |
| `LOGIN_FAILED` | 登录失败 | 用户名或密码错误 | 检查登录凭据 |

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
| `FORBIDDEN` | 禁止访问 | 通用授权失败 | 检查角色和权限 |
| `FORBIDDEN_TOKEN_USAGE` | 无权查看该令牌的用量 | 令牌不属于当前调用者 | 用所属账号查询该令牌 |
| `BUILTIN_READONLY` | 内置项只读 | 试图修改/删除内置适配器 | 改为新建自定义适配器 |

> ⚠️ `INSUFFICIENT_PERMISSIONS` / `ACCESS_DENIED` / `FORBIDDEN_RESOURCE` 曾在 `AuthorizationException` 里声明，但
> **全仓没有发出点**（其工厂方法只被单元测试调用）⇒ 已随未接线代码一并删除（#406），本表不列：客户端照它们写分支永远命中不到。
> 真正的 403 由 Spring Security 的授权链与上表的 `FORBIDDEN` 承担。
> `BUILTIN_READONLY` 由 `AdapterConfigController` 的 PUT/DELETE 内置分支**显式写成 403**；按 issue #405
> 的混合方案，同码的规则链推导值也已对齐为 403 ⇒ **两端一致**。

---

## 资源错误码 (Resource)

**HTTP 状态码**: 404 Not Found

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `NOT_FOUND` | 资源未找到 | 请求的资源不存在 | 检查资源 ID 或路径 |
| `TEMPLATE_NOT_FOUND` | 模板不存在 | 模板 ID 不存在 | 检查模板 ID |
| `TOKEN_NOT_FOUND` | 令牌不存在 | 令牌 ID 不存在 | 检查令牌 ID |
| `ADAPTER_NOT_FOUND` | 适配器不存在 | 适配器名称不存在 | 检查适配器名称 |

> `ApiException.resolveStatus` 还会把任何以 `_NOT_FOUND` 结尾的**未登记码**按规则映射为 404；
> 上表是**已登记**的四个码。

---

## 请求校验错误码 (Validation)

**HTTP 状态码**: 400 Bad Request

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INVALID_REQUEST` | 请求不合法 | 参数格式或值错误 | 检查请求参数 |
| `INVALID_PARAM` | 参数无效 | 参数值超出范围 | 检查参数约束 |
| `INVALID_PAGE` | 分页参数不合法 | 页码小于 0 | 传合法页码 |
| `INVALID_SIZE` | 分页大小不合法 | 页大小超出 1-100 | 传合法页大小 |
| `INVALID_TOKEN_ID` | 令牌 ID 不合法 | 令牌 ID 为空或格式错误 | 检查令牌 ID |
| `INVALID_TYPE` | 类型不合法 | 类型取值不在枚举内 | 检查类型取值 |
| `INVALID_NAME` | 名称不合法 | 名称缺失或为空 | 传非空名称 |
| `INVALID_PERMISSION` | 权限项不合法 | 权限码不在权限清单内 | 用已登记的权限码 |
| `INVALID_RECORD_LEVEL` | 记录级别不合法 | 记录级别不在枚举内 | 用 METADATA_ONLY / SUMMARY / FULL |
| `INVALID_ROLE` | 角色名不合法 | 角色名不在已知角色内 | 用已知角色名 |
| `RESTART_REQUIRED` | 需要重启后生效 | 提交了不支持热改的字段 | 移除这些字段，改配置文件后重启 |

> ⚠️ `RESTART_REQUIRED` 的端点实测值与规则链推导值**现在都是 400** —— 按 issue #405 的混合方案，
> 每条链先按自己的语义定状态（本码 = 请求校验语义 400），再把两端对齐：规则链对同码原先推导 403，
> 已改为 400。同一方案把 `BUILTIN_READONLY` 定为授权语义 403，故它已移入「授权错误码」一节。
> 上一版还列过 `VALIDATION_ERROR`（只是 `ValidationFailureType` 的枚举值，从不作为响应码发出）、
> `INVALID_PARAMETER` / `MISSING_PARAMETER` / `INVALID_FORMAT`（全库无匹配）—— 均已从本表移除。

---

## 业务冲突错误码 (Business)

**HTTP 状态码**: 409 Conflict

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `CONFLICT_STATE` | 资源冲突 | 资源当前状态不允许该操作 | 检查资源状态 |
| `CONFLICT_EXISTS` | 资源已存在 | 同名资源已存在 | 换名称或改为更新 |
| `CONFLICT_BUILTIN` | 与内置项冲突 | 试图覆盖内置项 | 换名称 |

> 冲突码的真实形态是 `CONFLICT_STATE`（由异常映射）以及共用前缀的端点级变体
> `CONFLICT_EXISTS` / `CONFLICT_BUILTIN`；**不存在**裸 `CONFLICT`。
> `ApiException.resolveStatus` 把任何 `CONFLICT*` 归 409。

---

## 限流错误码 (Rate Limit)

**HTTP 状态码**: 429 Too Many Requests（管理面与 API Key 校验链**两条路径**）

| 错误码 | 描述 | 响应头 | 解决方案 |
|--------|------|--------|----------|
| `RATE_LIMIT_EXCEEDED` | 管理面限流超限（`/api/auth/api-keys`） | `Retry-After` / `X-Quota-*` | 等待令牌恢复后重试 |
| `RATE_LIMITED_API_KEY` | API Key 每分钟限速超限（校验链） | 无 | 退避后重试 |

> ✅ **拆码已完成**（issue #404，2026-10-15 裁定「429 + 拆新码」）：限流不再「同码两状态」——
> 两个入口面各有一个码，且**状态都是 429**：
> ① 管理面限流器（`AdminApiRateLimiter`）直接写 429 扁平体 `{"code":"RATE_LIMIT_EXCEEDED","message":...}`；
> ② API Key 校验链（`ApiKeyValidator.validateRateLimit` → `FullValidationResult.toException()`）携带
> `RATE_LIMITED_API_KEY`，由认证过滤器渲染为 **429**（`/v1/**` 上的 `type` 按状态映射为 `rate_limit_error`）。
> ⇒ 客户端**不再需要**把 401 当作限流处理，只按状态码 429 分支即可；若仍按码分支，请把两个码都覆盖。
>
> 响应头**没有** `X-RateLimit-Reset` / `X-RateLimit-Remaining`（全仓无此头）；配额超限实际写
> `Retry-After` 与 `X-Quota-*`（limit / remaining / window）。原表里的 `GLOBAL_RATE_LIMIT` /
> `SERVICE_RATE_LIMIT` / `INSTANCE_RATE_LIMIT` 全库无匹配，已删除。

---

## 服务与系统错误码 (System)

**HTTP 状态码**: 500 Internal Server Error

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `INTERNAL_ERROR` | 服务器内部错误 | 未预期的异常 | 检查服务日志 |
| `ADD_FAILED` | 新增失败 | 黑名单等新增操作失败 | 查服务端日志 |
| `BATCH_TOKEN_REVOKE_FAILED` | 批量吊销令牌失败 | 批量吊销过程异常 | 查服务端日志 |
| `BLACKLIST_STATS_FAILED` | 获取黑名单统计失败 | 统计查询异常 | 查服务端日志 |
| `CLEANUP_FAILED` | 清理失败 | 清理操作异常 | 查服务端日志 |
| `GET_CLEANUP_STATS_FAILED` | 获取清理统计失败 | 统计查询异常 | 查服务端日志 |
| `GET_TOKENS_FAILED` | 获取令牌列表失败 | 列表查询异常 | 查服务端日志 |
| `GET_TOKEN_DETAILS_FAILED` | 获取令牌详情失败 | 详情查询异常 | 查服务端日志 |
| `TOKEN_REFRESH_FAILED` | 令牌刷新失败 | 刷新过程异常 | 重新登录 |
| `TOKEN_REVOKE_FAILED` | 令牌吊销失败 | 吊销过程异常 | 查服务端日志 |
| `SANITIZATION_FAILED` | 数据脱敏失败 | 脱敏过程异常 | 检查日志获取详情 |
| `INVALID_SANITIZATION_RULE` | 无效的脱敏规则 | 规则配置错误 | 检查规则配置 |
| `CONTENT_PROCESSING_FAILED` | 内容处理失败 | 内容格式不支持 | 检查内容类型 |

> ⚠️ `RULE_COMPILATION_FAILED` 与上节的三个授权码同类：曾在 `SanitizationException` 里声明、
> **全仓没有发出点**（工厂方法只被单元测试调用）⇒ 已随未接线代码一并删除（#406），本表不列。规则编译失败今天表现为
> `SANITIZATION_FAILED` 或 `INVALID_SANITIZATION_RULE`。
> 上一版列过的 `CONFIGURATION_ERROR` / `DATABASE_ERROR` / `CACHE_ERROR` 同样全库无匹配：配置、数据库、
> 缓存故障都表现为 `INTERNAL_ERROR`（也是解析器对任何不匹配规则的码的兜底值）。

---

## 服务不可用 (Service Unavailable)

**HTTP 状态码**: 503 Service Unavailable

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `SERVICE_NOT_AVAILABLE` | 服务不可用 | 依赖的持久化/清理服务未启用 | 检查服务端配置并等待恢复 |

---

## 下游服务错误码 (Downstream)

**HTTP 状态码**: 原样取下游返回的状态（常见 502/503/504；下游返回 401 时归一到 401 认证分支）

| 错误码 | 描述 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| `DOWNSTREAM_SERVICE_ERROR` | 下游服务异常 | 上游模型服务报错或超时 | 检查下游服务状态与网络 |

> 这个码的 **HTTP 状态不固定**：`SecurityExceptionHandler` 直写错误体时原样取下游返回的状态码
> ⇒ 注册表里它的状态登记为「随上下文」（不是 502/503 之一）。
> 上一版用 `5xx` / `502` / `503` / `504` 当「错误码模式」列出，那既不是码也不是本网关发出的值。

---

## 错误处理最佳实践

### 1. 客户端错误处理

```javascript
// JavaScript 示例
async function handleApiResponse(response) {
  const data = await response.json();

  if (!data.success) {
    const code = data.errorCode ?? data.error?.code;

    switch (code) {
      case 'INVALID_API_KEY':
      case 'EXPIRED_API_KEY':
      case 'EMPTY_API_KEY':
      case 'JWT_EXPIRED':
      case 'JWT_INVALID':
      case 'JWT_BLACKLISTED':
        // 需要重新认证
        window.location.href = '/login';
        break;
      case 'RATE_LIMIT_EXCEEDED':
      case 'RATE_LIMITED_API_KEY':
        // 退避后重试（管理面 429 会带 Retry-After）
        await sleep((Number(response.headers.get('Retry-After')) || 1) * 1000);
        return retryRequest();
      default:
        showError(data.message ?? data.error?.message);
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
        // 状态码取异常携带的值（注册表里的认证链路径）
        return ResponseEntity.status(ex.getHttpStatus())
            .body(new ErrorResponse(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponse(ex.getMessage(), ex.getErrorCode()));
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

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.2.0 | 2026-10-15 | 按 issue #404 的裁决**拆码**：新增 `RATE_LIMITED_API_KEY`（API Key 校验链，429），`RATE_LIMIT_EXCEEDED` 改为管理面限流器直写的 429 扁平体（注册表 62 → 63 个码；限流不再同码两状态） |
| 1.1.0 | 2026-10-15 | 按错误码注册表（`ErrorCode`，62 个会发出的码）重写：补齐 33 个漏列码、删除 7 个发不出的历史码、标注渲染路径与两处「端点实测状态 ≠ 规则链推导值」、登记 `DOWNSTREAM_SERVICE_ERROR` |
| 1.0.3 | 2026-09-15 | 按代码重写两张表（issue #342/#348）：删 17 个不存在的码，补认证链实际发出的码 |
| 1.0.1 | 2026-06-29 | 更新版本引用为 v2.7.5+，修正版本号错误 |
| 1.0.0 | 2026-05-25 | 初始版本，包含所有错误码定义 |

---

*最后更新: 2026-10-15*

# Error Codes Reference

<!-- 版本信息 -->
> **Document Version**: 1.0.2
> **Last Updated**: 2026-06-30
> **Applicable Version**: v2.7.6+
> **Author**: AI Assistant

JAiRouter uses a standardized error code system to identify various error conditions. All error responses include an error code field for client-side error handling.

## Error Response Format

```json
{
  "success": false,
  "message": "Error description",
  "errorCode": "ERROR_CODE",
  "data": null
}
```

## HTTP Status Code Mapping

| HTTP Status | Description | Error Category |
|-------------|-------------|----------------|
| 400 | Bad Request | Validation Error |
| 401 | Unauthorized | Authentication Error |
| 403 | Forbidden | Authorization Error |
| 404 | Not Found | Resource Error |
| 409 | Conflict | Business Error |
| 429 | Too Many Requests | Rate Limit Error |
| 500 | Internal Server Error | System Error |
| 502 | Bad Gateway | Downstream Error |
| 503 | Service Unavailable | Downstream Error |

---

## Authentication Error Codes

**HTTP Status**: 401 Unauthorized

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INVALID_API_KEY` | Invalid API Key | API Key format error or not found | Check API Key correctness |
| `EXPIRED_API_KEY` | API Key expired | API Key exceeded validity period | Regenerate API Key |
| `MISSING_API_KEY` | Missing API Key | Request header missing API Key | Add `X-API-Key` header |
| `JWT_INVALID` | Invalid JWT token | JWT format error or invalid signature | Check JWT format and signature |
| `JWT_EXPIRED` | JWT token expired | JWT exceeded validity period | Refresh or obtain new JWT |
| `JWT_BLACKLISTED` | Token blacklisted | JWT has been revoked | Re-login to obtain new token |
| `EMPTY_JWT_TOKEN` | No JWT carried | Request header missing `Jairouter_Token` | Add the `Jairouter_Token` header |
| `EMPTY_API_KEY` | No API Key carried | Request header missing `X-API-Key` | Add the `X-API-Key` header |
| `API_KEY_DISABLED` | API Key disabled | The key has been deactivated | Enable it or use another key |
| `API_KEY_AUTH_FAILED` | API Key authentication failed | Key does not match the account | Check key ownership |
| `AUTH_FAILED` | Authentication failed | Generic authentication failure | Check authentication credentials |
| `AUTH_ERROR` | Authentication error | Authentication process exception | Check authentication service status |

> The previous table listed `INVALID_JWT_TOKEN` / `EXPIRED_JWT_TOKEN` / `BLACKLISTED_TOKEN` (they exist only
> as **constant declarations** in `AuthenticationException`) and `TOKEN_EXPIRED` (which is a
> `SecurityEventType` enum name, **not** an error code). The codes the authentication chain actually emits are
> the ones in the table above (`DefaultJwtTokenValidator.java:76,85,120`,
> `CustomReactiveAuthenticationManager.java:57,67,92,115`).

**Example Response**:
```json
{
  "success": false,
  "message": "Invalid API Key",
  "errorCode": "INVALID_API_KEY",
  "data": null
}
```

---

## Authorization Error Codes

**HTTP Status**: 403 Forbidden

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INSUFFICIENT_PERMISSIONS` | Insufficient permissions | User lacks required permissions | Contact admin to assign permissions |
| `ACCESS_DENIED` | Access denied | Attempting to access unauthorized resource | Check resource access permissions |
| `FORBIDDEN_RESOURCE` | Resource forbidden | Resource set as forbidden | Contact resource owner |
| `FORBIDDEN` | Forbidden | Generic authorization failure | Check user role and permissions |

**Example Response**:
```json
{
  "success": false,
  "message": "Insufficient permissions, required: admin",
  "errorCode": "INSUFFICIENT_PERMISSIONS",
  "data": null
}
```

---

## Sanitization Error Codes

**HTTP Status**: 500 Internal Server Error

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `SANITIZATION_FAILED` | Data sanitization failed | Sanitization process exception | Check logs for details |
| `INVALID_SANITIZATION_RULE` | Invalid sanitization rule | Rule configuration error | Check rule configuration |
| `RULE_COMPILATION_FAILED` | Rule compilation failed | Regex syntax error | Fix regex pattern |
| `CONTENT_PROCESSING_FAILED` | Content processing failed | Unsupported content format | Check content type |

**Example Response**:
```json
{
  "success": false,
  "message": "Data sanitization failed: unsupported content format",
  "errorCode": "SANITIZATION_FAILED",
  "data": null
}
```

---

## Resource Error Codes

**HTTP Status**: 404 Not Found

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `NOT_FOUND` | Resource not found | Requested resource doesn't exist | Check resource ID or path |

> The previous table also listed `SERVICE_NOT_FOUND` / `INSTANCE_NOT_FOUND` / `CONFIG_NOT_FOUND`;
> **none of the three exists anywhere in the codebase.** A missing resource is reported as `NOT_FOUND`,
> and `ApiException.resolveStatus` additionally maps any code ending in `_NOT_FOUND` to 404 — the suffix
> is handled by rule, not by dedicated codes (`common/exception/ApiException.java:143`).

---

## Validation Error Codes

**HTTP Status**: 400 Bad Request

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INVALID_REQUEST` | Invalid request data | Parameter format or value error | Check request parameters |
| `INVALID_PARAM` | Invalid parameter | Parameter value out of range | Check parameter constraints |

> `VALIDATION_ERROR` exists **only** as the enum constant `ValidationFailureType.VALIDATION_ERROR`
> (`auth/security/service/ApiKeyValidator.java:344`) and is never emitted as a response code;
> `INVALID_PARAMETER` / `MISSING_PARAMETER` / `INVALID_FORMAT` have no match in the codebase at all.
> All four were removed from this table.

---

## Business Error Codes

| Error Code | Description | HTTP Status | Solution |
|------------|-------------|-------------|----------|
| `CONFLICT_STATE` | Resource conflict | 409 | Check resource state |
| `RATE_LIMIT_EXCEEDED` | Rate limit triggered | 429 | Reduce request frequency |

> `DUPLICATE_RESOURCE` and `OPERATION_FAILED` have **no match in the codebase** and were removed.
> Conflict reporting uses `CONFLICT_STATE` (mapped from exceptions in `common/exception/ApiExceptions.java:51`)
> plus per-endpoint variants that share the prefix — `CONFLICT_EXISTS` / `CONFLICT_BUILTIN`
> (`router/controller/AdapterConfigController.java:192·197`); `ApiException.resolveStatus` maps every
> `CONFLICT*` code to 409 (`common/exception/ApiException.java:149`). There is no generic
> "operation failed" code: unexpected failures surface as `INTERNAL_ERROR`.

---

## Downstream Service Error Codes

**HTTP Status**: 502/503

| Error Code Pattern | Description | Solution |
|--------------------|-------------|----------|
| `5xx` | Downstream service error | Check downstream service status |
| `502` | Bad gateway | Check network connection |
| `503` | Service unavailable | Wait for service recovery |
| `504` | Gateway timeout | Increase timeout or check downstream response |

---

## Rate Limit Error Codes

**HTTP Status**: 429 Too Many Requests

| Error Code | Description | Response Header | Solution |
|------------|-------------|-----------------|----------|
| `RATE_LIMIT_EXCEEDED` | Rate limit exceeded | `Retry-After` / `X-Quota-*` | Wait for token recovery |

> The response headers are **`Retry-After`** and the **`X-Quota-*`** family
> (`X-Quota-Limit` / `X-Quota-Remaining` / `X-Quota-Window`,
> `router/handler/ServiceRequestHandler.java:150·155·160`). There is **no** `X-RateLimit-Reset` /
> `X-RateLimit-Remaining` header anywhere in the codebase.
> `GLOBAL_RATE_LIMIT` / `SERVICE_RATE_LIMIT` / `INSTANCE_RATE_LIMIT` were removed from this table:
> the first two have no match in the codebase, and the third is only a JPA table name
> (`@Table(name = "instance_rate_limit")`, `persistence/jpa/entity/InstanceRateLimitEntity.java:26`).

---

## Circuit Breaker Error Codes

**The circuit breaker has no dedicated error codes.** Its state is exposed as a tracing span attribute
(the lowercase value `circuit_breaker_open` on `cb.rejection_reason`,
`monitor/tracing/wrapper/CircuitBreakerTracingDelegate.java:132`) and through the monitoring endpoint
`GET /api/monitoring/circuit-breaker/stats`. A request rejected while the circuit is open is handled as a
downstream-unavailable failure.

> `CIRCUIT_BREAKER_OPEN` / `CIRCUIT_BREAKER_HALF_OPEN` / `SERVICE_DEGRADED` have **no match in the
> codebase as error codes** and were removed from this table.

---

## System Error Codes

**HTTP Status**: 500 Internal Server Error

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INTERNAL_ERROR` | Internal server error | Unexpected exception | Check service logs |

> `CONFIGURATION_ERROR` / `DATABASE_ERROR` / `CACHE_ERROR` have **no match in the codebase** and were
> removed: configuration, database and cache failures all surface as `INTERNAL_ERROR`
> (`common/exception/ApiException.java:129`), which is also what the resolver falls back to when a code
> matches none of its rules (`common/exception/ApiException.java:164`).

---

## Error Handling Best Practices

### 1. Client-Side Error Handling

```javascript
// JavaScript Example
async function handleApiResponse(response) {
  const data = await response.json();
  
  if (!data.success) {
    switch (data.errorCode) {
      case 'INVALID_API_KEY':
      case 'EXPIRED_API_KEY':
      case 'INVALID_JWT_TOKEN':
      case 'EXPIRED_JWT_TOKEN':
        // Redirect to login page
        window.location.href = '/login';
        break;
      case 'RATE_LIMIT_EXCEEDED':
        // Wait and retry
        const resetTime = response.headers.get('X-RateLimit-Reset');
        await sleep(resetTime * 1000);
        return retryRequest();
        break;
      default:
        // Show error message
        showError(data.message);
    }
    return;
  }
  
  return data.data;
}
```

### 2. Server-Side Error Handling

```java
// Java Example
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
            .body(new ErrorResponse("Too many requests", "RATE_LIMIT_EXCEEDED"));
    }
}
```

### 3. Error Logging

```java
// Log error information
log.error("Authentication failed: errorCode={}, message={}", 
    ex.getErrorCode(), ex.getMessage());
```

---

## Quick Reference by Status Code

| Status | Error Codes |
|--------|-------------|
| 400 | `INVALID_REQUEST`, `INVALID_PARAM` (any `INVALID_*` code also resolves here, by rule) |
| 401 | `MISSING_API_KEY`, `INVALID_API_KEY`, `EXPIRED_API_KEY`, `INVALID_JWT_TOKEN`, `EXPIRED_JWT_TOKEN`, `BLACKLISTED_TOKEN`, `DAILY_QUOTA_EXCEEDED`, `TOKEN_QUOTA_EXCEEDED`, `LOGIN_FAILED` |
| 403 | `INSUFFICIENT_PERMISSIONS`, `ACCESS_DENIED`, `FORBIDDEN_RESOURCE`, `BUILTIN_READONLY` |
| 404 | `NOT_FOUND` (any `*_NOT_FOUND` code also resolves here, by rule) |
| 409 | `CONFLICT_STATE`, `CONFLICT_EXISTS`, `CONFLICT_BUILTIN` |
| 429 | `RATE_LIMIT_EXCEEDED` |
| 500 | `INTERNAL_ERROR`, `SANITIZATION_FAILED` — and the fallback for any code matching none of the rules |
| 502/503/504 | Downstream **status patterns**, not error codes; `SERVICE_NOT_AVAILABLE` resolves to 503 |

> Every code in this table was checked to exist as a string literal in `src/main/java`.
> The previous version also listed `VALIDATION_ERROR`, `INVALID_PARAMETER`, `MISSING_PARAMETER`,
> `INVALID_FORMAT`, `SERVICE_NOT_FOUND`, `INSTANCE_NOT_FOUND`, `CONFIG_NOT_FOUND`, `DUPLICATE_RESOURCE`,
> `GLOBAL_RATE_LIMIT`, `SERVICE_RATE_LIMIT`, `INSTANCE_RATE_LIMIT`, `CONFIGURATION_ERROR`, `DATABASE_ERROR`
> and `CACHE_ERROR` — **none of them exists** (see the notes in each section above).
> ⚠️ `TOO_MANY_REQUESTS` and `RATE_LIMITED` are **not** emitted codes either: the first is only the Spring
> `HttpStatus` enum, the second appears only inside the resolver's own rule — they are not in this table.

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.1 | 2026-06-29 | Update version reference to v2.7.5+, fix incorrect version number |
| 1.0.0 | 2026-05-25 | Initial version with all error code definitions |

---

*Last updated: 2026-06-29*

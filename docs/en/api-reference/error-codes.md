# Error Codes Reference

<!-- 版本信息 -->
> **Document Version**: 1.1.0
> **Last Updated**: 2026-10-15
> **Applicable Version**: v3.3.3+
> **Author**: AI Assistant

JAiRouter uses a standardized error code system to identify various error conditions. All error responses include an error code field for client-side error handling.

> ⚠️ **The response shape depends on the entry surface**: `/api/**` (console) uses the `RouterResponse` shape shown below (`errorCode` field); `/v1/**` (OpenAI surface) uses a nested `{"error":{"message","type","code"}}` (see [Universal API](universal-api.md)); `/v1/messages` (Anthropic surface) uses Anthropic's `{"type":"error","error":{...}}` shape. Two known exceptions do not use this table: the admin rate limiter returns a flat `{"code":"RATE_LIMIT_EXCEEDED","message":...}` body with 429, and exception-handler fallbacks return numeric codes (for example `{"code":"500"}`).
>
> 📌 **Code is the authority for this table**: it is reconciled entry by entry against `common/constants/ErrorCode.java` (the error-code registry, **62 codes that are actually emitted**), and `scripts/docs/check-error-codes.py` verifies it **both ways** in CI — a registry code missing from this table, or a table code that can never be emitted, fails the build. Which chain decides each code's HTTP status is registered too (its render path), so **change the registry before changing a code name or status**.

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

> The codes in this section are carried by authentication exceptions (their render path in the registry is the
> authentication chain): the client-visible status comes from the exception's 401, and is **not** derived from the
> code's naming rule — `INVALID_API_KEY`, for example, would resolve to 400 by rule, but clients receive 401.

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INVALID_API_KEY` | Invalid API Key | Key missing, malformed, caller IP not allowlisted, or the key is disabled | Check the key and where it is called from |
| `EXPIRED_API_KEY` | API Key expired | The key exceeded its validity period | Regenerate the API Key |
| `EMPTY_API_KEY` | No API Key carried | The request is missing the `X-API-Key` header | Add the `X-API-Key` header |
| `API_KEY_DISABLED` | API Key authentication is disabled | The server turned API Key auth off | Enable it on the server |
| `API_KEY_AUTH_FAILED` | API Key authentication failed | The validation chain threw an unexpected exception | Check server logs for the root cause |
| `DAILY_QUOTA_EXCEEDED` | Daily request quota exceeded | The key used up its daily requests | Wait for the daily reset or raise the quota |
| `TOKEN_QUOTA_EXCEEDED` | Daily token quota exceeded | The key used up its daily tokens | Wait for the daily reset or raise the quota |
| `RATE_LIMIT_EXCEEDED` | Request rate exceeded | The key exceeded its per-minute limit (see the **dual status** note in "Rate Limit Error Codes") | Back off and retry |
| `JWT_INVALID` | JWT validation failed | Signature mismatch or malformed token | Sign in again to obtain a new token |
| `JWT_EXPIRED` | JWT token expired | The token exceeded its validity period | Refresh it or sign in again |
| `EXPIRED_JWT_TOKEN` | JWT token expired (classified as expired) | The authentication manager classifies expired failures with this code | Sign in again |
| `JWT_BLACKLISTED` | Token is blacklisted | The token was revoked or logged out | Sign in again to obtain a new token |
| `JWT_VALIDATION_ERROR` | Error while validating the JWT | The validation chain threw | Check server logs |
| `JWT_REFRESH_EXPIRED` | JWT is past its refresh window | The refresh window has elapsed | Sign in again |
| `JWT_REFRESH_ERROR` | Error while refreshing the JWT | The refresh chain threw | Check server logs |
| `JWT_REFRESH_FAILED` | JWT refresh failed | The refresh chain threw | Sign in again |
| `JWT_USER_ID_EXTRACTION_FAILED` | Cannot extract a user id from the JWT | The token carries no parseable user identifier | Sign in again |
| `JWT_AUTH_FAILED` | JWT authentication failed | The authentication manager threw an unexpected exception | Check server logs |
| `JWT_DISABLED` | JWT authentication is disabled | The server turned JWT auth off | Enable it on the server |
| `TOKEN_REQUIRED` | The current token is required for refresh | The refresh request carries no current token | Send the current token with the refresh |
| `EMPTY_JWT_TOKEN` | No JWT carried | The request is missing the `Jairouter_Token` header | Add the `Jairouter_Token` header |
| `AUTH_MISSING` | No credentials carried | Neither an API Key nor a JWT was supplied | Supply credentials |
| `AUTH_FAILED` | Authentication failed (default code) | The authentication filter's fallback | Check server logs for the concrete cause |
| `AUTH_ERROR` | Unknown error during authentication | The authentication chain threw an unrecognized exception | Check server logs |
| `UNSUPPORTED_AUTH_TYPE` | Unsupported authentication type | The client used a credential type this gateway does not recognize | Use an API Key or a JWT |
| `LOGIN_FAILED` | Login failed | Wrong username or password | Check the login credentials |

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
| `FORBIDDEN` | Forbidden | Generic authorization failure | Check the role and permissions |
| `FORBIDDEN_TOKEN_USAGE` | Not allowed to view this token's usage | The token does not belong to the caller | Query it with the owning account |

> ⚠️ `INSUFFICIENT_PERMISSIONS` / `ACCESS_DENIED` / `FORBIDDEN_RESOURCE` are declared in `AuthorizationException`
> but have **no emission site anywhere in the repository** (their factory methods are only called by unit tests)
> ⇒ they were removed from this table: a client branching on them would never match. Real 403s come from the
> Spring Security authorization chain and from `FORBIDDEN` above.

---

## Resource Error Codes

**HTTP Status**: 404 Not Found

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `NOT_FOUND` | Resource not found | The requested resource does not exist | Check the resource id or path |
| `TEMPLATE_NOT_FOUND` | Template not found | Unknown template id | Check the template id |
| `TOKEN_NOT_FOUND` | Token not found | Unknown token id | Check the token id |
| `ADAPTER_NOT_FOUND` | Adapter not found | Unknown adapter name | Check the adapter name |

> `ApiException.resolveStatus` also maps any **unregistered** code ending in `_NOT_FOUND` to 404 by rule;
> the table above lists the four **registered** codes.

---

## Validation Error Codes

**HTTP Status**: 400 Bad Request

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INVALID_REQUEST` | Invalid request data | Parameter format or value error | Check the request parameters |
| `INVALID_PARAM` | Invalid parameter | Parameter value out of range | Check the parameter constraints |
| `INVALID_PAGE` | Invalid page parameter | Negative page number | Send a valid page number |
| `INVALID_SIZE` | Invalid page size | Page size outside 1-100 | Send a valid page size |
| `INVALID_TOKEN_ID` | Invalid token id | Empty or malformed token id | Check the token id |
| `INVALID_TYPE` | Invalid type | The type value is not in the enum | Check the type value |
| `INVALID_NAME` | Invalid name | Missing or blank name | Send a non-blank name |
| `INVALID_PERMISSION` | Invalid permission entry | The permission code is not in the permission list | Use a registered permission code |
| `INVALID_RECORD_LEVEL` | Invalid record level | The level is not in the enum | Use METADATA_ONLY / SUMMARY / FULL |
| `INVALID_ROLE` | Invalid role name | The role is not a known role | Use a known role name |
| `BUILTIN_READONLY` | Built-in entry is read-only | Attempting to modify or delete a built-in adapter | Create a custom adapter instead |
| `RESTART_REQUIRED` | A restart is required for these fields | Fields that cannot be hot-reloaded were submitted | Remove them, edit the config file and restart |

> ⚠️ `BUILTIN_READONLY` and `RESTART_REQUIRED` are **returned with 400** by their endpoints
> (`ResponseEntity.badRequest()`), while code-name resolution would derive 403 — the divergence is tracked as a
> pending decision (issue #405) and this table registers the **observed** values.
> The previous version also listed `VALIDATION_ERROR` (only the `ValidationFailureType` enum constant, never emitted
> as a response code) and `INVALID_PARAMETER` / `MISSING_PARAMETER` / `INVALID_FORMAT` (no match in the codebase);
> all four were removed.

---

## Business Error Codes

**HTTP Status**: 409 Conflict

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `CONFLICT_STATE` | Resource conflict | The resource state does not allow this operation | Check the resource state |
| `CONFLICT_EXISTS` | Resource already exists | A resource with the same name exists | Rename it or switch to an update |
| `CONFLICT_BUILTIN` | Conflicts with a built-in entry | Attempting to overwrite a built-in entry | Pick another name |

> Conflicts surface as `CONFLICT_STATE` (mapped from exceptions) plus the per-endpoint variants that share the
> prefix — `CONFLICT_EXISTS` / `CONFLICT_BUILTIN`; there is no bare `CONFLICT`.
> `ApiException.resolveStatus` maps every `CONFLICT*` code to 409.

---

## Rate Limit Error Codes

**HTTP Status**: 429 Too Many Requests (admin surface) · 401 (API Key validation chain)

| Error Code | Description | Response Header | Solution |
|------------|-------------|-----------------|----------|
| `RATE_LIMIT_EXCEEDED` | Rate limit exceeded | `Retry-After` / `X-Quota-*` (admin surface) / none (validation chain) | Wait for the bucket to recover and retry |

> ⚠️ **One code, two statuses on two real paths** (issue #404, pending decision):
> ① the admin rate limiter (`AdminApiRateLimiter`) writes a flat 429 body `{"code":"RATE_LIMIT_EXCEEDED","message":...}`;
> ② the API Key validation chain (`ApiKeyValidator` → `FullValidationResult.toException()`) wraps the same code in
> an `AuthenticationException`, so the client receives **401**. A client branching on status must handle both.
>
> There is **no** `X-RateLimit-Reset` / `X-RateLimit-Remaining` header anywhere in the codebase; quota rejections
> write `Retry-After` and the `X-Quota-*` family (limit / remaining / window).
> `GLOBAL_RATE_LIMIT` / `SERVICE_RATE_LIMIT` / `INSTANCE_RATE_LIMIT` were removed from this table: the first two
> have no match in the codebase, and the third is only a JPA table name.

---

## System Error Codes

**HTTP Status**: 500 Internal Server Error

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INTERNAL_ERROR` | Internal server error | Unexpected exception | Check the service logs |
| `ADD_FAILED` | Add operation failed | Adding a blacklist entry (or similar) failed | Check the service logs |
| `BATCH_TOKEN_REVOKE_FAILED` | Batch token revocation failed | The batch revoke path threw | Check the service logs |
| `BLACKLIST_STATS_FAILED` | Failed to read blacklist statistics | The statistics query threw | Check the service logs |
| `CLEANUP_FAILED` | Cleanup failed | The cleanup operation threw | Check the service logs |
| `GET_CLEANUP_STATS_FAILED` | Failed to read cleanup statistics | The statistics query threw | Check the service logs |
| `GET_TOKENS_FAILED` | Failed to list tokens | The list query threw | Check the service logs |
| `GET_TOKEN_DETAILS_FAILED` | Failed to read token details | The detail query threw | Check the service logs |
| `TOKEN_REFRESH_FAILED` | Token refresh failed | The refresh path threw | Sign in again |
| `TOKEN_REVOKE_FAILED` | Token revocation failed | The revoke path threw | Check the service logs |
| `SANITIZATION_FAILED` | Data sanitization failed | The sanitization process threw | Check the logs for details |
| `INVALID_SANITIZATION_RULE` | Invalid sanitization rule | Bad rule configuration | Check the rule configuration |
| `CONTENT_PROCESSING_FAILED` | Content processing failed | Unsupported content format | Check the content type |

> ⚠️ `RULE_COMPILATION_FAILED` belongs to the same family as the three authorization codes above: it is declared in
> `SanitizationException` and **has no emission site** (its factory method is only called by unit tests), so it was
> removed from this table. A rule compilation failure currently surfaces as `SANITIZATION_FAILED` or
> `INVALID_SANITIZATION_RULE`.
> `CONFIGURATION_ERROR` / `DATABASE_ERROR` / `CACHE_ERROR` from the previous version have no match either:
> configuration, database and cache failures all surface as `INTERNAL_ERROR` (which is also the resolver's fallback
> for any code that matches none of its rules).

---

## Service Unavailable

**HTTP Status**: 503 Service Unavailable

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `SERVICE_NOT_AVAILABLE` | Service unavailable | A required persistence/cleanup service is not enabled | Check the server configuration and retry |

---

## Downstream Service Error Codes

**HTTP Status**: the downstream status verbatim (commonly 502/503/504; a downstream 401 is normalized onto the 401 authentication branch)

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `DOWNSTREAM_SERVICE_ERROR` | Downstream service error | The upstream model service failed or timed out | Check the downstream service and the network |

> This code's **HTTP status is not fixed**: `SecurityExceptionHandler` writes the error body with the downstream
> status verbatim ⇒ the registry records its status as context-dependent (it is not one of 502/503).
> The previous version listed `5xx` / `502` / `503` / `504` as "error code patterns"; those are neither codes nor
> values this gateway emits.

---

## Error Handling Best Practices

### 1. Client-Side Error Handling

```javascript
// JavaScript Example
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
        // Re-authentication required
        window.location.href = '/login';
        break;
      case 'RATE_LIMIT_EXCEEDED':
        // Back off and retry (the admin surface sends Retry-After with its 429)
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

### 2. Server-Side Error Handling

```java
// Java Example
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthenticationException ex) {
        // The status comes from the exception (the authentication render path in the registry)
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

### 3. Error Logging

```java
// Log error information
log.error("Authentication failed: errorCode={}, message={}",
    ex.getErrorCode(), ex.getMessage());
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.1.0 | 2026-10-15 | Rewritten against the error-code registry (`ErrorCode`, 62 emitted codes): added the 33 missing codes, removed 7 codes that can never be emitted, documented each render path and the two "endpoint returns 400 while code-name resolution derives 403" cases, and registered `DOWNSTREAM_SERVICE_ERROR` |
| 1.0.2 | 2026-06-30 | Rewrote both tables from the code (issues #342/#348): dropped 17 non-existent codes, added the codes the authentication chain actually emits |
| 1.0.1 | 2026-06-29 | Update version reference to v2.7.5+, fix incorrect version number |
| 1.0.0 | 2026-05-25 | Initial version with all error code definitions |

---

*Last updated: 2026-10-15*

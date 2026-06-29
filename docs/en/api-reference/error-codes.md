# Error Codes Reference

<!-- Version Info -->
> **Document Version**: 1.0.1
> **Last Updated**: 2026-06-29
> **Applicable Version**: v2.7.5+
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
| `INVALID_JWT_TOKEN` | Invalid JWT token | JWT format error or invalid signature | Check JWT format and signature |
| `EXPIRED_JWT_TOKEN` | JWT token expired | JWT exceeded validity period | Refresh or obtain new JWT |
| `BLACKLISTED_TOKEN` | Token blacklisted | JWT has been revoked | Re-login to obtain new token |
| `AUTH_FAILED` | Authentication failed | Generic authentication failure | Check authentication credentials |
| `TOKEN_EXPIRED` | Token expired | JWT token expired | Refresh token |
| `AUTH_ERROR` | Authentication error | Authentication process exception | Check authentication service status |

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
| `RESOURCE_FORBIDDEN` | Resource forbidden | Resource set as forbidden | Contact resource owner |
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
| `SERVICE_NOT_FOUND` | Service not found | Service type doesn't exist | Check service type name |
| `INSTANCE_NOT_FOUND` | Instance not found | Instance ID doesn't exist | Check instance ID |
| `CONFIG_NOT_FOUND` | Configuration not found | Config item doesn't exist | Check config key |

---

## Validation Error Codes

**HTTP Status**: 400 Bad Request

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `VALIDATION_ERROR` | Invalid request data | Parameter format or value error | Check request parameters |
| `INVALID_PARAMETER` | Invalid parameter | Parameter value out of range | Check parameter constraints |
| `MISSING_PARAMETER` | Missing parameter | Required parameter not provided | Add required parameter |
| `INVALID_FORMAT` | Invalid format | Data format error | Check data format |

---

## Business Error Codes

| Error Code | Description | HTTP Status | Solution |
|------------|-------------|-------------|----------|
| `CONFLICT` | Resource conflict | 409 | Check resource state |
| `DUPLICATE_RESOURCE` | Duplicate resource | 409 | Use different identifier |
| `OPERATION_FAILED` | Operation failed | 500 | Check logs for details |
| `RATE_LIMIT_EXCEEDED` | Rate limit triggered | 429 | Reduce request frequency |

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
| `RATE_LIMIT_EXCEEDED` | Rate limit exceeded | `X-RateLimit-Reset` | Wait for token recovery |
| `GLOBAL_RATE_LIMIT` | Global rate limit triggered | `Retry-After` | Reduce global request frequency |
| `SERVICE_RATE_LIMIT` | Service rate limit triggered | `X-RateLimit-Remaining` | Reduce service request frequency |
| `INSTANCE_RATE_LIMIT` | Instance rate limit triggered | `X-RateLimit-Remaining` | Switch instance or wait |

---

## Circuit Breaker Error Codes

| Error Code | Description | State | Solution |
|------------|-------------|-------|----------|
| `CIRCUIT_BREAKER_OPEN` | Circuit breaker open | OPEN | Wait for half-open state |
| `CIRCUIT_BREAKER_HALF_OPEN` | Circuit breaker half-open | HALF_OPEN | Limited requests allowed |
| `SERVICE_DEGRADED` | Service degraded | - | Use fallback strategy |

---

## System Error Codes

**HTTP Status**: 500 Internal Server Error

| Error Code | Description | Common Cause | Solution |
|------------|-------------|--------------|----------|
| `INTERNAL_ERROR` | Internal server error | Unexpected exception | Check service logs |
| `CONFIGURATION_ERROR` | Configuration error | Config file format error | Check config files |
| `DATABASE_ERROR` | Database error | Database connection failed | Check database status |
| `CACHE_ERROR` | Cache error | Redis connection failed | Check Redis status |

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
| 400 | `VALIDATION_ERROR`, `INVALID_PARAMETER`, `MISSING_PARAMETER`, `INVALID_FORMAT` |
| 401 | `INVALID_API_KEY`, `EXPIRED_API_KEY`, `MISSING_API_KEY`, `INVALID_JWT_TOKEN`, `EXPIRED_JWT_TOKEN`, `BLACKLISTED_TOKEN` |
| 403 | `INSUFFICIENT_PERMISSIONS`, `ACCESS_DENIED`, `RESOURCE_FORBIDDEN`, `FORBIDDEN` |
| 404 | `NOT_FOUND`, `SERVICE_NOT_FOUND`, `INSTANCE_NOT_FOUND`, `CONFIG_NOT_FOUND` |
| 409 | `CONFLICT`, `DUPLICATE_RESOURCE` |
| 429 | `RATE_LIMIT_EXCEEDED`, `GLOBAL_RATE_LIMIT`, `SERVICE_RATE_LIMIT`, `INSTANCE_RATE_LIMIT` |
| 500 | `INTERNAL_ERROR`, `SANITIZATION_FAILED`, `CONFIGURATION_ERROR`, `DATABASE_ERROR`, `CACHE_ERROR` |
| 502/503 | Downstream service errors |

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.1 | 2026-06-29 | Update version reference to v2.7.5+, fix incorrect version number |
| 1.0.0 | 2026-05-25 | Initial version with all error code definitions |

---

*Last updated: 2026-06-29*

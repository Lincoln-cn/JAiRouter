package org.unreal.modelrouter.security.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.dto.SecurityErrorResponse;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.exception.AuthorizationException;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.exception.SecurityException;
import org.unreal.modelrouter.exceptionhandler.SecurityExceptionHandler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityExceptionHandler 单元测试
 */
class SecurityExceptionHandlerTest {

    private SecurityExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new SecurityExceptionHandler();
    }

    @Test
    void testHandleAuthenticationException() {
        // Given
        AuthenticationException exception = AuthenticationException.invalidApiKey();

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleAuthenticationException(exception);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals(401, errorResponse.getStatus());
        assertEquals("Unauthorized", errorResponse.getError());
        assertEquals("无效的API Key", errorResponse.getMessage());
        assertEquals(AuthenticationException.INVALID_API_KEY, errorResponse.getErrorCode());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testHandleAuthorizationException() {
        // Given
        AuthorizationException exception = AuthorizationException.insufficientPermissions("admin");

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleAuthorizationException(exception);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals(403, errorResponse.getStatus());
        assertEquals("Forbidden", errorResponse.getError());
        assertEquals("权限不足，需要权限: admin", errorResponse.getMessage());
        assertEquals(AuthorizationException.INSUFFICIENT_PERMISSIONS, errorResponse.getErrorCode());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testHandleSanitizationException() {
        // Given
        SanitizationException exception = SanitizationException.sanitizationFailed("规则编译失败");

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleSanitizationException(exception);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals(500, errorResponse.getStatus());
        assertEquals("Internal Server Error", errorResponse.getError());
        assertEquals("数据处理失败", errorResponse.getMessage()); // 不暴露具体错误信息
        assertEquals(SanitizationException.SANITIZATION_FAILED, errorResponse.getErrorCode());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testHandleGenericSecurityException() {
        // Given
        SecurityException exception = new SecurityException("通用安全异常", "GENERIC_ERROR", HttpStatus.BAD_REQUEST);

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleSecurityException(exception);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals(400, errorResponse.getStatus());
        assertEquals("Bad Request", errorResponse.getError());
        assertEquals("通用安全异常", errorResponse.getMessage());
        assertEquals("GENERIC_ERROR", errorResponse.getErrorCode());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testHandleExpiredApiKeyException() {
        // Given
        AuthenticationException exception = AuthenticationException.expiredApiKey();

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleAuthenticationException(exception);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals("API Key已过期", errorResponse.getMessage());
        assertEquals(AuthenticationException.EXPIRED_API_KEY, errorResponse.getErrorCode());
    }

    @Test
    void testHandleAccessDeniedException() {
        // Given
        AuthorizationException exception = AuthorizationException.accessDenied("/api/admin");

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleAuthorizationException(exception);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals("访问被拒绝，资源: /api/admin", errorResponse.getMessage());
        assertEquals(AuthorizationException.ACCESS_DENIED, errorResponse.getErrorCode());
    }

    @Test
    void testHandleInvalidRuleException() {
        // Given
        SanitizationException exception = SanitizationException.invalidRule("RULE_001", "无效的正则表达式");

        // When
        ResponseEntity<SecurityErrorResponse> response = exceptionHandler.handleSanitizationException(exception);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SecurityErrorResponse errorResponse = response.getBody();
        assertEquals("数据处理失败", errorResponse.getMessage()); // 不暴露具体错误信息
        assertEquals(SanitizationException.INVALID_SANITIZATION_RULE, errorResponse.getErrorCode());
    }
}
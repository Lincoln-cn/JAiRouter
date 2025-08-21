package org.unreal.modelrouter.security.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.exception.exception.SecurityException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthenticationException 单元测试
 */
class AuthenticationExceptionTest {

    @Test
    void testAuthenticationExceptionWithMessage() {
        // Given
        String message = "认证失败";
        String errorCode = "AUTH_FAILED";

        // When
        AuthenticationException exception = new AuthenticationException(message, errorCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertNull(exception.getCause());
    }

    @Test
    void testAuthenticationExceptionWithCause() {
        // Given
        String message = "认证失败";
        String errorCode = "AUTH_FAILED";
        Throwable cause = new RuntimeException("底层异常");

        // When
        AuthenticationException exception = new AuthenticationException(message, cause, errorCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testInvalidApiKeyException() {
        // When
        AuthenticationException exception = AuthenticationException.invalidApiKey();

        // Then
        assertEquals("无效的API Key", exception.getMessage());
        assertEquals(AuthenticationException.INVALID_API_KEY, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
    }

    @Test
    void testExpiredApiKeyException() {
        // When
        AuthenticationException exception = AuthenticationException.expiredApiKey();

        // Then
        assertEquals("API Key已过期", exception.getMessage());
        assertEquals(AuthenticationException.EXPIRED_API_KEY, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
    }

    @Test
    void testMissingApiKeyException() {
        // When
        AuthenticationException exception = AuthenticationException.missingApiKey();

        // Then
        assertEquals("缺少API Key", exception.getMessage());
        assertEquals(AuthenticationException.MISSING_API_KEY, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
    }

    @Test
    void testInvalidJwtTokenException() {
        // When
        AuthenticationException exception = AuthenticationException.invalidJwtToken();

        // Then
        assertEquals("无效的JWT令牌", exception.getMessage());
        assertEquals(AuthenticationException.INVALID_JWT_TOKEN, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
    }

    @Test
    void testExpiredJwtTokenException() {
        // When
        AuthenticationException exception = AuthenticationException.expiredJwtToken();

        // Then
        assertEquals("JWT令牌已过期", exception.getMessage());
        assertEquals(AuthenticationException.EXPIRED_JWT_TOKEN, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
    }

    @Test
    void testBlacklistedTokenException() {
        // When
        AuthenticationException exception = AuthenticationException.blacklistedToken();

        // Then
        assertEquals("令牌已被列入黑名单", exception.getMessage());
        assertEquals(AuthenticationException.BLACKLISTED_TOKEN, exception.getErrorCode());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
    }

    @Test
    void testErrorCodeConstants() {
        // Then
        assertEquals("INVALID_API_KEY", AuthenticationException.INVALID_API_KEY);
        assertEquals("EXPIRED_API_KEY", AuthenticationException.EXPIRED_API_KEY);
        assertEquals("MISSING_API_KEY", AuthenticationException.MISSING_API_KEY);
        assertEquals("INVALID_JWT_TOKEN", AuthenticationException.INVALID_JWT_TOKEN);
        assertEquals("EXPIRED_JWT_TOKEN", AuthenticationException.EXPIRED_JWT_TOKEN);
        assertEquals("BLACKLISTED_TOKEN", AuthenticationException.BLACKLISTED_TOKEN);
    }

    @Test
    void testInheritanceFromSecurityException() {
        // Given
        AuthenticationException exception = AuthenticationException.invalidApiKey();

        // Then
        assertTrue(exception instanceof SecurityException);
        assertTrue(exception instanceof RuntimeException);
    }
}
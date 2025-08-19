package org.unreal.moduler.security.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.exception.exception.AuthorizationException;
import org.unreal.modelrouter.exception.exception.SecurityException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthorizationException 单元测试
 */
class AuthorizationExceptionTest {

    @Test
    void testAuthorizationExceptionWithMessage() {
        // Given
        String message = "授权失败";
        String errorCode = "AUTH_FAILED";

        // When
        AuthorizationException exception = new AuthorizationException(message, errorCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        assertNull(exception.getCause());
    }

    @Test
    void testAuthorizationExceptionWithCause() {
        // Given
        String message = "授权失败";
        String errorCode = "AUTH_FAILED";
        Throwable cause = new RuntimeException("底层异常");

        // When
        AuthorizationException exception = new AuthorizationException(message, cause, errorCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testInsufficientPermissionsException() {
        // Given
        String requiredPermission = "admin";

        // When
        AuthorizationException exception = AuthorizationException.insufficientPermissions(requiredPermission);

        // Then
        assertEquals("权限不足，需要权限: admin", exception.getMessage());
        assertEquals(AuthorizationException.INSUFFICIENT_PERMISSIONS, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void testAccessDeniedException() {
        // Given
        String resource = "/api/admin/users";

        // When
        AuthorizationException exception = AuthorizationException.accessDenied(resource);

        // Then
        assertEquals("访问被拒绝，资源: /api/admin/users", exception.getMessage());
        assertEquals(AuthorizationException.ACCESS_DENIED, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void testResourceForbiddenException() {
        // Given
        String resource = "/api/sensitive-data";

        // When
        AuthorizationException exception = AuthorizationException.resourceForbidden(resource);

        // Then
        assertEquals("禁止访问资源: /api/sensitive-data", exception.getMessage());
        assertEquals(AuthorizationException.RESOURCE_FORBIDDEN, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void testErrorCodeConstants() {
        // Then
        assertEquals("INSUFFICIENT_PERMISSIONS", AuthorizationException.INSUFFICIENT_PERMISSIONS);
        assertEquals("ACCESS_DENIED", AuthorizationException.ACCESS_DENIED);
        assertEquals("RESOURCE_FORBIDDEN", AuthorizationException.RESOURCE_FORBIDDEN);
    }

    @Test
    void testInheritanceFromSecurityException() {
        // Given
        AuthorizationException exception = AuthorizationException.accessDenied("test");

        // Then
        assertTrue(exception instanceof SecurityException);
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testHttpStatusIsForbidden() {
        // Given
        AuthorizationException exception1 = AuthorizationException.insufficientPermissions("read");
        AuthorizationException exception2 = AuthorizationException.accessDenied("resource");
        AuthorizationException exception3 = AuthorizationException.resourceForbidden("resource");

        // Then
        assertEquals(HttpStatus.FORBIDDEN, exception1.getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN, exception2.getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN, exception3.getHttpStatus());
    }
}
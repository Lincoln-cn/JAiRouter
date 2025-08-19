package org.unreal.moduler.security.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.exception.exception.SecurityException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityException 单元测试
 */
class SecurityExceptionTest {

    @Test
    void testSecurityExceptionWithMessage() {
        // Given
        String message = "安全异常测试消息";
        String errorCode = "TEST_ERROR";
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

        // When
        SecurityException exception = new SecurityException(message, errorCode, httpStatus);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(httpStatus, exception.getHttpStatus());
        assertNull(exception.getCause());
    }

    @Test
    void testSecurityExceptionWithCause() {
        // Given
        String message = "安全异常测试消息";
        String errorCode = "TEST_ERROR";
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        Throwable cause = new RuntimeException("原始异常");

        // When
        SecurityException exception = new SecurityException(message, cause, errorCode, httpStatus);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(httpStatus, exception.getHttpStatus());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testSecurityExceptionInheritance() {
        // Given
        SecurityException exception = new SecurityException("测试", "CODE", HttpStatus.OK);

        // Then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testSecurityExceptionFields() {
        // Given
        String errorCode = "SECURITY_001";
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED;

        // When
        SecurityException exception = new SecurityException("测试消息", errorCode, httpStatus);

        // Then
        assertNotNull(exception.getErrorCode());
        assertNotNull(exception.getHttpStatus());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(httpStatus, exception.getHttpStatus());
    }
}
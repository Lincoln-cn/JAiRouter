package org.unreal.moduler.security.dto;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.security.dto.SecurityErrorResponse;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityErrorResponse 单元测试
 */
class SecurityErrorResponseTest {

    @Test
    void testSecurityErrorResponseBuilder() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        int status = 401;
        String error = "Unauthorized";
        String message = "认证失败";
        String errorCode = "AUTH_FAILED";
        String path = "/api/test";
        String requestId = "req-123";

        // When
        SecurityErrorResponse response = SecurityErrorResponse.builder()
                .timestamp(timestamp)
                .status(status)
                .error(error)
                .message(message)
                .errorCode(errorCode)
                .path(path)
                .requestId(requestId)
                .build();

        // Then
        assertEquals(timestamp, response.getTimestamp());
        assertEquals(status, response.getStatus());
        assertEquals(error, response.getError());
        assertEquals(message, response.getMessage());
        assertEquals(errorCode, response.getErrorCode());
        assertEquals(path, response.getPath());
        assertEquals(requestId, response.getRequestId());
    }

    @Test
    void testAuthenticationErrorFactory() {
        // Given
        String message = "无效的API Key";
        String errorCode = "INVALID_API_KEY";

        // When
        SecurityErrorResponse response = SecurityErrorResponse.authenticationError(message, errorCode);

        // Then
        assertNotNull(response.getTimestamp());
        assertEquals(401, response.getStatus());
        assertEquals("Unauthorized", response.getError());
        assertEquals(message, response.getMessage());
        assertEquals(errorCode, response.getErrorCode());
        assertNull(response.getPath());
        assertNull(response.getRequestId());
    }

    @Test
    void testAuthorizationErrorFactory() {
        // Given
        String message = "权限不足";
        String errorCode = "INSUFFICIENT_PERMISSIONS";

        // When
        SecurityErrorResponse response = SecurityErrorResponse.authorizationError(message, errorCode);

        // Then
        assertNotNull(response.getTimestamp());
        assertEquals(403, response.getStatus());
        assertEquals("Forbidden", response.getError());
        assertEquals(message, response.getMessage());
        assertEquals(errorCode, response.getErrorCode());
        assertNull(response.getPath());
        assertNull(response.getRequestId());
    }

    @Test
    void testSanitizationErrorFactory() {
        // Given
        String message = "数据脱敏失败";
        String errorCode = "SANITIZATION_FAILED";

        // When
        SecurityErrorResponse response = SecurityErrorResponse.sanitizationError(message, errorCode);

        // Then
        assertNotNull(response.getTimestamp());
        assertEquals(500, response.getStatus());
        assertEquals("Internal Server Error", response.getError());
        assertEquals(message, response.getMessage());
        assertEquals(errorCode, response.getErrorCode());
        assertNull(response.getPath());
        assertNull(response.getRequestId());
    }

    @Test
    void testNoArgsConstructor() {
        // When
        SecurityErrorResponse response = new SecurityErrorResponse();

        // Then
        assertNull(response.getTimestamp());
        assertEquals(0, response.getStatus());
        assertNull(response.getError());
        assertNull(response.getMessage());
        assertNull(response.getErrorCode());
        assertNull(response.getPath());
        assertNull(response.getRequestId());
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        int status = 500;
        String error = "Internal Server Error";
        String message = "系统错误";
        String errorCode = "SYSTEM_ERROR";
        String path = "/api/system";
        String requestId = "req-456";

        // When
        SecurityErrorResponse response = new SecurityErrorResponse(
                timestamp, status, error, message, errorCode, path, requestId
        );

        // Then
        assertEquals(timestamp, response.getTimestamp());
        assertEquals(status, response.getStatus());
        assertEquals(error, response.getError());
        assertEquals(message, response.getMessage());
        assertEquals(errorCode, response.getErrorCode());
        assertEquals(path, response.getPath());
        assertEquals(requestId, response.getRequestId());
    }

    @Test
    void testSettersAndGetters() {
        // Given
        SecurityErrorResponse response = new SecurityErrorResponse();
        LocalDateTime timestamp = LocalDateTime.now();
        int status = 400;
        String error = "Bad Request";
        String message = "请求参数错误";
        String errorCode = "INVALID_PARAMETER";
        String path = "/api/validate";
        String requestId = "req-789";

        // When
        response.setTimestamp(timestamp);
        response.setStatus(status);
        response.setError(error);
        response.setMessage(message);
        response.setErrorCode(errorCode);
        response.setPath(path);
        response.setRequestId(requestId);

        // Then
        assertEquals(timestamp, response.getTimestamp());
        assertEquals(status, response.getStatus());
        assertEquals(error, response.getError());
        assertEquals(message, response.getMessage());
        assertEquals(errorCode, response.getErrorCode());
        assertEquals(path, response.getPath());
        assertEquals(requestId, response.getRequestId());
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        LocalDateTime timestamp = LocalDateTime.now();
        SecurityErrorResponse response1 = SecurityErrorResponse.builder()
                .timestamp(timestamp)
                .status(401)
                .error("Unauthorized")
                .message("认证失败")
                .errorCode("AUTH_FAILED")
                .path("/api/test")
                .build();

        SecurityErrorResponse response2 = SecurityErrorResponse.builder()
                .timestamp(timestamp)
                .status(401)
                .error("Unauthorized")
                .message("认证失败")
                .errorCode("AUTH_FAILED")
                .path("/api/test")
                .build();

        // Then
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testToString() {
        // Given
        SecurityErrorResponse response = SecurityErrorResponse.builder()
                .status(401)
                .error("Unauthorized")
                .message("认证失败")
                .errorCode("AUTH_FAILED")
                .build();

        // When
        String toString = response.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("401"));
        assertTrue(toString.contains("Unauthorized"));
        assertTrue(toString.contains("认证失败"));
        assertTrue(toString.contains("AUTH_FAILED"));
    }
}
package org.unreal.moduler.security.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.exception.exception.SanitizationException;
import org.unreal.modelrouter.exception.exception.SecurityException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SanitizationException 单元测试
 */
class SanitizationExceptionTest {

    @Test
    void testSanitizationExceptionWithMessage() {
        // Given
        String message = "脱敏失败";
        String errorCode = "SANITIZATION_FAILED";

        // When
        SanitizationException exception = new SanitizationException(message, errorCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        assertNull(exception.getCause());
    }

    @Test
    void testSanitizationExceptionWithCause() {
        // Given
        String message = "脱敏失败";
        String errorCode = "SANITIZATION_FAILED";
        Throwable cause = new RuntimeException("底层异常");

        // When
        SanitizationException exception = new SanitizationException(message, cause, errorCode);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testSanitizationFailedException() {
        // Given
        String reason = "正则表达式编译失败";

        // When
        SanitizationException exception = SanitizationException.sanitizationFailed(reason);

        // Then
        assertEquals("数据脱敏失败: 正则表达式编译失败", exception.getMessage());
        assertEquals(SanitizationException.SANITIZATION_FAILED, exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
    }

    @Test
    void testInvalidRuleException() {
        // Given
        String ruleId = "RULE_001";
        String reason = "无效的正则表达式";

        // When
        SanitizationException exception = SanitizationException.invalidRule(ruleId, reason);

        // Then
        assertEquals("无效的脱敏规则 [RULE_001]: 无效的正则表达式", exception.getMessage());
        assertEquals(SanitizationException.INVALID_SANITIZATION_RULE, exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
    }

    @Test
    void testRuleCompilationFailedException() {
        // Given
        String ruleId = "RULE_002";
        Throwable cause = new RuntimeException("编译错误");

        // When
        SanitizationException exception = SanitizationException.ruleCompilationFailed(ruleId, cause);

        // Then
        assertEquals("脱敏规则编译失败 [RULE_002]", exception.getMessage());
        assertEquals(SanitizationException.RULE_COMPILATION_FAILED, exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testContentProcessingFailedException() {
        // Given
        String contentType = "application/json";
        Throwable cause = new RuntimeException("JSON解析错误");

        // When
        SanitizationException exception = SanitizationException.contentProcessingFailed(contentType, cause);

        // Then
        assertEquals("内容处理失败，类型: application/json", exception.getMessage());
        assertEquals(SanitizationException.CONTENT_PROCESSING_FAILED, exception.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testErrorCodeConstants() {
        // Then
        assertEquals("SANITIZATION_FAILED", SanitizationException.SANITIZATION_FAILED);
        assertEquals("INVALID_SANITIZATION_RULE", SanitizationException.INVALID_SANITIZATION_RULE);
        assertEquals("RULE_COMPILATION_FAILED", SanitizationException.RULE_COMPILATION_FAILED);
        assertEquals("CONTENT_PROCESSING_FAILED", SanitizationException.CONTENT_PROCESSING_FAILED);
    }

    @Test
    void testInheritanceFromSecurityException() {
        // Given
        SanitizationException exception = SanitizationException.sanitizationFailed("test");

        // Then
        assertTrue(exception instanceof SecurityException);
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testHttpStatusIsInternalServerError() {
        // Given
        SanitizationException exception1 = SanitizationException.sanitizationFailed("test");
        SanitizationException exception2 = SanitizationException.invalidRule("rule", "reason");
        SanitizationException exception3 = SanitizationException.ruleCompilationFailed("rule", new RuntimeException());
        SanitizationException exception4 = SanitizationException.contentProcessingFailed("json", new RuntimeException());

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception1.getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception2.getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception3.getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception4.getHttpStatus());
    }
}
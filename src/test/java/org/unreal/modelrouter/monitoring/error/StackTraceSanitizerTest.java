package org.unreal.modelrouter.monitoring.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.ErrorTrackerProperties;

/**
 * StackTraceSanitizer 单元测试
 */
class StackTraceSanitizerTest {

    private StackTraceSanitizer sanitizer;
    private ErrorTrackerProperties.SanitizationConfig config;

    @BeforeEach
    void setUp() {
        config = new ErrorTrackerProperties.SanitizationConfig();
        sanitizer = new StackTraceSanitizer(config);
    }

    @Test
    void testSanitizeSimpleException() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");

        // When
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // Then
        assertNotNull(sanitized);
        assertEquals("java.lang.RuntimeException", sanitized.getClassName());
        assertEquals("Test exception", sanitized.getMessage());
    }

    @Test
    void testSanitizeExceptionWithSensitiveMessage() {
        // Given
        RuntimeException exception = new RuntimeException("Authentication failed: password=secret123 token=abc123");

        // When
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // Then
        assertNotNull(sanitized);
        assertEquals("java.lang.RuntimeException", sanitized.getClassName());
        assertTrue(sanitized.getMessage().contains("***"));
        assertFalse(sanitized.getMessage().contains("secret123"));
        assertFalse(sanitized.getMessage().contains("abc123"));
    }

    @Test
    void testSanitizeStackTraceWithSensitivePackages() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        // 创建一个包含敏感包的堆栈跟踪
        StackTraceElement[] stackTrace = {
            new StackTraceElement("org.unreal.modelrouter.security.AuthService", "authenticate", "AuthService.java", 123),
            new StackTraceElement("org.unreal.modelrouter.controller.TestController", "test", "TestController.java", 456),
            new StackTraceElement("sun.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 62)
        };
        exception.setStackTrace(stackTrace);

        // When
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // Then
        assertNotNull(sanitized);
        assertNotNull(sanitized.getStackTrace());
        
        // 第一个元素应该被脱敏（敏感包）
        assertEquals("***SANITIZED***", sanitized.getStackTrace()[0].getClassName());
        
        // 第二个元素应该保持原样（不敏感包）
        assertEquals("org.unreal.modelrouter.controller.TestController", sanitized.getStackTrace()[1].getClassName());
        
        // 第三个元素应该被排除（排除包）
        assertEquals(2, sanitized.getStackTrace().length);
    }

    @Test
    void testSanitizeWithMaxStackDepth() {
        // Given
        config.setMaxStackDepth(2);
        sanitizer = new StackTraceSanitizer(config);
        
        RuntimeException exception = new RuntimeException("Test exception");
        StackTraceElement[] stackTrace = new StackTraceElement[5];
        for (int i = 0; i < 5; i++) {
            stackTrace[i] = new StackTraceElement("TestClass" + i, "method" + i, "Test.java", i);
        }
        exception.setStackTrace(stackTrace);

        // When
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // Then
        assertNotNull(sanitized);
        assertEquals(3, sanitized.getStackTrace().length); // 2个元素 + 1个截断标记
        assertEquals("...", sanitized.getStackTrace()[2].getClassName());
    }

    @Test
    void testSanitizeWithCause() {
        // Given
        RuntimeException cause = new RuntimeException("Cause exception with password=secret");
        RuntimeException exception = new RuntimeException("Main exception", cause);

        // When
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // Then
        assertNotNull(sanitized);
        assertNotNull(sanitized.getCause());
        assertEquals("java.lang.RuntimeException", sanitized.getCause().getClassName());
        assertTrue(sanitized.getCause().getMessage().contains("***"));
    }

    @Test
    void testSanitizeDisabled() {
        // Given
        config.setEnabled(false);
        sanitizer = new StackTraceSanitizer(config);
        
        RuntimeException exception = new RuntimeException("Test with password=secret");

        // When
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // Then
        assertNotNull(sanitized);
        assertEquals("Test with password=secret", sanitized.getMessage()); // 未脱敏
    }

    @Test
    void testToSimpleString() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // When
        String simpleString = sanitized.toSimpleString();

        // Then
        assertEquals("java.lang.RuntimeException: Test exception", simpleString);
    }

    @Test
    void testToStringWithStackTrace() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        StackTraceElement[] stackTrace = {
            new StackTraceElement("TestClass", "testMethod", "Test.java", 123)
        };
        exception.setStackTrace(stackTrace);

        StackTraceSanitizer.SanitizedThrowable sanitized = sanitizer.sanitize(exception);

        // When
        String fullString = sanitized.toString();

        // Then
        assertTrue(fullString.contains("java.lang.RuntimeException: Test exception"));
        assertTrue(fullString.contains("at TestClass.testMethod(Test.java:123)"));
    }
}
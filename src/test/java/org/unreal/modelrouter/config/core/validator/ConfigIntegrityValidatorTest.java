package org.unreal.modelrouter.config.core.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigIntegrityValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("ConfigIntegrityValidator 测试")
class ConfigIntegrityValidatorTest {

    private ConfigIntegrityValidator validator;

    @BeforeEach
    void setUp() {
        // 使用反射创建实例并设置字段值
        validator = new ConfigIntegrityValidator();
        setField(validator, "serverPort", 8080);
        setField(validator, "loadBalanceType", "round-robin");
        setField(validator, "rateLimitEnabled", true);
        setField(validator, "rateLimitAlgorithm", "token-bucket");
        setField(validator, "circuitBreakerEnabled", true);
        setField(validator, "failureThreshold", 5);
        setField(validator, "circuitBreakerTimeout", 60000L);
        setField(validator, "storeType", "jpa");
        setField(validator, "monitoringEnabled", true);
        setField(validator, "tracingEnabled", true);
        setField(validator, "validationEnabled", true);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    @Nested
    @DisplayName("服务器配置验证测试")
    class ServerConfigTests {

        @Test
        @DisplayName("VAL-201: 服务器配置 - 有效端口")
        void testValidPort() {
            setField(validator, "serverPort", 8080);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-202: 服务器配置 - 特权端口产生警告")
        void testPrivilegedPort() {
            setField(validator, "serverPort", 80);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-203: 服务器配置 - 无效端口抛异常")
        void testInvalidPort() {
            setField(validator, "serverPort", 0);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-204: 服务器配置 - 端口超出范围抛异常")
        void testPortOutOfRange() {
            setField(validator, "serverPort", 70000);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }
    }

    @Nested
    @DisplayName("负载均衡配置验证测试")
    class LoadBalanceConfigTests {

        @Test
        @DisplayName("VAL-205: 负载均衡配置 - 有效类型round-robin")
        void testValidLoadBalanceType() {
            setField(validator, "loadBalanceType", "round-robin");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-206: 负载均衡配置 - 有效类型weighted")
        void testValidWeightedType() {
            setField(validator, "loadBalanceType", "weighted");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-207: 负载均衡配置 - 无效类型抛异常")
        void testInvalidLoadBalanceType() {
            setField(validator, "loadBalanceType", "invalid");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }
    }

    @Nested
    @DisplayName("限流配置验证测试")
    class RateLimitConfigTests {

        @Test
        @DisplayName("VAL-208: 限流配置 - 有效算法token-bucket")
        void testValidRateLimitAlgorithm() {
            setField(validator, "rateLimitAlgorithm", "token-bucket");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-209: 限流配置 - 有效算法sliding-window")
        void testValidSlidingWindow() {
            setField(validator, "rateLimitAlgorithm", "sliding-window");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-210: 限流配置 - 无效算法抛异常")
        void testInvalidRateLimitAlgorithm() {
            setField(validator, "rateLimitAlgorithm", "invalid");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-211: 限流配置 - 未启用则不验证")
        void testRateLimitDisabled() {
            setField(validator, "rateLimitEnabled", false);
            setField(validator, "rateLimitAlgorithm", "invalid");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }
    }

    @Nested
    @DisplayName("熔断器配置验证测试")
    class CircuitBreakerConfigTests {

        @Test
        @DisplayName("VAL-212: 熔断器配置 - 有效配置")
        void testValidCircuitBreakerConfig() {
            setField(validator, "failureThreshold", 5);
            setField(validator, "circuitBreakerTimeout", 60000L);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-213: 熔断器配置 - 未启用则不验证")
        void testCircuitBreakerDisabled() {
            setField(validator, "circuitBreakerEnabled", false);
            setField(validator, "failureThreshold", 0);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-214: 熔断器配置 - 阈值为0抛异常")
        void testZeroFailureThreshold() {
            setField(validator, "failureThreshold", 0);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-215: 熔断器配置 - 超时为0抛异常")
        void testZeroTimeout() {
            setField(validator, "circuitBreakerTimeout", 0L);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-216: 熔断器配置 - 阈值过高产生警告")
        void testHighFailureThreshold() {
            setField(validator, "failureThreshold", 200);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }
    }

    @Nested
    @DisplayName("存储配置验证测试")
    class StorageConfigTests {

        @Test
        @DisplayName("VAL-217: 存储配置 - 有效类型jpa")
        void testValidStoreTypeJpa() {
            setField(validator, "storeType", "jpa");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-218: 存储配置 - 有效类型redis")
        void testValidStoreTypeRedis() {
            setField(validator, "storeType", "redis");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-219: 存储配置 - 有效类型h2")
        void testValidStoreTypeH2() {
            setField(validator, "storeType", "h2");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-220: 存储配置 - 无效类型抛异常")
        void testInvalidStoreType() {
            setField(validator, "storeType", "invalid");

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertThrows(IllegalStateException.class, () -> validator.onApplicationEvent(event));
        }
    }

    @Nested
    @DisplayName("验证开关测试")
    class ValidationSwitchTests {

        @Test
        @DisplayName("VAL-221: 验证开关 - 禁用验证时不执行")
        void testValidationDisabled() {
            setField(validator, "validationEnabled", false);
            setField(validator, "serverPort", 0); // 无效端口

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }
    }

    @Nested
    @DisplayName("监控配置验证测试")
    class MonitoringConfigTests {

        @Test
        @DisplayName("VAL-222: 监控配置 - 禁用监控产生警告")
        void testMonitoringDisabled() {
            setField(validator, "monitoringEnabled", false);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }

        @Test
        @DisplayName("VAL-223: 监控配置 - 禁用追踪产生警告")
        void testTracingDisabled() {
            setField(validator, "tracingEnabled", false);

            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        }
    }
}

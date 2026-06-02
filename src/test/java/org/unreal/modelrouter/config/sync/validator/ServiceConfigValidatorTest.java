package org.unreal.modelrouter.config.sync.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.dto.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceConfigValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("ServiceConfigValidator 测试")
class ServiceConfigValidatorTest {

    private ServiceConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ServiceConfigValidator();
    }

    @Nested
    @DisplayName("服务类型验证测试")
    class ValidateServiceTypeTests {

        @Test
        @DisplayName("VAL-083: 服务类型验证 - 有效类型chat")
        void testValidateServiceTypeChat() {
            assertDoesNotThrow(() -> validator.validateServiceType("chat"));
        }

        @Test
        @DisplayName("VAL-084: 服务类型验证 - 有效类型embedding")
        void testValidateServiceTypeEmbedding() {
            assertDoesNotThrow(() -> validator.validateServiceType("embedding"));
        }

        @Test
        @DisplayName("VAL-085: 服务类型验证 - null值抛异常")
        void testValidateServiceTypeNull() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateServiceType(null)
            );
            assertTrue(ex.getMessage().contains("服务类型不能为空"));
        }

        @Test
        @DisplayName("VAL-086: 服务类型验证 - 空字符串抛异常")
        void testValidateServiceTypeEmpty() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateServiceType("")
            );
            assertTrue(ex.getMessage().contains("服务类型不能为空"));
        }

        @Test
        @DisplayName("VAL-087: 服务类型验证 - 无效类型抛异常")
        void testValidateServiceTypeInvalid() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateServiceType("invalid-type")
            );
            assertTrue(ex.getMessage().contains("无效的服务类型"));
        }
    }

    @Nested
    @DisplayName("服务配置验证测试")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("VAL-088: 服务配置验证 - null抛异常")
        void testValidateConfigurationNull() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateConfiguration(null)
            );
            assertTrue(ex.getMessage().contains("服务配置不能为空"));
        }

        @Test
        @DisplayName("VAL-089: 服务配置验证 - 适配器为空抛异常")
        void testValidateConfigurationEmptyAdapter() {
            ServiceConfiguration config = new ServiceConfiguration(
                null, null, null, null, null, null
            );

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateConfiguration(config)
            );
            assertTrue(ex.getMessage().contains("适配器配置不能为空"));
        }

        @Test
        @DisplayName("VAL-089: 服务配置验证 - 成功")
        void testValidateConfigurationSuccess() {
            ServiceConfiguration config = new ServiceConfiguration(
                "ollama", null, null, null, null, null
            );

            assertDoesNotThrow(() -> validator.validateConfiguration(config));
        }
    }

    @Nested
    @DisplayName("实例配置验证测试")
    class ValidateInstancesTests {

        @Test
        @DisplayName("VAL-090: 实例配置验证 - null列表跳过")
        void testValidateInstancesNull() {
            assertDoesNotThrow(() -> validator.validateInstances(null));
        }

        @Test
        @DisplayName("VAL-091: 实例配置验证 - 空列表跳过")
        void testValidateInstancesEmpty() {
            assertDoesNotThrow(() -> validator.validateInstances(Collections.emptyList()));
        }

        @Test
        @DisplayName("VAL-092: 实例配置验证 - null实例抛异常")
        void testValidateInstancesNullInstance() {
            List<ModelInstanceConfiguration> instances = Arrays.asList((ModelInstanceConfiguration) null);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
            );
            assertTrue(ex.getMessage().contains("实例配置不能为空"));
        }

        @Test
        @DisplayName("VAL-093: 实例配置验证 - baseUrl为空抛异常")
        void testValidateInstancesEmptyBaseUrl() {
            ModelInstanceConfiguration instance = new ModelInstanceConfiguration(
                "instance-1", null, null, null, null, null, null, null, null, null, null
            );
            List<ModelInstanceConfiguration> instances = Collections.singletonList(instance);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
            );
            assertTrue(ex.getMessage().contains("baseUrl 不能为空"));
        }

        @Test
        @DisplayName("VAL-094: 实例配置验证 - 负数权重抛异常")
        void testValidateInstancesNegativeWeight() {
            ModelInstanceConfiguration instance = new ModelInstanceConfiguration(
                "instance-1", "http://localhost:8080", null, null, -1, null, null, null, null, null, null
            );
            List<ModelInstanceConfiguration> instances = Collections.singletonList(instance);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
            );
            assertTrue(ex.getMessage().contains("权重不能为负数"));
        }

        @Test
        @DisplayName("VAL-095: 实例配置验证 - 无效状态抛异常")
        void testValidateInstancesInvalidStatus() {
            ModelInstanceConfiguration instance = new ModelInstanceConfiguration(
                "instance-1", "http://localhost:8080", null, null, 1, "invalid-status", null, null, null, null, null
            );
            List<ModelInstanceConfiguration> instances = Collections.singletonList(instance);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
            );
            assertTrue(ex.getMessage().contains("实例状态必须是 active 或 inactive"));
        }

        @Test
        @DisplayName("VAL-096: 实例配置验证 - 成功")
        void testValidateInstancesSuccess() {
            ModelInstanceConfiguration instance = new ModelInstanceConfiguration(
                "instance-1", "http://localhost:8080", null, null, 1, "active", null, null, null, null, null
            );
            List<ModelInstanceConfiguration> instances = Collections.singletonList(instance);

            assertDoesNotThrow(() -> validator.validateInstances(instances));
        }
    }

    @Nested
    @DisplayName("限流配置验证测试")
    class ValidateRateLimitConfigTests {

        @Test
        @DisplayName("VAL-097: 限流配置验证 - null跳过")
        void testValidateRateLimitConfigNull() {
            assertDoesNotThrow(() -> validator.validateRateLimitConfig(null));
        }

        @Test
        @DisplayName("VAL-098: 限流配置验证 - 负数每秒请求数抛异常")
        void testValidateRateLimitConfigNegativeRps() {
            RateLimitConfiguration config = new RateLimitConfiguration(
                -1, null, null, null, null, true
            );

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRateLimitConfig(config)
            );
            assertTrue(ex.getMessage().contains("每秒请求数不能为负数"));
        }

        @Test
        @DisplayName("VAL-099: 限流配置验证 - 成功")
        void testValidateRateLimitConfigSuccess() {
            RateLimitConfiguration config = new RateLimitConfiguration(
                100, 1000, null, null, null, true
            );

            assertDoesNotThrow(() -> validator.validateRateLimitConfig(config));
        }
    }

    @Nested
    @DisplayName("熔断器配置验证测试")
    class ValidateCircuitBreakerConfigTests {

        @Test
        @DisplayName("VAL-100: 熔断器配置验证 - null跳过")
        void testValidateCircuitBreakerConfigNull() {
            assertDoesNotThrow(() -> validator.validateCircuitBreakerConfig(null));
        }

        @Test
        @DisplayName("VAL-101: 熔断器配置验证 - 负数失败阈值抛异常")
        void testValidateCircuitBreakerConfigNegativeThreshold() {
            CircuitBreakerConfiguration config = new CircuitBreakerConfiguration(
                -1, null, null, true
            );

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateCircuitBreakerConfig(config)
            );
            assertTrue(ex.getMessage().contains("失败阈值不能为负数"));
        }

        @Test
        @DisplayName("VAL-102: 熔断器配置验证 - 成功")
        void testValidateCircuitBreakerConfigSuccess() {
            CircuitBreakerConfiguration config = new CircuitBreakerConfiguration(
                5, 1000L, 3, true
            );

            assertDoesNotThrow(() -> validator.validateCircuitBreakerConfig(config));
        }
    }

    @Nested
    @DisplayName("降级配置验证测试")
    class ValidateFallbackConfigTests {

        @Test
        @DisplayName("VAL-103: 降级配置验证 - null跳过")
        void testValidateFallbackConfigNull() {
            assertDoesNotThrow(() -> validator.validateFallbackConfig(null));
        }

        @Test
        @DisplayName("VAL-104: 降级配置验证 - 负数重试次数抛异常")
        void testValidateFallbackConfigNegativeRetries() {
            FallbackConfiguration config = new FallbackConfiguration(
                true, null, -1, null, null
            );

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFallbackConfig(config)
            );
            assertTrue(ex.getMessage().contains("最大重试次数不能为负数"));
        }

        @Test
        @DisplayName("VAL-105: 降级配置验证 - 成功")
        void testValidateFallbackConfigSuccess() {
            FallbackConfiguration config = new FallbackConfiguration(
                true, "http://fallback:8080", 3, 1000L, true
            );

            assertDoesNotThrow(() -> validator.validateFallbackConfig(config));
        }
    }
}

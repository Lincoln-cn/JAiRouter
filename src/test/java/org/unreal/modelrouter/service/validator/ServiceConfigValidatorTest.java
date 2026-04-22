package org.unreal.modelrouter.service.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.dto.CircuitBreakerConfiguration;
import org.unreal.modelrouter.config.dto.FallbackConfiguration;
import org.unreal.modelrouter.config.dto.LoadBalanceConfiguration;
import org.unreal.modelrouter.config.dto.ModelInstanceConfiguration;
import org.unreal.modelrouter.config.dto.RateLimitConfiguration;
import org.unreal.modelrouter.config.dto.ServiceConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceConfigValidator 单元测试
 *
 * @author JAiRouter Team
 * @since v2.2.6
 */
class ServiceConfigValidatorTest {

    private ServiceConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ServiceConfigValidator();
    }

    @Test
    void testValidateServiceType_Valid() {
        // Given
        String serviceType = "chat";

        // When & Then
        assertDoesNotThrow(() -> validator.validateServiceType(serviceType));
    }

    @Test
    void testValidateServiceType_Null() {
        // Given
        String serviceType = null;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateServiceType(serviceType)
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testValidateServiceType_Blank() {
        // Given
        String serviceType = "  ";

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateServiceType(serviceType)
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testValidateServiceType_Invalid() {
        // Given
        String serviceType = "invalid-type";

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateServiceType(serviceType)
        );
        assertTrue(exception.getMessage().contains("无效的服务类型"));
    }

    @Test
    void testValidateConfiguration_Valid() {
        // Given
        ServiceConfiguration config = new ServiceConfiguration(
                "normal",
                List.of(),
                LoadBalanceConfiguration.defaultConfig(),
                RateLimitConfiguration.defaultConfig(),
                CircuitBreakerConfiguration.defaultConfig(),
                FallbackConfiguration.defaultConfig()
        );

        // When & Then
        assertDoesNotThrow(() -> validator.validateConfiguration(config));
    }

    @Test
    void testValidateConfiguration_Null() {
        // Given
        ServiceConfiguration config = null;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateConfiguration(config)
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    void testValidateConfiguration_NullAdapter() {
        // Given
        ServiceConfiguration config = new ServiceConfiguration(
                null,
                List.of(),
                null,
                null,
                null,
                null
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateConfiguration(config)
        );
        assertTrue(exception.getMessage().contains("适配器配置不能为空"));
    }

    @Test
    void testValidateConfiguration_BlankAdapter() {
        // Given
        ServiceConfiguration config = new ServiceConfiguration(
                "  ",
                List.of(),
                null,
                null,
                null,
                null
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateConfiguration(config)
        );
        assertTrue(exception.getMessage().contains("适配器配置不能为空"));
    }

    @Test
    void testValidateLoadBalanceConfig_Valid() {
        // Given
        LoadBalanceConfiguration config = new LoadBalanceConfiguration("round_robin", "murmur3");

        // When & Then
        assertDoesNotThrow(() -> validator.validateLoadBalanceConfig(config));
    }

    @Test
    void testValidateLoadBalanceConfig_Null() {
        // Given
        LoadBalanceConfiguration config = null;

        // When & Then
        assertDoesNotThrow(() -> validator.validateLoadBalanceConfig(config));
    }

    @Test
    void testValidateLoadBalanceConfig_InvalidType() {
        // Given
        LoadBalanceConfiguration config = new LoadBalanceConfiguration("invalid-type", null);

        // When & Then
        assertDoesNotThrow(() -> validator.validateLoadBalanceConfig(config));
    }

    @Test
    void testValidateRateLimitConfig_Valid() {
        // Given
        RateLimitConfiguration config = new RateLimitConfiguration(
                100, 6000, 360000, 8640000, 50, true
        );

        // When & Then
        assertDoesNotThrow(() -> validator.validateRateLimitConfig(config));
    }

    @Test
    void testValidateRateLimitConfig_NegativeRate() {
        // Given
        RateLimitConfiguration config = new RateLimitConfiguration(
                -1, null, null, null, null, true
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRateLimitConfig(config)
        );
        assertTrue(exception.getMessage().contains("不能为负数"));
    }

    @Test
    void testValidateRateLimitConfig_NegativeBurst() {
        // Given
        RateLimitConfiguration config = new RateLimitConfiguration(
                100, 6000, 360000, 8640000, -10, true
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRateLimitConfig(config)
        );
        assertTrue(exception.getMessage().contains("不能为负数"));
    }

    @Test
    void testValidateCircuitBreakerConfig_Valid() {
        // Given
        CircuitBreakerConfiguration config = new CircuitBreakerConfiguration(
                5, 60000L, 2, true
        );

        // When & Then
        assertDoesNotThrow(() -> validator.validateCircuitBreakerConfig(config));
    }

    @Test
    void testValidateCircuitBreakerConfig_NegativeThreshold() {
        // Given
        CircuitBreakerConfiguration config = new CircuitBreakerConfiguration(
                -1, 60000L, 2, true
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateCircuitBreakerConfig(config)
        );
        assertTrue(exception.getMessage().contains("不能为负数"));
    }

    @Test
    void testValidateCircuitBreakerConfig_NegativeTimeout() {
        // Given
        CircuitBreakerConfiguration config = new CircuitBreakerConfiguration(
                5, -1000L, 2, true
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateCircuitBreakerConfig(config)
        );
        assertTrue(exception.getMessage().contains("不能为负数"));
    }

    @Test
    void testValidateFallbackConfig_Valid() {
        // Given
        FallbackConfiguration config = new FallbackConfiguration(
                true, "http://fallback.com", 3, 1000L, true
        );

        // When & Then
        assertDoesNotThrow(() -> validator.validateFallbackConfig(config));
    }

    @Test
    void testValidateFallbackConfig_BlankUrl() {
        // Given
        FallbackConfiguration config = new FallbackConfiguration(
                true, "  ", 3, 1000L, true
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFallbackConfig(config)
        );
        assertTrue(exception.getMessage().contains("不能为空字符串"));
    }

    @Test
    void testValidateFallbackConfig_NegativeRetries() {
        // Given
        FallbackConfiguration config = new FallbackConfiguration(
                true, "http://fallback.com", -1, 1000L, true
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFallbackConfig(config)
        );
        assertTrue(exception.getMessage().contains("不能为负数"));
    }

    @Test
    void testValidateInstances_Valid() {
        // Given
        List<ModelInstanceConfiguration> instances = List.of(
                new ModelInstanceConfiguration(
                        "instance1", "http://localhost:8080", "/v1/chat", "normal",
                        1, "active", null, null, null, null, null
                )
        );

        // When & Then
        assertDoesNotThrow(() -> validator.validateInstances(instances));
    }

    @Test
    void testValidateInstances_Null() {
        // Given
        List<ModelInstanceConfiguration> instances = null;

        // When & Then
        assertDoesNotThrow(() -> validator.validateInstances(instances));
    }

    @Test
    void testValidateInstances_Empty() {
        // Given
        List<ModelInstanceConfiguration> instances = List.of();

        // When & Then
        assertDoesNotThrow(() -> validator.validateInstances(instances));
    }

    @Test
    void testValidateInstances_NullBaseUrl() {
        // Given
        List<ModelInstanceConfiguration> instances = List.of(
                new ModelInstanceConfiguration(
                        null, null, "/v1/chat", "normal",
                        1, "active", null, null, null, null, null
                )
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
        );
        assertTrue(exception.getMessage().contains("baseUrl 不能为空"));
    }

    @Test
    void testValidateInstances_NegativeWeight() {
        // Given
        List<ModelInstanceConfiguration> instances = List.of(
                new ModelInstanceConfiguration(
                        "instance1", "http://localhost:8080", "/v1/chat", "normal",
                        -1, "active", null, null, null, null, null
                )
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
        );
        assertTrue(exception.getMessage().contains("不能为负数"));
    }

    @Test
    void testValidateInstances_InvalidStatus() {
        // Given
        List<ModelInstanceConfiguration> instances = List.of(
                new ModelInstanceConfiguration(
                        "instance1", "http://localhost:8080", "/v1/chat", "normal",
                        1, "invalid-status", null, null, null, null, null
                )
        );

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateInstances(instances)
        );
        assertTrue(exception.getMessage().contains("必须是 active 或 inactive"));
    }
}

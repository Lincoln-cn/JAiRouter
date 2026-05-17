package org.unreal.modelrouter.config.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.config.core.tracker.ConfigSourceTracker;
import org.unreal.modelrouter.config.core.validator.SensitiveConfigValidator;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigValidationController 单元测试
 * 
 * <p>测试配置校验、来源追踪等核心功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("ConfigValidationController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigValidationControllerTest {

    @Mock
    private SensitiveConfigValidator sensitiveConfigValidator;

    @Mock
    private ConfigSourceTracker configSourceTracker;

    @InjectMocks
    private ConfigValidationController controller;

    // ==================== 获取配置来源测试 ====================

    @Nested
    @DisplayName("GET /api/config/sources - 获取配置来源测试")
    class GetConfigSourcesTests {

        @Test
        @DisplayName("CONFIG-001: 成功获取配置来源")
        void testGetConfigSources_success() {
            // Given
            Map<String, Object> sources = new HashMap<>();
            sources.put("server.port", Map.of("source", "application.yml", "value", 8080));
            when(configSourceTracker.getConfigSourcesInfo()).thenReturn(sources);

            // When
            Map<String, Object> result = controller.getConfigSources();

            // Then
            assertNotNull(result);
            assertTrue(result.containsKey("server.port"));
        }
    }

    // ==================== 获取校验规则测试 ====================

    @Nested
    @DisplayName("GET /api/config/validation-rules - 获取校验规则测试")
    class GetValidationRulesTests {

        @Test
        @DisplayName("CONFIG-002: 成功获取校验规则")
        void testGetValidationRules_success() {
            // When
            Map<String, Object> result = controller.getValidationRules();

            // Then
            assertNotNull(result);
            assertTrue(result.containsKey("sensitiveConfig"));
            assertTrue(result.containsKey("integrityConfig"));
            assertTrue(result.containsKey("priority"));
        }

        @Test
        @DisplayName("CONFIG-003: 验证敏感配置规则")
        void testGetValidationRules_sensitiveRules() {
            // When
            Map<String, Object> result = controller.getValidationRules();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> sensitiveRules = (Map<String, Object>) result.get("sensitiveConfig");
            assertTrue((Boolean) sensitiveRules.get("enabled"));
            assertEquals(32, sensitiveRules.get("minLength"));
        }

        @Test
        @DisplayName("CONFIG-004: 验证配置优先级")
        void testGetValidationRules_priority() {
            // When
            Map<String, Object> result = controller.getValidationRules();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> priority = (Map<String, Object>) result.get("priority");
            assertTrue(priority.containsKey("order"));
            assertTrue(priority.containsKey("recommendation"));
        }
    }

    // ==================== 获取环境变量指南测试 ====================

    @Nested
    @DisplayName("GET /api/config/environment-variables - 获取环境变量指南测试")
    class GetEnvironmentVariablesGuideTests {

        @Test
        @DisplayName("CONFIG-005: 成功获取环境变量指南")
        void testGetEnvironmentVariablesGuide_success() {
            // When
            Map<String, Object> result = controller.getEnvironmentVariablesGuide();

            // Then
            assertNotNull(result);
            assertTrue(result.containsKey("required"));
            assertTrue(result.containsKey("optional"));
        }

        @Test
        @DisplayName("CONFIG-006: 验证必须配置的环境变量")
        void testGetEnvironmentVariablesGuide_required() {
            // When
            Map<String, Object> result = controller.getEnvironmentVariablesGuide();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> required = (Map<String, Object>) result.get("required");
            assertTrue(required.containsKey("JWT_SECRET"));
            assertTrue(required.containsKey("INITIAL_ADMIN_PASSWORD"));
        }

        @Test
        @DisplayName("CONFIG-007: 验证可选配置的环境变量")
        void testGetEnvironmentVariablesGuide_optional() {
            // When
            Map<String, Object> result = controller.getEnvironmentVariablesGuide();

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> optional = (Map<String, Object>) result.get("optional");
            assertTrue(optional.containsKey("REDIS_HOST"));
            assertTrue(optional.containsKey("REDIS_PORT"));
            assertTrue(optional.containsKey("SERVER_PORT"));
        }
    }
}

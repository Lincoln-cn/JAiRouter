package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator.FullValidationResult;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator.ValidationFailureType;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator.ValidationResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ApiKeyValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyValidator 测试")
class ApiKeyValidatorTest {

    private ApiKeyValidator validator;
    private Map<String, ApiKey> apiKeyCache;

    @BeforeEach
    void setUp() {
        validator = new ApiKeyValidator();
        apiKeyCache = new HashMap<>();
    }

    @Nested
    @DisplayName("格式验证测试")
    class FormatValidationTests {

        @Test
        @DisplayName("VAL-001: 有效格式验证 - 标准API Key")
        void testValidateFormatValid() {
            ValidationResult result = validator.validateFormat("sk-valid-api-key-12345");

            assertTrue(result.isSuccess());
            assertNull(result.getMessage());
        }

        @Test
        @DisplayName("VAL-002: 格式验证 - 空值")
        void testValidateFormatNull() {
            ValidationResult result = validator.validateFormat(null);

            assertFalse(result.isSuccess());
            assertEquals("API Key 不能为空", result.getMessage());
        }

        @Test
        @DisplayName("VAL-003: 格式验证 - 空字符串")
        void testValidateFormatEmpty() {
            ValidationResult result = validator.validateFormat("");

            assertFalse(result.isSuccess());
            assertEquals("API Key 不能为空", result.getMessage());
        }

        @Test
        @DisplayName("VAL-004: 格式验证 - 空白字符串")
        void testValidateFormatBlank() {
            ValidationResult result = validator.validateFormat("   ");

            assertFalse(result.isSuccess());
            assertEquals("API Key 不能为空", result.getMessage());
        }

        @Test
        @DisplayName("VAL-005: 格式验证 - 长度不足")
        void testValidateFormatTooShort() {
            ValidationResult result = validator.validateFormat("sk-123");

            assertFalse(result.isSuccess());
            assertEquals("API Key 长度不足", result.getMessage());
        }

        @Test
        @DisplayName("VAL-006: 格式验证 - 前缀错误")
        void testValidateFormatWrongPrefix() {
            ValidationResult result = validator.validateFormat("pk-invalid-key-format");

            assertFalse(result.isSuccess());
            assertEquals("API Key 格式无效，必须以 'sk-' 开头", result.getMessage());
        }
    }

    @Nested
    @DisplayName("匹配查找测试")
    class FindMatchingKeyTests {

        @Test
        @DisplayName("VAL-007: 查找匹配的Key - 存在")
        void testFindMatchingKeyFound() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", true);
            apiKeyCache.put("hash1", apiKey);

            when(apiKey.verifyKey("sk-test-key-123456")).thenReturn(true);

            ApiKey found = validator.findMatchingKey("sk-test-key-123456", apiKeyCache);

            assertNotNull(found);
            assertEquals("key-001", found.getKeyId());
        }

        @Test
        @DisplayName("VAL-008: 查找匹配的Key - 不存在")
        void testFindMatchingKeyNotFound() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", true);
            apiKeyCache.put("hash1", apiKey);

            when(apiKey.verifyKey("sk-other-key")).thenReturn(false);

            ApiKey found = validator.findMatchingKey("sk-other-key", apiKeyCache);

            assertNull(found);
        }

        @Test
        @DisplayName("VAL-009: 查找匹配的Key - 空缓存")
        void testFindMatchingKeyEmptyCache() {
            ApiKey found = validator.findMatchingKey("sk-test-key", apiKeyCache);

            assertNull(found);
        }
    }

    @Nested
    @DisplayName("启用状态验证测试")
    class EnabledValidationTests {

        @Test
        @DisplayName("VAL-010: 启用状态验证 - 已启用")
        void testValidateEnabledTrue() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);

            ValidationResult result = validator.validateEnabled(apiKey);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("VAL-011: 启用状态验证 - 已禁用")
        void testValidateEnabledFalse() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", false);

            ValidationResult result = validator.validateEnabled(apiKey);

            assertFalse(result.isSuccess());
            assertEquals("API Key 已被禁用", result.getMessage());
        }
    }

    @Nested
    @DisplayName("过期验证测试")
    class ExpirationValidationTests {

        @Test
        @DisplayName("VAL-012: 过期验证 - 未过期")
        void testValidateExpirationNotExpired() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);
            when(apiKey.isExpired()).thenReturn(false);

            ValidationResult result = validator.validateExpiration(apiKey);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("VAL-013: 过期验证 - 已过期")
        void testValidateExpirationExpired() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);
            when(apiKey.isExpired()).thenReturn(true);

            ValidationResult result = validator.validateExpiration(apiKey);

            assertFalse(result.isSuccess());
            assertEquals("API Key 已过期", result.getMessage());
        }
    }

    @Nested
    @DisplayName("IP白名单验证测试")
    class IpWhitelistValidationTests {

        @Test
        @DisplayName("VAL-014: IP白名单验证 - 允许")
        void testValidateIpWhitelistAllowed() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);
            when(apiKey.isIpAllowed("192.168.1.100")).thenReturn(true);

            ValidationResult result = validator.validateIpWhitelist(apiKey, "192.168.1.100");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("VAL-015: IP白名单验证 - 不允许")
        void testValidateIpWhitelistNotAllowed() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);
            when(apiKey.isIpAllowed("10.0.0.1")).thenReturn(false);

            ValidationResult result = validator.validateIpWhitelist(apiKey, "10.0.0.1");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("IP 地址不允许访问"));
        }
    }

    @Nested
    @DisplayName("每日限制验证测试")
    class DailyLimitValidationTests {

        @Test
        @DisplayName("VAL-016: 每日限制验证 - 未超限")
        void testValidateDailyLimitNotExceeded() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);
            when(apiKey.isDailyLimitExceeded()).thenReturn(false);

            ValidationResult result = validator.validateDailyLimit(apiKey);

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("VAL-017: 每日限制验证 - 已超限")
        void testValidateDailyLimitExceeded() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);
            when(apiKey.isDailyLimitExceeded()).thenReturn(true);

            ValidationResult result = validator.validateDailyLimit(apiKey);

            assertFalse(result.isSuccess());
            assertEquals("超过每日请求限制", result.getMessage());
        }
    }

    @Nested
    @DisplayName("完整验证流程测试")
    class FullValidationTests {

        @Test
        @DisplayName("VAL-018: 完整验证 - 成功")
        void testValidateFullySuccess() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", true);
            when(apiKey.verifyKey("sk-test-key-123456")).thenReturn(true);
            when(apiKey.isExpired()).thenReturn(false);
            when(apiKey.isIpAllowed("192.168.1.1")).thenReturn(true);
            when(apiKey.isDailyLimitExceeded()).thenReturn(false);
            when(apiKey.createSecureCopy()).thenReturn(apiKey);

            apiKeyCache.put("hash1", apiKey);

            FullValidationResult result = validator.validateFully(
                    "sk-test-key-123456", apiKeyCache, "192.168.1.1");

            assertTrue(result.isSuccess());
            assertEquals("key-001", result.getKeyId());
        }

        @Test
        @DisplayName("VAL-019: 完整验证 - 格式错误")
        void testValidateFullyFormatError() {
            FullValidationResult result = validator.validateFully(
                    "invalid-key", apiKeyCache, "192.168.1.1");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.FORMAT_ERROR, result.getFailureType());
        }

        @Test
        @DisplayName("VAL-020: 完整验证 - Key未找到")
        void testValidateFullyNotFound() {
            FullValidationResult result = validator.validateFully(
                    "sk-unknown-key", apiKeyCache, "192.168.1.1");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.NOT_FOUND, result.getFailureType());
            assertEquals("无效的 API Key", result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-021: 完整验证 - Key已禁用")
        void testValidateFullyDisabled() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", false);
            when(apiKey.verifyKey("sk-test-key-123456")).thenReturn(true);

            apiKeyCache.put("hash1", apiKey);

            FullValidationResult result = validator.validateFully(
                    "sk-test-key-123456", apiKeyCache, "192.168.1.1");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.VALIDATION_ERROR, result.getFailureType());
            assertEquals("API Key 已被禁用", result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-022: 完整验证 - Key已过期")
        void testValidateFullyExpired() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", true);
            when(apiKey.verifyKey("sk-test-key-123456")).thenReturn(true);
            when(apiKey.isExpired()).thenReturn(true);

            apiKeyCache.put("hash1", apiKey);

            FullValidationResult result = validator.validateFully(
                    "sk-test-key-123456", apiKeyCache, "192.168.1.1");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.VALIDATION_ERROR, result.getFailureType());
            assertEquals("API Key 已过期", result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-023: 完整验证 - IP不在白名单")
        void testValidateFullyIpNotAllowed() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", true);
            when(apiKey.verifyKey("sk-test-key-123456")).thenReturn(true);
            when(apiKey.isExpired()).thenReturn(false);
            when(apiKey.isIpAllowed("10.0.0.1")).thenReturn(false);

            apiKeyCache.put("hash1", apiKey);

            FullValidationResult result = validator.validateFully(
                    "sk-test-key-123456", apiKeyCache, "10.0.0.1");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.VALIDATION_ERROR, result.getFailureType());
        }

        @Test
        @DisplayName("VAL-024: 完整验证 - 超过每日限制")
        void testValidateFullyDailyLimitExceeded() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test-key-123456", true);
            when(apiKey.verifyKey("sk-test-key-123456")).thenReturn(true);
            when(apiKey.isExpired()).thenReturn(false);
            when(apiKey.isIpAllowed("192.168.1.1")).thenReturn(true);
            when(apiKey.isDailyLimitExceeded()).thenReturn(true);

            apiKeyCache.put("hash1", apiKey);

            FullValidationResult result = validator.validateFully(
                    "sk-test-key-123456", apiKeyCache, "192.168.1.1");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.VALIDATION_ERROR, result.getFailureType());
            assertEquals("超过每日请求限制", result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("ValidationResult 测试")
    class ValidationResultTests {

        @Test
        @DisplayName("VAL-025: 创建成功结果")
        void testValidationResultSuccess() {
            ValidationResult result = ValidationResult.success();

            assertTrue(result.isSuccess());
            assertNull(result.getMessage());
            assertNull(result.getErrorCode());
        }

        @Test
        @DisplayName("VAL-026: 创建失败结果 - 仅消息")
        void testValidationResultFailureMessage() {
            ValidationResult result = ValidationResult.failure("验证失败");

            assertFalse(result.isSuccess());
            assertEquals("验证失败", result.getMessage());
            assertNotNull(result.getErrorCode());
        }

        @Test
        @DisplayName("VAL-027: 创建失败结果 - 消息和错误码")
        void testValidationResultFailureMessageAndCode() {
            ValidationResult result = ValidationResult.failure("验证失败", "ERR_001");

            assertFalse(result.isSuccess());
            assertEquals("验证失败", result.getMessage());
            assertEquals("ERR_001", result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("FullValidationResult 测试")
    class FullValidationResultTests {

        @Test
        @DisplayName("VAL-028: 创建成功结果")
        void testFullValidationResultSuccess() {
            ApiKey apiKey = createMockApiKey("key-001", "sk-test", true);

            FullValidationResult result = FullValidationResult.success(apiKey);

            assertTrue(result.isSuccess());
            assertEquals(apiKey, result.getApiKey());
            assertEquals("key-001", result.getKeyId());
        }

        @Test
        @DisplayName("VAL-029: 创建格式错误结果")
        void testFullValidationResultFormatError() {
            FullValidationResult result = FullValidationResult.formatError("格式错误");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.FORMAT_ERROR, result.getFailureType());
            assertEquals("格式错误", result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-030: 创建未找到结果")
        void testFullValidationResultNotFound() {
            FullValidationResult result = FullValidationResult.notFound();

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.NOT_FOUND, result.getFailureType());
            assertEquals("无效的 API Key", result.getErrorMessage());
        }

        @Test
        @DisplayName("VAL-031: 创建验证错误结果")
        void testFullValidationResultValidationError() {
            FullValidationResult result = FullValidationResult.validationError(
                    "key-001", "验证失败", "ERR_001");

            assertFalse(result.isSuccess());
            assertEquals(ValidationFailureType.VALIDATION_ERROR, result.getFailureType());
            assertEquals("key-001", result.getKeyId());
            assertEquals("验证失败", result.getErrorMessage());
            assertEquals("ERR_001", result.getErrorCode());
        }
    }

    // Helper method
    private ApiKey createMockApiKey(String keyId, String keyValue, boolean enabled) {
        ApiKey apiKey = mock(ApiKey.class);
        when(apiKey.getKeyId()).thenReturn(keyId);
        when(apiKey.isEnabled()).thenReturn(enabled);
        return apiKey;
    }
}

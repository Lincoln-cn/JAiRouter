package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.*;
import org.unreal.modelrouter.auth.security.config.SecurityConfigurationValidator.ValidationResult;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityConfigurationValidator 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("SecurityConfigurationValidator 测试")
class SecurityConfigurationValidatorTest {

    private SecurityConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SecurityConfigurationValidator();
    }

    @Nested
    @DisplayName("完整配置验证测试")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("VAL-106: 完整配置验证 - null配置抛异常")
        void testValidateConfigurationNull() {
            ValidationResult result = validator.validateConfiguration(null);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("安全配置不能为空")));
        }

        @Test
        @DisplayName("VAL-107: 完整配置验证 - 空配置成功")
        void testValidateConfigurationEmpty() {
            SecurityProperties properties = mock(SecurityProperties.class);
            when(properties.getApiKey()).thenReturn(null);
            when(properties.getJwt()).thenReturn(null);
            when(properties.getSanitization()).thenReturn(null);
            when(properties.getAudit()).thenReturn(null);

            ValidationResult result = validator.validateConfiguration(properties);

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("JWT配置验证测试")
    class ValidateJwtConfigTests {

        @Test
        @DisplayName("VAL-108: JWT配置验证 - 密钥太短抛异常")
        void testValidateJwtConfigShortSecret() {
            SecurityProperties properties = createSecurityPropertiesWithJwt("short", "HS256", 60L, 7L, "test");

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("JWT密钥长度至少32个字符")));
        }

        @Test
        @DisplayName("VAL-109: JWT配置验证 - 有效配置成功")
        void testValidateJwtConfigValid() {
            SecurityProperties properties = createSecurityPropertiesWithJwt(
                "this-is-a-valid-jwt-secret-key-32-chars", "HS256", 60L, 7L, "jairouter");

            ValidationResult result = validator.validateConfiguration(properties);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-110: JWT配置验证 - 无效算法抛异常")
        void testValidateJwtConfigInvalidAlgorithm() {
            SecurityProperties properties = createSecurityPropertiesWithJwt(
                "this-is-a-valid-jwt-secret-key-32-chars", "INVALID", 60L, 7L, "jairouter");

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("不支持的JWT算法")));
        }

        @Test
        @DisplayName("VAL-111: JWT配置验证 - 过期时间为0抛异常")
        void testValidateJwtConfigZeroExpiration() {
            SecurityProperties properties = createSecurityPropertiesWithJwt(
                "this-is-a-valid-jwt-secret-key-32-chars", "HS256", 0L, 7L, "jairouter");

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("JWT过期时间必须大于0")));
        }

        @Test
        @DisplayName("VAL-112: JWT配置验证 - 过期时间过长产生警告")
        void testValidateJwtConfigLongExpiration() {
            SecurityProperties properties = createSecurityPropertiesWithJwt(
                "this-is-a-valid-jwt-secret-key-32-chars", "HS256", 1500L, 7L, "jairouter");

            ValidationResult result = validator.validateConfiguration(properties);

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
        }
    }

    @Nested
    @DisplayName("API Key配置验证测试")
    class ValidateApiKeyConfigTests {

        @Test
        @DisplayName("VAL-113: API Key配置验证 - 有效配置成功")
        void testValidateApiKeyConfigValid() {
            SecurityProperties properties = createSecurityPropertiesWithApiKey(true, "X-API-Key", 30);

            ValidationResult result = validator.validateConfiguration(properties);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-114: API Key配置验证 - 请求头为空抛异常")
        void testValidateApiKeyConfigEmptyHeader() {
            SecurityProperties properties = createSecurityPropertiesWithApiKey(true, "", 30);

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("请求头名称不能为空")));
        }

        @Test
        @DisplayName("VAL-115: API Key配置验证 - 过期天数为0抛异常")
        void testValidateApiKeyConfigZeroExpiration() {
            SecurityProperties properties = createSecurityPropertiesWithApiKey(true, "X-API-Key", 0);

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("默认过期天数必须大于0")));
        }
    }

    @Nested
    @DisplayName("审计配置验证测试")
    class ValidateAuditConfigTests {

        @Test
        @DisplayName("VAL-116: 审计配置验证 - 有效配置成功")
        void testValidateAuditConfigValid() {
            SecurityProperties properties = createSecurityPropertiesWithAudit("INFO", 30);

            ValidationResult result = validator.validateConfiguration(properties);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("VAL-117: 审计配置验证 - 无效日志级别抛异常")
        void testValidateAuditConfigInvalidLogLevel() {
            SecurityProperties properties = createSecurityPropertiesWithAudit("INVALID", 30);

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("不支持的日志级别")));
        }

        @Test
        @DisplayName("VAL-118: 审计配置验证 - 保留天数为0抛异常")
        void testValidateAuditConfigZeroRetention() {
            SecurityProperties properties = createSecurityPropertiesWithAudit("INFO", 0);

            ValidationResult result = validator.validateConfiguration(properties);

            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("保留天数必须大于0")));
        }
    }

    @Nested
    @DisplayName("ValidationResult 测试")
    class ValidationResultTests {

        @Test
        @DisplayName("VAL-119: ValidationResult - 添加错误")
        void testValidationResultAddError() {
            ValidationResult result = new ValidationResult();
            result.addError("测试错误");

            assertFalse(result.isValid());
            assertEquals(1, result.getErrors().size());
            assertEquals("测试错误", result.getErrors().get(0));
        }

        @Test
        @DisplayName("VAL-120: ValidationResult - 添加警告")
        void testValidationResultAddWarning() {
            ValidationResult result = new ValidationResult();
            result.addWarning("测试警告");

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
            assertEquals(1, result.getWarnings().size());
        }

        @Test
        @DisplayName("VAL-121: ValidationResult - 多个错误和警告")
        void testValidationResultMultiple() {
            ValidationResult result = new ValidationResult();
            result.addError("错误1");
            result.addError("错误2");
            result.addWarning("警告1");

            assertFalse(result.isValid());
            assertEquals(2, result.getErrors().size());
            assertEquals(1, result.getWarnings().size());
        }

        @Test
        @DisplayName("VAL-122: ValidationResult - toString")
        void testValidationResultToString() {
            ValidationResult result = new ValidationResult();
            result.addError("错误");

            String str = result.toString();
            assertTrue(str.contains("valid=false"));
            assertTrue(str.contains("错误"));
        }
    }

    // Helper methods
    private SecurityProperties createSecurityPropertiesWithJwt(String secret, String algorithm,
            Long expirationMinutes, Long refreshDays, String issuer) {
        SecurityProperties properties = mock(SecurityProperties.class);
        JwtConfig jwtConfig = mock(JwtConfig.class);

        when(properties.getApiKey()).thenReturn(null);
        when(properties.getSanitization()).thenReturn(null);
        when(properties.getAudit()).thenReturn(null);

        when(properties.getJwt()).thenReturn(jwtConfig);
        when(jwtConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.getSecret()).thenReturn(secret);
        when(jwtConfig.getAlgorithm()).thenReturn(algorithm);
        when(jwtConfig.getExpirationMinutes()).thenReturn(expirationMinutes);
        when(jwtConfig.getRefreshExpirationDays()).thenReturn(refreshDays);
        when(jwtConfig.getIssuer()).thenReturn(issuer);

        return properties;
    }

    private SecurityProperties createSecurityPropertiesWithApiKey(boolean enabled, String headerName, long expirationDays) {
        SecurityProperties properties = mock(SecurityProperties.class);
        ApiKeyConfig apiKeyConfig = mock(ApiKeyConfig.class);

        when(properties.getJwt()).thenReturn(null);
        when(properties.getSanitization()).thenReturn(null);
        when(properties.getAudit()).thenReturn(null);

        when(properties.getApiKey()).thenReturn(apiKeyConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(enabled);
        when(apiKeyConfig.getHeaderName()).thenReturn(headerName);
        when(apiKeyConfig.getDefaultExpirationDays()).thenReturn(expirationDays);
        when(apiKeyConfig.getKeys()).thenReturn(null);

        return properties;
    }

    private SecurityProperties createSecurityPropertiesWithAudit(String logLevel, int retentionDays) {
        SecurityProperties properties = mock(SecurityProperties.class);
        AuditConfig auditConfig = mock(AuditConfig.class);

        when(properties.getApiKey()).thenReturn(null);
        when(properties.getJwt()).thenReturn(null);
        when(properties.getSanitization()).thenReturn(null);

        when(properties.getAudit()).thenReturn(auditConfig);
        when(auditConfig.getLogLevel()).thenReturn(logLevel);
        when(auditConfig.getRetentionDays()).thenReturn(retentionDays);
        when(auditConfig.getAlertThresholds()).thenReturn(null);

        return properties;
    }
}

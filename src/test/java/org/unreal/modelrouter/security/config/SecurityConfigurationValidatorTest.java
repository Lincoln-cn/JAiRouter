package org.unreal.modelrouter.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityConfigurationValidator测试类
 */
class SecurityConfigurationValidatorTest {

    private SecurityConfigurationValidator validator;
    private SecurityProperties properties;

    @BeforeEach
    void setUp() {
        validator = new SecurityConfigurationValidator();
        properties = new SecurityProperties();
    }

    @Test
    void testValidateConfiguration_NullProperties() {
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("安全配置不能为空", result.getErrors().get(0));
    }

    @Test
    void testValidateConfiguration_ValidConfiguration() {
        // 设置有效配置
        setupValidConfiguration();
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidateApiKeyConfig_EmptyHeaderName() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("");
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("API Key请求头名称不能为空")));
    }

    @Test
    void testValidateApiKeyConfig_InvalidExpirationDays() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setDefaultExpirationDays(0);
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("API Key默认过期天数必须大于0")));
    }

    @Test
    void testValidateApiKeyConfig_LongExpirationDays() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setDefaultExpirationDays(4000);
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("超过10年")));
    }

    @Test
    void testValidateApiKeyConfig_InvalidCacheExpiration() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setCacheEnabled(true);
        properties.getApiKey().setCacheExpirationSeconds(30);
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("API Key缓存过期时间不能少于60秒")));
    }

    @Test
    void testValidateApiKeyList_EmptyList() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setKeys(Collections.emptyList());
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("API Key列表为空")));
    }

    @Test
    void testValidateApiKeyList_InvalidKeyValue() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        
        ApiKeyInfo invalidApiKey = ApiKeyInfo.builder()
                .keyId("test-key")
                .keyValue("invalid@key!")  // 包含无效字符
                .description("测试密钥")
                .enabled(true)
                .build();
        
        properties.getApiKey().setKeys(Collections.singletonList(invalidApiKey));
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("Key Value格式无效")));
    }

    @Test
    void testValidateApiKeyList_ExpiredKey() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        
        ApiKeyInfo expiredApiKey = ApiKeyInfo.builder()
                .keyId("expired-key")
                .keyValue("validkeyvalue123")
                .description("过期密钥")
                .enabled(true)
                .expiresAt(LocalDateTime.now().minusDays(1))  // 已过期
                .build();
        
        properties.getApiKey().setKeys(Collections.singletonList(expiredApiKey));
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("已过期")));
    }

    @Test
    void testValidateApiKeyList_DuplicateKeyId() {
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        
        ApiKeyInfo apiKey1 = ApiKeyInfo.builder()
                .keyId("duplicate-key")
                .keyValue("validkeyvalue123")
                .description("密钥1")
                .enabled(true)
                .build();
        
        ApiKeyInfo apiKey2 = ApiKeyInfo.builder()
                .keyId("duplicate-key")  // 重复的Key ID
                .keyValue("validkeyvalue456")
                .description("密钥2")
                .enabled(true)
                .build();
        
        properties.getApiKey().setKeys(Arrays.asList(apiKey1, apiKey2));
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("存在重复的API Key ID")));
    }

    @Test
    void testValidateJwtConfig_ShortSecret() {
        properties.getJwt().setEnabled(true);
        properties.getJwt().setSecret("short");  // 密钥太短
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("JWT密钥长度至少32个字符")));
    }

    @Test
    void testValidateJwtConfig_UnsupportedAlgorithm() {
        properties.getJwt().setEnabled(true);
        properties.getJwt().setSecret("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        properties.getJwt().setAlgorithm("INVALID");  // 不支持的算法
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("不支持的JWT算法")));
    }

    @Test
    void testValidateJwtConfig_InvalidExpirationTime() {
        properties.getJwt().setEnabled(true);
        properties.getJwt().setSecret("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setExpirationMinutes(0);  // 无效的过期时间
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("JWT过期时间必须大于0分钟")));
    }

    @Test
    void testValidateSanitizationConfig_EmptyMaskingChar() {
        properties.getSanitization().getRequest().setMaskingChar("");
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("请求脱敏掩码字符不能为空")));
    }

    @Test
    void testValidateSanitizationConfig_InvalidPiiPattern() {
        properties.getSanitization().getRequest().setPiiPatterns(List.of("[invalid"));  // 无效的正则表达式
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("PII模式") && error.contains("格式无效")));
    }

    @Test
    void testValidateAuditConfig_UnsupportedLogLevel() {
        properties.getAudit().setLogLevel("INVALID");  // 不支持的日志级别
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("不支持的日志级别")));
    }

    @Test
    void testValidateAuditConfig_InvalidRetentionDays() {
        properties.getAudit().setRetentionDays(0);  // 无效的保留天数
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("审计日志保留天数必须大于0")));
    }

    @Test
    void testValidateAuditConfig_InvalidAlertThresholds() {
        properties.getAudit().getAlertThresholds().setAuthFailuresPerMinute(0);  // 无效的告警阈值
        
        SecurityConfigurationValidator.ValidationResult result = validator.validateConfiguration(properties);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("认证失败告警阈值必须大于0")));
    }

    @Test
    void testValidationResult_ToString() {
        SecurityConfigurationValidator.ValidationResult result = new SecurityConfigurationValidator.ValidationResult();
        result.addError("测试错误");
        result.addWarning("测试警告");
        
        String resultString = result.toString();
        
        assertTrue(resultString.contains("valid=false"));
        assertTrue(resultString.contains("测试错误"));
        assertTrue(resultString.contains("测试警告"));
    }

    /**
     * 设置有效的配置
     */
    private void setupValidConfiguration() {
        // API Key配置
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getApiKey().setDefaultExpirationDays(365);
        properties.getApiKey().setCacheEnabled(true);
        properties.getApiKey().setCacheExpirationSeconds(3600);
        
        ApiKeyInfo validApiKey = ApiKeyInfo.builder()
                .keyId("valid-key")
                .keyValue("validkeyvalue123456")
                .description("有效密钥")
                .enabled(true)
                .expiresAt(LocalDateTime.now().plusDays(365))
                .permissions(Arrays.asList("read", "write"))
                .build();
        properties.getApiKey().setKeys(Collections.singletonList(validApiKey));
        
        // JWT配置
        properties.getJwt().setEnabled(true);
        properties.getJwt().setSecret("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        properties.getJwt().setAlgorithm("HS256");
        properties.getJwt().setExpirationMinutes(60);
        properties.getJwt().setRefreshExpirationDays(7);
        properties.getJwt().setIssuer("jairouter");
        
        // 脱敏配置
        properties.getSanitization().getRequest().setMaskingChar("*");
        properties.getSanitization().getRequest().setSensitiveWords(Arrays.asList("password", "secret"));
        properties.getSanitization().getRequest().setPiiPatterns(List.of("\\d{11}"));
        
        properties.getSanitization().getResponse().setMaskingChar("*");
        properties.getSanitization().getResponse().setSensitiveWords(List.of("internal"));
        properties.getSanitization().getResponse().setPiiPatterns(List.of("\\d{11}"));
        
        // 审计配置
        properties.getAudit().setLogLevel("INFO");
        properties.getAudit().setRetentionDays(90);
        properties.getAudit().getAlertThresholds().setAuthFailuresPerMinute(10);
        properties.getAudit().getAlertThresholds().setSanitizationOperationsPerMinute(100);
    }
}
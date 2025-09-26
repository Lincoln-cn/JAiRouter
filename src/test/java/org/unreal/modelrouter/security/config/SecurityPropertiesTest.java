package org.unreal.modelrouter.security.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.unreal.modelrouter.security.config.properties.*;
import org.unreal.modelrouter.security.model.ApiKeyInfo;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityProperties配置属性类测试
 */
class SecurityPropertiesTest {

    private Validator validator;
    private SecurityProperties properties;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        properties = new SecurityProperties();
    }

    @Test
    void testDefaultConfiguration() {
        // 测试默认配置的有效性
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "默认配置应该是有效的");
        
        // 验证默认值
        assertFalse(properties.isEnabled());
        assertNotNull(properties.getApiKey());
        assertNotNull(properties.getJwt());
        assertNotNull(properties.getSanitization());
        assertNotNull(properties.getAudit());
    }

    @Test
    void testApiKeyConfigValidation() {
        ApiKeyConfig apiKeyConfig = properties.getApiKey();
        
        // 测试有效配置
        apiKeyConfig.setHeaderName("X-API-Key");
        apiKeyConfig.setDefaultExpirationDays(365);
        apiKeyConfig.setCacheExpirationSeconds(3600);
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        // 测试无效的headerName
        apiKeyConfig.setHeaderName("");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("headerName")));
        
        // 重置为有效值
        apiKeyConfig.setHeaderName("X-API-Key");
        
        // 测试无效的过期天数
        apiKeyConfig.setDefaultExpirationDays(0);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("defaultExpirationDays")));
        
        apiKeyConfig.setDefaultExpirationDays(4000);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("defaultExpirationDays")));
        
        // 重置为有效值
        apiKeyConfig.setDefaultExpirationDays(365);
        
        // 测试无效的缓存过期时间
        apiKeyConfig.setCacheExpirationSeconds(30);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("cacheExpirationSeconds")));
        
        apiKeyConfig.setCacheExpirationSeconds(100000);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("cacheExpirationSeconds")));
    }

    @Test
    void testJwtConfigValidation() {
        JwtConfig jwtConfig = properties.getJwt();
        jwtConfig.setEnabled(true);
        
        // 测试有效配置
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        jwtConfig.setAlgorithm("HS256");
        jwtConfig.setExpirationMinutes(60);
        jwtConfig.setRefreshExpirationDays(7);
        jwtConfig.setIssuer("jairouter");
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        // 测试无效的密钥长度
        jwtConfig.setSecret("short");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("secret")));
        
        // 重置为有效值
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        
        // 测试无效的算法
        jwtConfig.setAlgorithm("INVALID");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("algorithm")));
        
        // 重置为有效值
        jwtConfig.setAlgorithm("HS256");
        
        // 测试无效的过期时间
        jwtConfig.setExpirationMinutes(0);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("expirationMinutes")));
        
        jwtConfig.setExpirationMinutes(2000);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("expirationMinutes")));
        
        // 重置为有效值
        jwtConfig.setExpirationMinutes(60);
        
        // 测试无效的刷新过期天数
        jwtConfig.setRefreshExpirationDays(0);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("refreshExpirationDays")));
        
        jwtConfig.setRefreshExpirationDays(40);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("refreshExpirationDays")));
        
        // 重置为有效值
        jwtConfig.setRefreshExpirationDays(7);
        
        // 测试无效的发行者
        jwtConfig.setIssuer("");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("issuer")));
    }

    @Test
    void testSanitizationConfigValidation() {
        SanitizationConfig sanitizationConfig = properties.getSanitization();
        
        // 测试请求脱敏配置
        SanitizationConfig.RequestSanitization requestConfig = sanitizationConfig.getRequest();
        requestConfig.setMaskingChar("*");
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        // 测试无效的掩码字符
        requestConfig.setMaskingChar("");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maskingChar")));
        
        requestConfig.setMaskingChar("toolong");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maskingChar")));
        
        // 重置为有效值
        requestConfig.setMaskingChar("*");
        
        // 测试响应脱敏配置
        SanitizationConfig.ResponseSanitization responseConfig = sanitizationConfig.getResponse();
        responseConfig.setMaskingChar("*");
        
        violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        // 测试无效的掩码字符
        responseConfig.setMaskingChar("");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maskingChar")));
    }

    @Test
    void testAuditConfigValidation() {
        AuditConfig auditConfig = properties.getAudit();
        
        // 测试有效配置
        auditConfig.setLogLevel("INFO");
        auditConfig.setRetentionDays(90);
        auditConfig.getAlertThresholds().setAuthFailuresPerMinute(10);
        auditConfig.getAlertThresholds().setSanitizationOperationsPerMinute(100);
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        // 测试无效的日志级别
        auditConfig.setLogLevel("INVALID");
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("logLevel")));
        
        // 重置为有效值
        auditConfig.setLogLevel("INFO");
        
        // 测试无效的保留天数
        auditConfig.setRetentionDays(0);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("retentionDays")));
        
        auditConfig.setRetentionDays(4000);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("retentionDays")));
        
        // 重置为有效值
        auditConfig.setRetentionDays(90);
        
        // 测试无效的告警阈值
        auditConfig.getAlertThresholds().setAuthFailuresPerMinute(0);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("authFailuresPerMinute")));
        
        auditConfig.getAlertThresholds().setAuthFailuresPerMinute(2000);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("authFailuresPerMinute")));
        
        // 重置为有效值
        auditConfig.getAlertThresholds().setAuthFailuresPerMinute(10);
        
        auditConfig.getAlertThresholds().setSanitizationOperationsPerMinute(0);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("sanitizationOperationsPerMinute")));
        
        auditConfig.getAlertThresholds().setSanitizationOperationsPerMinute(20000);
        violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("sanitizationOperationsPerMinute")));
    }

    @Test
    void testConfigurationBinding() {
        // 测试从配置文件绑定属性
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("jairouter.security.enabled", true);
        configMap.put("jairouter.security.api-key.enabled", true);
        configMap.put("jairouter.security.api-key.header-name", "X-Custom-API-Key");
        configMap.put("jairouter.security.api-key.default-expiration-days", 180);
        configMap.put("jairouter.security.jwt.enabled", true);
        configMap.put("jairouter.security.jwt.secret", "this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        configMap.put("jairouter.security.jwt.algorithm", "HS512");
        configMap.put("jairouter.security.sanitization.request.enabled", true);
        configMap.put("jairouter.security.sanitization.request.masking-char", "#");
        configMap.put("jairouter.security.audit.enabled", true);
        configMap.put("jairouter.security.audit.log-level", "DEBUG");
        configMap.put("jairouter.security.audit.retention-days", 30);

        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);
        
        BindResult<SecurityProperties> result = binder.bind("jairouter.security", SecurityProperties.class);
        assertTrue(result.isBound());
        
        SecurityProperties boundProperties = result.get();
        assertTrue(boundProperties.isEnabled());
        assertTrue(boundProperties.getApiKey().isEnabled());
        assertEquals("X-Custom-API-Key", boundProperties.getApiKey().getHeaderName());
        assertEquals(180, boundProperties.getApiKey().getDefaultExpirationDays());
        assertTrue(boundProperties.getJwt().isEnabled());
        assertEquals("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars", boundProperties.getJwt().getSecret());
        assertEquals("HS512", boundProperties.getJwt().getAlgorithm());
        assertTrue(boundProperties.getSanitization().getRequest().isEnabled());
        assertEquals("#", boundProperties.getSanitization().getRequest().getMaskingChar());
        assertTrue(boundProperties.getAudit().isEnabled());
        assertEquals("DEBUG", boundProperties.getAudit().getLogLevel());
        assertEquals(30, boundProperties.getAudit().getRetentionDays());
    }

    @Test
    void testApiKeyListConfiguration() {
        // 测试API Key列表配置
        ApiKeyInfo apiKey1 = ApiKeyInfo.builder()
                .keyId("test-key-1")
                .keyValue("test-value-1")
                .description("测试密钥1")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(365))
                .permissions(Arrays.asList("read", "write"))
                .build();

        ApiKeyInfo apiKey2 = ApiKeyInfo.builder()
                .keyId("test-key-2")
                .keyValue("test-value-2")
                .description("测试密钥2")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(180))
                .permissions(List.of("read"))
                .build();

        properties.getApiKey().setKeys(Arrays.asList(apiKey1, apiKey2));
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        assertEquals(2, properties.getApiKey().getKeys().size());
        assertEquals("test-key-1", properties.getApiKey().getKeys().get(0).getKeyId());
        assertEquals("test-key-2", properties.getApiKey().getKeys().get(1).getKeyId());
    }

    @Test
    void testSensitiveWordsAndPatternsConfiguration() {
        // 测试敏感词和模式配置
        SanitizationConfig.RequestSanitization requestConfig = properties.getSanitization().getRequest();
        requestConfig.setSensitiveWords(Arrays.asList("password", "secret", "token"));
        requestConfig.setPiiPatterns(Arrays.asList("\\d{11}", "\\d{18}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        requestConfig.setWhitelistUsers(Arrays.asList("admin-key", "system-key"));

        SanitizationConfig.ResponseSanitization responseConfig = properties.getSanitization().getResponse();
        responseConfig.setSensitiveWords(Arrays.asList("internal", "debug"));
        responseConfig.setPiiPatterns(Arrays.asList("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
        
        assertEquals(3, requestConfig.getSensitiveWords().size());
        assertEquals(3, requestConfig.getPiiPatterns().size());
        assertEquals(2, requestConfig.getWhitelistUsers().size());
        assertEquals(2, responseConfig.getSensitiveWords().size());
        assertEquals(2, responseConfig.getPiiPatterns().size());
    }

    @Test
    void testCompleteValidConfiguration() {
        // 测试完整的有效配置
        properties.setEnabled(true);
        
        // API Key配置
        ApiKeyConfig apiKeyConfig = properties.getApiKey();
        apiKeyConfig.setEnabled(true);
        apiKeyConfig.setHeaderName("X-API-Key");
        apiKeyConfig.setDefaultExpirationDays(365);
        apiKeyConfig.setCacheEnabled(true);
        apiKeyConfig.setCacheExpirationSeconds(3600);
        
        // JWT配置
        JwtConfig jwtConfig = properties.getJwt();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-jwt-signing-at-least-32-chars");
        jwtConfig.setAlgorithm("HS256");
        jwtConfig.setExpirationMinutes(60);
        jwtConfig.setRefreshExpirationDays(7);
        jwtConfig.setIssuer("jairouter");
        jwtConfig.setBlacklistEnabled(true);
        
        // 脱敏配置
        SanitizationConfig sanitizationConfig = properties.getSanitization();
        sanitizationConfig.getRequest().setEnabled(true);
        sanitizationConfig.getRequest().setSensitiveWords(Arrays.asList("password", "secret"));
        sanitizationConfig.getRequest().setPiiPatterns(List.of("\\d{11}"));
        sanitizationConfig.getRequest().setMaskingChar("*");
        sanitizationConfig.getRequest().setWhitelistUsers(List.of("admin"));
        sanitizationConfig.getRequest().setLogSanitization(true);
        sanitizationConfig.getRequest().setFailOnError(false);
        
        sanitizationConfig.getResponse().setEnabled(true);
        sanitizationConfig.getResponse().setSensitiveWords(List.of("internal"));
        sanitizationConfig.getResponse().setPiiPatterns(List.of("\\d{11}"));
        sanitizationConfig.getResponse().setMaskingChar("*");
        sanitizationConfig.getResponse().setLogSanitization(true);
        sanitizationConfig.getResponse().setFailOnError(false);
        
        // 审计配置
        AuditConfig auditConfig = properties.getAudit();
        auditConfig.setEnabled(true);
        auditConfig.setLogLevel("INFO");
        auditConfig.setIncludeRequestBody(false);
        auditConfig.setIncludeResponseBody(false);
        auditConfig.setRetentionDays(90);
        auditConfig.setAlertEnabled(true);
        auditConfig.getAlertThresholds().setAuthFailuresPerMinute(10);
        auditConfig.getAlertThresholds().setSanitizationOperationsPerMinute(100);
        
        Set<ConstraintViolation<SecurityProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "完整的有效配置不应该有验证错误");
    }
}
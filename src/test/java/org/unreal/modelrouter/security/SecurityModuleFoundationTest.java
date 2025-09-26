package org.unreal.modelrouter.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.unreal.modelrouter.security.config.properties.*;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.security.model.UsageStatistics;
import org.unreal.modelrouter.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全模块基础架构测试
 * 验证安全模块的基础组件是否正确创建和配置
 */
@TestPropertySource(properties = {
    "jairouter.security.enabled=false"  // 禁用安全功能以避免自动配置冲突
})
class SecurityModuleFoundationTest {
    
    @Test
    void testSecurityPropertiesCreation() {
        // 测试安全配置属性类的创建
        SecurityProperties properties = new SecurityProperties();
        assertNotNull(properties);
        assertFalse(properties.isEnabled());
        assertNotNull(properties.getApiKey());
        assertNotNull(properties.getJwt());
        assertNotNull(properties.getSanitization());
        assertNotNull(properties.getAudit());
    }
    
    @Test
    void testApiKeyInfoModel() {
        // 测试API Key数据模型
        UsageStatistics usage = UsageStatistics.builder()
            .totalRequests(100L)
            .successfulRequests(95L)
            .failedRequests(5L)
            .lastUsedAt(LocalDateTime.now())
            .dailyUsage(new HashMap<>())
            .build();
        
        ApiKeyInfo apiKey = ApiKeyInfo.builder()
            .keyId("test-key-001")
            .keyValue("test-api-key-value")
            .description("测试API Key")
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(365))
            .enabled(true)
            .permissions(Arrays.asList("read", "write"))
            .metadata(new HashMap<>())
            .usage(usage)
            .build();
        
        assertNotNull(apiKey);
        assertEquals("test-key-001", apiKey.getKeyId());
        assertTrue(apiKey.isValid());
        assertTrue(apiKey.hasPermission("read"));
        assertFalse(apiKey.hasPermission("admin"));
        assertEquals(0.95, usage.getSuccessRate(), 0.01);
    }
    
    @Test
    void testSanitizationRuleModel() {
        // 测试脱敏规则数据模型
        SanitizationRule rule = SanitizationRule.builder()
            .ruleId("phone-rule-001")
            .name("手机号脱敏规则")
            .description("对手机号进行掩码处理")
            .pattern("\\d{11}")
            .enabled(true)
            .priority(1)
            .applicableContentTypes(Arrays.asList("application/json", "text/plain"))
            .replacementChar("*")
            .build();
        
        assertNotNull(rule);
        assertEquals("phone-rule-001", rule.getRuleId());
        assertTrue(rule.isEnabled());
        assertTrue(rule.isApplicableToContentType("application/json"));
        assertFalse(rule.isApplicableToContentType("application/xml"));
    }
    
    @Test
    void testSecurityAuditEventModel() {
        // 测试安全审计事件数据模型
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("endpoint", "/v1/chat/completions");
        additionalData.put("method", "POST");
        
        SecurityAuditEvent event = SecurityAuditEvent.builder()
            .eventId("audit-001")
            .eventType("AUTHENTICATION_SUCCESS")
            .userId("user-001")
            .clientIp("192.168.1.100")
            .userAgent("Mozilla/5.0")
            .timestamp(LocalDateTime.now())
            .resource("/v1/chat/completions")
            .action("POST")
            .success(true)
            .additionalData(additionalData)
            .requestId("req-001")
            .sessionId("session-001")
            .build();
        
        assertNotNull(event);
        assertEquals("audit-001", event.getEventId());
        assertTrue(event.isSuccess());
        assertEquals("192.168.1.100", event.getClientIp());
        assertNotNull(event.getAdditionalData());
        assertEquals("/v1/chat/completions", event.getAdditionalData().get("endpoint"));
    }
    
    @Test
    void testSecurityUtils() {
        // 测试安全工具类
        String id = SecurityUtils.generateId();
        assertNotNull(id);
        assertTrue(SecurityUtils.isValidUuid(id));
        
        String randomString = SecurityUtils.generateSecureRandomString(16);
        assertNotNull(randomString);
        assertTrue(randomString.length() > 0);
        
        String hash = SecurityUtils.sha256Hash("test-input");
        assertNotNull(hash);
        assertEquals(hash, SecurityUtils.sha256Hash("test-input")); // 相同输入应产生相同哈希
        
        String masked = SecurityUtils.maskSensitiveInfo("1234567890", '*', 2);
        assertEquals("12******90", masked);
        
        assertTrue(SecurityUtils.secureEquals("test", "test"));
        assertFalse(SecurityUtils.secureEquals("test", "different"));
        assertFalse(SecurityUtils.secureEquals("test", null));
        assertFalse(SecurityUtils.secureEquals(null, "test"));
        assertTrue(SecurityUtils.secureEquals(null, null));
        
        String dateString = SecurityUtils.getTodayDateString();
        assertNotNull(dateString);
        assertTrue(dateString.matches("\\d{4}-\\d{2}-\\d{2}"));
    }
    
    @Test
    void testApiKeyConfigDefaults() {
        // 测试API Key配置的默认值
        ApiKeyConfig config = new ApiKeyConfig();
        assertTrue(config.isEnabled());
        assertEquals("X-API-Key", config.getHeaderName());
        assertEquals(365L, config.getDefaultExpirationDays());
        assertTrue(config.isCacheEnabled());
        assertEquals(3600L, config.getCacheExpirationSeconds());
        assertNotNull(config.getKeys());
        assertTrue(config.getKeys().isEmpty());
    }
    
    @Test
    void testJwtConfigDefaults() {
        // 测试JWT配置的默认值
        JwtConfig config = new JwtConfig();
        assertFalse(config.isEnabled());
        assertEquals("HS256", config.getAlgorithm());
        assertEquals(60L, config.getExpirationMinutes());
        assertEquals(7L, config.getRefreshExpirationDays());
        assertEquals("jairouter", config.getIssuer());
        assertTrue(config.isBlacklistEnabled());
    }
    
    @Test
    void testSanitizationConfigDefaults() {
        // 测试脱敏配置的默认值
        SanitizationConfig config = new SanitizationConfig();
        assertNotNull(config.getRequest());
        assertNotNull(config.getResponse());

        SanitizationConfig.RequestSanitization requestConfig = config.getRequest();
        assertTrue(requestConfig.isEnabled());
        assertEquals("*", requestConfig.getMaskingChar());
        assertTrue(requestConfig.isLogSanitization());
        assertNotNull(requestConfig.getSensitiveWords());
        assertNotNull(requestConfig.getPiiPatterns());
        assertNotNull(requestConfig.getWhitelistUsers());

        SanitizationConfig.ResponseSanitization responseConfig = config.getResponse();
        assertTrue(responseConfig.isEnabled());
        assertEquals("*", responseConfig.getMaskingChar());
        assertTrue(responseConfig.isLogSanitization());
        assertNotNull(responseConfig.getSensitiveWords());
        assertNotNull(responseConfig.getPiiPatterns());
    }
    
    @Test
    void testAuditConfigDefaults() {
        // 测试审计配置的默认值
        AuditConfig config = new AuditConfig();
        assertTrue(config.isEnabled());
        assertEquals("INFO", config.getLogLevel());
        assertFalse(config.isIncludeRequestBody());
        assertFalse(config.isIncludeResponseBody());
        assertEquals(90, config.getRetentionDays());
        assertFalse(config.isAlertEnabled());
        assertNotNull(config.getAlertThresholds());

        AuditConfig.AlertThresholds thresholds = config.getAlertThresholds();
        assertEquals(10, thresholds.getAuthFailuresPerMinute());
        assertEquals(100, thresholds.getSanitizationOperationsPerMinute());
    }
}
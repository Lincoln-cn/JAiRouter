package org.unreal.modelrouter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;

/**
 * 测试用安全配置属性类
 * 为测试环境提供合理的默认值以通过验证
 */
@Component
@Validated
@ConfigurationProperties(prefix = "jairouter.security")
public class TestSecurityProperties extends SecurityProperties {
    
    public TestSecurityProperties() {
        // 设置测试环境的合理默认值
        setEnabled(false);
        
        // API Key配置
        getApiKey().setEnabled(true);
        getApiKey().setHeaderName("X-API-Key");
        getApiKey().setDefaultExpirationDays(365);
        getApiKey().setCacheEnabled(true);
        getApiKey().setCacheExpirationSeconds(3600);
        
        // JWT配置
        getJwt().setEnabled(false);
        getJwt().setAlgorithm("HS256");
        getJwt().setExpirationMinutes(60);
        getJwt().setRefreshExpirationDays(7);
        getJwt().setIssuer("jairouter-test");
        getJwt().setBlacklistEnabled(true);
        
        // 脱敏配置
        getSanitization().getRequest().setEnabled(true);
        getSanitization().getRequest().setMaskingChar("*");
        getSanitization().getResponse().setEnabled(true);
        getSanitization().getResponse().setMaskingChar("*");
        
        // 审计配置
        getAudit().setEnabled(true);
        getAudit().setLogLevel("INFO");
        getAudit().setRetentionDays(90);
        getAudit().getAlertThresholds().setAuthFailuresPerMinute(10);
        getAudit().getAlertThresholds().setSanitizationOperationsPerMinute(100);
        
        // 缓存配置
        getCache().setEnabled(true);
        getCache().getInMemory().setMaxSize(1000);
        getCache().getInMemory().setDefaultTtlSeconds(3600);
        getCache().getRedis().setHost("localhost");
        getCache().getRedis().setPort(6379);
        getCache().getRedis().setKeyPrefix("jairouter:test:");
    }
}
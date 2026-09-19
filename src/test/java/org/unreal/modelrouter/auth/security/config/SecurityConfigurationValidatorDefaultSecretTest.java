package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-P0-01：已知出厂默认 JWT 密钥必须被配置校验拒绝（否则可伪造令牌）。
 */
@DisplayName("SecurityConfigurationValidator 拒绝默认 JWT 密钥")
class SecurityConfigurationValidatorDefaultSecretTest {

    private final SecurityConfigurationValidator validator = new SecurityConfigurationValidator();

    private SecurityProperties propsWithSecret(final String secret) {
        SecurityProperties properties = new SecurityProperties();
        JwtConfig jwt = properties.getJwt();
        jwt.setEnabled(true);
        jwt.setSecret(secret);
        jwt.setAlgorithm("HS256");
        jwt.setExpirationMinutes(60);
        jwt.setRefreshExpirationDays(7);
        jwt.setIssuer("jairouter");
        return properties;
    }

    @Test
    @DisplayName("出厂默认密钥 ThisIsADefaultSecretKeyForDevOnly12345678 → error")
    void knownDefaultSecret_isRejected() {
        var result = validator.validateConfiguration(
                propsWithSecret("ThisIsADefaultSecretKeyForDevOnly12345678"));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("默认") || e.contains("JWT_SECRET")),
                "默认密钥必须报错: " + result.getErrors());
    }

    @Test
    @DisplayName("dev 测试默认密钥 → error")
    void devDefaultSecret_isRejected() {
        var result = validator.validateConfiguration(
                propsWithSecret("dev-secret-key-for-testing-only-32-characters-minimum"));
        assertFalse(result.getErrors().isEmpty(), "dev 默认密钥必须报错");
    }

    @Test
    @DisplayName("非默认且 ≥32 字符的密钥 → 无 JWT 密钥相关 error")
    void randomSecret_isAccepted() {
        var result = validator.validateConfiguration(
                propsWithSecret("prod-only-random-secret-please-rotate-0001"));
        assertTrue(result.getErrors().stream().noneMatch(e -> e.contains("默认")),
                "不应误伤真实密钥: " + result.getErrors());
    }
}

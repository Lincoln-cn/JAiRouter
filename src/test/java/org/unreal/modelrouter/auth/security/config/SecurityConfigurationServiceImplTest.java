package org.unreal.modelrouter.auth.security.config;

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
import org.springframework.context.ApplicationEventPublisher;
import org.unreal.modelrouter.auth.security.config.properties.*;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SecurityConfigurationServiceImpl 单元测试
 *
 * <p>测试安全配置管理功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("SecurityConfigurationServiceImpl 安全配置管理服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityConfigurationServiceImplTest {

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private StoreManager storeManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SecurityConfigurationServiceImpl configService;

    private ApiKeyConfig apiKeyConfig;
    private JwtConfig jwtConfig;
    private SanitizationConfig sanitizationConfig;
    private AuditConfig auditConfig;

    @BeforeEach
    void setUp() {
        // 设置 API Key 配置
        apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setEnabled(true);
        apiKeyConfig.setHeaderName("X-API-Key");
        apiKeyConfig.setKeys(new ArrayList<>());
        apiKeyConfig.setDefaultExpirationDays(30);
        when(securityProperties.getApiKey()).thenReturn(apiKeyConfig);

        // 设置 JWT 配置
        jwtConfig = new JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret("test-secret-key-must-be-at-least-32-chars");
        jwtConfig.setAlgorithm("HS512");
        jwtConfig.setExpirationMinutes(60);
        jwtConfig.setRefreshExpirationDays(7);
        jwtConfig.setIssuer("jairouter");
        jwtConfig.setBlacklistEnabled(true);
        when(securityProperties.getJwt()).thenReturn(jwtConfig);

        // 设置脱敏配置
        sanitizationConfig = new SanitizationConfig();
        when(securityProperties.getSanitization()).thenReturn(sanitizationConfig);

        // 设置审计配置
        auditConfig = new AuditConfig();
        auditConfig.setEnabled(true);
        auditConfig.setRetentionDays(90);
        when(securityProperties.getAudit()).thenReturn(auditConfig);

        when(securityProperties.isEnabled()).thenReturn(true);
    }

    // ==================== API Key 配置更新测试 ====================

    @Nested
    @DisplayName("API Key 配置更新测试")
    class UpdateApiKeysTests {

        @Test
        @DisplayName("SEC-001: 更新API Key配置成功")
        void testUpdateApiKeys_Success() {
            // Given
            List<ApiKey> apiKeys = new ArrayList<>();
            ApiKey key1 = ApiKey.builder()
                .keyId("key1")
                .keyHash("test-hash-1")
                .build();
            apiKeys.add(key1);

            // When
            var result = configService.updateApiKeys(apiKeys);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(eventPublisher).publishEvent(any(SecurityConfigurationChangeEvent.class));
        }

        @Test
        @DisplayName("SEC-002: 更新空API Key列表")
        void testUpdateApiKeys_EmptyList() {
            // When
            var result = configService.updateApiKeys(new ArrayList<>());

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }
    }

    // ==================== 脱敏规则更新测试 ====================

    @Nested
    @DisplayName("脱敏规则更新测试")
    class UpdateSanitizationRulesTests {

        @Test
        @DisplayName("SEC-003: 更新脱敏规则成功")
        void testUpdateSanitizationRules_Success() {
            // Given
            List<org.unreal.modelrouter.auth.security.model.SanitizationRule> rules = new ArrayList<>();

            // When
            var result = configService.updateSanitizationRules(rules);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(eventPublisher).publishEvent(any(SecurityConfigurationChangeEvent.class));
        }
    }

    // ==================== JWT 配置更新测试 ====================

    @Nested
    @DisplayName("JWT 配置更新测试")
    class UpdateJwtConfigTests {

        @Test
        @DisplayName("SEC-004: 更新JWT配置成功")
        void testUpdateJwtConfig_Success() {
            // Given
            JwtConfig newConfig = new JwtConfig();
            newConfig.setEnabled(true);
            newConfig.setSecret("new-secret-key-must-be-at-least-32-chars");
            newConfig.setAlgorithm("HS256");
            newConfig.setExpirationMinutes(30);
            newConfig.setRefreshExpirationDays(14);
            newConfig.setIssuer("jairouter-new");
            newConfig.setBlacklistEnabled(false);

            // When
            var result = configService.updateJwtConfig(newConfig);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(eventPublisher).publishEvent(any(SecurityConfigurationChangeEvent.class));
        }
    }

    // ==================== 获取当前配置测试 ====================

    @Nested
    @DisplayName("获取当前配置测试")
    class GetCurrentConfigurationTests {

        @Test
        @DisplayName("SEC-005: 获取当前配置成功")
        void testGetCurrentConfiguration_Success() {
            // When
            var result = configService.getCurrentConfiguration();

            // Then
            StepVerifier.create(result)
                .assertNext(props -> {
                    assertNotNull(props);
                    assertTrue(props.isEnabled());
                })
                .verifyComplete();
        }
    }

    // ==================== 配置验证测试 ====================

    @Nested
    @DisplayName("配置验证测试")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("SEC-006: 验证有效配置返回true")
        void testValidateConfiguration_Valid() {
            // Given
            SecurityProperties validProps = createValidSecurityProperties();

            // When
            var result = configService.validateConfiguration(validProps);

            // Then
            StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
        }

        @Test
        @DisplayName("SEC-007: JWT密钥过短返回false")
        void testValidateConfiguration_InvalidJwtSecret() {
            // Given
            SecurityProperties props = createValidSecurityProperties();
            props.getJwt().setSecret("short");

            // When
            var result = configService.validateConfiguration(props);

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        @DisplayName("SEC-008: JWT过期时间为0返回false")
        void testValidateConfiguration_InvalidJwtExpiration() {
            // Given
            SecurityProperties props = createValidSecurityProperties();
            props.getJwt().setExpirationMinutes(0);

            // When
            var result = configService.validateConfiguration(props);

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }

        @Test
        @DisplayName("SEC-009: 审计保留天数为0返回false")
        void testValidateConfiguration_InvalidAuditRetention() {
            // Given
            SecurityProperties props = createValidSecurityProperties();
            AuditConfig invalidAudit = new AuditConfig();
            invalidAudit.setRetentionDays(0);
            props.setAudit(invalidAudit);

            // When
            var result = configService.validateConfiguration(props);

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
        }
    }

    // ==================== 重新加载配置测试 ====================

    @Nested
    @DisplayName("重新加载配置测试")
    class ReloadConfigurationTests {

        @Test
        @DisplayName("SEC-010: 重新加载配置成功")
        void testReloadConfiguration_Success() {
            // When
            var result = configService.reloadConfiguration();

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(eventPublisher).publishEvent(any(SecurityConfigurationChangeEvent.class));
        }
    }

    // ==================== 配置历史测试 ====================

    @Nested
    @DisplayName("配置历史测试")
    class ConfigurationHistoryTests {

        @Test
        @DisplayName("SEC-011: 获取配置历史")
        void testGetConfigurationHistory() {
            // Given - 先触发一些配置变更
            configService.updateApiKeys(new ArrayList<>()).block();

            // When
            var result = configService.getConfigurationHistory(10);

            // Then
            StepVerifier.create(result)
                .assertNext(history -> {
                    assertNotNull(history);
                    assertFalse(history.isEmpty());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("SEC-012: 空历史返回空列表")
        void testGetConfigurationHistory_Empty() {
            // Given - 创建新实例，没有历史记录
            SecurityConfigurationServiceImpl newService = new SecurityConfigurationServiceImpl(
                securityProperties, storeManager, eventPublisher);

            // When
            var result = newService.getConfigurationHistory(10);

            // Then
            StepVerifier.create(result)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
        }
    }

    // ==================== 辅助方法 ====================

    private SecurityProperties createValidSecurityProperties() {
        SecurityProperties props = new SecurityProperties();
        props.setEnabled(true);

        // API Key 配置
        ApiKeyConfig apiKeyConfig = new ApiKeyConfig();
        apiKeyConfig.setEnabled(true);
        apiKeyConfig.setHeaderName("X-API-Key");
        apiKeyConfig.setKeys(new ArrayList<>());
        apiKeyConfig.setDefaultExpirationDays(30);
        props.setApiKey(apiKeyConfig);

        // JWT 配置
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret("valid-secret-key-must-be-at-least-32-chars");
        jwtConfig.setAlgorithm("HS512");
        jwtConfig.setExpirationMinutes(60);
        jwtConfig.setRefreshExpirationDays(7);
        jwtConfig.setIssuer("jairouter");
        jwtConfig.setBlacklistEnabled(true);
        props.setJwt(jwtConfig);

        // 脱敏配置
        SanitizationConfig sanitizationConfig = new SanitizationConfig();
        props.setSanitization(sanitizationConfig);

        // 审计配置
        AuditConfig auditConfig = new AuditConfig();
        auditConfig.setEnabled(true);
        auditConfig.setRetentionDays(90);
        props.setAudit(auditConfig);

        return props;
    }
}

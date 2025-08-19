package org.unreal.modelrouter.security.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.config.SecurityConfigurationService;
import org.unreal.modelrouter.security.config.SecurityConfigurationValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.monitoring.security.SecurityAlertService;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全配置管理的集成测试
 * 
 * 测试范围：
 * - 安全配置的动态更新和验证
 * - 配置备份和恢复功能
 * - 配置验证和错误处理
 * - 配置变更的事件通知
 * 
 * 需求覆盖：6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class SecurityConfigurationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SecurityConfigurationService securityConfigurationService;

    @Autowired
    private SecurityConfigurationValidator configurationValidator;

    @Autowired
    private SecurityAlertService securityAlertService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SecurityProperties securityProperties;

    private static final String TEST_API_KEY = "config-test-key-001";
    private static final String TEST_ENDPOINT = "/v1/chat/completions";
    private static final String CONFIG_ENDPOINT = "/v1/admin/security/config";

    @BeforeEach
    void setUp() {
        setupTestApiKey();
        setupInitialConfiguration();
    }

    /**
     * 测试安全配置的热更新功能
     * 需求：6.2 - 系统应当支持安全配置的热更新，无需重启服务
     */
    @Test
    void testHotConfigurationUpdate() {
        // 获取当前配置
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(currentConfig -> {
                    assertThat(currentConfig.getSanitization().getRequest().getSensitiveWords())
                            .contains("password", "secret");
                })
                .verifyComplete();

        // 准备新的配置
        SecurityProperties.SanitizationConfig newSanitizationConfig = 
                new SecurityProperties.SanitizationConfig();
        newSanitizationConfig.getRequest().setEnabled(true);
        newSanitizationConfig.getRequest().setSensitiveWords(
                List.of("password", "secret", "token", "hotUpdateTest"));
        newSanitizationConfig.getRequest().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));

        // 执行热更新
        StepVerifier.create(securityConfigurationService.updateSanitizationRules(newSanitizationConfig))
                .verifyComplete();

        // 验证新配置立即生效（无需重启）
        String requestWithNewSensitiveWord = """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "This contains hotUpdateTest word that should be sanitized"
                        }
                    ]
                }
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestWithNewSensitiveWord)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证新的敏感词被脱敏
                    assertThat(responseBody).doesNotContain("hotUpdateTest");
                });

        // 验证配置已持久化
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(updatedConfig -> {
                    assertThat(updatedConfig.getSanitization().getRequest().getSensitiveWords())
                            .contains("hotUpdateTest");
                })
                .verifyComplete();
    }

    /**
     * 测试API Key配置的动态更新
     * 需求：6.2 - 系统应当支持安全配置的热更新，无需重启服务
     */
    @Test
    void testDynamicApiKeyConfigurationUpdate() {
        // 创建新的API Key配置
        List<ApiKeyInfo> newApiKeys = List.of(
                ApiKeyInfo.builder()
                        .keyId("dynamic-key-001")
                        .keyValue("dynamic-test-key-001")
                        .description("动态添加的API密钥")
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusDays(30))
                        .enabled(true)
                        .permissions(List.of("read"))
                        .build(),
                ApiKeyInfo.builder()
                        .keyId("dynamic-key-002")
                        .keyValue("dynamic-test-key-002")
                        .description("另一个动态添加的API密钥")
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusDays(60))
                        .enabled(true)
                        .permissions(List.of("read", "write"))
                        .build()
        );

        // 执行API Key配置更新
        StepVerifier.create(securityConfigurationService.updateApiKeys(newApiKeys))
                .verifyComplete();

        // 验证新的API Key可以使用
        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", "dynamic-test-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", "dynamic-test-key-002")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createSimpleChatRequest())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试配置验证机制
     * 需求：6.3 - 系统应当提供安全配置的验证机制，防止无效配置
     */
    @Test
    void testConfigurationValidation() {
        // 测试无效的脱敏配置
        SecurityProperties.SanitizationConfig invalidConfig = 
                new SecurityProperties.SanitizationConfig();
        invalidConfig.getRequest().setEnabled(true);
        invalidConfig.getRequest().setSensitiveWords(null); // 无效：null值
        invalidConfig.getRequest().setPiiPatterns(List.of("[")); // 无效：错误的正则表达式

        // 验证配置验证器能够检测到错误
        StepVerifier.create(configurationValidator.validateSanitizationConfig(invalidConfig))
                .expectError()
                .verify();

        // 测试无效的API Key配置
        List<ApiKeyInfo> invalidApiKeys = List.of(
                ApiKeyInfo.builder()
                        .keyId("") // 无效：空ID
                        .keyValue("test-key")
                        .build(),
                ApiKeyInfo.builder()
                        .keyId("valid-id")
                        .keyValue("") // 无效：空值
                        .build()
        );

        StepVerifier.create(configurationValidator.validateApiKeys(invalidApiKeys))
                .expectError()
                .verify();
    }

    /**
     * 测试配置备份和恢复功能
     * 需求：6.5 - 系统应当提供安全配置的备份和恢复功能
     */
    @Test
    void testConfigurationBackupAndRestore() {
        // 获取当前配置作为备份点
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(originalConfig -> {
                    // 创建配置备份
                    StepVerifier.create(securityConfigurationService.createConfigurationBackup("test-backup"))
                            .verifyComplete();

                    // 修改配置
                    SecurityProperties.SanitizationConfig modifiedConfig = 
                            new SecurityProperties.SanitizationConfig();
                    modifiedConfig.getRequest().setEnabled(true);
                    modifiedConfig.getRequest().setSensitiveWords(List.of("modified", "test"));

                    StepVerifier.create(securityConfigurationService.updateSanitizationRules(modifiedConfig))
                            .verifyComplete();

                    // 验证配置已修改
                    StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                            .assertNext(modifiedConfigResult -> {
                                assertThat(modifiedConfigResult.getSanitization().getRequest().getSensitiveWords())
                                        .contains("modified", "test");
                            })
                            .verifyComplete();

                    // 恢复配置
                    StepVerifier.create(securityConfigurationService.restoreConfigurationBackup("test-backup"))
                            .verifyComplete();

                    // 验证配置已恢复
                    StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                            .assertNext(restoredConfig -> {
                                assertThat(restoredConfig.getSanitization().getRequest().getSensitiveWords())
                                        .isEqualTo(originalConfig.getSanitization().getRequest().getSensitiveWords());
                            })
                            .verifyComplete();
                })
                .verifyComplete();
    }

    /**
     * 测试不同环境的差异化配置
     * 需求：6.4 - 系统应当支持不同环境（开发、测试、生产）的差异化安全配置
     */
    @Test
    void testEnvironmentSpecificConfiguration() {
        // 模拟开发环境配置
        SecurityProperties devConfig = new SecurityProperties();
        devConfig.setEnabled(true);
        devConfig.getApiKey().setEnabled(false); // 开发环境可能禁用API Key
        devConfig.getSanitization().getRequest().setEnabled(false); // 开发环境可能禁用脱敏
        devConfig.getAudit().setEnabled(true);

        // 模拟生产环境配置
        SecurityProperties prodConfig = new SecurityProperties();
        prodConfig.setEnabled(true);
        prodConfig.getApiKey().setEnabled(true); // 生产环境启用API Key
        prodConfig.getSanitization().getRequest().setEnabled(true); // 生产环境启用脱敏
        prodConfig.getSanitization().getRequest().setSensitiveWords(
                List.of("password", "secret", "token", "key", "credential"));
        prodConfig.getAudit().setEnabled(true);

        // 测试应用开发环境配置
        StepVerifier.create(securityConfigurationService.applyEnvironmentConfiguration("development", devConfig))
                .verifyComplete();

        // 验证开发环境配置生效
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(currentConfig -> {
                    assertThat(currentConfig.getApiKey().isEnabled()).isFalse();
                    assertThat(currentConfig.getSanitization().getRequest().isEnabled()).isFalse();
                })
                .verifyComplete();

        // 测试应用生产环境配置
        StepVerifier.create(securityConfigurationService.applyEnvironmentConfiguration("production", prodConfig))
                .verifyComplete();

        // 验证生产环境配置生效
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(currentConfig -> {
                    assertThat(currentConfig.getApiKey().isEnabled()).isTrue();
                    assertThat(currentConfig.getSanitization().getRequest().isEnabled()).isTrue();
                    assertThat(currentConfig.getSanitization().getRequest().getSensitiveWords())
                            .contains("credential");
                })
                .verifyComplete();
    }

    /**
     * 测试敏感配置信息的加密存储
     * 需求：6.6 - 系统应当对敏感配置信息进行加密存储
     */
    @Test
    void testSensitiveConfigurationEncryption() {
        // 创建包含敏感信息的配置
        SecurityProperties.JwtConfig jwtConfig = new SecurityProperties.JwtConfig();
        jwtConfig.setEnabled(true);
        jwtConfig.setSecret("super-secret-jwt-key-that-should-be-encrypted");
        jwtConfig.setAlgorithm("HS256");

        // 更新JWT配置
        StepVerifier.create(securityConfigurationService.updateJwtConfiguration(jwtConfig))
                .verifyComplete();

        // 验证敏感信息在存储中是加密的
        StepVerifier.create(securityConfigurationService.getEncryptedConfigurationData())
                .assertNext(encryptedData -> {
                    // 验证原始密钥不会以明文形式出现在加密数据中
                    assertThat(encryptedData).doesNotContain("super-secret-jwt-key-that-should-be-encrypted");
                    // 验证存在加密标识
                    assertThat(encryptedData).contains("encrypted:");
                })
                .verifyComplete();

        // 验证解密后的配置是正确的
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(currentConfig -> {
                    assertThat(currentConfig.getJwt().getSecret())
                            .isEqualTo("super-secret-jwt-key-that-should-be-encrypted");
                })
                .verifyComplete();
    }

    /**
     * 测试配置变更的事件通知机制
     * 需求：6.2 - 系统应当支持安全配置的热更新，无需重启服务
     */
    @Test
    void testConfigurationChangeEventNotification() {
        // 监听配置变更事件
        StepVerifier.create(securityConfigurationService.getConfigurationChangeEvents())
                .then(() -> {
                    // 触发配置变更
                    SecurityProperties.SanitizationConfig newConfig = 
                            new SecurityProperties.SanitizationConfig();
                    newConfig.getRequest().setEnabled(true);
                    newConfig.getRequest().setSensitiveWords(List.of("eventTest"));

                    securityConfigurationService.updateSanitizationRules(newConfig).subscribe();
                })
                .assertNext(changeEvent -> {
                    assertThat(changeEvent.getEventType()).isEqualTo("SANITIZATION_CONFIG_UPDATED");
                    assertThat(changeEvent.getTimestamp()).isNotNull();
                    assertThat(changeEvent.getChangedProperties()).contains("sensitiveWords");
                })
                .thenCancel()
                .verify();
    }

    /**
     * 测试配置回滚机制
     */
    @Test
    void testConfigurationRollback() {
        // 记录初始配置
        StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                .assertNext(initialConfig -> {
                    // 应用错误的配置更新
                    SecurityProperties.SanitizationConfig badConfig = 
                            new SecurityProperties.SanitizationConfig();
                    badConfig.getRequest().setEnabled(true);
                    badConfig.getRequest().setSensitiveWords(List.of("badConfig"));

                    StepVerifier.create(securityConfigurationService.updateSanitizationRules(badConfig))
                            .verifyComplete();

                    // 验证错误配置已应用
                    StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                            .assertNext(badConfigResult -> {
                                assertThat(badConfigResult.getSanitization().getRequest().getSensitiveWords())
                                        .contains("badConfig");
                            })
                            .verifyComplete();

                    // 执行配置回滚
                    StepVerifier.create(securityConfigurationService.rollbackToPreviousConfiguration())
                            .verifyComplete();

                    // 验证配置已回滚
                    StepVerifier.create(securityConfigurationService.getCurrentConfiguration())
                            .assertNext(rolledBackConfig -> {
                                assertThat(rolledBackConfig.getSanitization().getRequest().getSensitiveWords())
                                        .isEqualTo(initialConfig.getSanitization().getRequest().getSensitiveWords());
                            })
                            .verifyComplete();
                })
                .verifyComplete();
    }

    private void setupTestApiKey() {
        ApiKeyInfo testKey = ApiKeyInfo.builder()
                .keyId("config-test-key-001")
                .keyValue(TEST_API_KEY)
                .description("配置测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read", "write"))
                .build();

        StepVerifier.create(apiKeyService.saveApiKey(testKey))
                .expectNext(testKey)
                .verifyComplete();
    }

    private void setupInitialConfiguration() {
        // 设置初始安全配置
        securityProperties.setEnabled(true);
        securityProperties.getApiKey().setEnabled(true);
        securityProperties.getSanitization().getRequest().setEnabled(true);
        securityProperties.getSanitization().getRequest().setSensitiveWords(
                List.of("password", "secret", "token"));
        securityProperties.getSanitization().getRequest().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        securityProperties.getAudit().setEnabled(true);
    }

    private String createSimpleChatRequest() {
        return """
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "Hello, world!"
                        }
                    ],
                    "max_tokens": 100
                }
                """;
    }
}
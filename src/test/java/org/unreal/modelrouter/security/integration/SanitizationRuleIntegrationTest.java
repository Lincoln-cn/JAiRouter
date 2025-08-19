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
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;
import org.unreal.modelrouter.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.sanitization.SanitizationService;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏规则组合和策略的集成测试
 * 
 * 测试范围：
 * - 不同脱敏策略的效果验证
 * - 脱敏规则的优先级处理
 * - 复杂规则组合的正确性
 * - 自定义脱敏规则的应用
 * 
 * 需求覆盖：4.3, 4.4, 4.5, 5.3, 5.4
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class SanitizationRuleIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SanitizationService sanitizationService;

    @Autowired
    private SanitizationRuleEngine ruleEngine;

    @Autowired
    private SecurityProperties securityProperties;

    private static final String TEST_API_KEY = "test-api-key-001";
    private static final String TEST_ENDPOINT = "/v1/chat/completions";

    @BeforeEach
    void setUp() {
        setupTestApiKey();
        setupSanitizationRules();
    }

    /**
     * 测试掩码策略的脱敏效果
     * 需求：4.5 - 系统应当支持不同类型数据的不同脱敏策略（掩码、替换、删除）
     */
    @Test
    void testMaskingSanitizationStrategy() {
        String testContent = "My phone number is 13812345678 and email is user@example.com";

        StepVerifier.create(sanitizationService.sanitizeRequest(testContent, "text/plain"))
                .assertNext(sanitizedContent -> {
                    // 验证手机号被掩码处理
                    assertThat(sanitizedContent).contains("138****5678");
                    // 验证邮箱被掩码处理
                    assertThat(sanitizedContent).contains("****@example.com");
                    // 验证原始数据不存在
                    assertThat(sanitizedContent).doesNotContain("13812345678");
                    assertThat(sanitizedContent).doesNotContain("user@example.com");
                })
                .verifyComplete();
    }

    /**
     * 测试替换策略的脱敏效果
     * 需求：4.5 - 系统应当支持不同类型数据的不同脱敏策略（掩码、替换、删除）
     */
    @Test
    void testReplacementSanitizationStrategy() {
        // 创建替换策略的规则
        SanitizationRule replaceRule = SanitizationRule.builder()
                .ruleId("replace-password-rule")
                .name("密码替换规则")
                .description("将密码替换为占位符")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("text/plain", "application/json"))
                .build();

        String testContent = "My password is secret123";

        StepVerifier.create(ruleEngine.applySanitizationRules(testContent, List.of(replaceRule)))
                .assertNext(sanitizedContent -> {
                    // 验证敏感词被替换
                    assertThat(sanitizedContent).contains("[REDACTED]");
                    assertThat(sanitizedContent).doesNotContain("password");
                })
                .verifyComplete();
    }

    /**
     * 测试删除策略的脱敏效果
     * 需求：4.5 - 系统应当支持不同类型数据的不同脱敏策略（掩码、替换、删除）
     */
    @Test
    void testRemovalSanitizationStrategy() {
        // 创建删除策略的规则
        SanitizationRule removeRule = SanitizationRule.builder()
                .ruleId("remove-token-rule")
                .name("令牌删除规则")
                .description("完全删除令牌信息")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("token:\\s*\\w+")
                .strategy(SanitizationStrategy.REMOVE)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("text/plain", "application/json"))
                .build();

        String testContent = "Authentication token: abc123def456 is required";

        StepVerifier.create(ruleEngine.applySanitizationRules(testContent, List.of(removeRule)))
                .assertNext(sanitizedContent -> {
                    // 验证令牌信息被完全删除
                    assertThat(sanitizedContent).doesNotContain("token: abc123def456");
                    assertThat(sanitizedContent).doesNotContain("abc123def456");
                    // 验证其他内容保留
                    assertThat(sanitizedContent).contains("Authentication");
                    assertThat(sanitizedContent).contains("is required");
                })
                .verifyComplete();
    }

    /**
     * 测试哈希策略的脱敏效果
     * 需求：4.5 - 系统应当支持不同类型数据的不同脱敏策略（掩码、替换、删除）
     */
    @Test
    void testHashSanitizationStrategy() {
        // 创建哈希策略的规则
        SanitizationRule hashRule = SanitizationRule.builder()
                .ruleId("hash-id-rule")
                .name("身份证号哈希规则")
                .description("将身份证号转换为哈希值")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{18}")
                .strategy(SanitizationStrategy.HASH)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("text/plain", "application/json"))
                .build();

        String testContent = "ID number: 123456789012345678";

        StepVerifier.create(ruleEngine.applySanitizationRules(testContent, List.of(hashRule)))
                .assertNext(sanitizedContent -> {
                    // 验证身份证号被哈希处理
                    assertThat(sanitizedContent).doesNotContain("123456789012345678");
                    assertThat(sanitizedContent).contains("HASH:");
                    // 验证哈希值的格式
                    assertThat(sanitizedContent).containsPattern("HASH:[a-f0-9]{8}");
                })
                .verifyComplete();
    }

    /**
     * 测试脱敏规则的优先级处理
     * 需求：4.4 - 系统应当支持正则表达式模式匹配进行数据识别
     */
    @Test
    void testSanitizationRulePriority() {
        // 创建不同优先级的规则
        SanitizationRule highPriorityRule = SanitizationRule.builder()
                .ruleId("high-priority-rule")
                .name("高优先级规则")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("secret")
                .strategy(SanitizationStrategy.REPLACE)
                .priority(1) // 高优先级
                .enabled(true)
                .build();

        SanitizationRule lowPriorityRule = SanitizationRule.builder()
                .ruleId("low-priority-rule")
                .name("低优先级规则")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("secret\\w+")
                .strategy(SanitizationStrategy.MASK)
                .priority(10) // 低优先级
                .enabled(true)
                .build();

        String testContent = "My secret123 is confidential";

        StepVerifier.create(ruleEngine.applySanitizationRules(testContent, 
                List.of(lowPriorityRule, highPriorityRule))) // 故意颠倒顺序
                .assertNext(sanitizedContent -> {
                    // 高优先级规则应该先执行，所以应该是替换而不是掩码
                    assertThat(sanitizedContent).contains("[REDACTED]");
                    assertThat(sanitizedContent).doesNotContain("secret");
                })
                .verifyComplete();
    }

    /**
     * 测试复杂规则组合的正确性
     * 需求：4.3 - 系统应当支持可配置的敏感词词库
     */
    @Test
    void testComplexRuleCombination() {
        String complexContent = """
                User profile:
                - Name: John Doe
                - Email: john.doe@example.com
                - Phone: 13812345678
                - Password: secret123
                - ID: 123456789012345678
                - Token: abc123def456
                """;

        webTestClient.post()
                .uri(TEST_ENDPOINT)
                .header("X-API-Key", TEST_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createChatRequest(complexContent))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(response -> {
                    String responseBody = new String(response.getResponseBody());
                    // 验证所有敏感信息都被正确处理
                    assertThat(responseBody).doesNotContain("john.doe@example.com");
                    assertThat(responseBody).doesNotContain("13812345678");
                    assertThat(responseBody).doesNotContain("secret123");
                    assertThat(responseBody).doesNotContain("123456789012345678");
                    assertThat(responseBody).doesNotContain("abc123def456");
                });
    }

    /**
     * 测试自定义正则表达式规则
     * 需求：4.4 - 系统应当支持正则表达式模式匹配进行数据识别
     */
    @Test
    void testCustomRegexRules() {
        // 创建自定义正则表达式规则
        SanitizationRule customRegexRule = SanitizationRule.builder()
                .ruleId("custom-regex-rule")
                .name("自定义银行卡号规则")
                .description("匹配银行卡号格式")
                .type(RuleType.CUSTOM_REGEX)
                .pattern("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("text/plain", "application/json"))
                .build();

        String testContent = "My bank card number is 1234 5678 9012 3456";

        StepVerifier.create(ruleEngine.applySanitizationRules(testContent, List.of(customRegexRule)))
                .assertNext(sanitizedContent -> {
                    // 验证银行卡号被掩码处理
                    assertThat(sanitizedContent).doesNotContain("1234 5678 9012 3456");
                    assertThat(sanitizedContent).contains("1234 **** **** 3456");
                })
                .verifyComplete();
    }

    /**
     * 测试不同内容类型的规则应用
     * 需求：5.3 - 系统应当支持可配置的响应脱敏规则
     */
    @Test
    void testContentTypeSpecificRules() {
        // 创建仅适用于JSON的规则
        SanitizationRule jsonOnlyRule = SanitizationRule.builder()
                .ruleId("json-only-rule")
                .name("仅JSON适用规则")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("apiKey")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .build();

        String jsonContent = "{\"apiKey\": \"secret123\", \"data\": \"test\"}";
        String textContent = "apiKey: secret123, data: test";

        // 测试JSON内容 - 应该应用规则
        StepVerifier.create(ruleEngine.applySanitizationRules(jsonContent, List.of(jsonOnlyRule)))
                .assertNext(sanitizedContent -> {
                    assertThat(sanitizedContent).doesNotContain("apiKey");
                    assertThat(sanitizedContent).contains("[REDACTED]");
                })
                .verifyComplete();

        // 测试纯文本内容 - 不应该应用规则
        StepVerifier.create(ruleEngine.applySanitizationRules(textContent, List.of(jsonOnlyRule)))
                .assertNext(sanitizedContent -> {
                    assertThat(sanitizedContent).contains("apiKey");
                    assertThat(sanitizedContent).doesNotContain("[REDACTED]");
                })
                .verifyComplete();
    }

    /**
     * 测试禁用规则的处理
     * 需求：5.4 - 系统应当保持响应数据的结构完整性
     */
    @Test
    void testDisabledRuleHandling() {
        // 创建禁用的规则
        SanitizationRule disabledRule = SanitizationRule.builder()
                .ruleId("disabled-rule")
                .name("禁用的规则")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(false) // 禁用规则
                .priority(1)
                .build();

        String testContent = "My password is secret123";

        StepVerifier.create(ruleEngine.applySanitizationRules(testContent, List.of(disabledRule)))
                .assertNext(sanitizedContent -> {
                    // 禁用的规则不应该被应用
                    assertThat(sanitizedContent).contains("password");
                    assertThat(sanitizedContent).contains("secret123");
                    assertThat(sanitizedContent).doesNotContain("[REDACTED]");
                })
                .verifyComplete();
    }

    /**
     * 测试规则引擎的性能
     */
    @Test
    void testRuleEnginePerformance() {
        // 创建多个规则
        List<SanitizationRule> rules = List.of(
                createRule("rule1", "password", SanitizationStrategy.MASK, 1),
                createRule("rule2", "\\d{11}", SanitizationStrategy.MASK, 2),
                createRule("rule3", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", SanitizationStrategy.MASK, 3),
                createRule("rule4", "secret", SanitizationStrategy.REPLACE, 4),
                createRule("rule5", "token", SanitizationStrategy.REMOVE, 5)
        );

        String complexContent = "My password is secret123, phone is 13812345678, email is user@example.com, secret token is abc123";

        int iterations = 1000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            StepVerifier.create(ruleEngine.applySanitizationRules(complexContent, rules))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;

        // 平均每次处理时间应该小于10ms
        assertThat(avgTime).isLessThan(10.0);
    }

    private void setupTestApiKey() {
        ApiKeyInfo testKey = ApiKeyInfo.builder()
                .keyId("test-key-001")
                .keyValue(TEST_API_KEY)
                .description("测试API密钥")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .enabled(true)
                .permissions(List.of("read", "write"))
                .build();

        StepVerifier.create(apiKeyService.saveApiKey(testKey))
                .expectNext(testKey)
                .verifyComplete();
    }

    private void setupSanitizationRules() {
        // 配置基础脱敏规则
        securityProperties.getSanitization().getRequest().setEnabled(true);
        securityProperties.getSanitization().getRequest().setSensitiveWords(
                List.of("password", "secret", "token"));
        securityProperties.getSanitization().getRequest().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "\\d{18}"));
        securityProperties.getSanitization().getRequest().setMaskingChar("*");

        securityProperties.getSanitization().getResponse().setEnabled(true);
        securityProperties.getSanitization().getResponse().setSensitiveWords(
                List.of("internal", "debug"));
        securityProperties.getSanitization().getResponse().setPiiPatterns(
                List.of("\\d{11}", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
    }

    private SanitizationRule createRule(String id, String pattern, SanitizationStrategy strategy, int priority) {
        return SanitizationRule.builder()
                .ruleId(id)
                .name("测试规则 " + id)
                .type(RuleType.CUSTOM_REGEX)
                .pattern(pattern)
                .strategy(strategy)
                .enabled(true)
                .priority(priority)
                .applicableContentTypes(List.of("text/plain", "application/json"))
                .build();
    }

    private String createChatRequest(String content) {
        return String.format("""
                {
                    "model": "gpt-3.5-turbo",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": 100
                }
                """, content.replace("\"", "\\\"").replace("\n", "\\n"));
    }
}
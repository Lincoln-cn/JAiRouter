package org.unreal.modelrouter.auth.security.sanitization.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationRuleEngine;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.auth.security.model.SanitizationStrategy;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultSanitizationRuleEngine 单元测试
 */
class DefaultSanitizationRuleEngineTest {
    
    private DefaultSanitizationRuleEngine ruleEngine;
    
    @BeforeEach
    void setUp() {
        ruleEngine = new DefaultSanitizationRuleEngine();
    }
    
    @Test
    void testApplySanitizationRules_EmptyContent() {
        List<SanitizationRule> rules = Collections.emptyList();
        
        StepVerifier.create(ruleEngine.applySanitizationRules("", rules, "application/json"))
                .expectNext("")
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_NullContent() {
        List<SanitizationRule> rules = Collections.emptyList();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(null, rules, "application/json"))
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_EmptyRules() {
        String content = "This is test content";
        List<SanitizationRule> rules = Collections.emptyList();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, rules, "application/json"))
                .expectNext(content)
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_MaskStrategy() {
        String content = "My password is secret123";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("mask-password")
                .name("Mask Password")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(rule), "application/json"))
                .expectNext("My ******** is secret123")
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_ReplaceStrategy() {
        String content = "Call me at 13812345678";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("replace-phone")
                .name("Replace Phone")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementText("[PHONE]")
                .build();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(rule), "application/json"))
                .expectNext("Call me at [PHONE]")
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_RemoveStrategy() {
        String content = "Email: user@example.com for contact";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("remove-email")
                .name("Remove Email")
                .type(RuleType.PII_PATTERN)
                .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .strategy(SanitizationStrategy.REMOVE)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .build();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(rule), "application/json"))
                .expectNext("Email:  for contact")
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_HashStrategy() {
        String content = "ID: 123456789012345678";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("hash-id")
                .name("Hash ID")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{18}")
                .strategy(SanitizationStrategy.HASH)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .build();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(rule), "application/json"))
                .assertNext(result -> {
                    assertTrue(result.startsWith("ID: [HASH:"));
                    assertTrue(result.endsWith("]"));
                    assertTrue(result.contains("[HASH:"));
                })
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_MultiplePriorities() {
        String content = "password: secret123, phone: 13812345678";
        
        SanitizationRule highPriorityRule = SanitizationRule.builder()
                .ruleId("high-priority")
                .name("High Priority")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();
        
        SanitizationRule lowPriorityRule = SanitizationRule.builder()
                .ruleId("low-priority")
                .name("Low Priority")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementText("[PHONE]")
                .build();
        
        List<SanitizationRule> rules = Arrays.asList(lowPriorityRule, highPriorityRule); // 故意颠倒顺序
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, rules, "application/json"))
                .expectNext("********: secret123, phone: [PHONE]")
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_DisabledRule() {
        String content = "password is secret";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("disabled-rule")
                .name("Disabled Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(false) // 禁用规则
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(rule), "application/json"))
                .expectNext(content) // 应该保持原样
                .verifyComplete();
    }
    
    @Test
    void testApplySanitizationRules_ContentTypeNotApplicable() {
        String content = "password is secret";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("json-only-rule")
                .name("JSON Only Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();
        
        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(rule), "text/plain"))
                .expectNext(content) // 应该保持原样
                .verifyComplete();
    }
    
    @Test
    void testValidateRule_ValidRule() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("valid-rule")
                .name("Valid Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .build();
        
        StepVerifier.create(ruleEngine.validateRule(rule))
                .expectNext(true)
                .verifyComplete();
    }
    
    @Test
    void testValidateRule_NullRule() {
        StepVerifier.create(ruleEngine.validateRule(null))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testValidateRule_EmptyRuleId() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("")
                .name("Invalid Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .build();
        
        StepVerifier.create(ruleEngine.validateRule(rule))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testValidateRule_EmptyPattern() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("invalid-rule")
                .name("Invalid Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("")
                .strategy(SanitizationStrategy.MASK)
                .build();
        
        StepVerifier.create(ruleEngine.validateRule(rule))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testValidateRule_InvalidRegex() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("invalid-regex-rule")
                .name("Invalid Regex Rule")
                .type(RuleType.CUSTOM_REGEX)
                .pattern("[invalid-regex")
                .strategy(SanitizationStrategy.MASK)
                .build();
        
        StepVerifier.create(ruleEngine.validateRule(rule))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testCompileRules() {
        SanitizationRule rule1 = SanitizationRule.builder()
                .ruleId("rule1")
                .name("Rule 1")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .build();
        
        SanitizationRule rule2 = SanitizationRule.builder()
                .ruleId("rule2")
                .name("Rule 2")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.REPLACE)
                .build();
        
        List<SanitizationRule> rules = Arrays.asList(rule1, rule2);
        
        StepVerifier.create(ruleEngine.compileRules(rules))
                .verifyComplete();
    }
    
    @Test
    void testGetRuleMatchCount_InitiallyZero() {
        StepVerifier.create(ruleEngine.getRuleMatchCount("non-existent-rule"))
                .expectNext(0L)
                .verifyComplete();
    }
    
    @Test
    void testGetRuleMatchCount_AfterMatch() {
        String content = "password is secret";
        
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("match-count-rule")
                .name("Match Count Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();
        
        // 应用规则
        Mono<String> sanitizationResult = ruleEngine.applySanitizationRules(content, List.of(rule), "application/json");
        Mono<Long> matchCountResult = sanitizationResult.then(ruleEngine.getRuleMatchCount("match-count-rule"));
        
        StepVerifier.create(matchCountResult)
                .expectNext(1L)
                .verifyComplete();
    }
    
    @Test
    void testClearCompiledPatterns() {
        // 这个测试主要验证方法不会抛出异常
        assertDoesNotThrow(() -> ruleEngine.clearCompiledPatterns());
    }
    
    @Test
    void testResetMatchCounts() {
        // 这个测试主要验证方法不会抛出异常
        assertDoesNotThrow(() -> ruleEngine.resetMatchCounts());
    }
    
    @Test
    void testGetAllMatchCounts() {
        // 初始状态应该为空
        assertTrue(ruleEngine.getAllMatchCounts().isEmpty());
    }

    // ========== JSON 结构保护脱敏测试 ==========

    @Test
    void testJsonAware_NumberLiteralsPreserved() {
        // 模拟 /api/dashboard/metrics 的 system 段响应
        String json = "{\"system\":{\"availableProcessors\":8,"
                + "\"systemLoadAverage\":1.228515625,"
                + "\"processCpuUsage\":0.12345678901234567,"
                + "\"systemCpuUsage\":0.98765432109876543,"
                + "\"startTime\":1.7398E9,"
                + "\"openFiles\":0.0,"
                + "\"uptimeSeconds\":964.057,"
                + "\"maxFiles\":0.0}}";

        SanitizationRule piiRule = SanitizationRule.builder()
                .ruleId("response-pii-phone")
                .name("手机号")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(json, List.of(piiRule), "application/json", true))
                .assertNext(result -> {
                    // 数值字面量必须原样保留
                    assertTrue(result.contains("\"systemLoadAverage\":1.228515625"),
                            "浮点数值不应被遮蔽");
                    assertTrue(result.contains("\"processCpuUsage\":0.12345678901234567"),
                            "长小数不应被遮蔽");
                    assertTrue(result.contains("\"systemCpuUsage\":0.98765432109876543"),
                            "长小数不应被遮蔽");
                    assertTrue(result.contains("\"startTime\":1.7398E9"),
                            "科学计数法不应被遮蔽");
                    assertTrue(result.contains("\"openFiles\":0.0"),
                            "零值浮点数不应被遮蔽");
                    assertTrue(result.contains("\"uptimeSeconds\":964.057"),
                            "正常浮点数不应被遮蔽");
                    // 输出必须包含完整的 JSON 结构字符
                    assertTrue(result.startsWith("{") && result.endsWith("}"),
                            "输出应保持JSON对象结构");
                })
                .verifyComplete();
    }

    @Test
    void testJsonAware_StringPhoneNumberMasked() {
        String json = "{\"user\":{\"name\":\"张三\",\"phone\":\"13800138000\",\"email\":\"test@example.com\"}}";

        SanitizationRule phoneRule = SanitizationRule.builder()
                .ruleId("response-pii-phone")
                .name("手机号")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        SanitizationRule emailRule = SanitizationRule.builder()
                .ruleId("response-pii-email")
                .name("邮箱")
                .type(RuleType.PII_PATTERN)
                .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(json,
                        List.of(phoneRule, emailRule), "application/json", true))
                .assertNext(result -> {
                    // 手机号应被遮蔽
                    assertFalse(result.contains("13800138000"), "手机号应被遮蔽");
                    assertTrue(result.contains("***********"), "手机号应被替换为星号");
                    // 邮箱应被遮蔽
                    assertFalse(result.contains("test@example.com"), "邮箱应被遮蔽");
                    // 非敏感字符串应保留
                    assertTrue(result.contains("张三"), "非敏感字符串不应被遮蔽");
                    // 结构完好
                    assertTrue(result.contains("\"phone\":\""), "键名应保留");
                })
                .verifyComplete();
    }

    @Test
    void testJsonAware_KeysNotMasked() {
        String json = "{\"password\":\"secret123\",\"account\":\"admin\"}";

        SanitizationRule wordRule = SanitizationRule.builder()
                .ruleId("response-sensitive-word-password")
                .name("敏感词password")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(json, List.of(wordRule), "application/json", true))
                .assertNext(result -> {
                    // 键名 "password" 不应被遮蔽（否则 JSON 结构被破坏）
                    assertTrue(result.contains("\"password\""), "JSON键名不应被遮蔽");
                    // 值 "secret123" 中不含 "password"，所以不应被遮蔽
                    assertTrue(result.contains("secret123"), "不含敏感词的值应保留");
                    // 键名 "account" 不应被遮蔽
                    assertTrue(result.contains("\"account\""), "JSON键名不应被遮蔽");
                })
                .verifyComplete();
    }

    @Test
    void testJsonAware_DisabledWhenPreserveJsonStructureFalse() {
        // 当 preserveJsonStructure=false 时，走原有的全文匹配逻辑
        String json = "{\"value\":0.12345678901234567}";

        SanitizationRule piiRule = SanitizationRule.builder()
                .ruleId("response-pii-phone")
                .name("手机号")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(json, List.of(piiRule), "application/json", false))
                .assertNext(result -> {
                    // preserveJsonStructure=false, 全文匹配会遮蔽数值中的11位数字
                    assertFalse(result.contains("0.12345678901234567"),
                            "preserveJsonStructure=false时数值中的匹配应被遮蔽");
                })
                .verifyComplete();
    }

    @Test
    void testJsonAware_ValidJsonOutput() {
        // 综合测试：完整的 dashboard metrics 响应样例
        String json = "{\"timestamp\":1739856000000,"
                + "\"system\":{\"availableProcessors\":8,"
                + "\"systemLoadAverage\":1.228515625,"
                + "\"processCpuUsage\":0.17748011299480112,"
                + "\"systemCpuUsage\":0.23577523785775237,"
                + "\"startTime\":1.739856E9,"
                + "\"openFiles\":0.0,\"uptimeSeconds\":964.057,\"maxFiles\":0.0},"
                + "\"contact\":{\"phone\":\"13800138000\",\"email\":\"admin@example.com\"}}";

        SanitizationRule phoneRule = SanitizationRule.builder()
                .ruleId("response-pii-phone")
                .name("手机号")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        SanitizationRule emailRule = SanitizationRule.builder()
                .ruleId("response-pii-email")
                .name("邮箱")
                .type(RuleType.PII_PATTERN)
                .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(json,
                        List.of(phoneRule, emailRule), "application/json", true))
                .assertNext(result -> {
                    // system 段数值必须保留
                    assertTrue(result.contains("\"processCpuUsage\":0.17748011299480112"));
                    assertTrue(result.contains("\"systemCpuUsage\":0.23577523785775237"));
                    assertTrue(result.contains("\"systemLoadAverage\":1.228515625"));
                    // contact 段敏感信息应被遮蔽
                    assertFalse(result.contains("13800138000"), "手机号应被遮蔽");
                    assertFalse(result.contains("admin@example.com"), "邮箱应被遮蔽");
                    // 结果应保持完整 JSON 结构
                    assertTrue(result.startsWith("{") && result.endsWith("}"),
                            "输出应保持JSON对象结构");
                    assertTrue(result.contains("\"system\":{"), "嵌套对象结构应保留");
                    assertTrue(result.contains("\"contact\":{"), "嵌套对象结构应保留");
                })
                .verifyComplete();
    }
}
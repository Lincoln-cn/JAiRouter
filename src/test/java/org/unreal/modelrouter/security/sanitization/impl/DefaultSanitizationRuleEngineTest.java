package org.unreal.modelrouter.security.sanitization.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;
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
}
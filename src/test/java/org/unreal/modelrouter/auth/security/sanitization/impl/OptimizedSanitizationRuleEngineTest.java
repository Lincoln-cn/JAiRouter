package org.unreal.modelrouter.auth.security.sanitization.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.sanitization.impl.OptimizedSanitizationRuleEngine;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.auth.security.model.SanitizationStrategy;
import org.unreal.modelrouter.common.util.SafeRegexValidator;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimizedSanitizationRuleEngineTest {

    private OptimizedSanitizationRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new OptimizedSanitizationRuleEngine();
    }

    @Test
    void testValidateRule_UnsafeRedosPatternRejected() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("unsafe-redos")
                .name("Unsafe ReDoS")
                .type(RuleType.CUSTOM_REGEX)
                .pattern("(a+)+$")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .build();

        StepVerifier.create(ruleEngine.validateRule(rule))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testCompileRules_UnsafePatternNotApplied() {
        String content = "aaaa";

        SanitizationRule unsafeRule = SanitizationRule.builder()
                .ruleId("unsafe-redos")
                .name("Unsafe ReDoS")
                .type(RuleType.CUSTOM_REGEX)
                .pattern("(a+)+$")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json"))
                .replacementText("[REDACTED]")
                .build();

        ruleEngine.compileRules(List.of(unsafeRule)).block();

        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(unsafeRule), "application/json"))
                .expectNext(content)
                .verifyComplete();
    }

    @Test
    void testApplySanitizationRules_SafePiiPatternStillMasks() {
        String content = "Phone: 13812345678";

        SanitizationRule phoneRule = SanitizationRule.builder()
                .ruleId("phone-mask")
                .name("Phone Mask")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("text/plain"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(phoneRule), "text/plain"))
                .expectNext("Phone: ***********")
                .verifyComplete();
    }

    @Test
    void testApplySanitizationRules_SensitiveWordUnaffected() {
        String content = "My password is secret";

        SanitizationRule wordRule = SanitizationRule.builder()
                .ruleId("sensitive-word")
                .name("Sensitive Word")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("text/plain"))
                .replacementChar("*")
                .build();

        StepVerifier.create(ruleEngine.applySanitizationRules(content, List.of(wordRule), "text/plain"))
                .expectNext("My ******** is secret")
                .verifyComplete();
    }

    @Test
    void testValidateRule_SafePiiPatternAccepted() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("phone-rule")
                .name("Phone Rule")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{11}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .build();

        StepVerifier.create(ruleEngine.validateRule(rule))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testShippedDefaultPatternsPassSafetyCheck() {
        assertTrue(SafeRegexValidator.isSafeUserPattern("\\d{11}"),
                "手机号模式应通过安全校验");
        assertTrue(SafeRegexValidator.isSafeUserPattern(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                "邮箱模式应通过安全校验");
        assertTrue(SafeRegexValidator.isSafeUserPattern(
                "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
                "银行卡模式应通过安全校验");
    }
}

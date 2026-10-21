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
        assertTrue(SafeRegexValidator.isSafeUserPattern("(?<!\\d)\\d{17}[\\dXx](?!\\d)"),
                "身份证模式应通过安全校验");
        assertTrue(SafeRegexValidator.isSafeUserPattern("(?<!\\d)(?:\\d{16}|\\d{19})(?!\\d)"),
                "无分隔银行卡模式应通过安全校验");
        assertTrue(SafeRegexValidator.isSafeUserPattern(
                "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
                "带分隔银行卡模式应通过安全校验");
        assertTrue(SafeRegexValidator.isSafeUserPattern("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
                "手机号模式应通过安全校验");
        assertTrue(SafeRegexValidator.isSafeUserPattern(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                "邮箱模式应通过安全校验");
    }

    /**
     * 生产装配的是 {@code @Primary} 的 Optimized 引擎（见 {@code @ConditionalOnProperty(matchIfMissing = true)}），
     * 而语料回归测试直接 new 的是 Default 引擎 —— 键名豁免必须在两个引擎里都存在，
     * 否则「测试绿、线上仍打码 traceId」（issue #561）。
     */
    private SanitizationRule phoneRule() {
        return SanitizationRule.builder()
                .ruleId("phone-mask")
                .name("Phone Mask")
                .type(RuleType.PII_PATTERN)
                .pattern("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json", "text/plain"))
                .replacementChar("*")
                .build();
    }

    private String maskJson(final String content) {
        return ruleEngine
                .applySanitizationRules(content, List.of(phoneRule()), "application/json", true)
                .block();
    }

    @Test
    void testJsonKeyExemption_TraceIdValueKept() {
        String content = "{\"traceId\":\"13800138000\"}";

        assertEquals(content, maskJson(content),
                "traceId 的值必须原样保留：打码会破坏与日志/链路的对齐（#561）");
    }

    @Test
    void testJsonKeyExemption_KeyMatchIsCaseInsensitive() {
        String content = "{\"TRACEID\":\"13800138000\"}";

        assertEquals(content, maskJson(content), "键名比较必须大小写不敏感");
    }

    @Test
    void testJsonKeyExemption_SpanAndRequestIdKept() {
        String content = "{\"spanId\":\"13800138000\",\"requestId\":\"13900139000\"}";

        assertEquals(content, maskJson(content), "spanId / requestId 同样属于排障标识，不得打码");
    }

    @Test
    void testJsonKeyExemption_NonExemptKeyStillMasked() {
        String content = "{\"correlationId\":\"13800138000\"}";

        assertEquals("{\"correlationId\":\"***********\"}", maskJson(content),
                "豁免必须只覆盖白名单键，其它键的值照旧脱敏");
    }

    @Test
    void testJsonKeyExemption_FollowsSiblingKeys() {
        String content = "{\"traceId\":\"13800138000\",\"phone\":\"13900139000\"}";

        assertEquals("{\"traceId\":\"13800138000\",\"phone\":\"***********\"}", maskJson(content),
                "键名要按当前值所属的键判定，不能沿用上一个键的豁免结论");
    }

    @Test
    void testJsonKeyExemption_DoesNotApplyToPlainText() {
        String content = "traceId=13800138000";

        String result = ruleEngine
                .applySanitizationRules(content, List.of(phoneRule()), "text/plain", true)
                .block();

        assertEquals("traceId=***********", result,
                "非 JSON 文本没有键名可推断，行为必须与改动前一致");
    }
}

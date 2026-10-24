package org.unreal.modelrouter.auth.security.sanitization.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.auth.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.auth.security.model.SanitizationStrategy;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityMetrics;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DefaultSanitizationService 单元测试。
 *
 * <p>请求侧脱敏（issue #601）已移除，规则只来自 response 配置；这些规则同时供网关响应脱敏
 * 与落库前脱敏（{@link DefaultSanitizationService#sanitizeForStorage}）使用。</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultSanitizationServiceTest {

    @Mock
    private SanitizationRuleEngine ruleEngine;

    private SecurityProperties securityProperties;
    private DefaultSanitizationService sanitizationService;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();

        securityProperties.getSanitization().getResponse()
                .setSensitiveWords(Arrays.asList("internal", "debug"));
        securityProperties.getSanitization().getResponse()
                .setPiiPatterns(List.of("\\d{11}", "\\d{18}"));
        securityProperties.getSanitization().getResponse().setMaskingChar("*");

        lenient().when(ruleEngine.compileRules(anyList())).thenReturn(Mono.empty());
        lenient().when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString()))
                .thenReturn(Mono.just("sanitized content"));
        lenient().when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(Mono.just("sanitized content"));
        lenient().when(ruleEngine.validateRule(any(SanitizationRule.class))).thenReturn(Mono.just(true));

        sanitizationService = new DefaultSanitizationService(ruleEngine, securityProperties);
    }

    @Test
    void testInitializeRules() {
        sanitizationService.initializeRules();

        verify(ruleEngine, atLeastOnce()).compileRules(anyList());

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertFalse(rules.isEmpty());
                    assertEquals(4, rules.size(), "2 个敏感词 + 2 个 PII 模式");
                    assertTrue(rules.stream().allMatch(rule -> rule.getRuleId().startsWith("response-")));
                })
                .verifyComplete();
    }

    @Test
    void testSanitizeResponse() {
        String content = "internal debug information";

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectNext("sanitized content")
                .verifyComplete();

        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }

    @Test
    void testSanitizeResponse_EmptyContent() {
        StepVerifier.create(sanitizationService.sanitizeResponse("", "application/json"))
                .expectNext("")
                .verifyComplete();

        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }

    @Test
    void testSanitizeResponse_NullContent() {
        StepVerifier.create(sanitizationService.sanitizeResponse(null, "application/json"))
                .verifyComplete();

        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }

    @Test
    void testGetAllRules() {
        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertNotNull(rules);
                    assertEquals(4, rules.size());
                    assertTrue(rules.stream().anyMatch(rule -> rule.getRuleId().startsWith("response-")));
                    assertTrue(rules.stream().noneMatch(rule -> rule.getRuleId().startsWith("request-")));
                })
                .verifyComplete();
    }

    @Test
    void testUpdateRules() {
        SanitizationRule rule1 = SanitizationRule.builder()
                .ruleId("test-rule-1")
                .name("Test Rule 1")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("test")
                .strategy(SanitizationStrategy.MASK)
                .build();

        SanitizationRule rule2 = SanitizationRule.builder()
                .ruleId("test-rule-2")
                .name("Test Rule 2")
                .type(RuleType.PII_PATTERN)
                .pattern("\\d{4}")
                .strategy(SanitizationStrategy.REPLACE)
                .build();

        List<SanitizationRule> newRules = Arrays.asList(rule1, rule2);

        StepVerifier.create(sanitizationService.updateRules(newRules))
                .verifyComplete();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertEquals(2, rules.size());
                    assertTrue(rules.stream().anyMatch(rule -> "test-rule-1".equals(rule.getRuleId())));
                    assertTrue(rules.stream().anyMatch(rule -> "test-rule-2".equals(rule.getRuleId())));
                })
                .verifyComplete();

        verify(ruleEngine, atLeastOnce()).compileRules(eq(newRules));
    }

    @Test
    void testUpdateRules_InvalidRule() {
        SanitizationRule invalidRule = SanitizationRule.builder()
                .ruleId("")
                .name("Invalid Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("test")
                .strategy(SanitizationStrategy.MASK)
                .build();

        StepVerifier.create(sanitizationService.updateRules(List.of(invalidRule)))
                .expectError(SanitizationException.class)
                .verify();
    }

    @Test
    void testAddRule() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("new-rule")
                .name("New Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("newword")
                .strategy(SanitizationStrategy.MASK)
                .build();

        StepVerifier.create(sanitizationService.addRule(rule))
                .verifyComplete();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertTrue(rules.stream().anyMatch(r -> "new-rule".equals(r.getRuleId())));
                })
                .verifyComplete();

        verify(ruleEngine).validateRule(rule);
        verify(ruleEngine, atLeastOnce()).compileRules(anyList());
    }

    @Test
    void testAddRule_NullRule() {
        StepVerifier.create(sanitizationService.addRule(null))
                .expectError(SanitizationException.class)
                .verify();
    }

    @Test
    void testAddRule_EmptyRuleId() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("")
                .name("Invalid Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("test")
                .strategy(SanitizationStrategy.MASK)
                .build();

        StepVerifier.create(sanitizationService.addRule(rule))
                .expectError(SanitizationException.class)
                .verify();
    }

    @Test
    void testRemoveRule() {
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("rule-to-remove")
                .name("Rule to Remove")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("remove")
                .strategy(SanitizationStrategy.MASK)
                .build();

        sanitizationService.addRule(rule).block();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertTrue(rules.stream().anyMatch(r -> "rule-to-remove".equals(r.getRuleId())));
                })
                .verifyComplete();

        StepVerifier.create(sanitizationService.removeRule("rule-to-remove"))
                .verifyComplete();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertFalse(rules.stream().anyMatch(r -> "rule-to-remove".equals(r.getRuleId())));
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRule_EmptyRuleId() {
        StepVerifier.create(sanitizationService.removeRule(""))
                .expectError(SanitizationException.class)
                .verify();
    }

    @Test
    void testRemoveRule_NonExistentRule() {
        StepVerifier.create(sanitizationService.removeRule("non-existent-rule"))
                .verifyComplete();
    }

    @Test
    void testReloadConfigurationRules() {
        sanitizationService.initializeRules();

        SanitizationRule manualRule = SanitizationRule.builder()
                .ruleId("manual-rule")
                .name("Manual Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("manual")
                .strategy(SanitizationStrategy.MASK)
                .build();

        sanitizationService.addRule(manualRule).block();

        StepVerifier.create(sanitizationService.reloadConfigurationRules())
                .verifyComplete();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertTrue(rules.stream().anyMatch(r -> "manual-rule".equals(r.getRuleId())));
                    assertTrue(rules.stream().anyMatch(r -> r.getRuleId().startsWith("response-")));
                    assertTrue(rules.stream().noneMatch(r -> r.getRuleId().startsWith("request-")));
                })
                .verifyComplete();
    }

    @Test
    void testSanitizationError() {
        String content = "test content";

        sanitizationService.initializeRules();

        when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(Mono.error(new RuntimeException("Rule engine error")));

        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectError(SanitizationException.class)
                .verify();
    }

    // ===== 开关闸测试 =====

    @Test
    void testSanitizeResponse_ResponseDisabled_ReturnsContentVerbatim() {
        securityProperties.getSanitization().getResponse().setEnabled(false);

        String content = "processCpuUsage:0.12345678901, phone:13812345678";

        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectNext(content)
                .verifyComplete();

        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }

    @Test
    void testSanitizeResponse_ResponseDisabled_EmptyContent() {
        securityProperties.getSanitization().getResponse().setEnabled(false);

        StepVerifier.create(sanitizationService.sanitizeResponse("", "application/json"))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    void testSanitizeResponse_ResponseEnabled_DelegatesToEngine() {
        assertTrue(securityProperties.getSanitization().getResponse().isEnabled());

        String content = "internal debug info";

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectNext("sanitized content")
                .verifyComplete();

        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }

    @Test
    void testInitializeRules_ResponseDisabled_RulesStillLoadedForStorage() {
        // response.enabled 只控制网关 sanitizeResponse；配置规则仍加载供记录侧使用
        securityProperties.getSanitization().getResponse().setEnabled(false);

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    boolean hasResponseRules = rules.stream()
                            .anyMatch(rule -> rule.getRuleId().startsWith("response-"));
                    assertTrue(hasResponseRules, "配置中的 response PII/敏感词应加载（记录侧可用）");
                })
                .verifyComplete();
    }

    @Test
    void testInitializeRules_NoPatterns_EmptyRuleSet() {
        securityProperties.getSanitization().getResponse().setSensitiveWords(List.of());
        securityProperties.getSanitization().getResponse().setPiiPatterns(List.of());

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> assertTrue(rules.isEmpty(), "无配置模式时规则库为空"))
                .verifyComplete();
    }

    @Test
    @DisplayName("sanitizeForStorage: 网关开关关闭时仍应应用配置中的 PII 规则")
    void testSanitizeForStorage_appliesRulesWhenGatewaySwitchesOff() {
        securityProperties.getSanitization().getResponse().setEnabled(false);
        sanitizationService.initializeRules();

        final String content = "user phone 13812345678 in chat log";
        when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(Mono.just("user phone *********** in chat log"));

        StepVerifier.create(sanitizationService.sanitizeForStorage(content, "application/json"))
                .expectNext("user phone *********** in chat log")
                .verifyComplete();

        verify(ruleEngine, atLeastOnce()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("sanitizeForStorage: 无任何配置规则时原样返回")
    void testSanitizeForStorage_noRules_returnsVerbatim() {
        securityProperties.getSanitization().getResponse().setSensitiveWords(List.of());
        securityProperties.getSanitization().getResponse().setPiiPatterns(List.of());
        securityProperties.getSanitization().getResponse().setEnabled(false);
        sanitizationService.initializeRules();

        final String content = "plain chat without pii patterns";
        StepVerifier.create(sanitizationService.sanitizeForStorage(content, "application/json"))
                .expectNext(content)
                .verifyComplete();
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("rebuildRulesFromProperties: 热改 piiPatterns 后规则库应更新")
    void testRebuildRulesFromProperties_afterHotUpdate() {
        securityProperties.getSanitization().getResponse()
                .setPiiPatterns(new java.util.ArrayList<>(List.of("\\d{11}")));
        sanitizationService.initializeRules();
        final int before = sanitizationService.getAllRules().block().size();

        securityProperties.getSanitization().getResponse().getPiiPatterns().add("\\b[A-Z]{2}\\d{6}\\b");
        StepVerifier.create(sanitizationService.rebuildRulesFromProperties()).verifyComplete();

        final List<SanitizationRule> after = sanitizationService.getAllRules().block();
        assertNotNull(after);
        assertTrue(after.size() > before, "热重建后规则数应增加");
        assertTrue(after.stream().anyMatch(r -> "\\b[A-Z]{2}\\d{6}\\b".equals(r.getPattern())),
                "新增 PII 模式应出现在规则库中");
        assertTrue(after.stream().allMatch(r -> r.getRuleId().startsWith("response-")));
    }

    @Test
    @DisplayName("脱敏指标接线（#456）：响应脱敏记 direction=response 并计时")
    void testSanitizationMetrics_recordedForResponse() {
        securityProperties.getSanitization().getResponse().setEnabled(true);
        sanitizationService.initializeRules();

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(sanitizationService, "securityMetrics", new SecurityMetrics(registry));

        StepVerifier.create(sanitizationService.sanitizeResponse("debug info", "application/json"))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(1.0, registry.get("jairouter.security.sanitization.operations")
                .tag("content_type", "application/json")
                .tag("direction", "response")
                .counter().count());
        assertEquals(1L, registry.get("jairouter.security.sanitization.duration")
                .tag("operation", "response").timer().count(),
                "计时器必须被 stop 一次，否则 duration 指标永远是 0");
    }
}

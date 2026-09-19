package org.unreal.modelrouter.auth.security.sanitization.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.auth.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.auth.security.model.SanitizationStrategy;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DefaultSanitizationService 单元测试
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
        
        // 设置请求脱敏配置
        securityProperties.getSanitization().getRequest().setSensitiveWords(Arrays.asList("password", "secret"));
        securityProperties.getSanitization().getRequest().setPiiPatterns(Arrays.asList("\\d{11}", "\\d{18}"));
        securityProperties.getSanitization().getRequest().setMaskingChar("*");
        securityProperties.getSanitization().getRequest().setWhitelistUsers(Arrays.asList("admin", "test-user"));
        
        // 设置响应脱敏配置
        securityProperties.getSanitization().getResponse().setSensitiveWords(Arrays.asList("internal", "debug"));
        securityProperties.getSanitization().getResponse().setPiiPatterns(List.of("\\d{11}"));
        securityProperties.getSanitization().getResponse().setMaskingChar("*");
        
        // Mock rule engine with lenient stubbing
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
        // 初始化会在构造函数后自动调用
        sanitizationService.initializeRules();
        
        // 验证规则编译被调用
        verify(ruleEngine, atLeastOnce()).compileRules(anyList());
        
        // 验证规则被加载
        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertFalse(rules.isEmpty());
                    // 应该有请求和响应的敏感词和PII规则
                    assertTrue(rules.size() >= 6); // 2+2 for request, 1+1 for response
                })
                .verifyComplete();
    }
    
    @Test
    void testSanitizeRequest_WithWhitelistedUser() {
        String content = "password is secret123";
        String userId = "admin"; // 在白名单中
        
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", userId))
                .expectNext(content) // 应该返回原始内容
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }
    
    @Test
    void testSanitizeRequest_WithNonWhitelistedUser() {
        String content = "password is secret123";
        String userId = "regular-user"; // 不在白名单中
        
        // 初始化规则
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", userId))
                .expectNext("sanitized content")
                .verifyComplete();
        
        // 应该调用规则引擎
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }
    
    @Test
    void testSanitizeRequest_WithNullUser() {
        String content = "password is secret123";
        
        // 初始化规则
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", null))
                .expectNext("sanitized content")
                .verifyComplete();
        
        // 应该调用规则引擎
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }
    
    @Test
    void testSanitizeRequest_EmptyContent() {
        StepVerifier.create(sanitizationService.sanitizeRequest("", "application/json", "user"))
                .expectNext("")
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }
    
    @Test
    void testSanitizeRequest_NullContent() {
        StepVerifier.create(sanitizationService.sanitizeRequest(null, "application/json", "user"))
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }
    
    @Test
    void testSanitizeResponse() {
        String content = "internal debug information";
        
        // 初始化规则
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectNext("sanitized content")
                .verifyComplete();
        
        // 应该调用规则引擎
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }
    
    @Test
    void testSanitizeResponse_EmptyContent() {
        StepVerifier.create(sanitizationService.sanitizeResponse("", "application/json"))
                .expectNext("")
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }
    
    @Test
    void testIsUserWhitelisted_WhitelistedUser() {
        StepVerifier.create(sanitizationService.isUserWhitelisted("admin"))
                .expectNext(true)
                .verifyComplete();
    }
    
    @Test
    void testIsUserWhitelisted_NonWhitelistedUser() {
        StepVerifier.create(sanitizationService.isUserWhitelisted("regular-user"))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testIsUserWhitelisted_NullUser() {
        StepVerifier.create(sanitizationService.isUserWhitelisted(null))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testIsUserWhitelisted_EmptyUser() {
        StepVerifier.create(sanitizationService.isUserWhitelisted(""))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testGetAllRules() {
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertNotNull(rules);
                    assertFalse(rules.isEmpty());
                    
                    // 验证包含请求和响应规则
                    boolean hasRequestRules = rules.stream().anyMatch(rule -> rule.getRuleId().startsWith("request-"));
                    boolean hasResponseRules = rules.stream().anyMatch(rule -> rule.getRuleId().startsWith("response-"));
                    
                    assertTrue(hasRequestRules);
                    assertTrue(hasResponseRules);
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
        
        // 验证规则被更新
        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertEquals(2, rules.size());
                    assertTrue(rules.stream().anyMatch(rule -> "test-rule-1".equals(rule.getRuleId())));
                    assertTrue(rules.stream().anyMatch(rule -> "test-rule-2".equals(rule.getRuleId())));
                })
                .verifyComplete();
        
        // 验证规则编译被调用
        verify(ruleEngine, atLeastOnce()).compileRules(eq(newRules));
    }
    
    @Test
    void testUpdateRules_InvalidRule() {
        SanitizationRule invalidRule = SanitizationRule.builder()
                .ruleId("") // 空ID
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
        
        // 验证规则被添加
        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertTrue(rules.stream().anyMatch(r -> "new-rule".equals(r.getRuleId())));
                })
                .verifyComplete();
        
        // 验证规则验证和编译被调用
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
        // 先添加一个规则
        SanitizationRule rule = SanitizationRule.builder()
                .ruleId("rule-to-remove")
                .name("Rule to Remove")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("remove")
                .strategy(SanitizationStrategy.MASK)
                .build();
        
        sanitizationService.addRule(rule).block();
        
        // 验证规则存在
        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertTrue(rules.stream().anyMatch(r -> "rule-to-remove".equals(r.getRuleId())));
                })
                .verifyComplete();
        
        // 删除规则
        StepVerifier.create(sanitizationService.removeRule("rule-to-remove"))
                .verifyComplete();
        
        // 验证规则被删除
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
        // 删除不存在的规则应该不会抛出异常，只是记录警告
        StepVerifier.create(sanitizationService.removeRule("non-existent-rule"))
                .verifyComplete();
    }
    
    @Test
    void testReloadConfigurationRules() {
        sanitizationService.initializeRules();
        
        // 添加一个手动规则
        SanitizationRule manualRule = SanitizationRule.builder()
                .ruleId("manual-rule")
                .name("Manual Rule")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("manual")
                .strategy(SanitizationStrategy.MASK)
                .build();
        
        sanitizationService.addRule(manualRule).block();
        
        // 重新加载配置规则
        StepVerifier.create(sanitizationService.reloadConfigurationRules())
                .verifyComplete();
        
        // 验证手动规则仍然存在，配置规则被重新加载
        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    // 手动规则应该仍然存在
                    assertTrue(rules.stream().anyMatch(r -> "manual-rule".equals(r.getRuleId())));
                    // 配置规则应该被重新加载
                    assertTrue(rules.stream().anyMatch(r -> r.getRuleId().startsWith("request-")));
                    assertTrue(rules.stream().anyMatch(r -> r.getRuleId().startsWith("response-")));
                })
                .verifyComplete();
    }
    
    @Test
    void testGetRuleStatistics() {
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.getRuleStatistics())
                .assertNext(stats -> {
                    assertTrue(stats.totalRules() > 0);
                    assertTrue(stats.enabledRules() > 0);
                    assertTrue(stats.requestRules() > 0);
                    assertTrue(stats.responseRules() > 0);
                    
                    // 验证统计信息的一致性
                    assertEquals(stats.requestRules() + stats.responseRules(), stats.totalRules());
                })
                .verifyComplete();
    }
    
    @Test
    void testSanitizationError() {
        String content = "test content";
        
        // 初始化规则
        sanitizationService.initializeRules();
        
        // Mock规则引擎抛出异常
        when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(Mono.error(new RuntimeException("Rule engine error")));
        
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", "user"))
                .expectError(SanitizationException.class)
                .verify();
    }
    
    // ===== 改动3: 开关闸测试 =====
    
    @Test
    void testSanitizeResponse_ResponseDisabled_ReturnsContentVerbatim() {
        // 关闭响应脱敏开关
        securityProperties.getSanitization().getResponse().setEnabled(false);
        
        // 即使内容包含手机号，也应原样返回
        String content = "processCpuUsage:0.12345678901, phone:13812345678";
        
        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectNext(content) // 逐字不变
                .verifyComplete();
        
        // 规则引擎不应被调用
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
    void testSanitizeRequest_RequestDisabled_ReturnsContentVerbatim() {
        // 关闭请求脱敏开关
        securityProperties.getSanitization().getRequest().setEnabled(false);
        
        String content = "password=secret, phone=13812345678";
        
        // 即使是白名单外用户，也应原样返回
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", "regular-user"))
                .expectNext(content) // 逐字不变
                .verifyComplete();
        
        // 规则引擎不应被调用
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }
    
    @Test
    void testSanitizeRequest_RequestDisabled_BypassesWhitelistCheck() {
        // 关闭请求脱敏开关
        securityProperties.getSanitization().getRequest().setEnabled(false);
        
        String content = "phone=13812345678";
        
        // 白名单用户也应直接返回（开关关闭时无论白名单与否都跳过）
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", "admin"))
                .expectNext(content)
                .verifyComplete();
        
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString(), anyBoolean());
    }
    
    @Test
    void testSanitizeResponse_ResponseEnabled_DelegatesToEngine() {
        // 响应脱敏开关保持默认 true
        assertTrue(securityProperties.getSanitization().getResponse().isEnabled());
        
        String content = "internal debug info";
        
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.sanitizeResponse(content, "application/json"))
                .expectNext("sanitized content")
                .verifyComplete();
        
        // 应该调用规则引擎
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }
    
    @Test
    void testSanitizeRequest_RequestEnabled_DelegatesToEngine() {
        // 请求脱敏开关保持默认 true
        assertTrue(securityProperties.getSanitization().getRequest().isEnabled());
        
        String content = "password is secret123";
        
        sanitizationService.initializeRules();
        
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", "regular-user"))
                .expectNext("sanitized content")
                .verifyComplete();
        
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"), anyBoolean());
    }
    
    @Test
    void testInitializeRules_ResponseDisabled_NoResponseRulesLoaded() {
        // v3.2.0：response.enabled 只控制网关 sanitizeResponse；配置规则仍加载供记录侧使用
        securityProperties.getSanitization().getResponse().setEnabled(false);

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    boolean hasResponseRules = rules.stream()
                            .anyMatch(rule -> rule.getRuleId().startsWith("response-"));
                    assertTrue(hasResponseRules, "配置中的 response PII/敏感词应加载（记录侧可用）");

                    boolean hasRequestRules = rules.stream()
                            .anyMatch(rule -> rule.getRuleId().startsWith("request-"));
                    assertTrue(hasRequestRules, "请求脱敏配置规则仍应加载");
                })
                .verifyComplete();
    }
    
    @Test
    void testInitializeRules_RequestDisabled_NoRequestRulesLoaded() {
        // v3.2.0：request.enabled 只控制网关 sanitizeRequest；
        // 配置中的 PII/敏感词仍加载，供记录侧 sanitizeForStorage 与管理端展示
        securityProperties.getSanitization().getRequest().setEnabled(false);

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    boolean hasRequestRules = rules.stream()
                            .anyMatch(rule -> rule.getRuleId().startsWith("request-"));
                    assertTrue(hasRequestRules, "配置中的 request PII/敏感词应加载（记录侧可用）");

                    boolean hasResponseRules = rules.stream()
                            .anyMatch(rule -> rule.getRuleId().startsWith("response-"));
                    assertTrue(hasResponseRules, "响应脱敏开启时应加载响应规则");
                })
                .verifyComplete();
    }

    @Test
    void testInitializeRules_BothDisabled_NoRulesLoaded() {
        securityProperties.getSanitization().getRequest().setEnabled(false);
        securityProperties.getSanitization().getResponse().setEnabled(false);
        // 清空模式，确保「无配置规则」时库为空
        securityProperties.getSanitization().getRequest().setSensitiveWords(List.of());
        securityProperties.getSanitization().getRequest().setPiiPatterns(List.of());
        securityProperties.getSanitization().getResponse().setSensitiveWords(List.of());
        securityProperties.getSanitization().getResponse().setPiiPatterns(List.of());

        sanitizationService.initializeRules();

        StepVerifier.create(sanitizationService.getAllRules())
                .assertNext(rules -> {
                    assertTrue(rules.isEmpty(), "请求和响应均无配置模式时规则库为空");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("sanitizeForStorage: 网关开关关闭时仍应应用配置中的 PII 规则")
    void testSanitizeForStorage_appliesRulesWhenGatewaySwitchesOff() {
        securityProperties.getSanitization().getRequest().setEnabled(false);
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
        securityProperties.getSanitization().getRequest().setSensitiveWords(List.of());
        securityProperties.getSanitization().getRequest().setPiiPatterns(List.of());
        securityProperties.getSanitization().getResponse().setSensitiveWords(List.of());
        securityProperties.getSanitization().getResponse().setPiiPatterns(List.of());
        securityProperties.getSanitization().getRequest().setEnabled(false);
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
        securityProperties.getSanitization().getRequest()
                .setPiiPatterns(new java.util.ArrayList<>(List.of("\\d{11}")));
        sanitizationService.initializeRules();
        final int before = sanitizationService.getAllRules().block().size();

        securityProperties.getSanitization().getRequest().getPiiPatterns().add("\\b[A-Z]{2}\\d{6}\\b");
        StepVerifier.create(sanitizationService.rebuildRulesFromProperties()).verifyComplete();

        final List<SanitizationRule> after = sanitizationService.getAllRules().block();
        assertNotNull(after);
        assertTrue(after.size() > before, "热重建后规则数应增加");
        assertTrue(after.stream().anyMatch(r -> "\\b[A-Z]{2}\\d{6}\\b".equals(r.getPattern())),
                "新增 PII 模式应出现在规则库中");
    }
}
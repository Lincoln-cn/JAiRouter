package org.unreal.modelrouter.security.sanitization.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;
import org.unreal.modelrouter.sanitization.SanitizationRuleEngine;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

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
        securityProperties.getSanitization().getResponse().setPiiPatterns(Arrays.asList("\\d{11}"));
        securityProperties.getSanitization().getResponse().setMaskingChar("*");
        
        // Mock rule engine with lenient stubbing
        lenient().when(ruleEngine.compileRules(anyList())).thenReturn(Mono.empty());
        lenient().when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString()))
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
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString());
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
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"));
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
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"));
    }
    
    @Test
    void testSanitizeRequest_EmptyContent() {
        StepVerifier.create(sanitizationService.sanitizeRequest("", "application/json", "user"))
                .expectNext("")
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString());
    }
    
    @Test
    void testSanitizeRequest_NullContent() {
        StepVerifier.create(sanitizationService.sanitizeRequest(null, "application/json", "user"))
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString());
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
        verify(ruleEngine).applySanitizationRules(eq(content), anyList(), eq("application/json"));
    }
    
    @Test
    void testSanitizeResponse_EmptyContent() {
        StepVerifier.create(sanitizationService.sanitizeResponse("", "application/json"))
                .expectNext("")
                .verifyComplete();
        
        // 不应该调用规则引擎
        verify(ruleEngine, never()).applySanitizationRules(anyString(), anyList(), anyString());
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
                    assertTrue(stats.getTotalRules() > 0);
                    assertTrue(stats.getEnabledRules() > 0);
                    assertTrue(stats.getRequestRules() > 0);
                    assertTrue(stats.getResponseRules() > 0);
                    
                    // 验证统计信息的一致性
                    assertEquals(stats.getRequestRules() + stats.getResponseRules(), stats.getTotalRules());
                })
                .verifyComplete();
    }
    
    @Test
    void testSanitizationError() {
        String content = "test content";
        
        // 初始化规则
        sanitizationService.initializeRules();
        
        // Mock规则引擎抛出异常
        when(ruleEngine.applySanitizationRules(anyString(), anyList(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Rule engine error")));
        
        StepVerifier.create(sanitizationService.sanitizeRequest(content, "application/json", "user"))
                .expectError(SanitizationException.class)
                .verify();
    }
}
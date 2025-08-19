package org.unreal.moduler.security.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.security.monitoring.SecurityMetrics;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityMetrics 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SecurityMetricsTest {
    
    private MeterRegistry meterRegistry;
    private SecurityMetrics securityMetrics;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        securityMetrics = new SecurityMetrics(meterRegistry);
    }
    
    @Test
    void testRecordAuthenticationAttempt() {
        // 执行测试
        securityMetrics.recordAuthenticationAttempt();
        securityMetrics.recordAuthenticationAttempt();
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.authentication.attempts").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }
    
    @Test
    void testRecordAuthenticationSuccess() {
        // 执行测试
        securityMetrics.recordAuthenticationSuccess("api_key");
        securityMetrics.recordAuthenticationSuccess("jwt");
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.authentication.successes").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
        
        // 验证标签
        Counter apiKeyCounter = meterRegistry.find("jairouter.security.authentication.successes")
                .tag("auth_type", "api_key")
                .counter();
        assertNotNull(apiKeyCounter);
        assertEquals(1.0, apiKeyCounter.count());
    }
    
    @Test
    void testRecordAuthenticationFailure() {
        // 执行测试
        securityMetrics.recordAuthenticationFailure("api_key", "Invalid API Key");
        securityMetrics.recordAuthenticationFailure("jwt", "Token expired");
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.authentication.failures").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
        
        // 验证失败原因分类
        Counter invalidCounter = meterRegistry.find("jairouter.security.authentication.failures")
                .tag("failure_reason", "invalid")
                .counter();
        assertNotNull(invalidCounter);
        assertEquals(1.0, invalidCounter.count());
        
        Counter expiredCounter = meterRegistry.find("jairouter.security.authentication.failures")
                .tag("failure_reason", "expired")
                .counter();
        assertNotNull(expiredCounter);
        assertEquals(1.0, expiredCounter.count());
    }
    
    @Test
    void testAuthenticationTimer() {
        // 执行测试
        Timer.Sample sample = securityMetrics.startAuthenticationTimer();
        
        // 模拟处理时间
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        securityMetrics.recordAuthenticationDuration(sample);
        
        // 验证计时器
        Timer timer = meterRegistry.find("jairouter.security.authentication.duration").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
    
    @Test
    void testRecordJwtValidation() {
        // 执行测试
        securityMetrics.recordJwtValidation(true);
        securityMetrics.recordJwtValidation(false);
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.jwt.validations").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
        
        // 验证成功/失败标签
        Counter successCounter = meterRegistry.find("jairouter.security.jwt.validations")
                .tag("result", "success")
                .counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count());
        
        Counter failureCounter = meterRegistry.find("jairouter.security.jwt.validations")
                .tag("result", "failure")
                .counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());
    }
    
    @Test
    void testRecordJwtRefresh() {
        // 执行测试
        securityMetrics.recordJwtRefresh();
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.jwt.refreshes").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }
    
    @Test
    void testRecordJwtExpired() {
        // 执行测试
        securityMetrics.recordJwtExpired();
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.jwt.expired").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }
    
    @Test
    void testRecordSanitizationOperation() {
        // 执行测试
        securityMetrics.recordSanitizationOperation("application/json", "request");
        securityMetrics.recordSanitizationOperation("text/plain", "response");
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.sanitization.operations").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
        
        // 验证标签
        Counter jsonCounter = meterRegistry.find("jairouter.security.sanitization.operations")
                .tag("content_type", "application/json")
                .tag("direction", "request")
                .counter();
        assertNotNull(jsonCounter);
        assertEquals(1.0, jsonCounter.count());
    }
    
    @Test
    void testRecordSanitizationRuleMatch() {
        // 执行测试
        securityMetrics.recordSanitizationRuleMatch("rule-001", "PII_PATTERN", 3);
        
        // 验证指标 - 应该记录3次匹配
        Counter counter = meterRegistry.find("jairouter.security.sanitization.rule.matches").counter();
        assertNotNull(counter);
        assertEquals(3.0, counter.count());
        
        // 验证标签
        Counter ruleCounter = meterRegistry.find("jairouter.security.sanitization.rule.matches")
                .tag("rule_id", "rule-001")
                .tag("rule_type", "PII_PATTERN")
                .counter();
        assertNotNull(ruleCounter);
        assertEquals(3.0, ruleCounter.count());
    }
    
    @Test
    void testRecordSecurityViolation() {
        // 执行测试
        securityMetrics.recordSecurityViolation("AUTH_FAILURE_SPIKE", "HIGH");
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.violations").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
        
        // 验证标签
        Counter violationCounter = meterRegistry.find("jairouter.security.violations")
                .tag("violation_type", "AUTH_FAILURE_SPIKE")
                .tag("severity", "HIGH")
                .counter();
        assertNotNull(violationCounter);
        assertEquals(1.0, violationCounter.count());
    }
    
    @Test
    void testRecordSuspiciousActivity() {
        // 执行测试
        securityMetrics.recordSuspiciousActivity("BRUTE_FORCE", "192.168.1.100");
        
        // 验证指标
        Counter counter = meterRegistry.find("jairouter.security.suspicious.activities").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
        
        // 验证标签（IP应该被哈希处理）
        Counter activityCounter = meterRegistry.find("jairouter.security.suspicious.activities")
                .tag("activity_type", "BRUTE_FORCE")
                .counter();
        assertNotNull(activityCounter);
        assertEquals(1.0, activityCounter.count());
    }
    
    @Test
    void testActiveUsersTracking() {
        // 执行测试
        securityMetrics.incrementActiveUsers();
        securityMetrics.incrementActiveUsers();
        securityMetrics.decrementActiveUsers();
        
        // 验证Gauge
        assertEquals(1.0, meterRegistry.find("jairouter.security.active.users").gauge().value());
        
        // 测试直接设置
        securityMetrics.updateActiveUsers(10);
        assertEquals(10.0, meterRegistry.find("jairouter.security.active.users").gauge().value());
    }
    
    @Test
    void testActiveApiKeysTracking() {
        // 执行测试
        securityMetrics.updateActiveApiKeys(5);
        
        // 验证Gauge
        assertEquals(5.0, meterRegistry.find("jairouter.security.apikeys.active").gauge().value());
    }
    
    @Test
    void testFailureReasonCounts() {
        // 执行测试
        securityMetrics.recordAuthenticationFailure("api_key", "Invalid API Key");
        securityMetrics.recordAuthenticationFailure("api_key", "Expired token");
        securityMetrics.recordAuthenticationFailure("jwt", "Invalid signature");
        
        // 验证失败原因统计
        var failureCounts = securityMetrics.getFailureReasonCounts();
        assertTrue(failureCounts.containsKey("invalid"));
        assertTrue(failureCounts.containsKey("expired"));
        assertEquals(2, failureCounts.get("invalid").get()); // "Invalid API Key" 和 "Invalid signature"
        assertEquals(1, failureCounts.get("expired").get());
        
        // 测试重置
        securityMetrics.resetFailureReasonCounts();
        assertTrue(securityMetrics.getFailureReasonCounts().isEmpty());
    }
    
    @Test
    void testSanitizationTimer() {
        // 执行测试
        Timer.Sample sample = securityMetrics.startSanitizationTimer();
        
        // 模拟处理时间
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        securityMetrics.recordSanitizationDuration(sample, "request_sanitization");
        
        // 验证计时器
        Timer timer = meterRegistry.find("jairouter.security.sanitization.duration")
                .tag("operation", "request_sanitization")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
}
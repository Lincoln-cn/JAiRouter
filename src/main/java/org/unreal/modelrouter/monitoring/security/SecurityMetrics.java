package org.unreal.modelrouter.monitoring.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全监控指标收集器
 * 集成Micrometer框架记录认证和脱敏指标
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.metrics.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 认证相关指标
    private final Counter authenticationAttempts;
    private final Counter authenticationSuccesses;
    private final Counter authenticationFailures;
    private final Timer authenticationDuration;
    
    // JWT相关指标
    private final Counter jwtTokenValidations;
    private final Counter jwtTokenRefreshes;
    private final Counter jwtTokenExpired;
    private final Timer jwtValidationDuration;
    
    // 脱敏相关指标
    private final Counter sanitizationOperations;
    private final Counter sanitizationRuleMatches;
    private final Timer sanitizationDuration;
    
    // 安全事件指标
    private final Counter securityViolations;
    private final Counter suspiciousActivities;
    
    // 实时统计数据
    private final AtomicLong activeAuthenticatedUsers = new AtomicLong(0);
    private final AtomicLong totalApiKeysInUse = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> failureReasonCounts = new ConcurrentHashMap<>();
    
    public SecurityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化认证指标
        this.authenticationAttempts = Counter.builder("jairouter.security.authentication.attempts")
                .description("认证尝试总数")
                .register(meterRegistry);
                
        this.authenticationSuccesses = Counter.builder("jairouter.security.authentication.successes")
                .description("认证成功总数")
                .register(meterRegistry);
                
        this.authenticationFailures = Counter.builder("jairouter.security.authentication.failures")
                .description("认证失败总数")
                .register(meterRegistry);
                
        this.authenticationDuration = Timer.builder("jairouter.security.authentication.duration")
                .description("认证处理时间")
                .register(meterRegistry);
        
        // 初始化JWT指标
        this.jwtTokenValidations = Counter.builder("jairouter.security.jwt.validations")
                .description("JWT令牌验证总数")
                .register(meterRegistry);
                
        this.jwtTokenRefreshes = Counter.builder("jairouter.security.jwt.refreshes")
                .description("JWT令牌刷新总数")
                .register(meterRegistry);
                
        this.jwtTokenExpired = Counter.builder("jairouter.security.jwt.expired")
                .description("JWT令牌过期总数")
                .register(meterRegistry);
                
        this.jwtValidationDuration = Timer.builder("jairouter.security.jwt.validation.duration")
                .description("JWT验证处理时间")
                .register(meterRegistry);
        
        // 初始化脱敏指标
        this.sanitizationOperations = Counter.builder("jairouter.security.sanitization.operations")
                .description("数据脱敏操作总数")
                .register(meterRegistry);
                
        this.sanitizationRuleMatches = Counter.builder("jairouter.security.sanitization.rule.matches")
                .description("脱敏规则匹配总数")
                .register(meterRegistry);
                
        this.sanitizationDuration = Timer.builder("jairouter.security.sanitization.duration")
                .description("数据脱敏处理时间")
                .register(meterRegistry);
        
        // 初始化安全事件指标
        this.securityViolations = Counter.builder("jairouter.security.violations")
                .description("安全违规事件总数")
                .register(meterRegistry);
                
        this.suspiciousActivities = Counter.builder("jairouter.security.suspicious.activities")
                .description("可疑活动总数")
                .register(meterRegistry);
        
        // 注册实时统计Gauge
        Gauge.builder("jairouter.security.active.users", activeAuthenticatedUsers, AtomicLong::doubleValue)
                .description("当前活跃认证用户数")
                .register(meterRegistry);
                
        Gauge.builder("jairouter.security.apikeys.active", totalApiKeysInUse, AtomicLong::doubleValue)
                .description("当前使用中的API Key数量")
                .register(meterRegistry);
        
        log.info("安全监控指标已初始化");
    }
    
    // 认证指标记录方法
    
    /**
     * 记录认证尝试
     */
    public void recordAuthenticationAttempt() {
        authenticationAttempts.increment();
    }
    
    /**
     * 记录认证成功
     */
    public void recordAuthenticationSuccess(String authType) {
        Counter.builder("jairouter.security.authentication.successes")
                .tag("auth_type", authType != null ? authType : "unknown")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * 记录认证失败
     */
    public void recordAuthenticationFailure(String authType, String failureReason) {
        Counter.builder("jairouter.security.authentication.failures")
                .tag("auth_type", authType != null ? authType : "unknown")
                .tag("failure_reason", categorizeFailureReason(failureReason))
                .register(meterRegistry)
                .increment();
        
        // 更新失败原因统计
        String category = categorizeFailureReason(failureReason);
        failureReasonCounts.computeIfAbsent(category, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 记录认证处理时间
     */
    public Timer.Sample startAuthenticationTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成认证计时
     */
    public void recordAuthenticationDuration(Timer.Sample sample) {
        sample.stop(authenticationDuration);
    }
    
    // JWT指标记录方法
    
    /**
     * 记录JWT令牌验证
     */
    public void recordJwtValidation(boolean success) {
        Counter.builder("jairouter.security.jwt.validations")
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * 记录JWT令牌刷新
     */
    public void recordJwtRefresh() {
        jwtTokenRefreshes.increment();
    }
    
    /**
     * 记录JWT令牌过期
     */
    public void recordJwtExpired() {
        jwtTokenExpired.increment();
    }
    
    /**
     * 记录JWT验证处理时间
     */
    public Timer.Sample startJwtValidationTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成JWT验证计时
     */
    public void recordJwtValidationDuration(Timer.Sample sample) {
        sample.stop(jwtValidationDuration);
    }
    
    // 脱敏指标记录方法
    
    /**
     * 记录脱敏操作
     */
    public void recordSanitizationOperation(String contentType, String direction) {
        Counter.builder("jairouter.security.sanitization.operations")
                .tag("content_type", contentType != null ? contentType : "unknown")
                .tag("direction", direction != null ? direction : "unknown")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * 记录脱敏规则匹配
     */
    public void recordSanitizationRuleMatch(String ruleId, String ruleType, int matchCount) {
        Counter ruleMatchCounter = Counter.builder("jairouter.security.sanitization.rule.matches")
                .tag("rule_id", ruleId != null ? ruleId : "unknown")
                .tag("rule_type", ruleType != null ? ruleType : "unknown")
                .register(meterRegistry);
        
        // 记录匹配次数
        ruleMatchCounter.increment(matchCount);
    }
    
    /**
     * 记录脱敏处理时间
     */
    public Timer.Sample startSanitizationTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 完成脱敏计时
     */
    public void recordSanitizationDuration(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("jairouter.security.sanitization.duration")
                .tag("operation", operation != null ? operation : "unknown")
                .register(meterRegistry));
    }
    
    // 安全事件指标记录方法
    
    /**
     * 记录安全违规事件
     */
    public void recordSecurityViolation(String violationType, String severity) {
        Counter.builder("jairouter.security.violations")
                .tag("violation_type", violationType != null ? violationType : "unknown")
                .tag("severity", severity != null ? severity : "medium")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * 记录可疑活动
     */
    public void recordSuspiciousActivity(String activityType, String clientIp) {
        Counter.builder("jairouter.security.suspicious.activities")
                .tag("activity_type", activityType != null ? activityType : "unknown")
                .tag("client_ip_hash", hashClientIp(clientIp))
                .register(meterRegistry)
                .increment();
    }
    
    // 实时统计更新方法
    
    /**
     * 更新活跃用户数
     */
    public void updateActiveUsers(long count) {
        activeAuthenticatedUsers.set(count);
    }
    
    /**
     * 增加活跃用户
     */
    public void incrementActiveUsers() {
        activeAuthenticatedUsers.incrementAndGet();
    }
    
    /**
     * 减少活跃用户
     */
    public void decrementActiveUsers() {
        long current = activeAuthenticatedUsers.get();
        if (current > 0) {
            activeAuthenticatedUsers.decrementAndGet();
        }
    }
    
    /**
     * 更新使用中的API Key数量
     */
    public void updateActiveApiKeys(long count) {
        totalApiKeysInUse.set(count);
    }
    
    // 工具方法
    
    /**
     * 对失败原因进行分类
     */
    private String categorizeFailureReason(String failureReason) {
        if (failureReason == null) {
            return "unknown";
        }
        
        String reason = failureReason.toLowerCase();
        if (reason.contains("expired") || reason.contains("过期")) {
            return "expired";
        } else if (reason.contains("invalid") || reason.contains("无效")) {
            return "invalid";
        } else if (reason.contains("missing") || reason.contains("缺失")) {
            return "missing";
        } else if (reason.contains("blocked") || reason.contains("阻止")) {
            return "blocked";
        } else if (reason.contains("rate") || reason.contains("限制")) {
            return "rate_limited";
        } else {
            return "other";
        }
    }
    
    /**
     * 对客户端IP进行哈希处理（保护隐私）
     */
    private String hashClientIp(String clientIp) {
        if (clientIp == null) {
            return "unknown";
        }
        
        // 简单的哈希处理，实际应用中可以使用更安全的哈希算法
        return String.valueOf(clientIp.hashCode() & 0x7FFFFFFF);
    }
    
    /**
     * 获取失败原因统计
     */
    public ConcurrentHashMap<String, AtomicLong> getFailureReasonCounts() {
        return new ConcurrentHashMap<>(failureReasonCounts);
    }
    
    /**
     * 重置失败原因统计
     */
    public void resetFailureReasonCounts() {
        failureReasonCounts.clear();
    }
}
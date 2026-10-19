package org.unreal.modelrouter.monitor.monitoring.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    private final Timer sanitizationDuration;
    
    // 安全事件指标
    private final Counter securityViolations;
    
    public SecurityMetrics(final MeterRegistry meterRegistry) {
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
                
        this.sanitizationDuration = Timer.builder("jairouter.security.sanitization.duration")
                .description("数据脱敏处理时间")
                .register(meterRegistry);
        
        // 初始化安全事件指标
        this.securityViolations = Counter.builder("jairouter.security.violations")
                .description("安全违规事件总数")
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
    public void recordAuthenticationSuccess(final String authType) {
        Counter.builder("jairouter.security.authentication.successes")
                .tag("auth_type", authType != null ? authType : "unknown")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * 记录认证失败
     */
    public void recordAuthenticationFailure(final String authType, final String failureReason) {
        Counter.builder("jairouter.security.authentication.failures")
                .tag("auth_type", authType != null ? authType : "unknown")
                .tag("failure_reason", categorizeFailureReason(failureReason))
                .register(meterRegistry)
                .increment();
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
    public void recordAuthenticationDuration(final Timer.Sample sample) {
        sample.stop(authenticationDuration);
    }
    
    // JWT指标记录方法
    
    /**
     * 记录JWT令牌验证
     */
    public void recordJwtValidation(final boolean success) {
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
    public void recordJwtValidationDuration(final Timer.Sample sample) {
        sample.stop(jwtValidationDuration);
    }
    
    // 脱敏指标记录方法
    
    /**
     * 记录脱敏操作
     */
    public void recordSanitizationOperation(final String contentType, final String direction) {
        Counter.builder("jairouter.security.sanitization.operations")
                .tag("content_type", contentType != null ? contentType : "unknown")
                .tag("direction", direction != null ? direction : "unknown")
                .register(meterRegistry)
                .increment();
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
    public void recordSanitizationDuration(final Timer.Sample sample, final String operation) {
        sample.stop(Timer.builder("jairouter.security.sanitization.duration")
                .tag("operation", operation != null ? operation : "unknown")
                .register(meterRegistry));
    }
    
    // 安全事件指标记录方法
    
    /**
     * 记录安全违规事件
     */
    public void recordSecurityViolation(final String violationType, final String severity) {
        Counter.builder("jairouter.security.violations")
                .tag("violation_type", violationType != null ? violationType : "unknown")
                .tag("severity", severity != null ? severity : "medium")
                .register(meterRegistry)
                .increment();
    }
    
    // 工具方法
    
    /**
     * 对失败原因进行分类
     */
    private String categorizeFailureReason(final String failureReason) {
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
    
    // 公共getter方法，用于测试
    
    public Counter getAuthenticationAttempts() {
        return authenticationAttempts;
    }
    
    public Counter getAuthenticationSuccesses() {
        return authenticationSuccesses;
    }
    
    public Counter getAuthenticationFailures() {
        return authenticationFailures;
    }
    
    public Counter getSanitizationOperations() {
        return sanitizationOperations;
    }
}
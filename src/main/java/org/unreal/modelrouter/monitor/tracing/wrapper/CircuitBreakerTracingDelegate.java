package org.unreal.modelrouter.monitor.tracing.wrapper;

import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器追踪委托助手
 * 
 * 包含从 CircuitBreakerTracingWrapper 中提取的私有记录方法和统计计算方法。
 * 不是 Spring Bean，由 wrapper 直接实例化。
 * * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
final class CircuitBreakerTracingDelegate {

    private final String instanceId;
    private final CircuitBreaker circuitBreaker;
    private final StructuredLogger structuredLogger;
    private final AtomicLong totalChecks;
    private final AtomicLong allowedExecutions;
    private final AtomicLong rejectedExecutions;
    private final AtomicLong successCalls;
    private final AtomicLong failureCalls;
    private final AtomicLong stateChanges;
    private final AtomicReference<Instant> lastStateChangeTime;
    private final AtomicReference<Instant> lastFailureTime;
    private final AtomicReference<Instant> lastSuccessTime;

    CircuitBreakerTracingDelegate(
            final String instanceId,
            final CircuitBreaker circuitBreaker,
            final StructuredLogger structuredLogger,
            final AtomicLong totalChecks,
            final AtomicLong allowedExecutions,
            final AtomicLong rejectedExecutions,
            final AtomicLong successCalls,
            final AtomicLong failureCalls,
            final AtomicLong stateChanges,
            final AtomicReference<Instant> lastStateChangeTime,
            final AtomicReference<Instant> lastFailureTime,
            final AtomicReference<Instant> lastSuccessTime) {
        this.instanceId = instanceId;
        this.circuitBreaker = circuitBreaker;
        this.structuredLogger = structuredLogger;
        this.totalChecks = totalChecks;
        this.allowedExecutions = allowedExecutions;
        this.rejectedExecutions = rejectedExecutions;
        this.successCalls = successCalls;
        this.failureCalls = failureCalls;
        this.stateChanges = stateChanges;
        this.lastStateChangeTime = lastStateChangeTime;
        this.lastFailureTime = lastFailureTime;
        this.lastSuccessTime = lastSuccessTime;
    }

    /**
     * 记录状态检查开始
     */
    void recordStateCheckStart(final TracingContext context) {
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance_id", instanceId);
            eventAttributes.put("current_state", circuitBreaker.getState().name());
            eventAttributes.put("total_checks", totalChecks.get());
            context.addEvent("cb.state_check_start", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_state_check");
        logData.put("instance_id", instanceId);
        logData.put("current_state", circuitBreaker.getState().name());
        logData.put("total_checks", totalChecks.get());
        logData.put("success_rate", calculateSuccessRate());
        logData.put("failure_rate", calculateFailureRate());
        
        structuredLogger.logBusinessEvent("circuit_breaker_state_check", logData, context);
    }
    
    /**
     * 记录执行被允许
     */
    void recordExecutionAllowed(final TracingContext context, final Span span, final long checkTimeMs) {
        if (span != null) {
            span.setAttribute("cb.execution_allowed", true);
            span.setAttribute("cb.check_time_ms", checkTimeMs);
            span.setAttribute("cb.state_after", circuitBreaker.getState().name());
        }
        
        // 记录允许事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("execution_allowed", true);
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("current_state", circuitBreaker.getState().name());
            context.addEvent("cb.execution_allowed", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_execution_allowed");
        logData.put("instance_id", instanceId);
        logData.put("current_state", circuitBreaker.getState().name());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("allowed_executions", allowedExecutions.get());
        logData.put("total_checks", totalChecks.get());
        logData.put("allow_rate", calculateAllowRate());
        
        structuredLogger.logBusinessEvent("circuit_breaker_execution_allowed", logData, context);
        
        log.debug("熔断器允许执行: {} (状态: {}, 耗时: {}ms)", instanceId, circuitBreaker.getState(), checkTimeMs);
    }
    
    /**
     * 记录执行被拒绝
     */
    void recordExecutionRejected(final TracingContext context, final Span span, final long checkTimeMs) {
        if (span != null) {
            span.setAttribute("cb.execution_allowed", false);
            span.setAttribute("cb.check_time_ms", checkTimeMs);
            span.setAttribute("cb.state_after", circuitBreaker.getState().name());
            span.setAttribute("cb.rejection_reason", "circuit_breaker_open");
        }
        
        // 记录拒绝事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("execution_allowed", false);
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("current_state", circuitBreaker.getState().name());
            eventAttributes.put("rejection_reason", "circuit_breaker_open");
            context.addEvent("cb.execution_rejected", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_execution_rejected");
        logData.put("instance_id", instanceId);
        logData.put("current_state", circuitBreaker.getState().name());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("rejection_reason", "circuit_breaker_open");
        logData.put("rejected_executions", rejectedExecutions.get());
        logData.put("total_checks", totalChecks.get());
        logData.put("rejection_rate", calculateRejectionRate());
        
        Instant failureTime = lastFailureTime.get();
        if (failureTime != null) {
            long timeSinceLastFailure = java.time.Duration.between(failureTime, Instant.now()).toMillis();
            logData.put("time_since_last_failure_ms", timeSinceLastFailure);
        }
        
        structuredLogger.logBusinessEvent("circuit_breaker_execution_rejected", logData, context);
        
        log.warn("熔断器拒绝执行: {} (状态: {}, 耗时: {}ms)", instanceId, circuitBreaker.getState(), checkTimeMs);
    }
    
    /**
     * 记录成功开始事件
     */
    void recordSuccessStart(final TracingContext context) {
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance_id", instanceId);
            eventAttributes.put("current_state", circuitBreaker.getState().name());
            context.addEvent("cb.success_start", eventAttributes);
        }
    }
    
    /**
     * 记录成功完成事件
     */
    void recordSuccessComplete(final TracingContext context, final Span span, final long operationTimeMs) {
        if (span != null) {
            span.setAttribute("cb.operation_time_ms", operationTimeMs);
        }
        
        // 记录完成事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("operation_time_ms", operationTimeMs);
            context.addEvent("cb.success_complete", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_success");
        logData.put("instance_id", instanceId);
        logData.put("current_state", circuitBreaker.getState().name());
        logData.put("operation_time_ms", operationTimeMs);
        
        structuredLogger.logBusinessEvent("circuit_breaker_success", logData, context);
        
        log.debug("熔断器成功调用: {} (状态: {}, 耗时: {}ms)", instanceId, circuitBreaker.getState(), operationTimeMs);
    }
    
    /**
     * 记录失败开始事件
     */
    void recordFailureStart(final TracingContext context) {
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance_id", instanceId);
            eventAttributes.put("current_state", circuitBreaker.getState().name());
            context.addEvent("cb.failure_start", eventAttributes);
        }
    }
    
    /**
     * 记录失败完成事件
     */
    void recordFailureComplete(final TracingContext context, final Span span, final long operationTimeMs) {
        if (span != null) {
            span.setAttribute("cb.operation_time_ms", operationTimeMs);
        }
        
        // 记录完成事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("operation_time_ms", operationTimeMs);
            context.addEvent("cb.failure_complete", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_failure");
        logData.put("instance_id", instanceId);
        logData.put("current_state", circuitBreaker.getState().name());
        logData.put("operation_time_ms", operationTimeMs);
        
        structuredLogger.logBusinessEvent("circuit_breaker_failure", logData, context);
        
        log.debug("熔断器失败调用: {} (状态: {}, 耗时: {}ms)", instanceId, circuitBreaker.getState(), operationTimeMs);
    }
    
    /**
     * 记录状态变化
     */
    void recordStateChange(final TracingContext context, final CircuitBreaker.State fromState, final CircuitBreaker.State toState) {
        // 记录状态变化事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("state_from", fromState.name());
            eventAttributes.put("state_to", toState.name());
            eventAttributes.put("state_change_count", stateChanges.get());
            
            Instant changeTime = lastStateChangeTime.get();
            if (changeTime != null) {
                long timeSinceLastChange = java.time.Duration.between(changeTime, Instant.now()).toMillis();
                eventAttributes.put("time_since_last_change_ms", timeSinceLastChange);
            }
            
            context.addEvent("cb.state_change", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_state_change");
        logData.put("instance_id", instanceId);
        logData.put("state_from", fromState.name());
        logData.put("state_to", toState.name());
        logData.put("state_change_count", stateChanges.get());
        logData.put("success_calls", successCalls.get());
        logData.put("failure_calls", failureCalls.get());
        logData.put("success_rate", calculateSuccessRate());
        logData.put("failure_rate", calculateFailureRate());
        
        Instant changeTime = lastStateChangeTime.get();
        if (changeTime != null) {
            long timeSinceLastChange = java.time.Duration.between(changeTime, Instant.now()).toMillis();
            logData.put("time_since_last_change_ms", timeSinceLastChange);
        }
        
        // 添加状态特定信息
        switch (toState) {
            case OPEN:
                logData.put("circuit_opened", true);
                logData.put("reason", "failure_threshold_exceeded");
                break;
            case HALF_OPEN:
                logData.put("circuit_half_opened", true);
                logData.put("reason", "timeout_recovery_attempt");
                break;
            case CLOSED:
                logData.put("circuit_closed", true);
                logData.put("reason", "success_threshold_reached");
                break;
        }
        
        structuredLogger.logBusinessEvent("circuit_breaker_state_change", logData, context);
        
        log.info("熔断器状态变化: {} (状态: {} -> {})", instanceId, fromState, toState);
    }
    
    /**
     * 记录熔断器错误
     */
    void recordCircuitBreakerError(
            final TracingContext context, final Span span,
            final long operationTimeMs, final Exception error) {
        if (span != null) {
            span.setAttribute("cb.operation_time_ms", operationTimeMs);
            span.setAttribute("cb.error", error.getMessage());
        }
        
        // 记录错误事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("operation_time_ms", operationTimeMs);
            eventAttributes.put("error", error.getMessage());
            eventAttributes.put("error_type", error.getClass().getSimpleName());
            context.addEvent("cb.operation_error", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_error");
        logData.put("instance_id", instanceId);
        logData.put("current_state", circuitBreaker.getState().name());
        logData.put("operation_time_ms", operationTimeMs);
        logData.put("error", error.getMessage());
        logData.put("error_type", error.getClass().getSimpleName());
        
        structuredLogger.logBusinessEvent("circuit_breaker_error", logData, context);
        
        log.error("熔断器操作错误: {} (状态: {}, 耗时: {}ms, 错误: {})", 
                instanceId, circuitBreaker.getState(), operationTimeMs, error.getMessage(), error);
    }
    
    /**
     * 计算成功率
     */
    private double calculateSuccessRate() {
        long total = successCalls.get() + failureCalls.get();
        long success = successCalls.get();
        return total > 0 ? (double) success / total : 1.0;
    }
    
    /**
     * 计算失败率
     */
    private double calculateFailureRate() {
        long total = successCalls.get() + failureCalls.get();
        long failure = failureCalls.get();
        return total > 0 ? (double) failure / total : 0.0;
    }
    
    /**
     * 计算允许率
     */
    private double calculateAllowRate() {
        long total = totalChecks.get();
        long allowed = allowedExecutions.get();
        return total > 0 ? (double) allowed / total : 1.0;
    }
    
    /**
     * 计算拒绝率
     */
    private double calculateRejectionRate() {
        long total = totalChecks.get();
        long rejected = rejectedExecutions.get();
        return total > 0 ? (double) rejected / total : 0.0;
    }
    
    /**
     * 获取熔断器统计信息
     */
    Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("instance_id", instanceId);
        stats.put("current_state", circuitBreaker.getState().name());
        stats.put("total_checks", totalChecks.get());
        stats.put("allowed_executions", allowedExecutions.get());
        stats.put("rejected_executions", rejectedExecutions.get());
        stats.put("success_calls", successCalls.get());
        stats.put("failure_calls", failureCalls.get());
        stats.put("state_changes", stateChanges.get());
        stats.put("success_rate", calculateSuccessRate());
        stats.put("failure_rate", calculateFailureRate());
        stats.put("allow_rate", calculateAllowRate());
        stats.put("rejection_rate", calculateRejectionRate());
        
        Instant changeTime = lastStateChangeTime.get();
        if (changeTime != null) {
            long timeSinceLastChange = java.time.Duration.between(changeTime, Instant.now()).toMillis();
            stats.put("time_since_last_state_change_ms", timeSinceLastChange);
        }
        
        Instant failureTime = lastFailureTime.get();
        if (failureTime != null) {
            long timeSinceLastFailure = java.time.Duration.between(failureTime, Instant.now()).toMillis();
            stats.put("time_since_last_failure_ms", timeSinceLastFailure);
        }
        
        Instant successTime = lastSuccessTime.get();
        if (successTime != null) {
            long timeSinceLastSuccess = java.time.Duration.between(successTime, Instant.now()).toMillis();
            stats.put("time_since_last_success_ms", timeSinceLastSuccess);
        }
        
        return stats;
    }
}

package org.unreal.modelrouter.tracing.wrapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 熔断器追踪包装器
 * 
 * 为熔断器添加分布式追踪功能，记录：
 * - 熔断器状态检查过程
 * - 熔断器状态变化事件
 * - 失败率和恢复时间
 * - 成功/失败调用统计
 * - 熔断触发和恢复详情
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreakerTracingWrapper implements CircuitBreaker {
    
    private final CircuitBreaker delegate;
    private final StructuredLogger structuredLogger;
    private final String instanceId;
    
    // 统计信息
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong allowedExecutions = new AtomicLong(0);
    private final AtomicLong rejectedExecutions = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);
    private final AtomicLong failureCalls = new AtomicLong(0);
    private final AtomicLong stateChanges = new AtomicLong(0);
    
    // 状态变化时间记录
    private volatile Instant lastStateChangeTime = Instant.now();
    private volatile Instant lastFailureTime;
    private volatile Instant lastSuccessTime;
    private volatile State previousState = State.CLOSED;
    
    public CircuitBreakerTracingWrapper(CircuitBreaker delegate, StructuredLogger structuredLogger, String instanceId) {
        this.delegate = delegate;
        this.structuredLogger = structuredLogger;
        this.instanceId = instanceId != null ? instanceId : "unknown";
    }
    
    @Override
    public boolean canExecute() {
        TracingContext context = TracingContextHolder.getCurrentContext();
        Span span = null;
        Instant startTime = Instant.now();
        
        try {
            // 创建熔断器追踪Span
            if (context != null && context.isActive()) {
                span = context.createChildSpan("circuit-breaker", SpanKind.INTERNAL, context.getCurrentSpan());
                context.setCurrentSpan(span);
                
                // 设置基础属性
                span.setAttribute("cb.instance_id", instanceId);
                span.setAttribute("cb.current_state", getState().name());
                span.setAttribute("cb.operation", "can_execute");
            }
            
            // 记录状态检查开始
            recordStateCheckStart(context);
            
            // 执行状态检查
            boolean canExecute = delegate.canExecute();
            
            // 计算检查时间
            long checkTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            
            // 更新统计信息
            totalChecks.incrementAndGet();
            
            if (canExecute) {
                allowedExecutions.incrementAndGet();
                recordExecutionAllowed(context, span, checkTimeMs);
            } else {
                rejectedExecutions.incrementAndGet();
                recordExecutionRejected(context, span, checkTimeMs);
            }
            
            // 检查状态是否发生变化
            State currentState = getState();
            if (currentState != previousState) {
                recordStateChange(context, previousState, currentState);
                previousState = currentState;
                lastStateChangeTime = Instant.now();
                stateChanges.incrementAndGet();
            }
            
            return canExecute;
            
        } catch (Exception e) {
            // 记录异常
            long checkTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            recordCircuitBreakerError(context, span, checkTimeMs, e);
            
            if (context != null && span != null) {
                context.finishSpan(span, e);
            }
            
            throw e;
        } finally {
            // 完成Span
            if (context != null && span != null) {
                context.finishSpan(span);
            }
        }
    }
    
    @Override
    public void onSuccess() {
        TracingContext context = TracingContextHolder.getCurrentContext();
        Span span = null;
        
        try {
            // 创建成功调用追踪Span
            if (context != null && context.isActive()) {
                span = context.createChildSpan("circuit-breaker-success", SpanKind.INTERNAL, context.getCurrentSpan());
                context.setCurrentSpan(span);
                
                span.setAttribute("cb.instance_id", instanceId);
                span.setAttribute("cb.operation", "on_success");
                span.setAttribute("cb.state_before", getState().name());
            }
            
            State stateBefore = getState();
            
            // 执行成功处理
            delegate.onSuccess();
            
            State stateAfter = getState();
            
            // 更新统计信息
            successCalls.incrementAndGet();
            lastSuccessTime = Instant.now();
            
            // 记录成功调用
            recordSuccessCall(context, span, stateBefore, stateAfter);
            
            // 检查状态变化
            if (stateBefore != stateAfter) {
                recordStateChange(context, stateBefore, stateAfter);
                previousState = stateAfter;
                lastStateChangeTime = Instant.now();
                stateChanges.incrementAndGet();
            }
            
        } catch (Exception e) {
            recordCircuitBreakerError(context, span, 0, e);
            
            if (context != null && span != null) {
                context.finishSpan(span, e);
            }
            
            throw e;
        } finally {
            // 完成Span
            if (context != null && span != null) {
                context.finishSpan(span);
            }
        }
    }
    
    @Override
    public void onFailure() {
        TracingContext context = TracingContextHolder.getCurrentContext();
        Span span = null;
        
        try {
            // 创建失败调用追踪Span
            if (context != null && context.isActive()) {
                span = context.createChildSpan("circuit-breaker-failure", SpanKind.INTERNAL, context.getCurrentSpan());
                context.setCurrentSpan(span);
                
                span.setAttribute("cb.instance_id", instanceId);
                span.setAttribute("cb.operation", "on_failure");
                span.setAttribute("cb.state_before", getState().name());
            }
            
            State stateBefore = getState();
            
            // 执行失败处理
            delegate.onFailure();
            
            State stateAfter = getState();
            
            // 更新统计信息
            failureCalls.incrementAndGet();
            lastFailureTime = Instant.now();
            
            // 记录失败调用
            recordFailureCall(context, span, stateBefore, stateAfter);
            
            // 检查状态变化
            if (stateBefore != stateAfter) {
                recordStateChange(context, stateBefore, stateAfter);
                previousState = stateAfter;
                lastStateChangeTime = Instant.now();
                stateChanges.incrementAndGet();
            }
            
        } catch (Exception e) {
            recordCircuitBreakerError(context, span, 0, e);
            
            if (context != null && span != null) {
                context.finishSpan(span, e);
            }
            
            throw e;
        } finally {
            // 完成Span
            if (context != null && span != null) {
                context.finishSpan(span);
            }
        }
    }
    
    @Override
    public State getState() {
        return delegate.getState();
    }
    
    /**
     * 记录状态检查开始
     */
    private void recordStateCheckStart(TracingContext context) {
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance_id", instanceId);
            eventAttributes.put("current_state", getState().name());
            eventAttributes.put("total_checks", totalChecks.get());
            context.addEvent("cb.state_check_start", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_state_check");
        logData.put("instance_id", instanceId);
        logData.put("current_state", getState().name());
        logData.put("total_checks", totalChecks.get());
        logData.put("success_rate", calculateSuccessRate());
        logData.put("failure_rate", calculateFailureRate());
        
        structuredLogger.logBusinessEvent("circuit_breaker_state_check", logData, context);
    }
    
    /**
     * 记录执行被允许
     */
    private void recordExecutionAllowed(TracingContext context, Span span, long checkTimeMs) {
        if (span != null) {
            span.setAttribute("cb.execution_allowed", true);
            span.setAttribute("cb.check_time_ms", checkTimeMs);
            span.setAttribute("cb.state_after", getState().name());
        }
        
        // 记录允许事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("execution_allowed", true);
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("current_state", getState().name());
            context.addEvent("cb.execution_allowed", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_execution_allowed");
        logData.put("instance_id", instanceId);
        logData.put("current_state", getState().name());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("allowed_executions", allowedExecutions.get());
        logData.put("total_checks", totalChecks.get());
        logData.put("allow_rate", calculateAllowRate());
        
        structuredLogger.logBusinessEvent("circuit_breaker_execution_allowed", logData, context);
        
        log.debug("熔断器允许执行: {} (状态: {}, 耗时: {}ms)", instanceId, getState(), checkTimeMs);
    }
    
    /**
     * 记录执行被拒绝
     */
    private void recordExecutionRejected(TracingContext context, Span span, long checkTimeMs) {
        if (span != null) {
            span.setAttribute("cb.execution_allowed", false);
            span.setAttribute("cb.check_time_ms", checkTimeMs);
            span.setAttribute("cb.state_after", getState().name());
            span.setAttribute("cb.rejection_reason", "circuit_breaker_open");
        }
        
        // 记录拒绝事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("execution_allowed", false);
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("current_state", getState().name());
            eventAttributes.put("rejection_reason", "circuit_breaker_open");
            context.addEvent("cb.execution_rejected", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_execution_rejected");
        logData.put("instance_id", instanceId);
        logData.put("current_state", getState().name());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("rejection_reason", "circuit_breaker_open");
        logData.put("rejected_executions", rejectedExecutions.get());
        logData.put("total_checks", totalChecks.get());
        logData.put("rejection_rate", calculateRejectionRate());
        
        if (lastFailureTime != null) {
            long timeSinceLastFailure = java.time.Duration.between(lastFailureTime, Instant.now()).toMillis();
            logData.put("time_since_last_failure_ms", timeSinceLastFailure);
        }
        
        structuredLogger.logBusinessEvent("circuit_breaker_execution_rejected", logData, context);
        
        log.warn("熔断器拒绝执行: {} (状态: {}, 耗时: {}ms)", instanceId, getState(), checkTimeMs);
    }
    
    /**
     * 记录成功调用
     */
    private void recordSuccessCall(TracingContext context, Span span, State stateBefore, State stateAfter) {
        if (span != null) {
            span.setAttribute("cb.call_result", "success");
            span.setAttribute("cb.state_after", stateAfter.name());
            span.setAttribute("cb.state_changed", stateBefore != stateAfter);
        }
        
        // 记录成功事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("call_result", "success");
            eventAttributes.put("state_before", stateBefore.name());
            eventAttributes.put("state_after", stateAfter.name());
            eventAttributes.put("state_changed", stateBefore != stateAfter);
            context.addEvent("cb.call_success", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_call_success");
        logData.put("instance_id", instanceId);
        logData.put("state_before", stateBefore.name());
        logData.put("state_after", stateAfter.name());
        logData.put("state_changed", stateBefore != stateAfter);
        logData.put("success_calls", successCalls.get());
        logData.put("total_calls", successCalls.get() + failureCalls.get());
        logData.put("success_rate", calculateSuccessRate());
        
        structuredLogger.logBusinessEvent("circuit_breaker_call_success", logData, context);
        
        log.debug("熔断器记录成功调用: {} (状态: {} -> {})", instanceId, stateBefore, stateAfter);
    }
    
    /**
     * 记录失败调用
     */
    private void recordFailureCall(TracingContext context, Span span, State stateBefore, State stateAfter) {
        if (span != null) {
            span.setAttribute("cb.call_result", "failure");
            span.setAttribute("cb.state_after", stateAfter.name());
            span.setAttribute("cb.state_changed", stateBefore != stateAfter);
        }
        
        // 记录失败事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("call_result", "failure");
            eventAttributes.put("state_before", stateBefore.name());
            eventAttributes.put("state_after", stateAfter.name());
            eventAttributes.put("state_changed", stateBefore != stateAfter);
            context.addEvent("cb.call_failure", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "circuit_breaker_call_failure");
        logData.put("instance_id", instanceId);
        logData.put("state_before", stateBefore.name());
        logData.put("state_after", stateAfter.name());
        logData.put("state_changed", stateBefore != stateAfter);
        logData.put("failure_calls", failureCalls.get());
        logData.put("total_calls", successCalls.get() + failureCalls.get());
        logData.put("failure_rate", calculateFailureRate());
        
        structuredLogger.logBusinessEvent("circuit_breaker_call_failure", logData, context);
        
        log.warn("熔断器记录失败调用: {} (状态: {} -> {})", instanceId, stateBefore, stateAfter);
    }
    
    /**
     * 记录状态变化
     */
    private void recordStateChange(TracingContext context, State fromState, State toState) {
        // 记录状态变化事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("state_from", fromState.name());
            eventAttributes.put("state_to", toState.name());
            eventAttributes.put("state_change_count", stateChanges.get());
            
            if (lastStateChangeTime != null) {
                long timeSinceLastChange = java.time.Duration.between(lastStateChangeTime, Instant.now()).toMillis();
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
        
        if (lastStateChangeTime != null) {
            long timeSinceLastChange = java.time.Duration.between(lastStateChangeTime, Instant.now()).toMillis();
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
    private void recordCircuitBreakerError(TracingContext context, Span span, long operationTimeMs, Exception error) {
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
        logData.put("current_state", getState().name());
        logData.put("operation_time_ms", operationTimeMs);
        logData.put("error", error.getMessage());
        logData.put("error_type", error.getClass().getSimpleName());
        
        structuredLogger.logBusinessEvent("circuit_breaker_error", logData, context);
        
        log.error("熔断器操作错误: {} (状态: {}, 耗时: {}ms, 错误: {})", 
                instanceId, getState(), operationTimeMs, error.getMessage(), error);
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
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("instance_id", instanceId);
        stats.put("current_state", getState().name());
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
        
        if (lastStateChangeTime != null) {
            long timeSinceLastChange = java.time.Duration.between(lastStateChangeTime, Instant.now()).toMillis();
            stats.put("time_since_last_state_change_ms", timeSinceLastChange);
        }
        
        if (lastFailureTime != null) {
            long timeSinceLastFailure = java.time.Duration.between(lastFailureTime, Instant.now()).toMillis();
            stats.put("time_since_last_failure_ms", timeSinceLastFailure);
        }
        
        if (lastSuccessTime != null) {
            long timeSinceLastSuccess = java.time.Duration.between(lastSuccessTime, Instant.now()).toMillis();
            stats.put("time_since_last_success_ms", timeSinceLastSuccess);
        }
        
        return stats;
    }
}
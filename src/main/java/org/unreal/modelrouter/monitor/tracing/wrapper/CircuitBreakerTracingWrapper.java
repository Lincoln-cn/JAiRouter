package org.unreal.modelrouter.monitor.tracing.wrapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
public final class CircuitBreakerTracingWrapper implements CircuitBreaker {
    
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
    private final AtomicReference<Instant> lastStateChangeTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>();
    private State previousState = State.CLOSED;
    
    // 委托助手，包含记录方法和统计计算
    private final CircuitBreakerTracingDelegate tracingDelegate;
    
    public CircuitBreakerTracingWrapper(
            final CircuitBreaker delegate,
            final StructuredLogger structuredLogger,
            final String instanceId) {
        this.delegate = delegate;
        this.structuredLogger = structuredLogger;
        this.instanceId = instanceId != null ? instanceId : "unknown";
        this.tracingDelegate = new CircuitBreakerTracingDelegate(
                this.instanceId, delegate, structuredLogger,
                totalChecks, allowedExecutions, rejectedExecutions,
                successCalls, failureCalls, stateChanges,
                lastStateChangeTime, lastFailureTime, lastSuccessTime);
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
            tracingDelegate.recordStateCheckStart(context);
            
            // 执行状态检查
            boolean canExecute = delegate.canExecute();
            
            // 计算检查时间
            long checkTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            
            // 更新统计信息
            totalChecks.incrementAndGet();
            
            if (canExecute) {
                allowedExecutions.incrementAndGet();
                tracingDelegate.recordExecutionAllowed(context, span, checkTimeMs);
            } else {
                rejectedExecutions.incrementAndGet();
                tracingDelegate.recordExecutionRejected(context, span, checkTimeMs);
            }
            
            // 检查状态是否发生变化
            State currentState = getState();
            if (currentState != previousState) {
                tracingDelegate.recordStateChange(context, previousState, currentState);
                previousState = currentState;
                lastStateChangeTime.set(Instant.now());
                stateChanges.incrementAndGet();
            }
            
            return canExecute;
            
        } catch (Exception e) {
            // 记录异常
            long checkTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            tracingDelegate.recordCircuitBreakerError(context, span, checkTimeMs, e);
            
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
        Instant startTime = Instant.now();
        
        try {
            // 创建熔断器追踪Span
            if (context != null && context.isActive()) {
                span = context.createChildSpan("circuit-breaker", SpanKind.INTERNAL, context.getCurrentSpan());
                context.setCurrentSpan(span);
                
                // 设置基础属性
                span.setAttribute("cb.instance_id", instanceId);
                span.setAttribute("cb.current_state", getState().name());
                span.setAttribute("cb.operation", "on_success");
            }
            
            // 记录成功开始
            tracingDelegate.recordSuccessStart(context);
            
            // 执行成功回调
            delegate.onSuccess();
            
            // 计算操作时间
            long operationTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            
            // 更新统计信息
            successCalls.incrementAndGet();
            lastSuccessTime.set(Instant.now());
            
            // 记录成功完成
            tracingDelegate.recordSuccessComplete(context, span, operationTimeMs);
            
        } catch (Exception e) {
            // 记录错误
            long operationTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            tracingDelegate.recordCircuitBreakerError(context, span, operationTimeMs, e);
            
            throw e;
        } finally {
            // 完成Span
            if (span != null) {
                context.finishSpan(span);
                context.setCurrentSpan(span);
            }
        }
    }
    
    @Override
    public void onFailure() {
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
                span.setAttribute("cb.operation", "on_failure");
            }
            
            // 记录失败开始
            tracingDelegate.recordFailureStart(context);
            
            // 执行失败回调
            delegate.onFailure();
            
            // 计算操作时间
            long operationTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            
            // 更新统计信息
            failureCalls.incrementAndGet();
            lastFailureTime.set(Instant.now());
            
            // 记录失败完成
            tracingDelegate.recordFailureComplete(context, span, operationTimeMs);
            
        } catch (Exception e) {
            // 记录错误
            long operationTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            tracingDelegate.recordCircuitBreakerError(context, span, operationTimeMs, e);
            
            throw e;
        } finally {
            // 完成Span
            if (span != null) {
                context.finishSpan(span);
                context.setCurrentSpan(span);
            }
        }
    }
    
    @Override
    public State getState() {
        return delegate.getState();
    }
    
    /**
     * 获取熔断器统计信息
     */
    public Map<String, Object> getStatistics() {
        return tracingDelegate.getStatistics();
    }
}

package org.unreal.modelrouter.tracing.wrapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.ratelimit.RateLimitConfig;
import org.unreal.modelrouter.ratelimit.RateLimitContext;
import org.unreal.modelrouter.ratelimit.RateLimiter;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流器追踪包装器
 * 
 * 为限流器添加分布式追踪功能，记录：
 * - 限流检查过程
 * - 限流算法和配置
 * - 剩余配额信息
 * - 限流决策结果
 * - 通过率和拒绝率统计
 * - 限流触发详情
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class RateLimiterTracingWrapper implements RateLimiter {
    
    private final RateLimiter delegate;
    private final StructuredLogger structuredLogger;
    
    public RateLimiterTracingWrapper(RateLimiter delegate, StructuredLogger structuredLogger) {
        this.delegate = delegate;
        this.structuredLogger = structuredLogger;
    }
    
    // 统计信息
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    private final Map<String, AtomicLong> serviceTypeAllowed = new HashMap<>();
    private final Map<String, AtomicLong> serviceTypeRejected = new HashMap<>();
    private final Map<String, AtomicLong> clientIpAllowed = new HashMap<>();
    private final Map<String, AtomicLong> clientIpRejected = new HashMap<>();
    
    @Override
    public boolean tryAcquire(RateLimitContext context) {
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        Span span = null;
        Instant startTime = Instant.now();
        
        try {
            // 创建限流追踪Span
            if (tracingContext != null && tracingContext.isActive()) {
                span = tracingContext.createChildSpan("rate-limiter", SpanKind.INTERNAL, tracingContext.getCurrentSpan());
                tracingContext.setCurrentSpan(span);
                
                // 设置基础属性
                span.setAttribute("rl.algorithm", getConfig().getAlgorithm());
                span.setAttribute("rl.capacity", getConfig().getCapacity());
                span.setAttribute("rl.rate", getConfig().getRate());
                span.setAttribute("rl.scope", getConfig().getScope());
                span.setAttribute("rl.service_type", context.getServiceType().toString());
                span.setAttribute("rl.model_name", context.getModelName() != null ? context.getModelName() : "unknown");
                span.setAttribute("rl.client_ip", context.getClientIp() != null ? context.getClientIp() : "unknown");
                span.setAttribute("rl.tokens_requested", context.getTokens());
                
                if (context.hasInstanceInfo()) {
                    span.setAttribute("rl.instance_id", context.getInstanceId());
                    span.setAttribute("rl.instance_url", context.getInstanceUrl());
                }
            }
            
            // 记录限流检查开始
            recordRateLimitCheckStart(tracingContext, context);
            
            // 执行限流检查
            boolean allowed = delegate.tryAcquire(context);
            
            // 计算检查时间
            long checkTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            
            // 更新统计信息
            totalRequests.incrementAndGet();
            String serviceType = context.getServiceType().toString();
            String clientIp = context.getClientIp() != null ? context.getClientIp() : "unknown";
            
            if (allowed) {
                // 记录允许通过
                recordAllowedRequest(tracingContext, span, context, checkTimeMs);
                allowedRequests.incrementAndGet();
                serviceTypeAllowed.computeIfAbsent(serviceType, k -> new AtomicLong(0)).incrementAndGet();
                clientIpAllowed.computeIfAbsent(clientIp, k -> new AtomicLong(0)).incrementAndGet();
            } else {
                // 记录限流拒绝
                recordRejectedRequest(tracingContext, span, context, checkTimeMs);
                rejectedRequests.incrementAndGet();
                serviceTypeRejected.computeIfAbsent(serviceType, k -> new AtomicLong(0)).incrementAndGet();
                clientIpRejected.computeIfAbsent(clientIp, k -> new AtomicLong(0)).incrementAndGet();
            }
            
            return allowed;
            
        } catch (Exception e) {
            // 记录异常
            long checkTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            recordRateLimitError(tracingContext, span, context, checkTimeMs, e);
            
            if (tracingContext != null && span != null) {
                tracingContext.finishSpan(span, e);
            }
            
            throw e;
        } finally {
            // 完成Span
            if (tracingContext != null && span != null) {
                tracingContext.finishSpan(span);
            }
        }
    }
    
    @Override
    public RateLimitConfig getConfig() {
        return delegate.getConfig();
    }
    
    /**
     * 记录限流检查开始
     */
    private void recordRateLimitCheckStart(TracingContext context, RateLimitContext rateLimitContext) {
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("service_type", rateLimitContext.getServiceType().toString());
            eventAttributes.put("model_name", rateLimitContext.getModelName());
            eventAttributes.put("client_ip", rateLimitContext.getClientIp());
            eventAttributes.put("tokens_requested", rateLimitContext.getTokens());
            eventAttributes.put("algorithm", getConfig().getAlgorithm());
            eventAttributes.put("capacity", getConfig().getCapacity());
            eventAttributes.put("rate", getConfig().getRate());
            context.addEvent("rl.check_start", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "rate_limit_check_start");
        logData.put("service_type", rateLimitContext.getServiceType().toString());
        logData.put("model_name", rateLimitContext.getModelName());
        logData.put("client_ip", rateLimitContext.getClientIp());
        logData.put("tokens_requested", rateLimitContext.getTokens());
        logData.put("algorithm", getConfig().getAlgorithm());
        logData.put("capacity", getConfig().getCapacity());
        logData.put("rate", getConfig().getRate());
        logData.put("scope", getConfig().getScope());
        
        if (rateLimitContext.hasInstanceInfo()) {
            logData.put("instance_id", rateLimitContext.getInstanceId());
            logData.put("instance_url", rateLimitContext.getInstanceUrl());
        }
        
        structuredLogger.logBusinessEvent("rate_limit_check_start", logData, context);
    }
    
    /**
     * 记录允许通过的请求
     */
    private void recordAllowedRequest(TracingContext context, Span span, 
                                    RateLimitContext rateLimitContext, long checkTimeMs) {
        if (span != null) {
            span.setAttribute("rl.allowed", true);
            span.setAttribute("rl.check_time_ms", checkTimeMs);
        }
        
        // 记录允许事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("allowed", true);
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("remaining_capacity", calculateRemainingCapacity());
            context.addEvent("rl.request_allowed", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "rate_limit_allowed");
        logData.put("service_type", rateLimitContext.getServiceType().toString());
        logData.put("model_name", rateLimitContext.getModelName());
        logData.put("client_ip", rateLimitContext.getClientIp());
        logData.put("tokens_requested", rateLimitContext.getTokens());
        logData.put("algorithm", getConfig().getAlgorithm());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("allowed", true);
        logData.put("total_requests", totalRequests.get());
        logData.put("allowed_requests", allowedRequests.get());
        logData.put("pass_rate", calculatePassRate());
        logData.put("remaining_capacity", calculateRemainingCapacity());
        
        // 添加服务类型统计
        String serviceType = rateLimitContext.getServiceType().toString();
        logData.put("service_pass_rate", calculateServicePassRate(serviceType));
        
        // 添加客户端IP统计
        String clientIp = rateLimitContext.getClientIp() != null ? rateLimitContext.getClientIp() : "unknown";
        logData.put("client_pass_rate", calculateClientPassRate(clientIp));
        
        structuredLogger.logBusinessEvent("rate_limit_allowed", logData, context);
        
        log.debug("限流检查通过: {} - {} (耗时: {}ms, 算法: {}, 通过率: {:.2f}%)", 
                rateLimitContext.getServiceType(), rateLimitContext.getModelName(), 
                checkTimeMs, getConfig().getAlgorithm(), calculatePassRate() * 100);
    }
    
    /**
     * 记录被拒绝的请求
     */
    private void recordRejectedRequest(TracingContext context, Span span, 
                                     RateLimitContext rateLimitContext, long checkTimeMs) {
        if (span != null) {
            span.setAttribute("rl.allowed", false);
            span.setAttribute("rl.check_time_ms", checkTimeMs);
            span.setAttribute("rl.rejection_reason", "rate_limit_exceeded");
        }
        
        // 记录拒绝事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("allowed", false);
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("rejection_reason", "rate_limit_exceeded");
            eventAttributes.put("remaining_capacity", calculateRemainingCapacity());
            context.addEvent("rl.request_rejected", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "rate_limit_rejected");
        logData.put("service_type", rateLimitContext.getServiceType().toString());
        logData.put("model_name", rateLimitContext.getModelName());
        logData.put("client_ip", rateLimitContext.getClientIp());
        logData.put("tokens_requested", rateLimitContext.getTokens());
        logData.put("algorithm", getConfig().getAlgorithm());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("allowed", false);
        logData.put("rejection_reason", "rate_limit_exceeded");
        logData.put("total_requests", totalRequests.get());
        logData.put("rejected_requests", rejectedRequests.get());
        logData.put("rejection_rate", calculateRejectionRate());
        logData.put("remaining_capacity", calculateRemainingCapacity());
        
        // 添加服务类型统计
        String serviceType = rateLimitContext.getServiceType().toString();
        logData.put("service_rejection_rate", calculateServiceRejectionRate(serviceType));
        
        // 添加客户端IP统计
        String clientIp = rateLimitContext.getClientIp() != null ? rateLimitContext.getClientIp() : "unknown";
        logData.put("client_rejection_rate", calculateClientRejectionRate(clientIp));
        
        structuredLogger.logBusinessEvent("rate_limit_rejected", logData, context);
        
        log.warn("限流检查拒绝: {} - {} (耗时: {}ms, 算法: {}, 拒绝率: {:.2f}%)", 
                rateLimitContext.getServiceType(), rateLimitContext.getModelName(), 
                checkTimeMs, getConfig().getAlgorithm(), calculateRejectionRate() * 100);
    }
    
    /**
     * 记录限流检查错误
     */
    private void recordRateLimitError(TracingContext context, Span span, 
                                    RateLimitContext rateLimitContext, long checkTimeMs, Exception error) {
        if (span != null) {
            span.setAttribute("rl.check_time_ms", checkTimeMs);
            span.setAttribute("rl.error", error.getMessage());
        }
        
        // 记录错误事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("check_time_ms", checkTimeMs);
            eventAttributes.put("error", error.getMessage());
            eventAttributes.put("error_type", error.getClass().getSimpleName());
            context.addEvent("rl.check_error", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "rate_limit_error");
        logData.put("service_type", rateLimitContext.getServiceType().toString());
        logData.put("model_name", rateLimitContext.getModelName());
        logData.put("client_ip", rateLimitContext.getClientIp());
        logData.put("tokens_requested", rateLimitContext.getTokens());
        logData.put("algorithm", getConfig().getAlgorithm());
        logData.put("check_time_ms", checkTimeMs);
        logData.put("error", error.getMessage());
        logData.put("error_type", error.getClass().getSimpleName());
        
        structuredLogger.logBusinessEvent("rate_limit_error", logData, context);
        
        log.error("限流检查错误: {} - {} (耗时: {}ms, 错误: {})", 
                rateLimitContext.getServiceType(), rateLimitContext.getModelName(), 
                checkTimeMs, error.getMessage(), error);
    }
    
    /**
     * 计算通过率
     */
    private double calculatePassRate() {
        long total = totalRequests.get();
        long allowed = allowedRequests.get();
        return total > 0 ? (double) allowed / total : 1.0;
    }
    
    /**
     * 计算拒绝率
     */
    private double calculateRejectionRate() {
        long total = totalRequests.get();
        long rejected = rejectedRequests.get();
        return total > 0 ? (double) rejected / total : 0.0;
    }
    
    /**
     * 计算服务类型通过率
     */
    private double calculateServicePassRate(String serviceType) {
        AtomicLong allowed = serviceTypeAllowed.get(serviceType);
        AtomicLong rejected = serviceTypeRejected.get(serviceType);
        
        if (allowed == null && rejected == null) {
            return 1.0;
        }
        
        long allowedCount = allowed != null ? allowed.get() : 0;
        long rejectedCount = rejected != null ? rejected.get() : 0;
        long totalCount = allowedCount + rejectedCount;
        
        return totalCount > 0 ? (double) allowedCount / totalCount : 1.0;
    }
    
    /**
     * 计算服务类型拒绝率
     */
    private double calculateServiceRejectionRate(String serviceType) {
        return 1.0 - calculateServicePassRate(serviceType);
    }
    
    /**
     * 计算客户端IP通过率
     */
    private double calculateClientPassRate(String clientIp) {
        AtomicLong allowed = clientIpAllowed.get(clientIp);
        AtomicLong rejected = clientIpRejected.get(clientIp);
        
        if (allowed == null && rejected == null) {
            return 1.0;
        }
        
        long allowedCount = allowed != null ? allowed.get() : 0;
        long rejectedCount = rejected != null ? rejected.get() : 0;
        long totalCount = allowedCount + rejectedCount;
        
        return totalCount > 0 ? (double) allowedCount / totalCount : 1.0;
    }
    
    /**
     * 计算客户端IP拒绝率
     */
    private double calculateClientRejectionRate(String clientIp) {
        return 1.0 - calculateClientPassRate(clientIp);
    }
    
    /**
     * 计算剩余容量（估算值）
     */
    private long calculateRemainingCapacity() {
        // 这是一个估算值，实际实现可能需要从底层限流器获取
        // 这里返回配置的容量作为占位符
        return getConfig().getCapacity();
    }
    
    /**
     * 获取限流器统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_requests", totalRequests.get());
        stats.put("allowed_requests", allowedRequests.get());
        stats.put("rejected_requests", rejectedRequests.get());
        stats.put("pass_rate", calculatePassRate());
        stats.put("rejection_rate", calculateRejectionRate());
        stats.put("algorithm", getConfig().getAlgorithm());
        stats.put("capacity", getConfig().getCapacity());
        stats.put("rate", getConfig().getRate());
        stats.put("scope", getConfig().getScope());
        
        // 服务类型级别统计
        Map<String, Object> serviceStats = new HashMap<>();
        for (String serviceType : serviceTypeAllowed.keySet()) {
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("allowed_count", serviceTypeAllowed.get(serviceType).get());
            serviceData.put("rejected_count", serviceTypeRejected.getOrDefault(serviceType, new AtomicLong(0)).get());
            serviceData.put("pass_rate", calculateServicePassRate(serviceType));
            serviceData.put("rejection_rate", calculateServiceRejectionRate(serviceType));
            serviceStats.put(serviceType, serviceData);
        }
        stats.put("service_statistics", serviceStats);
        
        // 客户端IP级别统计
        Map<String, Object> clientStats = new HashMap<>();
        for (String clientIp : clientIpAllowed.keySet()) {
            Map<String, Object> clientData = new HashMap<>();
            clientData.put("allowed_count", clientIpAllowed.get(clientIp).get());
            clientData.put("rejected_count", clientIpRejected.getOrDefault(clientIp, new AtomicLong(0)).get());
            clientData.put("pass_rate", calculateClientPassRate(clientIp));
            clientData.put("rejection_rate", calculateClientRejectionRate(clientIp));
            clientStats.put(clientIp, clientData);
        }
        stats.put("client_statistics", clientStats);
        
        return stats;
    }
}
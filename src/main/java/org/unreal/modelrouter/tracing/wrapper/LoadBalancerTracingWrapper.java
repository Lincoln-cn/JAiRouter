package org.unreal.modelrouter.tracing.wrapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.loadbalancer.LoadBalancer;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 负载均衡器追踪包装器
 * 
 * 为负载均衡器添加分布式追踪功能，记录：
 * - 负载均衡决策过程
 * - 候选实例信息
 * - 选中实例详情
 * - 决策时间统计
 * - 成功率和失败率
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class LoadBalancerTracingWrapper implements LoadBalancer {
    
    private final LoadBalancer delegate;
    private final StructuredLogger structuredLogger;
    
    public LoadBalancerTracingWrapper(LoadBalancer delegate, StructuredLogger structuredLogger) {
        this.delegate = delegate;
        this.structuredLogger = structuredLogger;
    }
    
    // 统计信息
    private final AtomicLong totalSelections = new AtomicLong(0);
    private final AtomicLong successfulSelections = new AtomicLong(0);
    private final AtomicLong failedSelections = new AtomicLong(0);
    private final Map<String, AtomicLong> instanceSelectionCount = new HashMap<>();
    private final Map<String, AtomicLong> instanceSuccessCount = new HashMap<>();
    private final Map<String, AtomicLong> instanceFailureCount = new HashMap<>();
    
    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            List<ModelRouterProperties.ModelInstance> instances, String clientIp) {
        return selectInstance(instances, clientIp, "unknown");
    }
    
    @Override
    public ModelRouterProperties.ModelInstance selectInstance(
            List<ModelRouterProperties.ModelInstance> instances, String clientIp, String serviceType) {
        
        TracingContext context = TracingContextHolder.getCurrentContext();
        Span span = null;
        Instant startTime = Instant.now();
        
        try {
            // 创建负载均衡追踪Span
            if (context != null && context.isActive()) {
                span = context.createChildSpan("load-balancer", SpanKind.INTERNAL, context.getCurrentSpan());
                context.setCurrentSpan(span);
                
                // 设置基础属性
                span.setAttribute("lb.service_type", serviceType);
                span.setAttribute("lb.client_ip", clientIp != null ? clientIp : "unknown");
                span.setAttribute("lb.candidates_count", instances != null ? instances.size() : 0);
                span.setAttribute("lb.strategy", getStrategyName());
            }
            
            // 记录候选实例信息
            if (instances != null && !instances.isEmpty()) {
                recordCandidateInstances(context, instances, serviceType);
            }
            
            // 执行负载均衡选择
            ModelRouterProperties.ModelInstance selectedInstance = delegate.selectInstance(instances, clientIp, serviceType);
            
            // 计算决策时间
            long decisionTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            
            if (selectedInstance != null) {
                // 记录成功选择
                recordSuccessfulSelection(context, span, selectedInstance, decisionTimeMs, serviceType);
                successfulSelections.incrementAndGet();
                instanceSelectionCount.computeIfAbsent(selectedInstance.getName(), k -> new AtomicLong(0)).incrementAndGet();
            } else {
                // 记录选择失败
                recordFailedSelection(context, span, decisionTimeMs, serviceType, "No instance selected");
                failedSelections.incrementAndGet();
            }
            
            totalSelections.incrementAndGet();
            return selectedInstance;
            
        } catch (Exception e) {
            // 记录异常
            long decisionTimeMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            recordFailedSelection(context, span, decisionTimeMs, serviceType, e.getMessage());
            failedSelections.incrementAndGet();
            totalSelections.incrementAndGet();
            
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
    public void recordCall(ModelRouterProperties.ModelInstance instance) {
        delegate.recordCall(instance);
        
        // 记录调用开始事件
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance.name", instance.getName());
            eventAttributes.put("instance.base_url", instance.getBaseUrl());
            eventAttributes.put("event.type", "call_start");
            context.addEvent("lb.instance_call_start", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "load_balancer_call_start");
        logData.put("instance_name", instance.getName());
        logData.put("instance_url", instance.getBaseUrl());
        logData.put("instance_weight", instance.getWeight());
        structuredLogger.logBusinessEvent("load_balancer_call_start", logData, context);
    }
    
    @Override
    public void recordCallComplete(ModelRouterProperties.ModelInstance instance) {
        delegate.recordCallComplete(instance);
        
        // 更新成功统计
        instanceSuccessCount.computeIfAbsent(instance.getName(), k -> new AtomicLong(0)).incrementAndGet();
        
        // 记录调用完成事件
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance.name", instance.getName());
            eventAttributes.put("instance.base_url", instance.getBaseUrl());
            eventAttributes.put("event.type", "call_success");
            context.addEvent("lb.instance_call_success", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "load_balancer_call_success");
        logData.put("instance_name", instance.getName());
        logData.put("instance_url", instance.getBaseUrl());
        logData.put("success_count", instanceSuccessCount.get(instance.getName()).get());
        structuredLogger.logBusinessEvent("load_balancer_call_success", logData, context);
    }
    
    @Override
    public void recordCallFailure(ModelRouterProperties.ModelInstance instance) {
        delegate.recordCallFailure(instance);
        
        // 更新失败统计
        instanceFailureCount.computeIfAbsent(instance.getName(), k -> new AtomicLong(0)).incrementAndGet();
        
        // 记录调用失败事件
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("instance.name", instance.getName());
            eventAttributes.put("instance.base_url", instance.getBaseUrl());
            eventAttributes.put("event.type", "call_failure");
            eventAttributes.put("failure_count", instanceFailureCount.get(instance.getName()).get());
            context.addEvent("lb.instance_call_failure", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "load_balancer_call_failure");
        logData.put("instance_name", instance.getName());
        logData.put("instance_url", instance.getBaseUrl());
        logData.put("failure_count", instanceFailureCount.get(instance.getName()).get());
        logData.put("success_rate", calculateSuccessRate(instance.getName()));
        structuredLogger.logBusinessEvent("load_balancer_call_failure", logData, context);
    }
    
    /**
     * 记录候选实例信息
     */
    private void recordCandidateInstances(TracingContext context, 
                                        List<ModelRouterProperties.ModelInstance> instances, 
                                        String serviceType) {
        if (context != null && context.isActive()) {
            // 记录候选实例详情
            for (int i = 0; i < instances.size(); i++) {
                ModelRouterProperties.ModelInstance instance = instances.get(i);
                context.setTag("lb.candidate." + i + ".name", instance.getName());
                context.setTag("lb.candidate." + i + ".url", instance.getBaseUrl());
                context.setTag("lb.candidate." + i + ".weight", instance.getWeight());
            }
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "load_balancer_candidates");
        logData.put("service_type", serviceType);
        logData.put("candidates_count", instances.size());
        logData.put("strategy", getStrategyName());
        
        // 添加候选实例详情
        List<Map<String, Object>> candidateDetails = instances.stream()
                .map(instance -> {
                    Map<String, Object> details = new HashMap<>();
                    details.put("name", instance.getName());
                    details.put("url", instance.getBaseUrl());
                    details.put("weight", instance.getWeight());
                    details.put("success_rate", calculateSuccessRate(instance.getName()));
                    return details;
                })
                .toList();
        logData.put("candidates", candidateDetails);
        
        structuredLogger.logBusinessEvent("load_balancer_candidates", logData, context);
    }
    
    /**
     * 记录成功选择
     */
    private void recordSuccessfulSelection(TracingContext context, Span span, 
                                         ModelRouterProperties.ModelInstance selectedInstance,
                                         long decisionTimeMs, String serviceType) {
        if (span != null) {
            // 设置选中实例属性
            span.setAttribute("lb.selected.name", selectedInstance.getName());
            span.setAttribute("lb.selected.url", selectedInstance.getBaseUrl());
            span.setAttribute("lb.selected.weight", selectedInstance.getWeight());
            span.setAttribute("lb.decision_time_ms", decisionTimeMs);
            span.setAttribute("lb.success", true);
        }
        
        // 记录选择事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("selected.name", selectedInstance.getName());
            eventAttributes.put("selected.url", selectedInstance.getBaseUrl());
            eventAttributes.put("selected.weight", selectedInstance.getWeight());
            eventAttributes.put("decision_time_ms", decisionTimeMs);
            context.addEvent("lb.instance_selected", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "load_balancer_selection");
        logData.put("service_type", serviceType);
        logData.put("strategy", getStrategyName());
        logData.put("selected_instance", selectedInstance.getName());
        logData.put("selected_url", selectedInstance.getBaseUrl());
        logData.put("selected_weight", selectedInstance.getWeight());
        logData.put("decision_time_ms", decisionTimeMs);
        logData.put("success", true);
        logData.put("total_selections", totalSelections.get());
        logData.put("success_rate", calculateOverallSuccessRate());
        
        structuredLogger.logBusinessEvent("load_balancer_selection", logData, context);
        
        log.debug("负载均衡器选择成功: {} -> {} (耗时: {}ms, 服务: {})", 
                getStrategyName(), selectedInstance.getName(), decisionTimeMs, serviceType);
    }
    
    /**
     * 记录失败选择
     */
    private void recordFailedSelection(TracingContext context, Span span, 
                                     long decisionTimeMs, String serviceType, String errorMessage) {
        if (span != null) {
            span.setAttribute("lb.decision_time_ms", decisionTimeMs);
            span.setAttribute("lb.success", false);
            span.setAttribute("lb.error", errorMessage);
        }
        
        // 记录失败事件
        if (context != null && context.isActive()) {
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("decision_time_ms", decisionTimeMs);
            eventAttributes.put("error", errorMessage);
            context.addEvent("lb.selection_failed", eventAttributes);
        }
        
        // 记录结构化日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "load_balancer_selection_failed");
        logData.put("service_type", serviceType);
        logData.put("strategy", getStrategyName());
        logData.put("decision_time_ms", decisionTimeMs);
        logData.put("error", errorMessage);
        logData.put("success", false);
        logData.put("total_selections", totalSelections.get());
        logData.put("failure_rate", calculateOverallFailureRate());
        
        structuredLogger.logBusinessEvent("load_balancer_selection_failed", logData, context);
        
        log.warn("负载均衡器选择失败: {} (耗时: {}ms, 服务: {}, 错误: {})", 
                getStrategyName(), decisionTimeMs, serviceType, errorMessage);
    }
    
    /**
     * 获取策略名称
     */
    private String getStrategyName() {
        return delegate.getClass().getSimpleName().replace("LoadBalancer", "").toLowerCase();
    }
    
    /**
     * 计算实例成功率
     */
    private double calculateSuccessRate(String instanceName) {
        AtomicLong success = instanceSuccessCount.get(instanceName);
        AtomicLong failure = instanceFailureCount.get(instanceName);
        
        if (success == null && failure == null) {
            return 1.0; // 没有调用记录，假设100%成功率
        }
        
        long successCount = success != null ? success.get() : 0;
        long failureCount = failure != null ? failure.get() : 0;
        long totalCount = successCount + failureCount;
        
        return totalCount > 0 ? (double) successCount / totalCount : 1.0;
    }
    
    /**
     * 计算整体成功率
     */
    private double calculateOverallSuccessRate() {
        long total = totalSelections.get();
        long success = successfulSelections.get();
        return total > 0 ? (double) success / total : 1.0;
    }
    
    /**
     * 计算整体失败率
     */
    private double calculateOverallFailureRate() {
        long total = totalSelections.get();
        long failed = failedSelections.get();
        return total > 0 ? (double) failed / total : 0.0;
    }
    
    /**
     * 获取负载均衡器统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_selections", totalSelections.get());
        stats.put("successful_selections", successfulSelections.get());
        stats.put("failed_selections", failedSelections.get());
        stats.put("success_rate", calculateOverallSuccessRate());
        stats.put("failure_rate", calculateOverallFailureRate());
        stats.put("strategy", getStrategyName());
        
        // 实例级别统计
        Map<String, Object> instanceStats = new HashMap<>();
        for (String instanceName : instanceSelectionCount.keySet()) {
            Map<String, Object> instanceData = new HashMap<>();
            instanceData.put("selection_count", instanceSelectionCount.get(instanceName).get());
            instanceData.put("success_count", instanceSuccessCount.getOrDefault(instanceName, new AtomicLong(0)).get());
            instanceData.put("failure_count", instanceFailureCount.getOrDefault(instanceName, new AtomicLong(0)).get());
            instanceData.put("success_rate", calculateSuccessRate(instanceName));
            instanceStats.put(instanceName, instanceData);
        }
        stats.put("instance_statistics", instanceStats);
        
        return stats;
    }
}
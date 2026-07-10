package org.unreal.modelrouter.router.adapter.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;

/**
 * 适配器监控记录器
 *
 * 负责记录和上报适配器调用的监控指标，包括：
 * - 请求开始/完成时间记录
 * - 错误记录
 * - 后端调用指标
 * - 模型调用统计
 * - 服务注册调用记录 (v2.26.0)
 * - API 调用历史记录 (v2.7.8)
 *
 * @author JAiRouter Team
 * @since v2.3.2
 */
@Slf4j
@Component
public class AdapterMetricsRecorder {

    private final MetricsCollector metricsCollector;
    private final ModelCallStatsRepository statsRepository;
    private final ModelServiceRegistry registry;

    @Autowired(required = false)
    private ApiCallHistoryRecorder callHistoryRecorder;

    public AdapterMetricsRecorder(MetricsCollector metricsCollector,
                                  ModelCallStatsRepository statsRepository,
                                  ModelServiceRegistry registry) {
        this.metricsCollector = metricsCollector;
        this.statsRepository = statsRepository;
        this.registry = registry;
    }

    /**
     * 记录请求开始
     * 
     * @param adapterType 适配器类型（如 gpustack, ollama 等）
     * @param instanceId 实例 ID
     * @param serviceType 服务类型（如 CHAT, EMBEDDING 等）
     * @param modelName 模型名称
     */
    public void recordRequestStart(
            final String adapterType,
            final String instanceId,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName) {
        
        if (log.isDebugEnabled()) {
            log.debug("记录请求开始：adapter={}, instance={}, service={}, model={}",
                    adapterType, instanceId, serviceType, modelName);
        }
        
        // 注意：ModelCallStatsRepository 没有 recordCallStart 方法，
        // 请求开始时不更新统计，等待完成时再更新
    }

    /**
     * 记录请求完成
     * 
     * @param adapterType 适配器类型
     * @param instanceId 实例 ID
     * @param durationMs 调用耗时（毫秒）
     * @param success 是否成功
     * @param errorCode 错误代码（失败时）
     * @param modelName 模型名称
     * @param serviceType 服务类型
     */
    public void recordRequestComplete(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final boolean success,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType) {
        
        // 记录后端调用指标
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceId, durationMs, success);
        }
        
        // 记录到统计仓库 - 使用 updateStats 方法
        if (statsRepository != null && serviceType != null && modelName != null) {
            statsRepository.updateStats(serviceType.name(), modelName, success, durationMs);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录请求完成：adapter={}, instance={}, duration={}ms, success={}, errorCode={}",
                    adapterType, instanceId, durationMs, success, errorCode);
        }
    }

    /**
     * 记录错误
     * 
     * @param adapterType 适配器类型
     * @param instanceId 实例 ID
     * @param errorCode 错误代码
     * @param error 错误对象
     * @param durationMs 调用耗时（毫秒）
     * @param serviceType 服务类型
     */
    public void recordError(
            final String adapterType,
            final String instanceId,
            final String errorCode,
            final Throwable error,
            final long durationMs,
            final ModelServiceRegistry.ServiceType serviceType) {
        
        // 记录错误指标
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceId, durationMs, false);
            
            // 记录追踪指标（如果有错误）
            if (error != null) {
                metricsCollector.recordTrace(
                    errorCode != null ? errorCode : "UNKNOWN",
                    instanceId,
                    "adapter_error",
                    durationMs,
                    false
                );
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录错误：adapter={}, instance={}, errorCode={}, duration={}ms, error={}",
                    adapterType, instanceId, errorCode, durationMs, 
                    error != null ? error.getMessage() : "null");
        }
    }

    /**
     * 记录重试事件
     * 
     * @param adapterType 适配器类型
     * @param instanceId 实例 ID
     * @param retryCount 重试次数
     * @param error 导致重试的错误
     */
    public void recordRetry(
            final String adapterType,
            final String instanceId,
            final int retryCount,
            final Throwable error) {
        
        if (log.isDebugEnabled()) {
            log.debug("记录重试：adapter={}, instance={}, retryCount={}, error={}",
                    adapterType, instanceId, retryCount, 
                    error != null ? error.getMessage() : "null");
        }
        
        // 重试时记录一次失败尝试
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceId, 0, false);
        }
    }

    /**
     * 记录请求大小
     * 
     * @param serviceType 服务类型
     * @param requestSize 请求大小（字节）
     * @param responseSize 响应大小（字节）
     */
    public void recordRequestSize(
            final ModelServiceRegistry.ServiceType serviceType,
            final long requestSize,
            final long responseSize) {
        
        if (metricsCollector != null) {
            metricsCollector.recordRequestSize(
                serviceType != null ? serviceType.name() : "UNKNOWN",
                requestSize,
                responseSize
            );
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录请求大小：service={}, requestSize={} bytes, responseSize={} bytes",
                    serviceType, requestSize, responseSize);
        }
    }

    /**
     * 记录响应时间
     * 
     * @param serviceType 服务类型
     * @param method HTTP 方法
     * @param responseTime 响应时间（毫秒）
     * @param status 状态码
     */
    public void recordResponseTime(
            final ModelServiceRegistry.ServiceType serviceType,
            final String method,
            final long responseTime,
            final String status) {
        
        if (metricsCollector != null) {
            metricsCollector.recordRequest(
                serviceType != null ? serviceType.name() : "UNKNOWN",
                method,
                responseTime,
                status
            );
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录响应时间：service={}, method={}, responseTime={}ms, status={}",
                    serviceType, method, responseTime, status);
        }
    }

    // ========== v2.26.0: 服务注册调用记录 ==========

    /**
     * 记录调用成功到服务注册
     * 
     * @param serviceType 服务类型
     * @param instance 实例
     * @since v2.26.0
     */
    public void recordCallSuccessToRegistry(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        if (registry != null && serviceType != null && instance != null) {
            registry.recordCallComplete(serviceType, instance);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录调用成功：service={}, instance={}", serviceType, instance.getName());
        }
    }

    /**
     * 记录调用失败到服务注册
     * 
     * @param serviceType 服务类型
     * @param instance 实例
     * @since v2.26.0
     */
    public void recordCallFailureToRegistry(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        if (registry != null && serviceType != null && instance != null) {
            registry.recordCallFailure(serviceType, instance);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录调用失败：service={}, instance={}", serviceType, instance.getName());
        }
    }

    /**
     * 完整的调用成功记录（统一入口）
     * 
     * 同时更新：Registry + MetricsCollector + StatsRepository
     * 
     * @param adapterType 适配器类型
     * @param instanceId 实例ID
     * @param durationMs 耗时（毫秒）
     * @param success 是否成功
     * @param errorCode 错误代码（失败时）
     * @param modelName 模型名称
     * @param serviceType 服务类型
     * @param instance 实例对象
     * @since v2.26.0
     */
    public void recordCompleteCall(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final boolean success,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        
        // 更新 Registry
        if (success) {
            recordCallSuccessToRegistry(serviceType, instance);
        } else {
            recordCallFailureToRegistry(serviceType, instance);
        }
        
        // 更新 MetricsCollector
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instanceId, durationMs, success);
        }
        
        // 更新 StatsRepository
        if (statsRepository != null && serviceType != null && modelName != null) {
            statsRepository.updateStats(serviceType.name(), modelName, success, durationMs);
            if (!success && errorCode != null) {
                statsRepository.recordErrorCode(serviceType.name(), modelName, errorCode);
            }
        }

        // v2.7.8: 记录 API 调用历史
        if (callHistoryRecorder != null && serviceType != null && modelName != null) {
            try {
                CallHistoryRecordDTO record = CallHistoryRecordDTO.builder()
                        .serviceType(serviceType.name())
                        .modelName(modelName)
                        .provider(adapterType)
                        .instanceName(instanceId)
                        .instanceUrl(instance != null ? instance.getBaseUrl() : null)
                        .responseTimeMs(durationMs)
                        .isSuccess(success)
                        .errorCode(errorCode)
                        .build();
                callHistoryRecorder.record(record);
            } catch (Exception e) {
                log.debug("Failed to record call history: {}", e.getMessage());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("完整调用记录：adapter={}, instance={}, duration={}ms, success={}, errorCode={}",
                    adapterType, instanceId, durationMs, success, errorCode);
        }
    }
}

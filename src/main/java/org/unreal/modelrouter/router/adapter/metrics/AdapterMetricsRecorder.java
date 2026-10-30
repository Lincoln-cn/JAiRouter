package org.unreal.modelrouter.router.adapter.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

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

    @Autowired(required = false)
    private CallHistoryProperties callHistoryProperties;

    @Autowired(required = false)
    private SanitizationService sanitizationService;

    public AdapterMetricsRecorder(MetricsCollector metricsCollector,
                                  ModelCallStatsRepository statsRepository,
                                  ModelServiceRegistry registry) {
        this.metricsCollector = metricsCollector;
        this.statsRepository = statsRepository;
        this.registry = registry;
    }

    /**
     * 调用结果分类（v3.5.4，#647）。
     *
     * <p>在此之前调用结果只有「成功 / 失败」二元，4xx（客户端错误）与 5xx（实例故障）走同一条失败路径，
     * 于是客户端传错参数、上游对单个 key 返 429 都会累加实例的熔断失败次数，5 次即可把健康实例熔断 60 秒。</p>
     *
     * <ul>
     *   <li>{@link #SUCCESS}：2xx，计入成功、重置失败计数；</li>
     *   <li>{@link #CLIENT_ERROR}：4xx（含上游 429），记统计与调用历史，但<b>不</b>计入实例熔断失败；</li>
     *   <li>{@link #INSTANCE_FAILURE}：5xx / 连接超时 / DNS / 连接拒绝等实例侧故障，计入熔断失败。</li>
     * </ul>
     */
    public enum CallOutcome {
        SUCCESS,
        CLIENT_ERROR,
        INSTANCE_FAILURE;

        public boolean isSuccess() {
            return this == SUCCESS;
        }
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
     * 记录一次「降级命中缓存」（#823）.
     *
     * <p>命中与未命中分开计数，便于判断降级到底是「有兜底」还是「兜底也不可用」。</p>
     *
     * @param serviceType 服务类型
     * @param modelName   模型名
     */
    public void recordFallbackHit(final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        final String service = serviceType == null ? "unknown" : serviceType.name();
        if (log.isDebugEnabled()) {
            log.debug("降级缓存命中：service={}, model={}", service, modelName);
        }
        if (metricsCollector != null) {
            metricsCollector.recordFallbackHit(service, modelName);
        }
    }

    /**
     * 记录一次「降级且未命中缓存」（#823）.
     *
     * @param serviceType 服务类型
     * @param modelName   模型名
     */
    public void recordFallbackMiss(final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        final String service = serviceType == null ? "unknown" : serviceType.name();
        if (log.isDebugEnabled()) {
            log.debug("降级但未命中缓存：service={}, model={}", service, modelName);
        }
        if (metricsCollector != null) {
            metricsCollector.recordFallbackMiss(service, modelName);
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
     * v2.9.3: 记录调用成功到服务注册（带调用时长和成功状态）
     * 
     * @param serviceType 服务类型
     * @param instance 实例
     * @param durationMs 调用耗时（毫秒）
     * @param success 是否成功
     * @since v2.9.3
     */
    public void recordCallSuccessToRegistry(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final long durationMs,
            final boolean success) {
        
        if (registry != null && serviceType != null && instance != null) {
            registry.recordCallComplete(serviceType, instance, durationMs, success);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录调用成功：service={}, instance={}, duration={}ms", serviceType, instance.getName(), durationMs);
        }
    }

    /**
     * v2.9.3: 记录调用失败到服务注册（带调用时长和错误码）
     * 
     * @param serviceType 服务类型
     * @param instance 实例
     * @param durationMs 调用耗时（毫秒）
     * @param errorCode 错误码
     * @since v2.9.3
     */
    public void recordCallFailureToRegistry(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final long durationMs,
            final String errorCode) {
        
        if (registry != null && serviceType != null && instance != null) {
            registry.recordCallFailure(serviceType, instance, durationMs, errorCode);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("记录调用失败：service={}, instance={}, duration={}ms, errorCode={}", 
                    serviceType, instance.getName(), durationMs, errorCode);
        }
    }

    /**
     * v3.5.4（#647）：记录「客户端侧错误」到服务注册。
     *
     * <p>4xx 属于调用方输入问题，与实例健康无关：这里只把请求标记为「已完成」（让负载均衡器
     * 递减在途计数），<b>不</b>累加熔断失败次数。上游 429 默认也按此处理，避免把上游限流放大成熔断。</p>
     *
     * @param serviceType 服务类型
     * @param instance 实例
     * @param durationMs 调用耗时（毫秒）
     * @param errorCode 错误码
     * @since v3.5.4
     */
    public void recordCallClientErrorToRegistry(
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final long durationMs,
            final String errorCode) {

        if (registry != null && serviceType != null && instance != null) {
            // success=false 只是「不算成功」：ModelServiceRegistry 仅在该值为 true 时记熔断成功，
            // 负载均衡器的在途计数无论如何都会递减（#647 顺带修掉 least-connections 只增不减）。
            registry.recordCallComplete(serviceType, instance, durationMs, false);
        }

        if (log.isDebugEnabled()) {
            log.debug("记录客户端错误：service={}, instance={}, duration={}ms, errorCode={}",
                    serviceType, instance.getName(), durationMs, errorCode);
        }
    }

    /**
     * 完整的调用成功记录（统一入口，不含请求/响应体）
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
        recordCompleteCall(adapterType, instanceId, durationMs, fromBoolean(success), errorCode,
                modelName, serviceType, instance);
    }

    /**
     * v3.5.4（#647）：按调用结果分类记录（不含请求/响应体）。
     *
     * @param adapterType 适配器类型
     * @param instanceId 实例ID
     * @param durationMs 耗时（毫秒）
     * @param outcome 调用结果分类
     * @param errorCode 错误代码（失败时）
     * @param modelName 模型名称
     * @param serviceType 服务类型
     * @param instance 实例对象
     * @since v3.5.4
     */
    public void recordCompleteCall(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final CallOutcome outcome,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        recordCompleteCall(adapterType, instanceId, durationMs, outcome, errorCode,
                modelName, serviceType, instance, null, null);
    }

    /**
     * 完整的调用统计记录（不含调用历史，v2.9.2 用于 processor 已记录历史时的统计更新）
     *
     * 同时更新：Registry + MetricsCollector + StatsRepository
     *
     * @param adapterType  适配器类型
     * @param instanceId   实例ID
     * @param durationMs   耗时（毫秒）
     * @param success      是否成功
     * @param errorCode    错误代码（失败时）
     * @param modelName    模型名称
     * @param serviceType  服务类型
     * @param instance     实例对象
     * @since v2.9.2
     */
    public void recordCompleteCallStats(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final boolean success,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        recordCompleteCallStats(adapterType, instanceId, durationMs, fromBoolean(success), errorCode,
                modelName, serviceType, instance);
    }

    /**
     * v3.5.4（#647）：按调用结果分类记录统计（不含调用历史）。
     *
     * @param adapterType  适配器类型
     * @param instanceId   实例ID
     * @param durationMs   耗时（毫秒）
     * @param outcome      调用结果分类
     * @param errorCode    错误代码（失败时）
     * @param modelName    模型名称
     * @param serviceType  服务类型
     * @param instance     实例对象
     * @since v3.5.4
     */
    public void recordCompleteCallStats(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final CallOutcome outcome,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {
        recordStats(adapterType, instanceId, durationMs, outcome, errorCode, modelName, serviceType, instance);

        if (log.isDebugEnabled()) {
            log.debug("完整调用统计记录：adapter={}, instance={}, duration={}ms, outcome={}, errorCode={}",
                    adapterType, instanceId, durationMs, outcome, errorCode);
        }
    }

    /**
     * #716：把流式路径上的终止异常分类为「客户端错误 / 实例故障」。
     *
     * <p>与非流式的 {@code BaseAdapter#classifyFailure} 同一口径：4xx 类异常属调用方输入问题，
     * 不计入实例熔断失败；其余（超时、连接失败、5xx）都是实例侧故障。</p>
     *
     * @param throwable 终止流式响应的异常
     * @return 调用结果分类
     * @since 3.5.5
     */
    public static CallOutcome classifyFailure(final Throwable throwable) {
        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException clientError
                && clientError.getStatusCode().is4xxClientError()) {
            return CallOutcome.CLIENT_ERROR;
        }
        if (throwable instanceof org.springframework.web.server.ResponseStatusException statusError
                && statusError.getStatusCode().is4xxClientError()) {
            return CallOutcome.CLIENT_ERROR;
        }
        return CallOutcome.INSTANCE_FAILURE;
    }

    private static CallOutcome fromBoolean(final boolean success) {
        return success ? CallOutcome.SUCCESS : CallOutcome.INSTANCE_FAILURE;
    }

    /**
     * 完整的调用成功记录（含请求/响应体捕获 - v2.9.2 记录治理）
     *
     * 当 recordLevel != METADATA_ONLY 时，记录请求体和响应体。
     * SUMMARY 级别：通过 SanitizationService 脱敏后再记录。
     * FULL 级别：直接记录原始内容。
     *
     * @param adapterType  适配器类型
     * @param instanceId   实例ID
     * @param durationMs   耗时（毫秒）
     * @param success      是否成功
     * @param errorCode    错误代码（失败时）
     * @param modelName    模型名称
     * @param serviceType  服务类型
     * @param instance     实例对象
     * @param requestBody  请求体原始内容（可为 null，METADATA_ONLY 时忽略）
     * @param responseBody 响应体原始内容（可为 null，METADATA_ONLY 时忽略）
     * @since v2.9.2
     */
    public void recordCompleteCall(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final boolean success,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final String requestBody,
            final String responseBody) {
        recordCompleteCall(adapterType, instanceId, durationMs, fromBoolean(success), errorCode,
                modelName, serviceType, instance, requestBody, responseBody);
    }

    /**
     * v3.5.4（#647）：按调用结果分类记录（含请求/响应体捕获）。
     *
     * @param adapterType  适配器类型
     * @param instanceId   实例ID
     * @param durationMs   耗时（毫秒）
     * @param outcome      调用结果分类
     * @param errorCode    错误代码（失败时）
     * @param modelName    模型名称
     * @param serviceType  服务类型
     * @param instance     实例对象
     * @param requestBody  请求体原始内容（可为 null，METADATA_ONLY 时忽略）
     * @param responseBody 响应体原始内容（可为 null，METADATA_ONLY 时忽略）
     * @since v3.5.4
     */
    public void recordCompleteCall(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final CallOutcome outcome,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final String requestBody,
            final String responseBody) {

        final boolean success = outcome.isSuccess();

        // v2.9.2: 统计更新（Registry + MetricsCollector + StatsRepository）
        recordStats(adapterType, instanceId, durationMs, outcome, errorCode, modelName, serviceType, instance);

        // v2.7.8: 记录 API 调用历史（v2.9.2: 含请求/响应体）
        // 脱敏与落库移到 boundedElastic，避免在 EventLoop / 回调线程上 block
        if (callHistoryRecorder != null && serviceType != null && modelName != null) {
            final RecordLevel level = resolveRecordLevel();
            final String rawRequestBody = level != RecordLevel.METADATA_ONLY ? requestBody : null;
            final String rawResponseBody = level != RecordLevel.METADATA_ONLY ? responseBody : null;
            final boolean needSanitize = level == RecordLevel.SUMMARY && sanitizationService != null;
            final SanitizationService sanitization = this.sanitizationService;
            final ApiCallHistoryRecorder recorder = this.callHistoryRecorder;
            final String finalAdapterType = adapterType;
            final String finalInstanceId = instanceId;
            final ModelRouterProperties.ModelInstance finalInstance = instance;
            final String finalServiceType = serviceType.name();
            final String finalModelName = modelName;
            final long finalDurationMs = durationMs;
            final boolean finalSuccess = success;
            final String finalErrorCode = errorCode;

            Mono.zip(
                            sanitizeOptional(needSanitize, sanitization, rawRequestBody),
                            sanitizeOptional(needSanitize, sanitization, rawResponseBody))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(tuple -> {
                        try {
                            CallHistoryRecordDTO record = CallHistoryRecordDTO.builder()
                                    .serviceType(finalServiceType)
                                    .modelName(finalModelName)
                                    .provider(finalAdapterType)
                                    .instanceName(finalInstanceId)
                                    .instanceUrl(finalInstance != null ? finalInstance.getBaseUrl() : null)
                                    .responseTimeMs(finalDurationMs)
                                    .isSuccess(finalSuccess)
                                    .errorCode(finalErrorCode)
                                    .requestBody(tuple.getT1())
                                    .responseBody(tuple.getT2())
                                    .build();
                            recorder.record(record);
                        } catch (Exception e) {
                            log.debug("Failed to record call history: {}", e.getMessage());
                        }
                    }, err -> log.debug("Failed to sanitize call history: {}", err.getMessage()));
        }

        if (log.isDebugEnabled()) {
            log.debug("完整调用记录：adapter={}, instance={}, duration={}ms, success={}, errorCode={}",
                    adapterType, instanceId, durationMs, success, errorCode);
        }
    }

    /**
     * 更新统计（Registry + MetricsCollector + StatsRepository）
     */
    private void recordStats(
            final String adapterType,
            final String instanceId,
            final long durationMs,
            final CallOutcome outcome,
            final String errorCode,
            final String modelName,
            final ModelServiceRegistry.ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance) {

        final boolean success = outcome.isSuccess();

        // 更新 Registry（v2.9.3: 传递 durationMs 到负载均衡器钩子）
        // v3.5.4（#647）：客户端错误不计入熔断失败，只完成在途计数
        switch (outcome) {
            case SUCCESS -> recordCallSuccessToRegistry(serviceType, instance, durationMs, true);
            case CLIENT_ERROR -> recordCallClientErrorToRegistry(serviceType, instance, durationMs, errorCode);
            case INSTANCE_FAILURE -> recordCallFailureToRegistry(serviceType, instance, durationMs, errorCode);
            default -> recordCallFailureToRegistry(serviceType, instance, durationMs, errorCode);
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
    }

    /**
     * 解析当前记录级别（默认 METADATA_ONLY）
     */
    private RecordLevel resolveRecordLevel() {
        if (callHistoryProperties != null) {
            return callHistoryProperties.getRecordLevel();
        }
        return RecordLevel.METADATA_ONLY;
    }

    /**
     * 异步脱敏：SUMMARY 且装配了 SanitizationService 时走脱敏链路，否则原样透传。
     * 超时/异常回退为原文，保证调用历史仍可记录。
     */
    private Mono<String> sanitizeOptional(final boolean needSanitize,
                                          final SanitizationService sanitization,
                                          final String raw) {
        if (!needSanitize || sanitization == null) {
            return Mono.justOrEmpty(raw);
        }
        return sanitization.sanitizeForStorage(raw, "application/json")
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.debug("Sanitization failed: {}", e.getMessage());
                    return Mono.justOrEmpty(raw);
                })
                .map(sanitized -> sanitized != null ? sanitized : raw)
                .defaultIfEmpty(raw);
    }
}

package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.handler.MultipartRequestHandler;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.processor.FallbackRequestProcessor;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.auth.security.quota.QuotaReservation;
import org.unreal.modelrouter.router.adapter.processor.StreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.util.ModelUtils;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.common.util.IpUtils;
import org.unreal.modelrouter.router.fallback.FallbackRequestKey;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;
import org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.loadbalancer.SelectedInstanceHolder;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * BaseAdapter - v2.28.0 重构版
 * 使用聚合组件减少构造函数参数：16 参数 → 3 参数。
 * 所有请求处理逻辑已完全委托给专门组件。
 */
public abstract class BaseAdapter implements ServiceCapability {

    private final AdapterContext context;
    private final RequestProcessingSupport requestSupport;
    private final ResilienceSupport resilienceSupport;
    protected final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(BaseAdapter.class);

    @Autowired
    private MultipartRequestHandler multipartRequestHandler;
    @Autowired(required = false)
    private FallbackRequestProcessor fallbackRequestProcessor;
    @Autowired(required = false)
    private ErrorTracker errorTracker;

    /**
     * 新构造函数 - 使用聚合组件（推荐）。
     * @param context 核心上下文
     * @param requestSupport 请求处理支持
     * @param resilienceSupport 弹性支持
     */
    @Autowired
    public BaseAdapter(final AdapterContext context,
                       final RequestProcessingSupport requestSupport,
                       final ResilienceSupport resilienceSupport) {
        this.context = context;
        this.requestSupport = requestSupport;
        this.resilienceSupport = resilienceSupport;
        this.objectMapper = context.getObjectMapper();
    }

    public void setErrorTracker(final ErrorTracker errorTracker) {
        this.errorTracker = errorTracker;
    }

    // ==================== 核心方法 ====================

    private String classifyError(final Throwable throwable) {
        return resilienceSupport.getErrorHandler().classifyError(throwable);
    }

    /**
     * v3.5.4（#647）：把上游响应分类为「成功 / 客户端错误 / 实例故障」。
     *
     * <p>2xx 计成功；4xx（含 400/401/403/404/422 与上游 429）属于调用方输入或上游限流，
     * 与实例健康无关，不计入熔断失败；其余（5xx）才是实例侧故障。</p>
     */
    private static AdapterMetricsRecorder.CallOutcome classifyResponse(final ResponseEntity<?> response) {
        if (response == null || response.getStatusCode().is2xxSuccessful()) {
            return AdapterMetricsRecorder.CallOutcome.SUCCESS;
        }
        if (response.getStatusCode().is4xxClientError()) {
            return AdapterMetricsRecorder.CallOutcome.CLIENT_ERROR;
        }
        return AdapterMetricsRecorder.CallOutcome.INSTANCE_FAILURE;
    }

    /**
     * v3.5.4（#647）：异常路径同样区分客户端错误与实例故障。
     * 只有 4xx 类异常算客户端错误，其余（超时、连接失败、5xx）都算实例故障。
     */
    private static AdapterMetricsRecorder.CallOutcome classifyFailure(final Throwable throwable) {
        return AdapterMetricsRecorder.classifyFailure(throwable);
    }

    /**
     * #716：响应体是否为「尚未订阅的流」。
     *
     * <p>流式响应在 {@code doOnSuccess} 时刻只是建好了流，真正的成败要到流终端才知道；
     * 因此这类响应的记账由 {@code StreamingRequestProcessor} 的终端回调完成。</p>
     */
    static boolean isStreamingResponse(final ResponseEntity<?> response) {
        if (response == null) {
            return false;
        }
        if (response.getBody() instanceof org.reactivestreams.Publisher) {
            return true;
        }
        final org.springframework.http.MediaType contentType = response.getHeaders().getContentType();
        return contentType != null
                && org.springframework.http.MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType);
    }

    public ModelServiceRegistry getRegistry() { return context.getRegistry(); }

    protected RetryPolicy getRetryPolicy() { return resilienceSupport.getRetryPolicy(); }

    /**
     * 根据实例 baseUrl 获取 WebClient（不重新选择实例）。
     * 用于故障转移重选实例后获取新实例的 WebClient。
     */
    @SuppressWarnings("all")
    private WebClient getWebClientForInstance(final ModelRouterProperties.ModelInstance instance) {
        return getWebClientForInstance(instance, false);
    }

    /**
     * 取该实例对应的 WebClient。
     *
     * @param instance  目标实例
     * @param streaming true 表示这是 SSE 流式请求，需要用更大的传输层超时（#701）
     * @return WebClient
     */
    private WebClient getWebClientForInstance(final ModelRouterProperties.ModelInstance instance,
                                              final boolean streaming) {
        String baseUrl = instance.getBaseUrl();
        try {
            var tracingFactory = org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                    org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory.class);
            return streaming
                    ? tracingFactory.createTracingWebClient(baseUrl, true)
                    : tracingFactory.createTracingWebClient(baseUrl);
        } catch (Exception e) {
            return org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                    org.unreal.modelrouter.router.model.WebClientCacheManager.class)
                    .getOrCreate(baseUrl);
        }
    }

    protected Mono<ResponseEntity<String>> checkCapability(final ModelServiceRegistry.ServiceType serviceType) {
        return resilienceSupport.getCapabilityChecker().checkCapability(supportCapability(), serviceType);
    }

    // ==================== 请求处理模板方法 ====================

    @SuppressWarnings("all")
    protected <T> Mono processRequest(final T request, final String authorization,
            final ServerHttpRequest httpRequest, final ModelServiceRegistry.ServiceType serviceType,
            final String modelName, final RequestProcessor<T> processor) {
        // 优先复用网关（ServiceRequestHandler）已选中的实例：每个请求只应发生一次实例选择，
        // 否则会重复推进负载均衡状态、重复扣减实例级限流令牌，并让路由监控一次请求 +多次计数。
        // 未经网关的调用（持有者为空）回退为自行选择，保持既有行为。
        ModelRouterProperties.ModelInstance selectedInstance = SelectedInstanceHolder.get();
        if (selectedInstance == null) {
            selectedInstance = selectInstance(serviceType, modelName, IpUtils.getClientIp(httpRequest));
        }
        // 用已选中的实例构造 WebClient：客户端目标与指标归属实例保持一致
        WebClient client = getWebClientForInstance(selectedInstance);
        // #635: 目标路径必须取自**被选中的实例**，而不是按模型名反查（池名 / auto-model / 别名会查不到）
        String path = resolvePath(selectedInstance, serviceType, modelName);
        long startTime = System.currentTimeMillis();
        String adapterType = getAdapterType();
        String modelNameFromRequest = ModelUtils.getModelNameFromRequest(request);
        resilienceSupport.getTracingManager()
                .recordCallStart(adapterType, selectedInstance, serviceType, modelNameFromRequest);
        final QuotaReservation quotaReservation = QuotaReservation.from(httpRequest);
        return processRequestWithRetry(request, authorization, client, path, selectedInstance,
                serviceType, modelNameFromRequest, processor, startTime, 0,
                new HashSet<>(), quotaReservation);
    }

    /**
     * 实例键生成：与 LoadBalancer 的实例标识保持一致
     */
    private String instanceKey(final ModelRouterProperties.ModelInstance instance) {
        return instance.getBaseUrl() + ":" + instance.getPath();
    }

    /**
     * v2.9.6: 尝试故障转移重选实例，排除已失败的实例。
     * 如果找不到新的可用实例，返回当前实例（保留原有重试行为作为兜底）。
     *
     * @param currentInstance 当前失败的实例
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param failedKeys 已失败的实例键集合
     * @return 重选的实例（可能与 currentInstance 相同，表示无可用替代）
     */
    private ModelRouterProperties.ModelInstance tryReselectInstance(
            final ModelRouterProperties.ModelInstance currentInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final Set<String> failedKeys) {
        // 最大尝试次数 = 已失败数 + 2（留余量，避免死循环）
        int maxAttempts = failedKeys.size() + 2;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                ModelRouterProperties.ModelInstance candidate =
                        getRegistry().selectInstance(serviceType, modelName, null);
                if (candidate != null && !failedKeys.contains(instanceKey(candidate))) {
                    return candidate;
                }
                // 候选已在失败列表中或为空，继续尝试
            } catch (Exception e) {
                // 选择异常（如无可用实例），跳出循环
                logger.debug("故障转移重选实例失败: {}", e.getMessage());
                break;
            }
        }
        return currentInstance;
    }

    @SuppressWarnings("all")
    private <T> Mono processRequestWithRetry(final T request, final String authorization,
            final WebClient client, final String path, final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType, final String modelName,
            final RequestProcessor<T> processor, final long startTime, final int retryCount,
            final Set<String> failedKeys, final QuotaReservation quotaReservation) {
        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        RetryPolicy retryPolicy = resilienceSupport.getRetryPolicy();
        int maxRetries = retryPolicy.getMaxRetriesByServiceType(serviceType);
        return processor.process(request, authorization, client, path, selectedInstance, serviceType)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    // v3.5.4（#647）：4xx 是客户端错误，不计入实例熔断失败
                    final AdapterMetricsRecorder.CallOutcome outcome = classifyResponse(response);
                    final boolean success = outcome.isSuccess();
                    // #716：流式响应此时「只是建好了流」，成败要到流终端才知道（可能首块之后才 5xx）。
                    // 在这里记成功会立刻把熔断失败计数重置为 0，导致持续失败的实例永远进不了 OPEN；
                    // 因此流式响应的记账交给 StreamingRequestProcessor 的终端回调（完成/错误/取消）。
                    final boolean streaming = isStreamingResponse(response);
                    AdapterMetricsRecorder metricsRecorder = resilienceSupport.getMetricsRecorder();
                    if (metricsRecorder != null && !streaming) {
                        // v2.9.2: 成功路径的调用历史由 RequestProcessor 层记录（含请求/响应体），此处仅更新统计，避免重复记录
                        metricsRecorder.recordCompleteCallStats(adapterType, instanceName, duration, outcome,
                                null, modelName, serviceType, selectedInstance);
                    }
                    resilienceSupport.getTracingManager().recordCallComplete(adapterType, selectedInstance, serviceType,
                            ModelUtils.getModelNameFromRequest(request), duration, success);
                })
                .onErrorResume(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String errorCode = classifyError(throwable);
                    // v3.5.4（#647）：4xx 异常（如 WebClientResponseException 4xx）同样是客户端错误
                    final AdapterMetricsRecorder.CallOutcome outcome = classifyFailure(throwable);
                    AdapterMetricsRecorder metricsRecorder = resilienceSupport.getMetricsRecorder();
                    if (metricsRecorder != null) {
                        metricsRecorder.recordCompleteCall(adapterType, instanceName, duration, outcome,
                                errorCode, modelName, serviceType, selectedInstance);
                    }
                    // #827：媒体类服务（TTS/STT/生图/编辑）不幂等，只在建连失败时重试
                    final boolean willRetry = retryPolicy.canRetry(retryCount, throwable)
                            && retryPolicy.isRetryableForService(throwable, serviceType);
                    // #719：结算只在最终结果落一次。首次尝试就 settleFailure 会把凭据的 CAS 置成已结算，
                    // 之后重试成功的 settleSuccess 被静默忽略 —— 账本停在「失败回滚」（requests −1、tokens 0），
                    // 而 token_usage 表记了实际用量，两处对同一次请求给出相反结论。
                    settleOnFinalGiveUp(quotaReservation, willRetry);
                    if (willRetry) {
                        resilienceSupport.getTracingManager()
                                .recordRetry(adapterType, selectedInstance, retryCount + 1, maxRetries, throwable);
                        if (metricsRecorder != null) {
                            metricsRecorder.recordRetry(adapterType, instanceName, retryCount + 1, throwable);
                        }
                        long retryDelay = retryPolicy.calculateRetryDelay(retryCount);
                        // v2.9.6: 请求级故障转移 — 重选实例排除已失败实例
                        failedKeys.add(instanceKey(selectedInstance));
                        ModelRouterProperties.ModelInstance nextInstance =
                                tryReselectInstance(selectedInstance, serviceType, modelName, failedKeys);
                        // 使用新实例时需要获取新的 WebClient
                        WebClient nextClient = (nextInstance != selectedInstance)
                                ? getWebClientForInstance(nextInstance) : client;
                        String nextPath = (nextInstance != selectedInstance)
                                ? resolvePath(nextInstance, serviceType, modelName) : path;
                        return Mono.delay(Duration.ofMillis(retryDelay))
                                .then(processRequestWithRetry(request, authorization, nextClient, nextPath,
                                        nextInstance, serviceType, modelName, processor,
                                        System.currentTimeMillis(), retryCount + 1, failedKeys,
                                        quotaReservation));
                    }
                    if (errorTracker != null) {
                        Map<String, Object> errorContext = new HashMap<>();
                        errorContext.put("modelName", modelName);
                        errorContext.put("serviceType", serviceType != null ? serviceType.name() : null);
                        errorContext.put("provider", adapterType);
                        errorContext.put("instanceName", instanceName);
                        errorContext.put("responseTimeMs", duration);
                        errorContext.put("errorCode", errorCode);
                        errorContext.put("retryCount", retryCount);
                        errorTracker.trackError(throwable, adapterType + ".request", errorContext);
                    }
                    return resilienceSupport.getErrorResponseBuilder()
                            .buildErrorResponse(throwable).flatMap(Mono::error);
                });
    }


    /**
     * #719：重试场景下的失败结算入口。
     *
     * <p>重试复用同一个 {@link QuotaReservation}，而它的恰一次语义（CAS）不允许「先失败回滚、后成功冲正」。
     * 因此重试期间不结算，只有在确定不会再重试时才回滚整笔预留。</p>
     *
     * @param quotaReservation 预留凭据，{@code null} 时空操作
     * @param willRetry        {@code true} 表示还有下一次尝试，此时不结算
     * @since 3.5.5
     */
    public static void settleOnFinalGiveUp(final QuotaReservation quotaReservation, final boolean willRetry) {
        if (willRetry) {
            return;
        }
        QuotaReservation.settleFailure(quotaReservation);
    }

    @SuppressWarnings({"all", "unchecked"})
    protected <T> Mono<? extends ResponseEntity<?>> processRequestWithFallback(final T request,
            final String authorization, final ServerHttpRequest httpRequest,
            final ModelServiceRegistry.ServiceType serviceType, final String modelName,
            final RequestProcessor<T> processor) {
        ModelRouterProperties.ServiceConfig serviceConfig = getRegistry().getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                getRegistry().getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);
        if (fallbackStrategy == null) {
            return processRequest(request, authorization, httpRequest, serviceType, modelName, processor);
        }
        // #823：降级缓存按请求键隔离 —— 服务 + 模型 + 调用方 + 请求体哈希，
        // 请求体不可序列化时 key 为 null，此时既不缓存也不命中（宁可 503，也不串响应）。
        final FallbackRequestKey requestKey = FallbackRequestKey.of(
                serviceType == null ? null : serviceType.name(), modelName,
                AffinityContextHolder.apiKeyId(), request, objectMapper);
        Mono<ResponseEntity<?>> requestMono = ((Mono<ResponseEntity<?>>) (Mono) processRequest(
                request, authorization, httpRequest, serviceType, modelName, processor))
                .doOnNext(response -> cacheSuccessfulResponse(serviceType, modelName, request, response));
        if (fallbackRequestProcessor != null) {
            return requestMono.onErrorResume(throwable -> {
                if (errorTracker != null) {
                    String adapterType = getAdapterType();
                    Map<String, Object> errorContext = new HashMap<>();
                    errorContext.put("modelName", modelName);
                    errorContext.put("serviceType", serviceType != null ? serviceType.name() : null);
                    errorContext.put("provider", adapterType);
                    errorTracker.trackError(throwable, adapterType + ".fallback", errorContext);
                }
                return fallbackRequestProcessor.handleFallbackError(throwable, fallbackStrategy, requestKey)
                        .doOnNext(response -> recordFallbackOutcome(serviceType, modelName, response));
            });
        }
        return requestMono.onErrorResume(throwable -> {
            if (errorTracker != null) {
                String adapterType = getAdapterType();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("modelName", modelName);
                errorContext.put("serviceType", serviceType != null ? serviceType.name() : null);
                errorContext.put("provider", adapterType);
                errorTracker.trackError(throwable, adapterType + ".fallback", errorContext);
            }
            // #816：降级策略只能求值一次。此前写成 fallback() != null ? Mono.just(fallback()) : error，
            // 有状态的策略（例如按请求取缓存条目、带一次性计数）会第二次取到 null 或取到别的条目。
            final ResponseEntity<?> fallbackResponse = fallbackStrategy.fallback(requestKey, (Exception) throwable);
            recordFallbackOutcome(serviceType, modelName, fallbackResponse);
            return fallbackResponse != null
                ? Mono.just(fallbackResponse)
                : Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "服务降级且无缓存"));
        });
    }

    /**
     * 记录降级结果是否命中缓存（#823）。
     *
     * <p>命中时响应带 {@code X-Fallback: cache}；这里按同一个判据打点，
     * 避免「日志说命中、指标说未命中」两套口径。</p>
     */
    private void recordFallbackOutcome(final ModelServiceRegistry.ServiceType serviceType,
            final String modelName, final ResponseEntity<?> response) {
        final AdapterMetricsRecorder metricsRecorder = resilienceSupport.getMetricsRecorder();
        if (metricsRecorder == null) {
            return;
        }
        final String marker = response == null ? null
                : response.getHeaders().getFirst(FallbackStrategy.FALLBACK_HEADER);
        if (FallbackStrategy.FALLBACK_HEADER_CACHE_VALUE.equals(marker)) {
            metricsRecorder.recordFallbackHit(serviceType, modelName);
        } else {
            metricsRecorder.recordFallbackMiss(serviceType, modelName);
        }
    }

    // ==================== 非流式请求处理 ====================

    protected <T> Mono<? extends ResponseEntity<?>> processNonStreamingRequest(final T request,
            final String authorization, final WebClient client, final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType, final Class<?> responseType,
            final ServerHttpRequest httpRequest) {
        String adapterType = getAdapterType();
        String finalPath = adaptModelName(path);
        String finalAuth = getAuthorizationHeader(adaptModelName(authorization), adapterType);
        Function<Object, Object> transformRequestFn = req -> transformRequest(req, adapterType);
        Function<Object, Object> transformResponseFn = data -> transformResponse(data, adapterType);

        // 延迟获取 multipartRequestHandler（如果尚未注入）
        MultipartRequestHandler handler = this.multipartRequestHandler;
        if (handler == null) {
            try {
                handler = org.unreal.modelrouter.common.util.ApplicationContextProvider
                        .getBean(MultipartRequestHandler.class);
                this.multipartRequestHandler = handler; // 缓存以供后续使用
                logger.debug("从 ApplicationContext 获取 MultipartRequestHandler 成功");
            } catch (Exception e) {
                logger.warn("无法获取 MultipartRequestHandler: {}", e.getMessage());
            }
        }

        return requestSupport.getNonStreamingProcessor().processRequest(request, finalAuth, client, finalPath,
                selectedInstance, serviceType, responseType, adapterType,
                transformRequestFn, transformResponseFn, handler, httpRequest, getAdditionalHeaders());
    }

    // ==================== 流式请求处理 ====================

    protected <T> Mono<? extends ResponseEntity<?>> processStreamingRequest(final T request,
            final String authorization, final WebClient client, final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final org.springframework.http.server.reactive.ServerHttpRequest httpRequest) {
        StreamingRequestProcessor processor = requestSupport.getStreamingProcessor();
        if (processor == null) {
            logger.error("StreamingRequestProcessor 未注入，无法处理流式请求");
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "流式请求处理器未配置"));
        }
        String adapterType = getAdapterType();
        String finalPath = adaptModelName(path);
        String finalAuth = getAuthorizationHeader(adaptModelName(authorization), adapterType);
        // #720：上游可能是任意形状（OpenAI 原生即 ChatDTO.Request，其它面由翻译器构造），
        // 因此在这里、拿原始请求判定 switch，再以请求属性传给处理器（处理器只读属性，不依赖 DTO 形状）。
        if (httpRequest != null && request instanceof ChatDTO.Request chatRequest) {
            httpRequest.getAttributes().put(
                    StreamingRequestProcessor.INCLUDE_USAGE_ATTRIBUTE, chatRequest.includeUsage());
        }
        Object transformedRequest = transformRequest(request, adapterType);
        return processor.processStreamingRequest(transformedRequest, finalAuth, client,
                finalPath, selectedInstance, serviceType, adapterType, httpRequest);
    }

    // ==================== 抽象方法 ====================

    public abstract AdapterCapabilities supportCapability();
    protected abstract String getAdapterType();

    // ==================== 业务方法入口 ====================

    @SuppressWarnings("all")
    @Override
    public Mono chat(final ChatDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.chat);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.chat, request.model(),
                (req, auth, client, path, instance, st) -> Boolean.TRUE.equals(req.stream())
                        ? processStreamingRequest(req, auth,
                                getWebClientForInstance(instance, true), path, instance, st, httpRequest)
                        : processNonStreamingRequest(req, auth, client, path, instance, st, String.class, httpRequest));
    }

    @SuppressWarnings("all")
    @Override
    public Mono embedding(final EmbeddingDTO.Request request, final String authorization,
            final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.embedding);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.embedding, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class, httpRequest));
    }

    @SuppressWarnings("all")
    @Override
    public Mono rerank(final RerankDTO.Request request, final String authorization,
            final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.rerank);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.rerank, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class, httpRequest));
    }

    @SuppressWarnings("all")
    @Override
    public Mono tts(final TtsDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.tts);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.tts, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, byte[].class, httpRequest));
    }

    @SuppressWarnings("all")
    @Override
    public Mono stt(final SttDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.stt);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.stt, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class, httpRequest));
    }

    @SuppressWarnings("all")
    public Mono imageGenerate(final ImageGenerateDTO.Request request, final String authorization,
                              final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgGen);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.imgGen, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class, httpRequest));
    }

    @SuppressWarnings("all")
    public Mono imageEdit(final ImageEditDTO.Request request, final String authorization,
                          final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgEdit);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.imgEdit, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class, httpRequest));
    }

    // ==================== 子类可重写方法 ====================

    /**
     * 获取适配器特有的额外请求头
     * 子类可重写此方法以添加适配器特定的头（如 Claude 的 anthropic-version）
     *
     * @return 额外请求头映射，空 Map 表示无额外头
     */
    protected Map<String, String> getAdditionalHeaders() {
        return java.util.Collections.emptyMap();
    }

    protected <T> WebClient.RequestBodySpec configureRequestHeaders(
            final WebClient.RequestBodySpec requestSpec, final T request) {
        return multipartRequestHandler.configureRequestHeaders(requestSpec, request);
    }

    protected <T> WebClient.RequestBodySpec configureRequestHeaders(
            final WebClient.RequestBodySpec requestSpec, final T request,
            final ModelRouterProperties.ModelInstance instance) {
        return multipartRequestHandler.configureRequestHeaders(requestSpec, request, instance);
    }

    protected String transformStreamChunk(final String chunk) {
        return requestSupport.getResponseTransformer().transformStreamChunk(chunk);
    }

    protected long calculateRequestSize(final Object request) {
        if (request == null) return 0;
        try { return request.toString().getBytes().length; } catch (Exception e) { return 0; }
    }

    // ==================== 辅助方法（委托） ====================

    protected ModelRouterProperties.ModelInstance selectInstance(
            final ModelServiceRegistry.ServiceType serviceType, final String modelName, final String clientIp) {
        return requestSupport.getInstanceSelector().selectInstance(serviceType, modelName, clientIp);
    }

    protected String getModelPath(final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        return requestSupport.getInstanceSelector().getModelPath(serviceType, modelName);
    }

    /**
     * 解析本次请求发往上游的路径：优先用**被选中的实例**自己声明的 {@code path}。
     *
     * <p>#635：早期实现按 {@code modelName} 反查实例再取 path（{@link #getModelPath}），一旦请求里的
     * model 不是实例名（资源池名、{@code auto-model}、别名），反查失败会得到空串，请求于是打到上游
     * baseUrl 的根路径；同名多实例且 path 不同时，也会串用另一个实例的 path。</p>
     *
     * @param instance    本次实际被选中的实例（网关注入或本类自选）
     * @param serviceType 服务类型（仅在回退反查时使用）
     * @param modelName   请求里的模型名（仅在回退反查时使用）
     * @return 目标路径；实例未声明 path 时回退为按模型名反查的结果（可能为空串，保持既有行为）
     */
    protected String resolvePath(final ModelRouterProperties.ModelInstance instance,
            final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        if (instance != null && instance.getPath() != null && !instance.getPath().isBlank()) {
            return instance.getPath();
        }
        return getModelPath(serviceType, modelName);
    }

    protected Object transformRequest(final Object request, final String adapterType) {
        return requestSupport.getResponseTransformer().transformRequest(request, adapterType);
    }

    protected String adaptModelName(final String originalModelName) {
        return requestSupport.getResponseTransformer().adaptModelName(originalModelName);
    }

    protected Object transformResponse(final Object responseData, final String adapterType) {
        return requestSupport.getResponseTransformer().transformResponse(responseData, adapterType);
    }

    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        return authorization;
    }

    /**
     * 把成功响应写进降级缓存（#823）.
     *
     * @param serviceType 服务类型
     * @param modelName   模型名
     * @param request     原始请求（参与请求键计算）
     * @param response    成功响应
     */
    protected void cacheSuccessfulResponse(final ModelServiceRegistry.ServiceType serviceType,
            final String modelName, final Object request, final ResponseEntity<?> response) {
        if (serviceType == null) {
            return;
        }
        ModelRouterProperties.ServiceConfig serviceConfig = getRegistry().getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                getRegistry().getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);
        if (fallbackStrategy instanceof CacheFallbackStrategy) {
            ((CacheFallbackStrategy) fallbackStrategy).cacheResponse(
                    FallbackRequestKey.of(serviceType.name(), modelName,
                            AffinityContextHolder.apiKeyId(), request, objectMapper),
                    response);
        }
    }

    protected void logAdapterRetryEvent(final String adapterType,
            final ModelRouterProperties.ModelInstance instance, final int retryCount,
            final int maxRetries, final Throwable error) {
        resilienceSupport.getTracingManager().recordRetry(adapterType, instance, retryCount, maxRetries, error);
        AdapterMetricsRecorder metricsRecorder = resilienceSupport.getMetricsRecorder();
        if (metricsRecorder != null) {
            metricsRecorder.recordRetry(adapterType,
                    instance != null ? instance.getName() : "unknown", retryCount, error);
        }
    }

    protected void logAdapterTransformError(final String adapterType, final Throwable error) {
        resilienceSupport.getTracingManager().recordTransformError(adapterType, error);
        AdapterMetricsRecorder metricsRecorder = resilienceSupport.getMetricsRecorder();
        if (metricsRecorder != null) {
            metricsRecorder.recordError(adapterType, "unknown", "TRANSFORM_ERROR", error, 0, null);
        }
    }

    // ==================== 函数式接口 ====================

    @FunctionalInterface
    protected interface RequestProcessor<T> {
        Mono<? extends ResponseEntity<?>> process(T request, String authorization, WebClient client,
                String path, ModelRouterProperties.ModelInstance selectedInstance,
                ModelServiceRegistry.ServiceType serviceType);
    }
}

package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.handler.MultipartRequestHandler;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.processor.FallbackRequestProcessor;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.processor.StreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.request.NonStreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
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
import org.unreal.modelrouter.router.fallback.FallbackStrategy;
import org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Function;

/**
 * BaseAdapter - v2.26.6 精简版
 * 所有请求处理逻辑已完全委托给专门组件。
 */
public abstract class BaseAdapter implements ServiceCapability {

    private final ModelServiceRegistry registry;
    private final ModelCallStatsRepository statsRepository;
    private final RequestBuilder requestBuilder;
    private final ResponseHandler responseHandler;
    private final InstanceSelector instanceSelector;
    private final ResponseTransformer responseTransformer;
    private final CapabilityChecker capabilityChecker;
    private final AdapterErrorHandler errorHandler;
    private final RetryPolicy retryPolicy;
    private final HttpRequestProcessor httpRequestProcessor;
    private final ResponseMapper responseMapper;
    private final AdapterMetricsRecorder metricsRecorder;
    private final AdapterTracingManager tracingManager;
    private final ErrorResponseBuilder errorResponseBuilder;
    private final NonStreamingRequestProcessor nonStreamingProcessor;
    protected final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(BaseAdapter.class);

    @Autowired(required = false)
    private StreamingRequestProcessor streamingRequestProcessor;
    @Autowired
    private MultipartRequestHandler multipartRequestHandler;
    @Autowired(required = false)
    private FallbackRequestProcessor fallbackRequestProcessor;

    @Autowired
    public BaseAdapter(final ModelServiceRegistry registry, final ObjectMapper objectMapper,
                       final ModelCallStatsRepository statsRepository, final RequestBuilder requestBuilder,
                       final ResponseHandler responseHandler, final InstanceSelector instanceSelector,
                       final ResponseTransformer responseTransformer, final CapabilityChecker capabilityChecker,
                       final AdapterErrorHandler errorHandler, final RetryPolicy retryPolicy,
                       final HttpRequestProcessor httpRequestProcessor, final ResponseMapper responseMapper,
                       final AdapterMetricsRecorder metricsRecorder, final AdapterTracingManager tracingManager,
                       final ErrorResponseBuilder errorResponseBuilder,
                       final NonStreamingRequestProcessor nonStreamingProcessor) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.statsRepository = statsRepository;
        this.requestBuilder = requestBuilder;
        this.responseHandler = responseHandler;
        this.instanceSelector = instanceSelector;
        this.responseTransformer = responseTransformer;
        this.capabilityChecker = capabilityChecker;
        this.errorHandler = errorHandler;
        this.retryPolicy = retryPolicy;
        this.httpRequestProcessor = httpRequestProcessor;
        this.responseMapper = responseMapper;
        this.metricsRecorder = metricsRecorder;
        this.tracingManager = tracingManager;
        this.errorResponseBuilder = errorResponseBuilder;
        this.nonStreamingProcessor = nonStreamingProcessor;
    }

    // ==================== 核心方法 ====================

    private String classifyError(final Throwable throwable) {
        return errorHandler.classifyError(throwable);
    }

    public ModelServiceRegistry getRegistry() { return registry; }

    protected RetryPolicy getRetryPolicy() { return retryPolicy; }

    protected WebClient getWebClient(final ModelServiceRegistry.ServiceType serviceType,
                                     final String modelName, final ServerHttpRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        String baseUrl = selectedInstance.getBaseUrl();
        try {
            var tracingFactory = org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                    org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory.class);
            return tracingFactory.createTracingWebClient(baseUrl);
        } catch (Exception e) {
            return getRegistry().getClient(serviceType, modelName, clientIp);
        }
    }

    protected Mono<ResponseEntity<String>> checkCapability(final ModelServiceRegistry.ServiceType serviceType) {
        return capabilityChecker.checkCapability(supportCapability(), serviceType);
    }

    // ==================== 请求处理模板方法 ====================

    @SuppressWarnings("all")
    protected <T> Mono processRequest(final T request, final String authorization,
            final ServerHttpRequest httpRequest, final ModelServiceRegistry.ServiceType serviceType,
            final String modelName, final RequestProcessor<T> processor) {
        ModelRouterProperties.ModelInstance selectedInstance =
                selectInstance(serviceType, modelName, IpUtils.getClientIp(httpRequest));
        WebClient client = getWebClient(serviceType, modelName, httpRequest);
        String path = getModelPath(serviceType, modelName);
        long startTime = System.currentTimeMillis();
        String adapterType = getAdapterType();
        String modelNameFromRequest = ModelUtils.getModelNameFromRequest(request);
        tracingManager.recordCallStart(adapterType, selectedInstance, serviceType, modelNameFromRequest);
        return processRequestWithRetry(request, authorization, client, path, selectedInstance,
                serviceType, modelNameFromRequest, processor, startTime, 0);
    }

    @SuppressWarnings("all")
    private <T> Mono processRequestWithRetry(final T request, final String authorization,
            final WebClient client, final String path, final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType, final String modelName,
            final RequestProcessor<T> processor, final long startTime, final int retryCount) {
        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        int maxRetries = retryPolicy.getMaxRetriesByServiceType(serviceType);
        return processor.process(request, authorization, client, path, selectedInstance, serviceType)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    boolean success = response != null && response.getStatusCode().is2xxSuccessful();
                    if (metricsRecorder != null) {
                        metricsRecorder.recordCompleteCall(adapterType, instanceName, duration, success,
                                null, modelName, serviceType, selectedInstance);
                    }
                    tracingManager.recordCallComplete(adapterType, selectedInstance, serviceType,
                            ModelUtils.getModelNameFromRequest(request), duration, success);
                })
                .onErrorResume(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String errorCode = classifyError(throwable);
                    if (metricsRecorder != null) {
                        metricsRecorder.recordCompleteCall(adapterType, instanceName, duration, false,
                                errorCode, modelName, serviceType, selectedInstance);
                    }
                    if (retryPolicy.canRetry(retryCount, throwable) && retryPolicy.isRetryable(throwable)) {
                        tracingManager.recordRetry(adapterType, selectedInstance, retryCount + 1, maxRetries, throwable);
                        if (metricsRecorder != null) {
                            metricsRecorder.recordRetry(adapterType, instanceName, retryCount + 1, throwable);
                        }
                        long retryDelay = retryPolicy.calculateRetryDelay(retryCount);
                        return Mono.delay(Duration.ofMillis(retryDelay))
                                .then(processRequestWithRetry(request, authorization, client, path,
                                        selectedInstance, serviceType, modelName, processor,
                                        System.currentTimeMillis(), retryCount + 1));
                    }
                    return errorResponseBuilder.buildErrorResponse(throwable).flatMap(Mono::error);
                });
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
        Mono<ResponseEntity<?>> requestMono = (Mono<ResponseEntity<?>>)(Mono) processRequest(
                request, authorization, httpRequest, serviceType, modelName, processor);
        if (fallbackRequestProcessor != null) {
            return requestMono.onErrorResume(throwable ->
                    fallbackRequestProcessor.handleFallbackError(throwable, fallbackStrategy));
        }
        return requestMono.onErrorResume(throwable -> fallbackStrategy.fallback((Exception) throwable) != null
                ? Mono.just(fallbackStrategy.fallback((Exception) throwable))
                : Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "服务降级且无缓存")));
    }

    // ==================== 非流式请求处理 ====================

    protected <T> Mono<? extends ResponseEntity<?>> processNonStreamingRequest(final T request,
            final String authorization, final WebClient client, final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType, final Class<?> responseType) {
        String adapterType = getAdapterType();
        String finalPath = adaptModelName(path);
        String finalAuth = getAuthorizationHeader(adaptModelName(authorization), adapterType);
        Function<Object, Object> transformRequestFn = req -> transformRequest(req, adapterType);
        Function<Object, Object> transformResponseFn = data -> transformResponse(data, adapterType);
        return nonStreamingProcessor.processRequest(request, finalAuth, client, finalPath,
                selectedInstance, serviceType, responseType, adapterType,
                transformRequestFn, transformResponseFn, multipartRequestHandler);
    }

    // ==================== 流式请求处理 ====================

    protected <T> Mono<? extends ResponseEntity<?>> processStreamingRequest(final T request,
            final String authorization, final WebClient client, final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType) {
        if (streamingRequestProcessor == null) {
            logger.error("StreamingRequestProcessor 未注入，无法处理流式请求");
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "流式请求处理器未配置"));
        }
        String adapterType = getAdapterType();
        String finalPath = adaptModelName(path);
        String finalAuth = getAuthorizationHeader(adaptModelName(authorization), adapterType);
        Object transformedRequest = transformRequest(request, adapterType);
        return streamingRequestProcessor.processStreamingRequest(transformedRequest, finalAuth, client,
                finalPath, selectedInstance, serviceType, adapterType);
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
                (req, auth, client, path, instance, st) -> req.stream()
                        ? processStreamingRequest(req, auth, client, path, instance, st)
                        : processNonStreamingRequest(req, auth, client, path, instance, st, String.class));
    }

    @SuppressWarnings("all")
    @Override
    public Mono embedding(final EmbeddingDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.embedding);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.embedding, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class));
    }

    @SuppressWarnings("all")
    @Override
    public Mono rerank(final RerankDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.rerank);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.rerank, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class));
    }

    @SuppressWarnings("all")
    @Override
    public Mono tts(final TtsDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.tts);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.tts, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, byte[].class));
    }

    @SuppressWarnings("all")
    @Override
    public Mono stt(final SttDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.stt);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.stt, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class));
    }

    @SuppressWarnings("all")
    public Mono imageGenerate(final ImageGenerateDTO.Request request, final String authorization,
                              final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgGen);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.imgGen, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class));
    }

    @SuppressWarnings("all")
    public Mono imageEdit(final ImageEditDTO.Request request, final String authorization,
                          final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgEdit);
        if (capabilityCheck != null) return capabilityCheck;
        return processRequestWithFallback(request, authorization, httpRequest,
                ModelServiceRegistry.ServiceType.imgEdit, request.model(),
                (req, auth, client, path, instance, st) ->
                        processNonStreamingRequest(req, auth, client, path, instance, st, String.class));
    }

    // ==================== 子类可重写方法 ====================

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
        return responseTransformer.transformStreamChunk(chunk);
    }

    protected long calculateRequestSize(final Object request) {
        if (request == null) return 0;
        try { return request.toString().getBytes().length; } catch (Exception e) { return 0; }
    }

    // ==================== 辅助方法（委托） ====================

    protected ModelRouterProperties.ModelInstance selectInstance(
            final ModelServiceRegistry.ServiceType serviceType, final String modelName, final String clientIp) {
        return instanceSelector.selectInstance(serviceType, modelName, clientIp);
    }

    protected String getModelPath(final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        return instanceSelector.getModelPath(serviceType, modelName);
    }

    protected Object transformRequest(final Object request, final String adapterType) {
        return responseTransformer.transformRequest(request, adapterType);
    }

    protected String adaptModelName(final String originalModelName) {
        return responseTransformer.adaptModelName(originalModelName);
    }

    protected Object transformResponse(final Object responseData, final String adapterType) {
        return responseTransformer.transformResponse(responseData, adapterType);
    }

    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        return authorization;
    }

    protected void cacheSuccessfulResponse(final ModelServiceRegistry.ServiceType serviceType,
            final String modelName, final ResponseEntity<?> response, final ServerHttpRequest httpRequest) {
        ModelRouterProperties.ServiceConfig serviceConfig = getRegistry().getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                getRegistry().getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);
        if (fallbackStrategy instanceof CacheFallbackStrategy) {
            ((CacheFallbackStrategy) fallbackStrategy).cacheResponse(serviceType, modelName, httpRequest, response);
        }
    }

    protected void logAdapterRetryEvent(final String adapterType,
            final ModelRouterProperties.ModelInstance instance, final int retryCount,
            final int maxRetries, final Throwable error) {
        tracingManager.recordRetry(adapterType, instance, retryCount, maxRetries, error);
        if (metricsRecorder != null) {
            metricsRecorder.recordRetry(adapterType, instance != null ? instance.getName() : "unknown", retryCount, error);
        }
    }

    protected void logAdapterTransformError(final String adapterType, final Throwable error) {
        tracingManager.recordTransformError(adapterType, error);
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
package org.unreal.modelrouter.router.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.processor.StreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.handler.MultipartRequestHandler;
import org.unreal.modelrouter.router.adapter.processor.FallbackRequestProcessor;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.common.constants.ServiceTypeConstants;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.common.util.IpUtils;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;
import org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public abstract class BaseAdapter implements ServiceCapability {

    private final ModelServiceRegistry registry;
    private final MetricsCollector metricsCollector;
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

    protected final ObjectMapper objectMapper;

    private Logger logger = LoggerFactory.getLogger(BaseAdapter.class);

    @Autowired
    public BaseAdapter(final ModelServiceRegistry registry,
                       final MetricsCollector metricsCollector,
                       final ObjectMapper objectMapper,
                       final ModelCallStatsRepository statsRepository,
                       final RequestBuilder requestBuilder,
                       final ResponseHandler responseHandler,
                       final InstanceSelector instanceSelector,
                       final ResponseTransformer responseTransformer,
                       final CapabilityChecker capabilityChecker,
                       final AdapterErrorHandler errorHandler,
                       final RetryPolicy retryPolicy,
                       final HttpRequestProcessor httpRequestProcessor,
                       final ResponseMapper responseMapper,
                       final AdapterMetricsRecorder metricsRecorder,
                       final AdapterTracingManager tracingManager) {
        this.registry = registry;
        this.metricsCollector = metricsCollector;
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
    }

    // ============ v2.15.x 新组件注入 (setter方式，向后兼容) ============

    @Autowired(required = false)
    private StreamingRequestProcessor streamingRequestProcessor;

    @Autowired(required = false)
    private MultipartRequestHandler multipartRequestHandler;
    @Autowired(required = false)
    private FallbackRequestProcessor fallbackRequestProcessor;
    /**
     * v2.0.0: 错误分类
     * v2.3.0: 委托给 AdapterErrorHandler
     *
     * @param throwable 异常
     * @return 错误码分类
     */
    private String classifyError(final Throwable throwable) {
        return errorHandler.classifyError(throwable);
    }

    public ModelServiceRegistry getRegistry() {
        return registry;
    }

    protected MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    /**
     * 获取WebClient实例
     * 确保WebClient配置了追踪拦截器
     */
    protected WebClient getWebClient(final ModelServiceRegistry.ServiceType serviceType,
                                     final String modelName,
                                     final ServerHttpRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, clientIp);
        String baseUrl = selectedInstance.getBaseUrl();
        // 尝试获取带追踪功能的WebClient
        try {
            org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory tracingFactory =
                    org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                            org.unreal.modelrouter.monitor.tracing.client.TracingWebClientFactory.class);
            return tracingFactory.createTracingWebClient(baseUrl);
        } catch (Exception e) {
            return getRegistry().getClient(serviceType, modelName, clientIp);
        }
    }

    /**
     * 检查适配器是否支持指定的服务能力
     * v2.2.7: 委托给 CapabilityChecker
     */
    protected Mono<ResponseEntity<String>> checkCapability(final ModelServiceRegistry.ServiceType serviceType) {
        return capabilityChecker.checkCapability(supportCapability(), serviceType);
    }

    /**
     * 通用请求处理模板方法
     * 
     * 使用 RequestProcessor 函数式接口处理请求，支持降级策略和重试机制。
     * 
     * @param request 请求对象
     * @param authorization 认证信息
     * @param httpRequest HTTP请求对象
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param processor 请求处理器
     * @return Mono响应
     * @since v2.0.0
     */
    @SuppressWarnings("all")
    protected <T> Mono processRequest(
            final T request,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final RequestProcessor<T> processor) {

        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, IpUtils.getClientIp(httpRequest));
        WebClient client = getWebClient(serviceType, modelName, httpRequest);
        String path = getModelPath(serviceType, modelName);

        // 记录开始时间用于计算响应时间
        long startTime = System.currentTimeMillis();
        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();

        // 获取追踪上下文并记录适配器调用开始
        org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                enhancer.logAdapterCallStart(adapterType, selectedInstance, serviceType.name(),
                        getModelNameFromRequest(request), tracingContext);

                // 增强适配器Span
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    enhancer.enhanceAdapterSpan(currentSpan, adapterType, selectedInstance,
                            serviceType.name(), getModelNameFromRequest(request));
                }
            } catch (Exception e) {
                // 忽略追踪错误，不影响主业务流程
            }
        }

        String modelNameFromRequest = getModelNameFromRequest(request);
        return processRequestWithRetry(request, authorization, client, path, selectedInstance,
                serviceType, modelNameFromRequest, processor, tracingContext, startTime, 0);
    }

    /**
     * 带重试的请求处理
     */
    @SuppressWarnings("all")
    private <T> Mono processRequestWithRetry(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final RequestProcessor<T> processor,
            final org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext,
            final long startTime,
            final int retryCount) {

        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        int maxRetries = getMaxRetries(serviceType);

        return processor.process(request, authorization, client, path, selectedInstance, serviceType)
                // 在成功完成时记录成功
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    boolean success = response != null && response.getStatusCode().is2xxSuccessful();

                    if (success) {
                        getRegistry().recordCallComplete(serviceType, selectedInstance);
                        // 记录成功的后端调用指标
                        if (metricsCollector != null) {
                            metricsCollector.recordBackendCall(adapterType, instanceName, duration, true);
                        }
                        // 缓存功能由降级策略处理 (CacheFallbackStrategy)
                    } else {
                        // 非2xx响应视为失败
                        getRegistry().recordCallFailure(serviceType, selectedInstance);
                        // 记录失败的后端调用指标
                        if (metricsCollector != null) {
                            metricsCollector.recordBackendCall(adapterType, instanceName, duration, false);
                        }
                    }

                    // 记录适配器调用完成追踪
                    if (tracingContext != null && tracingContext.isActive()) {
                        try {
                            org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                                    org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                                            org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                            enhancer.logAdapterCallComplete(adapterType, selectedInstance, serviceType.name(),
                                    getModelNameFromRequest(request), duration, success, tracingContext);
                        } catch (Exception e) {
                            // 忽略追踪错误
                        }
                    }
                })
                // 在发生错误时处理重试逻辑
                .onErrorResume(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;
                    getRegistry().recordCallFailure(serviceType, selectedInstance);

                    // 记录异常的后端调用指标
                    if (metricsCollector != null) {
                        metricsCollector.recordBackendCall(adapterType, instanceName, duration, false);
                    }

                    // v2.0.0: 记录模型调用统计
                    if (statsRepository != null) {
                        statsRepository.updateStats(serviceType.name(), modelName, false, duration);
                        // 记录错误类型
                        String errorCode = classifyError(throwable);
                        statsRepository.recordErrorCode(serviceType.name(), modelName, errorCode);
                    }

                    // v2.9.14: 使用 RetryPolicy 替代 shouldRetry
                    if (retryPolicy.canRetry(retryCount, throwable) && retryPolicy.isRetryable(throwable)) {
                        // 记录重试追踪
                        if (tracingContext != null && tracingContext.isActive()) {
                            try {
                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                                        org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                                enhancer.logAdapterRetry(adapterType, selectedInstance, retryCount + 1,
                                        maxRetries, throwable, tracingContext);
                            } catch (Exception e) {
                                // 忽略追踪错误
                            }
                        }

                        // 记录重试指标
                        if (metricsCollector != null) {
                            // 使用 recordBackendCall 记录重试，duration 为 0，success 为 false 表示需要重试
                            metricsCollector.recordBackendCall(adapterType, instanceName, 0, false);
                        }

                        // 等待重试延迟
                        long retryDelay = calculateRetryDelay(retryCount);
                        return Mono.delay(java.time.Duration.ofMillis(retryDelay))
                                .then(processRequestWithRetry(request, authorization, client, path,
                                        selectedInstance, serviceType, modelName, processor, tracingContext,
                                        System.currentTimeMillis(), retryCount + 1));
                    } else {
                        // 记录适配器调用失败追踪
                        if (tracingContext != null && tracingContext.isActive()) {
                            try {
                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                                        org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                                enhancer.logAdapterCallComplete(adapterType, selectedInstance, serviceType.name(),
                                        getModelNameFromRequest(request), duration, false, tracingContext);
                            } catch (Exception e) {
                                // 忽略追踪错误
                            }
                        }

                        // 记录最终失败指标
                        if (metricsCollector != null) {
                            // 使用 recordBackendCall 记录最终失败
                            long finalDuration = System.currentTimeMillis() - startTime;
                            metricsCollector.recordBackendCall(adapterType, instanceName, finalDuration, false);
                        }

                        // 修复：保持原始的错误状态码，并确保传递给 Mono.error() 的参数是 Throwable 类型
                        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                            org.springframework.web.reactive.function.client.WebClientResponseException webEx =
                                    (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                            ResponseStatusException responseEx = new ResponseStatusException(webEx.getStatusCode(), webEx.getMessage(), webEx);
                            logger.error("WebClientResponseException 处理失败: {}", responseEx.getMessage(), responseEx);
                            return Mono.error(responseEx);
                        } else if (throwable instanceof DownstreamServiceException) {
                            DownstreamServiceException downStreamEx = (DownstreamServiceException) throwable;
                            ResponseStatusException responseEx = new ResponseStatusException(
                                    downStreamEx.getStatusCode(),
                                    downStreamEx.getMessage(),
                                    downStreamEx);
                            logger.error("DownstreamServiceException 处理失败: {}", responseEx.getMessage(), responseEx);
                            return Mono.error(responseEx);
                        } else {
                            logger.error("未知异常类型处理失败: {}", throwable.getMessage(), throwable);
                            return Mono.error(throwable);
                        }
                    }
                });
    }

    /**
     * 通用请求处理模板方法（带降级处理）
     */
    @SuppressWarnings("all")
    protected <T> Mono<? extends ResponseEntity<?>> processRequestWithFallback(
            final T request,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final RequestProcessor<T> processor) {

        ModelRouterProperties.ServiceConfig serviceConfig = getRegistry().getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                getRegistry().getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);

        // v2.15.4: TODO - FallbackRequestProcessor 委托调用因类型推断问题暂未启用
        // processRequest 返回原始 Mono 类型，导致泛型推断失败
        // 未来改进：修复 processRequest 泛型声明后启用委托

        // 如果未启用降级，则使用普通的处理方法
        if (fallbackStrategy == null) {
            return processRequest(request, authorization, httpRequest, serviceType, modelName, processor);
        }

        return processRequest(request, authorization, httpRequest, serviceType, modelName, processor)
                .onErrorResume(throwable -> {
                    try {
                        ResponseEntity<?> fallbackResponse = fallbackStrategy.fallback((Exception) throwable);
                        if (fallbackResponse == null || fallbackResponse.getBody() == null) {
                            if (throwable instanceof DownstreamServiceException) {
                                DownstreamServiceException downStreamEx = (DownstreamServiceException) throwable;
                                return Mono.error(new ResponseStatusException(
                                        downStreamEx.getStatusCode(),
                                        downStreamEx.getMessage(),
                                        downStreamEx));
                            } else if (throwable instanceof ResponseStatusException) {
                                return Mono.error((ResponseStatusException) throwable);
                            } else {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE, "服务降级且无缓存"));
                            }
                        }
                        if (!fallbackResponse.getStatusCode().is2xxSuccessful()) {
                            if (throwable instanceof DownstreamServiceException) {
                                DownstreamServiceException downStreamEx = (DownstreamServiceException) throwable;
                                return Mono.error(new ResponseStatusException(
                                        downStreamEx.getStatusCode(),
                                        downStreamEx.getMessage(),
                                        downStreamEx));
                            } else if (throwable instanceof ResponseStatusException) {
                                return Mono.error((ResponseStatusException) throwable);
                            } else {
                                return Mono.error(new ResponseStatusException(
                                        fallbackResponse.getStatusCode(),
                                        fallbackResponse.getBody() != null
                                                ? fallbackResponse.getBody().toString() : "未知错误"));
                            }
                        }
                        return Mono.just(fallbackResponse);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    /**
     * 通用非流式请求处理
     */
    protected <T> Mono<? extends ResponseEntity<?>> processNonStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final Class<?> responseType) {

        Object transformedRequest = transformRequest(request, getAdapterType());
        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        String modelName = getModelNameFromRequest(request);

        // 记录请求开始指标
        long requestStartTime = System.currentTimeMillis();

        String finalPath = adaptModelName(path);
        String finalAuth = getAuthorizationHeader(adaptModelName(authorization), getAdapterType());

        logger.debug("发送请求到下游服务: instance={}, path={}, auth={}",
                instanceName, finalPath, finalAuth != null ? "***" : "null");

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(finalPath)
                .header("Authorization", finalAuth);

        // 设置Content-Type（如果需要）
        requestSpec = configureRequestHeaders(requestSpec, request, selectedInstance);

        if (responseType == byte[].class) {
            // ... (二进制文件处理逻辑保持不变)
            return requestSpec
                    .body(createRequestBody(transformedRequest))
                    .exchangeToMono(clientResponse -> {
                        // 处理 5xx 服务器错误
                        if (clientResponse.statusCode().is5xxServerError()) {
                            return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                        }

                        // 处理 4xx 客户端错误
                        if (clientResponse.statusCode().is4xxClientError()) {
                            if (clientResponse.statusCode().value() == 401) {
                                logger.error("下游服务认证失败 (401): instance={}, path={}, response={}",
                                        instanceName, path, clientResponse.statusCode());
                            } else if (clientResponse.statusCode().value() == 400) {
                                logger.error("下游服务请求错误 (400): instance={}, path={}, response={}",
                                        instanceName, path, clientResponse.statusCode());
                            } else if (clientResponse.statusCode().value() == 503) {
                                logger.error("下游服务请求错误 (503): instance={}, path={}, response={}",
                                        instanceName, path, clientResponse.statusCode());
                            }
                            return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                        }

                        // 获取响应体和响应头
                        return clientResponse.bodyToMono(byte[].class)
                                .map(body -> {
                                    // 构建包含响应头信息的 ResponseEntity
                                    ResponseEntity.BodyBuilder responseBuilder;

                                    if (clientResponse.statusCode().is2xxSuccessful()) {
                                        responseBuilder = ResponseEntity.ok();
                                    } else {
                                        responseBuilder = ResponseEntity.status(clientResponse.statusCode());
                                    }

                                    // 获取并复制重要的响应头
                                    org.springframework.http.HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();

                                    // 设置 Content-Type（最重要）
                                    if (downstreamHeaders.getContentType() != null) {
                                        responseBuilder.contentType(downstreamHeaders.getContentType());
                                    }

                                    // 设置 Content-Length
                                    if (downstreamHeaders.getContentLength() > 0) {
                                        responseBuilder.contentLength(downstreamHeaders.getContentLength());
                                    }

                                    // 复制 Content-Disposition（文件下载重要）
                                    String contentDisposition = downstreamHeaders.getFirst("Content-Disposition");
                                    if (contentDisposition != null) {
                                        responseBuilder.header("Content-Disposition", contentDisposition);
                                    }

                                    // 复制缓存相关头信息
                                    String cacheControl = downstreamHeaders.getFirst("Cache-Control");
                                    if (cacheControl != null) {
                                        responseBuilder.header("Cache-Control", cacheControl);
                                    }

                                    String etag = downstreamHeaders.getFirst("ETag");
                                    if (etag != null) {
                                        responseBuilder.header("ETag", etag);
                                    }

                                    String lastModified = downstreamHeaders.getFirst("Last-Modified");
                                    if (lastModified != null) {
                                        responseBuilder.header("Last-Modified", lastModified);
                                    }

                                    // 直接返回二进制内容，保持原始格式
                                    return responseBuilder.body(body);
                                })
                                .switchIfEmpty(Mono.fromSupplier(() -> {
                                    // 处理空响应体的情况
                                    ResponseEntity.BodyBuilder responseBuilder = clientResponse.statusCode().is2xxSuccessful()
                                            ? ResponseEntity.ok()
                                            : ResponseEntity.status(clientResponse.statusCode());

                                    org.springframework.http.HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();
                                    if (downstreamHeaders.getContentType() != null) {
                                        responseBuilder.contentType(downstreamHeaders.getContentType());
                                    }

                                    return responseBuilder.build();
                                }));
                    })
                    .doOnSuccess(responseEntity -> {
                        // 记录请求大小指标
                        if (metricsCollector != null && responseEntity != null) {
                            long requestSize = calculateRequestSize(transformedRequest);
                            long responseSize = responseEntity.getBody() != null
                                    ? ((byte[]) responseEntity.getBody()).length : 0;
                            metricsCollector.recordRequestSize(serviceType.name(), requestSize, responseSize);

                            // 记录响应时间指标
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            String status = responseEntity.getStatusCode().toString();
                            metricsCollector.recordRequest(serviceType.name(), "POST", responseTime, status);
                        }
                    })
                    .doOnError(throwable -> {
                        // 记录错误指标
                        if (metricsCollector != null) {
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            String errorType = throwable.getClass().getSimpleName();
                            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
                        }
                    });
        } else {
            return requestSpec
                    .body(createRequestBody(transformedRequest))
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        // 5xx错误视为服务失败，用于熔断器
                        logger.error("下游服务5xx错误: instance={}, path={}, status={}",
                                instanceName, path, clientResponse.statusCode());
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        // 特别处理401错误
                        if (clientResponse.statusCode().value() == 401) {
                            logger.error("下游服务认证失败 (401): instance={}, path={}, response={}",
                                    instanceName, path, clientResponse.statusCode());
                            // 返回特定异常，避免与本地认证错误混淆
                            return Mono.error(new DownstreamServiceException(
                                    "下游服务认证失败，请检查下游服务的认证配置",
                                    HttpStatus.valueOf(clientResponse.statusCode().value())));
                        } else if (clientResponse.statusCode().value() == 400) {
                            logger.error("下游服务请求错误 (400): instance={}, path={}, response={}",
                                    instanceName, path, clientResponse.statusCode());
                            return Mono.error(new DownstreamServiceException(
                                    "下游服务请求参数错误，请检查请求内容",
                                    HttpStatus.valueOf(clientResponse.statusCode().value())));
                        } else if (clientResponse.statusCode().value() == 503) {
                            logger.error("下游服务不可用 (503): instance={}, path={}, response={}",
                                    instanceName, path, clientResponse.statusCode());
                            return Mono.error(new DownstreamServiceException(
                                    "下游服务暂时不可用，请稍后重试",
                                    HttpStatus.valueOf(clientResponse.statusCode().value())));
                        }
                        logger.error("下游服务4xx错误: instance={}, path={}, status={}",
                                instanceName, path, clientResponse.statusCode());
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    })
                    .toEntity(String.class)
                    // ==================== 核心修改开始 ====================
                    .flatMap(responseEntity -> {
                        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
                            String bodyStr = responseEntity.getBody() != null ? responseEntity.getBody() : "";
                            return Mono.<ResponseEntity<?>>error(new ResponseStatusException(
                                    responseEntity.getStatusCode(),
                                    "下游服务异常: " + bodyStr
                            ));
                        }

                        try {
                            String bodyStr = responseEntity.getBody();
                            Object downstreamData;

                            if (bodyStr == null || bodyStr.isEmpty()) {
                                // 如果下游成功响应但body为空，则data部分为null
                                downstreamData = null;
                            } else {
                                // 1. 将下游服务的JSON响应体解析为通用Object
                                downstreamData = objectMapper.readValue(bodyStr, Object.class);
                            }

                            // 2. 对解析后的数据应用转换逻辑（子类可重写此方法）
                            Object transformedData = transformResponse(downstreamData, getAdapterType());

                            // 3. 将最终数据包装到RouterResponse中
                            RouterResponse<Object> finalResponse = RouterResponse.success(transformedData, "请求成功");

                            // 4. 构建并返回包含RouterResponse的最终ResponseEntity
                            return Mono.just(
                                    ResponseEntity.status(responseEntity.getStatusCode())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(finalResponse)
                            );
                        } catch (JsonProcessingException e) {
                            // 如果下游返回的不是合法的JSON，则处理错误
                            logger.error("无法解析下游服务的响应体: {}", responseEntity.getBody(), e);
                            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法解析下游服务响应"));
                        }
                    })
                    // ==================== 核心修改结束 ====================
                    .doOnSuccess(responseEntity -> {
                        Object responseBody = responseEntity.getBody();
                        String bodyStr = responseBody != null ? responseBody.toString() : "";
                        logger.debug("下游服务响应成功: instance={}, path={}, status={}, body length={}",
                                instanceName, path, responseEntity.getStatusCode(), bodyStr.length());
                        if (!bodyStr.isEmpty()) {
                            logger.debug("响应内容预览: {}", bodyStr.length() > 200 ? bodyStr.substring(0, 200) + "..." : bodyStr);
                        }
                    })
                    .doOnSuccess(responseEntity -> {
                        // 记录请求大小指标
                        if (metricsCollector != null && responseEntity != null) {
                            long requestSize = calculateRequestSize(transformedRequest);
                            Object responseBody = responseEntity.getBody();
                            String bodyStr = responseBody != null ? responseBody.toString() : "";
                            long responseSize = bodyStr.getBytes().length;
                            metricsCollector.recordRequestSize(serviceType.name(), requestSize, responseSize);

                            // 记录响应时间指标
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            String status = responseEntity.getStatusCode().toString();
                            metricsCollector.recordRequest(serviceType.name(), "POST", responseTime, status);
                        }
                    })
                    .doOnError(throwable -> {
                        // 记录错误指标
                        if (metricsCollector != null) {
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            String errorType = throwable.getClass().getSimpleName();
                            metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
                            // 可以考虑添加更多错误类型记录
                        }
                    });
        }
    }

    /**
     * 通用流式请求处理
     */
    protected <T> Mono<? extends ResponseEntity<?>> processStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType) {
        // v2.15.2: 优先使用 StreamingRequestProcessor
        if (streamingRequestProcessor != null) {
            return streamingRequestProcessor.processStreamingRequest(
                    request, authorization, client, path,
                    selectedInstance, serviceType, getAdapterType());
        }

        // 原有实现（向后兼容）

        Object transformedRequest = transformRequest(request, getAdapterType());
        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        String modelName = getModelNameFromRequest(request);

        // 记录请求开始指标
        long requestStartTime = System.currentTimeMillis();

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), getAdapterType()));

        requestSpec = configureRequestHeaders(requestSpec, request, selectedInstance);

        // 记录请求大小
        long requestSize = calculateRequestSize(transformedRequest);

        // 使用 ServerSentEvent 包装每个数据块，确保 SSE 格式正确
        Flux<ServerSentEvent<String>> streamResponse = requestSpec
                .bodyValue(transformedRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    // 5xx错误视为服务失败，用于熔断器
                    return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                })
                .bodyToFlux(String.class)
                .map(chunk -> {
                    String transformed = transformStreamChunk(chunk);
                    // 使用 ServerSentEvent.builder() 包装数据，Spring WebFlux 会自动添加正确的 SSE 格式
                    return ServerSentEvent.<String>builder()
                            .data(transformed)
                            .build();
                })
                .doOnComplete(() -> {
                    // 流式响应完成时记录请求大小指标（响应大小难以准确计算）
                    if (metricsCollector != null) {
                        metricsCollector.recordRequestSize(serviceType.name(), requestSize, 0);

                        // 记录响应时间指标
                        long responseTime = System.currentTimeMillis() - requestStartTime;
                        metricsCollector.recordRequest(serviceType.name(), "POST", responseTime, "200");
                    }
                })
                .doOnError(throwable -> {
                    // 记录错误指标
                    if (metricsCollector != null) {
                        long responseTime = System.currentTimeMillis() - requestStartTime;
                        String errorType = throwable.getClass().getSimpleName();
                        metricsCollector.recordBackendCall(adapterType, instanceName, responseTime, false);
                        // 可以考虑添加更多错误类型记录
                    }
                })
                .onErrorResume(throwable -> {
                    return Flux.error(throwable);
                });

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streamResponse));
    }

    /**
     * 配置请求头 - 子类可以重写
     */
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(
            final WebClient.RequestBodySpec requestSpec,
            final T request) {
        // 默认设置JSON Content-Type
        if (!(request instanceof SttDTO.Request) && !(request instanceof ImageEditDTO.Request)) { // STT需要multipart
            requestSpec.header("Content-Type", "application/json");
        } else {
            requestSpec.contentType(MediaType.MULTIPART_FORM_DATA);
        }
        return requestSpec;
    }

    /**
     * 配置请求头（带实例配置） - 子类可以重写
     */
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(
            final WebClient.RequestBodySpec requestSpec,
            final T request,
            final ModelRouterProperties.ModelInstance instance) {
        // 首先应用默认的请求头配置
        // v2.15.2: 优先使用 MultipartRequestHandler
        if (multipartRequestHandler != null) {
            return multipartRequestHandler.configureRequestHeaders(requestSpec, request, instance);
        }

        // 原有实现（向后兼容）
        WebClient.RequestBodySpec spec = configureRequestHeaders(requestSpec, request);
        
        // 然后应用实例配置中的自定义headers
        if (instance != null && instance.getHeaders() != null) {
            for (Map.Entry<String, String> header : instance.getHeaders().entrySet()) {
                spec = spec.header(header.getKey(), header.getValue());
                logger.debug("应用实例自定义请求头: {} = {}", header.getKey(), header.getValue());
            }
        }
        
        return spec;
    }

    /**
     * 创建请求体 - 处理不同类型的请求体格式
     */
    protected BodyInserter<?, ? super ClientHttpRequest> createRequestBody(final Object request) {
        logger.debug("创建请求体，请求类型: {}", request.getClass().getSimpleName());
        // v2.15.2: 优先使用 MultipartRequestHandler
        if (multipartRequestHandler != null) {
            return multipartRequestHandler.createRequestBody(request);
        }

        // 原有实现（向后兼容）

        // 检查是否已经是转换后的multipart数据
        if (request instanceof MultiValueMap) {
            logger.debug("检测到已转换的multipart数据，直接使用");
            return BodyInserters.fromMultipartData((MultiValueMap<String, ?>) request);
        } else if (request instanceof SttDTO.Request) {
            logger.debug("检测到STT请求，使用multipart处理");
            return createMultipartBody((SttDTO.Request) request);
        } else if (request instanceof ImageEditDTO.Request) {
            logger.debug("检测到图像编辑请求，使用multipart处理");
            return createMultipartBody((ImageEditDTO.Request) request);
        } else {
            // 普通JSON请求
            logger.debug("使用JSON请求体处理");
            return BodyInserters.fromValue(request);
        }
    }

    /**
     * 创建STT请求的multipart表单数据
     */
    private BodyInserter<?, ? super ClientHttpRequest> createMultipartBody(final SttDTO.Request sttRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        logger.debug("创建STT multipart请求体: model={}, file={}, language={}",
                sttRequest.model(),
                sttRequest.file() != null ? sttRequest.file().filename() : "null",
                sttRequest.language());

        // 添加文件部分
        if (sttRequest.file() != null) {
            parts.add("file", sttRequest.file());
            logger.debug("添加文件部分: filename={}", sttRequest.file().filename());
        }

        // 添加其他表单字段
        if (sttRequest.model() != null) {
            parts.add("model", sttRequest.model());
            logger.debug("添加model字段: {}", sttRequest.model());
        }
        if (sttRequest.language() != null) {
            parts.add("language", sttRequest.language());
            logger.debug("添加language字段: {}", sttRequest.language());
        }
        if (sttRequest.prompt() != null) {
            parts.add("prompt", sttRequest.prompt());
            logger.debug("添加prompt字段: {}", sttRequest.prompt());
        }
        if (sttRequest.responseFormat() != null) {
            parts.add("response_format", sttRequest.responseFormat());
            logger.debug("添加response_format字段: {}", sttRequest.responseFormat());
        }
        if (sttRequest.temperature() != null) {
            parts.add("temperature", sttRequest.temperature().toString());
            logger.debug("添加temperature字段: {}", sttRequest.temperature());
        }

        logger.debug("Multipart表单数据创建完成，包含{}个字段", parts.size());
        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 创建图像编辑请求的multipart表单数据
     */
    private BodyInserter<?, ? super ClientHttpRequest> createMultipartBody(final ImageEditDTO.Request imageEditRequest) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        // 这里需要根据ImageEditDTO.Request的实际结构来实现
        // 暂时返回空的multipart数据

        return BodyInserters.fromMultipartData(parts);
    }

    /**
     * 转换流式响应块 - 子类可以重写
     */
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }

    public abstract AdapterCapabilities supportCapability();

    /**
     * 处理聊天请求
     *
     * @param request       聊天请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    @Override
    public Mono chat(final ChatDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.chat);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.chat,
                request.model(), (req, auth, client, path, instance, serviceType) -> {
                    if (req.stream()) {
                        return processStreamingRequest(req, auth, client, path, instance, serviceType);
                    } else {
                        return processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class);
                    }
                });
    }

    /**
     * 处理嵌入请求
     *
     * @param request       嵌入请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    @Override
    public Mono embedding(final EmbeddingDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.embedding);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.embedding,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    /**
     * 处理重排序请求
     *
     * @param request       重排序请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    @Override
    public Mono rerank(final RerankDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.rerank);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.rerank,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    /**
     * 处理文本转语音请求
     *
     * @param request       TTS请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    @Override
    public Mono tts(final TtsDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.tts);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.tts,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, byte[].class));
    }

    /**
     * 处理语音转文本请求
     *
     * @param request       STT请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    @Override
    public Mono stt(final SttDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.stt);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.stt,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    /**
     * 处理图像生成请求
     *
     * @param request       图像生成请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    public Mono imageGenerate(final ImageGenerateDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgGen);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.imgGen,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    /**
     * 处理图像编辑请求
     *
     * @param request       图像编辑请求参数
     * @param authorization 授权信息
     * @param httpRequest   HTTP请求对象
     * @return 响应实体
     */
    @SuppressWarnings("all")
    public Mono imageEdit(final ImageEditDTO.Request request, final String authorization, final ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<String>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgEdit);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.imgEdit,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    // ========== 原有的抽象方法和工具方法 ==========

    /**
     * 获取适配器类型标识 - 子类必须实现
     */
    protected abstract String getAdapterType();

    /**
     * 获取选中的实例
     */
    protected ModelRouterProperties.ModelInstance selectInstance(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final String clientIp) {
        return instanceSelector.selectInstance(serviceType, modelName, clientIp);
    }

    /**
     * 获取模型路径
     */
    protected String getModelPath(final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        return instanceSelector.getModelPath(serviceType, modelName);
    }

    /**
     * 转换请求体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformRequest(final Object request, final String adapterType) {
        return responseTransformer.transformRequest(request, adapterType);
    }

    /**
     * 适配模型名称格式
     */
    protected String adaptModelName(final String originalModelName) {
        return responseTransformer.adaptModelName(originalModelName);
    }

    /**
     * 转换响应体 - 子类可以重写此方法来适配不同的API格式.
     * 注意：此方法现在处理的是已解析为Object的下游响应体，而不是原始的ResponseEntity。
     */
    protected Object transformResponse(final Object responseData, final String adapterType) {
        return responseTransformer.transformResponse(responseData, adapterType);
    }

    /**
     * 获取授权头 - 子类可以重写此方法来处理不同的认证方式
     */
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        return authorization;
    }

    /**
     * 缓存成功的响应结果
     */
    protected void cacheSuccessfulResponse(
            final ModelServiceRegistry.ServiceType serviceType,
            final String modelName,
            final ResponseEntity<?> response,
            final ServerHttpRequest httpRequest) {

        ModelRouterProperties.ServiceConfig serviceConfig = getRegistry().getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                getRegistry().getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);

        // 如果降级策略是缓存类型，则缓存响应
        if (fallbackStrategy instanceof CacheFallbackStrategy) {
            ((CacheFallbackStrategy) fallbackStrategy).cacheResponse(serviceType, modelName, httpRequest, response);
        }
    }

    /**
     * 计算请求大小（字节）
     */
    protected long calculateRequestSize(final Object request) {
        if (request == null) {
            return 0;
        }
        try {
            // 简单估算：将对象转换为字符串并计算字节长度
            String requestStr = request.toString();
            return requestStr.getBytes().length;
        } catch (Exception e) {
            // 如果无法计算，返回0
            return 0;
        }
    }

    /**
     * 从请求对象中提取模型名称
     */
    protected String getModelNameFromRequest(final Object request) {
        if (request == null) {
            return "unknown";
        }

        try {
            // 使用反射获取model字段
            java.lang.reflect.Method modelMethod = request.getClass().getMethod("model");
            Object modelName = modelMethod.invoke(request);
            return modelName != null ? modelName.toString() : "unknown";
        } catch (Exception e) {
            // 如果无法获取模型名称，返回unknown
            return "unknown";
        }
    }

    /**
     * 从请求对象中提取服务类型
     */
    protected String getServiceTypeFromRequest(final Object request) {
        if (request == null) {
            return "unknown";
        }

        // 根据请求类型判断服务类型
        if (request instanceof org.unreal.modelrouter.common.dto.ChatDTO.Request) {
            return ServiceTypeConstants.CHAT;
        } else if (request instanceof org.unreal.modelrouter.common.dto.EmbeddingDTO.Request) {
            return ServiceTypeConstants.EMBEDDING;
        } else if (request instanceof org.unreal.modelrouter.common.dto.RerankDTO.Request) {
            return ServiceTypeConstants.RERANK;
        } else if (request instanceof org.unreal.modelrouter.common.dto.TtsDTO.Request) {
            return ServiceTypeConstants.TTS;
        } else if (request instanceof org.unreal.modelrouter.common.dto.SttDTO.Request) {
            return ServiceTypeConstants.STT;
        } else if (request instanceof org.unreal.modelrouter.common.dto.ImageGenerateDTO.Request) {
            return ServiceTypeConstants.IMG_GEN;
        } else if (request instanceof org.unreal.modelrouter.common.dto.ImageEditDTO.Request) {
            return ServiceTypeConstants.IMG_EDIT;
        } else {
            return "unknown";
        }
    }

    /**
     * 记录适配器重试事件
     *
     * @param adapterType 适配器类型
     * @param instance    服务实例
     * @param retryCount  当前重试次数
     * @param maxRetries  最大重试次数
     * @param error       错误信息
     */
    protected void logAdapterRetryEvent(final String adapterType,
                                        final ModelRouterProperties.ModelInstance instance,
                                        final int retryCount,
                                        final int maxRetries,
                                        final Throwable error) {
        org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                enhancer.logAdapterRetry(adapterType, instance, retryCount, maxRetries, error, tracingContext);
            } catch (Exception ex) {
                // 忽略追踪错误
            }
        }

        // 记录重试指标
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, instance != null ? instance.getName() : "unknown", 0, false);
        }
    }

    /**
     * 记录适配器请求转换错误事件
     *
     * @param adapterType 适配器类型
     * @param error       错误信息
     */
    protected void logAdapterTransformError(final String adapterType, final Throwable error) {
        org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                                org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                enhancer.logAdapterRetry(adapterType, null, 0, 0, error, tracingContext);
            } catch (Exception ex) {
                // 忽略追踪错误
            }
        }

        // 记录转换错误指标
        if (metricsCollector != null) {
            metricsCollector.recordBackendCall(adapterType, "unknown", 0, false);
        }
    }

    /**
     * 获取最大重试次数
     */
    protected int getMaxRetries(final ModelServiceRegistry.ServiceType serviceType) {
        // 根据服务类型返回不同的重试次数
        switch (serviceType) {
            case chat:
                return 2; // 聊天服务重试2次
            case embedding:
                return 2; // 嵌入服务重试2次（降低）
            case rerank:
                return 1; // 重排序服务重试1次（降低，避免body重读问题）
            case tts:
                return 1; // TTS服务重试1次（文件较大）
            case stt:
                return 1; // STT服务重试1次（文件较大）
            case imgGen:
                return 1; // 图像生成重试1次（耗时较长）
            case imgEdit:
                return 1; // 图像编辑重试1次（耗时较长）
            default:
                return 1; // 默认重试1次（降低）
        }
    }
    /**
     * 计算重试延迟（指数退避）
     */
    protected long calculateRetryDelay(final int retryCount) {
        // 指数退避：基础延迟 * 2^重试次数，最大不超过10秒
        long baseDelay = 1000; // 1秒基础延迟
        long delay = baseDelay * (1L << retryCount); // 2^retryCount
        return Math.min(delay, 10000); // 最大10秒
    }

    /**
     * v2.3.2: 使用新监控和追踪组件的简化请求处理方法
     * 委托给 AdapterMetricsRecorder 和 AdapterTracingManager
     */
    @SuppressWarnings("all")
    protected <T> Mono processRequestWithMetricsAndTracing(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final RequestProcessor<T> processor,
            final long startTime,
            final int retryCount) {

        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        String modelName = getModelNameFromRequest(request);
        int maxRetries = getMaxRetries(serviceType);

        return processor.process(request, authorization, client, path, selectedInstance, serviceType)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    boolean success = response != null && response.getStatusCode().is2xxSuccessful();

                    // v2.3.2: 使用新组件记录
                    if (metricsRecorder != null) {
                        metricsRecorder.recordRequestComplete(
                                adapterType, instanceName, duration, success, null, modelName, serviceType);
                    }

                    // v2.3.2: 使用新组件结束追踪
                    if (tracingManager != null && success) {
                        tracingManager.endAdapterCall(null, true, null);
                    }
                })
                .onErrorResume(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String errorCode = classifyError(throwable);

                    // v2.3.2: 使用新组件记录错误
                    if (metricsRecorder != null) {
                        // v2.9.14: 使用 RetryPolicy 替代 shouldRetry
                        if (retryPolicy.canRetry(retryCount, throwable) && retryPolicy.isRetryable(throwable)) {
                            metricsRecorder.recordRetry(adapterType, instanceName, retryCount + 1, throwable);
                        } else {
                            metricsRecorder.recordError(
                                    adapterType, instanceName, errorCode, throwable, duration, serviceType);
                        }
                    }

                    // v2.3.2: 使用新组件结束追踪（失败）
                    if (tracingManager != null) {
                        tracingManager.endAdapterCall(null, false, throwable);
                    }

                    // v2.9.14: 使用 RetryPolicy 替代 shouldRetry 的重试逻辑
                    if (retryPolicy.canRetry(retryCount, throwable) && retryPolicy.isRetryable(throwable)) {
                        long retryDelay = calculateRetryDelay(retryCount);
                        return Mono.delay(java.time.Duration.ofMillis(retryDelay))
                                .then(processRequestWithMetricsAndTracing(request, authorization, client, path,
                                        selectedInstance, serviceType, processor,
                                        System.currentTimeMillis(), retryCount + 1));
                    }

                    // 最终失败处理 - 直接返回错误
                    if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException webEx =
                                (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                        ResponseStatusException responseEx = new ResponseStatusException(webEx.getStatusCode(), webEx.getMessage(), webEx);
                        logger.error("WebClientResponseException: {}", responseEx.getMessage(), responseEx);
                        return Mono.error(responseEx);
                    } else if (throwable instanceof DownstreamServiceException) {
                        DownstreamServiceException downStreamEx = (DownstreamServiceException) throwable;
                        ResponseStatusException responseEx = new ResponseStatusException(
                                downStreamEx.getStatusCode(), downStreamEx.getMessage(), downStreamEx);
                        logger.error("DownstreamServiceException: {}", responseEx.getMessage(), responseEx);
                        return Mono.error(responseEx);
                    } else {
                        logger.error("Unexpected error: {}", throwable.getMessage(), throwable);
                        return Mono.error(throwable);
                    }
                });
    }

    /**
     * 函数式接口用于请求处理
     */
    @FunctionalInterface
    protected interface RequestProcessor<T> {
        Mono<? extends ResponseEntity<?>> process(
                T request,
                String authorization,
                WebClient client,
                String path,
                ModelRouterProperties.ModelInstance selectedInstance,
                ModelServiceRegistry.ServiceType serviceType
        );
    }
}
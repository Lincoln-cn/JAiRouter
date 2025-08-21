package org.unreal.modelrouter.adapter;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.fallback.FallbackStrategy;
import org.unreal.modelrouter.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseAdapter implements ServiceCapability {

    private final ModelServiceRegistry registry;
    private final MetricsCollector metricsCollector;

    public BaseAdapter(final ModelServiceRegistry registry, final MetricsCollector metricsCollector) {
        this.registry = registry;
        this.metricsCollector = metricsCollector;
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
            org.unreal.modelrouter.tracing.client.TracingWebClientFactory tracingFactory =
                    org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                            org.unreal.modelrouter.tracing.client.TracingWebClientFactory.class);
            return tracingFactory.createTracingWebClient(baseUrl);
        } catch (Exception e) {
            return getRegistry().getClient(serviceType, modelName, clientIp);
        }
    }

    /**
     * 检查适配器是否支持指定的服务能力
     */
    protected Mono<ResponseEntity<String>> checkCapability(final ModelServiceRegistry.ServiceType serviceType) {
        if (!supportCapability().contains(serviceType)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body("This adapter does not support " + serviceType.name() + " capability."));
        }
        return null;
    }

    /**
     * 通用请求处理模板方法
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
        org.unreal.modelrouter.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
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

        return processRequestWithRetry(request, authorization, client, path, selectedInstance,
                serviceType, processor, tracingContext, startTime, 0);
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
            final RequestProcessor<T> processor,
            final org.unreal.modelrouter.tracing.TracingContext tracingContext,
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
                        // 缓存成功响应 - TODO: 实现缓存逻辑
                        // cacheSuccessfulResponse(serviceType, modelName, response, httpRequest);
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
                            org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                                    org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                            org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
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

                    // 检查是否应该重试
                    if (shouldRetry(throwable, retryCount, maxRetries)) {
                        // 记录重试追踪
                        if (tracingContext != null && tracingContext.isActive()) {
                            try {
                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                                        org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
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
                                        selectedInstance, serviceType, processor, tracingContext,
                                        System.currentTimeMillis(), retryCount + 1));
                    } else {
                        // 记录适配器调用失败追踪
                        if (tracingContext != null && tracingContext.isActive()) {
                            try {
                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                                        org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
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

                        return Mono.error(throwable);
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

        // 如果未启用降级，则使用普通的处理方法
        if (fallbackStrategy == null) {
            return processRequest(request, authorization, httpRequest, serviceType, modelName, processor);
        }

        return processRequest(request, authorization, httpRequest, serviceType, modelName, processor)
                .onErrorResume(throwable -> {
                    try {
                        ResponseEntity<?> fallbackResponse = fallbackStrategy.fallback((Exception) throwable);
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

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), getAdapterType()));

        // 设置Content-Type（如果需要）
        requestSpec = configureRequestHeaders(requestSpec, request);

        if (responseType == byte[].class) {
            return requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        // 5xx错误视为服务失败，用于熔断器
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    })
                    .toEntity(byte[].class)
                    .doOnSuccess(responseEntity -> {
                        // 记录请求大小指标（如果可能）
                        if (metricsCollector != null && responseEntity != null) {
                            long requestSize = calculateRequestSize(transformedRequest);
                            long responseSize = responseEntity.getBody() != null ?
                                    ((byte[]) responseEntity.getBody()).length : 0;
                            metricsCollector.recordRequestSize(serviceType.name(), requestSize, responseSize);

                            // 记录响应时间指标
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            String status = responseEntity.getStatusCode().toString();
                            metricsCollector.recordRequest(serviceType.name(), "POST", responseTime , status);
                        }
                    })
                    .map(responseEntity -> {
                        if (responseEntity.getStatusCode().is2xxSuccessful()) {
                            // 只返回body部分
                            return ResponseEntity.ok(responseEntity.getBody());
                        } else {
                            return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
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
        } else {
            return requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        // 5xx错误视为服务失败，用于熔断器
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    })
                    .toEntity(String.class)
                    .doOnSuccess(responseEntity -> {
                        // 记录请求大小指标
                        if (metricsCollector != null && responseEntity != null) {
                            long requestSize = calculateRequestSize(transformedRequest);
                            long responseSize = responseEntity.getBody() != null ?
                                    responseEntity.getBody().getBytes().length : 0;
                            metricsCollector.recordRequestSize(serviceType.name(), requestSize, responseSize);

                            // 记录响应时间指标
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            String status = responseEntity.getStatusCode().toString();
                            metricsCollector.recordRequest(serviceType.name(), "POST", responseTime, status);
                        }
                    })
                    .map(responseEntity -> {
                        Object transformedResponse = transformResponse(responseEntity, getAdapterType());
                        if (responseEntity.getStatusCode().is2xxSuccessful()) {
                            // 只返回body部分
                            if (transformedResponse instanceof ResponseEntity) {
                                return ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(((ResponseEntity<?>) transformedResponse).getBody());
                            } else {
                                return ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(transformedResponse);
                            }
                        } else {
                            if (transformedResponse instanceof ResponseEntity) {
                                return ResponseEntity.status(responseEntity.getStatusCode())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(((ResponseEntity<?>) transformedResponse).getBody());
                            } else {
                                return ResponseEntity.status(responseEntity.getStatusCode())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(transformedResponse);
                            }
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

        Object transformedRequest = transformRequest(request, getAdapterType());
        String adapterType = getAdapterType();
        String instanceName = selectedInstance.getName();
        String modelName = getModelNameFromRequest(request);

        // 记录请求开始指标
        long requestStartTime = System.currentTimeMillis();

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), getAdapterType()));

        requestSpec = configureRequestHeaders(requestSpec, request);

        // 记录请求大小
        long requestSize = calculateRequestSize(transformedRequest);

        Flux<String> streamResponse = requestSpec
                .bodyValue(transformedRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    // 5xx错误视为服务失败，用于熔断器
                    return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                })
                .bodyToFlux(String.class)
                .map(this::transformStreamChunk)
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
                .onErrorResume(throwable -> Flux.just(throwable.getMessage()));

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
     * 转换流式响应块 - 子类可以重写
     */
    protected String transformStreamChunk(final String chunk) {
        return adaptModelName(chunk);
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
        return getRegistry().selectInstance(serviceType, modelName, clientIp);
    }

    /**
     * 获取模型路径
     */
    protected String getModelPath(final ModelServiceRegistry.ServiceType serviceType, final String modelName) {
        return getRegistry().getModelPath(serviceType, modelName);
    }

    /**
     * 转换请求体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformRequest(final Object request, final String adapterType) {
        return request;
    }

    /**
     * 适配模型名称格式
     */
    protected String adaptModelName(final String originalModelName) {
        return originalModelName;
    }

    /**
     * 转换响应体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformResponse(final Object response, final String adapterType) {
        return response;
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
        if (request instanceof org.unreal.modelrouter.dto.ChatDTO.Request) {
            return "chat";
        } else if (request instanceof org.unreal.modelrouter.dto.EmbeddingDTO.Request) {
            return "embedding";
        } else if (request instanceof org.unreal.modelrouter.dto.RerankDTO.Request) {
            return "rerank";
        } else if (request instanceof org.unreal.modelrouter.dto.TtsDTO.Request) {
            return "tts";
        } else if (request instanceof org.unreal.modelrouter.dto.SttDTO.Request) {
            return "stt";
        } else if (request instanceof org.unreal.modelrouter.dto.ImageGenerateDTO.Request) {
            return "imgGen";
        } else if (request instanceof org.unreal.modelrouter.dto.ImageEditDTO.Request) {
            return "imgEdit";
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
    protected void logAdapterRetryEvent(String adapterType,
                                        ModelRouterProperties.ModelInstance instance,
                                        int retryCount,
                                        int maxRetries,
                                        Throwable error) {
        org.unreal.modelrouter.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
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
    protected void logAdapterTransformError(String adapterType, Throwable error) {
        org.unreal.modelrouter.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
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
                return 3; // 嵌入服务重试3次
            case rerank:
                return 2; // 重排序服务重试2次
            case tts:
                return 1; // TTS服务重试1次（文件较大）
            case stt:
                return 1; // STT服务重试1次（文件较大）
            case imgGen:
                return 1; // 图像生成重试1次（耗时较长）
            case imgEdit:
                return 1; // 图像编辑重试1次（耗时较长）
            default:
                return 2; // 默认重试2次
        }
    }

    /**
     * 判断是否应该重试
     */
    protected boolean shouldRetry(final Throwable throwable, final int currentRetryCount, final int maxRetries) {
        // 如果已达到最大重试次数，不再重试
        if (currentRetryCount >= maxRetries) {
            return false;
        }

        // 检查异常类型，只对特定类型的异常进行重试
        if (throwable instanceof org.springframework.web.server.ResponseStatusException) {
            org.springframework.web.server.ResponseStatusException statusException =
                    (org.springframework.web.server.ResponseStatusException) throwable;

            // 5xx服务器错误可以重试
            if (statusException.getStatusCode().is5xxServerError()) {
                return true;
            }

            // 429 Too Many Requests可以重试
            if (statusException.getStatusCode().value() == 429) {
                return true;
            }

            // 408 Request Timeout可以重试
            if (statusException.getStatusCode().value() == 408) {
                return true;
            }

            // 4xx客户端错误通常不重试
            return false;
        }

        // 网络相关异常可以重试
        if (throwable instanceof java.net.ConnectException ||
                throwable instanceof java.net.SocketTimeoutException ||
                throwable instanceof java.io.IOException) {
            return true;
        }

        // 其他异常不重试
        return false;
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
     * 函数式接口用于请求处理
     */
    @FunctionalInterface
    protected interface RequestProcessor<T> {
        Mono<? extends ResponseEntity<?>> process(
                final T request,
                final String authorization,
                final WebClient client,
                final String path,
                final ModelRouterProperties.ModelInstance selectedInstance,
                final ModelServiceRegistry.ServiceType serviceType
        );
    }
}

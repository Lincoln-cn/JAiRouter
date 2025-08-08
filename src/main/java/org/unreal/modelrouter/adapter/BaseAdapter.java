package org.unreal.modelrouter.adapter;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.fallback.CacheFallbackStrategy;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import org.unreal.modelrouter.fallback.FallbackStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseAdapter implements ServiceCapability {

    protected final ModelServiceRegistry registry;

    public BaseAdapter(ModelServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 检查适配器是否支持指定的服务能力
     */
    protected Mono<ResponseEntity<ErrorResponse>> checkCapability(ModelServiceRegistry.ServiceType serviceType) {
        if (!supportCapability().contains(serviceType)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(ErrorResponse.builder()
                            .code("not_implemented")
                            .type(serviceType.name())
                            .message("This adapter does not support " + serviceType.name() + " capability.")
                            .build()));
        }
        return null;
    }

    /**
     * 通用请求处理模板方法
     */
    protected <T> Mono processRequest(
            T request,
            String authorization,
            ServerHttpRequest httpRequest,
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            RequestProcessor<T> processor) {

        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, httpRequest);
        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(serviceType, modelName, clientIp);
        String path = getModelPath(serviceType, modelName);

        return processor.process(request, authorization, client, path, selectedInstance, serviceType)
                // 在成功完成时记录成功
                .doOnSuccess(response -> {
                    if (response != null && response.getStatusCode().is2xxSuccessful()) {
                        registry.recordCallComplete(serviceType, selectedInstance);
                        // 缓存成功响应
                        cacheSuccessfulResponse(serviceType, modelName, response, httpRequest);
                    } else {
                        // 非2xx响应视为失败
                        registry.recordCallFailure(serviceType, selectedInstance);
                    }
                })
                // 在发生错误时记录失败
                .onErrorResume(throwable -> {
                    registry.recordCallFailure(serviceType, selectedInstance);
                    return Mono.error(throwable);
                });
    }

    /**
     * 通用请求处理模板方法（带降级处理）
     */
    protected <T> Mono<? extends ResponseEntity<?>> processRequestWithFallback(
            T request,
            String authorization,
            ServerHttpRequest httpRequest,
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            RequestProcessor<T> processor) {

        ModelRouterProperties.ServiceConfig serviceConfig = registry.getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                registry.getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);

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
    protected <T> Mono processNonStreamingRequest(
            T request,
            String authorization,
            WebClient client,
            String path,
            ModelRouterProperties.ModelInstance selectedInstance,
            ModelServiceRegistry.ServiceType serviceType,
            Class<?> responseType) {

        Object transformedRequest = transformRequest(request, getAdapterType());

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
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(responseEntity));
        } else {
            return requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        // 5xx错误视为服务失败，用于熔断器
                        return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                    })
                    .toEntity(String.class)
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity, getAdapterType())));
        }
    }

    /**
     * 通用流式请求处理
     */
    protected <T> Mono<? extends ResponseEntity<?>> processStreamingRequest(
            T request,
            String authorization,
            WebClient client,
            String path,
            ModelRouterProperties.ModelInstance selectedInstance,
            ModelServiceRegistry.ServiceType serviceType) {

        Object transformedRequest = transformRequest(request, getAdapterType());

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), getAdapterType()));

        requestSpec = configureRequestHeaders(requestSpec, request);

        Flux<String> streamResponse = requestSpec
                .bodyValue(transformedRequest)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    // 5xx错误视为服务失败，用于熔断器
                    return Mono.error(new ResponseStatusException(clientResponse.statusCode()));
                })
                .bodyToFlux(String.class)
                .map(this::transformStreamChunk)
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = createErrorResponse("error", serviceType.name(), throwable.getMessage());
                    return Flux.just(errorResponse.toJson());
                });

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(streamResponse));
    }

    /**
     * 配置请求头 - 子类可以重写
     */
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(WebClient.RequestBodySpec requestSpec, T request) {
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
    protected String transformStreamChunk(String chunk) {
        return adaptModelName(chunk);
    }

    /**
     * 统一错误处理
     */
    protected ErrorResponse createErrorResponse(String code, String type, String message) {
        return ErrorResponse.builder()
                .code(code)
                .type(type)
                .message(message)
                .build();
    }

    public abstract AdapterCapabilities supportCapability();

    @Override
    public Mono<? extends ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.chat);
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

    @Override
    public Mono<? extends ResponseEntity<?>> embedding(EmbeddingDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.embedding);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.embedding,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.rerank);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.rerank,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.tts);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.tts,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, byte[].class));
    }

    @Override
    public Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.stt);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.stt,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    public Mono<? extends ResponseEntity<?>> imageGenerate(ImageGenerateDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgGen);
        if (capabilityCheck != null) {
            return capabilityCheck;
        }
        return processRequestWithFallback(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.imgGen,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    public Mono<? extends ResponseEntity<?>> imageEdit(ImageEditDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        Mono<ResponseEntity<ErrorResponse>> capabilityCheck = checkCapability(ModelServiceRegistry.ServiceType.imgEdit);
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
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
        return registry.selectInstance(serviceType, modelName, clientIp);
    }

    /**
     * 获取模型路径
     */
    protected String getModelPath(ModelServiceRegistry.ServiceType serviceType, String modelName) {
        return registry.getModelPath(serviceType, modelName);
    }

    /**
     * 转换请求体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformRequest(Object request, String adapterType) {
        return request;
    }

    /**
     * 适配模型名称格式
     */
    protected String adaptModelName(String originalModelName) {
        return originalModelName;
    }

    /**
     * 转换响应体 - 子类可以重写此方法来适配不同的API格式
     */
    protected Object transformResponse(Object response, String adapterType) {
        return response;
    }

    /**
     * 获取授权头 - 子类可以重写此方法来处理不同的认证方式
     */
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        return authorization;
    }

    /**
     * 缓存成功的响应结果
     */
    protected void cacheSuccessfulResponse(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ResponseEntity<?> response,
            ServerHttpRequest httpRequest) {

        ModelRouterProperties.ServiceConfig serviceConfig = registry.getServiceConfig(serviceType);
        FallbackStrategy<ResponseEntity<?>> fallbackStrategy =
                registry.getFallbackManager().getFallbackStrategy(serviceType.name(), serviceConfig);

        // 如果降级策略是缓存类型，则缓存响应
        if (fallbackStrategy instanceof CacheFallbackStrategy) {
            ((CacheFallbackStrategy) fallbackStrategy).cacheResponse(serviceType, modelName, httpRequest, response);
        }
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

package org.unreal.modelrouter.adapter;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseAdapter implements ServiceCapability {

    protected final ModelServiceRegistry registry;

    public BaseAdapter(ModelServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * 通用请求处理模板方法
     */
    protected <T> Mono<? extends ResponseEntity<?>> processRequest(
            T request,
            String authorization,
            ServerHttpRequest httpRequest,
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            RequestProcessor<T> processor) {

        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, httpRequest);
        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(serviceType, modelName, adaptModelName(clientIp));
        String path = getModelPath(serviceType, modelName);

        return processor.process(request, authorization, client, path, selectedInstance, serviceType);
    }

    /**
     * 通用非流式请求处理
     */
    protected <T> Mono<? extends ResponseEntity<?>> processNonStreamingRequest(
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
                    .toEntity(byte[].class)
                    .doFinally(signalType -> recordCallComplete(serviceType, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(responseEntity.getBody()))
                    .onErrorResume(throwable -> (Mono<? extends ResponseEntity<byte[]>>) handleError(throwable, serviceType, byte[].class));
        } else {
            return requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> recordCallComplete(serviceType, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity.getBody(), getAdapterType())))
                    .onErrorResume(throwable -> (Mono<? extends ResponseEntity<Object>>) handleError(throwable, serviceType, String.class));
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
                .bodyToFlux(String.class)
                .map(this::transformStreamChunk)
                .doFinally(signalType -> recordCallComplete(serviceType, selectedInstance))
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
        if (!(request instanceof SttDTO.Request)) { // STT需要multipart
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
    protected <T> Mono<? extends ResponseEntity<? extends Object>> handleError(Throwable throwable,
                                                                     ModelServiceRegistry.ServiceType serviceType,
                                                                     Class<T> responseType) {
        ErrorResponse errorResponse = createErrorResponse("error", serviceType.name(),
                getAdapterType() + " adapter error: " + throwable.getMessage());

        if (responseType == byte[].class) {
            return Mono.just(ResponseEntity.<T>internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse.toJson().getBytes()));
        } else {
            return Mono.just(ResponseEntity.<T>internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorResponse.toJson()));
        }
    }

    /**
     * 创建错误响应
     */
    protected ErrorResponse createErrorResponse(String code, String type, String message) {
        return ErrorResponse.builder()
                .code(code)
                .type(type)
                .message(message)
                .build();
    }

    // ========== 实现ServiceCapability的默认方法 ==========

    @Override
    public Mono<? extends ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.chat,
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
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.embedding,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.rerank,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.tts,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, byte[].class));
    }

    @Override
    public Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.stt,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    public Mono<? extends ResponseEntity<?>> imageGenerate(ImageGenerateDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.imgGen,
                request.model(), (req, auth, client, path, instance, serviceType) ->
                        processNonStreamingRequest(req, auth, client, path, instance, serviceType, String.class));
    }

    public Mono<? extends ResponseEntity<?>> imageEdit(ImageEditDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        return processRequest(request, authorization, httpRequest, ModelServiceRegistry.ServiceType.imgEdit,
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
     * 记录调用完成
     */
    protected void recordCallComplete(
            ModelServiceRegistry.ServiceType serviceType,
            ModelRouterProperties.ModelInstance instance) {
        registry.recordCallComplete(serviceType, instance);
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
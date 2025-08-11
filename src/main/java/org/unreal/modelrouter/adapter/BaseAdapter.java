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
import org.unreal.modelrouter.dto.ChatDTO;
import org.unreal.modelrouter.dto.EmbeddingDTO;
import org.unreal.modelrouter.dto.ImageEditDTO;
import org.unreal.modelrouter.dto.ImageGenerateDTO;
import org.unreal.modelrouter.dto.RerankDTO;
import org.unreal.modelrouter.dto.SttDTO;
import org.unreal.modelrouter.dto.TtsDTO;
import org.unreal.modelrouter.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.util.IpUtils;
import org.unreal.modelrouter.fallback.FallbackStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseAdapter implements ServiceCapability {

    private final ModelServiceRegistry registry;

    public BaseAdapter(final ModelServiceRegistry registry) {
        this.registry = registry;
    }

    public ModelServiceRegistry getRegistry() {
        return registry;
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

        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(serviceType, modelName, httpRequest);
        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = getRegistry().getClient(serviceType, modelName, clientIp);
        String path = getModelPath(serviceType, modelName);

        return processor.process(request, authorization, client, path, selectedInstance, serviceType)
                // 在成功完成时记录成功
                .doOnSuccess(response -> {
                    if (response != null && response.getStatusCode().is2xxSuccessful()) {
                        getRegistry().recordCallComplete(serviceType, selectedInstance);
                        // 缓存成功响应
                        cacheSuccessfulResponse(serviceType, modelName, response, httpRequest);
                    } else {
                        // 非2xx响应视为失败
                        getRegistry().recordCallFailure(serviceType, selectedInstance);
                    }
                })
                // 在发生错误时记录失败
                .onErrorResume(throwable -> {
                    getRegistry().recordCallFailure(serviceType, selectedInstance);
                    return Mono.error(throwable);
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
                    .map(responseEntity -> {
                        if (responseEntity.getStatusCode().is2xxSuccessful()) {
                            // 只返回body部分
                            return ResponseEntity.ok(responseEntity.getBody());
                        } else {
                            return ResponseEntity.status(responseEntity.getStatusCode()).body(responseEntity.getBody());
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
     * @param request 聊天请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
     * @param request 嵌入请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
     * @param request 重排序请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
     * @param request TTS请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
     * @param request STT请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
     * @param request 图像生成请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
     * @param request 图像编辑请求参数
     * @param authorization 授权信息
     * @param httpRequest HTTP请求对象
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
            final ServerHttpRequest httpRequest) {
        String clientIp = IpUtils.getClientIp(httpRequest);
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

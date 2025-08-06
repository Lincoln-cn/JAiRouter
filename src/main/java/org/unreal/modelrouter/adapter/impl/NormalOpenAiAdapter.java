package org.unreal.modelrouter.adapter.impl;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.response.ErrorResponse;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class NormalOpenAiAdapter extends BaseAdapter {

    public NormalOpenAiAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    public Object transformRequest(Object request, String adapterType){
        if(request instanceof SttDTO.Request sttRequest){
            return transformSttRequest(sttRequest);
        }else{
            return super.transformRequest(request, adapterType);
        }
    }

    // 确保 transformRequest 返回的是 MultiValueMap 用于 multipart 请求
    private Object transformSttRequest(SttDTO.Request sttRequest) {
        // 直接构建 MultiValueMap 而不处理文件内容
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", sttRequest.model());
        builder.part("language", sttRequest.language());

        // 使用 asyncPart 处理文件内容流
        builder.asyncPart("file", sttRequest.file().content(), DataBuffer.class)
                .filename(sttRequest.file().filename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return builder.build();
    }

    @Override
    public Mono<? extends ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        // 使用负载均衡选择实例
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.chat,
                request.model(),
                httpRequest
        );

        // 获取WebClient和路径
        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.chat, request.model(), adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.chat, request.model());

        // 转换请求（Normal adapter通常不需要转换）
        Object transformedRequest = transformRequest(request, "normal");

        if (request.stream()) {
            // 流式响应：直接转发流数据
            Flux<String> streamResponse = client.post()
                    .uri(adaptModelName(path))
                    .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), "normal"))
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message(throwable.getMessage())
                                .build();
                        return Flux.just(errorResponse.toJson());
                    });

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamResponse));
        } else {
            // 非流式响应：等待完整响应并转发
            return client.post()
                    .uri(adaptModelName(path))
                    .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), "normal"))
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity.getBody(), "normal")))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message(throwable.getMessage())
                                .build();
                        return Mono.just(ResponseEntity.internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(errorResponse.toJson()));
                    });
        }
    }

    @Override
    public Mono<? extends ResponseEntity<?>> embedding(EmbeddingDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.embedding,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        Object transformedRequest = transformRequest(request, "normal");

        return client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), "normal"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "normal")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("embedding")
                            .message(throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.rerank,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.rerank, request.model(), adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.rerank, request.model());

        Object transformedRequest = transformRequest(request, "normal");

        return client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), "normal"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.rerank, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "normal")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("rerank")
                            .message(throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.tts,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.tts, request.model(), adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.tts, request.model());

        Object transformedRequest = transformRequest(request, "normal");

        return client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), "normal"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(byte[].class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.tts, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(responseEntity.getBody()))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("tts")
                            .message(throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson().getBytes()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.stt,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.stt, request.model(), adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.stt, request.model());

        Object transformedRequest = transformRequest(request, "normal");

        return client.post()
                .uri(adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(adaptModelName(authorization), "normal"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.stt, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "normal")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("stt")
                            .message(throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }
}
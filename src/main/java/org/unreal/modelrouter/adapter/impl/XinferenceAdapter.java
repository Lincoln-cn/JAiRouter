package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/**
 * Xinference Adapter - 适配Xinference API格式
 * Xinference是一个支持多种模型的推理平台
 */
public class XinferenceAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public XinferenceAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequest(chatRequest);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return transformEmbeddingRequest(embeddingRequest);
        } else if (request instanceof RerankDTO.Request rerankRequest) {
            return transformRerankRequest(rerankRequest);
        } else if (request instanceof TtsDTO.Request ttsRequest) {
            return transformTtsRequest(ttsRequest);
        } else if (request instanceof SttDTO.Request sttRequest) {
            return transformSttRequest(sttRequest);
        }
        return request;
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

        // 添加其他字段
        if (sttRequest.prompt() != null) {
            builder.part("prompt", sttRequest.prompt());
        }
        if (sttRequest.responseFormat() != null) {
            builder.part("response_format", sttRequest.responseFormat());
        }
        if (sttRequest.temperature() != null) {
            builder.part("temperature", sttRequest.temperature());
        }

        return builder.build();
    }

    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(ttsRequest.model());
            xinferenceRequest.put("model", super.adaptModelName(gpuStackModelName));

            // TTS特定参数转换
            xinferenceRequest.put("input", ttsRequest.input());
            xinferenceRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                xinferenceRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                xinferenceRequest.put("speed", ttsRequest.speed());
            }

            return xinferenceRequest;
        } catch (Exception e) {
            // 如果转换失败，返回原请求
            return ttsRequest;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(rerankRequest.model());
            xinferenceRequest.put("model", super.adaptModelName(gpuStackModelName));

            // Rerank特定参数转换
            xinferenceRequest.put("query", rerankRequest.query());
            xinferenceRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                xinferenceRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                xinferenceRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            return xinferenceRequest;
        } catch (Exception e) {
            // 如果转换失败，返回原请求
            return rerankRequest;
        }
    }

    /**
     * 转换Chat请求为Xinference格式
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            // Xinference模型标识符处理
            xinferenceRequest.put("model", request.model());

            // 消息格式转换
            xinferenceRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // 基础参数
            if (request.temperature() != null) {
                xinferenceRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                xinferenceRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                xinferenceRequest.put("top_p", request.topP());
            }
            if (request.stream() != null) {
                xinferenceRequest.put("stream", request.stream());
            }

            // Xinference特定参数
            if (request.frequencyPenalty() != null) {
                xinferenceRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.presencePenalty() != null) {
                xinferenceRequest.put("presence_penalty", request.presencePenalty());
            }

            // Xinference可能支持的额外参数
            xinferenceRequest.put("echo", false); // 是否回显prompt
            xinferenceRequest.put("logprobs", 0); // 日志概率

            // 如果有stop参数
            if (request.stop() != null) {
                if (request.stop() instanceof String) {
                    ArrayNode stopArray = objectMapper.createArrayNode();
                    stopArray.add((String) request.stop());
                    xinferenceRequest.set("stop", stopArray);
                } else if (request.stop() instanceof java.util.List) {
                    xinferenceRequest.set("stop", objectMapper.valueToTree(request.stop()));
                }
            }

            return xinferenceRequest;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform chat request for Xinference", e);
        }
    }

    /**
     * 转换Embedding请求为Xinference格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();
            
            // Xinference模型标识符处理
            xinferenceRequest.put("model", request.model());
            
            // 输入文本处理
            if (request.input() instanceof String) {
                xinferenceRequest.put("input", (String) request.input());
            } else if (request.input() instanceof java.util.List) {
                xinferenceRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // 编码格式
            if (request.encodingFormat() != null) {
                xinferenceRequest.put("encoding_format", request.encodingFormat());
            }
            
            // 用户标识
            if (request.user() != null) {
                xinferenceRequest.put("user", request.user());
            }
            
            return xinferenceRequest;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform embedding request for Xinference", e);
        }
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.chat, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.chat, request.model());

        // 转换请求
        Object transformedRequest = transformRequest(request, "xinference");

        if (request.stream()) {
            // 流式响应：直接转发流数据
            Flux<String> streamResponse = client.post()
                    .uri(super.adaptModelName(path))
                    .header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "xinference"))
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
                    .uri(super.adaptModelName(path))
                    .header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "xinference"))
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity.getBody(), "xinference")))
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        Object transformedRequest = transformRequest(request, "xinference");

        return client.post()
                .uri(super.adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "xinference"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "xinference")))
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.rerank, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.rerank, request.model());

        Object transformedRequest = transformRequest(request, "xinference");

        return client.post()
                .uri(super.adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "xinference"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.rerank, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "xinference")))
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.tts, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.tts, request.model());

        Object transformedRequest = transformRequest(request, "xinference");

        return client.post()
                .uri(super.adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "xinference"))
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.stt, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.stt, request.model());

        Object transformedRequest = transformRequest(request, "xinference");

        return client.post()
                .uri(super.adaptModelName(path))
                .header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "xinference"))
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.stt, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "xinference")))
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

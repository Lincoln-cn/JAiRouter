package org.unreal.modelrouter.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/**
 * LocalAI Adapter - 适配LocalAI API格式
 * LocalAI是OpenAI API的开源替代方案
 */
public class LocalAiAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalAiAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequest(chatRequest);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return transformEmbeddingRequest(embeddingRequest);
        } else if (request instanceof TtsDTO.Request ttsRequest) {
            return transformTtsRequest(ttsRequest);
        }
        return request;
    }

    /**
     * 转换Chat请求为LocalAI格式
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            // LocalAI模型名称处理
            localAiRequest.put("model", request.model());

            // 消息格式 - LocalAI与OpenAI兼容
            localAiRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // 标准参数
            if (request.temperature() != null) {
                localAiRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                localAiRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                localAiRequest.put("top_p", request.topP());
            }
            if (request.stream() != null) {
                localAiRequest.put("stream", request.stream());
            }

            // LocalAI特定参数
            if (request.frequencyPenalty() != null) {
                localAiRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.presencePenalty() != null) {
                localAiRequest.put("presence_penalty", request.presencePenalty());
            }

            // LocalAI支持的额外参数
            if (request.topK() != null) {
                localAiRequest.put("top_k", request.topK());
            }

            // LocalAI特有的配置
            localAiRequest.put("echo", false);
            localAiRequest.put("ignore_eos", false);

            // 如果有stop参数
            if (request.stop() != null) {
                localAiRequest.set("stop", objectMapper.valueToTree(request.stop()));
            }

            return localAiRequest;
        } catch (Exception e) {
            return request;
        }
    }

    /**
     * 转换Embedding请求为LocalAI格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());

            // LocalAI embedding输入
            if (request.input() instanceof String) {
                localAiRequest.put("input", (String) request.input());
            } else {
                localAiRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // LocalAI特定参数
            localAiRequest.put("encoding_format", "float");
            if (request.user() != null) {
                localAiRequest.put("user", request.user());
            }

            return localAiRequest;
        } catch (Exception e) {
            return request;
        }
    }

    /**
     * 转换TTS请求为LocalAI格式
     */
    private Object transformTtsRequest(TtsDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());
            localAiRequest.put("input", request.input());
            localAiRequest.put("voice", request.voice());

            // LocalAI TTS特定参数
            if (request.responseFormat() != null) {
                localAiRequest.put("response_format", request.responseFormat());
            } else {
                localAiRequest.put("response_format", "mp3"); // 默认格式
            }

            if (request.speed() != null) {
                localAiRequest.put("speed", request.speed());
            }

            return localAiRequest;
        } catch (Exception e) {
            return request;
        }
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return enhanceLocalAiResponse(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 增强LocalAI响应
     */
    private String enhanceLocalAiResponse(JsonNode localAiResponse) {
        try {
            if (localAiResponse.isObject()) {
                ObjectNode enhancedResponse = (ObjectNode) localAiResponse.deepCopy();

                // 确保标准字段
                if (!enhancedResponse.has("id")) {
                    enhancedResponse.put("id", "localai-" + System.currentTimeMillis());
                }

                if (!enhancedResponse.has("created")) {
                    enhancedResponse.put("created", System.currentTimeMillis() / 1000);
                }

                // 添加系统指纹
                enhancedResponse.put("system_fingerprint", "localai-adapter");

                return enhancedResponse.toString();
            }
            return localAiResponse.toString();
        } catch (Exception e) {
            return localAiResponse.toString();
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // LocalAI通常不需要严格的认证，但支持API key
        return authorization;
    }

    @Override
    public Mono<? extends ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.chat,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.chat, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.chat, request.model());

        Object transformedRequest = transformRequest(request, "localai");

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Content-Type", "application/json");

        if (authorization != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(authorization, "localai"));
        }

        if (request.stream()) {
            Flux<String> streamResponse = requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(this::processLocalAiStreamChunk)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("LocalAI adapter error: " + throwable.getMessage())
                                .build();
                        return Flux.just("data: " + errorResponse.toJson() + "\n\n");
                    });

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamResponse));
        } else {
            return requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity.getBody(), "localai")))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("LocalAI adapter error: " + throwable.getMessage())
                                .build();
                        return Mono.just(ResponseEntity.internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(errorResponse.toJson()));
                    });
        }
    }

    /**
     * 处理LocalAI流式响应块
     */
    private String processLocalAiStreamChunk(String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6).trim();
                if ("[DONE]".equals(jsonPart)) {
                    return chunk;
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                if (chunkJson.isObject()) {
                    ObjectNode enhancedChunk = (ObjectNode) chunkJson.deepCopy();

                    // 添加系统指纹
                    if (!enhancedChunk.has("system_fingerprint")) {
                        enhancedChunk.put("system_fingerprint", "localai-adapter");
                    }

                    return "data: " + enhancedChunk.toString() + "\n\n";
                }
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        Object transformedRequest = transformRequest(request, "localai");

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Content-Type", "application/json");

        if (authorization != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(authorization, "localai"));
        }

        return requestSpec
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "localai")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("embedding")
                            .message("LocalAI adapter error: " + throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("LocalAI adapter does not support rerank service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.tts,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.tts, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.tts, request.model());

        Object transformedRequest = transformRequest(request, "localai");

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Content-Type", "application/json");

        if (authorization != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(authorization, "localai"));
        }

        return requestSpec
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
                            .message("LocalAI adapter error: " + throwable.getMessage())
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.stt, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.stt, request.model());

        Object transformedRequest = transformRequest(request, "localai");

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Content-Type", "application/json");

        if (authorization != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(authorization, "localai"));
        }

        return requestSpec
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.stt, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "localai")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("stt")
                            .message("LocalAI adapter error: " + throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }
}

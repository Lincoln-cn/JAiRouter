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
 * VLLM Adapter - 适配VLLM API格式
 * VLLM (Very Large Language Model) 推理服务器适配器
 */
public class VllmAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VllmAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequest(chatRequest);
        }
        return request;
    }

    /**
     * 转换Chat请求为VLLM格式
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            // VLLM模型名称处理
            vllmRequest.put("model", request.model());

            // VLLM支持OpenAI兼容的消息格式
            vllmRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // VLLM特定参数
            if (request.temperature() != null) {
                vllmRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                vllmRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                vllmRequest.put("top_p", request.topP());
            }
            if (request.stream() != null) {
                vllmRequest.put("stream", request.stream());
            }

            // VLLM高级参数
            if (request.frequencyPenalty() != null) {
                vllmRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.presencePenalty() != null) {
                vllmRequest.put("presence_penalty", request.presencePenalty());
            }

            // VLLM特有的推理参数
            vllmRequest.put("use_beam_search", false); // 默认不使用beam search
            vllmRequest.put("best_of", 1); // 生成候选数量
            vllmRequest.put("ignore_eos", false); // 是否忽略结束标记

            // 采样参数
            if (request.topK() != null) {
                vllmRequest.put("top_k", request.topK());
            } else {
                vllmRequest.put("top_k", -1); // VLLM默认值
            }

            return vllmRequest;
        } catch (Exception e) {
            return request;
        }
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        // VLLM通常与OpenAI API兼容，可能不需要太多转换
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return enhanceVllmResponse(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 增强VLLM响应，添加一些标准化字段
     */
    private String enhanceVllmResponse(JsonNode vllmResponse) {
        try {
            if (vllmResponse.isObject()) {
                ObjectNode enhancedResponse = (ObjectNode) vllmResponse.deepCopy();

                // 确保有ID字段
                if (!enhancedResponse.has("id")) {
                    enhancedResponse.put("id", "chatcmpl-vllm-" + System.currentTimeMillis());
                }

                // 确保有created字段
                if (!enhancedResponse.has("created")) {
                    enhancedResponse.put("created", System.currentTimeMillis() / 1000);
                }

                // 添加系统标识
                enhancedResponse.put("system_fingerprint", "vllm-adapter");

                return enhancedResponse.toString();
            }
            return vllmResponse.toString();
        } catch (Exception e) {
            return vllmResponse.toString();
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // VLLM支持标准的Bearer token认证
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

        Object transformedRequest = transformRequest(request, "vllm");

        // 构建请求头
        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Content-Type", "application/json");

        if (authorization != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(authorization, "vllm"));
        }

        if (request.stream()) {
            Flux<String> streamResponse = requestSpec
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(this::processVllmStreamChunk)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("VLLM adapter error: " + throwable.getMessage())
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
                            .body(transformResponse(responseEntity.getBody(), "vllm")))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("VLLM adapter error: " + throwable.getMessage())
                                .build();
                        return Mono.just(ResponseEntity.internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(errorResponse.toJson()));
                    });
        }
    }

    /**
     * 处理VLLM流式响应块
     */
    private String processVllmStreamChunk(String chunk) {
        try {
            // VLLM通常返回标准的SSE格式，可能需要一些清理
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6).trim();
                if ("[DONE]".equals(jsonPart)) {
                    return chunk;
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                if (chunkJson.isObject()) {
                    ObjectNode enhancedChunk = (ObjectNode) chunkJson.deepCopy();

                    // 确保有system_fingerprint
                    if (!enhancedChunk.has("system_fingerprint")) {
                        enhancedChunk.put("system_fingerprint", "vllm-adapter");
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
        // VLLM主要用于文本生成，embedding支持可能有限
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.embedding,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .header("Content-Type", "application/json");

        if (authorization != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(authorization, "vllm"));
        }

        return requestSpec
                .bodyValue(request) // VLLM embedding可能与OpenAI兼容
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "vllm")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("embedding")
                            .message("VLLM adapter error: " + throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("VLLM adapter does not support rerank service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("VLLM adapter does not support TTS service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("VLLM adapter does not support STT service");
    }
}
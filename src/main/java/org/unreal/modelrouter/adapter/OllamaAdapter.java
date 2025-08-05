package org.unreal.modelrouter.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * Ollama Adapter - 适配Ollama API格式
 * Ollama使用不同的API端点和请求格式
 */
public class OllamaAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequest(chatRequest);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return transformEmbeddingRequest(embeddingRequest);
        }
        return request;
    }

    /**
     * 转换Chat请求为Ollama格式
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            // Ollama使用不同的模型名称格式
            ollamaRequest.put("model", request.model());

            // 转换消息格式 - Ollama与OpenAI格式基本兼容
            ollamaRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // Ollama特定的选项
            ObjectNode options = objectMapper.createObjectNode();
            if (request.temperature() != null) {
                options.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                options.put("num_predict", request.maxTokens()); // Ollama使用num_predict而不是max_tokens
            }
            if (request.topP() != null) {
                options.put("top_p", request.topP());
            }

            if (options.size() > 0) {
                ollamaRequest.set("options", options);
            }

            // Ollama的流式设置
            if (request.stream() != null) {
                ollamaRequest.put("stream", request.stream());
            }

            // Ollama特定参数
            ollamaRequest.put("format", "json"); // 可选：强制JSON输出

            return ollamaRequest;
        } catch (Exception e) {
            return request;
        }
    }

    /**
     * 转换Embedding请求为Ollama格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            ollamaRequest.put("model", request.model());

            // Ollama的embedding接口使用prompt而不是input
            if (request.input() instanceof String) {
                ollamaRequest.put("prompt", (String) request.input());
            } else if (request.input() instanceof String[]) {
                // 如果是数组，取第一个元素
                String[] inputs = (String[]) request.input();
                if (inputs.length > 0) {
                    ollamaRequest.put("prompt", inputs[0]);
                }
            }

            return ollamaRequest;
        } catch (Exception e) {
            return request;
        }
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return transformResponseJson(jsonResponse);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
    }

    /**
     * 转换Ollama响应为OpenAI标准格式
     */
    private String transformResponseJson(JsonNode ollamaResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            if (ollamaResponse.has("message")) {
                // 聊天响应转换
                ObjectNode choice = objectMapper.createObjectNode();
                choice.set("message", ollamaResponse.get("message"));
                choice.put("index", 0);
                choice.put("finish_reason", ollamaResponse.get("done").asBoolean() ? "stop" : "length");

                ArrayNode choices = objectMapper.createArrayNode();
                choices.add(choice);
                standardResponse.set("choices", choices);
                standardResponse.put("object", "chat.completion");

                // 使用统计信息
                if (ollamaResponse.has("prompt_eval_count") && ollamaResponse.has("eval_count")) {
                    ObjectNode usage = objectMapper.createObjectNode();
                    usage.put("prompt_tokens", ollamaResponse.get("prompt_eval_count").asInt());
                    usage.put("completion_tokens", ollamaResponse.get("eval_count").asInt());
                    usage.put("total_tokens",
                            ollamaResponse.get("prompt_eval_count").asInt() +
                                    ollamaResponse.get("eval_count").asInt());
                    standardResponse.set("usage", usage);
                }

            } else if (ollamaResponse.has("embedding")) {
                // 嵌入响应转换
                ObjectNode dataItem = objectMapper.createObjectNode();
                dataItem.put("object", "embedding");
                dataItem.set("embedding", ollamaResponse.get("embedding"));
                dataItem.put("index", 0);

                ArrayNode data = objectMapper.createArrayNode();
                data.add(dataItem);

                standardResponse.set("data", data);
                standardResponse.put("object", "list");
                standardResponse.put("model", ollamaResponse.get("model").asText("unknown"));

                // 使用统计信息
                ObjectNode usage = objectMapper.createObjectNode();
                usage.put("prompt_tokens", ollamaResponse.has("prompt_eval_count") ?
                        ollamaResponse.get("prompt_eval_count").asInt() : 0);
                usage.put("total_tokens", ollamaResponse.has("prompt_eval_count") ?
                        ollamaResponse.get("prompt_eval_count").asInt() : 0);
                standardResponse.set("usage", usage);

            } else {
                return ollamaResponse.toString();
            }

            // 添加通用字段
            standardResponse.put("id", "ollama-" + System.currentTimeMillis());
            standardResponse.put("created", System.currentTimeMillis() / 1000);
            standardResponse.put("model", ollamaResponse.get("model").asText("unknown"));

            return standardResponse.toString();
        } catch (Exception e) {
            return ollamaResponse.toString();
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // Ollama通常不需要认证，但保留授权头以防万一
        return authorization;
    }

    /**
     * 获取Ollama特定的API路径
     */
    private String getOllamaPath(ModelServiceRegistry.ServiceType serviceType) {
        return switch (serviceType) {
            case chat -> "/api/chat";
            case embedding -> "/api/embeddings";
            default -> "/api/generate"; // 默认生成接口
        };
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
        String path = getOllamaPath(ModelServiceRegistry.ServiceType.chat); // 使用Ollama特定路径

        Object transformedRequest = transformRequest(request, "ollama");

        if (request.stream()) {
            Flux<String> streamResponse = client.post()
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(this::transformOllamaStreamChunk)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("Ollama adapter error: " + throwable.getMessage())
                                .build();
                        return Flux.just("data: " + errorResponse.toJson() + "\n\n");
                    });

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamResponse));
        } else {
            return client.post()
                    .uri(path)
                    .header("Content-Type", "application/json")
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity.getBody(), "ollama")))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("Ollama adapter error: " + throwable.getMessage())
                                .build();
                        return Mono.just(ResponseEntity.internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(errorResponse.toJson()));
                    });
        }
    }

    /**
     * 转换Ollama流式响应块为OpenAI格式
     */
    private String transformOllamaStreamChunk(String chunk) {
        try {
            JsonNode chunkJson = objectMapper.readTree(chunk);

            ObjectNode standardChunk = objectMapper.createObjectNode();
            standardChunk.put("id", "chatcmpl-" + System.currentTimeMillis());
            standardChunk.put("object", "chat.completion.chunk");
            standardChunk.put("created", System.currentTimeMillis() / 1000);
            standardChunk.put("model", chunkJson.get("model").asText("unknown"));

            ObjectNode choice = objectMapper.createObjectNode();
            choice.put("index", 0);

            // Ollama流式响应格式
            if (chunkJson.has("message") && chunkJson.get("message").has("content")) {
                ObjectNode delta = objectMapper.createObjectNode();
                delta.put("content", chunkJson.get("message").get("content").asText());
                choice.set("delta", delta);

                if (chunkJson.get("done").asBoolean()) {
                    choice.put("finish_reason", "stop");
                } else {
                    choice.putNull("finish_reason");
                }
            }

            ArrayNode choices = objectMapper.createArrayNode();
            choices.add(choice);
            standardChunk.set("choices", choices);

            return "data: " + standardChunk.toString() + "\n\n";
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
        String path = getOllamaPath(ModelServiceRegistry.ServiceType.embedding);

        Object transformedRequest = transformRequest(request, "ollama");

        return client.post()
                .uri(path)
                .header("Content-Type", "application/json")
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "ollama")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("embedding")
                            .message("Ollama adapter error: " + throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("Ollama adapter does not support rerank service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("Ollama adapter does not support TTS service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        throw new UnsupportedOperationException("Ollama adapter does not support STT service");
    }
}
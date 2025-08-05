package org.unreal.modelrouter.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
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
 * GPUStack Adapter - 适配GPUStack API格式
 */
public class GpuStackAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GpuStackAdapter(ModelServiceRegistry registry) {
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

    private Object transformSttRequest(SttDTO.Request sttRequest) {
        return DataBufferUtils.join(sttRequest.file().content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(fileBytes -> {
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();
                    builder.part("model", sttRequest.model());
                    builder.part("language", sttRequest.language());
                    builder.part("file", fileBytes)
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

                    return Mono.just(builder.build());
                });
    }

    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(ttsRequest.model());
            gpuStackRequest.put("model", gpuStackModelName);

            // TTS特定参数转换
            gpuStackRequest.put("input", ttsRequest.input());
            gpuStackRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                gpuStackRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                gpuStackRequest.put("speed", ttsRequest.speed());
            }

            return gpuStackRequest;
        } catch (Exception e) {
            // 如果转换失败，返回原请求
            return ttsRequest;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(rerankRequest.model());
            gpuStackRequest.put("model", gpuStackModelName);

            // Rerank特定参数转换
            gpuStackRequest.put("query", rerankRequest.query());
            gpuStackRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                gpuStackRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                gpuStackRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            return gpuStackRequest;
        } catch (Exception e) {
            // 如果转换失败，返回原请求
            return rerankRequest;
        }
    }

    /**
     * 转换Chat请求格式以适配GPUStack
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(request.model());
            gpuStackRequest.put("model", gpuStackModelName);

            // 转换消息格式
            gpuStackRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // GPUStack特定参数
            if (request.temperature() != null) {
                gpuStackRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                gpuStackRequest.put("max_tokens", request.maxTokens());
            }
            if (request.stream() != null) {
                gpuStackRequest.put("stream", request.stream());
            }

            // GPUStack可能需要额外的参数
            gpuStackRequest.put("do_sample", true);
            if (!request.stream()) {
                gpuStackRequest.put("return_full_text", false);
            }

            return gpuStackRequest;
        } catch (Exception e) {
            // 如果转换失败，返回原请求
            return request;
        }
    }

    /**
     * 转换Embedding请求格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            String gpuStackModelName = adaptModelName(request.model());
            gpuStackRequest.put("model", gpuStackModelName);

            // GPUStack的embedding接口可能使用不同的参数名
            if (request.input() instanceof String) {
                gpuStackRequest.put("input", (String) request.input());
            } else {
                gpuStackRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // GPUStack特定参数
            gpuStackRequest.put("encoding_format", "float");

            return gpuStackRequest;
        } catch (Exception e) {
            return request;
        }
    }

    /**
     * 适配模型名称格式
     */
    private String adaptModelName(String originalModelName) {
        // GPUStack可能使用不同的模型名称格式
        // 例如: qwen3:1.7B -> qwen3-1.7b
        return originalModelName.toLowerCase().replace(":", "-");
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return transformResponseJson(jsonResponse);
            } catch (Exception e) {
                // 如果解析失败，返回原响应
                return response;
            }
        }
        return response;
    }

    /**
     * 转换响应格式以符合OpenAI标准
     */
    private String transformResponseJson(JsonNode gpuStackResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            // 转换GPUStack响应为OpenAI格式
            if (gpuStackResponse.has("generated_text")) {
                // 聊天响应转换
                ObjectNode choice = objectMapper.createObjectNode();
                ObjectNode message = objectMapper.createObjectNode();
                message.put("role", "assistant");
                message.put("content", gpuStackResponse.get("generated_text").asText());
                choice.set("message", message);
                choice.put("index", 0);
                choice.put("finish_reason", "stop");

                standardResponse.set("choices", objectMapper.createArrayNode().add(choice));
                standardResponse.put("object", "chat.completion");
            } else if (gpuStackResponse.has("embeddings")) {
                // 嵌入响应转换
                standardResponse.set("data", gpuStackResponse.get("embeddings"));
                standardResponse.put("object", "list");
                standardResponse.put("model", gpuStackResponse.get("model").asText());
            } else {
                // 如果格式不符合预期，返回原响应
                return gpuStackResponse.toString();
            }

            // 添加通用字段
            standardResponse.put("id", "chatcmpl-" + System.currentTimeMillis());
            standardResponse.put("created", System.currentTimeMillis() / 1000);

            return standardResponse.toString();
        } catch (Exception e) {
            return gpuStackResponse.toString();
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // GPUStack可能使用不同的认证方式
        if (authorization != null && authorization.startsWith("Bearer ")) {
            // 保持Bearer token格式
            return authorization;
        } else if (authorization != null) {
            // 如果不是Bearer格式，转换为GPUStack期望的格式
            return "Bearer " + authorization;
        }
        return null;
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

        Object transformedRequest = transformRequest(request, "gpustack");

        if (request.stream()) {
            Flux<String> streamResponse = client.post()
                    .uri(path)
                    .header("Authorization", getAuthorizationHeader(authorization, "gpustack"))
                    .header("Content-Type", "application/json")
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(this::transformStreamChunk)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("GPUStack adapter error: " + throwable.getMessage())
                                .build();
                        return Flux.just(errorResponse.toJson());
                    });

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamResponse));
        } else {
            return client.post()
                    .uri(path)
                    .header("Authorization", getAuthorizationHeader(authorization, "gpustack"))
                    .header("Content-Type", "application/json")
                    .bodyValue(transformedRequest)
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.chat, selectedInstance))
                    .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(responseEntity.getHeaders())
                            .body(transformResponse(responseEntity.getBody(), "gpustack")))
                    .onErrorResume(throwable -> {
                        ErrorResponse errorResponse = ErrorResponse.builder()
                                .code("error")
                                .type("chat")
                                .message("GPUStack adapter error: " + throwable.getMessage())
                                .build();
                        return Mono.just(ResponseEntity.internalServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(errorResponse.toJson()));
                    });
        }
    }

    /**
     * 转换流式响应块
     */
    private String transformStreamChunk(String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6);
                if ("[DONE]".equals(jsonPart)) {
                    return chunk;
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                // 根据GPUStack的流式响应格式进行转换
                ObjectNode standardChunk = objectMapper.createObjectNode();
                standardChunk.put("id", "chatcmpl-" + System.currentTimeMillis());
                standardChunk.put("object", "chat.completion.chunk");
                standardChunk.put("created", System.currentTimeMillis() / 1000);

                ObjectNode choice = objectMapper.createObjectNode();
                choice.put("index", 0);

                if (chunkJson.has("token")) {
                    ObjectNode delta = objectMapper.createObjectNode();
                    delta.put("content", chunkJson.get("token").asText());
                    choice.set("delta", delta);
                }

                standardChunk.set("choices", objectMapper.createArrayNode().add(choice));

                return "data: " + standardChunk.toString();
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }

    @Override
    public Mono<? extends ResponseEntity<?>> embedding(EmbeddingDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        // 实现类似于chat的逻辑，但针对embedding接口
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.embedding,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        Object transformedRequest = transformRequest(request, "gpustack");

        return client.post()
                .uri(path)
                .header("Authorization", getAuthorizationHeader(authorization, "gpustack"))
                .header("Content-Type", "application/json")
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.embedding, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "gpustack")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("embedding")
                            .message("GPUStack adapter error: " + throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }

    @Override
    public Mono<? extends ResponseEntity<?>> rerank(RerankDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        // GPUStack可能不支持rerank，使用基础实现
        throw new UnsupportedOperationException("GPUStack adapter does not support rerank service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> tts(TtsDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        // GPUStack可能不支持TTS，使用基础实现
        throw new UnsupportedOperationException("GPUStack adapter does not support TTS service");
    }

    @Override
    public Mono<? extends ResponseEntity<?>> stt(SttDTO.Request request, String authorization, ServerHttpRequest httpRequest) {

        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.stt,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), clientIp);
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        Object transformedRequest = transformRequest(request, "gpustack");

        return client.post()
                .uri(path)
                .header("Authorization", authorization)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(transformedRequest)
                .retrieve()
                .toEntity(String.class)
                .doFinally(signalType -> recordCallComplete(ModelServiceRegistry.ServiceType.stt, selectedInstance))
                .map(responseEntity -> ResponseEntity.status(responseEntity.getStatusCode())
                        .headers(responseEntity.getHeaders())
                        .body(transformResponse(responseEntity.getBody(), "gpustack")))
                .onErrorResume(throwable -> {
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("error")
                            .type("embedding")
                            .message("GPUStack adapter error: " + throwable.getMessage())
                            .build();
                    return Mono.just(ResponseEntity.internalServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorResponse.toJson()));
                });
    }
}
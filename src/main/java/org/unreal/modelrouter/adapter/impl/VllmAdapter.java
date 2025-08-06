package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(ttsRequest.model());
            vllmRequest.put("model", super.adaptModelName(gpuStackModelName));

            // TTS特定参数转换
            vllmRequest.put("input", ttsRequest.input());
            vllmRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                vllmRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                vllmRequest.put("speed", ttsRequest.speed());
            }

            return vllmRequest;
        } catch (Exception e) {
            // 如果转换失败，返回原请求
            return ttsRequest;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(rerankRequest.model());
            vllmRequest.put("model", super.adaptModelName(gpuStackModelName));

            // Rerank特定参数转换
            vllmRequest.put("query", rerankRequest.query());
            vllmRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                vllmRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                vllmRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            return vllmRequest;
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
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            // GPUStack可能需要不同的模型名称格式
            String gpuStackModelName = adaptModelName(request.model());
            vllmRequest.put("model", super.adaptModelName(gpuStackModelName));

            // 转换消息格式
            vllmRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // GPUStack特定参数
            if (request.temperature() != null) {
                vllmRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                vllmRequest.put("max_tokens", request.maxTokens());
            }
            if (request.stream() != null) {
                vllmRequest.put("stream", request.stream());
            }

            // GPUStack可能需要额外的参数
            vllmRequest.put("do_sample", true);
            if (!request.stream()) {
                vllmRequest.put("return_full_text", false);
            }

            return vllmRequest;
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
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            String gpuStackModelName = adaptModelName(request.model());
            vllmRequest.put("model", super.adaptModelName(gpuStackModelName));

            // GPUStack的embedding接口可能使用不同的参数名
            if (request.input() instanceof String) {
                vllmRequest.put("input", (String) request.input());
            } else {
                vllmRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // GPUStack特定参数
            vllmRequest.put("encoding_format", "float");

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
                JsonNode jsonResponse = objectMapper.readTree(super.adaptModelName(responseStr));
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
        return super.adaptModelName(authorization);
    }

    @Override
    public Mono<? extends ResponseEntity<?>> chat(ChatDTO.Request request, String authorization, ServerHttpRequest httpRequest) {
        ModelRouterProperties.ModelInstance selectedInstance = selectInstance(
                ModelServiceRegistry.ServiceType.chat,
                request.model(),
                httpRequest
        );

        String clientIp = IpUtils.getClientIp(httpRequest);
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.chat, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.chat, request.model());

        Object transformedRequest = transformRequest(request, "vllm");

        // 构建请求头
        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(super.adaptModelName(path))
                .header("Content-Type", "application/json");

        if (super.adaptModelName(authorization) != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "vllm"));
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
            if (super.adaptModelName(chunk).startsWith("data: ")) {
                String jsonPart = super.adaptModelName(chunk).substring(6).trim();
                if ("[DONE]".equals(super.adaptModelName(jsonPart))) {
                    return super.adaptModelName(chunk);
                }

                JsonNode chunkJson = objectMapper.readTree(super.adaptModelName(jsonPart));
                if (chunkJson.isObject()) {
                    ObjectNode enhancedChunk = (ObjectNode) chunkJson.deepCopy();

                    // 确保有system_fingerprint
                    if (!enhancedChunk.has("system_fingerprint")) {
                        enhancedChunk.put("system_fingerprint", "vllm-adapter");
                    }

                    return "data: " + enhancedChunk.toString() + "\n\n";
                }
            }
            return super.adaptModelName(chunk);
        } catch (Exception e) {
            return super.adaptModelName(chunk);
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
        WebClient client = registry.getClient(ModelServiceRegistry.ServiceType.embedding, request.model(), super.adaptModelName(clientIp));
        String path = getModelPath(ModelServiceRegistry.ServiceType.embedding, request.model());

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(super.adaptModelName(path))
                .header("Content-Type", "application/json");

        if (super.adaptModelName(authorization) != null) {
            requestSpec = requestSpec.header("Authorization", getAuthorizationHeader(super.adaptModelName(authorization), "vllm"));
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
}
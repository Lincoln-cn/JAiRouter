package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.unreal.modelrouter.adapter.AdapterCapabilities;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;

/**
 * GPUStack Adapter - 适配GPUStack API格式
 */
public class GpuStackAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GpuStackAdapter(ModelServiceRegistry registry, org.unreal.modelrouter.monitoring.MetricsCollector metricsCollector) {
        super(registry, metricsCollector);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.all();
    }

    @Override
    public String getAdapterType() {
        return "gpustack";
    }

    @Override
    public Object transformRequest(Object request, String adapterType) {
        if (request instanceof ChatDTO.Request) {
            return transformChatRequest((ChatDTO.Request) request);
        } else if (request instanceof EmbeddingDTO.Request) {
            return transformEmbeddingRequest((EmbeddingDTO.Request) request);
        } else if (request instanceof RerankDTO.Request) {
            return transformRerankRequest((RerankDTO.Request) request);
        } else if (request instanceof TtsDTO.Request) {
            return transformTtsRequest((TtsDTO.Request) request);
        } else if (request instanceof SttDTO.Request) {
            return transformSttRequest((SttDTO.Request) request);
        } else {
            return request;
        }
    }

    /**
     * 转换Chat请求格式以适配GPUStack
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            String gpuStackModelName = adaptModelName(request.model());
            gpuStackRequest.put("model", adaptModelName(gpuStackModelName));
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

            // GPUStack需要的额外参数
            gpuStackRequest.put("do_sample", true);
            if (!request.stream()) {
                gpuStackRequest.put("return_full_text", false);
            }

            return gpuStackRequest;
        } catch (Exception e) {
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
            gpuStackRequest.put("model", adaptModelName(gpuStackModelName));

            if (request.input() instanceof String) {
                gpuStackRequest.put("input", (String) request.input());
            } else {
                gpuStackRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            gpuStackRequest.put("encoding_format", "float");
            return gpuStackRequest;
        } catch (Exception e) {
            return request;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            String gpuStackModelName = adaptModelName(rerankRequest.model());
            gpuStackRequest.put("model", adaptModelName(gpuStackModelName));
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
            return rerankRequest;
        }
    }

    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode gpuStackRequest = objectMapper.createObjectNode();

            String gpuStackModelName = adaptModelName(ttsRequest.model());
            gpuStackRequest.put("model", adaptModelName(gpuStackModelName));
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
            return ttsRequest;
        }
    }

    private Object transformSttRequest(SttDTO.Request sttRequest) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", sttRequest.model());
        builder.part("language", sttRequest.language());

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

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(adaptModelName(responseStr));
                return transformResponseJson(jsonResponse);
            } catch (Exception e) {
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
        if (adaptModelName(authorization) != null && adaptModelName(authorization).startsWith("Bearer ")) {
            return adaptModelName(authorization);
        } else if (adaptModelName(authorization) != null) {
            return "Bearer " + adaptModelName(authorization);
        }
        return null;
    }

    @Override
    protected String transformStreamChunk(String chunk) {
        try {
            if (adaptModelName(chunk).startsWith("data: ")) {
                String jsonPart = adaptModelName(chunk).substring(6);
                if ("[DONE]".equals(adaptModelName(jsonPart))) {
                    return adaptModelName(chunk);
                }

                JsonNode chunkJson = objectMapper.readTree(adaptModelName(jsonPart));
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
                return "data: " + standardChunk;
            }
            return adaptModelName(chunk);
        } catch (Exception e) {
            return adaptModelName(chunk);
        }
    }
}
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
 * VLLM Adapter - 适配VLLM API格式
 * VLLM (Very Large Language Model) 推理服务器适配器
 */
public class VllmAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VllmAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .rerank(true)
                .build();
    }

    @Override
    protected String getAdapterType() {
        return "vllm";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
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
     * 转换Chat请求格式以适配VLLM
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            String vllmModelName = adaptModelName(request.model());
            vllmRequest.put("model", adaptModelName(vllmModelName));
            vllmRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // VLLM特定参数
            if (request.temperature() != null) {
                vllmRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                vllmRequest.put("max_tokens", request.maxTokens());
            }
            if (request.stream() != null) {
                vllmRequest.put("stream", request.stream());
            }

            // VLLM需要的额外参数
            vllmRequest.put("do_sample", true);
            if (!request.stream()) {
                vllmRequest.put("return_full_text", false);
            }

            return vllmRequest;
        } catch (Exception e) {
            return request;
        }
    }

    /**
     * 转换Embedding请求格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            String vllmModelName = adaptModelName(request.model());
            vllmRequest.put("model", adaptModelName(vllmModelName));

            if (request.input() instanceof String) {
                vllmRequest.put("input", (String) request.input());
            } else {
                vllmRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            vllmRequest.put("encoding_format", "float");
            return vllmRequest;
        } catch (Exception e) {
            return request;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            String vllmModelName = adaptModelName(rerankRequest.model());
            vllmRequest.put("model", adaptModelName(vllmModelName));
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
            return rerankRequest;
        }
    }

    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode vllmRequest = objectMapper.createObjectNode();

            String vllmModelName = adaptModelName(ttsRequest.model());
            vllmRequest.put("model", adaptModelName(vllmModelName));
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
    private String transformResponseJson(JsonNode vllmResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            // 转换VLLM响应为OpenAI格式
            if (vllmResponse.has("generated_text")) {
                // 聊天响应转换
                ObjectNode choice = objectMapper.createObjectNode();
                ObjectNode message = objectMapper.createObjectNode();
                message.put("role", "assistant");
                message.put("content", vllmResponse.get("generated_text").asText());
                choice.set("message", message);
                choice.put("index", 0);
                choice.put("finish_reason", "stop");

                standardResponse.set("choices", objectMapper.createArrayNode().add(choice));
                standardResponse.put("object", "chat.completion");
            } else if (vllmResponse.has("embeddings")) {
                // 嵌入响应转换
                standardResponse.set("data", vllmResponse.get("embeddings"));
                standardResponse.put("object", "list");
                standardResponse.put("model", vllmResponse.get("model").asText());
            } else {
                return vllmResponse.toString();
            }

            // 添加通用字段
            standardResponse.put("id", "chatcmpl-" + System.currentTimeMillis());
            standardResponse.put("created", System.currentTimeMillis() / 1000);

            return standardResponse.toString();
        } catch (Exception e) {
            return vllmResponse.toString();
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
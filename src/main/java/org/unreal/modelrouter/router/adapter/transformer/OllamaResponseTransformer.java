package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ollama响应转换器
 * 负责将Ollama API响应转换为标准格式
 */
public class OllamaResponseTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaResponseTransformer.class);

    private final ObjectMapper objectMapper;

    public OllamaResponseTransformer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 转换响应JSON为标准格式
     */
    public String transformResponseJson(final JsonNode ollamaResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            if (ollamaResponse.has("choices")) {
                transformChatResponse(ollamaResponse, standardResponse);
            } else if (ollamaResponse.has("embedding")) {
                // Ollama embedding 响应格式: {"embedding": [...]}
                transformOllamaEmbeddingResponse(ollamaResponse, standardResponse);
            } else if (ollamaResponse.has("data") && ollamaResponse.has("model")) {
                transformEmbeddingResponse(ollamaResponse, standardResponse);
            } else if (ollamaResponse.has("results")) {
                transformRerankResponse(ollamaResponse, standardResponse);
            } else {
                return ollamaResponse.toString();
            }

            return standardResponse.toString();
        } catch (Exception e) {
            LOGGER.warn("Failed to transform response from Ollama: {}", e.getMessage());
            return ollamaResponse.toString();
        }
    }

    /**
     * 转换流式响应块
     */
    public String transformStreamChunk(final String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6);
                if ("[DONE]".equals(jsonPart.trim())) {
                    return "[DONE]";
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                ObjectNode standardChunk = objectMapper.createObjectNode();

                standardChunk.put("id", "ollama-" + System.currentTimeMillis());
                standardChunk.put("object", "chat.completion.chunk");
                standardChunk.put("created", System.currentTimeMillis() / 1000);

                if (chunkJson.has("model")) {
                    standardChunk.put("model", chunkJson.get("model").asText());
                }

                if (chunkJson.has("choices")) {
                    standardChunk.set("choices", chunkJson.get("choices"));
                }

                return "data: " + standardChunk.toString();
            }

            return chunk;
        } catch (Exception e) {
            LOGGER.warn("Failed to transform stream chunk from Ollama: {}", e.getMessage());
            return chunk;
        }
    }

    // ==================== 私有方法 ====================

    private void transformChatResponse(final JsonNode ollamaResponse, final ObjectNode standardResponse) {
        standardResponse.set("id", ollamaResponse.path("id"));
        standardResponse.put("object", "chat.completion");
        standardResponse.put("created", System.currentTimeMillis() / 1000);

        if (ollamaResponse.has("model")) {
            standardResponse.put("model", ollamaResponse.get("model").asText());
        }

        standardResponse.set("choices", ollamaResponse.get("choices"));
        addUsageInfo(ollamaResponse, standardResponse);
    }

    private void transformEmbeddingResponse(final JsonNode ollamaResponse, final ObjectNode standardResponse) {
        standardResponse.put("object", "list");
        standardResponse.set("data", ollamaResponse.get("data"));
        standardResponse.put("model", ollamaResponse.get("model").asText());

        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("total_tokens", 0);
        standardResponse.set("usage", ollamaResponse.has("usage") ? ollamaResponse.get("usage") : usage);
    }

    /**
     * 转换Ollama embedding响应为标准OpenAI格式
     * Ollama格式: {"embedding": [0.1, 0.2, ...]}
     * OpenAI格式: {"data": [{"object": "embedding", "index": 0, "embedding": [...]}], "model": "xxx", "usage": {...}}
     */
    private void transformOllamaEmbeddingResponse(final JsonNode ollamaResponse, final ObjectNode standardResponse) {
        standardResponse.put("object", "list");

        // 构建 data 数组
        ObjectNode embeddingData = objectMapper.createObjectNode();
        embeddingData.put("object", "embedding");
        embeddingData.put("index", 0);
        embeddingData.set("embedding", ollamaResponse.get("embedding"));

        standardResponse.set("data", objectMapper.createArrayNode().add(embeddingData));

        // 添加 model 字段（如果有）
        if (ollamaResponse.has("model")) {
            standardResponse.put("model", ollamaResponse.get("model").asText());
        }

        // 添加 usage 字段
        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("total_tokens", 0);
        standardResponse.set("usage", usage);
    }

    private void transformRerankResponse(final JsonNode ollamaResponse, final ObjectNode standardResponse) {
        standardResponse.set("id", objectMapper.getNodeFactory().textNode("ollama-" + System.currentTimeMillis()));
        standardResponse.set("results", ollamaResponse.get("results"));

        if (ollamaResponse.has("model")) {
            standardResponse.put("model", ollamaResponse.get("model").asText());
        }

        addUsageInfo(ollamaResponse, standardResponse);
    }

    private void addUsageInfo(final JsonNode ollamaResponse, final ObjectNode standardResponse) {
        if (ollamaResponse.has("usage")) {
            standardResponse.set("usage", ollamaResponse.get("usage"));
        } else {
            ObjectNode usage = objectMapper.createObjectNode();
            usage.put("prompt_tokens", 0);
            usage.put("completion_tokens", 0);
            usage.put("total_tokens", 0);
            standardResponse.set("usage", usage);
        }
    }
}

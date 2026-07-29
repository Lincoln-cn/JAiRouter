package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI响应转换器实现
 * 将后端响应转换为标准格式
 *
 * @since v2.7.24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiResponseTransformerImpl implements OpenAiResponseTransformer {

    private final ObjectMapper objectMapper;

    @Override
    public String transformResponse(final Object response) {
        if (response instanceof String responseStr) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(responseStr);
                return transformStandardResponse(jsonResponse);
            } catch (Exception e) {
                log.debug("Failed to transform response: {}", e.getMessage());
                return responseStr;
            }
        }
        return response != null ? response.toString() : null;
    }

    @Override
    public String transformStreamChunk(final String chunk) {
        try {
            if (!chunk.startsWith("data: ")) {
                return chunk;
            }

            String jsonPart = chunk.substring(6).trim();
            if ("[DONE]".equals(jsonPart)) {
                return "[DONE]";
            }

            JsonNode chunkJson = objectMapper.readTree(jsonPart);
            ObjectNode standardChunk = objectMapper.createObjectNode();

            standardChunk.put("id", "chatcmpl-" + System.currentTimeMillis());
            standardChunk.put("object", "chat.completion.chunk");
            standardChunk.put("created", System.currentTimeMillis() / 1000);

            if (chunkJson.has("model")) {
                standardChunk.put("model", chunkJson.get("model").asText());
            }

            if (chunkJson.has("choices")) {
                standardChunk.set("choices", chunkJson.get("choices"));
            } else {
                standardChunk.set("choices", createStandardChoices(chunkJson));
            }

            if (chunkJson.has("usage")) {
                standardChunk.set("usage", chunkJson.get("usage"));
            }

            return standardChunk.toString();
        } catch (Exception e) {
            log.debug("Failed to transform stream chunk: {}", e.getMessage());
            return chunk;
        }
    }

    /**
     * 创建标准选择项
     */
    private JsonNode createStandardChoices(final JsonNode chunkJson) {
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode delta = createDeltaFromChunk(chunkJson);
        choice.set("delta", delta);

        if (chunkJson.has("finish_reason")) {
            choice.put("finish_reason", chunkJson.get("finish_reason").asText());
        }

        return objectMapper.createArrayNode().add(choice);
    }

    /**
     * 从块数据创建delta
     */
    private ObjectNode createDeltaFromChunk(final JsonNode chunkJson) {
        ObjectNode delta = objectMapper.createObjectNode();

        if (chunkJson.has("delta")) {
            return (ObjectNode) chunkJson.get("delta");
        } else if (chunkJson.has("content")) {
            delta.put("content", chunkJson.get("content").asText());
        } else if (chunkJson.has("text")) {
            delta.put("content", chunkJson.get("text").asText());
        }

        return delta;
    }

    @Override
    public String transformStandardResponse(final JsonNode openAiResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            if (openAiResponse.has("choices")) {
                return transformChatResponse(openAiResponse, standardResponse);
            } else if (openAiResponse.has("data") && openAiResponse.has("model")) {
                return transformEmbeddingResponse(openAiResponse, standardResponse);
            } else if (openAiResponse.has("results")) {
                return transformRerankResponse(openAiResponse, standardResponse);
            }

            return openAiResponse.toString();
        } catch (Exception e) {
            log.debug("Failed to transform standard response: {}", e.getMessage());
            return openAiResponse.toString();
        }
    }

    /**
     * 转换聊天响应
     */
    private String transformChatResponse(final JsonNode openAiResponse, final ObjectNode standardResponse) {
        standardResponse.set("id", openAiResponse.path("id"));
        standardResponse.put("object", "chat.completion");
        standardResponse.put("created", System.currentTimeMillis() / 1000);

        if (openAiResponse.has("model")) {
            standardResponse.put("model", openAiResponse.get("model").asText());
        }

        standardResponse.set("choices", openAiResponse.get("choices"));
        standardResponse.set("usage", createUsageIfNeeded(openAiResponse));

        return standardResponse.toString();
    }

    /**
     * 转换嵌入响应
     */
    private String transformEmbeddingResponse(final JsonNode openAiResponse, final ObjectNode standardResponse) {
        standardResponse.put("object", "list");
        standardResponse.set("data", openAiResponse.get("data"));
        standardResponse.put("model", openAiResponse.get("model").asText());
        standardResponse.set("usage", createUsageIfNeeded(openAiResponse));

        return standardResponse.toString();
    }

    /**
     * 转换重排序响应
     */
    private String transformRerankResponse(final JsonNode openAiResponse, final ObjectNode standardResponse) {
        standardResponse.set("id", objectMapper.getNodeFactory().textNode("openai-" + System.currentTimeMillis()));
        standardResponse.set("results", openAiResponse.get("results"));

        if (openAiResponse.has("model")) {
            standardResponse.put("model", openAiResponse.get("model").asText());
        }

        standardResponse.set("usage", createUsageIfNeeded(openAiResponse));

        return standardResponse.toString();
    }

    /**
     * 创建使用情况信息（如果不存在）
     */
    private JsonNode createUsageIfNeeded(final JsonNode openAiResponse) {
        if (openAiResponse.has("usage")) {
            return openAiResponse.get("usage");
        }

        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("completion_tokens", 0);
        usage.put("total_tokens", 0);
        return usage;
    }
}

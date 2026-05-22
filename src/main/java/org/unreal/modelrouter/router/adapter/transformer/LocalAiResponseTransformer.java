package org.unreal.modelrouter.router.adapter.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LocalAI响应转换器
 * 将LocalAI响应转换为标准格式
 */
@Slf4j
@RequiredArgsConstructor
public class LocalAiResponseTransformer {

    private final ObjectMapper objectMapper;

    /**
     * 转换响应
     */
    public Object transform(final Object response) {
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
     * 转换流式响应块
     */
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

            standardChunk.put("id", "localai-" + System.currentTimeMillis());
            standardChunk.put("object", "chat.completion.chunk");
            standardChunk.put("created", System.currentTimeMillis() / 1000);

            if (chunkJson.has("model")) {
                standardChunk.put("model", chunkJson.get("model").asText());
            }

            if (chunkJson.has("choices")) {
                standardChunk.set("choices", chunkJson.get("choices"));
            } else {
                standardChunk.set("choices", buildChoicesArray(chunkJson));
            }

            standardChunk.put("system_fingerprint", "localai-adapter");

            if (chunkJson.has("usage")) {
                standardChunk.set("usage", chunkJson.get("usage"));
            }

            return standardChunk.toString();
        } catch (Exception e) {
            return chunk;
        }
    }

    private String enhanceLocalAiResponse(final JsonNode localAiResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            if (localAiResponse.has("choices")) {
                buildChatResponse(localAiResponse, standardResponse);
            } else if (localAiResponse.has("data") && localAiResponse.has("model")) {
                buildEmbeddingResponse(localAiResponse, standardResponse);
            } else if (localAiResponse.has("results")) {
                buildRerankResponse(localAiResponse, standardResponse);
            } else {
                return localAiResponse.toString();
            }

            ensureStandardFields(standardResponse);
            return standardResponse.toString();
        } catch (Exception e) {
            return localAiResponse.toString();
        }
    }

    private void buildChatResponse(final JsonNode localAiResponse, final ObjectNode standardResponse) {
        standardResponse.set("id", localAiResponse.path("id"));
        standardResponse.put("object", "chat.completion");
        standardResponse.put("created", System.currentTimeMillis() / 1000);

        if (localAiResponse.has("model")) {
            standardResponse.put("model", localAiResponse.get("model").asText());
        }

        standardResponse.set("choices", localAiResponse.get("choices"));

        if (localAiResponse.has("usage")) {
            standardResponse.set("usage", localAiResponse.get("usage"));
        } else {
            standardResponse.set("usage", createEmptyUsage());
        }
    }

    private void buildEmbeddingResponse(final JsonNode localAiResponse, final ObjectNode standardResponse) {
        standardResponse.put("object", "list");
        standardResponse.set("data", localAiResponse.get("data"));
        standardResponse.put("model", localAiResponse.get("model").asText());

        if (localAiResponse.has("usage")) {
            standardResponse.set("usage", localAiResponse.get("usage"));
        } else {
            ObjectNode usage = objectMapper.createObjectNode();
            usage.put("prompt_tokens", 0);
            usage.put("total_tokens", 0);
            standardResponse.set("usage", usage);
        }
    }

    private void buildRerankResponse(final JsonNode localAiResponse, final ObjectNode standardResponse) {
        standardResponse.set("id", objectMapper.getNodeFactory().textNode("localai-" + System.currentTimeMillis()));
        standardResponse.set("results", localAiResponse.get("results"));

        if (localAiResponse.has("model")) {
            standardResponse.put("model", localAiResponse.get("model").asText());
        }

        if (localAiResponse.has("usage")) {
            standardResponse.set("usage", localAiResponse.get("usage"));
        } else {
            standardResponse.set("usage", createEmptyUsage());
        }
    }

    private void ensureStandardFields(final ObjectNode standardResponse) {
        if (!standardResponse.has("id")) {
            standardResponse.put("id", "localai-" + System.currentTimeMillis());
        }

        if (!standardResponse.has("created")) {
            standardResponse.put("created", System.currentTimeMillis() / 1000);
        }

        standardResponse.put("system_fingerprint", "localai-adapter");
    }

    private ObjectNode createEmptyUsage() {
        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", 0);
        usage.put("completion_tokens", 0);
        usage.put("total_tokens", 0);
        return usage;
    }

    private JsonNode buildChoicesArray(final JsonNode chunkJson) {
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode delta = buildDelta(chunkJson);
        choice.set("delta", delta);

        if (chunkJson.has("finish_reason")) {
            choice.put("finish_reason", chunkJson.get("finish_reason").asText());
        }

        return objectMapper.createArrayNode().add(choice);
    }

    private ObjectNode buildDelta(final JsonNode chunkJson) {
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
}

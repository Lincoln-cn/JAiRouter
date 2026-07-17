package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;

import java.util.Map;

/**
 * Google Gemini 适配器
 * 支持 Gemini API 格式，包括：
 * - x-goog-api-key 认证头（非 Authorization: Bearer）
 * - 请求/响应格式转换（Gemini 格式 ↔ OpenAI 格式）
 *
 * Gemini API 端点:
 * - 生成内容: POST /v1beta/models/{model}:generateContent
 * - 流式生成: POST /v1beta/models/{model}:streamGenerateContent
 *
 * @since v2.8.3
 */
@Slf4j
public class GeminiAdapter extends BaseAdapter {

    private final OpenAiRequestTransformer requestTransformer;
    private final OpenAiResponseTransformer responseTransformer;
    private final ObjectMapper objectMapper;

    public GeminiAdapter(final AdapterContext context,
                         final RequestProcessingSupport requestSupport,
                         final ResilienceSupport resilienceSupport,
                         final OpenAiRequestTransformer openAiRequestTransformer,
                         final OpenAiResponseTransformer openAiResponseTransformer) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = openAiRequestTransformer;
        this.responseTransformer = openAiResponseTransformer;
        this.objectMapper = context.getObjectMapper();
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .streaming(true)
                .build();
    }

    @Override
    protected String getAdapterType() {
        return "gemini";
    }

    @Override
    protected Map<String, String> getAdditionalHeaders() {
        // Gemini 不需要额外头部，API Key 通过 x-goog-api-key 头传递
        return Map.of();
    }

    @Override
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        // Gemini 不使用 Authorization: Bearer 头
        // API Key 通过实例自定义头 x-goog-api-key 传递
        return null;
    }

    @Override
    protected Object transformRequest(final Object request, final String adapterType) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequestToGemini(chatRequest);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return transformEmbeddingRequestToGemini(embeddingRequest);
        }
        return request;
    }

    /**
     * 将 OpenAI 格式的 Chat 请求转换为 Gemini 格式
     *
     * OpenAI 格式:
     * {
     *   "model": "gpt-4",
     *   "messages": [
     *     {"role": "system", "content": "You are helpful"},
     *     {"role": "user", "content": "hello"}
     *   ],
     *   "temperature": 0.7,
     *   "max_tokens": 1000
     * }
     *
     * Gemini 格式:
     * {
     *   "contents": [
     *     {"role": "user", "parts": [{"text": "hello"}]}
     *   ],
     *   "systemInstruction": {"parts": [{"text": "You are helpful"}]},
     *   "generationConfig": {
     *     "temperature": 0.7,
     *     "maxOutputTokens": 1000
     *   }
     * }
     */
    private Object transformChatRequestToGemini(ChatDTO.Request request) {
        try {
            ObjectNode geminiRequest = objectMapper.createObjectNode();

            // 转换 messages 为 contents
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode systemInstruction = null;

            if (request.messages() != null) {
                for (Object msgObj : request.messages()) {
                    JsonNode msgNode = objectMapper.valueToTree(msgObj);
                    String role = msgNode.has("role") ? msgNode.get("role").asText() : "";
                    String content = msgNode.has("content") ? msgNode.get("content").asText() : "";

                    if ("system".equalsIgnoreCase(role)) {
                        // Gemini 将 system 消息放在 systemInstruction 字段
                        systemInstruction = objectMapper.createObjectNode();
                        ArrayNode parts = objectMapper.createArrayNode();
                        parts.add(objectMapper.createObjectNode().put("text", content));
                        systemInstruction.set("parts", parts);
                    } else {
                        // 转换为 Gemini 格式
                        ObjectNode geminiContent = objectMapper.createObjectNode();
                        // Gemini 使用 "user" 和 "model" 角色（不是 "assistant"）
                        String geminiRole = "assistant".equalsIgnoreCase(role) ? "model" : role;
                        geminiContent.put("role", geminiRole);

                        ArrayNode parts = objectMapper.createArrayNode();
                        parts.add(objectMapper.createObjectNode().put("text", content));
                        geminiContent.set("parts", parts);

                        contents.add(geminiContent);
                    }
                }
            }

            geminiRequest.set("contents", contents);

            if (systemInstruction != null) {
                geminiRequest.set("systemInstruction", systemInstruction);
            }

            // 构建 generationConfig
            ObjectNode generationConfig = objectMapper.createObjectNode();

            if (request.temperature() != null) {
                generationConfig.put("temperature", request.temperature());
            }
            if (request.topP() != null) {
                generationConfig.put("topP", request.topP());
            }
            if (request.maxTokens() != null) {
                generationConfig.put("maxOutputTokens", request.maxTokens());
            }
            if (request.stop() != null) {
                // stop 可以是 String 或 List，需要统一转换为数组
                Object stopValue = request.stop();
                if (stopValue instanceof String stopStr) {
                    ArrayNode stopArray = objectMapper.createArrayNode();
                    stopArray.add(stopStr);
                    generationConfig.set("stopSequences", stopArray);
                } else {
                    generationConfig.set("stopSequences", objectMapper.valueToTree(stopValue));
                }
            }
            // Gemini 特有参数
            if (request.topK() != null) {
                generationConfig.put("topK", request.topK());
            }

            if (generationConfig.size() > 0) {
                geminiRequest.set("generationConfig", generationConfig);
            }

            return geminiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform request to Gemini format: {}", e.getMessage());
            return request;
        }
    }

    /**
     * 将 OpenAI 格式的 Embedding 请求转换为 Gemini 格式
     *
     * OpenAI 格式:
     * {
     *   "model": "text-embedding-ada-002",
     *   "input": "hello world"
     * }
     *
     * Gemini 格式:
     * {
     *   "requests": [
     *     {
     *       "model": "models/text-embedding-004",
     *       "content": {"parts": [{"text": "hello world"}]}
     *     }
     *   ]
     * }
     */
    private Object transformEmbeddingRequestToGemini(EmbeddingDTO.Request request) {
        try {
            ObjectNode geminiRequest = objectMapper.createObjectNode();

            // Gemini Embedding 使用不同的端点格式
            // 构建 requests 数组
            ArrayNode requests = objectMapper.createArrayNode();
            ObjectNode embeddingRequest = objectMapper.createObjectNode();

            embeddingRequest.put("model", "models/" + adaptModelName(request.model()));

            ObjectNode content = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();

            // input 可以是 String 或 List<String>
            Object input = request.input();
            if (input instanceof String text) {
                parts.add(objectMapper.createObjectNode().put("text", text));
            } else if (input instanceof java.util.List<?> list) {
                for (Object item : list) {
                    parts.add(objectMapper.createObjectNode().put("text", item.toString()));
                }
            }

            content.set("parts", parts);
            embeddingRequest.set("content", content);

            requests.add(embeddingRequest);
            geminiRequest.set("requests", requests);

            return geminiRequest;
        } catch (Exception e) {
            log.warn("Failed to transform embedding request to Gemini format: {}", e.getMessage());
            return request;
        }
    }

    @Override
    protected Object transformResponse(final Object response, final String adapterType) {
        if (response instanceof String responseStr) {
            return transformGeminiResponseToOpenAi(responseStr);
        }
        return response;
    }

    /**
     * 将 Gemini 格式的响应转换为 OpenAI 格式
     *
     * Gemini 格式:
     * {
     *   "candidates": [
     *     {
     *       "content": {
     *         "parts": [{"text": "Hello!"}],
     *         "role": "model"
     *       },
     *       "finishReason": "STOP",
     *       "safetyRatings": [...]
     *     }
     *   ],
     *   "usageMetadata": {
     *     "promptTokenCount": 10,
     *     "candidatesTokenCount": 20,
     *     "totalTokenCount": 30
     *   }
     * }
     *
     * OpenAI 格式:
     * {
     *   "id": "chatcmpl-xxx",
     *   "object": "chat.completion",
     *   "choices": [{"message": {"role": "assistant", "content": "Hello!"}, "finish_reason": "stop"}],
     *   "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
     * }
     */
    private String transformGeminiResponseToOpenAi(String responseStr) {
        try {
            JsonNode geminiResponse = objectMapper.readTree(responseStr);

            // 如果不是 Gemini 格式（没有 candidates），直接返回
            if (!geminiResponse.has("candidates")) {
                return responseStr;
            }

            ObjectNode openAiResponse = objectMapper.createObjectNode();

            // 基本信息
            openAiResponse.put("id", "chatcmpl-" + System.currentTimeMillis());
            openAiResponse.put("object", "chat.completion");
            openAiResponse.put("created", System.currentTimeMillis() / 1000);

            // 提取模型名称（Gemini 响应中可能没有 model 字段）
            openAiResponse.put("model", "gemini");

            // 转换 choices
            ArrayNode choices = objectMapper.createArrayNode();

            JsonNode candidates = geminiResponse.get("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                ObjectNode choice = objectMapper.createObjectNode();

                // 提取内容
                ObjectNode message = objectMapper.createObjectNode();
                message.put("role", "assistant");

                if (candidate.has("content") && candidate.get("content").has("parts")) {
                    JsonNode parts = candidate.get("content").get("parts");
                    if (parts.isArray()) {
                        StringBuilder contentBuilder = new StringBuilder();
                        for (JsonNode part : parts) {
                            if (part.has("text")) {
                                contentBuilder.append(part.get("text").asText());
                            }
                        }
                        message.put("content", contentBuilder.toString());
                    }
                }

                choice.set("message", message);

                // 转换 finishReason
                String finishReason = candidate.has("finishReason")
                        ? candidate.get("finishReason").asText()
                        : "STOP";
                choice.put("finish_reason", mapGeminiFinishReason(finishReason));

                choices.add(choice);
            }

            openAiResponse.set("choices", choices);

            // 转换 usage
            if (geminiResponse.has("usageMetadata")) {
                JsonNode usage = geminiResponse.get("usageMetadata");
                ObjectNode openAiUsage = objectMapper.createObjectNode();
                openAiUsage.put("prompt_tokens", usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : 0);
                openAiUsage.put("completion_tokens", usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : 0);
                openAiUsage.put("total_tokens", usage.has("totalTokenCount") ? usage.get("totalTokenCount").asInt() : 0);
                openAiResponse.set("usage", openAiUsage);
            }

            return objectMapper.writeValueAsString(openAiResponse);
        } catch (Exception e) {
            log.debug("Failed to transform Gemini response: {}", e.getMessage());
            return responseStr;
        }
    }

    /**
     * 映射 Gemini 的 finishReason 到 OpenAI 的 finish_reason
     */
    private String mapGeminiFinishReason(String geminiFinishReason) {
        return switch (geminiFinishReason) {
            case "STOP" -> "stop";
            case "MAX_TOKENS" -> "length";
            case "SAFETY" -> "content_filter";
            case "RECITATION" -> "content_filter";
            default -> "stop";
        };
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        // Gemini 流式响应格式与 OpenAI 不同，需要转换
        // 暂时返回原始格式，后续可以增强
        return chunk;
    }
}

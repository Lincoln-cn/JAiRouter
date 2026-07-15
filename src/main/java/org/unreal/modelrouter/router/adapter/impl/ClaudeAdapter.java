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
 * Anthropic Claude 适配器
 * 支持 Claude API 格式，包括：
 * - x-api-key 认证头（非 Authorization: Bearer）
 * - anthropic-version 头自动注入
 * - 请求/响应格式转换（Claude 格式 ↔ OpenAI 格式）
 *
 * @since v2.8.0
 */
@Slf4j
public class ClaudeAdapter extends BaseAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final OpenAiRequestTransformer requestTransformer;
    private final OpenAiResponseTransformer responseTransformer;
    private final ObjectMapper objectMapper;

    public ClaudeAdapter(final AdapterContext context,
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
                .streaming(true)
                .build();
    }

    @Override
    protected String getAdapterType() {
        return "claude";
    }

    @Override
    protected Map<String, String> getAdditionalHeaders() {
        return Map.of("anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        // Claude 不使用 Authorization: Bearer 头
        // API Key 通过实例自定义头 x-api-key 传递
        // 此处返回 null 以阻止 NonStreamingRequestProcessor 添加 Authorization 头
        return null;
    }

    @Override
    protected Object transformRequest(final Object request, final String adapterType) {
        if (request instanceof ChatDTO.Request chatRequest) {
            return transformChatRequestToClaude(chatRequest);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            // Claude 目前不支持 Embedding API，但保留接口
            return requestTransformer.transformEmbeddingRequest(embeddingRequest, this::adaptModelName);
        }
        return request;
    }

    /**
     * 将 OpenAI 格式的 Chat 请求转换为 Claude 格式
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
     * Claude 格式:
     * {
     *   "model": "claude-3-sonnet-20240229",
     *   "max_tokens": 1000,
     *   "system": "You are helpful",
     *   "messages": [
     *     {"role": "user", "content": "hello"}
     *   ],
     *   "temperature": 0.7
     * }
     */
    private Object transformChatRequestToClaude(ChatDTO.Request request) {
        try {
            ObjectNode claudeRequest = objectMapper.createObjectNode();

            // 模型名称
            claudeRequest.put("model", adaptModelName(request.model()));

            // 提取 system 消息作为独立的 system 字段
            ArrayNode messages = objectMapper.createArrayNode();
            String systemPrompt = null;

            if (request.messages() != null) {
                for (Object msgObj : request.messages()) {
                    JsonNode msgNode = objectMapper.valueToTree(msgObj);
                    String role = msgNode.has("role") ? msgNode.get("role").asText() : "";
                    String content = msgNode.has("content") ? msgNode.get("content").asText() : "";

                    if ("system".equalsIgnoreCase(role)) {
                        // Claude 将 system 消息放在顶级 system 字段
                        systemPrompt = content;
                    } else {
                        messages.add(msgNode);
                    }
                }
            }

            if (systemPrompt != null) {
                claudeRequest.put("system", systemPrompt);
            }

            claudeRequest.set("messages", messages);

            // max_tokens（Claude 必填）
            if (request.maxTokens() != null) {
                claudeRequest.put("max_tokens", request.maxTokens());
            } else {
                claudeRequest.put("max_tokens", 4096); // Claude 默认值
            }

            // 可选参数
            if (request.temperature() != null) {
                claudeRequest.put("temperature", request.temperature());
            }
            if (request.topP() != null) {
                claudeRequest.put("top_p", request.topP());
            }
            if (request.stop() != null) {
                // stop 可以是 String 或 List，需要统一转换为数组
                Object stopValue = request.stop();
                if (stopValue instanceof String stopStr) {
                    ArrayNode stopArray = objectMapper.createArrayNode();
                    stopArray.add(stopStr);
                    claudeRequest.set("stop_sequences", stopArray);
                } else {
                    claudeRequest.set("stop_sequences", objectMapper.valueToTree(stopValue));
                }
            }
            if (request.stream() != null) {
                claudeRequest.put("stream", request.stream());
            }

            // Claude 特有参数
            if (request.topK() != null) {
                claudeRequest.put("top_k", request.topK());
            }

            return claudeRequest;
        } catch (Exception e) {
            log.warn("Failed to transform request to Claude format: {}", e.getMessage());
            return request;
        }
    }

    @Override
    protected Object transformResponse(final Object response, final String adapterType) {
        if (response instanceof String responseStr) {
            return transformClaudeResponseToOpenAi(responseStr);
        }
        return response;
    }

    /**
     * 将 Claude 格式的响应转换为 OpenAI 格式
     *
     * Claude 格式:
     * {
     *   "id": "msg_01XFDUDYJgAACjnvnFvZwEge",
     *   "type": "message",
     *   "role": "assistant",
     *   "content": [{"type": "text", "text": "Hello!"}],
     *   "model": "claude-3-sonnet-20240229",
     *   "stop_reason": "end_turn",
     *   "usage": {"input_tokens": 10, "output_tokens": 5}
     * }
     *
     * OpenAI 格式:
     * {
     *   "id": "chatcmpl-xxx",
     *   "object": "chat.completion",
     *   "choices": [{"message": {"role": "assistant", "content": "Hello!"}, "finish_reason": "stop"}],
     *   "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
     * }
     */
    private String transformClaudeResponseToOpenAi(String responseStr) {
        try {
            JsonNode claudeResponse = objectMapper.readTree(responseStr);

            // 如果不是 Claude 格式，直接返回
            if (!claudeResponse.has("type") || !"message".equals(claudeResponse.get("type").asText())) {
                return responseStr;
            }

            ObjectNode openAiResponse = objectMapper.createObjectNode();

            // 基本信息
            openAiResponse.put("id", "chatcmpl-" + (claudeResponse.has("id") ? claudeResponse.get("id").asText() : System.currentTimeMillis()));
            openAiResponse.put("object", "chat.completion");
            openAiResponse.put("created", System.currentTimeMillis() / 1000);
            openAiResponse.put("model", claudeResponse.has("model") ? claudeResponse.get("model").asText() : "unknown");

            // 转换 choices
            ArrayNode choices = objectMapper.createArrayNode();
            ObjectNode choice = objectMapper.createObjectNode();

            // 提取内容
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", claudeResponse.has("role") ? claudeResponse.get("role").asText() : "assistant");

            if (claudeResponse.has("content") && claudeResponse.get("content").isArray()) {
                StringBuilder contentBuilder = new StringBuilder();
                for (JsonNode contentBlock : claudeResponse.get("content")) {
                    if (contentBlock.has("text")) {
                        contentBuilder.append(contentBlock.get("text").asText());
                    }
                }
                message.put("content", contentBuilder.toString());
            }

            choice.set("message", message);

            // 转换 finish_reason
            String stopReason = claudeResponse.has("stop_reason") ? claudeResponse.get("stop_reason").asText() : "stop";
            choice.put("finish_reason", mapClaudeStopReason(stopReason));

            choices.add(choice);
            openAiResponse.set("choices", choices);

            // 转换 usage
            if (claudeResponse.has("usage")) {
                JsonNode usage = claudeResponse.get("usage");
                ObjectNode openAiUsage = objectMapper.createObjectNode();
                openAiUsage.put("prompt_tokens", usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0);
                openAiUsage.put("completion_tokens", usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0);
                openAiUsage.put("total_tokens",
                        openAiUsage.get("prompt_tokens").asInt() + openAiUsage.get("completion_tokens").asInt());
                openAiResponse.set("usage", openAiUsage);
            }

            return objectMapper.writeValueAsString(openAiResponse);
        } catch (Exception e) {
            log.debug("Failed to transform Claude response: {}", e.getMessage());
            return responseStr;
        }
    }

    /**
     * 映射 Claude 的 stop_reason 到 OpenAI 的 finish_reason
     */
    private String mapClaudeStopReason(String claudeStopReason) {
        return switch (claudeStopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "stop_sequence" -> "stop";
            default -> "stop";
        };
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        // Claude 流式响应格式与 OpenAI 不同，需要转换
        // 暂时返回原始格式，后续可以增强
        return chunk;
    }
}

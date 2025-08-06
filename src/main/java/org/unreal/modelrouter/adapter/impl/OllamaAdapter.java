package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.ChatDTO;
import org.unreal.modelrouter.dto.EmbeddingDTO;

/**
 * Ollama Adapter - 适配Ollama API格式的示例
 * 展示如何轻松扩展新的适配器
 */
public class OllamaAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    protected String getAdapterType() {
        return "ollama";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof ChatDTO.Request) {
            return transformChatRequest((ChatDTO.Request) request);
        } else if (request instanceof EmbeddingDTO.Request) {
            return transformEmbeddingRequest((EmbeddingDTO.Request) request);
        } else {
            return request;
        }
    }

    /**
     * Ollama的Chat API格式转换
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            // Ollama使用不同的字段名
            ollamaRequest.put("model", adaptModelName(request.model()));

            // Ollama的消息格式可能需要调整
            ollamaRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // Ollama特有的选项
            ObjectNode options = objectMapper.createObjectNode();
            if (request.temperature() != null) {
                options.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                options.put("num_predict", request.maxTokens()); // Ollama使用num_predict
            }
            ollamaRequest.set("options", options);

            // 流式处理
            if (request.stream() != null) {
                ollamaRequest.put("stream", request.stream());
            }

            return ollamaRequest;
        } catch (Exception e) {
            return request;
        }
    }

    /**
     * Ollama的Embedding API格式转换
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode ollamaRequest = objectMapper.createObjectNode();

            ollamaRequest.put("model", adaptModelName(request.model()));

            // Ollama embedding使用prompt字段而不是input
            if (request.input() instanceof String) {
                ollamaRequest.put("prompt", (String) request.input());
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
     * 将Ollama响应转换为OpenAI格式
     */
    private String transformResponseJson(JsonNode ollamaResponse) {
        try {
            ObjectNode standardResponse = objectMapper.createObjectNode();

            if (ollamaResponse.has("message")) {
                // Chat响应转换
                ObjectNode choice = objectMapper.createObjectNode();
                choice.set("message", ollamaResponse.get("message"));
                choice.put("index", 0);
                choice.put("finish_reason", ollamaResponse.get("done").asBoolean() ? "stop" : "length");

                standardResponse.set("choices", objectMapper.createArrayNode().add(choice));
                standardResponse.put("object", "chat.completion");
            } else if (ollamaResponse.has("embedding")) {
                // Embedding响应转换
                ObjectNode embeddingData = objectMapper.createObjectNode();
                embeddingData.set("embedding", ollamaResponse.get("embedding"));
                embeddingData.put("index", 0);
                embeddingData.put("object", "embedding");

                standardResponse.set("data", objectMapper.createArrayNode().add(embeddingData));
                standardResponse.put("object", "list");
                standardResponse.put("model", ollamaResponse.get("model").asText());
            } else {
                return ollamaResponse.toString();
            }

            // 添加标准字段
            standardResponse.put("id", "ollama-" + System.currentTimeMillis());
            standardResponse.put("created", System.currentTimeMillis() / 1000);

            return standardResponse.toString();
        } catch (Exception e) {
            return ollamaResponse.toString();
        }
    }

    @Override
    protected String transformStreamChunk(String chunk) {
        try {
            // Ollama的流式响应处理
            if (!chunk.trim().isEmpty()) {
                JsonNode chunkJson = objectMapper.readTree(chunk);

                ObjectNode standardChunk = objectMapper.createObjectNode();
                standardChunk.put("id", "ollama-" + System.currentTimeMillis());
                standardChunk.put("object", "chat.completion.chunk");
                standardChunk.put("created", System.currentTimeMillis() / 1000);

                ObjectNode choice = objectMapper.createObjectNode();
                choice.put("index", 0);

                if (chunkJson.has("message") && chunkJson.get("message").has("content")) {
                    ObjectNode delta = objectMapper.createObjectNode();
                    delta.put("content", chunkJson.get("message").get("content").asText());
                    choice.set("delta", delta);
                }

                if (chunkJson.has("done") && chunkJson.get("done").asBoolean()) {
                    choice.put("finish_reason", "stop");
                }

                standardChunk.set("choices", objectMapper.createArrayNode().add(choice));
                return "data: " + standardChunk.toString() + "\n\n";
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // Ollama通常不需要认证，或使用简单的API Key
        if (authorization != null && !authorization.startsWith("Bearer ")) {
            return "Bearer " + authorization;
        }
        return authorization;
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(WebClient.RequestBodySpec requestSpec, T request) {
        // Ollama特有的请求头配置
        return super.configureRequestHeaders(requestSpec, request)
                .header("User-Agent", "ModelRouter-OllamaAdapter/1.0");
    }
}
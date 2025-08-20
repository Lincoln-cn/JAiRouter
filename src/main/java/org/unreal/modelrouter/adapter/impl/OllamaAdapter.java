package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.AdapterCapabilities;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.dto.ChatDTO;
import org.unreal.modelrouter.dto.EmbeddingDTO;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

/**
 * Ollama Adapter - 适配Ollama API格式的示例
 * 展示如何轻松扩展新的适配器
 */
public class OllamaAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector) {
        super(registry, metricsCollector);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.builder()
                .chat(true)
                .embedding(true)
                .build();
    }

    @Override
    protected String getAdapterType() {
        return "ollama";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        // 记录Ollama适配器特定的追踪信息
        org.unreal.modelrouter.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    // 添加Ollama特定的属性
                    currentSpan.setAttribute("adapter.local_deployment", true);
                    currentSpan.setAttribute("adapter.supports_custom_models", true);
                    currentSpan.setAttribute("adapter.deployment_type", "local");
                    currentSpan.setAttribute("adapter.model_format", "gguf");
                    currentSpan.setAttribute("adapter.version", "v1");

                    // 根据请求类型添加特定属性
                    if (request instanceof ChatDTO.Request) {
                        ChatDTO.Request chatRequest = (ChatDTO.Request) request;
                        currentSpan.setAttribute("request.model_family", inferModelFamily(chatRequest.model()));
                        currentSpan.setAttribute("request.stream", chatRequest.stream() != null ? chatRequest.stream() : false);
                        currentSpan.setAttribute("request.temperature", chatRequest.temperature() != null ? chatRequest.temperature() : 0.8);
                    } else if (request instanceof EmbeddingDTO.Request) {
                        EmbeddingDTO.Request embeddingRequest = (EmbeddingDTO.Request) request;
                        currentSpan.setAttribute("request.embedding_model", embeddingRequest.model());
                        currentSpan.setAttribute("request.input_type", embeddingRequest.input() instanceof String ? "string" : "array");
                    }
                }

                // 记录适配器调用开始事件
                try {
                    org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer enhancer =
                            org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
                                    org.unreal.modelrouter.tracing.adapter.AdapterTracingEnhancer.class);
                    enhancer.logAdapterCallStart(adapterType, null, getServiceTypeFromRequest(request),
                            getModelNameFromRequest(request), tracingContext);
                } catch (Exception e) {
                    // 忽略追踪增强错误
                }
            } catch (Exception e) {
                // 忽略追踪错误
            }
        }

        if (request instanceof ChatDTO.Request) {
            return transformChatRequest((ChatDTO.Request) request);
        } else if (request instanceof EmbeddingDTO.Request) {
            return transformEmbeddingRequest((EmbeddingDTO.Request) request);
        } else {
            return request;
        }
    }

    /**
     * 推断模型家族
     */
    private String inferModelFamily(String modelName) {
        if (modelName == null) {
            return "unknown";
        }

        String lowerName = modelName.toLowerCase();
        if (lowerName.contains("llama")) {
            return "llama";
        } else if (lowerName.contains("qwen")) {
            return "qwen";
        } else if (lowerName.contains("chatglm")) {
            return "chatglm";
        } else if (lowerName.contains("baichuan")) {
            return "baichuan";
        } else if (lowerName.contains("mistral")) {
            return "mistral";
        } else {
            return "custom";
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
            logAdapterTransformError(getAdapterType(), e);
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
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
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
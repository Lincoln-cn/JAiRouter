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
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;

/**
 * LocalAI Adapter - 适配LocalAI API格式
 * LocalAI是OpenAI API的开源替代方案
 */
public class LocalAiAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalAiAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector) {
        super(registry, metricsCollector);
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.all();
    }

    @Override
    protected String getAdapterType() {
        return "localai";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        // 记录适配器特定的追踪信息
        org.unreal.modelrouter.tracing.TracingContext tracingContext = 
            org.unreal.modelrouter.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    // 添加LocalAI特定的属性
                    currentSpan.setAttribute("adapter.local_deployment", true);
                    currentSpan.setAttribute("adapter.open_source", true);
                    currentSpan.setAttribute("adapter.deployment_type", "localai");
                    currentSpan.setAttribute("adapter.version", "v1");
                    
                    // 根据请求类型添加特定属性
                    if (request instanceof ChatDTO.Request) {
                        ChatDTO.Request chatRequest = (ChatDTO.Request) request;
                        currentSpan.setAttribute("request.stream", chatRequest.stream() != null ? chatRequest.stream() : false);
                        currentSpan.setAttribute("request.max_tokens", chatRequest.maxTokens() != null ? chatRequest.maxTokens() : 0);
                        currentSpan.setAttribute("request.temperature", chatRequest.temperature() != null ? chatRequest.temperature() : 1.0);
                        currentSpan.setAttribute("request.top_p", chatRequest.topP() != null ? chatRequest.topP() : 1.0);
                    } else if (request instanceof EmbeddingDTO.Request) {
                        EmbeddingDTO.Request embeddingRequest = (EmbeddingDTO.Request) request;
                        currentSpan.setAttribute("request.embedding_model", embeddingRequest.model());
                        currentSpan.setAttribute("request.input_type", embeddingRequest.input() instanceof String ? "string" : "array");
                    } else if (request instanceof RerankDTO.Request) {
                        RerankDTO.Request rerankRequest = (RerankDTO.Request) request;
                        currentSpan.setAttribute("request.query_length", rerankRequest.query() != null ? rerankRequest.query().length() : 0);
                        currentSpan.setAttribute("request.documents_count", rerankRequest.documents() != null ? rerankRequest.documents().size() : 0);
                    } else if (request instanceof TtsDTO.Request) {
                        TtsDTO.Request ttsRequest = (TtsDTO.Request) request;
                        currentSpan.setAttribute("request.voice", ttsRequest.voice());
                        currentSpan.setAttribute("request.input_length", ttsRequest.input() != null ? ttsRequest.input().length() : 0);
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
     * 转换Chat请求格式以适配LocalAI
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());
            localAiRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // LocalAI特定参数
            if (request.temperature() != null) {
                localAiRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                localAiRequest.put("max_tokens", request.maxTokens());
            }
            if (request.stream() != null) {
                localAiRequest.put("stream", request.stream());
            }
            if (request.topP() != null) {
                localAiRequest.put("top_p", request.topP());
            }
            if (request.frequencyPenalty() != null) {
                localAiRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.presencePenalty() != null) {
                localAiRequest.put("presence_penalty", request.presencePenalty());
            }
            if (request.user() != null) {
                localAiRequest.put("user", request.user());
            }

            return localAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * 转换Embedding请求为LocalAI格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());

            // LocalAI embedding输入
            if (request.input() instanceof String) {
                localAiRequest.put("input", (String) request.input());
            } else {
                localAiRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            // LocalAI特定参数
            localAiRequest.put("encoding_format", "float");
            if (request.user() != null) {
                localAiRequest.put("user", request.user());
            }

            return localAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", rerankRequest.model());
            localAiRequest.put("query", rerankRequest.query());
            localAiRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                localAiRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                localAiRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            return localAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return rerankRequest;
        }
    }

    /**
     * 转换TTS请求为LocalAI格式
     */
    private Object transformTtsRequest(TtsDTO.Request request) {
        try {
            ObjectNode localAiRequest = objectMapper.createObjectNode();

            localAiRequest.put("model", request.model());
            localAiRequest.put("input", request.input());
            localAiRequest.put("voice", request.voice());

            // LocalAI TTS特定参数
            if (request.responseFormat() != null) {
                localAiRequest.put("response_format", request.responseFormat());
            } else {
                localAiRequest.put("response_format", "mp3"); // 默认格式
            }

            if (request.speed() != null) {
                localAiRequest.put("speed", request.speed());
            }

            return localAiRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    // 确保 transformRequest 返回的是 MultiValueMap 用于 multipart 请求
    private Object transformSttRequest(SttDTO.Request sttRequest) {
        try {
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
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return sttRequest;
        }
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
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
     * 增强LocalAI响应
     */
    private String enhanceLocalAiResponse(JsonNode localAiResponse) {
        try {
            if (localAiResponse.isObject()) {
                ObjectNode enhancedResponse = localAiResponse.deepCopy();

                // 确保标准字段
                if (!enhancedResponse.has("id")) {
                    enhancedResponse.put("id", "localai-" + System.currentTimeMillis());
                }

                if (!enhancedResponse.has("created")) {
                    enhancedResponse.put("created", System.currentTimeMillis() / 1000);
                }

                // 添加系统指纹
                enhancedResponse.put("system_fingerprint", "localai-adapter");

                return enhancedResponse.toString();
            }
            return localAiResponse.toString();
        } catch (Exception e) {
            return localAiResponse.toString();
        }
    }

    @Override
    protected String getAuthorizationHeader(String authorization, String adapterType) {
        // LocalAI通常不需要严格的认证，但支持API key
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization;
        } else if (authorization != null) {
            return "Bearer " + authorization;
        }
        return null;
    }

    @Override
    protected String transformStreamChunk(String chunk) {
        try {
            if (chunk.startsWith("data: ")) {
                String jsonPart = chunk.substring(6).trim();
                if ("[DONE]".equals(jsonPart)) {
                    return chunk;
                }

                JsonNode chunkJson = objectMapper.readTree(jsonPart);
                if (chunkJson.isObject()) {
                    ObjectNode enhancedChunk = chunkJson.deepCopy();

                    // 添加系统指纹
                    if (!enhancedChunk.has("system_fingerprint")) {
                        enhancedChunk.put("system_fingerprint", "localai-adapter");
                    }

                    return "data: " + enhancedChunk;
                }
            }
            return chunk;
        } catch (Exception e) {
            return chunk;
        }
    }
}

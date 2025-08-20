package org.unreal.modelrouter.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * Xinference Adapter - 适配Xinference API格式
 * Xinference是一个支持多种模型的推理平台
 */
public class XinferenceAdapter extends BaseAdapter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public XinferenceAdapter(ModelServiceRegistry registry, MetricsCollector metricsCollector) {
        super(registry, metricsCollector);
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
        return "xinference";
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
                    // 添加Xinference特定的属性
                    currentSpan.setAttribute("adapter.distributed", true);
                    currentSpan.setAttribute("adapter.supports_multiple_models", true);
                    currentSpan.setAttribute("adapter.deployment_type", "xinference");
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
     * 转换Chat请求格式以适配Xinference
     */
    private Object transformChatRequest(ChatDTO.Request request) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            String xinferenceModelName = adaptModelName(request.model());
            xinferenceRequest.put("model", adaptModelName(xinferenceModelName));
            xinferenceRequest.set("messages", objectMapper.valueToTree(request.messages()));

            // Xinference特定参数
            if (request.temperature() != null) {
                xinferenceRequest.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                xinferenceRequest.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                xinferenceRequest.put("top_p", request.topP());
            }
            if (request.stream() != null) {
                xinferenceRequest.put("stream", request.stream());
            }
            if (request.frequencyPenalty() != null) {
                xinferenceRequest.put("frequency_penalty", request.frequencyPenalty());
            }
            if (request.presencePenalty() != null) {
                xinferenceRequest.put("presence_penalty", request.presencePenalty());
            }

            // 如果有stop参数
            if (request.stop() != null) {
                if (request.stop() instanceof String) {
                    ArrayNode stopArray = objectMapper.createArrayNode();
                    stopArray.add((String) request.stop());
                    xinferenceRequest.set("stop", stopArray);
                } else if (request.stop() instanceof java.util.List) {
                    xinferenceRequest.set("stop", objectMapper.valueToTree(request.stop()));
                }
            }

            return xinferenceRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    /**
     * 转换Embedding请求格式
     */
    private Object transformEmbeddingRequest(EmbeddingDTO.Request request) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            String xinferenceModelName = adaptModelName(request.model());
            xinferenceRequest.put("model", adaptModelName(xinferenceModelName));

            if (request.input() instanceof String) {
                xinferenceRequest.put("input", (String) request.input());
            } else {
                xinferenceRequest.set("input", objectMapper.valueToTree(request.input()));
            }

            if (request.encodingFormat() != null) {
                xinferenceRequest.put("encoding_format", request.encodingFormat());
            }
            if (request.user() != null) {
                xinferenceRequest.put("user", request.user());
            }

            return xinferenceRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return request;
        }
    }

    private Object transformRerankRequest(RerankDTO.Request rerankRequest) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            String xinferenceModelName = adaptModelName(rerankRequest.model());
            xinferenceRequest.put("model", adaptModelName(xinferenceModelName));
            xinferenceRequest.put("query", rerankRequest.query());
            xinferenceRequest.set("documents", objectMapper.valueToTree(rerankRequest.documents()));

            if (rerankRequest.topN() != null) {
                xinferenceRequest.put("top_n", rerankRequest.topN());
            }
            if (rerankRequest.returnDocuments() != null) {
                xinferenceRequest.put("return_documents", rerankRequest.returnDocuments());
            }

            return xinferenceRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return rerankRequest;
        }
    }

    private Object transformTtsRequest(TtsDTO.Request ttsRequest) {
        try {
            ObjectNode xinferenceRequest = objectMapper.createObjectNode();

            String xinferenceModelName = adaptModelName(ttsRequest.model());
            xinferenceRequest.put("model", adaptModelName(xinferenceModelName));
            xinferenceRequest.put("input", ttsRequest.input());
            xinferenceRequest.put("voice", ttsRequest.voice());

            if (ttsRequest.responseFormat() != null) {
                xinferenceRequest.put("response_format", ttsRequest.responseFormat());
            }
            if (ttsRequest.speed() != null) {
                xinferenceRequest.put("speed", ttsRequest.speed());
            }

            return xinferenceRequest;
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return ttsRequest;
        }
    }

    private Object transformSttRequest(SttDTO.Request sttRequest) {
        try {
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
        } catch (Exception e) {
            logAdapterTransformError(getAdapterType(), e);
            return sttRequest;
        }
    }

    @Override
    protected Object transformResponse(Object response, String adapterType) {
        // 如果需要对响应进行转换，可以在这里实现
        return response;
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
        // 如果需要对流式响应块进行转换，可以在这里实现
        return adaptModelName(chunk);
    }
}

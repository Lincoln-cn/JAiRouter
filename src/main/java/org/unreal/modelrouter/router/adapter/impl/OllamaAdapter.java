package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OllamaRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OllamaResponseTransformer;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

/**
 * Ollama Adapter - 适配Ollama API格式
 * 支持最新的Ollama API特性
 */
public class OllamaAdapter extends BaseAdapter {

    private static final Logger logger = LoggerFactory.getLogger(OllamaAdapter.class);

    private final OllamaRequestTransformer requestTransformer;
    private final OllamaResponseTransformer responseTransformer;

    public OllamaAdapter(final AdapterContext context,
                         final RequestProcessingSupport requestSupport,
                         final ResilienceSupport resilienceSupport) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = new OllamaRequestTransformer(context.getObjectMapper());
        this.responseTransformer = new OllamaResponseTransformer(context.getObjectMapper());
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
    protected Object transformRequest(final Object request, final String adapterType) {
        recordTracingInfo();

        if (request instanceof ChatDTO.Request) {
            return requestTransformer.transformChatRequest((ChatDTO.Request) request);
        } else if (request instanceof EmbeddingDTO.Request) {
            return requestTransformer.transformEmbeddingRequest((EmbeddingDTO.Request) request);
        } else if (request instanceof RerankDTO.Request) {
            return requestTransformer.transformRerankRequest((RerankDTO.Request) request);
        } else if (request instanceof TtsDTO.Request) {
            return requestTransformer.transformTtsRequest((TtsDTO.Request) request);
        } else if (request instanceof SttDTO.Request) {
            return requestTransformer.transformSttRequest((SttDTO.Request) request);
        }

        return request;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformResponse(final Object response, final String adapterType) {
        try {
            JsonNode jsonNode;
            if (response instanceof JsonNode) {
                jsonNode = (JsonNode) response;
            } else if (response instanceof String) {
                jsonNode = objectMapper.readTree((String) response);
            } else if (response instanceof java.util.Map) {
                // NonStreamingRequestProcessor 会将 JSON 解析为 Map<String, Object>
                jsonNode = objectMapper.valueToTree(response);
            } else {
                return response;
            }

            // 转换响应格式
            String transformedJson = responseTransformer.transformResponseJson(jsonNode);

            // 解析回 Map 以便后续处理
            return objectMapper.readValue(transformedJson, java.util.Map.class);
        } catch (Exception e) {
            logger.warn("Failed to transform response for Ollama: {}", e.getMessage());
            return response;
        }
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }

    @Override
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        return authorization;
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(final WebClient.RequestBodySpec requestSpec, final T request) {
        return requestSpec;
    }

    // ==================== 私有方法 ====================

    private void recordTracingInfo() {
        org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext != null && tracingContext.isActive()) {
            try {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    currentSpan.setAttribute("adapter.type", "ollama");
                    currentSpan.setAttribute("adapter.transform", "ollama_format");
                }
            } catch (Exception e) {
                logAdapterTransformError(getAdapterType(), e);
            }
        }
    }
}

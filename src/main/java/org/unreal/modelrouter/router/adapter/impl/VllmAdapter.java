package org.unreal.modelrouter.router.adapter.impl;

import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.util.ModelUtils;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

/**
 * VLLM Adapter - 适配VLLM API格式
 * VLLM (Very Large Language Model) 推理服务器适配器
 * 支持最新的vLLM OpenAI兼容API
 */
public class VllmAdapter extends BaseAdapter {

    private final VllmRequestTransformer requestTransformer;
    private final VllmResponseTransformer responseTransformer;

    public VllmAdapter(final AdapterContext context,
                       final RequestProcessingSupport requestSupport,
                       final ResilienceSupport resilienceSupport) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = new VllmRequestTransformer(context.getObjectMapper());
        this.responseTransformer = new VllmResponseTransformer(context.getObjectMapper());
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
        return "vllm";
    }

    @Override
    protected Object transformRequest(final Object request, final String adapterType) {
        recordTracingAttributes(request, adapterType);

        String modelFieldName = adaptModelName(ModelUtils.getModelNameFromRequest(request));

        if (request instanceof ChatDTO.Request chatRequest) {
            return requestTransformer.transformChatRequest(chatRequest, modelFieldName);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return requestTransformer.transformEmbeddingRequest(embeddingRequest, modelFieldName);
        } else if (request instanceof RerankDTO.Request rerankRequest) {
            return requestTransformer.transformRerankRequest(rerankRequest, modelFieldName);
        } else if (request instanceof TtsDTO.Request ttsRequest) {
            return requestTransformer.transformTtsRequest(ttsRequest, modelFieldName);
        } else if (request instanceof SttDTO.Request sttRequest) {
            return requestTransformer.transformSttRequest(sttRequest);
        }
        return request;
    }

    private void recordTracingAttributes(final Object request, final String adapterType) {
        try {
            org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
            if (tracingContext != null && tracingContext.isActive()) {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    currentSpan.setAttribute("adapter.high_performance", true);
                    currentSpan.setAttribute("adapter.gpu_accelerated", true);
                    currentSpan.setAttribute("adapter.deployment_type", "vllm");
                    currentSpan.setAttribute("adapter.version", "v1");

                    // 根据请求类型添加特定属性
                    if (request instanceof ChatDTO.Request chatRequest) {
                        currentSpan.setAttribute("request.stream",
                                chatRequest.stream() != null ? chatRequest.stream() : false);
                        currentSpan.setAttribute("request.max_tokens",
                                chatRequest.maxTokens() != null ? chatRequest.maxTokens() : 0);
                        currentSpan.setAttribute("request.temperature",
                                chatRequest.temperature() != null ? chatRequest.temperature() : 1.0);
                    } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
                        currentSpan.setAttribute("request.embedding_model", embeddingRequest.model());
                        currentSpan.setAttribute("request.input_type",
                                embeddingRequest.input() instanceof String ? "string" : "array");
                    } else if (request instanceof RerankDTO.Request rerankRequest) {
                        currentSpan.setAttribute("request.query_length",
                                rerankRequest.query() != null ? rerankRequest.query().length() : 0);
                        currentSpan.setAttribute("request.documents_count",
                                rerankRequest.documents() != null ? rerankRequest.documents().size() : 0);
                    }
                }

                // 记录适配器调用开始事件
                try {
                    org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer enhancer =
                        org.unreal.modelrouter.common.util.ApplicationContextProvider.getBean(
                            org.unreal.modelrouter.monitor.tracing.adapter.AdapterTracingEnhancer.class);
                    enhancer.logAdapterCallStart(adapterType, null,
                        ModelUtils.getServiceTypeFromRequest(request),
                        ModelUtils.getModelNameFromRequest(request),
                        tracingContext);
                } catch (Exception e) {
                    // 忽略追踪增强错误
                }
            }
        } catch (Exception e) {
            // 忽略追踪错误
        }
    }

    @Override
    protected Object transformResponse(final Object response, final String adapterType) {
        return responseTransformer.transformResponse(response);
    }

    @Override
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        String adapted = adaptModelName(authorization);
        if (adapted != null && adapted.startsWith("Bearer ")) {
            return adapted;
        } else if (adapted != null) {
            return "Bearer " + adapted;
        }
        return null;
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }
}

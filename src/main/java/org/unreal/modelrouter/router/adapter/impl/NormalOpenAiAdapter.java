package org.unreal.modelrouter.router.adapter.impl;

import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

/**
 * 标准OpenAI适配器
 * 使用OpenAiRequestTransformer和OpenAiResponseTransformer进行请求/响应转换
 */
public class NormalOpenAiAdapter extends BaseAdapter {

    private final OpenAiRequestTransformer requestTransformer;
    private final OpenAiResponseTransformer responseTransformer;

    public NormalOpenAiAdapter(final AdapterContext context,
                               final RequestProcessingSupport requestSupport,
                               final ResilienceSupport resilienceSupport,
                               final OpenAiRequestTransformer openAiRequestTransformer,
                               final OpenAiResponseTransformer openAiResponseTransformer) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = openAiRequestTransformer;
        this.responseTransformer = openAiResponseTransformer;
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.all();
    }

    @Override
    protected String getAdapterType() {
        return "normal";
    }

    @Override
    protected Object transformRequest(final Object request, final String adapterType) {
        addTracingAttributes(request, adapterType);

        OpenAiRequestTransformer.ModelNameAdapter modelNameAdapter = this::adaptModelName;

        if (request instanceof ChatDTO.Request chatRequest) {
            return requestTransformer.transformChatRequest(chatRequest, modelNameAdapter);
        } else if (request instanceof EmbeddingDTO.Request embeddingRequest) {
            return requestTransformer.transformEmbeddingRequest(embeddingRequest, modelNameAdapter);
        } else if (request instanceof RerankDTO.Request rerankRequest) {
            return requestTransformer.transformRerankRequest(rerankRequest, modelNameAdapter);
        } else if (request instanceof TtsDTO.Request ttsRequest) {
            return requestTransformer.transformTtsRequest(ttsRequest, modelNameAdapter);
        } else if (request instanceof ImageEditDTO.Request imageEditRequest) {
            return requestTransformer.transformImageEditRequest(imageEditRequest, modelNameAdapter);
        } else if (request instanceof SttDTO.Request sttRequest) {
            return requestTransformer.transformSttRequest(sttRequest, modelNameAdapter);
        }

        return request;
    }

    /**
     * 添加追踪属性
     */
    private void addTracingAttributes(final Object request, final String adapterType) {
        org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
            org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return;
        }

        try {
            io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
            if (currentSpan != null) {
                currentSpan.setAttribute("adapter.api_standard", "openai");
                currentSpan.setAttribute("adapter.version", "v1");
                currentSpan.setAttribute("adapter.compliance_level", "full");

                addRequestSpecificAttributes(currentSpan, request);
            }
        } catch (Exception e) {
            // 忽略追踪错误
        }
    }

    /**
     * 添加请求特定属性
     */
    private void addRequestSpecificAttributes(final io.opentelemetry.api.trace.Span span, final Object request) {
        if (request instanceof SttDTO.Request sttRequest) {
            span.setAttribute("request.model", sttRequest.model());
            span.setAttribute("request.language", sttRequest.language());
        } else if (request instanceof ImageEditDTO.Request imageEditRequest) {
            span.setAttribute("request.model", imageEditRequest.model());
            span.setAttribute("request.prompt_length",
                imageEditRequest.prompt() != null ? imageEditRequest.prompt().length() : 0);
        }
    }

    @Override
    protected Object transformResponse(final Object response, final String adapterType) {
        return responseTransformer.transformResponse(response);
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(final WebClient.RequestBodySpec requestSpec, final T request) {
        return super.configureRequestHeaders(requestSpec, request);
    }
}

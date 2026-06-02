package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.checker.CapabilityChecker;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.error.AdapterErrorHandler;
import org.unreal.modelrouter.router.adapter.retry.RetryPolicy;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.router.adapter.tracing.AdapterTracingManager;
import org.unreal.modelrouter.router.adapter.error.ErrorResponseBuilder;
import org.unreal.modelrouter.router.adapter.request.NonStreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;

/**
 * 标准OpenAI适配器
 * 使用OpenAiRequestTransformer和OpenAiResponseTransformer进行请求/响应转换
 */
public class NormalOpenAiAdapter extends BaseAdapter {

    private final OpenAiRequestTransformer requestTransformer;
    private final OpenAiResponseTransformer responseTransformer;

    public NormalOpenAiAdapter(final ModelServiceRegistry registry,
                               final ObjectMapper objectMapper,
                               final ModelCallStatsRepository statsRepository,
                               final RequestBuilder requestBuilder,
                               final ResponseHandler responseHandler,
                               final InstanceSelector instanceSelector,
                               final ResponseTransformer responseTransformer,
                               final CapabilityChecker capabilityChecker,
                               final AdapterErrorHandler errorHandler,
                               final RetryPolicy retryPolicy,
                               final HttpRequestProcessor httpRequestProcessor,
                               final ResponseMapper responseMapper,
                               final AdapterMetricsRecorder metricsRecorder,
                               final AdapterTracingManager tracingManager,
                               final ErrorResponseBuilder errResponseBuilder,
                               final NonStreamingRequestProcessor nonStreamingProcessor,
                               final OpenAiRequestTransformer openAiRequestTransformer,
                               final OpenAiResponseTransformer openAiResponseTransformer) {
        super(registry, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, 
              responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, 
              responseMapper, metricsRecorder, tracingManager, errResponseBuilder, nonStreamingProcessor);
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

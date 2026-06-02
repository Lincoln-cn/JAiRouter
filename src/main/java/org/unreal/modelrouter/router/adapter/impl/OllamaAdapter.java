package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.unreal.modelrouter.router.adapter.transformer.OllamaRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OllamaResponseTransformer;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;

/**
 * Ollama Adapter - 适配Ollama API格式
 * 支持最新的Ollama API特性
 */
public class OllamaAdapter extends BaseAdapter {

    private final OllamaRequestTransformer requestTransformer;
    private final OllamaResponseTransformer responseTransformer;

    public OllamaAdapter(final ModelServiceRegistry registry,
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
                         final NonStreamingRequestProcessor nonStreamingProcessor) {
        super(registry, objectMapper, statsRepository, requestBuilder, responseHandler, instanceSelector, responseTransformer, capabilityChecker, errorHandler, retryPolicy, httpRequestProcessor, responseMapper, metricsRecorder, tracingManager, errResponseBuilder, nonStreamingProcessor);
        this.requestTransformer = new OllamaRequestTransformer(objectMapper);
        this.responseTransformer = new OllamaResponseTransformer(objectMapper);
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
    protected Object transformResponse(final Object response, final String adapterType) {
        if (response instanceof JsonNode) {
            return responseTransformer.transformResponseJson((JsonNode) response);
        } else if (response instanceof String) {
            try {
                JsonNode jsonNode = objectMapper.readTree((String) response);
                return responseTransformer.transformResponseJson(jsonNode);
            } catch (Exception e) {
                return response;
            }
        }
        return response;
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

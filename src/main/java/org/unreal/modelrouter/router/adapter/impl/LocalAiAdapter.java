package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.unreal.modelrouter.router.adapter.transformer.LocalAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.LocalAiResponseTransformer;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;

/**
 * LocalAI Adapter - 适配LocalAI API格式
 * LocalAI是OpenAI API的开源替代方案，支持最新的LocalAI API特性
 */
public class LocalAiAdapter extends BaseAdapter {

    private final LocalAiRequestTransformer requestTransformer;
    private final LocalAiResponseTransformer responseTransformer;

    public LocalAiAdapter(final ModelServiceRegistry registry,
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
        this.requestTransformer = new LocalAiRequestTransformer(objectMapper);
        this.responseTransformer = new LocalAiResponseTransformer(objectMapper);
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
    protected Object transformRequest(final Object request, final String adapterType) {
        addTracingAttributes(request);
        return requestTransformer.transform(request);
    }

    @Override
    protected Object transformResponse(final Object response, final String adapterType) {
        return responseTransformer.transform(response);
    }

    @Override
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization;
        } else if (authorization != null) {
            return "Bearer " + authorization;
        }
        return null;
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }

    private void addTracingAttributes(final Object request) {
        org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
            org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            return;
        }

        try {
            io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
            if (currentSpan != null) {
                currentSpan.setAttribute("adapter.local_deployment", true);
                currentSpan.setAttribute("adapter.open_source", true);
                currentSpan.setAttribute("adapter.deployment_type", "localai");
                currentSpan.setAttribute("adapter.version", "v1");
            }
        } catch (Exception e) {
            // 忽略追踪错误
        }
    }
}

package org.unreal.modelrouter.router.adapter.impl;

import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.LocalAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.LocalAiResponseTransformer;

/**
 * LocalAI Adapter - 适配LocalAI API格式
 * LocalAI是OpenAI API的开源替代方案，支持最新的LocalAI API特性
 */
public class LocalAiAdapter extends BaseAdapter {

    private final LocalAiRequestTransformer requestTransformer;
    private final LocalAiResponseTransformer responseTransformer;

    public LocalAiAdapter(final AdapterContext context,
                          final RequestProcessingSupport requestSupport,
                          final ResilienceSupport resilienceSupport) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = new LocalAiRequestTransformer(context.getObjectMapper());
        this.responseTransformer = new LocalAiResponseTransformer(context.getObjectMapper());
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

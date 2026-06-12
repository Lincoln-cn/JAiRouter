package org.unreal.modelrouter.router.adapter.impl;

import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.XinferenceRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.XinferenceResponseTransformer;

/**
 * Xinference Adapter - 适配Xinference API格式
 * Xinference是一个支持多种模型的推理平台，支持最新的Xinference API特性
 */
public class XinferenceAdapter extends BaseAdapter {

    private final XinferenceRequestTransformer requestTransformer;
    private final XinferenceResponseTransformer responseTransformer;

    public XinferenceAdapter(final AdapterContext context,
                             final RequestProcessingSupport requestSupport,
                             final ResilienceSupport resilienceSupport) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = new XinferenceRequestTransformer(context.getObjectMapper());
        this.responseTransformer = new XinferenceResponseTransformer(context.getObjectMapper());
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
                currentSpan.setAttribute("adapter.distributed", true);
                currentSpan.setAttribute("adapter.supports_multiple_models", true);
                currentSpan.setAttribute("adapter.deployment_type", "xinference");
                currentSpan.setAttribute("adapter.version", "v1");
            }
        } catch (Exception e) {
            // 忽略追踪错误
        }
    }
}

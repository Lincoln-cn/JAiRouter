package org.unreal.modelrouter.router.adapter.impl;

import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.router.adapter.util.ModelUtils;

/**
 * GPUStack Adapter - 适配GPUStack API格式
 * 支持最新的GPUStack OpenAI兼容API
 */
public class GpuStackAdapter extends BaseAdapter {

    private final GpuStackRequestTransformer requestTransformer;
    private final GpuStackResponseTransformer responseTransformer;

    public GpuStackAdapter(final AdapterContext context,
                           final RequestProcessingSupport requestSupport,
                           final ResilienceSupport resilienceSupport) {
        super(context, requestSupport, resilienceSupport);
        this.requestTransformer = new GpuStackRequestTransformer(context.getObjectMapper());
        this.responseTransformer = new GpuStackResponseTransformer(context.getObjectMapper());
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return AdapterCapabilities.all();
    }

    @Override
    protected String getAdapterType() {
        return "gpustack";
    }

    @Override
    protected Object transformRequest(final Object request, final String adapterType) {
        recordTracingAttributes(request, adapterType);

        if (request instanceof SttDTO.Request) {
            return requestTransformer.transformSttRequest((SttDTO.Request) request);
        }

        String modelFieldName = adaptModelName(ModelUtils.getModelNameFromRequest(request));
        return requestTransformer.transformRequest(request, adapterType, modelFieldName);
    }

    private void recordTracingAttributes(final Object request, final String adapterType) {
        try {
            org.unreal.modelrouter.monitor.tracing.TracingContext tracingContext =
                org.unreal.modelrouter.monitor.tracing.TracingContextHolder.getCurrentContext();
            if (tracingContext != null && tracingContext.isActive()) {
                io.opentelemetry.api.trace.Span currentSpan = tracingContext.getCurrentSpan();
                if (currentSpan != null) {
                    currentSpan.setAttribute("adapter.gpu_optimized", true);
                    currentSpan.setAttribute("adapter.supports_streaming", true);
                    currentSpan.setAttribute("adapter.deployment_type", "gpustack");
                    currentSpan.setAttribute("adapter.version", "v1");

                    if (request instanceof org.unreal.modelrouter.common.dto.ChatDTO.Request chatRequest) {
                        currentSpan.setAttribute("request.stream", chatRequest.stream() != null ? chatRequest.stream() : false);
                        currentSpan.setAttribute("request.max_tokens", chatRequest.maxTokens() != null ? chatRequest.maxTokens() : 0);
                        currentSpan.setAttribute("request.temperature", chatRequest.temperature() != null ? chatRequest.temperature() : 1.0);
                    }
                }

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

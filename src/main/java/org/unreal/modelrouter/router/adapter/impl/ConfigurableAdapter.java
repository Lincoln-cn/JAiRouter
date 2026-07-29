package org.unreal.modelrouter.router.adapter.impl;

import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OpenAiResponseTransformer;

import java.util.Collections;
import java.util.Map;

/**
 * 可配置的OpenAI兼容Adapter
 * 通过配置文件定义adapter参数，无需编写代码即可添加新的OpenAI兼容adapter
 */
public class ConfigurableAdapter extends BaseAdapter {

    private final String adapterType;
    private final AdapterCapabilities capabilities;
    private final String authHeaderName;
    private final String authHeaderPrefix;
    private final Map<String, String> additionalHeaders;
    private final OpenAiRequestTransformer requestTransformer;
    private final OpenAiResponseTransformer responseTransformer;

    public ConfigurableAdapter(final AdapterContext context,
                               final RequestProcessingSupport requestSupport,
                               final ResilienceSupport resilienceSupport,
                               final String adapterType,
                               final AdapterCapabilities capabilities,
                               final String authHeaderName,
                               final String authHeaderPrefix,
                               final Map<String, String> additionalHeaders,
                               final OpenAiRequestTransformer requestTransformer,
                               final OpenAiResponseTransformer responseTransformer) {
        super(context, requestSupport, resilienceSupport);
        this.adapterType = adapterType;
        this.capabilities = capabilities;
        this.authHeaderName = authHeaderName;
        this.authHeaderPrefix = authHeaderPrefix;
        this.additionalHeaders = additionalHeaders != null ? additionalHeaders : Collections.emptyMap();
        this.requestTransformer = requestTransformer;
        this.responseTransformer = responseTransformer;
    }

    @Override
    public AdapterCapabilities supportCapability() {
        return capabilities;
    }

    @Override
    protected String getAdapterType() {
        return adapterType;
    }

    @Override
    protected String getAuthorizationHeader(final String authorization, final String adapterType) {
        if ("Authorization".equals(authHeaderName) && "Bearer ".equals(authHeaderPrefix)) {
            return authorization;
        }
        return null;
    }

    @Override
    protected Map<String, String> getAdditionalHeaders() {
        return additionalHeaders;
    }

    @Override
    protected Object transformRequest(final Object request, final String adapterType) {
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

    @Override
    protected Object transformResponse(final Object response, final String adapterType) {
        return responseTransformer.transformResponse(response);
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }
}

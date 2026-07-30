package org.unreal.modelrouter.router.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import org.unreal.modelrouter.router.adapter.transformer.OllamaRequestTransformer;
import org.unreal.modelrouter.router.adapter.transformer.OllamaResponseTransformer;

import java.util.Collections;
import java.util.Map;

/**
 * 可配置的Ollama兼容Adapter
 * 通过配置文件定义adapter参数，无需编写代码即可添加新的Ollama兼容adapter
 */
public class OllamaConfigurableAdapter extends BaseAdapter {

    private static final Logger logger = LoggerFactory.getLogger(OllamaConfigurableAdapter.class);

    private final String adapterType;
    private final AdapterCapabilities capabilities;
    private final String authHeaderName;
    private final String authHeaderPrefix;
    private final Map<String, String> additionalHeaders;
    private final OllamaRequestTransformer requestTransformer;
    private final OllamaResponseTransformer responseTransformer;

    public OllamaConfigurableAdapter(final AdapterContext context,
                                     final RequestProcessingSupport requestSupport,
                                     final ResilienceSupport resilienceSupport,
                                     final String adapterType,
                                     final AdapterCapabilities capabilities,
                                     final String authHeaderName,
                                     final String authHeaderPrefix,
                                     final Map<String, String> additionalHeaders) {
        super(context, requestSupport, resilienceSupport);
        this.adapterType = adapterType;
        this.capabilities = capabilities;
        this.authHeaderName = authHeaderName;
        this.authHeaderPrefix = authHeaderPrefix;
        this.additionalHeaders = additionalHeaders != null ? additionalHeaders : Collections.emptyMap();
        this.requestTransformer = new OllamaRequestTransformer(context.getObjectMapper());
        this.responseTransformer = new OllamaResponseTransformer(context.getObjectMapper());
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
                jsonNode = objectMapper.valueToTree(response);
            } else {
                return response;
            }

            String transformedJson = responseTransformer.transformResponseJson(jsonNode);
            return objectMapper.readValue(transformedJson, java.util.Map.class);
        } catch (Exception e) {
            logger.warn("Failed to transform response for {}: {}", adapterType, e.getMessage());
            return response;
        }
    }

    @Override
    protected String transformStreamChunk(final String chunk) {
        return responseTransformer.transformStreamChunk(chunk);
    }
}

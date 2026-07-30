package org.unreal.modelrouter.router.adapter.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.router.adapter.BaseAdapter;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.adapter.support.AdapterContext;
import org.unreal.modelrouter.router.adapter.support.RequestProcessingSupport;
import org.unreal.modelrouter.router.adapter.support.ResilienceSupport;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

/**
 * 继承扩展Adapter
 * 基于现有adapter创建变体，支持覆盖能力配置、认证方式和额外请求头
 */
public class ExtendedAdapter implements ServiceCapability {

    private final ServiceCapability parentAdapter;
    private final String adapterType;
    private final AdapterCapabilities overriddenCapabilities;
    private final String authHeaderName;
    private final String authHeaderPrefix;
    private final Map<String, String> additionalHeaders;

    public ExtendedAdapter(final ServiceCapability parentAdapter,
                           final String adapterType,
                           final AdapterCapabilities overriddenCapabilities,
                           final String authHeaderName,
                           final String authHeaderPrefix,
                           final Map<String, String> additionalHeaders) {
        this.parentAdapter = parentAdapter;
        this.adapterType = adapterType;
        this.overriddenCapabilities = overriddenCapabilities;
        this.authHeaderName = authHeaderName;
        this.authHeaderPrefix = authHeaderPrefix;
        this.additionalHeaders = additionalHeaders != null ? additionalHeaders : Collections.emptyMap();
    }

    public AdapterCapabilities supportCapability() {
        if (overriddenCapabilities != null) {
            return overriddenCapabilities;
        }
        if (parentAdapter instanceof BaseAdapter baseAdapter) {
            return baseAdapter.supportCapability();
        }
        return null;
    }

    @Override
    public Mono<ResponseEntity<?>> chat(final ChatDTO.Request request, final String authorization,
                                        final ServerHttpRequest httpRequest) {
        return parentAdapter.chat(request, authorization, httpRequest);
    }

    @Override
    public Mono<ResponseEntity<?>> embedding(final EmbeddingDTO.Request request, final String authorization,
                                              final ServerHttpRequest httpRequest) {
        return parentAdapter.embedding(request, authorization, httpRequest);
    }

    @Override
    public Mono<ResponseEntity<?>> rerank(final RerankDTO.Request request, final String authorization,
                                           final ServerHttpRequest httpRequest) {
        return parentAdapter.rerank(request, authorization, httpRequest);
    }

    @Override
    public Mono<ResponseEntity<?>> tts(final TtsDTO.Request request, final String authorization,
                                        final ServerHttpRequest httpRequest) {
        return parentAdapter.tts(request, authorization, httpRequest);
    }

    @Override
    public Mono<ResponseEntity<?>> stt(final SttDTO.Request request, final String authorization,
                                        final ServerHttpRequest httpRequest) {
        return parentAdapter.stt(request, authorization, httpRequest);
    }

    @Override
    public Mono<ResponseEntity<?>> imageGenerate(final ImageGenerateDTO.Request request,
                                                   final String authorization, final ServerHttpRequest httpRequest) {
        return parentAdapter.imageGenerate(request, authorization, httpRequest);
    }

    @Override
    public Mono<ResponseEntity<?>> imageEdit(final ImageEditDTO.Request request,
                                               final String authorization, final ServerHttpRequest httpRequest) {
        return parentAdapter.imageEdit(request, authorization, httpRequest);
    }

    public String getAdapterType() {
        return adapterType;
    }

    public ServiceCapability getParentAdapter() {
        return parentAdapter;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public String getAuthHeaderPrefix() {
        return authHeaderPrefix;
    }

    public Map<String, String> getAdditionalHeaders() {
        return additionalHeaders;
    }
}

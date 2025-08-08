package org.unreal.modelrouter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/v1")
public class UniversalController {

    private final AdapterRegistry adapterRegistry;
    private final ServiceStateManager serviceStateManager;

    public UniversalController(AdapterRegistry adapterRegistry,
                               ServiceStateManager serviceStateManager) {
        this.adapterRegistry = adapterRegistry;
        this.serviceStateManager = serviceStateManager;
    }

    /**
     * 聊天完成接口
     */
    @PostMapping("/chat/completions")
    public Mono<? extends ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ChatDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.chat,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)
                        .chat(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    /**
     * 文本嵌入接口
     */
    @PostMapping("/embeddings")
    public Mono<? extends ResponseEntity<?>> embeddings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody EmbeddingDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.embedding,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.embedding)
                        .embedding(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    /**
     * 重排序接口
     */
    @PostMapping("/rerank")
    public Mono<? extends ResponseEntity<?>> rerank(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RerankDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.rerank,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.rerank)
                        .rerank(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    /**
     * 文本转语音接口
     */
    @PostMapping("/audio/speech")
    public Mono<? extends ResponseEntity<?>> textToSpeech(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.tts,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.tts)
                        .tts(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    /**
     * 语音转文本接口
     */
    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<? extends ResponseEntity<?>> speechToText(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            SttDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.stt,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.stt)
                        .stt(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    @PostMapping("/images/generations")
    public Mono<? extends ResponseEntity<?>> imageGenerate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ImageGenerateDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.imgGen,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.imgGen)
                        .imageGenerate(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    @PostMapping("/images/edits")
    public Mono<? extends ResponseEntity<?>> imageEdits(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ImageEditDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.imgEdit,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.imgEdit)
                        .imageEdit(request, authorization, httpRequest),
                httpRequest,
                request.model()
        );
    }

    /**
     * 通用服务请求处理器
     */
    private Mono<? extends ResponseEntity<?>> handleServiceRequest(
            ModelServiceRegistry.ServiceType serviceType,
            ServiceRequestSupplier requestSupplier,
            ServerHttpRequest httpRequest,
            String modelName) {

        // 获取客户端IP
        String clientIp = IpUtils.getClientIp(httpRequest);

        // 检查服务健康状态
        if (!serviceStateManager.isServiceHealthy(serviceType.name())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    serviceType.name() + " service is currently unavailable");
        }

        try {
            // 委托给适配器处理
            return requestSupplier.get();

        } catch (UnsupportedOperationException e) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Service not supported by current adapter: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Adapter configuration error: " + e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    @FunctionalInterface
    protected interface ServiceRequestSupplier {
        Mono<? extends ResponseEntity<?>> get() throws Exception;
    }
}
package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
@Tag(name = "统一模型接口", description = "提供兼容OpenAI格式的统一模型服务接口")
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
    @Operation(summary = "聊天完成接口", description = "处理聊天完成请求，兼容OpenAI格式")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> chatCompletions(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "聊天请求参数")
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
    @Operation(summary = "文本嵌入接口", description = "处理文本嵌入请求，兼容OpenAI格式")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> embeddings(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "嵌入请求参数")
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
    @Operation(summary = "重排序接口", description = "处理重排序请求")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> rerank(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "重排序请求参数")
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
    @Operation(summary = "文本转语音接口", description = "处理文本转语音请求，兼容OpenAI格式")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> textToSpeech(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "文本转语音请求参数")
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
    @Operation(summary = "语音转文本接口", description = "处理语音转文本请求，兼容OpenAI格式")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> speechToText(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "语音转文本请求参数")
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
    @Operation(summary = "图像生成接口", description = "处理图像生成请求，兼容OpenAI格式")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> imageGenerate(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "图像生成请求参数")
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
    @Operation(summary = "图像编辑接口", description = "处理图像编辑请求，兼容OpenAI格式")
    @ApiResponse(responseCode = "200", description = "请求处理成功")
    @ApiResponse(responseCode = "400", description = "请求参数错误")
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
    @ApiResponse(responseCode = "503", description = "服务不可用")
    public Mono<? extends ResponseEntity<?>> imageEdits(
            @Parameter(description = "认证令牌")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "图像编辑请求参数")
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
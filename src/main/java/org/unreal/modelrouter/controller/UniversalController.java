package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/v1")
@Tag(name = "统一模型接口", description = "提供兼容OpenAI格式的统一模型服务接口")
public class UniversalController {

    private final AdapterRegistry adapterRegistry;
    private final ServiceStateManager serviceStateManager;
    private final MetricsCollector metricsCollector;

    private Logger logger = LoggerFactory.getLogger(UniversalController.class);

    public UniversalController(AdapterRegistry adapterRegistry,
                               ServiceStateManager serviceStateManager,
                               @Autowired(required = false) MetricsCollector metricsCollector) {
        this.adapterRegistry = adapterRegistry;
        this.serviceStateManager = serviceStateManager;
        this.metricsCollector = metricsCollector;
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
            @RequestBody(required = false) ChatDTO.Request request,
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
            @RequestBody(required = false) EmbeddingDTO.Request request,
            ServerHttpRequest httpRequest) {

        // 添加请求体检查
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

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
            @RequestBody(required = false) RerankDTO.Request request,
            ServerHttpRequest httpRequest) {

        // 添加请求体检查
        if (request == null) {
            logger.error("Rerank request body is null");
            throw new ServerWebInputException("Request body is required");
        }

        // 记录请求信息用于调试
        logger.debug("Rerank request received: model={}, query length={}, documents count={}", 
            request.model(), 
            request.query() != null ? request.query().length() : 0,
            request.documents() != null ? request.documents().size() : 0);

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
            @RequestBody(required = false) TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        // 添加请求体检查
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

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
            @RequestBody(required = false) ImageGenerateDTO.Request request,
            ServerHttpRequest httpRequest) {

        // 添加请求体检查
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

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
            @RequestBody(required = false) ImageEditDTO.Request request,
            ServerHttpRequest httpRequest) {

        // 添加请求体检查
        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

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
        
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        String serviceName = serviceType.name();
        String method = httpRequest.getMethod().name();
        String path = httpRequest.getPath().value();

        // 检查服务健康状态
        if (!serviceStateManager.isServiceHealthy(serviceName)) {
            // 记录服务不可用的指标
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "503", 0, 0);
            
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    serviceName + " service is currently unavailable");
        }

        try {
            // 委托给适配器处理，并在完成时记录指标
            return requestSupplier.get()
                .doOnSuccess(response -> {
                    // 请求成功完成时记录指标
                    long duration = System.currentTimeMillis() - startTime;
                    String status = getResponseStatus(response);
                    long requestSize = estimateRequestSize(httpRequest);
                    long responseSize = estimateResponseSize(response);
                    
                    recordRequestMetrics(serviceName, method, duration, status, requestSize, responseSize);
                })
                .doOnError(error -> {
                    // 请求出错时记录指标
                    long duration = System.currentTimeMillis() - startTime;
                    String status = getErrorStatus(error);
                    long requestSize = estimateRequestSize(httpRequest);
                    
                    recordRequestMetrics(serviceName, method, duration, status, requestSize, 0);
                });

        } catch (UnsupportedOperationException e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "501", 0, 0);
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Service not supported by current adapter: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "400", 0, 0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Adapter configuration error: " + e.getMessage());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "500", 0, 0);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * 记录请求指标
     */
    private void recordRequestMetrics(String service, String method, long duration, String status, 
                                    long requestSize, long responseSize) {
        // 如果监控功能未启用，直接返回
        if (metricsCollector == null) {
            return;
        }
        
        try {
            // 记录请求指标
            metricsCollector.recordRequest(service, method, duration, status);
            
            // 记录请求和响应大小指标
            if (requestSize > 0 || responseSize > 0) {
                metricsCollector.recordRequestSize(service, requestSize, responseSize);
            }
        } catch (Exception e) {
            // 指标记录失败不应影响主业务流程，只记录日志
            // 这里可以添加日志记录，但为了保持简洁暂时省略
        }
    }

    /**
     * 获取响应状态码
     */
    private String getResponseStatus(ResponseEntity<?> response) {
        if (response == null) {
            return "unknown";
        }
        return String.valueOf(response.getStatusCode().value());
    }

    /**
     * 获取错误状态码
     */
    private String getErrorStatus(Throwable error) {
        if (error instanceof ResponseStatusException) {
            return String.valueOf(((ResponseStatusException) error).getStatusCode().value());
        }
        // 处理WebClient响应异常
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            org.springframework.web.reactive.function.client.WebClientResponseException webClientException = 
                (org.springframework.web.reactive.function.client.WebClientResponseException) error;
            // 特别处理401 Unauthorized错误
            if (webClientException.getStatusCode().value() == 401) {
                // 记录详细的401错误信息
                logger.error("下游服务认证失败: status={}, message={}, response body={}", 
                    webClientException.getStatusCode(), 
                    webClientException.getMessage(), 
                    webClientException.getResponseBodyAsString());
            }
            // 特别处理400 Bad Request错误
            else if (webClientException.getStatusCode().value() == 400) {
                // 记录详细的400错误信息
                logger.error("下游服务请求错误: status={}, message={}, response body={}", 
                    webClientException.getStatusCode(), 
                    webClientException.getMessage(), 
                    webClientException.getResponseBodyAsString());
            }
            return String.valueOf(webClientException.getStatusCode().value());
        }
        return "500";
    }

    /**
     * 估算请求大小
     */
    private long estimateRequestSize(ServerHttpRequest request) {
        try {
            // 基于Content-Length头估算请求大小
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                return Long.parseLong(contentLength);
            }
            
            // 如果没有Content-Length，返回一个估算值
            // 这里可以根据实际需要进行更精确的计算
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 估算响应大小
     */
    private long estimateResponseSize(ResponseEntity<?> response) {
        try {
            if (response == null || response.getBody() == null) {
                return 0;
            }
            
            // 基于响应体内容估算大小
            // 这里是一个简化的实现，实际可能需要更精确的计算
            String body = response.getBody().toString();
            return body.getBytes().length;
        } catch (Exception e) {
            return 0;
        }
    }

    @FunctionalInterface
    protected interface ServiceRequestSupplier {
        Mono<? extends ResponseEntity<?>> get() throws Exception;
    }
}
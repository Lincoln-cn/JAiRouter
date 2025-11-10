package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.adapter.ServiceCapability;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.util.IpUtils;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "统一模型接口", description = "提供兼容OpenAI格式的统一模型服务接口")
public class UniversalController {

    private final AdapterRegistry adapterRegistry;
    private final ModelServiceRegistry registry;
    private final ServiceStateManager serviceStateManager;
    private final MetricsCollector metricsCollector;
    private final org.unreal.modelrouter.tracing.interceptor.ControllerTracingInterceptor tracingInterceptor;

    private final Logger logger = LoggerFactory.getLogger(UniversalController.class);

    public UniversalController(AdapterRegistry adapterRegistry,
                               ModelServiceRegistry registry,
                               ServiceStateManager serviceStateManager,
                               @Autowired(required = false) MetricsCollector metricsCollector,
                               @Autowired(required = false) org.unreal.modelrouter.tracing.interceptor.ControllerTracingInterceptor tracingInterceptor) {
        this.adapterRegistry = adapterRegistry;
        this.registry = registry;
        this.serviceStateManager = serviceStateManager;
        this.metricsCollector = metricsCollector;
        this.tracingInterceptor = tracingInterceptor;
    }

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ChatDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.chat,
                request.model(),
                httpRequest,
                "chatCompletions",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.chat,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.chat(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }
        
        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.chat,
                request.model(),
                httpRequest,
                (adapter) -> adapter.chat(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    @PostMapping("/embeddings")
    public Mono<ResponseEntity<?>> embeddings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) EmbeddingDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.embedding,
                request.model(),
                httpRequest,
                "embeddings",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.embedding,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.embedding(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }

        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.embedding,
                request.model(),
                httpRequest,
                (adapter) -> adapter.embedding(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    @PostMapping("/rerank")
    public Mono<ResponseEntity<?>> rerank(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RerankDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (request == null) {
            logger.error("Rerank request body is null");
            throw new ServerWebInputException("Request body is required");
        }

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.rerank,
                request.model(),
                httpRequest,
                "rerank",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.rerank,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.rerank(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }

        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.rerank,
                request.model(),
                httpRequest,
                (adapter) -> adapter.rerank(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    @PostMapping("/audio/speech")
    public Mono<ResponseEntity<?>> textToSpeech(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) TtsDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.tts,
                request.model(),
                httpRequest,
                "textToSpeech",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.tts,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.tts(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }

        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.tts,
                request.model(),
                httpRequest,
                (adapter) -> adapter.tts(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> speechToText(
            @RequestPart("model") String model,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "language", required = false) String language,
            @RequestPart(value = "prompt", required = false) String prompt,
            @RequestPart(value = "responseFormat", required = false) String responseFormat,
            @RequestPart(value = "temperature", required = false) Double temperature,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            ServerHttpRequest httpRequest) {

        SttDTO.Request request = new SttDTO.Request(model, file, language, prompt, responseFormat, temperature);

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.stt,
                request.model(),
                httpRequest,
                "speechToText",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.stt,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.stt(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }

        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.stt,
                request.model(),
                httpRequest,
                (adapter) -> adapter.stt(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    @PostMapping("/images/generations")
    public Mono<ResponseEntity<?>> imageGenerate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ImageGenerateDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.imgGen,
                request.model(),
                httpRequest,
                "imageGenerate",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.imgGen,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.imageGenerate(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }

        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.imgGen,
                request.model(),
                httpRequest,
                (adapter) -> adapter.imageGenerate(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    @PostMapping("/images/edits")
    public Mono<ResponseEntity<?>> imageEdits(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ImageEditDTO.Request request,
            ServerHttpRequest httpRequest) {

        if (request == null) {
            throw new ServerWebInputException("Request body is required");
        }

        if (tracingInterceptor != null) {
            return tracingInterceptor.traceControllerCall(
                ModelServiceRegistry.ServiceType.imgEdit,
                request.model(),
                httpRequest,
                "imageEdits",
                () -> handleServiceRequestWithInstanceAdapter(
                    ModelServiceRegistry.ServiceType.imgEdit,
                    request.model(),
                    httpRequest,
                    (adapter) -> adapter.imageEdit(request, authorization, httpRequest)
                            .map(resp -> (ResponseEntity<?>) resp)
                )
            );
        }

        return handleServiceRequestWithInstanceAdapter(
                ModelServiceRegistry.ServiceType.imgEdit,
                request.model(),
                httpRequest,
                (adapter) -> adapter.imageEdit(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp)
        );
    }

    /**
     * 通用服务请求处理器
     */
    private Mono<ResponseEntity<?>> handleServiceRequest(
            ModelServiceRegistry.ServiceType serviceType,
            ServiceRequestSupplier requestSupplier,
            ServerHttpRequest httpRequest,
            String modelName) {

        String serviceName = serviceType.name();
        long startTime = System.currentTimeMillis();
        String method = httpRequest.getMethod().name();

        // 检查服务健康状态
        if (!serviceStateManager.isServiceHealthy(serviceName)) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "503", 0, 0);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    serviceName + " service is currently unavailable");
        }

        try {
            return requestSupplier.get()
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String status = getResponseStatus(response);
                    long requestSize = estimateRequestSize(httpRequest);
                    long responseSize = estimateResponseSize(response);
                    recordRequestMetrics(serviceName, method, duration, status, requestSize, responseSize);
                })
                .doOnError(error -> {
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



    private void recordRequestMetrics(String service, String method, long duration, String status, 
                                    long requestSize, long responseSize) {
        if (metricsCollector == null) {
            return;
        }
        try {
            metricsCollector.recordRequest(service, method, duration, status);
            if (requestSize > 0 || responseSize > 0) {
                metricsCollector.recordRequestSize(service, requestSize, responseSize);
            }
        } catch (Exception e) {
            // 仅记录日志
        }
    }

    private String getResponseStatus(ResponseEntity<?> response) {
        if (response == null) {
            return "unknown";
        }
        return String.valueOf(response.getStatusCode().value());
    }

    private String getErrorStatus(Throwable error) {
        if (error instanceof ResponseStatusException) {
            return String.valueOf(((ResponseStatusException) error).getStatusCode().value());
        }
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
            if (webClientException.getStatusCode().value() == 401) {
                logger.error("下游服务认证失败: status={}, message={}, response body={}", 
                    webClientException.getStatusCode(), 
                    webClientException.getMessage(), 
                    webClientException.getResponseBodyAsString());
            } else if (webClientException.getStatusCode().value() == 400) {
                logger.error("下游服务请求错误: status={}, message={}, response body={}", 
                    webClientException.getStatusCode(), 
                    webClientException.getMessage(), 
                    webClientException.getResponseBodyAsString());
            }
            return String.valueOf(webClientException.getStatusCode().value());
        }
        if (error instanceof org.unreal.modelrouter.exception.DownstreamServiceException downstreamException) {
            return String.valueOf(downstreamException.getStatusCode().value());
        }
        return "500";
    }

    private long estimateRequestSize(ServerHttpRequest request) {
        try {
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                return Long.parseLong(contentLength);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long estimateResponseSize(ResponseEntity<?> response) {
        try {
            if (response == null || response.getBody() == null) {
                return 0;
            }
            String body = response.getBody().toString();
            return body.getBytes().length;
        } catch (Exception e) {
            return 0;
        }
    }



    @FunctionalInterface
    protected interface ServiceRequestSupplier {
        Mono<ResponseEntity<?>> get() throws Exception;
    }

    @FunctionalInterface
    protected interface InstanceAdapterRequestSupplier {
        Mono<ResponseEntity<?>> get(ServiceCapability adapter) throws Exception;
    }

    /**
     * 支持实例级适配器选择的服务请求处理器
     */
    private Mono<ResponseEntity<?>> handleServiceRequestWithInstanceAdapter(
            ModelServiceRegistry.ServiceType serviceType,
            String modelName,
            ServerHttpRequest httpRequest,
            InstanceAdapterRequestSupplier requestSupplier) {

        String clientIp = IpUtils.getClientIp(httpRequest);
        
        // 1. 首先选择实例
        ModelRouterProperties.ModelInstance selectedInstance;
        try {
            selectedInstance = registry.selectInstance(serviceType, modelName, clientIp);
            
            // 追踪实例选择
            if (tracingInterceptor != null) {
                tracingInterceptor.traceInstanceSelection(serviceType, modelName, clientIp, selectedInstance);
            }
        } catch (Exception e) {
            logger.error("Failed to select instance for service: {}, model: {}", serviceType, modelName, e);
            return Mono.error(e);
        }

        // 2. 根据选中的实例获取适配器
        ServiceCapability adapter;
        String adapterName;
        try {
            adapter = adapterRegistry.getAdapter(serviceType, selectedInstance);
            adapterName = selectedInstance.getAdapter() != null ? selectedInstance.getAdapter() : "default";
            logger.info("Selected adapter '{}' for instance '{}' in service '{}'", 
                       adapterName, selectedInstance.getName(), serviceType);
        } catch (Exception e) {
            logger.error("Failed to get adapter for instance: {}", selectedInstance.getName(), e);
            return Mono.error(e);
        }

        // 3. 使用选中的适配器处理请求，并追踪适配器调用
        final String finalAdapterName = adapterName;
        return handleServiceRequest(
                serviceType,
                () -> {
                    try {
                        if (tracingInterceptor != null) {
                            return tracingInterceptor.traceAdapterCall(
                                finalAdapterName,
                                serviceType,
                                selectedInstance,
                                () -> {
                                    try {
                                        return requestSupplier.get(adapter);
                                    } catch (Exception e) {
                                        return Mono.error(e);
                                    }
                                }
                            );
                        } else {
                            return requestSupplier.get(adapter);
                        }
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                },
                httpRequest,
                modelName
        );
    }
}
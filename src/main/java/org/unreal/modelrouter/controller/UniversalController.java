package org.unreal.modelrouter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.util.IpUtils;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.reactive.ReactiveTracingContextHolder;
import org.unreal.modelrouter.tracing.query.TraceQueryService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@Tag(name = "统一模型接口", description = "提供兼容OpenAI格式的统一模型服务接口")
public class UniversalController {

    private final AdapterRegistry adapterRegistry;
    private final ServiceStateManager serviceStateManager;
    private final MetricsCollector metricsCollector;
    private final TraceQueryService traceQueryService;

    private final Logger logger = LoggerFactory.getLogger(UniversalController.class);

    public UniversalController(AdapterRegistry adapterRegistry,
                               ServiceStateManager serviceStateManager,
                               @Autowired(required = false) MetricsCollector metricsCollector,
                               @Autowired(required = false) TraceQueryService traceQueryService) {
        this.adapterRegistry = adapterRegistry;
        this.serviceStateManager = serviceStateManager;
        this.metricsCollector = metricsCollector;
        this.traceQueryService = traceQueryService;
    }

    // 下面所有接口签名不变（返回 Mono<? extends ResponseEntity<?>> 没问题），
    // 但 handleServiceRequest 只返回 Mono<ResponseEntity<?>>，可以自动适配

    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ChatDTO.Request request,
            ServerHttpRequest httpRequest) {

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.chat,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)
                        .chat(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.embedding,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.embedding)
                        .embedding(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        logger.debug("Rerank request received: model={}, query length={}, documents count={}", 
            request.model(), 
            request.query() != null ? request.query().length() : 0,
            request.documents() != null ? request.documents().size() : 0);

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.rerank,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.rerank)
                        .rerank(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.tts,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.tts)
                        .tts(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.stt,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.stt)
                        .stt(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.imgGen,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.imgGen)
                        .imageGenerate(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        return handleServiceRequest(
                ModelServiceRegistry.ServiceType.imgEdit,
                () -> adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.imgEdit)
                        .imageEdit(request, authorization, httpRequest)
                        .map(resp -> (ResponseEntity<?>) resp),
                httpRequest,
                request.model()
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

        String clientIp = IpUtils.getClientIp(httpRequest);
        long startTime = System.currentTimeMillis();
        String serviceName = serviceType.name();
        String method = httpRequest.getMethod().name();
        String path = httpRequest.getPath().value();

        return ReactiveTracingContextHolder.getCurrentContext()
                .cast(TracingContext.class)
                .doOnNext(tracingContext -> {
                    logger.info("UniversalController - TracingContext found: traceId={}, spanId={}", 
                               tracingContext.getTraceId(), tracingContext.getSpanId());
                })
                .flatMap(tracingContext -> {
                    logger.info("UniversalController - Creating child span for service: {}", serviceName);

                    Span serviceSpan = tracingContext.createChildSpan(
                        serviceName + ".process", 
                        SpanKind.INTERNAL, 
                        tracingContext.getCurrentSpan()
                    );

                    logger.info("UniversalController - Child span created: spanId={}", 
                               serviceSpan.getSpanContext().getSpanId());

                    serviceSpan.setAttribute("service.type", serviceName);
                    serviceSpan.setAttribute("model.name", modelName != null ? modelName : "unknown");
                    serviceSpan.setAttribute("client.ip", clientIp);

                    return processRequestWithTracing(serviceType, requestSupplier, httpRequest, 
                                                    tracingContext, serviceSpan, clientIp, startTime, 
                                                    serviceName, method);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    logger.info("UniversalController - No TracingContext found in Reactor context");
                    return processRequestWithoutTracing(serviceType, requestSupplier, httpRequest, 
                                                clientIp, startTime, serviceName, method);
                }));
    }

    /**
     * 带追踪的请求处理
     */
    private Mono<ResponseEntity<?>> processRequestWithTracing(
            ModelServiceRegistry.ServiceType serviceType,
            ServiceRequestSupplier requestSupplier,
            ServerHttpRequest httpRequest,
            TracingContext tracingContext,
            Span serviceSpan,
            String clientIp,
            long startTime,
            String serviceName,
            String method) {

        if (!serviceStateManager.isServiceHealthy(serviceName)) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "503", 0, 0);
            serviceSpan.setStatus(StatusCode.ERROR, "Service unavailable");
            serviceSpan.setAttribute("error", true);
            serviceSpan.setAttribute("error.type", "ServiceUnavailable");
            tracingContext.finishSpan(serviceSpan);
            recordServiceSpan(tracingContext, serviceSpan, serviceName, duration, true);
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
                    serviceSpan.setStatus(StatusCode.OK);
                    serviceSpan.setAttribute("http.status_code", status);
                    serviceSpan.setAttribute("response.size", responseSize);
                    serviceSpan.setAttribute("duration.ms", duration);
                    tracingContext.finishSpan(serviceSpan);
                    recordServiceSpan(tracingContext, serviceSpan, serviceName, duration, false);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String status = getErrorStatus(error);
                    long requestSize = estimateRequestSize(httpRequest);
                    
                    logger.info("UniversalController - doOnError: service={}, duration={}ms, error={}", 
                               serviceName, duration, error.getMessage());
                    
                    recordRequestMetrics(serviceName, method, duration, status, requestSize, 0);
                    tracingContext.finishSpan(serviceSpan, error);
                    
                    logger.info("UniversalController - About to call recordServiceSpan for error case");
                    recordServiceSpan(tracingContext, serviceSpan, serviceName, duration, true);
                });

        } catch (UnsupportedOperationException e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "501", 0, 0);
            tracingContext.finishSpan(serviceSpan, e);
            recordServiceSpan(tracingContext, serviceSpan, serviceName, duration, true);
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Service not supported by current adapter: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "400", 0, 0);
            tracingContext.finishSpan(serviceSpan, e);
            recordServiceSpan(tracingContext, serviceSpan, serviceName, duration, true);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Adapter configuration error: " + e.getMessage());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "500", 0, 0);
            tracingContext.finishSpan(serviceSpan, e);
            recordServiceSpan(tracingContext, serviceSpan, serviceName, duration, true);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * 不带追踪的请求处理（原有逻辑）
     */
    private Mono<ResponseEntity<?>> processRequestWithoutTracing(
            ModelServiceRegistry.ServiceType serviceType,
            ServiceRequestSupplier requestSupplier,
            ServerHttpRequest httpRequest,
            String clientIp,
            long startTime,
            String serviceName,
            String method) {

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

    private void recordServiceSpan(TracingContext tracingContext, Span serviceSpan, 
                                  String serviceName, long duration, boolean hasError) {
        logger.info("UniversalController - Recording service span: traceId={}, spanId={}, service={}, duration={}ms, hasError={}", 
                   tracingContext != null ? tracingContext.getTraceId() : "null",
                   serviceSpan != null ? serviceSpan.getSpanContext().getSpanId() : "null",
                   serviceName, duration, hasError);
        
        if (traceQueryService == null) {
            logger.warn("UniversalController - TraceQueryService is null, cannot record service span");
            return;
        }
        if (tracingContext == null || serviceSpan == null) {
            logger.warn("UniversalController - TracingContext or ServiceSpan is null, cannot record service span");
            return;
        }
        
        try {
            List<TraceQueryService.SpanRecord> spanRecords = new ArrayList<>();
            Instant startTime = Instant.now().minusMillis(duration);
            Instant endTime = Instant.now();
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("service.type", serviceName);
            attributes.put("trace.trace_id", tracingContext.getTraceId());
            attributes.put("trace.span_id", serviceSpan.getSpanContext().getSpanId());
            attributes.put("duration.ms", duration);
            attributes.put("error", hasError);
            
            TraceQueryService.SpanRecord spanRecord = new TraceQueryService.SpanRecord(
                serviceSpan.getSpanContext().getSpanId(),
                tracingContext.getTraceId(),
                serviceName + ".process",
                startTime,
                endTime,
                duration,
                hasError,
                hasError ? "500" : "200",
                attributes
            );
            spanRecords.add(spanRecord);
            
            logger.info("UniversalController - Calling traceQueryService.recordTrace with {} spans", spanRecords.size());
            
            traceQueryService.recordTrace(
                tracingContext.getTraceId(),
                "jairouter",
                spanRecords,
                duration
            ).subscribe(
                result -> logger.info("UniversalController - Service span recorded successfully: {}", result),
                error -> logger.error("UniversalController - 记录服务Span失败", error)
            );
        } catch (Exception e) {
            logger.error("UniversalController - 记录服务Span异常", e);
        }
    }

    @FunctionalInterface
    protected interface ServiceRequestSupplier {
        Mono<ResponseEntity<?>> get() throws Exception;
    }
}
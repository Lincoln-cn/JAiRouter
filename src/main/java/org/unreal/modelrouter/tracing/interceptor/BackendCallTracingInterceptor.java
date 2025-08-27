package org.unreal.modelrouter.tracing.interceptor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后端调用追踪拦截器
 * 
 * 实现ExchangeFilterFunction接口，拦截WebClient的HTTP请求，提供：
 * - 创建CLIENT类型的Span进行链路追踪
 * - 注入追踪头到后端请求中
 * - 记录后端调用的详细信息（URL、方法、参数等）
 * - 处理后端调用的成功和失败情况
 * - 记录性能指标和错误统计
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Component
public class BackendCallTracingInterceptor implements ExchangeFilterFunction {
    
    private static final Logger logger = LoggerFactory.getLogger(BackendCallTracingInterceptor.class);
    
    private final StructuredLogger structuredLogger;
    
    // HTTP语义约定常量
    private static final String HTTP_METHOD = "http.method";
    private static final String HTTP_URL = "http.url";
    private static final String HTTP_STATUS_CODE = "http.status_code";
    private static final String HTTP_USER_AGENT = "http.user_agent";
    private static final String HTTP_REQUEST_CONTENT_LENGTH = "http.request_content_length";
    private static final String HTTP_RESPONSE_CONTENT_LENGTH = "http.response_content_length";
    
    // 自定义属性常量
    private static final String BACKEND_ADAPTER = "backend.adapter";
    private static final String BACKEND_INSTANCE = "backend.instance";
    private static final String BACKEND_HOST = "backend.host";
    private static final String BACKEND_PORT = "backend.port";
    
    public BackendCallTracingInterceptor(StructuredLogger structuredLogger) {
        this.structuredLogger = structuredLogger;
    }
    
    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        // 获取当前追踪上下文
        TracingContext tracingContext = TracingContextHolder.getCurrentContext();
        if (tracingContext == null || !tracingContext.isActive()) {
            // 如果没有追踪上下文，直接执行请求
            return next.exchange(request);
        }
        
        // 记录请求开始时间
        Instant startTime = Instant.now();
        
        // 创建CLIENT类型的Span
        String operationName = buildOperationName(request);
        Span span = tracingContext.createSpan(operationName, SpanKind.CLIENT);
        
        // 设置Span属性
        setSpanAttributes(span, request);
        
        // 注入追踪头到请求中
        ClientRequest tracedRequest = injectTracingHeaders(request, tracingContext);
        
        // 记录请求开始日志
        logRequestStart(request, tracingContext);
        
        // 执行请求并处理响应
        return next.exchange(tracedRequest)
            .doOnNext(response -> handleSuccessResponse(response, request, span, tracingContext, startTime))
            .doOnError(error -> handleErrorResponse(error, request, span, tracingContext, startTime))
            .doFinally(signalType -> {
                // 确保Span被正确完成
                if (span != null && span.isRecording()) {
                    tracingContext.finishSpan(span);
                }
            });
    }
    
    /**
     * 构建操作名称
     */
    private String buildOperationName(ClientRequest request) {
        String method = request.method().name();
        URI uri = request.url();
        String host = uri.getHost();
        String path = uri.getPath();
        
        // 简化路径，移除模型名称等动态部分
        String simplifiedPath = simplifyPath(path);
        
        return String.format("HTTP %s %s%s", method, host, simplifiedPath);
    }
    
    /**
     * 简化路径，移除动态部分
     */
    private String simplifyPath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // 移除常见的动态路径部分
        return path.replaceAll("/v\\d+", "/v*")
                  .replaceAll("/models/[^/]+", "/models/*")
                  .replaceAll("/chat/completions/[^/]+", "/chat/completions/*");
    }
    
    /**
     * 设置Span属性
     */
    private void setSpanAttributes(Span span, ClientRequest request) {
        URI uri = request.url();
        
        // HTTP语义约定属性
        span.setAttribute(HTTP_METHOD, request.method().name());
        span.setAttribute(HTTP_URL, uri.toString());
        
        // 设置主机和端口信息
        if (uri.getHost() != null) {
            span.setAttribute(BACKEND_HOST, uri.getHost());
        }
        if (uri.getPort() != -1) {
            span.setAttribute(BACKEND_PORT, uri.getPort());
        }
        
        // 设置User-Agent（如果存在）
        String userAgent = request.headers().getFirst("User-Agent");
        if (userAgent != null) {
            span.setAttribute(HTTP_USER_AGENT, userAgent);
        }
        
        // 尝试从URL中提取适配器和实例信息
        extractBackendInfo(span, uri);
    }
    
    /**
     * 从URL中提取后端信息
     */
    private void extractBackendInfo(Span span, URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        
        // 构建实例标识
        String instance = port != -1 ? host + ":" + port : host;
        span.setAttribute(BACKEND_INSTANCE, instance);
        
        // 尝试从主机名推断适配器类型
        String adapter = inferAdapterType(host);
        if (adapter != null) {
            span.setAttribute(BACKEND_ADAPTER, adapter);
        }
    }
    
    /**
     * 推断适配器类型
     */
    String inferAdapterType(String host) {
        if (host == null) {
            return null;
        }
        
        String lowerHost = host.toLowerCase();
        if (lowerHost.contains("openai") || lowerHost.contains("azure")) {
            return "openai";
        } else if (lowerHost.contains("anthropic") || lowerHost.contains("claude")) {
            return "anthropic";
        } else if (lowerHost.contains("googleapis") || lowerHost.contains("google")) {
            return "google";
        } else if (lowerHost.contains("huggingface")) {
            return "huggingface";
        } else if (lowerHost.contains("cohere")) {
            return "cohere";
        } else if (lowerHost.equals("localhost") || lowerHost.equals("127.0.0.1") || 
                 lowerHost.startsWith("192.168.") || lowerHost.contains("ollama")) {
            return "ollama";
        } else if (lowerHost.contains("vllm")) {
            return "vllm";
        } else if (lowerHost.contains("gpustack")) {
            return "gpustack";
        } else if (lowerHost.contains("xinference")) {
            return "xinference";
        } else if (lowerHost.contains("localai")) {
            return "localai";
        }
        
        return "unknown";
    }
    
    /**
     * 注入追踪头到请求中
     */
    private ClientRequest injectTracingHeaders(ClientRequest request, TracingContext tracingContext) {
        Map<String, String> tracingHeaders = new HashMap<>();
        tracingContext.injectContext(tracingHeaders);
        
        if (tracingHeaders.isEmpty()) {
            return request;
        }
        
        // 构建新的请求，添加追踪头
        ClientRequest.Builder builder = ClientRequest.from(request);
        tracingHeaders.forEach(builder::header);
        
        return builder.build();
    }
    
    /**
     * 记录请求开始日志
     */
    private void logRequestStart(ClientRequest request, TracingContext tracingContext) {
        try {
            URI uri = request.url();
            String adapter = inferAdapterType(uri.getHost());
            String instance = uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
            
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("method", request.method().name());
            requestData.put("url", uri.toString());
            requestData.put("adapter", adapter != null ? adapter : "unknown");
            requestData.put("instance", instance);
            requestData.put("headers", sanitizeHeaders(request.headers().toSingleValueMap()));
            
            structuredLogger.logBusinessEvent("backend_call_start", requestData, tracingContext);
        } catch (Exception e) {
            logger.warn("Failed to log backend call start", e);
        }
    }
    
    /**
     * 处理成功响应
     */
    private void handleSuccessResponse(ClientResponse response, ClientRequest request, 
                                     Span span, TracingContext tracingContext, Instant startTime) {
        try {
            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            int statusCode = response.statusCode().value();
            
            // 设置响应相关的Span属性
            span.setAttribute(HTTP_STATUS_CODE, statusCode);
            
            // 设置Span状态
            if (response.statusCode().is2xxSuccessful()) {
                span.setStatus(StatusCode.OK);
            } else if (response.statusCode().is4xxClientError()) {
                span.setStatus(StatusCode.ERROR, "Client Error: " + statusCode);
            } else if (response.statusCode().is5xxServerError()) {
                span.setStatus(StatusCode.ERROR, "Server Error: " + statusCode);
            }
            
            // 记录响应内容长度（如果可用）
            if (response.headers() != null) {
                List<String> contentLengthHeaders = response.headers().header("Content-Length");
                if (contentLengthHeaders != null && !contentLengthHeaders.isEmpty()) {
                    String contentLength = contentLengthHeaders.get(0);
                    if (contentLength != null) {
                        try {
                            span.setAttribute(HTTP_RESPONSE_CONTENT_LENGTH, Long.parseLong(contentLength));
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            
            // 记录结构化日志
            URI uri = request.url();
            String adapter = inferAdapterType(uri.getHost());
            String instance = uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
            boolean success = response.statusCode().is2xxSuccessful();
            
            structuredLogger.logBackendCallDetails(
                adapter != null ? adapter : "unknown",
                instance,
                uri.toString(),
                request.method().name(),
                duration,
                statusCode,
                success,
                tracingContext
            );
            
            // 添加Span事件
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("http.status_code", statusCode);
            eventAttributes.put("duration_ms", duration);
            tracingContext.addEvent("backend_call_completed", eventAttributes);
            
        } catch (Exception e) {
            logger.warn("Failed to handle success response", e);
        }
    }
    
    /**
     * 处理错误响应
     */
    private void handleErrorResponse(Throwable error, ClientRequest request, 
                                   Span span, TracingContext tracingContext, Instant startTime) {
        try {
            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            
            // 设置Span错误状态
            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.recordException(error);
            
            // 记录结构化错误日志
            URI uri = request.url();
            String adapter = inferAdapterType(uri.getHost());
            String instance = uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
            
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("adapter", adapter != null ? adapter : "unknown");
            errorInfo.put("instance", instance);
            errorInfo.put("url", uri.toString());
            errorInfo.put("method", request.method().name());
            errorInfo.put("duration", duration);
            errorInfo.put("error_type", error.getClass().getSimpleName());
            
            structuredLogger.logError(error, tracingContext, errorInfo);
            
            // 记录后端调用失败
            structuredLogger.logBackendCallDetails(
                adapter != null ? adapter : "unknown",
                instance,
                uri.toString(),
                request.method().name(),
                duration,
                0, // 错误情况下状态码为0
                false,
                tracingContext
            );
            
            // 添加Span事件
            Map<String, Object> eventAttributes = new HashMap<>();
            eventAttributes.put("error.type", error.getClass().getSimpleName());
            eventAttributes.put("error.message", error.getMessage());
            eventAttributes.put("duration_ms", duration);
            tracingContext.addEvent("backend_call_failed", eventAttributes);
            
        } catch (Exception e) {
            logger.warn("Failed to handle error response", e);
        }
    }
    
    /**
     * 脱敏请求头信息
     */
    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new HashMap<>();
        
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // 脱敏敏感头信息
            if (isSensitiveHeader(key)) {
                sanitized.put(key, "***");
            } else {
                sanitized.put(key, value);
            }
        }
        
        return sanitized;
    }
    
    /**
     * 检查是否为敏感头信息
     */
    private boolean isSensitiveHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        
        String lowerName = headerName.toLowerCase();
        return lowerName.contains("authorization") ||
               lowerName.contains("token") ||
               lowerName.contains("key") ||
               lowerName.contains("secret") ||
               lowerName.contains("password") ||
               lowerName.contains("credential");
    }
}
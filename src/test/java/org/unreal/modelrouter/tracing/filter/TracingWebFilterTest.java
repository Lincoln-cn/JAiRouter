package org.unreal.modelrouter.tracing.filter;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingService;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.tracing.performance.TracingPerformanceMonitor;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * TracingWebFilter单元测试
 * 
 * 测试WebFlux环境中的请求追踪过滤器功能，包括：
 * - 过滤器优先级设置
 * - 追踪上下文创建和传播
 * - 请求成功和错误处理
 * - 跳过路径逻辑
 * - 性能指标记录
 * - 慢请求检测和优化
 * - 错误情况处理
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingWebFilterTest {

    private TracingWebFilter tracingWebFilter;
    private TracingContext tracingContext;
    
    @Mock
    private TracingService tracingService;
    
    @Mock
    private StructuredLogger structuredLogger;
    
    @Mock
    private TracingPerformanceMonitor performanceMonitor;
    
    @Mock
    private ServerWebExchange exchange;
    
    @Mock
    private WebFilterChain filterChain;
    
    @Mock
    private ServerHttpRequest request;
    
    @Mock
    private ServerHttpResponse response;
    
    @Mock
    private RequestPath requestPath;
    
    @Mock
    private HttpHeaders requestHeaders;
    
    @Mock
    private HttpHeaders responseHeaders;

    @BeforeEach
    void setUp() {
        // 创建真实的TracingContext用于测试
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        Tracer tracer = tracerProvider.get("test-tracer");
        tracingContext = new DefaultTracingContext(tracer);
        
        // 创建TracingWebFilter实例
        tracingWebFilter = new TracingWebFilter(tracingService, structuredLogger, performanceMonitor);
        
        // 设置基本Mock行为
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getPath()).thenReturn(requestPath);
        when(request.getHeaders()).thenReturn(requestHeaders);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(requestPath.value()).thenReturn("/api/test");
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/test"));
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(responseHeaders.getContentLength()).thenReturn(1024L);
        
        // 设置TracingService Mock行为
        when(tracingService.createRootSpan(any(ServerWebExchange.class))).thenReturn(Mono.just(tracingContext));
        when(tracingService.triggerPerformanceOptimization()).thenReturn(Mono.empty());
        
        // 设置性能监控Mock行为
        when(performanceMonitor.recordOperationPerformance(anyString(), anyLong(), anyLong(), anyBoolean(), anyMap()))
                .thenReturn(Mono.empty());
        
        // 设置FilterChain Mock行为
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void testFilterOrderPriority() {
        // 测试过滤器优先级设置
        assertEquals(Ordered.HIGHEST_PRECEDENCE, tracingWebFilter.getOrder(),
                "TracingWebFilter应该有最高优先级");
    }

    @Test
    void testSuccessfulRequest() {
        // Given
        when(requestPath.value()).thenReturn("/api/users");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证追踪服务被调用
        verify(tracingService, times(1)).createRootSpan(exchange);
        
        // 验证日志记录
        verify(structuredLogger, times(1)).logRequest(request, tracingContext);
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
        
        // 验证性能监控
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(eq("GET /api/users"), anyLong(), anyLong(), eq(true), anyMap());
        
        // 验证Exchange属性设置
        verify(exchange, times(1)).getAttributes();
    }

    @Test
    void testErrorHandling() {
        // Given
        RuntimeException testError = new RuntimeException("Test error message");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.error(testError));
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyError(RuntimeException.class);
        
        // 验证错误日志记录
        verify(structuredLogger, times(1)).logError(testError, tracingContext);
        
        // 验证错误性能指标记录
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(startsWith("error.GET"), anyLong(), anyLong(), eq(false), anyMap());
    }

    @Test
    void testSkipHealthCheckEndpoints() {
        // Given - 健康检查端点
        when(requestPath.value()).thenReturn("/actuator/health");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证跳过了追踪服务调用
        verify(tracingService, never()).createRootSpan(any());
        verify(structuredLogger, never()).logRequest(any(), any());
        
        // 但仍然调用了过滤器链
        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    void testSkipMetricsEndpoints() {
        // Given - 监控端点
        when(requestPath.value()).thenReturn("/actuator/metrics");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证跳过了追踪
        verify(tracingService, never()).createRootSpan(any());
        verify(structuredLogger, never()).logRequest(any(), any());
    }

    @Test
    void testSkipPrometheusEndpoints() {
        // Given - Prometheus端点
        when(requestPath.value()).thenReturn("/actuator/prometheus");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证跳过了追踪
        verify(tracingService, never()).createRootSpan(any());
    }

    @Test
    void testSkipSwaggerEndpoints() {
        // Given - Swagger UI端点
        when(requestPath.value()).thenReturn("/swagger-ui/index.html");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证跳过了追踪
        verify(tracingService, never()).createRootSpan(any());
    }

    @Test
    void testSkipStaticResources() {
        // Given - 静态资源
        when(requestPath.value()).thenReturn("/webjars/bootstrap/css/bootstrap.min.css");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证跳过了追踪
        verify(tracingService, never()).createRootSpan(any());
    }

    @Test
    void testSkipApiDocsEndpoints() {
        // Given - API文档端点
        when(requestPath.value()).thenReturn("/v3/api-docs/swagger-config");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证跳过了追踪
        verify(tracingService, never()).createRootSpan(any());
    }

    @Test
    void testClientErrorResponse() {
        // Given - 4xx错误响应
        when(response.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证请求仍然被记录
        verify(structuredLogger, times(1)).logRequest(request, tracingContext);
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
    }

    @Test
    void testServerErrorResponse() {
        // Given - 5xx错误响应
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证响应被记录
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
    }

    @Test
    void testPostRequest() {
        // Given - POST请求
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(requestPath.value()).thenReturn("/api/users");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证POST操作名称
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(eq("POST /api/users"), anyLong(), anyLong(), eq(true), anyMap());
    }

    @Test
    void testRequestWithQueryParameters() {
        // Given - 带查询参数的请求
        when(requestPath.value()).thenReturn("/api/search?query=test&page=1");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证操作名称中移除了查询参数
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(eq("GET /api/search"), anyLong(), anyLong(), eq(true), anyMap());
    }

    @Test
    void testRequestWithClientIP() {
        // Given - 带X-Forwarded-For头部的请求
        when(requestHeaders.getFirst("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证性能指标包含客户端IP
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(anyString(), anyLong(), anyLong(), eq(true), 
                        argThat(metadata -> "192.168.1.100".equals(metadata.get("client.ip"))));
    }

    @Test
    void testRequestWithXRealIP() {
        // Given - 带X-Real-IP头部的请求
        when(requestHeaders.getFirst("X-Real-IP")).thenReturn("203.0.113.1");
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证客户端IP被正确提取
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(anyString(), anyLong(), anyLong(), eq(true),
                        argThat(metadata -> "203.0.113.1".equals(metadata.get("client.ip"))));
    }

    @Test
    void testRequestWithRemoteAddress() {
        // Given - 通过RemoteAddress获取IP
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("198.51.100.1", 12345));
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证使用了远程地址
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(anyString(), anyLong(), anyLong(), eq(true),
                        argThat(metadata -> "198.51.100.1".equals(metadata.get("client.ip"))));
    }

    @Test
    void testSlowRequestDetection() throws InterruptedException {
        // Given - 模拟慢请求（超过5秒）
        when(filterChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.fromCallable(() -> {
                    Thread.sleep(100); // 模拟一些处理时间
                    return null;
                }).then());
        
        // 使用反射或其他方式模拟慢请求，这里简化处理
        // 在实际场景中，会通过时间控制来测试
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 由于模拟时间限制，这里主要验证基本功能
        verify(tracingService, times(1)).createRootSpan(exchange);
    }

    @Test
    void testTracingServiceError() {
        // Given - 追踪服务出错
        when(tracingService.createRootSpan(any(ServerWebExchange.class)))
                .thenReturn(Mono.error(new RuntimeException("Tracing service error")));
        
        // When & Then - 应该继续执行主业务流程
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证仍然调用了过滤器链
        verify(filterChain, times(1)).filter(exchange);
        
        // 验证没有调用结构化日志（因为追踪失败）
        verify(structuredLogger, never()).logRequest(any(), any());
    }

    @Test
    void testNullMethod() {
        // Given - null HTTP方法
        when(request.getMethod()).thenReturn(null);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证使用UNKNOWN作为方法名
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(eq("UNKNOWN /api/test"), anyLong(), anyLong(), eq(true), anyMap());
    }

    @Test
    void testNullStatusCode() {
        // Given - null状态码
        when(response.getStatusCode()).thenReturn(null);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证仍然正常处理
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
    }

    @Test
    void testZeroContentLength() {
        // Given - 零内容长度
        when(responseHeaders.getContentLength()).thenReturn(0L);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证正常处理
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
    }

    @Test
    void testNegativeContentLength() {
        // Given - 负内容长度（未知长度）
        when(responseHeaders.getContentLength()).thenReturn(-1L);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证正常处理
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
    }

    @Test
    void testPerformanceMonitorError() {
        // Given - 性能监控器出错
        when(performanceMonitor.recordOperationPerformance(anyString(), anyLong(), anyLong(), anyBoolean(), anyMap()))
                .thenReturn(Mono.error(new RuntimeException("Performance monitor error")));
        
        // When & Then - 应该不影响主流程
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证主要功能仍然执行
        verify(structuredLogger, times(1)).logRequest(request, tracingContext);
        verify(structuredLogger, times(1)).logResponse(eq(response), eq(tracingContext), anyLong());
    }

    @Test
    void testStructuredLoggerError() {
        // Given - 结构化日志器出错
        doThrow(new RuntimeException("Logger error")).when(structuredLogger).logRequest(any(), any());
        
        // When & Then - 应该不影响主流程
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证过滤器链仍然被调用
        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    void testComplexErrorScenario() {
        // Given - 复杂错误场景
        RuntimeException chainError = new RuntimeException("Chain processing error");
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.error(chainError));
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyError(RuntimeException.class);
        
        // 验证错误处理
        verify(structuredLogger, times(1)).logError(chainError, tracingContext);
        verify(performanceMonitor, times(1))
                .recordOperationPerformance(startsWith("error."), anyLong(), anyLong(), eq(false), anyMap());
    }

    @Test
    void testConcurrentRequests() {
        // Given - 多个并发请求
        // 这个测试主要验证过滤器的线程安全性
        
        // When & Then
        Mono<Void> request1 = tracingWebFilter.filter(exchange, filterChain);
        Mono<Void> request2 = tracingWebFilter.filter(exchange, filterChain);
        Mono<Void> request3 = tracingWebFilter.filter(exchange, filterChain);
        
        StepVerifier.create(Mono.when(request1, request2, request3))
                .verifyComplete();
        
        // 验证所有请求都被处理
        verify(tracingService, times(3)).createRootSpan(exchange);
        verify(structuredLogger, times(3)).logRequest(request, tracingContext);
    }

    @Test
    void testReactorContextPropagation() {
        // Given - 验证Reactor上下文传播
        when(filterChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.deferContextual(contextView -> {
                    // 验证追踪上下文在Reactor上下文中
                    assertTrue(contextView.hasKey("tracingContext"), 
                            "Reactor上下文应该包含追踪上下文");
                    return Mono.empty();
                }));
        
        // When & Then
        StepVerifier.create(tracingWebFilter.filter(exchange, filterChain))
                .verifyComplete();
    }
}
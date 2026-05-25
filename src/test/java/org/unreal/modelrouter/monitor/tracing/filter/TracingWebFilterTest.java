package org.unreal.modelrouter.monitor.tracing.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.TracingService;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.monitor.tracing.performance.TracingPerformanceMonitor;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * TracingWebFilter 单元测试
 *
 * <p>测试追踪过滤器功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("TracingWebFilter 追踪过滤器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingWebFilterTest {

    @Mock
    private TracingService tracingService;

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private TracingPerformanceMonitor performanceMonitor;

    @Mock
    private WebFilterChain filterChain;

    @Mock
    private TracingContext tracingContext;

    @Mock
    private Span mockSpan;

    private TracingWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TracingWebFilter(tracingService, structuredLogger, performanceMonitor);

        // 默认行为
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        when(tracingContext.isActive()).thenReturn(true);
        when(tracingContext.getTraceId()).thenReturn("test-trace-id");
        when(tracingContext.getSpanId()).thenReturn("test-span-id");
        when(tracingContext.getCurrentSpan()).thenReturn(mockSpan);
        when(mockSpan.setAttribute(anyString(), anyLong())).thenReturn(mockSpan);
        when(mockSpan.setAttribute(anyString(), anyString())).thenReturn(mockSpan);
        when(mockSpan.setAttribute(anyString(), any(Boolean.class))).thenReturn(mockSpan);
        when(mockSpan.setStatus(any(StatusCode.class))).thenReturn(mockSpan);
        when(mockSpan.setStatus(any(StatusCode.class), anyString())).thenReturn(mockSpan);
        when(tracingService.createRootSpan(any(ServerWebExchange.class))).thenReturn(Mono.just(tracingContext));
        doNothing().when(tracingService).finishHttpSpan(any(ServerWebExchange.class), any(TracingContext.class), anyLong());
        when(tracingService.triggerPerformanceOptimization()).thenReturn(Mono.empty());
        when(performanceMonitor.recordOperationPerformance(anyString(), anyLong(), anyLong(), anyBoolean(), any(Map.class)))
            .thenReturn(Mono.empty());
        doNothing().when(structuredLogger).logRequest(any(), any());
        doNothing().when(structuredLogger).logResponse(any(), any(), anyLong());
        doNothing().when(structuredLogger).logError(any(Throwable.class), any());
    }

    // ==================== 过滤器优先级测试 ====================

    @Nested
    @DisplayName("过滤器优先级测试")
    class OrderTests {

        @Test
        @DisplayName("TRACE-001: 过滤器具有最高优先级")
        void testGetOrder_HighestPrecedence() {
            assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
        }
    }

    // ==================== 跳过追踪测试 ====================

    @Nested
    @DisplayName("跳过追踪测试")
    class SkipTracingTests {

        @Test
        @DisplayName("TRACE-002: 健康检查端点跳过追踪")
        void testFilter_HealthEndpoint_SkipsTracing() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
            verify(tracingService, never()).createRootSpan(any());
        }

        @Test
        @DisplayName("TRACE-003: Prometheus端点跳过追踪")
        void testFilter_PrometheusEndpoint_SkipsTracing() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/prometheus")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
            verify(tracingService, never()).createRootSpan(any());
        }

        @Test
        @DisplayName("TRACE-004: Swagger UI跳过追踪")
        void testFilter_SwaggerUI_SkipsTracing() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/swagger-ui/index.html")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
            verify(tracingService, never()).createRootSpan(any());
        }

        @Test
        @DisplayName("TRACE-005: API文档跳过追踪")
        void testFilter_ApiDocs_SkipsTracing() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/v3/api-docs/swagger")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(filterChain).filter(exchange);
            verify(tracingService, never()).createRootSpan(any());
        }
    }

    // ==================== 正常请求追踪测试 ====================

    @Nested
    @DisplayName("正常请求追踪测试")
    class NormalRequestTests {

        @Test
        @DisplayName("TRACE-006: API请求成功追踪")
        void testFilter_ApiRequest_TracesSuccessfully() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/services")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(tracingService).createRootSpan(any());
            verify(structuredLogger).logRequest(any(), any());
        }

        @Test
        @DisplayName("TRACE-007: POST请求追踪")
        void testFilter_PostRequest_TracesSuccessfully() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/services")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            verify(tracingService).createRootSpan(any());
        }

        @Test
        @DisplayName("TRACE-008: 请求追踪失败时降级处理")
        void testFilter_TracingFails_FallbackGracefully() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/services")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            when(tracingService.createRootSpan(any())).thenReturn(Mono.error(new RuntimeException("Tracing failed")));

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            // 追踪失败时应继续处理请求
            verify(filterChain).filter(exchange);
        }
    }

    // ==================== 已存在追踪上下文测试 ====================

    @Nested
    @DisplayName("已存在追踪上下文测试")
    class ExistingContextTests {

        @Test
        @DisplayName("TRACE-009: 已存在活跃追踪上下文时跳过创建")
        void testFilter_ExistingActiveContext_SkipsCreation() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/services")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put("tracing.context", tracingContext);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
            // 已存在上下文时不再创建新的
            verify(tracingService, never()).createRootSpan(any());
        }
    }
}

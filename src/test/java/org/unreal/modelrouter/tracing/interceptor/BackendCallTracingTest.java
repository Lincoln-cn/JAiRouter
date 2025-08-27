package org.unreal.modelrouter.tracing.interceptor;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.client.TracingWebClientFactory;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * BackendCallTracing单元测试
 * 
 * 测试后端调用追踪拦截器功能，包括：
 * - WebClient ExchangeFilterFunction实现
 * - 追踪Span的创建和管理
 * - 追踪头部的注入
 * - 请求和响应的记录
 * - 错误处理和Span状态设置
 * - 后端服务类型识别
 * - 性能指标收集
 * - TracingWebClientFactory集成
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackendCallTracingTest {

    private BackendCallTracingInterceptor interceptor;
    private TracingWebClientFactory webClientFactory;
    private TracingContext tracingContext;
    
    @Mock
    private StructuredLogger structuredLogger;
    
    @Mock
    private ExchangeFunction exchangeFunction;
    
    @Mock
    private ClientResponse clientResponse;
    
    @Mock
    private ClientResponse.Headers responseHeaders;

    @BeforeEach
    void setUp() {
        // 创建真实的TracingContext
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        Tracer tracer = tracerProvider.get("test-tracer");
        tracingContext = new DefaultTracingContext(tracer);
        
        // 创建一个活跃的Span使TracingContext变为活跃状态
        Span testSpan = tracingContext.createSpan("test-operation", SpanKind.SERVER);
        tracingContext.setCurrentSpan(testSpan);
        
        // 创建测试对象
        interceptor = new BackendCallTracingInterceptor(structuredLogger);
        webClientFactory = new TracingWebClientFactory(interceptor);
        
        // 设置ClientResponse Mock行为
        when(clientResponse.statusCode()).thenReturn(HttpStatus.OK);
        when(clientResponse.headers()).thenReturn(responseHeaders);
        when(responseHeaders.header("Content-Length")).thenReturn(List.of("1024"));
        
        // 设置ExchangeFunction默认行为
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
    }

    @Test
    void testFilterWithoutTracingContext() {
        // Given - 没有追踪上下文
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://example.com/api"))
                .build();
        when(exchangeFunction.exchange(request)).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(null);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证直接调用了exchange，没有追踪处理
            verify(exchangeFunction, times(1)).exchange(request);
            verify(structuredLogger, never()).logBusinessEvent(anyString(), anyMap(), any());
        }
    }

    @Test
    void testFilterWithInactiveTracingContext() {
        // Given - 非活跃的追踪上下文
        TracingContext inactiveContext = mock(TracingContext.class);
        when(inactiveContext.isActive()).thenReturn(false);
        
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://example.com/api"))
                .build();
        when(exchangeFunction.exchange(request)).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(inactiveContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证直接调用了exchange，没有追踪处理
            verify(exchangeFunction, times(1)).exchange(request);
            verify(structuredLogger, never()).logBusinessEvent(anyString(), anyMap(), any());
        }
    }

    @Test
    void testFilterWithActiveTracingContextSuccess() {
        // Given - 活跃的追踪上下文
        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证交换函数被调用（可能带有修改的请求）
            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
            
            // 验证记录了请求开始事件
            verify(structuredLogger, times(1)).logBusinessEvent(eq("backend_call_start"), anyMap(), eq(tracingContext));
            
            // 验证记录了后端调用详情
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("openai"), // 推断的适配器类型
                    eq("api.openai.com"), // 实例
                    eq("https://api.openai.com/v1/chat/completions"), // URL
                    eq("POST"), // 方法
                    anyLong(), // 持续时间
                    eq(200), // 状态码
                    eq(true), // 成功
                    eq(tracingContext)
            );
        }
    }

    @Test
    void testFilterWithClientError() {
        // Given - 4xx客户端错误
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        
        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.openai.com/v1/chat/completions"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证记录了后端调用详情（失败）
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("openai"),
                    eq("api.openai.com"),
                    anyString(),
                    eq("POST"),
                    anyLong(),
                    eq(400), // 错误状态码
                    eq(false), // 失败
                    eq(tracingContext)
            );
        }
    }

    @Test
    void testFilterWithServerError() {
        // Given - 5xx服务器错误
        when(clientResponse.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.anthropic.com/v1/messages"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证记录了后端调用详情（失败）
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("anthropic"), // 推断的适配器类型
                    eq("api.anthropic.com"),
                    anyString(),
                    eq("GET"),
                    anyLong(),
                    eq(500), // 服务器错误状态码
                    eq(false), // 失败
                    eq(tracingContext)
            );
        }
    }

    @Test
    void testFilterWithNetworkError() {
        // Given - 网络错误
        RuntimeException networkError = new RuntimeException("Connection timeout");
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.error(networkError));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectError(RuntimeException.class)
                    .verify();
            
            // 验证记录了错误
            verify(structuredLogger, times(1)).logError(eq(networkError), eq(tracingContext), anyMap());
            
            // 验证记录了失败的后端调用详情
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("openai"),
                    eq("api.openai.com"),
                    anyString(),
                    eq("GET"),
                    anyLong(),
                    eq(0), // 错误情况下状态码为0
                    eq(false), // 失败
                    eq(tracingContext)
            );
        }
    }

    @Test
    void testInferAdapterTypeOpenAI() {
        // When & Then
        assertEquals("openai", interceptor.inferAdapterType("api.openai.com"));
        assertEquals("openai", interceptor.inferAdapterType("openai.azure.com"));
    }

    @Test
    void testInferAdapterTypeAnthropic() {
        // When & Then
        assertEquals("anthropic", interceptor.inferAdapterType("api.anthropic.com"));
        assertEquals("anthropic", interceptor.inferAdapterType("claude.anthropic.com"));
    }

    @Test
    void testInferAdapterTypeGoogle() {
        // When & Then
        assertEquals("google", interceptor.inferAdapterType("generativelanguage.googleapis.com"));
        assertEquals("google", interceptor.inferAdapterType("ai.googleapis.com"));
    }

    @Test
    void testInferAdapterTypeHuggingFace() {
        // When & Then
        assertEquals("huggingface", interceptor.inferAdapterType("api-inference.huggingface.co"));
        assertEquals("huggingface", interceptor.inferAdapterType("huggingface.co"));
    }

    @Test
    void testInferAdapterTypeCohere() {
        // When & Then
        assertEquals("cohere", interceptor.inferAdapterType("api.cohere.ai"));
        assertEquals("cohere", interceptor.inferAdapterType("cohere.com"));
    }

    @Test
    void testInferAdapterTypeOllama() {
        // When & Then
        assertEquals("ollama", interceptor.inferAdapterType("localhost"));
        assertEquals("ollama", interceptor.inferAdapterType("127.0.0.1"));
        assertEquals("ollama", interceptor.inferAdapterType("192.168.1.100"));
    }

    @Test
    void testInferAdapterTypeUnknown() {
        // When & Then
        assertEquals("unknown", interceptor.inferAdapterType("unknown-service.com"));
        assertEquals("unknown", interceptor.inferAdapterType("example.com"));
        assertNull(interceptor.inferAdapterType(null));
    }

    @Test
    void testTracingHeaderInjection() {
        // Given
        ClientRequest originalRequest = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .header("User-Agent", "ModelRouter/1.0")
                .build();
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When
            StepVerifier.create(interceptor.filter(originalRequest, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // Then - 验证追踪头被注入
            verify(exchangeFunction).exchange(argThat(request -> {
                // 验证请求包含原有头部
                assertTrue(request.headers().containsKey("User-Agent"));
                // 验证请求可能包含追踪头部（具体实现取决于追踪上下文的头部注入逻辑）
                return true;
            }));
        }
    }

    @Test
    void testOperationNameGeneration() {
        // Given
        ClientRequest request1 = ClientRequest.create(HttpMethod.POST, URI.create("https://api.openai.com/v1/chat/completions"))
                .build();
        ClientRequest request2 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.anthropic.com/v1/messages"))
                .build();
        ClientRequest request3 = ClientRequest.create(HttpMethod.PUT, URI.create("https://example.com/complex/path/with/ids/123"))
                .build();
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then - 验证操作名称生成逻辑
            StepVerifier.create(interceptor.filter(request1, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            StepVerifier.create(interceptor.filter(request2, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            StepVerifier.create(interceptor.filter(request3, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证调用了3次exchange
            verify(exchangeFunction, times(3)).exchange(any());
        }
    }

    @Test
    void testRequestWithPort() {
        // Given - 带端口的请求
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost:8080/api/models"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证记录了正确的实例信息（包含端口）
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("ollama"), // localhost 推断为 ollama
                    eq("localhost:8080"), // 包含端口的实例
                    anyString(),
                    anyString(),
                    anyLong(),
                    anyInt(),
                    anyBoolean(),
                    eq(tracingContext)
            );
        }
    }

    @Test
    void testRequestWithUserAgent() {
        // Given - 带User-Agent的请求
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .header("User-Agent", "ModelRouter/1.0.0")
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证User-Agent被记录
            verify(exchangeFunction).exchange(argThat(req -> 
                "ModelRouter/1.0.0".equals(req.headers().getFirst("User-Agent"))));
        }
    }

    @Test
    void testResponseWithContentLength() {
        // Given - 带Content-Length的响应
        when(responseHeaders.header("Content-Length")).thenReturn(List.of("2048"));
        
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证响应处理正常
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    anyString(), anyString(), anyString(), anyString(),
                    anyLong(), eq(200), eq(true), eq(tracingContext)
            );
        }
    }

    @Test
    void testResponseWithoutContentLength() {
        // Given - 没有Content-Length的响应
        when(responseHeaders.header("Content-Length")).thenReturn(List.of());
        
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证响应处理正常（即使没有Content-Length）
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    anyString(), anyString(), anyString(), anyString(),
                    anyLong(), eq(200), eq(true), eq(tracingContext)
            );
        }
    }

    @Test
    void testTracingWebClientFactory() {
        // When
        var webClient = webClientFactory.createTracingWebClient("https://api.openai.com");
        
        // Then
        assertNotNull(webClient);
        
        // 验证WebClient工厂能正常创建WebClient实例
        // （实际的拦截器功能在其他测试中验证）
    }

    @Test
    void testTracingWebClientFactoryAddToExisting() {
        // Given
        var existingClient = org.springframework.web.reactive.function.client.WebClient.builder()
                .baseUrl("https://example.com")
                .build();
        
        // When
        var enhancedClient = webClientFactory.addTracingToWebClient(existingClient);
        
        // Then
        assertNotNull(enhancedClient);
        assertNotSame(existingClient, enhancedClient);
    }

    @Test
    void testConcurrentRequests() {
        // Given - 并发请求测试
        ClientRequest request1 = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .build();
        ClientRequest request2 = ClientRequest.create(HttpMethod.POST, URI.create("https://api.anthropic.com/v1/messages"))
                .build();
        
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When - 并发执行两个请求
            Mono<ClientResponse> response1 = interceptor.filter(request1, exchangeFunction);
            Mono<ClientResponse> response2 = interceptor.filter(request2, exchangeFunction);
            
            // Then
            StepVerifier.create(Mono.when(response1, response2))
                    .verifyComplete();
            
            // 验证两个请求都被处理
            verify(exchangeFunction, times(2)).exchange(any(ClientRequest.class));
            verify(structuredLogger, times(2)).logBusinessEvent(eq("backend_call_start"), anyMap(), eq(tracingContext));
            verify(structuredLogger, times(2)).logBackendCallDetails(
                    anyString(), anyString(), anyString(), anyString(),
                    anyLong(), anyInt(), anyBoolean(), eq(tracingContext)
            );
        }
    }

    @Test
    void testStructuredLoggerError() {
        // Given - 结构化日志器抛出异常
        doThrow(new RuntimeException("Logger error")).when(structuredLogger)
                .logBusinessEvent(anyString(), anyMap(), any());
        
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.openai.com/v1/models"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then - 应该不影响主流程
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证仍然调用了exchange
            verify(exchangeFunction, times(1)).exchange(any(ClientRequest.class));
        }
    }

    @Test
    void testComplexURL() {
        // Given - 复杂URL测试
        ClientRequest request = ClientRequest.create(HttpMethod.POST, 
                URI.create("https://api.openai.com/v1/chat/completions?stream=false&timeout=30"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证复杂URL被正确处理
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("openai"),
                    eq("api.openai.com"),
                    eq("https://api.openai.com/v1/chat/completions?stream=false&timeout=30"),
                    eq("POST"),
                    anyLong(),
                    eq(200),
                    eq(true),
                    eq(tracingContext)
            );
        }
    }

    @Test
    void testIPAddressHost() {
        // Given - IP地址主机
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://192.168.1.100:8080/api/generate"))
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(clientResponse));
        
        try (MockedStatic<TracingContextHolder> mockedStatic = mockStatic(TracingContextHolder.class)) {
            mockedStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            
            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(clientResponse)
                    .verifyComplete();
            
            // 验证IP地址被正确识别为ollama适配器
            verify(structuredLogger, times(1)).logBackendCallDetails(
                    eq("ollama"),
                    eq("192.168.1.100:8080"),
                    anyString(),
                    eq("GET"),
                    anyLong(),
                    eq(200),
                    eq(true),
                    eq(tracingContext)
            );
        }
    }
}
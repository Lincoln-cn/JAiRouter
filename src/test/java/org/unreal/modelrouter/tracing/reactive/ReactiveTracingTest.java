package org.unreal.modelrouter.tracing.reactive;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.TracingConstants;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingService;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * ReactiveTracing单元测试
 * 
 * 测试Reactor响应式流中的追踪上下文传播功能，包括：
 * - Reactor Context中的追踪上下文传播
 * - WebFlux ServerWebExchange上下文操作
 * - 异步操作中的上下文保持
 * - 链式操作中的上下文传递
 * - 错误处理时的上下文维护
 * - 并发操作中的上下文隔离
 * - 上下文生命周期管理
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReactiveTracingTest {

    private ReactiveTracingContextHolder reactiveContextHolder;
    private WebFluxTracingContextPropagator webFluxPropagator;
    private TracingContext tracingContext;
    
    @Mock
    private TracingService tracingService;
    
    @Mock
    private ServerWebExchange exchange;
    
    @Mock
    private ServerHttpRequest request;
    
    @Mock
    private ServerHttpResponse response;
    
    @Mock
    private RequestPath requestPath;

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
        reactiveContextHolder = new ReactiveTracingContextHolder();
        webFluxPropagator = new WebFluxTracingContextPropagator();
        
        // 设置基本Mock行为
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/test"));
        when(request.getHeaders()).thenReturn(HttpHeaders.EMPTY);
        
        Map<String, Object> exchangeAttributes = new HashMap<>();
        when(exchange.getAttributes()).thenReturn(exchangeAttributes);
    }

    @Test
    void testReactiveTracingContextHolderGetCurrentContext() {
        // Given - 创建包含追踪上下文的Reactor Context
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When & Then
        StepVerifier.create(
                ReactiveTracingContextHolder.getCurrentContext()
                        .contextWrite(reactorContext)
        )
        .expectNext(tracingContext)
        .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderGetCurrentContextEmpty() {
        // When & Then - 空上下文应该返回空Mono
        StepVerifier.create(ReactiveTracingContextHolder.getCurrentContext())
                .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderGetCurrentTraceId() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        String expectedTraceId = tracingContext.getTraceId();
        
        // When & Then
        StepVerifier.create(
                ReactiveTracingContextHolder.getCurrentTraceId()
                        .contextWrite(reactorContext)
        )
        .expectNext(expectedTraceId)
        .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderGetCurrentSpanId() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        String expectedSpanId = tracingContext.getSpanId();
        
        // When & Then
        StepVerifier.create(
                ReactiveTracingContextHolder.getCurrentSpanId()
                        .contextWrite(reactorContext)
        )
        .expectNext(expectedSpanId)
        .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderWithTracingContext() {
        // When
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // Then
        assertTrue(reactorContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertTrue(reactorContext.hasKey(TracingConstants.ContextKeys.TRACE_ID));
        assertTrue(reactorContext.hasKey(TracingConstants.ContextKeys.SPAN_ID));
        
        assertEquals(tracingContext, reactorContext.get(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals(tracingContext.getTraceId(), reactorContext.get(TracingConstants.ContextKeys.TRACE_ID));
        assertEquals(tracingContext.getSpanId(), reactorContext.get(TracingConstants.ContextKeys.SPAN_ID));
    }

    @Test
    void testReactiveTracingContextHolderWithNullContext() {
        // When
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(null);
        
        // Then
        assertEquals(Context.empty(), reactorContext);
    }

    @Test
    void testReactiveTracingContextHolderMergeContext() {
        // Given
        Context existingContext = Context.of("existing", "value");
        
        // When
        Context mergedContext = ReactiveTracingContextHolder.withTracingContext(existingContext, tracingContext);
        
        // Then
        assertTrue(mergedContext.hasKey("existing"));
        assertTrue(mergedContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals("value", mergedContext.get("existing"));
        assertEquals(tracingContext, mergedContext.get(TracingConstants.ContextKeys.TRACING_CONTEXT));
    }

    @Test
    void testReactiveTracingContextHolderHasTracingContext() {
        // Given
        Context reactorContextWithTracing = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        Context reactorContextEmpty = Context.empty();
        
        // When & Then
        StepVerifier.create(
                ReactiveTracingContextHolder.hasTracingContext()
                        .contextWrite(reactorContextWithTracing)
        )
        .expectNext(true)
        .verifyComplete();
        
        StepVerifier.create(
                ReactiveTracingContextHolder.hasTracingContext()
                        .contextWrite(reactorContextEmpty)
        )
        .expectNext(false)
        .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderStaticHasTracingContext() {
        // Given
        Context reactorContextWithTracing = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        Context reactorContextEmpty = Context.empty();
        
        // When & Then
        assertTrue(ReactiveTracingContextHolder.hasTracingContext(reactorContextWithTracing));
        assertFalse(ReactiveTracingContextHolder.hasTracingContext(reactorContextEmpty));
    }

    @Test
    void testReactiveTracingContextHolderClearTracingContext() {
        // Given
        Context contextWithTracing = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When
        Context clearedContext = ReactiveTracingContextHolder.clearTracingContext(contextWithTracing);
        
        // Then
        assertFalse(clearedContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertFalse(clearedContext.hasKey(TracingConstants.ContextKeys.TRACE_ID));
        assertFalse(clearedContext.hasKey(TracingConstants.ContextKeys.SPAN_ID));
    }

    @Test
    void testReactiveTracingContextHolderCopyTracingContext() {
        // Given
        Context sourceContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        Context targetContext = Context.of("target", "value");
        
        // When
        Context copiedContext = ReactiveTracingContextHolder.copyTracingContext(sourceContext, targetContext);
        
        // Then
        assertTrue(copiedContext.hasKey("target"));
        assertTrue(copiedContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals("value", copiedContext.get("target"));
        assertEquals(tracingContext, copiedContext.get(TracingConstants.ContextKeys.TRACING_CONTEXT));
    }

    @Test
    void testReactiveTracingContextHolderContextWriter() {
        // Given
        Context emptyContext = Context.empty();
        
        // When
        Context enhancedContext = ReactiveTracingContextHolder.contextWriter(tracingContext).apply(emptyContext);
        
        // Then
        assertTrue(enhancedContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals(tracingContext, enhancedContext.get(TracingConstants.ContextKeys.TRACING_CONTEXT));
    }

    @Test
    void testReactiveTracingContextHolderWithContext() {
        // Given
        Mono<String> operation = Mono.just("test-result");
        
        // When & Then
        StepVerifier.create(
                ReactiveTracingContextHolder.withContext(tracingContext, operation)
                        .flatMap(result -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> result + ":" + ctx.getTraceId()))
        )
        .expectNext("test-result:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderWithNullContextInOperation() {
        // Given
        Mono<String> operation = Mono.just("test-result");
        
        // When & Then
        StepVerifier.create(ReactiveTracingContextHolder.withContext(null, operation))
                .expectNext("test-result")
                .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderWithCurrentContext() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When & Then
        StepVerifier.create(
                ReactiveTracingContextHolder.withCurrentContext(ctx -> {
                    if (ctx != null) {
                        return Mono.just("tracing-enabled:" + ctx.getTraceId());
                    } else {
                        return Mono.just("no-tracing");
                    }
                }).contextWrite(reactorContext)
        )
        .expectNext("tracing-enabled:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testReactiveTracingContextHolderWithCurrentContextEmpty() {
        // When & Then - 空上下文应该调用函数并传入null
        StepVerifier.create(
                ReactiveTracingContextHolder.withCurrentContext(ctx -> {
                    if (ctx != null) {
                        return Mono.just("tracing-enabled");
                    } else {
                        return Mono.just("no-tracing");
                    }
                })
        )
        .expectNext("no-tracing")
        .verifyComplete();
    }

    @Test
    void testWebFluxTracingContextPropagatorExtractFromExchange() {
        // Given - 设置exchange属性
        exchange.getAttributes().put(TracingConstants.ContextKeys.TRACING_CONTEXT, tracingContext);
        
        // When
        TracingContext extractedContext = webFluxPropagator.extractFromExchange(exchange);
        
        // Then
        assertEquals(tracingContext, extractedContext);
    }

    @Test
    void testWebFluxTracingContextPropagatorExtractFromExchangeEmpty() {
        // When - exchange中没有追踪上下文
        TracingContext extractedContext = webFluxPropagator.extractFromExchange(exchange);
        
        // Then
        assertNull(extractedContext);
    }

    @Test
    void testWebFluxTracingContextPropagatorInjectToExchange() {
        // When
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        // Then
        verify(exchange, times(3)).getAttributes();
        assertEquals(tracingContext, exchange.getAttributes().get(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals(tracingContext.getTraceId(), exchange.getAttributes().get(TracingConstants.ContextKeys.TRACE_ID));
        assertEquals(tracingContext.getSpanId(), exchange.getAttributes().get(TracingConstants.ContextKeys.SPAN_ID));
    }

    @Test
    void testWebFluxTracingContextPropagatorInjectNullContext() {
        // When
        webFluxPropagator.injectToExchange(exchange, null);
        
        // Then - 不应该有任何操作
        verify(exchange, never()).getAttributes();
    }

    @Test
    void testWebFluxTracingContextPropagatorContextWriter() {
        // Given - 注入追踪上下文到exchange
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        // When
        var contextWriter = webFluxPropagator.contextWriter(exchange);
        Context enhancedContext = contextWriter.apply(Context.empty());
        
        // Then
        assertTrue(enhancedContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals(tracingContext, enhancedContext.get(TracingConstants.ContextKeys.TRACING_CONTEXT));
    }

    @Test
    void testWebFluxTracingContextPropagatorContextWriterEmpty() {
        // When - exchange中没有追踪上下文
        var contextWriter = webFluxPropagator.contextWriter(exchange);
        Context enhancedContext = contextWriter.apply(Context.of("test", "value"));
        
        // Then - 应该返回原始上下文
        assertEquals("value", enhancedContext.get("test"));
        assertFalse(enhancedContext.hasKey(TracingConstants.ContextKeys.TRACING_CONTEXT));
    }

    @Test
    void testWebFluxTracingContextPropagatorWithTracingContextMono() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        Mono<String> operation = Mono.just("test-result");
        
        // When & Then
        StepVerifier.create(
                webFluxPropagator.withTracingContext(exchange, operation)
                        .flatMap(result -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> result + ":" + ctx.getTraceId()))
        )
        .expectNext("test-result:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testWebFluxTracingContextPropagatorWithTracingContextFunction() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        // When & Then
        StepVerifier.create(
                webFluxPropagator.withTracingContext(exchange, ctx -> {
                    if (ctx != null) {
                        return Mono.just("context-available:" + ctx.getTraceId());
                    } else {
                        return Mono.just("no-context");
                    }
                })
        )
        .expectNext("context-available:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testWebFluxTracingContextPropagatorHasTracingContext() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        // When & Then
        assertTrue(webFluxPropagator.hasTracingContext(exchange));
        
        // Test without context
        ServerWebExchange emptyExchange = mock(ServerWebExchange.class);
        when(emptyExchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT)).thenReturn(null);
        assertFalse(webFluxPropagator.hasTracingContext(emptyExchange));
    }

    @Test
    void testWebFluxTracingContextPropagatorGetTraceId() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        // When
        String traceId = webFluxPropagator.getTraceId(exchange);
        
        // Then
        assertEquals(tracingContext.getTraceId(), traceId);
    }

    @Test
    void testWebFluxTracingContextPropagatorGetTraceIdEmpty() {
        // When - exchange中没有追踪上下文
        String traceId = webFluxPropagator.getTraceId(exchange);
        
        // Then
        assertEquals("", traceId);
    }

    @Test
    void testWebFluxTracingContextPropagatorGetSpanId() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        // When
        String spanId = webFluxPropagator.getSpanId(exchange);
        
        // Then
        assertEquals(tracingContext.getSpanId(), spanId);
    }

    @Test
    void testWebFluxTracingContextPropagatorGetSpanIdEmpty() {
        // When - exchange中没有追踪上下文
        String spanId = webFluxPropagator.getSpanId(exchange);
        
        // Then
        assertEquals("", spanId);
    }

    @Test
    void testWebFluxTracingContextPropagatorClearTracingContext() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        assertTrue(webFluxPropagator.hasTracingContext(exchange));
        
        // When
        webFluxPropagator.clearTracingContext(exchange);
        
        // Then
        assertFalse(webFluxPropagator.hasTracingContext(exchange));
        assertNull(exchange.getAttributes().get(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertNull(exchange.getAttributes().get(TracingConstants.ContextKeys.TRACE_ID));
        assertNull(exchange.getAttributes().get(TracingConstants.ContextKeys.SPAN_ID));
    }

    @Test
    void testWebFluxTracingContextPropagatorCopyTracingContext() {
        // Given
        webFluxPropagator.injectToExchange(exchange, tracingContext);
        
        ServerWebExchange targetExchange = mock(ServerWebExchange.class);
        Map<String, Object> targetAttributes = new HashMap<>();
        when(targetExchange.getAttributes()).thenReturn(targetAttributes);
        
        // When
        webFluxPropagator.copyTracingContext(exchange, targetExchange);
        
        // Then
        assertEquals(tracingContext, targetAttributes.get(TracingConstants.ContextKeys.TRACING_CONTEXT));
        assertEquals(tracingContext.getTraceId(), targetAttributes.get(TracingConstants.ContextKeys.TRACE_ID));
        assertEquals(tracingContext.getSpanId(), targetAttributes.get(TracingConstants.ContextKeys.SPAN_ID));
    }

    @Test
    void testTracingContextPropagationInChainedOperations() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When & Then - 测试链式操作中的上下文传播
        StepVerifier.create(
                Mono.just("start")
                        .flatMap(value -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> value + ":" + ctx.getTraceId()))
                        .flatMap(value -> Mono.just(value + ":middle"))
                        .flatMap(value -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> value + ":" + ctx.getSpanId()))
                        .contextWrite(reactorContext)
        )
        .expectNext("start:" + tracingContext.getTraceId() + ":middle:" + tracingContext.getSpanId())
        .verifyComplete();
    }

    @Test
    void testTracingContextPropagationWithErrorHandling() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When & Then - 测试错误处理时上下文是否保持
        StepVerifier.create(
                Mono.just("start")
                        .flatMap(value -> Mono.<String>error(new RuntimeException("test error")))
                        .onErrorResume(error -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> "error-handled:" + ctx.getTraceId()))
                        .contextWrite(reactorContext)
        )
        .expectNext("error-handled:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testTracingContextPropagationWithDelay() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When & Then - 测试延迟操作中的上下文传播
        StepVerifier.create(
                Mono.just("delayed")
                        .delayElement(Duration.ofMillis(10))
                        .flatMap(value -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> value + ":" + ctx.getTraceId()))
                        .contextWrite(reactorContext)
        )
        .expectNext("delayed:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testTracingContextPropagationWithFlux() {
        // Given
        Context reactorContext = ReactiveTracingContextHolder.withTracingContext(tracingContext);
        
        // When & Then - 测试Flux中的上下文传播
        StepVerifier.create(
                Flux.just("item1", "item2", "item3")
                        .flatMap(item -> ReactiveTracingContextHolder.getCurrentContext()
                                .map(ctx -> item + ":" + ctx.getTraceId()))
                        .contextWrite(reactorContext)
        )
        .expectNext("item1:" + tracingContext.getTraceId())
        .expectNext("item2:" + tracingContext.getTraceId())
        .expectNext("item3:" + tracingContext.getTraceId())
        .verifyComplete();
    }

    @Test
    void testConcurrentTracingContextIsolation() {
        // Given - 创建两个不同的追踪上下文
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        Tracer tracer = tracerProvider.get("test-tracer");
        TracingContext context1 = new DefaultTracingContext(tracer);
        TracingContext context2 = new DefaultTracingContext(tracer);
        
        Context reactorContext1 = ReactiveTracingContextHolder.withTracingContext(context1);
        Context reactorContext2 = ReactiveTracingContextHolder.withTracingContext(context2);
        
        AtomicReference<String> result1 = new AtomicReference<>();
        AtomicReference<String> result2 = new AtomicReference<>();
        
        // When - 并发执行两个操作
        Mono<Void> operation1 = ReactiveTracingContextHolder.getCurrentTraceId()
                .doOnNext(result1::set)
                .contextWrite(reactorContext1)
                .then();
        
        Mono<Void> operation2 = ReactiveTracingContextHolder.getCurrentTraceId()
                .doOnNext(result2::set)
                .contextWrite(reactorContext2)
                .then();
        
        // Then
        StepVerifier.create(Mono.when(operation1, operation2))
                .verifyComplete();
        
        assertEquals(context1.getTraceId(), result1.get());
        assertEquals(context2.getTraceId(), result2.get());
        assertNotEquals(result1.get(), result2.get());
    }

    @Test
    void testTracingOperatorWithMono() {
        // Given
        when(tracingService.createOperationSpan(anyString(), any(SpanKind.class))).thenReturn(tracingContext);
        
        Mono<String> source = Mono.just("test-data");
        
        // When & Then
        StepVerifier.create(
                TracingOperator.trace(source, "test-operation", SpanKind.INTERNAL, tracingService)
        )
        .expectNext("test-data")
        .verifyComplete();
        
        verify(tracingService, times(1)).createOperationSpan("test-operation", SpanKind.INTERNAL);
    }

    @Test
    void testTracingOperatorWithFlux() {
        // Given
        when(tracingService.createOperationSpan(anyString(), any(SpanKind.class))).thenReturn(tracingContext);
        
        Flux<String> source = Flux.just("item1", "item2");
        
        // When & Then
        StepVerifier.create(
                TracingOperator.trace(source, "test-flux-operation", SpanKind.INTERNAL, tracingService)
        )
        .expectNext("item1")
        .expectNext("item2")
        .verifyComplete();
        
        verify(tracingService, times(1)).createOperationSpan("test-flux-operation", SpanKind.INTERNAL);
    }

    @Test
    void testTracingOperatorMonoTracer() {
        // Given
        when(tracingService.createOperationSpan(anyString(), any(SpanKind.class))).thenReturn(tracingContext);
        
        // When & Then
        StepVerifier.create(
                Mono.just("test")
                        .transform(TracingOperator.monoTracer("mono-operation", tracingService))
        )
        .expectNext("test")
        .verifyComplete();
        
        verify(tracingService, times(1)).createOperationSpan("mono-operation", SpanKind.INTERNAL);
    }

    @Test
    void testTracingOperatorFluxTracer() {
        // Given
        when(tracingService.createOperationSpan(anyString(), any(SpanKind.class))).thenReturn(tracingContext);
        
        // When & Then
        StepVerifier.create(
                Flux.just("a", "b")
                        .transform(TracingOperator.fluxTracer("flux-operation", tracingService))
        )
        .expectNext("a")
        .expectNext("b")
        .verifyComplete();
        
        verify(tracingService, times(1)).createOperationSpan("flux-operation", SpanKind.INTERNAL);
    }

    @Test
    void testTracingOperatorWebClientTracer() {
        // Given
        when(tracingService.createOperationSpan(anyString(), any(SpanKind.class))).thenReturn(tracingContext);
        
        // When & Then
        StepVerifier.create(
                Mono.just("response")
                        .transform(TracingOperator.webClientTracer("http-call", tracingService))
        )
        .expectNext("response")
        .verifyComplete();
        
        verify(tracingService, times(1)).createOperationSpan("http-call", SpanKind.CLIENT);
    }
}